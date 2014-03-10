package com.qweex.eyebrowssync;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.HashMap;

public class JobList extends ListActivity implements PopupMenu.OnMenuItemClickListener {
    AttachedRelativeLayout rowClicked;
    String nameOfClicked() { return ((TextView)rowClicked.findViewById(R.id.title)).getText().toString(); }

    static HashMap<String, Syncer> syncers;

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
    }

    @Override
    public void onResume() {
        super.onResume();
        CursorAdapter cursorAdapter = new SimplishCursorAdapter(this,
                R.layout.server_item,
                SavedJobs.getAll(),
                new String[] {"name", "last_updated"},
                new int[] { R.id.title, R.id.status});
        setListAdapter(cursorAdapter);
    }

    // Creates options menu
    @Override
    public boolean onCreateOptionsMenu(Menu u) {
        u.add(0, 0, 0, R.string.create);
        return super.onCreateOptionsMenu(u);
    }

    // Called when an options menu is selected
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        Intent i = null;
        Class<?> clas = null;
        switch(item.getItemId())
        {
            case 0: //create
                clas = EditJob.class;
                break;
            default:
                return false;
        }
        i = new Intent(JobList.this, clas);
        startActivityForResult(i, clas.hashCode() % 0xffff);
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {

        switch(item.getItemId()) {
            // Not Running
            case R.id.run:
            case R.id.simulate:
                if(syncers.containsKey(nameOfClicked())) {
                    if(syncers.get(nameOfClicked()).isRunning())
                        break;
                    syncers.remove(nameOfClicked());
                }

                Syncer newSyncer = new Syncer(this, nameOfClicked(), item.getItemId()==R.id.simulate);
                rowClicked.attachTo(newSyncer);
                newSyncer.execute();
                syncers.put(nameOfClicked(), newSyncer);
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
            case R.id.log:
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

            popupMenu.setOnMenuItemClickListener(JobList.this);
            popupMenu.show();
        }
    };

    class SimplishCursorAdapter extends SimpleCursorAdapter {

        public SimplishCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
            super(context, layout, c, from, to);
        }

        public SimplishCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to, int flags) {
            super(context, layout, c, from, to, flags);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            boolean setListener = convertView == null;
            View v = super.getView(position, convertView, parent);

            if(setListener)
                v.findViewById(R.id.button).setOnClickListener(showPopup);


            String name = ((TextView)v.findViewById(R.id.title)).getText().toString();
            TextView statusView = (TextView) v.findViewById(R.id.status);
            if(syncers.get(name)!=null)
                syncers.get(name).setViewOnScreen((AttachedRelativeLayout) v);
            else if(statusView.getText().toString().length()==0)
                statusView.setText(R.string.never_run);
            else
                Syncer.setStatusTime(JobList.this, name, (AttachedRelativeLayout) v);
            return v;
        }
    }
}
