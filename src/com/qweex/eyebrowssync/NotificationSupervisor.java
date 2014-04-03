package com.qweex.eyebrowssync;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import com.qweex.eyebrowssync.JobList.Base;

import java.util.Set;

public class NotificationSupervisor {
    NotificationManager manager;
    Activity activity;
    NotificationCompat.Builder mBuilder;
    final int NOTIFICATION_ID = 1337;

    public NotificationSupervisor(Activity activity) {
        manager = (NotificationManager) activity.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        this.activity = activity;
        Intent intent = activity.getIntent();
        PendingIntent pIntent = PendingIntent.getActivity(activity, 0, intent, 0);
        mBuilder = new NotificationCompat.Builder(activity)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(pIntent)
                .setOngoing(true);
    }

    public void update() {
        int activeSyncers = 0, filesTotal = 0, filesDone = 0;
        Syncer.PHASE targetPhase = Syncer.PHASE.CANCELED; //DOWNLOADING, PREPARING/COUNTING, DELETING/ERROR/FINISHED
        Set<String> keys = Base.syncers.keySet();
        for(String key : keys) {
            Syncer syncer = Base.syncers.get(key);
            if(syncer == null)
                continue;
            activeSyncers++;
            filesTotal += syncer.totalDownloadCount();
            filesDone += syncer.finishedDownloadCount();
            if(targetPhase== Syncer.PHASE.DOWNLOADING)
                continue;
            if(syncer.getPhase() == Syncer.PHASE.DOWNLOADING)
                targetPhase = syncer.getPhase();
            else if((targetPhase==Syncer.PHASE.DELETING || targetPhase==Syncer.PHASE.ERROR || targetPhase==Syncer.PHASE.FINISHED || targetPhase==Syncer.PHASE.CANCELED) &&
                    (syncer.getPhase()==Syncer.PHASE.PREPARING || syncer.getPhase()==Syncer.PHASE.COUNTING))
                targetPhase = syncer.getPhase();
            else if((targetPhase==Syncer.PHASE.DELETING || targetPhase==Syncer.PHASE.ERROR || targetPhase==Syncer.PHASE.FINISHED) &&
                    (syncer.getPhase()==Syncer.PHASE.DELETING || syncer.getPhase()==Syncer.PHASE.ERROR || syncer.getPhase()==Syncer.PHASE.FINISHED))
                targetPhase = syncer.getPhase();
        }

        if(targetPhase==Syncer.PHASE.FINISHED || targetPhase==Syncer.PHASE.ERROR || targetPhase==Syncer.PHASE.CANCELED)
        {
            PendingIntent pIntent = PendingIntent.getActivity(activity, 0, activity.getIntent(), 0);
            mBuilder = new NotificationCompat.Builder(activity)
                    .setContentTitle("Finished Syncing")
                    .setProgress(0, 0, false)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setOngoing(false)
                    .setContentIntent(pIntent);
            if(targetPhase==Syncer.PHASE.ERROR)
                mBuilder.setContentInfo("Some jobs had errors");
            else
                mBuilder.setContentInfo(null);
            manager.notify(NOTIFICATION_ID, mBuilder.build());
            return;
        }

        mBuilder.setContentTitle("Syncing " + activeSyncers + " job" + (activeSyncers>1 ? "s" : ""));

        switch(targetPhase) {
            case DOWNLOADING:
                mBuilder.setContentInfo("Downloading " + (filesTotal-filesDone) + " files")
                        .setProgress(filesDone, filesTotal, false);
                break;
            case PREPARING:
            case COUNTING:
                mBuilder.setContentInfo("Preparing...")
                        .setProgress(0, 1, true);
                break;
            case DELETING:
                mBuilder.setContentInfo("Finishing...")
                        .setProgress(0, 1, true);
            case CANCELED:
                mBuilder.setContentInfo("Canceling...")
                        .setProgress(0, 1, true);
        }

        manager.notify(NOTIFICATION_ID, mBuilder.build());
    }

}
