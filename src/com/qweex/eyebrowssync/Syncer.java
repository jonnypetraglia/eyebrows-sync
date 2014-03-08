package com.qweex.eyebrowssync;

import android.app.DownloadManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import com.qweex.eyebrows.EyebrowsError;
import com.qweex.eyebrows.did_not_write.JSONDownloader;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Syncer extends AsyncTask<String, Void, Exception> {
    ArrayList<String> filesToDownload;
    int totalDownloads;
    ArrayList<String> filesToDelete;
    int totalDeletes;
    Bundle server;
    Resources resources;
    enum PHASE {COUNTING, DOWNLOADING, DELETING, ERROR}
    PHASE phase;
    Exception failure;

    public Syncer(String name, TextView statusView) {
        this.statusView = statusView;
        server = SavedServers.get(statusView.getContext(), name);
        filesToDownload = new ArrayList<String>();
        filesToDelete = new ArrayList<String>();
    }

    @Override
    protected void onPreExecute() {
    }

    @Override
    protected void onProgressUpdate(Void... voids) {
        switch(phase) {
            case COUNTING:
                statusView.setText("Changes: +" + filesToDownload.size() + "/-" + filesToDelete.size());
                statusView.setTextColor(resources.getColor(R.color.status_preparing));
                break;
            case DOWNLOADING:
                statusView.setText("Downloading: " + filesToDownload.size() + "/" + totalDownloads);
                statusView.setTextColor(resources.getColor(R.color.status_running));
            case DELETING:
                statusView.setText("Deleting: " + filesToDelete.size() + "/" + totalDeletes);
                //statusView.setTextColor(resources.getColor(R.color.status_running));
            case ERROR:
                statusView.setText("Error: " + failure);
        }

    }


    @Override
    protected Exception doInBackground(String... params) {
        //1. Build list of all files needing to be downloaded (and deleted)
        phase = PHASE.COUNTING;
        try {
            tallyFolder("");
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

        //2. Do the actual downloading
        phase = PHASE.DOWNLOADING;
        totalDownloads = filesToDownload.size();
        File localSubdir = new File(server.getString("local_path"));
        for(String foreignPath : filesToDownload) {
            File localTarget = new File(localSubdir, foreignPath);
            Log.d("EyebrowsSync", "++Downloading: " + foreignPath + " -> " + localTarget);

            Uri downloadUri = Uri.parse(getServerURI() + foreignPath);
            DownloadManager.Request request = new DownloadManager.Request(downloadUri);
            request.addRequestHeader("Authorization", "Basic " + server.getString("auth"));
            request.setDestinationUri(Uri.parse(localTarget.getAbsolutePath()));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
            request.setVisibleInDownloadsUi(false);
            request.allowScanningByMediaScanner();
            //request.setTitle(filename);
            ServerList.downloadManager.enqueue(request);
        }

        //3. Do the deleting
        phase = PHASE.DELETING;
        totalDeletes = filesToDelete.size();
        Log.d("EyebrowsSync", "FilesToDelete: ");
        for(String f : filesToDelete)
            Log.d("EyebrowsSync", "--Deleting: " + f);
        return null;
    }

    @Override
    protected void onPostExecute(Exception e) {
        failure = e;
        if(failure!=null) {
            onProgressUpdate();
            return;
        }
        //TODO: get current timestamp & update it in the DB
        String current_time = "NOW";
        // SavedServers.update(name, ...)
        statusView.setText(current_time);
        statusView.setTextColor(resources.getColor(R.color.status_inactive));
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
        String path_to_load;
        if(subfolder.length()==0)
            path_to_load = server.getString("foreign_path");
        else
            path_to_load = TextUtils.join("/", new String[]{server.getString("foreign_path"), subfolder});
        path_to_load = getServerURI() + Uri.encode(path_to_load);
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

            if(localFiles.contains(foreignFile.getString("name"))) {
                File localFile = new File(localSubdir, foreignFile.getString("name"));
                Log.i("EyebrowsSync", "Examining: " + foreignFile.getString("name"));
                Log.d("EyebrowsSync", foreignFile.getLong("mtime") + " vs " + localFile.lastModified());
                if(localFile.lastModified() < foreignFile.getLong("mtime"))
                    filesToDownload.add(subfolder + "/" + foreignFile.getString("name"));
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

    String getServerURI()
    {

        return (server.getBoolean("ssl") ? "https://" : "http://") +
                server.getString("host") + ":" + server.getInt("port") + "/";
    }


    String joinAppend(String subfolder, String thingToAppend) {
        if(subfolder.length()==0)
            return thingToAppend;
        return TextUtils.join("/", new String[]{subfolder, thingToAppend});
    }

    TextView statusView;

    public void setStatusView(TextView view) {
        statusView = view;
        statusView.setText("Running"); //TODO: Green
    }
}
