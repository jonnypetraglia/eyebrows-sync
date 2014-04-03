package com.qweex;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.qweex.eyebrowssync.R;

/**
 * A {@link DialogPreference} that provides a user with the means to select an integer from a {@link NumberPicker}, and persist it.
 * Forked from the original version by lukehorvat to include a widget for < HONEYCOMB devices
 *
 * @author notbryant
 *
 */
public class NumberPickerDialogPreference extends DialogPreference
{
    private static final int DEFAULT_MIN_VALUE = 0;
    private static final int DEFAULT_MAX_VALUE = 100;
    private static final int DEFAULT_VALUE = 0;

    private int mMinValue;
    private int mMaxValue;
    private int mValue;
    private View mNumberPicker;

    public NumberPickerDialogPreference(Context context)
    {
        this(context, null);
    }

    public NumberPickerDialogPreference(Context context, AttributeSet attrs)
    {
        super(context, attrs);

// get attributes specified in XML
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.NumberPickerDialogPreference, 0, 0);
        try
        {
            setMinValue(a.getInteger(R.styleable.NumberPickerDialogPreference_min, DEFAULT_MIN_VALUE));
            setMaxValue(a.getInteger(R.styleable.NumberPickerDialogPreference_android_max, DEFAULT_MAX_VALUE));
        }
        finally
        {
            a.recycle();
        }

// set layout
        setDialogLayoutResource(R.layout.preference_number_picker_dialog);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
        setDialogIcon(null);
    }

    @Override
    protected void onSetInitialValue(boolean restore, Object defaultValue)
    {
        setValue(restore ? getPersistedInt(DEFAULT_VALUE) : (Integer) defaultValue);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index)
    {
        return a.getInt(index, DEFAULT_VALUE);
    }

    @Override
    protected void onBindDialogView(View view)
    {
        super.onBindDialogView(view);

        TextView dialogMessageText = (TextView) view.findViewById(R.id.text_dialog_message);
        dialogMessageText.setText(getDialogMessage());

        if(android.os.Build.VERSION.SDK_INT >= 11) {
            mNumberPicker = new android.widget.NumberPicker(view.getContext());
            ((android.widget.NumberPicker)mNumberPicker).setMinValue(mMinValue);
            ((android.widget.NumberPicker)mNumberPicker).setMaxValue(mMaxValue);
            ((android.widget.NumberPicker)mNumberPicker).setValue(mValue);
        } else {
            mNumberPicker = new NumberPicker(view.getContext());
            ((NumberPicker)mNumberPicker).setMinValue(mMinValue);
            ((NumberPicker)mNumberPicker).setMaxValue(mMaxValue);
            ((NumberPicker)mNumberPicker).setValue(mValue);
        }
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, view.getContext().getResources().getDisplayMetrics());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int)px, 0, (int)px);
        mNumberPicker.setLayoutParams(lp);
        ((LinearLayout)view).addView(mNumberPicker);
    }

    public int getMinValue()
    {
        return mMinValue;
    }

    public void setMinValue(int minValue)
    {
        mMinValue = minValue;
        setValue(Math.max(mValue, mMinValue));
    }

    public int getMaxValue()
    {
        return mMaxValue;
    }

    public void setMaxValue(int maxValue)
    {
        mMaxValue = maxValue;
        setValue(Math.min(mValue, mMaxValue));
    }

    public int getValue()
    {
        return mValue;
    }

    public void setValue(int value)
    {
        value = Math.max(Math.min(value, mMaxValue), mMinValue);

        if (value != mValue)
        {
            mValue = value;
            persistInt(value);
            notifyChanged();
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult)
    {
        super.onDialogClosed(positiveResult);

// when the user selects "OK", persist the new value
        if (positiveResult)
        {
            int numberPickerValue;
            if(android.os.Build.VERSION.SDK_INT >= 11)
                numberPickerValue = ((android.widget.NumberPicker)mNumberPicker).getValue();
            else
                numberPickerValue = ((NumberPicker)mNumberPicker).getValue();
            if (callChangeListener(numberPickerValue))
            {
                setValue(numberPickerValue);
            }
        }
    }

    @Override
    protected Parcelable onSaveInstanceState()
    {
// save the instance state so that it will survive screen orientation changes and other events that may temporarily destroy it
        final Parcelable superState = super.onSaveInstanceState();

// set the state's value with the class member that holds current setting value
        final SavedState myState = new SavedState(superState);
        myState.minValue = getMinValue();
        myState.maxValue = getMaxValue();
        myState.value = getValue();

        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state)
    {
// check whether we saved the state in onSaveInstanceState()
        if (state == null || !state.getClass().equals(SavedState.class))
        {
// didn't save the state, so call superclass
            super.onRestoreInstanceState(state);
            return;
        }

// restore the state
        SavedState myState = (SavedState) state;
        setMinValue(myState.minValue);
        setMaxValue(myState.maxValue);
        setValue(myState.value);

        super.onRestoreInstanceState(myState.getSuperState());
    }

    private static class SavedState extends BaseSavedState
    {
        int minValue;
        int maxValue;
        int value;

        public SavedState(Parcelable superState)
        {
            super(superState);
        }

        public SavedState(Parcel source)
        {
            super(source);

            minValue = source.readInt();
            maxValue = source.readInt();
            value = source.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags)
        {
            super.writeToParcel(dest, flags);

            dest.writeInt(minValue);
            dest.writeInt(maxValue);
            dest.writeInt(value);
        }

        @SuppressWarnings("unused")
        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>()
        {
            @Override
            public SavedState createFromParcel(Parcel in)
            {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size)
            {
                return new SavedState[size];
            }
        };
    }



    public class NumberPicker extends LinearLayout {

        int counter = 0, min, max;
        Button add;
        Button sub;
        TextView display;

        public NumberPicker(Context context) {
            super(context);
            init();
        }

        public NumberPicker(Context context, AttributeSet attrs) {
            super(context, attrs);
            init();
        }

        private void init() {
            super.setOrientation(LinearLayout.VERTICAL);
            add = new Button(getContext());
            sub = new Button(getContext());
            display = new TextView(getContext());

            this.addView(add);
            this.addView(display);
            this.addView(sub);


            add.setOnClickListener(new View.OnClickListener() {

                public void onClick(View v) {
                    counter++;
                    update();
                }
            });

            sub.setOnClickListener(new View.OnClickListener() {

                public void onClick(View v) {
                    counter--;
                    update();
                }
            });

            //TODO: Button Drawables
        }

        private void update() {
            display.setText( "" + counter);
        }

        public void setMinValue(int m) {min = m;}
        public void setMaxValue(int m) {max = m;}
        public void setValue(int m) {
            counter = m;
            update();
        }
        public int getValue() { return counter; }
    }
}