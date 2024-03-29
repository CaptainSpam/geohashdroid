/*
 * StockWorker.java
 * Copyright (C)2014 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid.services;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import android.util.Log;

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
 * StockWorker handles all stock retrieval duties.  You ask it for a stock,
 * it'll later broadcast an Intent either with that stock or some error.
 * </p>
 * 
 * <p>
 * This is now the THIRD generation of something resembling StockService.  Only,
 * it's a Worker now.  Still, {@link #enqueueWork(Context, Intent)} functions
 * the way the second generation did, so it takes the same Intent as before and
 * converts it into Worker-related stuff.  Still broadcasts at the end, though.
 * </p>
 * 
 * @author Nicholas Killewald
 */
public class StockWorker extends Worker {
    private static final String DEBUG_TAG = "StockWorker";

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
     * but isn't a Graticule object (null counts as a Graticule), StockWorker
     * will ignore and discard the request, even if a request ID was sent.
     * </p>
     */
    public static final String ACTION_STOCK_REQUEST = "net.exclaimindustries.geohashdroid.STOCK_REQUEST";
    
    /**
     * Action that gets broadcast whenever StockWorker is returning a stock
     * result.  The intent will have a motley assortment of extras with it, each
     * of which are mentioned in this class, most of which were supplied with
     * the {@link #ACTION_STOCK_REQUEST} that started this.
     */
    public static final String ACTION_STOCK_RESULT = "net.exclaimindustries.geohashdroid.STOCK_RESULT";

    /**
     * <p>
     * Key for the extra stuff Bundle.  This Bundle will contain all the needed
     * Extras to put StockWorker together.  This is needed because not all
     * devices seem to apply the correct ClassLoader when dealing with Intents
     * being sent across remote services (i.e. broadcasts), resulting in
     * problems when custom Parcelables are used (i.e. Graticule and Info).  A
     * Bundle, on the other hand, doesn't try to unmarshall Parcelables until
     * needed, and we can properly assign the ClassLoader then.
     * </p>
     *
     * <p>
     * Note that this also implies you should call {@link Bundle#setClassLoader(ClassLoader)}
     * on this Bundle with whatever the current ClassLoader is any time you deal
     * with data from StockWorker.
     * </p>
     */
    public static final String EXTRA_STUFF = "net.exclaimindustries.geohashdroid.EXTRA_STUFF";

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
     * <p>
     * Key for the class to which this request should respond.  As per Oreo,
     * we can't define BroadcastReceivers in the manifest anymore (or, to be
     * exact, we can't define BroadcastReceivers with <i>implicit</i> Intents
     * and expect them to go through), which causes problems when talking back
     * to, say, AlarmWorker's StockReceiver.  The presence of this Extra (and
     * it being not null) will tell StockWorker to explicitly send the intent
     * to that class.  As such, it must be a class object (like, say,
     * AlarmWorker.StockReceiver.class), and should preferably be something
     * that can receive an Intent.  There's no telling what might happen if it
     * can't.
     * </p>
     *
     * <p>
     * Of course, because this API change only affects BroadcastReceivers
     * defined in the manifest, this doesn't affect when you're explicitly
     * registering the receiver on an as-needed basis, like what CentralMap
     * does.  In other words, this is likely only to be used in the Services,
     * and because of that, is probably only going to matter to AlarmWorker.
     * </p>
     *
     * <p>
     * <i>TODO: Look into whether or not JobScheduler can fix this mess.</i>
     * </p>
     */
    public static final String EXTRA_RESPOND_TO = "net.exclaimindustries.geohashdroid.EXTRA_RESPOND_TO";

    /**
     * Data key for whether or not this request is for a Globalhash and thus
     * won't have a Graticule latitude or longitude.
     */
    private static final String DATA_GRATICULE_GLOBALHASH = "isGlobalhash";
    /**
     * Data key for the Graticule's latitude in string form, with negative
     * values being southern and positive values being northern (with the
     * possibility of a -0 value for 0° to 1°S).
     */
    private static final String DATA_GRATICULE_LATITUDE = "graticuleLatitude";
    /**
     * Data key for the Graticule's longitude in string form, with negative
     * values being western and positive values being eastern (with the
     * possibility of a -0 value for 0° to 1°W).
     */
    private static final String DATA_GRATICULE_LONGITUDE = "graticuleLongitude";

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
     * Convenience method for enqueuing work in to this Worker.  This is largely
     * to keep me from having to re-write everything from when this was
     * StockService.
     */
    public static void enqueueWork(@NonNull Context context, @NonNull Intent work) {
        // Remake the Intent into a Data.  I guess we're doing type checks here
        // now!
        Parcelable p = work.getParcelableExtra(EXTRA_GRATICULE);

        // Remember, the Graticule MIGHT be null if it's a globalhash.
        if(p != null && !(p instanceof Graticule)) {
            Log.e(DEBUG_TAG, "BAILING OUT: EXTRA_GRATICULE is not null and isn't a Graticule!");
            return;
        }
        Graticule graticule = (Graticule)p;

        // Unfortunately, we need to decompose the Graticule into simpler things
        // that a Data object can understand.
        boolean isGlobalhash = graticule == null;
        String graticuleLatitude = !isGlobalhash ? graticule.getLatitudeString(true) : null;
        String graticuleLongitude = !isGlobalhash ? graticule.getLongitudeString(true) : null;

        // The date can be serialized out to a Long.
        Serializable s = work.getSerializableExtra(EXTRA_DATE);

        if(!(s instanceof Calendar)) {
            Log.e(DEBUG_TAG, "BAILING OUT: EXTRA_DATE is null or not a Calendar!");
            return;
        }
        long date = ((Calendar)s).getTimeInMillis();

        // The respond-to class is a tricky one.  I think we can make that into
        // a String?
        s = work.getSerializableExtra(EXTRA_RESPOND_TO);

        if(s != null && !(s instanceof Class)) {
            Log.e(DEBUG_TAG, "BAILING OUT: EXTRA_RESPOND_TO is not null and isn't a Class!");
            return;
        }
        String respondTo = s != null ? ((Class<?>)s).getName() : null;

        WorkManager.getInstance(context).enqueue(new OneTimeWorkRequest.Builder(StockWorker.class)
                .setInputData(new Data.Builder()
                        .putLong(EXTRA_REQUEST_ID, work.getLongExtra(EXTRA_REQUEST_ID, -1L))
                        .putInt(EXTRA_REQUEST_FLAGS, work.getIntExtra(EXTRA_REQUEST_FLAGS, 0))
                        .putLong(EXTRA_DATE, date)
                        .putString(EXTRA_RESPOND_TO, respondTo)
                        .putBoolean(DATA_GRATICULE_GLOBALHASH, isGlobalhash)
                        .putString(DATA_GRATICULE_LATITUDE, graticuleLatitude)
                        .putString(DATA_GRATICULE_LONGITUDE, graticuleLongitude)
                        .build())
                .build());
    }

    public StockWorker(Context appContext, WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Unroll all the input data into something that fits the old
        // StockService code, as that's kinda simpler.
        Data data = getInputData();

        long timestamp = data.getLong(EXTRA_DATE, -1L);
        if(timestamp == -1) {
            Log.e(DEBUG_TAG, "BAILING OUT: There's no date!");
            return Result.failure();
        }
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);

        long requestId = data.getLong(EXTRA_REQUEST_ID, -1L);
        int flags = data.getInt(EXTRA_REQUEST_FLAGS, 0);

        // Reconstruct the Graticule.
        Graticule graticule = null;

        if(!data.getBoolean(DATA_GRATICULE_GLOBALHASH, false)) {
            String lat = data.getString(DATA_GRATICULE_LATITUDE);
            String lon = data.getString(DATA_GRATICULE_LONGITUDE);

            if(lat == null || lon == null) {
                Log.e(DEBUG_TAG, "BAILING OUT: Invalid graticule data!  lat: " + lat + "; lon: " + lon);
                return Result.failure();
            }

            graticule = new Graticule(lat, lon);
        }

        // Try to dig up a respondTo class.
        Class<?> respondTo = null;
        String className = data.getString(EXTRA_RESPOND_TO);
        if(className != null && className.length() > 0) {
            try {
                respondTo = Class.forName(className);
            } catch(ClassNotFoundException cnfe) {
                Log.e(DEBUG_TAG, "BAILING OUT: Couldn't load a class named '" + className + "'!");
                return Result.failure();
            }
        }

        // Maybe we'll respond with flags!
        int respFlags = 0;

        Context context = getApplicationContext();

        // First, ask the stock cache if we've got an Info we can throw back.
        Info info = HashBuilder.getStoredInfo(context, cal, graticule);

        // If we got something, great!  Broadcast it right on out!
        if(info != null) {
            respFlags |= FLAG_CACHED;
            Info[] nearby = null;
            if((flags & FLAG_INCLUDE_NEARBY_POINTS) != 0)
                nearby = getNearbyPoints(context, cal, graticule);
            dispatchIntent(context, RESPONSE_OKAY, requestId, flags, respFlags, cal, graticule, info, nearby, respondTo);
        } else {
            // Otherwise, we need to go to the web.
            if(!AndroidUtil.isConnected(context)) {
                // ...if we CAN go to the web, that is.
                Log.i(DEBUG_TAG, "We're not connected, stopping now.");
                dispatchIntent(context, RESPONSE_NO_CONNECTION, requestId, flags, respFlags, cal, graticule, null, null, respondTo);
            } else {
                StockRunner runner = HashBuilder.requestStockRunner(context, cal, graticule);
                runner.runStock();

                // And the results are in!
                int result = runner.getStatus();

                switch(result) {
                    case HashBuilder.StockRunner.ALL_OKAY:
                        // Hooray!  We win!  Dispatch an intent with the info.
                        Log.d(DEBUG_TAG, "Stock's good!  Away it goes!");
                        Info[] nearby = null;
                        if((flags & FLAG_INCLUDE_NEARBY_POINTS) != 0)
                            nearby = getNearbyPoints(context, cal, graticule);
                        dispatchIntent(context, RESPONSE_OKAY, requestId, flags, respFlags, cal, graticule, runner.getLastResultObject(), nearby, respondTo);
                        break;
                    case HashBuilder.StockRunner.ERROR_NOT_POSTED:
                        // Aw.  It's not posted yet.
                        Log.d(DEBUG_TAG, "Stock isn't posted yet.");
                        dispatchIntent(context, RESPONSE_NOT_POSTED_YET, requestId, flags, respFlags, cal, graticule, null, null, respondTo);
                        break;
                    default:
                        // In all other cases, just assume it's a network error.
                        // We either got ERROR_NETWORK, which is just that, or
                        // we got IDLE, BUSY, or ABORTED, none of which make any
                        // sense in this context, which means something went
                        // horribly, horribly wrong.
                        Log.e(DEBUG_TAG, "Network error!");
                        dispatchIntent(context, RESPONSE_NETWORK_ERROR, requestId, flags, respFlags, cal, graticule, null, null, respondTo);
                }
            }
        }

        // If we got here, then all the inputs were valid, per se.  That's what
        // I call a success.
        return Result.success();
    }

    private static void dispatchIntent(Context context,
                                       int responseCode,
                                       long requestId,
                                       int flags,
                                       int respFlags,
                                       Calendar date,
                                       Graticule graticule,
                                       Info info,
                                       Info[] nearby,
                                       @Nullable Class<?> respondTo) {
        // Welcome to central Intent dispatch.  How may I help you?
        Intent intent = new Intent(ACTION_STOCK_RESULT);

        // Make sure the Intent goes to the right place if a return address was
        // explicitly defined.
        if(respondTo != null)
            intent.setClass(context, respondTo);

        // Stuff all the extras into a Bundle.  There's ClassLoader issues on
        // some devices that require us to do it this way (see comments on
        // EXTRA_STUFF).
        Bundle bun = new Bundle();
        bun.putInt(EXTRA_RESPONSE_CODE, responseCode);
        bun.putLong(EXTRA_REQUEST_ID, requestId);
        bun.putInt(EXTRA_REQUEST_FLAGS, flags);
        bun.putInt(EXTRA_RESPONSE_FLAGS, respFlags);
        bun.putSerializable(EXTRA_DATE, date);
        bun.putParcelable(EXTRA_GRATICULE, graticule);
        bun.putParcelable(EXTRA_INFO, info);
        if(nearby != null && nearby.length != 0) {
            bun.putParcelableArray(EXTRA_NEARBY_POINTS, nearby);
        }

        intent.putExtra(EXTRA_STUFF, bun);
        
        // And away it goes!
        Log.d(DEBUG_TAG, "Dispatching intent...");
        context.sendBroadcast(intent);
    }

    private static Info[] getNearbyPoints(Context context, Calendar cal, Graticule g) {
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
                Info info = HashBuilder.getStoredInfo(context, cal, offset);
                if(info == null) {
                    // It's not in the cache.  Try to make it be in the cache.
                    StockRunner runner = HashBuilder.requestStockRunner(context, cal, offset);
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
