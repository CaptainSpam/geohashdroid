/**
 * HashService.java
 * Copyright (C)2014 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid.services;

import net.exclaimindustries.geohashdroid.util.GHDConstants;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.commonsware.cwac.wakeful.WakefulIntentService;

/**
 * <p>
 * StockService, made possible by the sleepless efforts of Mark "CommonsGuy"
 * Murphy, handles all stock retrieval duties.  You ask it for a stock, it'll
 * later broadcast an Intent either with that stock or some error.
 * </p>
 * 
 * <p>
 * This is going to be similar to the old StockService of ages past, but just
 * the business end of it.  The alarm is handled elsewhere, as well as all
 * involved reschedule-if-not-connected tomfoolery.  We'll  report errors back,
 * of course, so that any callers know what's going on, but otherwise we'll just
 * try to get the stock and convert it to a hash (either to the web or just from
 * the stock cache).
 * </p>
 * 
 * @author Nicholas Killewald
 */
public class StockService extends WakefulIntentService {

    private static final String DEBUG_TAG = "StockService";
    
    private static final int NOTIFICATION_ID = 1;
    
    public StockService() {
        super("HashService");
    }

    @Override
    protected void doWakefulWork(Intent intent) {
        // Gee, thanks, WakefulIntentService, for covering all that confusing
        // WakeLock stuff!  Let's get right into figuring out what sort of
        // request this is.
        

    }

    private static boolean isConnected(Context c) {
        // This just checks if we've got any valid network connection at all.
        NetworkInfo networkInfo = ((ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }
    
}
