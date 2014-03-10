package com.qweex.eyebrowssync;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class AttachedRelativeLayout extends RelativeLayout {
    Syncer currentSyncer;
    public AttachedRelativeLayout(Context context) {
        super(context);
    }

    public AttachedRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AttachedRelativeLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void attachTo(Syncer s) {
        if(currentSyncer!=null)
            currentSyncer.setViewOnScreen(null);
        currentSyncer=s;
        if(currentSyncer!=null)
            currentSyncer.setViewOnScreen(this);
    }

    public TextView status() {
        return (TextView) findViewById(R.id.status);
    }

    public Button button() {
        return (Button) findViewById(R.id.button);
    }
}
