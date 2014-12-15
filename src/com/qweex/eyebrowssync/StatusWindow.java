package com.qweex.eyebrowssync;


import android.app.Dialog;
import android.content.Context;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;

import java.io.File;

public class StatusWindow extends Dialog {
    ListView lv;
    TextView footer;
    Syncer syncer;
    long currentProgressVal;
    ProgressBar currentProgress;

    public StatusWindow(Context context, Syncer syncer) {
        super(context);
        this.syncer = syncer;

        setContentView(R.layout.status_window);
        setTitle(syncer.getName());

        lv = (ListView) findViewById(android.R.id.list);
        footer = (TextView) findViewById(R.id.footer);
        lv.setAdapter(new Adapter(getContext(), R.layout.status_item));


        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.height = WindowManager.LayoutParams.FILL_PARENT;
        params.width  = WindowManager.LayoutParams.FILL_PARENT;
        getWindow().setAttributes(params);
    }

    public void refreshAdapter() {
        ((Adapter)lv.getAdapter()).notifyDataSetChanged();
    }

    @Override
    public void show() {
        refreshAdapter();
        super.show();
    }

    public void updateCurrentProgress(long currentProg) {
        currentProgressVal = currentProg;
        if(currentProgress!=null)
            currentProgress.setProgress((int) (currentProgressVal / 1000));
    }

    class Adapter extends ArrayAdapter {
        int resource;
        int COLOR_RUNNING, COLOR_INACTIVE, COLOR_PREPARING;

        public Adapter(Context context, int resource) {
            super(context, resource);
            this.resource = resource;
            COLOR_RUNNING = getContext().getResources().getColor(R.color.status_running);
            COLOR_INACTIVE = getContext().getResources().getColor(R.color.status_inactive);
            COLOR_PREPARING = getContext().getResources().getColor(R.color.status_preparing);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent){
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(resource, null);
            }
            TextView name = (TextView) convertView.findViewById(android.R.id.text1);
            ProgressBar progress = (ProgressBar) convertView.findViewById(android.R.id.progress);
            ImageView imageView = (ImageView) convertView.findViewById(R.id.image);

            //header
            if(position == 0) {
                name.setText("Scanning for changes...");
                name.setTextColor(COLOR_INACTIVE);
                progress.setVisibility(View.GONE);
                imageView.setVisibility(View.INVISIBLE);
                return convertView;
            }
            //footer
            if(position == getCount()-1) {
                name.setText("Finished sync");
                if(position == syncer.currentDownload) {
                    name.setTextColor(COLOR_RUNNING);
                } else {
                    name.setTextColor(COLOR_INACTIVE);
                }
                progress.setVisibility(View.GONE);
                imageView.setVisibility(View.INVISIBLE);
                return convertView;
            }
            position--;
            //Download
            if(position < syncer.getDownloads().size()) {
                Pair<String, Long> download = syncer.getDownloads().get(position);
                String filename = new File("/" + download.first).getName();
                name.setText(filename);
                imageView.setVisibility(View.VISIBLE);
                imageView.setImageResource(android.R.drawable.stat_sys_download_done);
                if(position == syncer.currentDownload)
                {
                    //ACTIVE
                    name.setTextColor(COLOR_RUNNING);
                    imageView.setBackgroundColor(COLOR_RUNNING);
                    progress.setVisibility(View.VISIBLE);
                    currentProgress = progress;
                    currentProgress.setMax((int) (download.second / 1000));
                    updateCurrentProgress(currentProgressVal);
                } else {
                    progress.setVisibility(View.GONE);
                    if(position < syncer.currentDownload) {
                        //COMPLETED
                        name.setTextColor(COLOR_INACTIVE);
                        imageView.setBackgroundColor(COLOR_INACTIVE);
                    } else {
                        //FUTURE
                        name.setTextColor(COLOR_PREPARING);
                        imageView.setBackgroundColor(COLOR_PREPARING);
                    }
                }
            // Deletes
            } else {
                position -= syncer.getDownloads().size();
                progress.setVisibility(View.GONE);
                String delete = syncer.getDeletes().get(position);
                String filename = new File("/" + delete).getName();
                currentProgress = null;
                name.setText(filename);
                imageView.setImageResource(android.R.drawable.ic_delete);
                if(position == syncer.currentDelete && syncer.getPhase()==Syncer.PHASE.DELETING)
                {
                    //ACTIVE
                    name.setTextColor(COLOR_RUNNING);
                    imageView.setBackgroundColor(COLOR_RUNNING);
                } else if(position < syncer.currentDelete) {
                    //COMPLETED
                    name.setTextColor(COLOR_INACTIVE);
                    imageView.setBackgroundColor(COLOR_INACTIVE);
                } else {
                    //FUTURE
                    name.setTextColor(COLOR_PREPARING);
                    imageView.setBackgroundColor(COLOR_PREPARING);
                }
            }

            //IF position == syncer.current

            return convertView;
        }

        @Override
        public int getCount() {
            if(syncer.getPhase()== Syncer.PHASE.PREPARING || syncer.getPhase()==Syncer.PHASE.COUNTING)
                return 1;
            return 1 + syncer.getDownloads().size() + syncer.getDeletes().size() + 1;
        }

    }
}
