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
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Window;
import android.widget.TextView;

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
    public final static int RESULT_NOT_POSTED_YET = 1;
    public final static int RESULT_SERVER_FAILURE = 2;
    
//    private final static String DEBUG_TAG = "StockGrabber";
    
    private Calendar mCal;
    private Graticule mGrat;
    private HashBuilder.StockRunner mRunner;
    private Thread mThread;
    
    // These get handed back to the caller in case it needs them.  It may not.
    // This saves us the trouble of holding them in a somewhat hokey manner in
    // the caller itself.
    private double mLatitude;
    private double mLongitude;
    
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
            if(mCal == null) {
                // FAILURE!  We're missing the date!  Note that if we're missing
                // the graticule, we assume it to be a globalhash.
                failure(RESULT_SERVER_FAILURE);
                return;
            }
        } else {
            // FAILURE!  The intent's missing data!
            failure(RESULT_SERVER_FAILURE);
            return;
        }
        
        // Last call, figure out if we need to return the doubles containing
        // location data...
        mLatitude = intent.getDoubleExtra(GeohashDroid.LATITUDE, 0.0);
        mLongitude = intent.getDoubleExtra(GeohashDroid.LONGITUDE, 0.0);
        
        // Good!  Data's retrieved, and we're ready to talk to HashBuilder!  We
        // want to do one check to the database right now, though, so that this
        // activity can return immediately if the data's there.
        Info inf = HashBuilder.getStoredInfo(this, mCal, mGrat);
        if(inf != null) {
            // We got info!  Woo!  Send it back right away.
            success(inf);
            return;
        }
        
        // No data?  Well, all right, but we'll need to make ourselves
        // presentable first...
        displaySelf();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Resume!  First, go to the database just in case.  This first case
        // should be impossible, but I'm going defensive.
        Info inf = HashBuilder.getStoredInfo(this, mCal, mGrat);
        if(inf != null) {
            // We got info!  Woo!  Send it back right away.
            success(inf);
            return;
        } else {
            // Otherwise, we need a stock runner.
            mRunner = HashBuilder.requestStockRunner(this, mCal, mGrat,
                    new StockFetchHandler(Looper.myLooper()));
            mThread = new Thread(mRunner);
            mThread.setName("StockRunnerThread");
            mThread.start();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // When paused, abort the thread and bail out entirely.  There's
        // trickery we COULD do to keep the thread running for when we get back,
        // but we have no guarantee that we WILL ever get back, so this is the
        // safest.
        if(mRunner != null)
        {
            mRunner.abort();
            failure(RESULT_CANCELED);
        }
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
        i.putExtra(GeohashDroid.LATITUDE, mLatitude);
        i.putExtra(GeohashDroid.LONGITUDE, mLongitude);
        setResult(RESULT_OK, i);
        finish();
    }
    
    private void displaySelf() {
        // ONLY CALL THIS ONCE!
        // Remove the title so it looks sorta right (the Dialog theme doesn't
        // *quite* get it right, so no title looks a lot better).
        requestWindowFeature(Window.FEATURE_NO_TITLE); 
        
        // Throw up content and away we go!
        setContentView(R.layout.genericbusydialog);
        
        TextView textView = (TextView)findViewById(R.id.Text);
        textView.setText(R.string.stock_label);
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
