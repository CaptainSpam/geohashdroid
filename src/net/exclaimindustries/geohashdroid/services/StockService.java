/**
 * HashService.java
 * Copyright (C)2014 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid.services;

import java.io.Serializable;
import java.util.Calendar;

import net.exclaimindustries.geohashdroid.util.Graticule;
import net.exclaimindustries.geohashdroid.util.HashBuilder;
import net.exclaimindustries.geohashdroid.util.HashBuilder.StockRunner;
import net.exclaimindustries.geohashdroid.util.Info;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Parcelable;

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
 * involved reschedule-if-not-connected tomfoolery.  We'll report errors back,
 * of course, so that any callers know what's going on, but otherwise we'll just
 * try to get the stock and convert it to a hash (either to the web or just from
 * the stock cache).
 * </p>
 * 
 * @author Nicholas Killewald
 */
public class StockService extends WakefulIntentService {

//    private static final String DEBUG_TAG = "StockService";
    
    /**
     * <p>
     * Action to send out when you want stock data and the associated Info
     * object for a Graticule and date.  You want to make sure this at least has
     * a Calendar for {@link #EXTRA_DATE}.  If {@link #EXTRA_GRATICULE} is
     * given, it'll look for an Info object for a single-Graticule expedition.
     * If there isn't or the given Graticule is null, it'll assume it's a
     * Globalhash.
     * </p>
     * 
     * <p>
     * If the date is null or isn't a Calendar, or if the Graticule extra exists
     * but isn't a Graticule object (null counts as a Graticule), StockService
     * will ignore and discard the request, even if a request ID was sent.
     * </p>
     * 
     * @see #EXTRA_REQUEST_ID
     */
    public static final String ACTION_STOCK_REQUEST = "net.exclaimindustries.geohashdroid.STOCK_REQUEST";
    
    /**
     * Action that gets broadcast whenever StockService is returning a stock
     * result.  The intent will have a motley assortment of extras with it, each
     * of which are mentioned in this class, most of which were supplied with
     * the {@link #ACTION_STOCK_REQUEST} that started this.
     * 
     * @see #EXTRA_DATE
     * @see #EXTRA_GRATICULE
     * @see #EXTRA_INFO
     * @see #EXTRA_REQUEST_ID
     * @see #EXTRA_RESPONSE_CODE
     */
    public static final String ACTION_STOCK_RESULT = "net.exclaimindustries.geohashdroid.STOCK_RESULT";
    
    /**
     * Key for an ID extra on the response.  This isn't actually used and is not
     * required, but whatever is stored here (so long as it's an int) will be
     * put in the broadcast Intent when done.  Try to keep this positive.
     */
    public static final String EXTRA_REQUEST_ID = "net.exclaimindustries.geohashdroid.EXTRA_REQUEST_ID";
    /**
     * Key for a Graticule extra.  This must be defined, though it can be null
     * if you're requesting a Globalhash.
     */
    public static final String EXTRA_GRATICULE = "net.exclaimindustries.geohashdroid.EXTRA_GRATICULE";
    /**
     * Key for a Calendar extra.  This must be defined and not null.  And a
     * Calendar.
     */
    public static final String EXTRA_DATE = "net.exclaimindustries.geohashdroid.EXTRA_DATE";
    /**
     * Key for an Info extra.  This comes back in the broadcast.  Note that the
     * data will be null if there was an error.
     */
    public static final String EXTRA_INFO = "net.exclaimindustries.geohashdroid.EXTRA_INFO";
    /**
     * Key for the response code extra.  This will be an int.
     */
    public static final String EXTRA_RESPONSE_CODE = "net.exclaimindustries.geohashdroid.EXTRA_RESPONSE_CODE";
    
    /** All okay response. */
    public static final int RESPONSE_OKAY = 0;
    /** Error response if the requested stock wasn't posted yet. */
    public static final int RESPONSE_NOT_POSTED_YET = -1;
    /**
     * Error response if we needed to go to the network for a stock lookup, but
     * we didn't have any network connection at all. 
     */
    public static final int RESPONSE_NO_CONNECTION = -2;
    /** Error response if there was some network error involved. */
    public static final int RESPONSE_NETWORK_ERROR = -3;
    
    /**
     * A dummy Graticule that uses the 30W rule, and thus needs yesterday's date
     * to work.
     */
    public static final Graticule DUMMY_YESTERDAY = new Graticule(51, false, 0, true);
    /**
     * A dummy Graticule that doesn't use the 30W rule, and thus needs today's
     * date to work.
     */
    public static final Graticule DUMMY_TODAY = new Graticule(38, false, 84, true);
    
    public StockService() {
        super("StockService");
    }

    @Override
    protected void doWakefulWork(Intent intent) {
        // Gee, thanks, WakefulIntentService, for covering all that confusing
        // WakeLock stuff!  You're even off the main thread, too, so I don't
        // have to spawn a new thread to not screw up the UI!  So let's get that
        // data right in hand, shall we?
        if(!intent.hasExtra(EXTRA_GRATICULE) || !intent.hasExtra(EXTRA_DATE)) return;
        
        // Maybe we have a request ID!
        int requestId = -1;
        if(intent.hasExtra(EXTRA_REQUEST_ID)) {
            requestId = intent.getIntExtra(EXTRA_REQUEST_ID, -1);
        }
        
        // Oh, man, can we ever parcelize a Graticule!
        Parcelable p = intent.getParcelableExtra(EXTRA_GRATICULE);
        
        if(!(p instanceof Graticule)) return;
        Graticule graticule = (Graticule)p;
        
        // Calendar, well, we can't parcelize that, but we CAN serialize it,
        // which is almost as good!
        Serializable s = intent.getSerializableExtra(EXTRA_DATE);
        
        if(s == null || !(s instanceof Calendar)) return;
        Calendar cal = (Calendar)s;
        
        // First, ask the stock cache if we've got an Info we can throw back.
        Info info = HashBuilder.getStoredInfo(this, cal, graticule);
        
        // If we got something, great!  Broadcast it right on out!
        if(info != null) {
            dispatchIntent(RESPONSE_OKAY, requestId, cal, graticule, info);
        } else {
            // Otherwise, we need to go to the web.
            if(!isConnected(this)) {
                // ...if we CAN go to the web, that is.
                dispatchIntent(RESPONSE_NO_CONNECTION, requestId, cal, graticule, null);
            } else {
                StockRunner runner = HashBuilder.requestStockRunner(this, cal, graticule, null);
                runner.runStock();

                // And the results are in!
                int result = runner.getStatus();
                
                switch(result) {
                    case HashBuilder.StockRunner.ALL_OKAY:
                        // Hooray!  We win!  Dispatch an intent with the info.
                        dispatchIntent(RESPONSE_OKAY, requestId, cal, graticule, runner.getLastResultObject());
                        break;
                    case HashBuilder.StockRunner.ERROR_NOT_POSTED:
                        // Aw.  It's not posted yet.
                        dispatchIntent(RESPONSE_NOT_POSTED_YET, requestId, cal, graticule, null);
                        break;
                    default:
                        // In all other cases, just assume it's a network error.
                        // We either got ERROR_NETWORK, which is just that, or
                        // we got IDLE, BUSY, or ABORTED, none of which make any
                        // sense in this context, which means something went
                        // horribly, horribly wrong.
                        dispatchIntent(RESPONSE_NETWORK_ERROR, requestId, cal, graticule, null);
                }
            }
        }
    }
    
    private void dispatchIntent(int responseCode, int requestId, Calendar date, Graticule graticule, Info info) {
        // Welcome to central Intent dispatch.  How may I help you?
        Intent intent = new Intent(ACTION_STOCK_RESULT);
        
        // Attach all the extras as need be.  I'm at least partly sure this is
        // not at all what Intent broadcasting was made for, but hey.
        intent.putExtra(EXTRA_RESPONSE_CODE, responseCode);
        intent.putExtra(EXTRA_REQUEST_ID, requestId);
        intent.putExtra(EXTRA_DATE, date);
        intent.putExtra(EXTRA_GRATICULE, graticule);
        intent.putExtra(EXTRA_INFO, info);
        
        // And away it goes!
        sendBroadcast(intent);
    }

    private static boolean isConnected(Context c) {
        // This just checks if we've got any valid network connection at all.
        NetworkInfo networkInfo = ((ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }
}
