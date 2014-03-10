package com.qweex.eyebrowssync;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
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

public class Syncer extends AsyncTask<String, Void, Exception> {
    ArrayList<String> filesToDownload;
    int totalDownloads;
    ArrayList<String> filesToDelete;
    int totalDeletes;
    Bundle server;
    Context context;
    public enum PHASE {PREPARING, COUNTING, DOWNLOADING, DELETING, ERROR, FINISHED}
    PHASE phase = PHASE.PREPARING;
    Exception failure;
    AttachedRelativeLayout viewOnScreen;
    Pattern maskPattern;
    String[] bouncingBall = new String[] {"Ooo", "oOo", "ooO", "oOo"};
    int bouncingIndex = 0;
    int bouncingRate = 4; //Higher == Slower
    boolean onlySimulate;

    public Syncer(Context context, String name, boolean simulate) {
        server = SavedServers.get(context, name);
        filesToDownload = new ArrayList<String>();
        filesToDelete = new ArrayList<String>();
        this.context = context;
        if(server.getString("mask").length()>0)
            maskPattern = Pattern.compile(server.getString("mask"));
        onlySimulate = simulate;
    }

    @Override
    protected void onProgressUpdate(Void... voids) {
        if(viewOnScreen ==null)
            return;
        switch(phase) {
            case COUNTING:
                viewOnScreen.button().setText(bouncingBall[bouncingIndex / bouncingRate]);
                viewOnScreen.status().setText("Changes: +" + filesToDownload.size() + "/-" + filesToDelete.size());
                viewOnScreen.status().setTextColor(context.getResources().getColor(R.color.status_preparing));
                break;
            case DOWNLOADING:
                viewOnScreen.button().setText(bouncingBall[bouncingIndex / bouncingRate]);
                viewOnScreen.status().setText("Downloading: " + (totalDownloads - filesToDownload.size()) + "/" + totalDownloads);
                viewOnScreen.status().setTextColor(context.getResources().getColor(R.color.status_running));
                break;
            case DELETING:
                viewOnScreen.button().setText(bouncingBall[bouncingIndex / bouncingRate]);
                viewOnScreen.status().setText("Deleting: " + (totalDeletes - filesToDelete.size()) + "/" + totalDeletes);
                viewOnScreen.status().setTextColor(context.getResources().getColor(R.color.status_running));
                break;
            case ERROR:
                viewOnScreen.button().setText(bouncingBall[bouncingIndex / bouncingRate]);
                viewOnScreen.status().setText("Error: " + failure);
                viewOnScreen.status().setTextColor(context.getResources().getColor(R.color.status_error));
                break;
        }
        bouncingIndex = (bouncingIndex+1) % (bouncingBall.length*bouncingRate);
    }


    @Override
    protected Exception doInBackground(String... params) {
        try {
            //1. Build list of all files needing to be downloaded (and deleted)
            phase = PHASE.COUNTING;
            tallyFolder("");
            if(onlySimulate)
                return null;


            //2. Do the actual downloading
            phase = PHASE.DOWNLOADING;
            totalDownloads = filesToDownload.size();
            File localSubdir = new File(server.getString("local_path"));
            while(!filesToDownload.isEmpty()) {
                String foreignPath = filesToDownload.remove(0);
                File localTarget = new File(localSubdir, foreignPath);
                downloadFile(getServerPath(foreignPath), localTarget);
            }

            //3. Do the deleting
            phase = PHASE.DELETING;
            totalDeletes = filesToDelete.size();
            while(!filesToDelete.isEmpty()) {
                String f = filesToDelete.remove(0);
                File localTarget = new File(localSubdir, f);
                boolean r = DeleteRecursive(localTarget);
                if(localTarget.exists() && r)
                    MediaScannerConnection.scanFile(context, new String[]{localTarget.getPath()}, null, null);
                Log.d("EyebrowsSync", "---Deleting? " + r);
            }

        } catch (EyebrowsError eyebrowsError) {
            //TODO
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

    boolean DeleteRecursive(File fileOrDirectory) throws CanceledException {
        if(isCancelled())
            throw new CanceledException();
        boolean res = true;
        if(fileOrDirectory.isDirectory())
            for(File child : fileOrDirectory.listFiles())
                res &= DeleteRecursive(child);

        Log.d("EyebrowsSync", "--Deleting: " + fileOrDirectory);
        return res && fileOrDirectory.delete();
    }

    @Override
    protected void onPostExecute(Exception e) {
        failure = e;
        if(failure!=null) {
            phase = PHASE.ERROR;
            onProgressUpdate();
            return;
        }
        phase = PHASE.FINISHED;

        long time;
        if(!onlySimulate && !isCancelled()) {
            time = System.currentTimeMillis();
            Bundle b = new Bundle();
            b.putLong("last_updated", time);
            SavedServers.update(context, server.getString("name"), b);
        } else {
            Bundle b = SavedServers.get(context, server.getString("name"));
            time = b.getLong("last_updated");
        }
        String current_time = DateUtils.getRelativeDateTimeString(context, time, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString();
        if(viewOnScreen !=null) {
            viewOnScreen.status().setText(current_time);
            viewOnScreen.status().setTextColor(context.getResources().getColor(R.color.status_inactive));
            viewOnScreen.button().setText("...");
            viewOnScreen.attachTo(null);
        }
    }

    @Override
    protected void onCancelled(Exception e) {
        onPostExecute(e);
    }

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
                    filesToDownload.add(joinAppend(subfolder, foreignFile.getString("name")));
                localFiles.remove(foreignFile.getString("name"));
            } else
                filesToDownload.add(joinAppend(subfolder, foreignFile.getString("name")));
        }

        //4. foreach file still in local_files
        for(String localFile : localFiles)
            filesToDelete.add(joinAppend(subfolder, localFile));
        publishProgress();

        //5. foreach subfolder in foreign_files
        for(String foreignDir : foreignDirs)
            tallyFolder(joinAppend(subfolder, foreignDir));
    }

    void downloadFile(String url, File target) throws IOException, CanceledException {
        Log.d("EyebrowsSync", "++Downloading: " + url + " -> " + target);

        HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();
        urlConnection.setRequestMethod("GET");
        urlConnection.setRequestProperty("Authentication", "Basic " + server.getString("auth"));
        urlConnection.setUseCaches(false);
        urlConnection.setDoOutput(false);
        urlConnection.connect();

        InputStream inputStream = urlConnection.getInputStream();
        target.getParentFile().mkdirs();
        FileOutputStream fileOutput = new FileOutputStream(target);

        byte[] buffer = new byte[1024];
        int bufferLength = 0;
        int bucket = 0;
        int updateInterval = 1024*1024; //Bigger == less often

        while ( (bufferLength = inputStream.read(buffer)) > 0 ) {
            bucket += bufferLength;
            if(bucket>updateInterval) {
                bucket = bucket % updateInterval;
                publishProgress();
                if(isCancelled()) {
                    fileOutput.close();
                    target.delete();
                    throw new CanceledException();
                }
            }
            fileOutput.write(buffer, 0, bufferLength);
        }
        MediaScannerConnection.scanFile(context, new String[]{target.getPath()}, null, null);
        fileOutput.close();
    }

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


    String joinAppend(String subfolder, String thingToAppend) {
        if(subfolder.length()==0)
            return thingToAppend;
        return TextUtils.join("/", new String[]{subfolder, thingToAppend});
    }

    public void setViewOnScreen(AttachedRelativeLayout view) {
        viewOnScreen = view;
        onProgressUpdate();
    }

    public PHASE getPhase() {
        return phase;
    }

    public boolean isRunning() {
        return phase != Syncer.PHASE.FINISHED && phase != Syncer.PHASE.ERROR;
    }

    public class CanceledException extends Exception {}
}
