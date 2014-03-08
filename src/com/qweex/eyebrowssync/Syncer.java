package com.qweex.eyebrowssync;

import android.content.Context;
import android.content.Intent;
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
    enum PHASE {PREPARING, COUNTING, DOWNLOADING, DELETING, ERROR}
    PHASE phase = PHASE.PREPARING;
    Exception failure;
    StatusTextView statusView;
    Pattern maskPattern;
    String[] bouncingBall = new String[] {"(O o o)", "(o O o)", "(o o O)", "(o O o)"};
    int bouncingIndex = 0;
    int bouncingRate = 4; //Higher == Slower

    public Syncer(Context context, String name) {
        server = SavedServers.get(context, name);
        filesToDownload = new ArrayList<String>();
        filesToDelete = new ArrayList<String>();
        this.context = context;
        if(server.getString("mask").length()>0)
            maskPattern = Pattern.compile(server.getString("mask"));
    }

    @Override
    protected void onProgressUpdate(Void... voids) {
        if(statusView==null)
            return;
        switch(phase) {
            case COUNTING:
                statusView.setText(bouncingBall[bouncingIndex/bouncingRate] + " Changes: +" + filesToDownload.size() + "/-" + filesToDelete.size());
                statusView.setTextColor(context.getResources().getColor(R.color.status_preparing));
                break;
            case DOWNLOADING:
                statusView.setText(bouncingBall[bouncingIndex/bouncingRate] + " Downloading: " + (totalDownloads-filesToDownload.size()) + "/" + totalDownloads);
                statusView.setTextColor(context.getResources().getColor(R.color.status_running));
                break;
            case DELETING:
                statusView.setText(bouncingBall[bouncingIndex/bouncingRate] + " Deleting: " + (totalDeletes-filesToDelete.size()) + "/" + totalDeletes);
                statusView.setTextColor(context.getResources().getColor(R.color.status_running));
                break;
            case ERROR:
                statusView.setText("Error: " + failure);
                statusView.setTextColor(context.getResources().getColor(R.color.status_error));
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
            Log.d("EyebrowsSync", "FilesToDelete: ");
            for(String f : filesToDelete)
                Log.d("EyebrowsSync", "--Deleting: " + f);

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
        }
        return null;
    }

    @Override
    protected void onPostExecute(Exception e) {
        failure = e;
        if(failure!=null) {
            phase = PHASE.ERROR;
            onProgressUpdate();
            return;
        }

        MediaScannerConnection.scanFile(context, new String[]{server.getString("local_path")}, null, null);

        long time = System.currentTimeMillis();
        Bundle b = new Bundle();
        b.putLong("last_updated", time);
        SavedServers.update(context, server.getString("name"), b);
        String current_time = DateUtils.getRelativeDateTimeString(context, time, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString();
        if(statusView!=null) {
            statusView.setText(current_time);
            statusView.setTextColor(context.getResources().getColor(R.color.status_inactive));
            statusView.attachTo(null);
        }
        ServerList.syncers.remove(server.getString("name"));
    }

    void tallyFolder(String subfolder) throws EyebrowsError, IOException, JSONException {
        Log.i("EyebrowsSync", "Tallying: " + subfolder);
        List<String> foreignDirs = new ArrayList<String>();
        File localSubdir = new File(server.getString("local_path"));
        localSubdir = new File(localSubdir, subfolder);

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

        //4. foreach file stil lin local_files
        for(String localFile : localFiles)
            filesToDelete.add(joinAppend(subfolder, localFile));
        publishProgress();

        //5. foreach subfolder in foreign_files
        for(String foreignDir : foreignDirs)
            tallyFolder(joinAppend(subfolder, foreignDir));
    }

    void downloadFile(String url, File target) throws IOException {
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

        while ( (bufferLength = inputStream.read(buffer)) > 0 ) {
            bucket += bufferLength;
            if(bucket>1024)
                publishProgress();
            fileOutput.write(buffer, 0, bufferLength);
        }
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

    public void setStatusView(StatusTextView view) {
        statusView = view;
        onProgressUpdate();
    }
}
