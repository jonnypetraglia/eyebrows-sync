package com.qweex.eyebrowssync;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.Pair;
import com.qweex.eyebrows.EyebrowsError;
import com.qweex.eyebrows.did_not_write.JSONDownloader;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Syncer extends AsyncTask<String, Long, Exception> {
    ArrayList<Pair<String, Long>> filesToDownload;
    int currentDownload;
    ArrayList<String> filesToDelete;
    int currentDelete;
    Bundle server;
    Context context;
    public enum PHASE {PREPARING, COUNTING, DOWNLOADING, DELETING, ERROR, FINISHED, CANCELED}
    PHASE phase = PHASE.PREPARING;
    Exception failure;
    AttachedRelativeLayout viewOnScreen;
    Pattern maskPattern;
    String[] bouncingBall = new String[] {"Ooo", "oOo", "ooO", "oOo"};
    int bouncingIndex = 0;
    int bouncingRate = 4; //Higher == Slower
    boolean onlySimulate;
    JobList.NotificationSupervisor supervisor;
    int dudeDontFreakTheSupervisorOut = 0;
    StatusWindow statusWindow;
    boolean updateStatusAdapter = false;

    // Constructor
    public Syncer(Context context, String name, boolean simulate, JobList.NotificationSupervisor s) {
        server = SavedJobs.get(context, name);
        filesToDownload = new ArrayList<Pair<String, Long>>();
        filesToDelete = new ArrayList<String>();
        this.context = context;
        if(server.getString("mask").length()>0)
            maskPattern = Pattern.compile(server.getString("mask"));
        onlySimulate = simulate;
        supervisor = s;
        statusWindow = new StatusWindow(context, this);
    }

    // Update the notification & button
    @Override
    protected void onProgressUpdate(Long... currentFileSize) {
        Log.d("EyebrowsSync", "CurrentFileSize: " + currentFileSize.length);
        if(updateStatusAdapter) // Update the adapter if we have just recently retrieved the Downloads
            statusWindow.refreshAdapter();
        if(++dudeDontFreakTheSupervisorOut>7)
        {
            dudeDontFreakTheSupervisorOut=0;
            supervisor.update();
        }
        if(viewOnScreen ==null)
            return;
        switch(phase) {
            case COUNTING:
                viewOnScreen.button().setText(bouncingBall[bouncingIndex / bouncingRate]);
                viewOnScreen.status().setText("Changes: +" + filesToDownload.size() + "/-" + filesToDelete.size());
                viewOnScreen.status().setTextColor(context.getResources().getColor(R.color.status_preparing));
                break;
            case DOWNLOADING:
                if(currentFileSize.length>0)
                    statusWindow.updateCurrentProgress(currentFileSize[0]);
                viewOnScreen.button().setText(bouncingBall[bouncingIndex / bouncingRate]);
                viewOnScreen.status().setText("Downloading: " + (currentDownload+1) + "/" + filesToDownload.size());
                viewOnScreen.status().setTextColor(context.getResources().getColor(R.color.status_running));
                break;
            case DELETING:
                viewOnScreen.button().setText(bouncingBall[bouncingIndex / bouncingRate]);
                viewOnScreen.status().setText("Deleting: " + (currentDelete+1) + "/" + filesToDelete.size());
                viewOnScreen.status().setTextColor(context.getResources().getColor(R.color.status_running));
                break;
            case ERROR:
                viewOnScreen.button().setText("...");
                viewOnScreen.status().setText("Error: " + failure);
                viewOnScreen.status().setTextColor(context.getResources().getColor(R.color.status_error));
                break;
        }
        bouncingIndex = (bouncingIndex+1) % (bouncingBall.length*bouncingRate);
    }


    // Do the thing
    @Override
    protected Exception doInBackground(String... params) {
        try {
            //1. Build list of all files needing to be downloaded (and deleted)
            phase = PHASE.COUNTING;
            tallyFolder("");
            updateStatusAdapter = true;
            publishProgress();
            if(onlySimulate)
                return null;


            //2. Do the actual downloading
            phase = PHASE.DOWNLOADING;
            File localSubdir = new File(server.getString("local_path"));
            for(currentDownload = 0; currentDownload < filesToDownload.size(); currentDownload++) {
                Pair<String, Long> foreignFile = filesToDownload.get(currentDownload);
                downloadFile(foreignFile);
            }

            //3. Do the deleting
            phase = PHASE.DELETING;
            for(currentDelete = 0; currentDelete < filesToDelete.size(); currentDelete++) {
                String f = filesToDelete.get(currentDelete);
                File localTarget = new File(localSubdir, f);
                boolean r = DeleteRecursive(localTarget);
                if(localTarget.exists() && r)
                    MediaScannerConnection.scanFile(context, new String[]{localTarget.getPath()}, null, null);
                Log.d("EyebrowsSync", "---Deleting? " + r);
            }

        } catch (EyebrowsError eyebrowsError) {
            eyebrowsError.printStackTrace();
            return eyebrowsError;
        } catch (IOException e) {
            e.printStackTrace();
            return e;
        } catch (JSONException e) {
            e.printStackTrace();
            return e;
        } catch (CanceledException e) {
            Log.d("EyebrowsSync", "Task has been Canceled");
        }
        return null;
    }

    @Override
    protected void onPostExecute(Exception e) {
        failure = e;
        if(failure!=null) {
            //TODO
            phase = PHASE.ERROR;
            onProgressUpdate(new Long[0]);
            return;
        }
        phase = isCancelled() ? PHASE.CANCELED : PHASE.FINISHED;

        long time;
        if(!onlySimulate && !isCancelled()) {
            time = System.currentTimeMillis();
            Bundle b = new Bundle();
            b.putLong("last_updated", time);
            SavedJobs.update(context, server.getString("name"), b);
        } else {
            Bundle b = SavedJobs.get(context, server.getString("name"));
            time = b.getLong("last_updated");
        }
        Syncer.setStatusTime(context, viewOnScreen, time);
        if(viewOnScreen !=null)
            viewOnScreen.attachTo(null);
        supervisor.update();
    }

    @Override
    protected void onCancelled(Exception e) {
        onPostExecute(e);
    }

    // Tallies a folder; decides what files need to be downloaded
    void tallyFolder(String subfolder) throws EyebrowsError, IOException, JSONException, CanceledException {
        Log.i("EyebrowsSync", "Tallying: " + subfolder);
        List<String> foreignDirs = new ArrayList<String>();
        File localSubdir = new File(server.getString("local_path"));
        localSubdir = new File(localSubdir, subfolder);

        //0. Check to see if we are canceled
        if(isCancelled())
            throw new CanceledException();

        //1. Get list of local files
        List<String> localFiles = new ArrayList<String>();
        if(localSubdir.exists() && localSubdir.isDirectory())
            for(File file : localSubdir.listFiles())
                localFiles.add(file.getName());

        //2. Make JSON request & get file list
        JSONArray foreignFilesJSON;
        String path_to_load = getServerPath(subfolder);
        if(server.getBoolean("ssl"))
            foreignFilesJSON = (JSONArray) (new JSONDownloader().new https()).readJsonFromUrl(server.getString("auth"), path_to_load, null);
        else
            foreignFilesJSON = (JSONArray) (new JSONDownloader().new http()).readJsonFromUrl(server.getString("auth"), path_to_load, null);

        //3. foreach file in foreign_files
        for(int i=0; i<foreignFilesJSON.length(); i++) {
            JSONObject foreignFile = foreignFilesJSON.getJSONObject(i);
            if(foreignFile.getString("icon").equals("folder")) {
                foreignDirs.add(foreignFile.getString("name"));
                localFiles.remove(foreignFile.getString("name"));
                continue;
            }
            if(maskPattern!=null) {
                Matcher m = maskPattern.matcher(foreignFile.getString("name"));
                if(!m.matches())
                    continue;
            }

            if(localFiles.contains(foreignFile.getString("name"))) {
                //File localFile = new File(localSubdir, foreignFile.getString("name"));
                Log.i("EyebrowsSync", "Examining: " + foreignFile.getString("name"));
                //Log.d("EyebrowsSync", foreignFile.getLong("mtime") + " vs " + localFile.lastModified());
                Log.d("EyebrowsSync", foreignFile.getLong("mtime") + " vs " + server.getLong("last_updated")/1000);
                if(server.getLong("last_updated")/1000 < foreignFile.getLong("mtime"))
                    filesToDownload.add(Pair.create(joinAppend(subfolder, foreignFile.getString("name")), foreignFile.getLong("size")));
                localFiles.remove(foreignFile.getString("name"));
            } else
                filesToDownload.add(Pair.create(joinAppend(subfolder, foreignFile.getString("name")), foreignFile.getLong("size")));
        }

        //4. foreach file still in local_files
        for(String localFile : localFiles)
            filesToDelete.add(joinAppend(subfolder, localFile));
        publishProgress();

        //5. foreach subfolder in foreign_files
        for(String foreignDir : foreignDirs)
            tallyFolder(joinAppend(subfolder, foreignDir));
    }

    // Downloads a file; String = URL, long = file size
    void downloadFile(Pair<String, Long> foreignFile) throws IOException, CanceledException {
        File localSubdir = new File(server.getString("local_path"));

        File localTarget = new File(localSubdir, foreignFile.first);
        String url = getServerPath(foreignFile.first);

        Log.d("EyebrowsSync", "++Downloading: " + url + " -> " + localTarget);

        HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();
        urlConnection.setRequestMethod("GET");
        urlConnection.setRequestProperty("Authentication", "Basic " + server.getString("auth"));
        urlConnection.setUseCaches(false);
        urlConnection.setDoOutput(false);
        urlConnection.connect();

        InputStream inputStream = urlConnection.getInputStream();
        localTarget.getParentFile().mkdirs();
        FileOutputStream fileOutput = new FileOutputStream(localTarget);

        byte[] buffer = new byte[1024];
        int bufferLength = 0;
        int bucket = 0;
        int updateInterval = 1024*128; //Bigger == less often
        long currentDownloadCurrentSize = 0;

        while ( (bufferLength = inputStream.read(buffer)) > 0 ) {
            currentDownloadCurrentSize += bufferLength;
            bucket += bufferLength;
            if(bucket>updateInterval) {
                if(isCancelled()) {
                    fileOutput.close();
                    localTarget.delete();
                    throw new CanceledException();
                }
                publishProgress(currentDownloadCurrentSize);
                bucket = bucket % updateInterval;
            }
            fileOutput.write(buffer, 0, bufferLength);
        }
        MediaScannerConnection.scanFile(context, new String[]{localTarget.getPath()}, null, null);
        fileOutput.close();
    }

    // Deletes a folder recursively; basically 'rm -r'
    boolean DeleteRecursive(File fileOrDirectory) throws CanceledException {
        if(isCancelled())
            throw new CanceledException();
        boolean res = true;
        if(fileOrDirectory.isDirectory())
            for(File child : fileOrDirectory.listFiles())
                res &= DeleteRecursive(child);

        return res && fileOrDirectory.delete();
    }

    // Gets the base path to the server
    String getServerPath(String subfolder)
    {
        String thing;
        if(subfolder.length()==0)
            thing = Uri.encode(server.getString("foreign_path"));
        else
            thing = Uri.encode(TextUtils.join("/", new String[]{server.getString("foreign_path"), subfolder}));
        return (server.getBoolean("ssl") ? "https://" : "http://") +
                server.getString("host") + ":" + server.getInt("port") + "/" + thing;
    }

    // Appends a folder onto a subfolder
    String joinAppend(String subfolder, String thingToAppend) {
        if(subfolder.length()==0)
            return thingToAppend;
        return TextUtils.join("/", new String[]{subfolder, thingToAppend});
    }

    // Setter
    public void setViewOnScreen(AttachedRelativeLayout view) {
        viewOnScreen = view;
        onProgressUpdate(new Long[0]);
    }

    // Getter
    public PHASE getPhase() {
        return phase;
    }

    // Tests if running
    public boolean isRunning() {
        return phase != Syncer.PHASE.FINISHED && phase != Syncer.PHASE.ERROR && phase != PHASE.CANCELED;
    }

    // (static) Setter
    public static void setStatusTime(Context context, AttachedRelativeLayout view, long time) {
        String current_time = DateUtils.getRelativeDateTimeString(context, time, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString();
        view.status().setText(current_time);
        view.status().setTextColor(context.getResources().getColor(R.color.status_inactive));
        view.button().setText("...");
    }

    // Getter
    public int totalDownloadCount() {
        return filesToDownload.size() - currentDownload;
    }

    // Getter
    public int finishedDownloadCount() {
        return currentDownload;
    }

    // Getter
    public StatusWindow getStatusWindow() {
        return statusWindow;
    }

    // Getter
    public ArrayList<Pair<String, Long>> getDownloads() {
        return filesToDownload;
    }

    // Getter
    public ArrayList<String> getDeletes() {
        return filesToDelete;
    }

    // Getter
    public String getName() {
        return server.getString("name");
    }

    // Task was canceled
    public class CanceledException extends Exception {}
}
