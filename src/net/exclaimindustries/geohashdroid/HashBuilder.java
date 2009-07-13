/**
 * HashBuilder.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.util.Calendar;

import android.os.Handler;

/**
 * <p>
 * The <code>HashBuilder</code> class encompasses a whole bunch of static
 * methods to grab and store the day's DJIA and calculate the hash, given a
 * <code>Graticule</code> object.
 * </p>
 * 
 * <p>
 * This implementation uses the peeron.com site to get the DJIA
 * (http://irc.peeron.com/xkcd/map/data/2008/12/03).
 * </p>
 * 
 * @author Nicholas Killewald
 */
public class HashBuilder {
    /**
     * The possible statuses that can be returned from this HashBuilder.
     */
    public enum Status {
        /**
         * HashBuilder is busy, either with getting the stock price or working
         * out the hash.
         */
        BUSY,
        /**
         * HashBuilder hasn't been started yet and has no Info object handy.
         */
        IDLE,
        /**
         * HashBuilder is done, and its last action was successful, in that it
         * got stock data and calculated a new hash.  If this is returned from
         * getStatus, you can get a fresh Info object.
         */
        DONE_OKAY,
        /**
         * The last request couldn't be met because the stock value wasn't
         * posted for the given day yet.  The last Info object is NOT reset at
         * this point.  If you really really want, you CAN get the old one.
         */
        ERROR_NOT_POSTED,
        /**
         * The last request couldn't be met because of some server error.  The
         * last Info object is NOT reset at this point.  If, for whatever
         * reason, you actually WANT that data, you can get it.
         */
        ERROR_SERVER,
        /**
         * The user aborted the most recent request.  Note that this can only
         * reliably be done during the HTTP connection phase (if one is needed).
         * Thus, if this status is returned, the previous Info object has NOT
         * been reset yet.
         */
        ABORTED
    }

    // The most recent status.
    private static Status mLastStatus = IDLE;

    // Hold on to the request here so we can abort it if requested.  There
    // should be only one request going at any time, so this may also be
    // queried to determine if HashBuilder is busy.
    private static HttpGet mRequest;

    // Hold on to the most recent Info object generated.  This might be the most
    // horribly wrong way to handle a nasty synchronization problem involving
    // 
    public static Info mLastInfo;

    // You don't construct a HashBuilder!  You gotta EARN it!
    private HashBuilder() { }
   
    /**
     * Initializes HashBuilder.  This should be called only once.  Well, it can
     * be called more often, but it won't do anything past the first time.
     * 
     * TODO: And come to think of it, it doesn't do anything now, either.
     */
    public static void initialize() {
        // TODO: PUT INIT STUFF HERE ONCE NEEDED
    }
    
    /**
     * Starts a request to get an Info object based on the date and graticule.
     * The response will come to the Handler specified.  This will return right
     * away and kick off a new thread to do the job.
     * 
     * TODO: Needs some way to abort the connection, as well as some way to tell
     * if the process is busy right now.
     * 
     * @param c Calendar object with the adventure date requested (this will
     *          account for the 30W Rule, so don't put it in) 
     * @param g Graticule to use
     * @param h Handler to handle the response once it comes in
     */
    public static void requestInfo(Calendar c, Graticule g, Handler h) {
        
    }
    
    /**
     * Checks if the stock price for the given date and graticule (accounting
     * for the 30W rule) is stored and can be retrieved without going to the
     * internet.  If this returns true, the interface should NOT display a popup
     * and should expect to recieve a new Info object quickly.
     * 
     * @param c Calendar object with the adventure date requested (this will
     *          account for the 30W Rule, so don't put it in) 
     * @param g Graticule to use
     * @return true if the stock value is stored, false if we need to go to the
     *         internet for it
     */
    public static boolean hasStockStored(Calendar c, Graticule g) {
        // This is always false until stock caching is working.
        return false;
    }

    /**
     * Attempt to construct an Info object from stored info and return it,
     * explicitly without going to the internet.  If this can't be done, this
     * will return null.
     *
     * @param c Calendar object with the adventure date requested (this will
     *          account for the 30W Rule, so don't put it in) 
     * @param g Graticule to use
     * @return the Info object for the given data, or null if can't be built
     *         without going to the internet.
     */
    public static Info getStoredInfo(Calendar c, Graticule g) {
        // This is always null until stock caching is working.
        return null;
    }

    /**
     * Returns whatever the last status was.  This is returned as a part of the
     * Handler callback, but if, for instance, the Activity was destroyed
     * between the call to get the stock value and the time it actually got it,
     * the new caller will need to come here for the status.
     *
     * @return the last status encountered
     */
    public static Status getLastStatus() {
        return mLastStatus;
    }
}
