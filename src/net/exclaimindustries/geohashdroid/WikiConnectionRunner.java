/**
 * WikiConnectionRunner.java
 * Copyright (C)2009 Thomas Hirsch
 * Geohashdroid Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

/**
 * WikiConnectionRunner is used by the wiki-manipulating classes.  It
 * encompasses a group of methods common to everything they do.  All what you
 * need to do is implement the run() method from Runnable, assign a Handler,
 * and update it as need be.
 *  
 * @author Thomas Hirsch and Nicholas Killewald
 */
public abstract class WikiConnectionRunner implements Runnable {
    /** New update for dialog text. */
    static public final int DIALOG_UPDATE = 1;
    /** Dismiss the current dialog. */
    static public final int DIALOG_DISMISS = 2;
    /** Dismiss the current dialog and pop up a new one with an error. */
    static public final int DIALOG_ERROR = 3;
    
    private Handler mHandler;
    private Context mContext;
    private String mOldStatus = "";
    private boolean mWasAborted = false;
    
    public WikiConnectionRunner(Handler h, Context c) {
      mHandler = h;
      mContext = c;
    }
    
    public void resetHandler(Handler h) {
        this.mHandler = h;
        setStatus(mOldStatus);
    }
    
    public void abort() {
        WikiUtils.abort();
        mWasAborted = true;
    }
    
    /**
     * Tells the handler to dismiss the current dialog and then pop up a new one
     * with the given error.
     * 
     * @param status error to report, without title
     */
    protected void error(String status) {
        // If we were aborted, just dismiss.
        if(mWasAborted)
            dismiss();
        else {
            // First, display the fail text.  This uses the plain addStatus.
            // This will, of course, be taken down right away, but it's best to
            // put up at least the "failed" text.
            addStatusAndNewline(R.string.wiki_conn_failure);
            
            // Second, add the specific error text.  This will add in the error
            // flag.  This won't use the addStatus family of methods because we
            // need that flag in there.
            Message msg = mHandler.obtainMessage(DIALOG_ERROR, status);
            mHandler.sendMessage(msg);
        }
    }

    /**
     * Sets the status to the given string.  Don't use this.  It's here in case
     * you really really need it, but don't.  Use the addStatus family of
     * methods, each of which eventually come here.
     * 
     * @param status the new status to use
     */
    protected void setStatus(String status) {
        Message msg = mHandler.obtainMessage(DIALOG_UPDATE, status);
        mHandler.sendMessage(msg);
    } 
    
    /**
     * Adds the given string to the status and update it.
     * 
     * @param status string to add to the current status
     */
    protected void addStatus(String status) {
        mOldStatus = mOldStatus + status;
        setStatus(mOldStatus);
    }
    
    /**
     * Adds the string referred to by the given resource ID to the status and
     * update the dialog.
     * 
     * @param resId resource ID of string to add to the current status
     */
    protected void addStatus(int resId) {
        addStatus(mContext.getText(resId).toString());
    }
    
    /**
     * Adds the string referred to by the given resource ID to the status, plus
     * a newline, and update the dialog.
     * 
     * @param resId resource ID of string to add to the current status
     */
    protected void addStatusAndNewline(int resId) {
        addStatus(resId);
        addStatus("\n");
    }
    
    /**
     * Tells the handler to dismiss the current dialog.
     */
    protected void dismiss() {
        Message msg = mHandler.obtainMessage(DIALOG_DISMISS);
        mHandler.sendMessage(msg);
    } 

}
