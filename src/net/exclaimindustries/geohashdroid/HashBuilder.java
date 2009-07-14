/**
 * HashBuilder.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.util.Calendar;

import org.apache.http.client.methods.HttpGet;

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
    
    // This is used as the lock to prevent multiple requests from happening at
    // once.  This really shouldn't ever happen, but just in case.
    private static Object locker = new Object();

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
     * Requests a <code>StockRunner</code> object to perform a stock-fetching
     * operation.
     * 
     * TODO: Needs some way to abort the connection, as well as some way to tell
     * if the process is busy right now.
     * 
     * @param c Calendar object with the adventure date requested (this will
     *          account for the 30W Rule, so don't put it in) 
     * @param g Graticule to use
     * @param h Handler to handle the response once it comes in
     */
    public static StockRunner requestStockRunner(Calendar c, Graticule g, Handler h) {
        // Start the thread immediately, then return.  The Handler gets whatever
        // happens next.
        return new StockRunner(c, g, h);
    }
    
    /**
     * <code>StockRunner</code> is what runs the stocks.  It is meant to be run
     * as a thread.  Only one will run at a time for purposes of the stock cache
     * database remaining sane.
     */
    public static class StockRunner implements Runnable {
        /**
         * The possible statuses that can be returned from this StockRunner.
         */
        public enum Status {
            /**
             * This is busy, either with getting the stock price or working out
             * the hash.
             */
            BUSY,
            /**
             * This hasn't been started yet and has no Info object handy.
             */
            IDLE,
            /**
             * This is done, and its last action was successful, in that it got
             * stock data and calculated a new hash.  If this is returned from
             * getStatus, you can get a fresh Info object.
             */
            DONE_OKAY,
            /**
             * The last request couldn't be met because the stock value wasn't
             * posted for the given day yet.
             */
            ERROR_NOT_POSTED,
            /**
             * The last request couldn't be met because of some server error.
             */
            ERROR_SERVER,
            /**
             * The user aborted the request.
             */
            ABORTED
        }

    	private Calendar mCal;
    	private Graticule mGrat;
    	private Handler mHandler;
    	private HttpGet mRequest;
    	private Status mStatus;
    	
    	private StockRunner(Calendar c, Graticule g, Handler h) {
    		mCal = c;
    		mGrat = g;
    		mHandler = h;
    		mStatus = Status.IDLE;
    	}
    	
        @Override
        public void run() {
        	Info toReturn;
            // Grab a lock on our lock object.
        	synchronized(locker) {
        		// First, if this exists in the cache, use it instead of going
        		// off to the internet.
        		toReturn = getStoredInfo(mCal, mGrat);
        		if(toReturn != null) {
        			// TODO: Send this data back to Handler and return!
        			// return;
        		}
        		
        	}
        }
        
        /**
         * Updates the Handler that will be informed when this thread is done.
         * 
         * @param h the Handler what gets updaterin'.
         */
        public void changeHandler(Handler h) {
        	mHandler = h;
        }
        
        /**
         * Abort the current connection, if one exists.
         */
        public void abort() {
        	if(mRequest != null) mRequest.abort();
        }
        
        /**
         * Returns whatever the current status is.  This is returned as a part
         * of the Handler callback, but if, for instance, the Activity was
         * destroyed between the call to get the stock value and the time it
         * actually got it, the new caller will need to come here for the status.
         *
         * @return the current status
         */
        public Status getStatus() {
            return mStatus;
        }
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

}
