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
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.DialogInterface.OnCancelListener;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
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
    
    protected GeohashServiceInterface mService;
    
    /**
     * Service!
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            mService = GeohashServiceInterface.Stub.asInterface(service);
            
            // If the service isn't tracking, we don't have any reason to be
            // here.  Finish now.
            try {
                if(!mService.isTracking()) {
                    finish();
                    return;
                }
            } catch (RemoteException e) {

            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected.  All that means for us is that we
            // throw up standby.
            mService = null;
        }
    };
    
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
    
    @Override
    protected void onResume() {
        super.onResume();
        
        bindService(new Intent(WikiBaseActivity.this, GeohashService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        unbindService(mConnection);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        
        // Stash away whatever the last error was in case we need the dialog
        // back.
        outState.putString(LAST_ERROR, mLastErrorText);
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        // Here, we create the error dialog.  In onPrepareDialog, we actually
        // put text in it.  This one we actually CAN handle this way, as this
        // doesn't require a handler to be reset to repopulate the dialog.
        switch(id) {
            case DIALOG_ERROR: {
                AlertDialog.Builder build = new AlertDialog.Builder(this);
                build.setMessage("ERROR!");
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
    
    protected Location getCurrentLocation() {
        // We're perfectly allowed to return null here.  In fact, that's how we
        // let the caller know there is no location.  We just wrap it up here
        // to catch the mService == null condition.
        if(mService != null) {
            try {
                return mService.getLastLocation();
            } catch (RemoteException e) {
                return null;
            }
        } else {
            return null;
        }
    }
}
