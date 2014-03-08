package com.qweex.eyebrowssync;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import com.qweex.utils.DirectoryChooserDialog;

public class EditServer extends Activity implements DirectoryChooserDialog.OnDirectoryChosen {

    String nameWas;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_activity);

        if(getIntent().getExtras()!=null) {
            nameWas = getIntent().getExtras().getString("name");
            if(nameWas!=null) {
                Bundle b = SavedServers.get(this, nameWas);
                ((EditText)findViewById(R.id.name)).setText(nameWas);
                ((EditText)findViewById(R.id.host)).setText(b.getString("host"));
                ((EditText)findViewById(R.id.port)).setText(Integer.toString(b.getInt("port")));
                ((CheckBox)findViewById(R.id.ssl)).setChecked(b.getBoolean("ssl"));
                //auth (username, password)
                ((EditText)findViewById(R.id.foreign_path)).setText(b.getString("foreign_path"));
                ((Button)findViewById(R.id.local_path)).setText(b.getString("local_path"));
                ((EditText)findViewById(R.id.mask)).setText(b.getString("mask"));
            }
        }

        findViewById(R.id.save).setOnClickListener(save);
        findViewById(R.id.local_path).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DirectoryChooserDialog d = new DirectoryChooserDialog(EditServer.this, EditServer.this);
                d.setNewFolderEnabled(true);
                d.chooseDirectory();
            }
        });
    }

    @Override
    public void onDirectoryChosen(String chosenDir) {
        Log.wtf("EyebrowsSync", chosenDir);
        ((Button)findViewById(R.id.local_path)).setText(chosenDir);
    }


    View.OnClickListener save = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Bundle b = new Bundle();
            String newName = ((EditText)findViewById(R.id.name)).getText().toString();
            b.putString("name", newName);
            b.putString("host", ((EditText)findViewById(R.id.host)).getText().toString());
            b.putInt("port", Integer.parseInt(((EditText)findViewById(R.id.port)).getText().toString()));
            b.putBoolean("ssl", ((CheckBox)findViewById(R.id.ssl)).isChecked());
            //auth (username, password)
            b.putString("foreign_path", ((EditText)findViewById(R.id.foreign_path)).getText().toString());
            b.putString("local_path", ((Button)findViewById(R.id.local_path)).getText().toString());
            b.putString("mask", ((EditText)findViewById(R.id.mask)).getText().toString());

            if(nameWas==null)
                SavedServers.add(EditServer.this, b);
            else
            {
                if(!nameWas.equals(newName)) {
                    ServerList.syncers.put(newName, ServerList.syncers.get(nameWas));
                    ServerList.syncers.remove(nameWas);
                }
                SavedServers.update(EditServer.this, nameWas, b);
            }
            finish();
        }
    };
}