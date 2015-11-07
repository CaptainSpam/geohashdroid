/**
 * StockService.java
 * Copyright (C)2014 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid.services;

import android.content.Intent;
import android.os.Parcelable;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import net.exclaimindustries.geohashdroid.util.Graticule;
import net.exclaimindustries.geohashdroid.util.HashBuilder;
import net.exclaimindustries.geohashdroid.util.HashBuilder.StockRunner;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.tools.AndroidUtil;

import java.io.Serializable;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

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
     */
    public static final String ACTION_STOCK_REQUEST = "net.exclaimindustries.geohashdroid.STOCK_REQUEST";
    
    /**
     * Action that gets broadcast whenever StockService is returning a stock
     * result.  The intent will have a motley assortment of extras with it, each
     * of which are mentioned in this class, most of which were supplied with
     * the {@link #ACTION_STOCK_REQUEST} that started this.
     */
    public static final String ACTION_STOCK_RESULT = "net.exclaimindustries.geohashdroid.STOCK_RESULT";
    
    /**
     * Key for an ID extra on the response.  This isn't actually used and is not
     * required, but whatever is stored here (so long as it's a long) will be
     * put in the broadcast Intent when done.  If this isn't specified, it will
     * come back as -1.
     */
    public static final String EXTRA_REQUEST_ID = "net.exclaimindustries.geohashdroid.EXTRA_REQUEST_ID";
    /**
     * Key for additional flags in the request.  For the most part, these don't
     * change how the request is handled, like the request ID, and will simply
     * be passed back in the resulting broadcast Intent.  Some flags, however,
     * like {@link #FLAG_INCLUDE_NEARBY_POINTS}, will add more data.  This helps
     * BroadcastReceivers know what led to this request, which can come in handy
     * if there's some case where you want to ignore responses the came from,
     * say, the stock alarm.
     */
    public static final String EXTRA_REQUEST_FLAGS = "net.exclaimindustries.geohashdroid.EXTRA_REQUEST_FLAGS";
    /**
     * Key for additional flags in the response.  These give additional info as
     * to what happened during the request.
     */
    public static final String EXTRA_RESPONSE_FLAGS = "net.exclaimindustries.geohashdroid.EXTRA_RESPONSE_FLAGS";
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
    /**
     * Key for nearby points, if {@link #FLAG_INCLUDE_NEARBY_POINTS} was
     * specified.  This will be an array of Info objects.  The order of the
     * array is arbitrary.  There will usually be eight elements in it, though
     * there may be fewer if the request is either at the poles or in rare
     * 30W-related cases.
     */
    public static final String EXTRA_NEARBY_POINTS = "net.exclaimindustries.geohashdroid.EXTRA_NEARBY_POINTS";
    
    /**
     * Flag meaning this request came from the stock alarm around 9:30am EST.
     * This is for pre-cache stuff.
     */
    public static final int FLAG_ALARM = 0x1;
    /**
     * Flag meaning this request was manually initiated by the user.  This is
     * for if the user specifically wants a certain date or Graticule.
     */
    public static final int FLAG_USER_INITIATED = 0x2;
    /**
     * Flag meaning this request was automatically initiated.  This is used for
     * odd cases where we need to make requests behind the user's back, like
     * coming back in from preferences and we need to re-read the "place nearby
     * flags" setting.
     */
    public static final int FLAG_AUTO_INITIATED = 0x4;
    /**
     * Flag meaning this request is due to the user wanting to find the closest
     * point to some location.  As you can tell from the value, this will imply
     * {@link #FLAG_INCLUDE_NEARBY_POINTS}.
     */
    public static final int FLAG_FIND_CLOSEST = 0x28;
    /**
     * Flag meaning this request came from Select-A-Graticule mode.  CentralMap
     * should know what to do with it.
     */
    public static final int FLAG_SELECT_A_GRATICULE = 0x10;

    /**
     * Flag meaning that, in addition to the point requested, the (up to) eight
     * surrounding points should also be included in the response.
     */
    public static final int FLAG_INCLUDE_NEARBY_POINTS = 0x20;

    /**
     * Flag meaning this response was found in the cache.  If not set, it was
     * either found on the web or it wasn't found at all, the latter of which
     * implying you really ought to have checked the response code first.
     */
    public static final int FLAG_CACHED = 0x1;

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
        if(!intent.hasExtra(EXTRA_DATE)) return;
        
        // Maybe we have a request ID!
        long requestId = intent.getLongExtra(EXTRA_REQUEST_ID, -1);

        // Maybe we have flags!
        int flags = intent.getIntExtra(EXTRA_REQUEST_FLAGS, 0);

        // Maybe we'll respond with flags!
        int respFlags = 0;
        
        // Oh, man, can we ever parcelize a Graticule!
        Parcelable p = intent.getParcelableExtra(EXTRA_GRATICULE);

        // Remember, the Graticule MIGHT be null if it's a globalhash.
        if(p != null && !(p instanceof Graticule)) return;
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
            respFlags |= FLAG_CACHED;
            Info[] nearby = null;
            if((flags & FLAG_INCLUDE_NEARBY_POINTS) != 0)
                nearby = getNearbyPoints(cal, graticule);
            dispatchIntent(RESPONSE_OKAY, requestId, flags, respFlags, cal, graticule, info, nearby);
        } else {
            // Otherwise, we need to go to the web.
            if(!AndroidUtil.isConnected(this)) {
                // ...if we CAN go to the web, that is.
                dispatchIntent(RESPONSE_NO_CONNECTION, requestId, flags, respFlags, cal, graticule, null, null);
            } else {
                StockRunner runner = HashBuilder.requestStockRunner(this, cal, graticule, null);
                runner.runStock();

                // And the results are in!
                int result = runner.getStatus();
                
                switch(result) {
                    case HashBuilder.StockRunner.ALL_OKAY:
                        // Hooray!  We win!  Dispatch an intent with the info.
                        Info[] nearby = null;
                        if((flags & FLAG_INCLUDE_NEARBY_POINTS) != 0)
                            nearby = getNearbyPoints(cal, graticule);
                        dispatchIntent(RESPONSE_OKAY, requestId, flags, respFlags, cal, graticule, runner.getLastResultObject(), nearby);
                        break;
                    case HashBuilder.StockRunner.ERROR_NOT_POSTED:
                        // Aw.  It's not posted yet.
                        dispatchIntent(RESPONSE_NOT_POSTED_YET, requestId, flags, respFlags, cal, graticule, null, null);
                        break;
                    default:
                        // In all other cases, just assume it's a network error.
                        // We either got ERROR_NETWORK, which is just that, or
                        // we got IDLE, BUSY, or ABORTED, none of which make any
                        // sense in this context, which means something went
                        // horribly, horribly wrong.
                        dispatchIntent(RESPONSE_NETWORK_ERROR, requestId, flags, respFlags, cal, graticule, null, null);
                }
            }
        }
    }
    
    private void dispatchIntent(int responseCode, long requestId, int flags, int respFlags, Calendar date, Graticule graticule, Info info, Info[] nearby) {
        // Welcome to central Intent dispatch.  How may I help you?
        Intent intent = new Intent(ACTION_STOCK_RESULT);
        
        // Attach all the extras as need be.  I'm at least partly sure this is
        // not at all what Intent broadcasting was made for, but hey.
        intent.putExtra(EXTRA_RESPONSE_CODE, responseCode);
        intent.putExtra(EXTRA_REQUEST_ID, requestId);
        intent.putExtra(EXTRA_REQUEST_FLAGS, flags);
        intent.putExtra(EXTRA_RESPONSE_FLAGS, respFlags);
        intent.putExtra(EXTRA_DATE, date);
        intent.putExtra(EXTRA_GRATICULE, graticule);
        intent.putExtra(EXTRA_INFO, info);
        if(nearby != null && nearby.length != 0) {
            intent.putExtra(EXTRA_NEARBY_POINTS, nearby);
        }
        
        // And away it goes!
        sendBroadcast(intent);
    }

    private Info[] getNearbyPoints(Calendar cal, Graticule g) {
        if(g == null) return new Info[0];

        List<Info> infos = new LinkedList<>();

        // Hopefully, each nearby point is available.  In addition to cases
        // involving the poles, I *think* there's cases where a 30W point IS
        // available, but a neighboring non-30W point ISN'T.  We'll just ignore
        // those cases.
        for(int i = -1; i <= 1; i++) {
            for(int j = -1; j <= 1; j++) {
                // Zero and zero isn't a nearby point, that's the very point
                // we're at right now!
                if(i == 0 && j == 0) continue;

                // If the user's truly adventurous enough to go to the 90N/S
                // graticules, there aren't any nearby points north/south of
                // where they are.  Also, the nearby points aren't going to
                // be drawn anyway due to the projection, but hey, that's
                // nitpicking.
                if(Math.abs((g.isSouth() ? -1 : 1) * g.getLatitude() + i) > 90)
                    continue;

                // Make a new Graticule, properly offset...
                Graticule offset = Graticule.createOffsetFrom(g, i, j);

                // ...then do the request.  Check the cache first!
                Info info = HashBuilder.getStoredInfo(this, cal, offset);
                if(info == null) {
                    // It's not in the cache.  Try to make it be in the cache.
                    StockRunner runner = HashBuilder.requestStockRunner(this, cal, offset, null);
                    runner.runStock();

                    if(runner.getStatus() == HashBuilder.StockRunner.ALL_OKAY) {
                        // We've got a winner!
                        info = runner.getLastResultObject();
                    }
                    // We'll just ignore it if not.  The user doesn't need to be
                    // bugged about cache failures or whatnot, they already got
                    // what they were looking for.
                }

                // Now, add that to the array, if it's not null...
                if(info != null)
                    infos.add(info);

                // And continue on!
            }
        }

        Info[] toReturn = new Info[8];
        return infos.toArray(toReturn);
    }
}
