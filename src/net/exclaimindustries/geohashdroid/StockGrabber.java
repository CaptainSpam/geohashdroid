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
import android.util.Log;
import android.view.KeyEvent;
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
    
    private final static String DEBUG_TAG = "StockGrabber";
    
    private Calendar mCal;
    private Graticule mGrat;
    private HashBuilder.StockRunner mRunner;
    private Thread mThread;
    
    private boolean mDontStopTheThread = false;
    
    // These get handed back to the caller in case it needs them.  It may not.
    // This saves us the trouble of holding them in a somewhat hokey manner in
    // the caller itself.
    private double mLatitude;
    private double mLongitude;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // As it stands, a stored configuration instance takes precedence.
        boolean configInstanceHandled = false;

        // We ALWAYS need these rebuilt...
        Intent intent = getIntent();
        
        if(intent != null && intent.hasExtra(GeohashDroid.CALENDAR)
                && intent.hasExtra(GeohashDroid.GRATICULE)) {
            // If we have all the data, store it in our private variables.
            // Granted, we still need to know if the pieces are null or not.
            mCal = (Calendar)intent.getSerializableExtra(GeohashDroid.CALENDAR);
            mGrat = (Graticule)intent.getParcelableExtra(GeohashDroid.GRATICULE);
            if(mCal == null) {
                // FAILURE!  We're missing some data!  Note that if we're
                // missing the graticule, we assume it to be a globalhash.
                failure(RESULT_SERVER_FAILURE);
                return;
            }
        } else {
            // FAILURE!  The intent's missing data!
            failure(RESULT_SERVER_FAILURE);
            return;
        }
        
        mLatitude = intent.getDoubleExtra(GeohashDroid.LATITUDE, 0.0);
        mLongitude = intent.getDoubleExtra(GeohashDroid.LONGITUDE, 0.0);
        
        if(getLastNonConfigurationInstance() != null) {
            try {
                // This'll get reset to false if an exception is thrown.
                configInstanceHandled = true;
                
                RetainedThings things = (RetainedThings)getLastNonConfigurationInstance();
                
                // First, re-establish the runner and thread.
                mRunner = things.runner;
                mThread = things.thread;
                
                // Then, check if the runner is still running.  If it is, re-
                // establish ourselves as the handler.  If it isn't, figure out
                // why.
                Log.d(DEBUG_TAG, "StockRunner's status is " + mRunner.getStatus());
                
                if(mRunner.getStatus() == HashBuilder.StockRunner.BUSY) {
                    // Still busy, make us the handler.
                    mRunner.changeHandler(new StockFetchHandler(Looper.myLooper()));
                } else if(mRunner.getStatus() == HashBuilder.StockRunner.ABORTED
                        || mRunner.getStatus() == HashBuilder.StockRunner.IDLE) {
                    // This shouldn't happen (we should've already returned or
                    // not even started yet), but just in case...
                    failure(RESULT_CANCELED);
                    return;
                } else if(mRunner.getStatus() == HashBuilder.StockRunner.ERROR_NOT_POSTED) {
                    // Not posted yet, return that as an error.
                    failure(RESULT_NOT_POSTED_YET);
                    return;
                } else if(mRunner.getStatus() == HashBuilder.StockRunner.ERROR_SERVER) {
                    // Server failure, return that as an error.
                    failure(RESULT_SERVER_FAILURE);
                    return;
                } else if(mRunner.getStatus() == HashBuilder.StockRunner.ALL_OKAY) {
                    // Hey!  We actually got something!
                    success((Info)(mRunner.getLastResultObject()));
                    return;
                } else {
                    // In any other case, it's safe to say we aborted.  Because
                    // I don't have the slightest clue what else would've just
                    // happened.
                    failure(RESULT_CANCELED);
                    return;
                }
            } catch (Exception ex) {
                // Pretty safe to assume that if we threw any exception,
                // the last instance is invalid.
                configInstanceHandled = false;
            }
        }
        
        // If we had a stored instance, it's what is still active and we can
        // just redraw from there.  Otherwise, build stuff up and start our own
        // StockRunner right away.  We do this in onCreate because there's
        // really no way this should be interrupted to the point of stopping the
        // StockRunner unless the user wants to interrupt things manually via
        // the Back button (and, in the event of an onDestroy without a config
        // change, we can just start over anyway).
        if(!configInstanceHandled) {
            // Now we're ready to talk to HashBuilder.  We want to do one check
            // to the database right now, though, so that this activity can
            // return immediately if the data's there.
            Info inf = HashBuilder.getStoredInfo(this, mCal, mGrat);
            if(inf != null) {
                // We got info!  Woo!  Send it back right away.
                success(inf);
                return;
            }
            
            // Now, build the stock runner!
            mRunner = HashBuilder.requestStockRunner(this, mCal, mGrat,
                    new StockFetchHandler(Looper.myLooper()));
            mThread = new Thread(mRunner);
            mThread.setName("StockRunnerThread");
            mThread.start();
        }
        
        // And then, display.
        displaySelf();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // If we're being destroyed, check to see if the thread should be
        // stopped.  By default, it should be.  We'll kick it back in later,
        // assuming we're not being finished.  If we're retaining our state due
        // to a configuration change, though, no such luck.
        if(!mDontStopTheThread && mThread != null && mThread.isAlive()
                && mRunner != null) {
            Log.d(DEBUG_TAG, "Aborting mRunner!");
            mRunner.abort();
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // If back is pressed, we want to call failure() instead of the normal
        // finish() command alone.  Else we'd get stuck in a loop.
        if(keyCode == KeyEvent.KEYCODE_BACK) {
            failure(RESULT_CANCELED);
            return true;
        } else {
            return super.onKeyUp(keyCode, event);
        }
    }

    private void failure(int resultcode) {
        Log.d(DEBUG_TAG, "FAILURE!");
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
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        // If the thread is still alive, retain it.  If it isn't, we've already
        // sent the result back.
        if(mThread != null && mThread.isAlive()) {
            Log.d(DEBUG_TAG, "Retaining instance...");
            mDontStopTheThread = true;
            RetainedThings retain = new RetainedThings();
            retain.runner = mRunner;
            retain.thread = mThread;
            return retain;
        } else {
            // I'm not at all sure how this would happen, but let's call it a
            // null anyway.
            return null;
        }
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
    
    private class RetainedThings {
        public Thread thread;
        public HashBuilder.StockRunner runner;
    }
}
