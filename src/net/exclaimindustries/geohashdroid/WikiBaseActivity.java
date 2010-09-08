/**
 * WikiBaseActivity.java
 * Copyright (C)2009 Thomas Hirsch
 * Geohashdroid Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.text.DecimalFormat;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;

/**
 * Base class with the various things that both WikiPictureEditor and
 * WikiMessageEditor share in common.  Reduce repeated code and all that.
 * 
 * @author Thomas Hirsch and Nicholas Killewald
 */
public abstract class WikiBaseActivity extends Activity implements OnCancelListener {
    /**
     * WikiConnectionRunner is used by the wiki-manipulating classes.  It
     * encompasses a group of methods common to everything they do.  All what you
     * need to do is implement the run() method from Runnable, assign a Handler,
     * and update it as need be.
     *  
     * @author Thomas Hirsch and Nicholas Killewald
     */
    protected abstract class WikiConnectionRunner implements Runnable {
        /** New update for dialog text. */
        static public final int DIALOG_UPDATE = 1;
        /** Dismiss the current dialog. */
        static public final int DIALOG_DISMISS = 2;
        /** Dismiss the current dialog and pop up a new one with an error. */
        static public final int DIALOG_ERROR = 3;
        /** Replace the current message with a success, wait, and then dismiss. */
        static public final int DIALOG_SUCCESS = 4;
        
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

        /**
         * Tells the handler we're done.
         */
        protected void finishDialog() {
            Message msg = mHandler.obtainMessage(DIALOG_SUCCESS);
            mHandler.sendMessage(msg);
        }

    }

    protected ProgressDialog mProgress;
    protected Thread mWikiConnectionThread;
    protected boolean mDontStopTheThread = false;
    
    protected WikiConnectionRunner mConnectionHandler;
    
    /** This format is used for all latitude/longitude texts in the wiki. */
    protected static final DecimalFormat mLatLonFormat = new DecimalFormat("###.0000");
    
    private static final String LAST_ERROR = "LastError";
    
    // NOT the same as WikiConnectionRunner.DIALOG_ERROR, confusingly...
    private static final int DIALOG_ERROR = 0;
    private static final int DIALOG_SUCCESS = 1;
    
    // We need to define this here, as we've got no other way of passing the
    // string into the dialog.
    private String mLastErrorText = "";
    
    // Menu constant!  Just one!
    private static final int MENU_SETTINGS = 0;
    
    protected final Handler mProgressHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case WikiConnectionRunner.DIALOG_UPDATE:
                {
                    String status = (String)(msg.obj);
                    mProgress.setMessage(status);
                    break;
                }
                case WikiConnectionRunner.DIALOG_DISMISS:
                    mProgress.dismiss();
                    doDismiss();
                    break;
                case WikiConnectionRunner.DIALOG_ERROR:
                {
                    String status = (String)(msg.obj);
                    mProgress.dismiss();
                    if(status != null)
                    {
                        // If the status came in null, just dismiss.  That means
                        // either something went wrong or we actually got a
                        // dismiss command that was sent wrong.  Otherwise, make
                        // a new informational dialog.  We'll let it pass if it
                        // was empty but not null; that just gets turned into a
                        // generic error.
                        mLastErrorText = status.trim();
                        showDialog(DIALOG_ERROR);
                    }
                    break;
                }
                case WikiConnectionRunner.DIALOG_SUCCESS:
                {
                    mProgress.dismiss();
                    showDialog(DIALOG_SUCCESS);
                }

                default:
                    break;
                
            }
        }
    };
    
    /**
     * Does the normal onCreate stuff, plus gets the last error out of the
     * icicle.  Call through to this as usual.
     * 
     * @param icicle the icicle passed in
     */
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        // Repopulate the saved instance state.
        if(icicle != null && icicle.containsKey(LAST_ERROR)) {
            try {
                mLastErrorText = icicle.getString(LAST_ERROR);
            } catch (Exception ex) {}
        }
    }
    
    /* (non-Javadoc)
     * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        
        // Stash away whatever the last error was in case we need the dialog
        // back.
        outState.putString(LAST_ERROR, mLastErrorText);
    }
    
    /* (non-Javadoc)
     * @see android.app.Activity#onCreateDialog(int)
     */
    @Override
    protected Dialog onCreateDialog(int id) {
        // Here, we create the error dialog.  In onPrepareDialog, we actually
        // put text in it.  This one we actually CAN handle this way, as this
        // doesn't require a handler to be reset to repopulate the dialog.
        switch(id) {
            case DIALOG_ERROR: {
                AlertDialog.Builder build = new AlertDialog.Builder(this);
                build.setMessage(R.string.error_search_failed);
                build.setTitle(R.string.error_title);
                build.setIcon(android.R.drawable.ic_dialog_alert);
                build.setNegativeButton(R.string.darn_label,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,
                                int whichButton) {
                            WikiBaseActivity.this
                                .dismissDialog(DIALOG_ERROR);
                    }
                });
                return build.create();
            }
            case DIALOG_SUCCESS: {
                AlertDialog.Builder build = new AlertDialog.Builder(this);
                build.setMessage(R.string.wiki_conn_finished);
                build.setTitle(R.string.wiki_conn_finished_title);
                build.setIcon(android.R.drawable.ic_dialog_info);
                build.setNegativeButton(R.string.ok_label,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,
                                int whichButton) {
                            WikiBaseActivity.this
                                .dismissDialog(DIALOG_SUCCESS);
                    }
                });
                return build.create();
            }

        }
        return null;
    }
    
    /* (non-Javadoc)
     * @see android.app.Activity#onPrepareDialog(int, android.app.Dialog)
     */
    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        // And now, we put some text in.
        super.onPrepareDialog(id, dialog);
        switch(id) {
            case DIALOG_ERROR:
                // If there's actually text to see, use that.  Otherwise, we
                // need to use the generic.
                if(mLastErrorText != null && mLastErrorText.length() == 0)
                    ((AlertDialog)dialog).setMessage(getText(R.string.wiki_generic_error));
                else
                    ((AlertDialog)dialog).setMessage(mLastErrorText);
                break;
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(!mDontStopTheThread && mWikiConnectionThread != null && mWikiConnectionThread.isAlive()) {
            // If we want to stop the thread (default, assuming this isn't a
            // configuration change), AND the thread is defined, AND it's still
            // alive, abort it via WikiUtils.  Otherwise, this IS a config
            // change, so keep the thread running.  We'll catch up with it on
            // the next run.
            mConnectionHandler.abort();
        }
    }
    
    @Override
    public void onCancel(DialogInterface dialog) {
        // If the dialog is canceled (that is, the user pressed Back; any other
        // manner just dismisses the dialog, not cancels it), we want to stop
        // the connection immediately.  When we call WikiUtils.abort(), that'll
        // abort the connection, which will cause it to throw exceptions all
        // over the place.  Fortunately, the thread that runs the connection
        // will catch just about everything and bail out of the thread once it
        // stops.
        if(mWikiConnectionThread != null && mWikiConnectionThread.isAlive())
            mConnectionHandler.abort();
    }


    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        super.onMenuItemSelected(featureId, item);
        
        switch(item.getItemId()) {
            case MENU_SETTINGS: {
                // Pop up our settings window!
                startActivity(new Intent(this, PreferenceEditScreen.class));
                return true;
            }
        }
        
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        MenuItem item;
        
        // Just one this time.
        item = menu.add(Menu.NONE, MENU_SETTINGS, 3,
                R.string.menu_item_settings);
        item.setIcon(android.R.drawable.ic_menu_preferences);
        
        return true;
    }
    
    /**
     * Do whatever needs to be done when the dialog is dismissed on success.
     * The base method does nothing.
     */
    protected void doDismiss() {
        
    }
}
