/**
 * GHDConstants.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid.util;

import java.text.DecimalFormat;

/**
 * The <code>GHDConstants</code> class doesn't do anything directly.  All it
 * does is serve as a place to store project-wide statics.
 * 
 * @author Nicholas Killewald
 */
public final class GHDConstants {
    /**
     * What was once the preferences base.  Now it shouldn't be used except in
     * cases where we need to convert the user's old preferences into the new
     * default SharedPreferences object.
     */
    public static final String PREFS_BASE = "GeohashDroid";
    
    /** Dummy Graticule that uses the 30W rule (51N, 0W). */
    public static final Graticule DUMMY_YESTERDAY = new Graticule(51, false, 0, true);
    /** Dummy Graticule that doesn't use the 30W rule (38N, 84W). */
    public static final Graticule DUMMY_TODAY = new Graticule(38, false, 84, true);
    
    /**
     * Prefs key where the last latitude is stored. 
     * @see #PREF_REMEMBER_GRATICULE
     * */
    public static final String PREF_DEFAULT_LAT = "DefaultLatitude";
    /** Prefs key where the last longitude is stored. */
    public static final String PREF_DEFAULT_LON = "DefaultLongitude";
    /** Prefs key where we keep track of whether we were in globalhash mode. */
    public static final String PREF_GLOBALHASH_MODE = "GlobalhashMode";
    /** Prefs key specifying coordinate units. */
    public static final String PREF_COORD_UNITS = "CoordUnits";
    /** Prefs key specifying distance units. */
    public static final String PREF_DIST_UNITS = "Units";
    /** Prefs key specifying whether or not to remember the last graticule. */
    public static final String PREF_REMEMBER_GRATICULE = "RememberGraticule";
    /** Prefs key specifying whether or not to auto-zoom. */
    public static final String PREF_AUTOZOOM = "AutoZoom";
    /** Prefs key specifying info box size. */
    public static final String PREF_INFOBOX_SIZE = "InfoBoxSize";
    /** Prefs key specifying info box visibility. */
    public static final String PREF_INFOBOX = "InfoBox";
    /** Prefs key specifying stock cache size. */
    public static final String PREF_STOCK_CACHE_SIZE = "StockCacheSize";
    /** Prefs key specifying to show nearby meetup points. */
    public static final String PREF_NEARBY_POINTS = "NearbyPoints";
    /** Prefs key specifying whether the closest checkbox is ticked. */
    public static final String PREF_CLOSEST = "ClosestOn";
    /** Prefs key specifying wiki user name. */
    public static final String PREF_WIKI_USER = "WikiUserName";
    /** Prefs key specifying wiki user pass. */
    public static final String PREF_WIKI_PASS = "WikiPassword";
    /** Prefs key specifying if the Today checkbox is ticked. */
    public static final String PREF_TODAY = "AlwaysToday";
    /**
     * Prefs key tracking if we've reported on the closeness of the user to the
     * final destination.
     */
    public static final String PREF_CLOSENESS_REPORTED = "ClosenessReported";
    /** Prefs key specifying if the background StockService should be used. */
    public static final String PREF_STOCK_SERVICE = "UseStockService";
    
    /** Prefs value for no infobox at all. */
    public static final String PREFVAL_INFOBOX_NONE = "None";
    /** Prefs value for a small infobox (with compass). */
    public static final String PREFVAL_INFOBOX_SMALL = "Small";
    /** Prefs value for a jumbo infobox (without compass). */
    public static final String PREFVAL_INFOBOX_JUMBO = "Jumbo";
    
    /** Prefs value for metric distances. */
    public static final String PREFVAL_DIST_METRIC = "Metric";
    /** Prefs value for not-metric distances. */
    public static final String PREFVAL_DIST_IMPERIAL = "Imperial";
    
    /** Prefs value for coordinates in degrees. */
    public static final String PREFVAL_COORD_DEGREES = "Degrees";
    /** Prefs value for coordinates in minutes. */
    public static final String PREFVAL_COORD_MINUTES = "Minutes";
    /** Prefs value for coordinates in minutes and seconds. */
    public static final String PREFVAL_COORD_SECONDS = "Seconds";
    
    /** Threshold for the "Accuracy Low" warning (currently 64m). **/
    public static final int LOW_ACCURACY_THRESHOLD = 64;
    /** Threshold for the "Accuracy Really Low" warning (currently 200m). **/
    public static final int REALLY_LOW_ACCURACY_THRESHOLD = 200;
    
    /**
     * Action for picking a graticule. In Geohash Droid, this means to go to
     * GraticuleMap. Though, so long as it returns a
     * net.exclaimindustries.geohashdroid.Graticule object, I'd assume anything
     * could take its place if someone else writes a better graticule picker.
     */
    public static final String PICK_GRATICULE = "net.exclaimindustries.geohashdroid.PICK_GRATICULE";
    
    /**
     * Broadcast intent to tell StockService to retrieve a specific day's stock
     * value.  This will require some extra Intent data to tell it what date it
     * should be retrieving.  Also, assuming this isn't what StockService is
     * already fetching, this will abort anything currently in progress.
     * 
     * TODO: Determine what that extra data should be; a manual fetch request
     * should include the graticule and perform 30W adjustments.
     */
    public static final String STOCK_FETCH = "net.exclaimindustries.geohashdroid.STOCK_FETCH";

    /**
     * Broadcast intent to tell StockService to abort whatever it was doing.
     */
    public static final String STOCK_ABORT = "net.exclaimindustries.geohashdroid.STOCK_ABORT";

    
    /**
     * Broadcast intent sent back by StockService when a result has been
     * retrieved.  In general, you'd register something to pick this up on an
     * as-you-need-it basis, not register it with the manifest.
     * 
     * TODO: This doesn't happen yet.  It'll happen once I convert all the stock
     * grabbing stuff to the background service.
     */
    public static final String STOCK_RESULT = "net.exclaimindustries.geohashdroid.STOCK_RESULT";
    
    /**
     * Date extra passed into the Intent for StockService.  This contains the
     * date to retrieve.
     * 
     * TODO: The date in some format or another?
     */
    public static final String SERVICE_DATE = "date";
    
    /**
     * <p>
     * Flag extra passed into the Intent for StockService.  If present, this
     * flag means StockService should also take whatever it gets and return an
     * appropriate Info object.   If this is passed in, you MUST pass in 
     * SERVICE_GRATICULE else an error will be returned.
     * </p>
     * 
     * <p>
     * Don't pass this during the stock prefeetch, obviously, as that's just
     * supposed to toss the stock into the cache.
     * </p>
     */
    public static final String SERVICE_GET_INFO = "getInfo";
    
    /**
     * Graticule extra passed into the Intent for StockService.  This MUST be
     * passed if SERVICE_GET_INFO is passed in.  If this is NOT a Graticule,
     * this is assumed to be a Globalhash check.  If a 30W Graticule is passed
     * in or a Globalhash is requested, this WILL affect what date is checked.
     */
    public static final String SERVICE_GRATICULE = "graticule";
    
    /**
     * Flag extra passed into the Intent for StockService.  If present, this
     * throws a notification up if a network call is required.  This is used
     * during stock prefetch; during normal use, the user should get some other
     * feedback that indicates a fetch is in progress.
     */
    public static final String SERVICE_NOTIFY = "notify";
    
    /**
     * Arbitrary ID int to pass to StockService.  This will be returned with the
     * BroadcastIntent, if given.
     */
    public static final String SERVICE_REQUEST_ID = "id";

    /**
     * BroadcastIntent response extra whose presence indicates something went
     * wrong.  Exactly what, you ask?  Well, that's what the various sundry
     * SERVICE_RESPONSE_ERROR_* things are for!
     */
    public static final String SERVICE_RESPONSE_ERROR = "error";
    
    /**
     * Error response indicating the requested stock has not been posted yet.
     */
    public static final String SERVICE_RESPONSE_ERROR_STOCK_NOT_POSTED = "notPosted";
    
    /**
     * Error response indicating there's no network connection.
     */
    public static final String SERVICE_RESPONSE_ERROR_NO_NETWORK = "noNetwork";
    
    /**
     * Error response indicating nearly anything else went wrong.
     */
    public static final String SERVICE_RESPONSE_ERROR_UNKNOWN = "unknown";
    
    /**
     * BroadcastIntent response extra containing the stock result.
     */
    public static final String SERVICE_RESPONSE_STOCK = "stock";
    
    /**
     * BroadcastIntent response extra containing an Info object, if it was
     * requested.
     */
    public static final String SERVICE_RESPONSE_INFO = "info";
    
    /** The decimal format for most distances. */
    public static final DecimalFormat DIST_FORMAT = new DecimalFormat("###.######");
    /** The decimal format for most accuracy readouts. */
    public static final DecimalFormat ACCURACY_FORMAT = new DecimalFormat("###.##");
}
