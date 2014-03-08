package com.qweex.eyebrowssync;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

public class StatusTextView extends TextView {
    Syncer currentSyncer;
    public StatusTextView(Context context) {
        super(context);
    }

    public StatusTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StatusTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void attachTo(Syncer s) {
        if(currentSyncer!=null)
            currentSyncer.setStatusView(null);
        currentSyncer=s;
        if(currentSyncer!=null)
            currentSyncer.setStatusView(this);
    }
}
