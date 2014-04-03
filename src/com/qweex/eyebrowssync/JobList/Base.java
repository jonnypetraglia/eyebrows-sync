package com.qweex.eyebrowssync.JobList;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.CursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import com.qweex.eyebrowssync.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public abstract class Base extends ListActivity {
    AttachedRelativeLayout rowClicked;
    String nameOfClicked() { return ((TextView)rowClicked.findViewById(R.id.title)).getText().toString(); }
    ArrayList<SimpleMenuItem> runMenu = new ArrayList<SimpleMenuItem>(), editMenu = new ArrayList<SimpleMenuItem>();

    public static HashMap<String, Syncer> syncers;
    NotificationSupervisor supervisor;

    abstract void showItemMenu(View v, String name, ArrayList<SimpleMenuItem> menuItems);

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

        Resources r = getResources();
        runMenu.add(new SimpleMenuItem(R.id.cancel, r.getString(R.string.cancel)));
        runMenu.add(new SimpleMenuItem(R.id.status, r.getString(R.string.status)));
        editMenu.add(new SimpleMenuItem(R.id.run, r.getString(R.string.run)));
        editMenu.add(new SimpleMenuItem(R.id.simulate, r.getString(R.string.simulate)));
        editMenu.add(new SimpleMenuItem(R.id.status, r.getString(R.string.status)));
        editMenu.add(new SimpleMenuItem(R.id.edit, r.getString(R.string.edit)));
        editMenu.add(new SimpleMenuItem(R.id.delete, r.getString(R.string.delete)));
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
        Log.d("EyebrowsSync", "HERP");
        u.add(0, CREATE_NEW_ID, 0, R.string.create);
        u.add(0, RUN_ALL_ID, 0, R.string.run_all);
        u.add(0, CANCEL_ALL_ID, 0, R.string.cancel_all);
        u.add(0, ABOUT_ID, 0, R.string.about);
        return super.onCreateOptionsMenu(u);
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

    // Called when an options menu is selected
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch(item.getItemId())
        {
            case CREATE_NEW_ID:
                startActivity(new Intent(Base.this, EditJob.class));
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
                startActivity(new Intent(Base.this, AboutActivity.class));
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
            v.findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    rowClicked = (AttachedRelativeLayout) v.getParent();
                    String name = ((TextView)rowClicked.findViewById(R.id.title)).getText().toString();
                    if(syncers.get(name)!=null && syncers.get(name).isRunning())
                        showItemMenu(v, name, new ArrayList<SimpleMenuItem>(runMenu));
                    else
                        showItemMenu(v, name, new ArrayList<SimpleMenuItem>(editMenu));
                }
            });
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
                Syncer.setStatusTime(Base.this, (AttachedRelativeLayout) v, time);
        }
    }

    public void handleMenuItemClick(int id) {
        switch(id) {
            // Not Running
            case R.id.run:
            case R.id.simulate:
                runJob(nameOfClicked(), id==R.id.simulate, rowClicked);
                break;
            case R.id.edit:
                Intent i = new Intent(Base.this, EditJob.class);
                i.putExtra("name", nameOfClicked());
                startActivity(i);
                break;
            case R.id.delete:
                new AlertDialog.Builder(Base.this)
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
            default:
                Toast.makeText(this, "Uh, I dunno what to do", Toast.LENGTH_SHORT).show();
        }
    }

    class SimpleMenuItem {
        int id;
        String title;
        boolean visible;

        public SimpleMenuItem(int i, String t) {
            id = i;
            title = t;
            visible = true;
        }

        public int getItemId() { return id ; }
        public String getTitle() { return title; }
        public boolean getVisible() { return visible; }
        public void setVisible(boolean v) { visible = v; }
    }
}
