/**
 * DateButton.java
 * Copyright (C)2010 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.tools;

import java.text.DateFormat;
import java.util.Calendar;

import android.app.DatePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
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

    /** The date that'll be returned later. */
    private Calendar mDate;
    /** Whether or not the dialog is showing (so it can be restored later). */
    private boolean mDialogShown = false;
    
    public DateButton(Context context) {
        super(context);
        initButton();
    }

    public DateButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initButton();
    }
    
    public DateButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initButton();
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
        setText(DateFormat.getDateInstance(DateFormat.LONG).format(
                mDate.getTime()));
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

    @Override
    public void onClick(View v) {
        // So!  At this point, bring up the DatePickerDialog.  We'll need
        // to listen for its completion, too, so that's ANOTHER callback...
        DatePickerDialog dialog = new DatePickerDialog(getContext(), this,
                mDate.get(Calendar.YEAR), mDate.get(Calendar.MONTH),
                mDate.get(Calendar.DAY_OF_MONTH));
        dialog.setOnDismissListener(this);
        
        mDialogShown = true;
        dialog.show();
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
    
}
