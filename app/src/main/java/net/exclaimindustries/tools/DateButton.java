/**
 * DateButton.java
 * Copyright (C)2010 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.tools;

import java.text.DateFormat;
import java.util.Calendar;

import net.exclaimindustries.geohashdroid.R;

import android.app.DatePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.DatePicker;

/**
 * A <code>DateButton</code> is a <code>Button</code> that, when clicked, pops
 * up a <code>DatePickerDialog</code>.  Its text will default to the date last
 * picked and the button itself can be queried to see what date it thinks it
 * was.  This date will be returned as a <code>Calendar</code> object.
 * 
 * @author Nicholas Killewald
 */
public class DateButton extends Button implements OnClickListener, OnDateSetListener, OnDismissListener {
//    private final static String DEBUG_TAG = "DateButton";
    
    /** The date that'll be returned later. */
    private Calendar mDate;
    /** Whether or not the dialog is showing (so it can be restored later). */
    private boolean mDialogShown = false;
    
    private DatePickerDialog mLastDialog = null;
    
    private final static String SHOW_DIALOG = "ShowDialog";
    private final static String LAST_DATE = "LastDate";
    private final static String DIALOG_STATE = "DialogState";
    
    private DateFormat mFormat;
    
    public DateButton(Context context) {
        super(context);
        initButton();
    }

    public DateButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        applyExtraAttributes(attrs);
        initButton();
    }
    
    public DateButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        applyExtraAttributes(attrs);
        initButton();
    }
    
    private void applyExtraAttributes(AttributeSet attrs) {
        // This simply determines what date format we'll be using.  Different
        // orientations and/or sizes might want short text, for instance.
        TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.DateButton);
        
        int format = ta.getInt(R.styleable.DateButton_date_format, 1);

        mFormat = DateFormat.getDateInstance(format);
        
        ta.recycle();
    }

    private void initButton() {
        // First, init the date.
        setDate(Calendar.getInstance());
        
        // Then, set up the click callback.
        setOnClickListener(this);
    }
    
    /**
     * Sets the date on the button.
     * 
     * @param newDate new date to be set
     */
    public void setDate(Calendar newDate) {
        mDate = newDate;
        setText(mFormat.format(mDate.getTime()));
    }
    
    /**
     * Sets the date on the button to whatever today is.
     */
    public void setToday() {
        setDate(Calendar.getInstance());
    }
    
    /**
     * Returns the last date picked by this button.  This defaults to the
     * current date.
     * 
     * @return the last date picked by this button
     */
    public Calendar getDate() {
        return mDate;
    }
    
    private DatePickerDialog constructDialog() {
        DatePickerDialog dialog = new DatePickerDialog(getContext(), this,
                mDate.get(Calendar.YEAR), mDate.get(Calendar.MONTH),
                mDate.get(Calendar.DAY_OF_MONTH));
        
        dialog.setOnDismissListener(this);
        
        return dialog;
    }

    @Override
    public void onClick(View v) {
        // So!  At this point, bring up the DatePickerDialog.  We'll need
        // to listen for its completion, too, so that's ANOTHER callback...
        mLastDialog = constructDialog();
        
        mDialogShown = true;
        mLastDialog.show();
    }
    
    private void restoreDialog(Bundle b) {
        mLastDialog = constructDialog();
        mLastDialog.onRestoreInstanceState(b);
        mLastDialog.show();
    }

    @Override
    public void onDateSet(DatePicker view, int year, int monthOfYear,
            int dayOfMonth) {
        // And now, we set the new date.
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, monthOfYear);
        cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        
        setDate(cal);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        mDialogShown = false;
    }
    
    public static class SavedState extends BaseSavedState {
        boolean mInternalDialogShown;
        Calendar mCalendar;
        Bundle mDialogBundle;

        SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            
            Bundle b = new Bundle();
            
            b.putBoolean(SHOW_DIALOG, mInternalDialogShown);
            b.putSerializable(LAST_DATE, mCalendar);
            b.putBundle(DIALOG_STATE, mDialogBundle);
            
            out.writeBundle(b);
        }

        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };

        private SavedState(Parcel in) {
            super(in);
            Bundle b = in.readBundle();
            
            mInternalDialogShown = b.getBoolean(SHOW_DIALOG);
            mCalendar = (Calendar)(b.getSerializable(LAST_DATE));
            mDialogBundle = b.getBundle(DIALOG_STATE);
        }
    }
    
    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        SavedState ss = new SavedState(superState);
        ss.mInternalDialogShown = mDialogShown;
        ss.mCalendar = mDate;
        if(mLastDialog != null)
            ss.mDialogBundle = mLastDialog.onSaveInstanceState();

        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState)state;
        super.onRestoreInstanceState(ss.getSuperState());

        mDialogShown = ss.mInternalDialogShown;
        setDate(ss.mCalendar);
        if(mDialogShown && ss.mDialogBundle != null)
            restoreDialog(ss.mDialogBundle);
    }
    
}
