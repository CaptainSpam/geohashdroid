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
    protected ProgressDialog mProgress;
    protected Thread mWikiConnectionThread;
    protected boolean mDontStopTheThread = false;
    
    protected WikiConnectionRunner mConnectionHandler;
    
    /** This format is used for all latitude/longitude texts in the wiki. */
    protected static final DecimalFormat mLatLonFormat = new DecimalFormat("###.000");
    
    private static final String LAST_ERROR = "LastError";
    
    // NOT the same as WikiConnectionRunner.DIALOG_ERROR, confusingly...
    private static final int DIALOG_ERROR = 0;
    
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
}
