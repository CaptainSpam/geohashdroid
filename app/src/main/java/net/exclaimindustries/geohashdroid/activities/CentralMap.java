/**
 * CentralMap.java
 * Copyright (C)2015 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid.activities;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.services.StockService;
import net.exclaimindustries.geohashdroid.util.Graticule;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.geohashdroid.widgets.ErrorBanner;

import java.util.Calendar;

/**
 * CentralMap replaces MainMap as the map display.  Unlike MainMap, it also
 * serves as the entry point for the entire app.  These comments are going to
 * make so much sense later when MainMap is little more than a class that only
 * exists on the legacy branch.
 */
public class CentralMap extends Activity {
    private boolean mSelectAGraticule = false;
    private Info mCurrentInfo;

    private ErrorBanner mBanner;

    private class StockReceiver extends BroadcastReceiver {
        private long mWaitingOnThisOne = -1;

        public void setWaitingId(long id) {
            mWaitingOnThisOne = id;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            // A stock result arrives!  Let's make sure it's really what we're
            // looking for.  We're assuming this is already an
            // ACTION_STOCK_RESULT, else this would just be broken.
            long reqId = intent.getLongExtra(StockService.EXTRA_REQUEST_ID, -1);
            if(reqId != mWaitingOnThisOne) return;

            // Well, it's what we're looking for.  What was the result?
            int responseCode = intent.getIntExtra(StockService.EXTRA_RESPONSE_CODE, StockService.RESPONSE_NETWORK_ERROR);

            switch(responseCode) {
                case StockService.RESPONSE_OKAY:
                    // Hey, would you look at that, it actually worked!  So, get
                    // the Info out of it and fire it away!
                    setInfo((Info)intent.getParcelableExtra(StockService.EXTRA_INFO));
                    break;
                case StockService.RESPONSE_NOT_POSTED_YET:
                    // TODO: Error response
                    mBanner.setText("NOT POSTED YET!");
                    mBanner.setErrorStatus(ErrorBanner.Status.ERROR);
                    mBanner.animateBanner(true);
                    break;
                case StockService.RESPONSE_NO_CONNECTION:
                    // TODO: Also error response
                    mBanner.setText("YOU CAN HAS NO CONNECTION YET!");
                    mBanner.setErrorStatus(ErrorBanner.Status.ERROR);
                    mBanner.animateBanner(true);
                    break;
                case StockService.RESPONSE_NETWORK_ERROR:
                    // TODO: Again, error response
                    mBanner.setText("RRRRRRRRRG STUPID NETWORK");
                    mBanner.setErrorStatus(ErrorBanner.Status.ERROR);
                    mBanner.animateBanner(true);
                    break;
                default:
                    break;
            }
        }
    }

    private StockReceiver mStockReceiver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.centralmap);

        mBanner = (ErrorBanner)findViewById(R.id.error_banner);
        mStockReceiver = new StockReceiver();
    }

    @Override
    protected void onPause() {
        // The receiver goes right off as soon as we pause.
        unregisterReceiver(mStockReceiver);

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // The receiver goes on during onResume, even though we might not be
        // waiting for anything yet.
        IntentFilter filt = new IntentFilter();
        filt.addAction(StockService.ACTION_STOCK_RESULT);

        registerReceiver(mStockReceiver, filt);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();

        if(mSelectAGraticule)
            inflater.inflate(R.menu.centralmap_selectagraticule, menu);
        else
            inflater.inflate(R.menu.centralmap_expedition, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_preferences:
                // Preferences!  To the Preferencemobile!
                Intent i = new Intent(this, PreferencesActivity.class);
                startActivity(i);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setInfo(Info info) {
        // TODO: Something
        mCurrentInfo = info;
    }

    private void requestStock(Graticule g, Calendar cal, int flags) {
        // Make sure the banner's going away!
        mBanner.animateBanner(false);

        // As a request ID, we'll use the current date.
        long date = cal.getTimeInMillis();

        Intent i = new Intent(this, StockService.class)
                .putExtra(StockService.EXTRA_DATE, cal)
                .putExtra(StockService.EXTRA_GRATICULE, g)
                .putExtra(StockService.EXTRA_REQUEST_ID, date)
                .putExtra(StockService.EXTRA_REQUEST_FLAGS, flags);

        mStockReceiver.setWaitingId(date);

        WakefulIntentService.sendWakefulWork(this, i);
    }
}
