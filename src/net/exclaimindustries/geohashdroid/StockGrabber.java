/**
 * StockGrabber.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.util.Calendar;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Window;

/**
 * The <code>StockGrabber</code> activity grabs a given stock value and returns
 * it to the calling activity.  It might pop up a dialog to this effect and
 * offer a means to cancel the operation, but it otherwise doesn't show anything
 * and has no interaction. 
 * 
 * @author Nicholas Killewald
 */
public class StockGrabber extends Activity {
    // Basic plan of action:
    //
    // 1. Go to HashBuilder to see if we can get it without going to the
    // internet.
    // 2. If so, good, return right away.
    // 3. If not, spawn the thread.  Make sure the thread gets stored away in
    // case this activity dies before it's done.
    public final static int RESULT_OK = 0;
    public final static int RESULT_NOT_POSTED_YET = 1;
    public final static int RESULT_SERVER_FAILURE = 2;
    public final static int RESULT_CANCEL = 3;
    
    private final static int DIALOG_FIND_STOCK = 0;
    
//    private final static String DEBUG_TAG = "StockGrabber";
    
    private Calendar mCal;
    private Graticule mGrat;
    private HashBuilder.StockRunner mRunner;
    private Thread mThread;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // The Intent should contain the request we need.  If not, return right
        // away with an error.
        Intent intent = getIntent();
        
        if(intent != null && intent.hasExtra(GeohashDroid.CALENDAR)
                && intent.hasExtra(GeohashDroid.GRATICULE)) {
            // If we have all the data, store it in our private variables.
            // Granted, we still need to know if the pieces are null or not.
            mCal = (Calendar)intent.getSerializableExtra(GeohashDroid.CALENDAR);
            mGrat = (Graticule)intent.getSerializableExtra(GeohashDroid.GRATICULE);
            if(mCal == null || mGrat == null) {
                // FAILURE!  We're missing some data!
                failure(RESULT_SERVER_FAILURE);
                return;
            }
        } else {
            // FAILURE!  The intent's missing data!
            failure(RESULT_SERVER_FAILURE);
            return;
        }
        
        // Good!  Data's retrieved, and we're ready to talk to HashBuilder!
        Info inf = HashBuilder.getStoredInfo(mCal, mGrat);
        if(inf != null) {
            // We got info!  Woo!  Send it back right away.
            success(inf);
            return;
        } else {
            // Otherwise, we need a stock runner.  When setContentView hits,
        	// the dialog gets thrown up.
            requestWindowFeature(Window.FEATURE_LEFT_ICON);
            // I was going to make it so that any "standby" dialog (either the
            // stock grabber or the location finder) would blur the background
            // to indicate it was doing something, but I really have to think
            // that out a bit better.  Doesn't look right.
//            getWindow().setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
//                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND);

            setContentView(R.layout.stockdialog);
            
            getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, 
                    android.R.drawable.ic_dialog_info);

            mRunner = HashBuilder.requestStockRunner(mCal, mGrat,
                    new StockFetchHandler(Looper.myLooper()));
            mThread = new Thread(mRunner);
            mThread.start();
        }
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        // TODO Auto-generated method stub
        super.onPause();
    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();
    }

    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        super.onStart();
    }

    @Override
    protected void onStop() {
        // TODO Auto-generated method stub
        super.onStop();
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        switch(id) {
            case DIALOG_FIND_STOCK: {
                // Box that says we're looking for stock data.
                AlertDialog.Builder build = new AlertDialog.Builder(this);
                build.setMessage(R.string.stock_label);
                build.setTitle(R.string.standby_title);
                build.setIcon(android.R.drawable.ic_dialog_info);
                build.setNegativeButton(R.string.cancel_label,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                // Abort the connection and drop the dialog.
                                if (mThread != null
                                        && mThread.isAlive()
                                        && mRunner != null)
                                {
                                    mRunner.abort();
                                }
                                StockGrabber.this
                                        .dismissDialog(DIALOG_FIND_STOCK);
                                failure(RESULT_CANCEL);
                            }
                        });
                return build.create();
            }
        }

        return null;
    }

    private void failure(int resultcode) {
        // FAILURE!  We're missing some data!
        Intent i = new Intent();
        setResult(resultcode, i);
        finish();
    }

    private void success(Info inf) {
        // SUCCESS!  We're ready to go!
        Intent i = new Intent();
        i.putExtra(GeohashDroid.INFO, inf);
        setResult(RESULT_OK, i);
        finish();
    }
    
    private class StockFetchHandler extends Handler {
        public StockFetchHandler(Looper looper) {
            super(looper);
        }
        
        public void handleMessage(Message message) {
            // Act upon the result.  The "dialog" gets closed as soon as this
        	// Activity returns a result.
            if (message.what != HashBuilder.StockRunner.ALL_OKAY) {
                switch (message.what) {
                    case HashBuilder.StockRunner.ERROR_NOT_POSTED:
                        failure(RESULT_NOT_POSTED_YET);
                        break;
                    case HashBuilder.StockRunner.ERROR_SERVER:
                        failure(RESULT_SERVER_FAILURE);
                        break;
                }
            } else {
                // If, however, we got the all clear, then we're clear! Get
                // the Info object and act!
                success((Info)message.obj);
            }

        }
    }
}
