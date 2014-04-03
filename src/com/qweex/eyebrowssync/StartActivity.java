package com.qweex.eyebrowssync;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.qweex.eyebrowssync.JobList.v11;
import com.qweex.eyebrowssync.JobList.v3;

public class StartActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //android.os.Build.VERSION_CODES.HONEYCOMB
        if(android.os.Build.VERSION.SDK_INT >= 11)
            startActivity(new Intent(this, v11.class));
        else
            startActivity(new Intent(this, v3.class));
        finish();
    }
}
