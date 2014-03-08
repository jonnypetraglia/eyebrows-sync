package com.qweex.eyebrowssync;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.HashMap;

public class ServerList extends ListActivity implements PopupMenu.OnMenuItemClickListener {
    RelativeLayout rowClicked;
    String nameOfClicked() { return ((TextView)rowClicked.findViewById(R.id.title)).getText().toString(); }

    static HashMap<String, Syncer> syncers;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SavedServers.initialize(this);

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
                SavedServers.getAll(),
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
                clas = EditServer.class;
                break;
            default:
                return false;
        }
        i = new Intent(ServerList.this, clas);
        startActivityForResult(i, clas.hashCode() % 0xffff);
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {

        switch(item.getItemId()) {
            case R.id.run:
                StatusTextView statusView = (StatusTextView) rowClicked.findViewById(R.id.status);
                Syncer newSyncer = new Syncer(this, nameOfClicked());
                statusView.attachTo(newSyncer);
                newSyncer.execute();
                syncers.put(nameOfClicked(), newSyncer);
                break;
            case R.id.edit:
                Intent i = new Intent(ServerList.this, EditServer.class);
                i.putExtra("name", nameOfClicked());
                startActivity(i);
                break;
            case R.id.delete:
                new AlertDialog.Builder(ServerList.this)
                        .setTitle(nameOfClicked())
                        .setMessage(R.string.confirm_delete)
                        .setNegativeButton(R.string.no, null)
                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                SavedServers.remove(nameOfClicked());
                                onResume();
                            }
                        })
                        .show();
                break;
        }

        return false;
    }



    View.OnClickListener showPopup = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            rowClicked = (RelativeLayout) v.getParent();
            PopupMenu popupMenu = new PopupMenu(ServerList.this, v);
            popupMenu.getMenuInflater().inflate(R.menu.edit_menu, popupMenu.getMenu());
            popupMenu.setOnMenuItemClickListener(ServerList.this);
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
                v.findViewById(R.id.show_menu).setOnClickListener(showPopup);


            String name = ((TextView)v.findViewById(R.id.title)).getText().toString();
            StatusTextView statusView = (StatusTextView) v.findViewById(R.id.status);
            if(syncers.get(name)!=null)
                syncers.get(name).setStatusView(statusView);
            else if(statusView.getText().toString().length()==0)
                statusView.setText("(Never run)");
            else {
                long time = Long.parseLong(statusView.getText().toString());
                statusView.setText(
                        DateUtils.getRelativeDateTimeString(v.getContext(), time, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0)
                );
            }
            return v;
        }
    }
}
