package com.qweex.eyebrowssync;

import android.app.*;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.support.v4.app.NotificationCompat;

import java.util.HashMap;
import java.util.Set;

public class JobList extends ListActivity implements PopupMenu.OnMenuItemClickListener {
    AttachedRelativeLayout rowClicked;
    String nameOfClicked() { return ((TextView)rowClicked.findViewById(R.id.title)).getText().toString(); }

    static HashMap<String, Syncer> syncers;
    NotificationSupervisor supervisor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SavedJobs.initialize(this);

        getListView().setBackgroundColor(getResources().getColor(android.R.color.white));
        ((View)getListView().getParent()).setBackgroundColor(getResources().getColor(android.R.color.white));

        getListView().setEmptyView(getLayoutInflater().inflate(R.layout.empty_servers, null));
        ((ViewGroup)getListView().getParent()).addView(getListView().getEmptyView());
        getListView().setDivider(getResources().getDrawable(R.drawable.divider));
        getListView().setDividerHeight(3);

        if(syncers == null)
            syncers = new HashMap<String, Syncer>();
        supervisor = new NotificationSupervisor(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        CursorAdapter cursorAdapter = new SimplishCursorAdapter(this, SavedJobs.getAll());
        setListAdapter(cursorAdapter);
    }

    // Creates options menu
    final int CREATE_NEW_ID = 0, RUN_ALL_ID = 1, CANCEL_ALL_ID = 2, ABOUT_ID = 3;
    @Override
    public boolean onCreateOptionsMenu(Menu u) {
        u.add(0, CREATE_NEW_ID, 0, R.string.create);
        u.add(0, RUN_ALL_ID, 0, R.string.run_all);
        u.add(0, CANCEL_ALL_ID, 0, R.string.cancel_all);
        u.add(0, ABOUT_ID, 0, R.string.about);
        return super.onCreateOptionsMenu(u);
    }

    // Called when an options menu is selected
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch(item.getItemId())
        {
            case CREATE_NEW_ID:
                startActivity(new Intent(JobList.this, EditJob.class));
                break;
            case RUN_ALL_ID:
                Cursor c = SavedJobs.getAll();
                if(c.getCount()>0)
                    c.moveToFirst();
                while(!c.isAfterLast()) {
                    String name = c.getString(c.getColumnIndex("name"));

                    //TODO: This sucks
                    AttachedRelativeLayout v = null;
                    for(int i=0; i<getListView().getChildCount(); i++) {
                        v = (AttachedRelativeLayout) getListView().getChildAt(i);
                        if(((TextView)(v.findViewById(R.id.title))).getText().toString().equals(name))
                            break;
                    }

                    runJob(name, false, v);
                    c.moveToNext();
                }
                break;
            case CANCEL_ALL_ID:
                Set<String> keys = syncers.keySet();
                for(String key : keys) {
                    Syncer syncer = syncers.get(key);
                    if(syncer == null)
                        continue;
                    syncer.cancel(false);
                }
                break;
            case ABOUT_ID:
                startActivity(new Intent(JobList.this, AboutActivity.class));
                break;
            default:
                return false;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        if(menu==null) {
            return super.onMenuOpened(featureId, menu);
        }
        boolean noneRunning = true;
        boolean allRunning = true;
        Cursor c = SavedJobs.getAll();
        if(c.getCount()>0)
            c.moveToFirst();
        while(!c.isAfterLast()) {
            String name = c.getString(c.getColumnIndex("name"));
            if(!syncers.containsKey(name) || syncers.get(name)==null)
                allRunning = false; //We found a stopped one!
            else
                noneRunning &= !syncers.get(name).isRunning();
            c.moveToNext();
        }
        menu.findItem(RUN_ALL_ID).setEnabled(!allRunning);
        menu.findItem(CANCEL_ALL_ID).setEnabled(!noneRunning);
        return super.onMenuOpened(featureId, menu);
    }

    void runJob(String name, boolean simulate, AttachedRelativeLayout view) {
        if(syncers.containsKey(name)) {
            if(syncers.get(name).isRunning())
                return;
            syncers.remove(name);
        }

        Syncer newSyncer = new Syncer(this, name, simulate, supervisor);
        view.attachTo(newSyncer);
        newSyncer.prepareYourDiddlyHole();
        if(Build.VERSION.SDK_INT >= 11) //HONEYCOMB
            newSyncer.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        else
            newSyncer.execute();
        syncers.put(name, newSyncer);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {

        switch(item.getItemId()) {
            // Not Running
            case R.id.run:
            case R.id.simulate:
                runJob(nameOfClicked(), item.getItemId()==R.id.simulate, rowClicked);
                break;
            case R.id.edit:
                Intent i = new Intent(JobList.this, EditJob.class);
                i.putExtra("name", nameOfClicked());
                startActivity(i);
                break;
            case R.id.delete:
                new AlertDialog.Builder(JobList.this)
                        .setTitle(nameOfClicked())
                        .setMessage(R.string.confirm_delete)
                        .setNegativeButton(R.string.no, null)
                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                SavedJobs.remove(nameOfClicked());
                                onResume();
                            }
                        })
                        .show();
                break;
            case R.id.status:
                if(syncers.containsKey(nameOfClicked())) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            syncers.get(nameOfClicked()).getStatusWindow().show();
                        }
                    });
                }
                break;
            // Running
            case R.id.cancel:
                if(syncers.containsKey(nameOfClicked())) {
                    syncers.get(nameOfClicked()).cancel(false);
                }
                break;
        }

        return false;
    }



    View.OnClickListener showPopup = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            rowClicked = (AttachedRelativeLayout) v.getParent();

            PopupMenu popupMenu = new PopupMenu(JobList.this, v);


            String name = ((TextView)rowClicked.findViewById(R.id.title)).getText().toString();
            if(syncers.get(name)!=null && syncers.get(name).isRunning())
                popupMenu.getMenuInflater().inflate(R.menu.run_menu, popupMenu.getMenu());
            else
                popupMenu.getMenuInflater().inflate(R.menu.edit_menu, popupMenu.getMenu());

            popupMenu.getMenu().findItem(R.id.status).setVisible(syncers.get(name)!=null);

            popupMenu.setOnMenuItemClickListener(JobList.this);
            popupMenu.show();
        }
    };

    class SimplishCursorAdapter extends CursorAdapter {
        private LayoutInflater mLayoutInflater;
        private Context mContext;

        public SimplishCursorAdapter(Context context, Cursor c) {
            super(context, c);
            this.mContext = context;
            this.mLayoutInflater = LayoutInflater.from(context);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v = mLayoutInflater.inflate(R.layout.server_item, parent, false);
            v.findViewById(R.id.button).setOnClickListener(showPopup);
            return v;
        }

        @Override
        public void bindView(View v, Context context, Cursor c) {
            String name = c.getString(c.getColumnIndex("name"));
            long time = c.getLong(c.getColumnIndex("last_updated"));

            ((TextView)v.findViewById(R.id.title)).setText(name);

            TextView statusView = (TextView) v.findViewById(R.id.status);
            if(syncers.get(name)!=null)
                syncers.get(name).setViewOnScreen((AttachedRelativeLayout) v);
            else if(time==0)
                statusView.setText(R.string.never_run);
            else
                Syncer.setStatusTime(JobList.this, (AttachedRelativeLayout) v, time);
        }
    }


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
            Set<String> keys = syncers.keySet();
            for(String key : keys) {
                Syncer syncer = syncers.get(key);
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
}
