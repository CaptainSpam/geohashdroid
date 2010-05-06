/**
 * HashBuilder.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.security.InvalidParameterException;
import java.util.Calendar;

import net.exclaimindustries.tools.DateTools;
import net.exclaimindustries.tools.HexFraction;
import net.exclaimindustries.tools.MD5Tools;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * <p>
 * The <code>HashBuilder</code> class encompasses a whole bunch of static
 * methods to grab and store the day's DJIA and calculate the hash, given a
 * <code>Graticule</code> object.
 * </p>
 * 
 * <p>
 * This also encompasses <code>StockRunner</code>, which goes out to the web
 * to get the current stock data.  <code>HashBuilder</code> itself, though,
 * does the hash calculations.
 * </p>
 * 
 * <p>
 * This implementation uses the Crox site to get the DJIA, falling back to
 * the peeron.com site if Crox can't figure it out (upstream faults, server
 * failure, etc).
 * </p>
 * 
 * @author Nicholas Killewald
 */
public class HashBuilder {
    
    // This is used as the lock to prevent multiple requests from happening at
    // once.  This really shouldn't ever happen, but just in case.
    private static Object locker = new Object();
    
    private static final String DEBUG_TAG = "HashBuilder";
    
    private static StockStoreDatabase mStore;
    // This set allows for quick reloading of the most recent stock and hash in
    // a given instance of the program, bypassing the SQLite database, as well
    // as allow for a small cache even if the SQLite database is turned off by
    // preferences.
    private static Info mLastInfo;
    private static Info mTwoInfosAgo;

    /**
     * <code>StockRunner</code> is what runs the stocks.  It is meant to be run
     * as a thread.  Only one will run at a time for purposes of the stock cache
     * database remaining sane.  Once it has the data, it'll go back to the
     * static methods of HashBuilder to make the Info bundle.
     */
    public static class StockRunner implements Runnable {
        /**
         * This is busy, either with getting the stock price or working out
         * the hash.
         */
        public static final int BUSY = 0;
        /**
         * This hasn't been started yet and has no Info object handy.
         */
        public static final int IDLE = 1;
        /**
         * This is done, and its last action was successful, in that it got
         * stock data and calculated a new hash.  If this is returned from
         * getStatus, you can get a fresh Info object.
         */
        public static final int ALL_OKAY = 2;
        /**
         * The last request couldn't be met because the stock value wasn't
         * posted for the given day yet.
         */
        public static final int ERROR_NOT_POSTED = 3;
        /**
         * The last request couldn't be met because of some server error.
         */
        public static final int ERROR_SERVER = 4;
        /**
         * The user aborted the request.
         */
        public static final int ABORTED = 5;
    
        private Context mContext;
    	private Calendar mCal;
    	private Graticule mGrat;
    	private Handler mHandler;
    	private HttpGet mRequest;
    	private int mStatus;
    	
        // This may be expanded later to allow a user-definable list, hence why
        // it doesn't follow the usual naming conventions I use.  Of course, in
        // THAT case, we'd need to make it not be a raw array.  The general form
        // is that %Y is the four-digit year, %m is the zero-padded month, and
        // %d is (wait for it...) the zero-padded date.
        private final static String[] mServers = { "http://geo.crox.net/djia/%Y/%m/%d",
            "http://irc.peeron.com/xkcd/map/data/%Y/%m/%d"};

    	
    	private StockRunner(Context con, Calendar c, Graticule g, Handler h) {
    	    mContext = con;
    		mCal = c;
    		mGrat = g;
    		mHandler = h;
    		mStatus = IDLE;
    	}
    	
        @Override
        public void run() {
        	Info toReturn;
        	String stock;
        	
            // First, we need to adjust the calendar in the event we're in the
            // range of the 30W rule.  To that end, sCal is for stock calendar.
            Calendar sCal = Info.makeAdjustedCalendar(mCal, mGrat);
            
            // Grab a lock on our lock object.
        	synchronized(locker) {
        		// First, if this exists in the cache, use it instead of going
        		// off to the internet.  This method uses the ACTUAL date, so
        	    // we can ignore sCal for now.
        		toReturn = getStoredInfo(mContext, mCal, mGrat);
        		if(toReturn != null) {
                    // Hey, whadya know, we've got something!  Send this data
        		    // back to the Handler and return!
        		    mStatus = ALL_OKAY;
        		    sendMessage(toReturn);
        			return;
        		}
        		
        		// If that failed, we need a stock price.  First, check to see
        		// if it's in the database.  
        		stock = getStoredStock(mContext, sCal);
        		
        		// If we found something, great!  Let's move on!
        		if(stock == null) {
            		// Otherwise, we need to start heading off to the net.
            		mStatus = BUSY;
            		try {
            		    stock = fetchStock(sCal);
                        // If this didn't throw an exception AND it's not blank,
                        // stash it in the database.
                        if(stock.trim().length() != 0)
                            storeStock(mContext, sCal, stock);
            		} catch (FileNotFoundException fnfe) {
            		    // If we got a 404, assume it's not posted yet.
            		    mStatus = ERROR_NOT_POSTED;
            		    sendMessage(null);
            		    return;
            		} catch (IOException ioe) {
            		    // If we got anything else, assume a problem.
            		    mStatus = ERROR_SERVER;
            		    sendMessage(null);
            		    return;
            		}
            		
            		if(mStatus == ABORTED) {
            		    // If we aborted, send that back, too.
            		    sendMessage(null);
            		    return;
            		}
        		}
        	}

    		// We assemble an Info object and get ready to return it.  This uses
        	// the REAL date so we display the right thing on the detail screen
        	// (or anywhere else; the point is, we can report to the user if
        	// they're in the influence of the 30W Rule).
            toReturn = createInfo(mCal, stock, mGrat);
                
    		// Good!  Now, we can stash this away in the database for later.
    		storeInfo(mContext, toReturn);
        	
        	// And we're done!
        	mStatus = ALL_OKAY;
        	sendMessage(toReturn);
        }
        
        private void sendMessage(Object toReturn) {
            // If mHandler is null, either this wasn't set up right or we've
            // been told to abort and need to put the brakes on quick.
            if(mHandler != null)
            {
                Message m = Message.obtain(mHandler, mStatus, toReturn);
                m.sendToTarget();
            }   
        }
        
        private String fetchStock(Calendar sCal) throws FileNotFoundException, IOException {
            // Now, generate a string for the URL.
            String sMonthStr;
            String sDayStr;

            if (sCal.get(Calendar.MONTH) + 1 < 10)
                sMonthStr = "0" + (sCal.get(Calendar.MONTH) + 1);
            else
                sMonthStr = new Integer(sCal.get(Calendar.MONTH) + 1).toString();

            if (sCal.get(Calendar.DAY_OF_MONTH) < 10)
                sDayStr = "0" + sCal.get(Calendar.DAY_OF_MONTH);
            else
                sDayStr = new Integer(sCal.get(Calendar.DAY_OF_MONTH)).toString();

            // Good, good! Now, to the web!  Go through our list of sites in
            // order until we find an answer, we bottom out, or we abort.  In
            // terms of what we report to the user, "Server error" is lowest-
            // priority, with "Stock not posted" rating above it.  That is to
            // say, if one server reports and error but another one explicitly
            // tells us the stock wasn't found, the latter is what we use.  Of
            // course, if we get an abort request, that takes absolute
            // precedence.
            int curStatus = ERROR_SERVER;
            String result = "";
            
            for(String s : mServers) {
                // Do all our substitutions...
                String location = s.replaceAll("%Y", Integer.toString(sCal.get(Calendar.YEAR)));
                location = location.replaceAll("%m", sMonthStr);
                location = location.replaceAll("%d", sDayStr);
                Log.d(DEBUG_TAG, "Trying " + location + "...");
                
                // And go fetch!
                HttpClient client = new DefaultHttpClient();
                mRequest = new HttpGet(location);
                HttpResponse response;
                
                try {
                    response = client.execute(mRequest);
                } catch (IOException e) {
                    // If there was an exception, but we aborted, return a blank
                    // response (aborting throws an IOException).  If not, there
                    // was a legitimate problem with this particular server.
                    if(mStatus == ABORTED) {
                        return "";
                    }
                    continue;
                }
                
                // Make sure we've caught an abort...
                if(mStatus == ABORTED) {
                    return "";
                }
                
                if (response.getStatusLine().getStatusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                    curStatus = ERROR_NOT_POSTED;
                } else if (response.getStatusLine().getStatusCode() != HttpURLConnection.HTTP_OK) {
                    // A non-okay response that isn't a 404 is bad.  Count this
                    // one as ERROR_SERVER and just continue.
                    continue;
                }
                
                // Well, we got this far!  Let's read!
                result = getStringFromStream(response.getEntity().getContent());
                
                // With that done, we try to convert the output to the float.
                // If this fails, we got bogus data and should roll on.
                try {
                    new Float(result);
                } catch (NumberFormatException nfe) {
                    result = "";
                    continue;
                }
                
                // We survived!  Set the status flag and keep going!
                curStatus = ALL_OKAY;
                break;
            }
            
            // If we got this far and we still had an ERROR_SERVER or
            // ERROR_NOT_POSTED, throw 'em.  We failed.  If we got an ABORTED,
            // return a blank.
            if(mStatus == ABORTED)
                return "";
            else if(curStatus == ERROR_NOT_POSTED)
                throw new FileNotFoundException();
            else if(curStatus == ERROR_SERVER)
                throw new IOException();



            // If we finally, FINALLY got this far, we've got a successful stock!
            return result;
        }
        
        /**
         * Takes the given stream and makes a String out of whatever data it has. Be
         * really careful with this, as it will just attempt to read whatever's in
         * the stream until it stops, meaning it'll spin endlessly if this isn't the
         * sort of stream that ends.
         * 
         * @param stream
         *            InputStream to read from
         * @return a String consisting of the data from the stream
         */
        protected static String getStringFromStream(InputStream stream)
                throws IOException {
            BufferedReader buff = new BufferedReader(new InputStreamReader(stream));

            // Load it up...
            StringBuffer tempstring = new StringBuffer();
            char bean[] = new char[1024];
            int read = 0;
            while ((read = buff.read(bean)) != -1) {
                tempstring.append(bean, 0, read);
            }

            return tempstring.toString();
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
        	if(mRequest != null)
    	    {
        	    // Bail out of the request (if there is one)...
    	        mRequest.abort();
    	    }
	        // Put the brakes on the handler...
	        mHandler = null;
	        // And change status.
	        mStatus = ABORTED;
        }
        
        /**
         * Returns whatever the current status is.  This is returned as a part
         * of the Handler callback, but if, for instance, the Activity was
         * destroyed between the call to get the stock value and the time it
         * actually got it, the new caller will need to come here for the status.
         *
         * @return the current status
         */
        public int getStatus() {
            return mStatus;
        }
    }

    // You don't construct a HashBuilder!  You gotta EARN it!
    private HashBuilder() { }
   
    /**
     * Initializes HashBuilder.  This should be called only once.  Well, it can
     * be called more often, but it won't do anything past the first time.
     */
    public static synchronized void initialize(Context c) {
        if(mStore == null) {
            mStore = new StockStoreDatabase(c).init();
        }
    }
    
    /**
     * Initializes and returns a StockStoreDatabase object.  This should be used
     * in ALL cases the mStore is needed to ensure it actually exists.  It can,
     * for instance, stop existing if the app is destroyed to reclaim memory.
     * 
     * @param c Context with which StockStoreDatabase will be initialized.
     * @return
     */
    private static synchronized StockStoreDatabase getStore(Context c) {
        if(mStore == null) {
            mStore = new StockStoreDatabase(c).init();
        }
        
        return mStore;
    }
    
    /**
     * Requests a <code>StockRunner</code> object to perform a stock-fetching
     * operation.
     * 
     * @param c Calendar object with the adventure date requested (this will
     *          account for the 30W Rule, so don't put it in) 
     * @param g Graticule to use
     * @param h Handler to handle the response once it comes in
     */
    public static StockRunner requestStockRunner(Context con, Calendar c, Graticule g, Handler h) {
        return new StockRunner(con, c, g, h);
    }
    
    /**
     * Checks if the stock price for the given date and graticule (accounting
     * for the 30W rule) is stored and can be retrieved without going to the
     * internet.  If this returns true, the interface should NOT display a popup
     * and should expect to recieve a new Info object quickly.
     * 
     * @param con Context used to retrieve the database, if needed
     * @param c Calendar object with the adventure date requested (this will
     *          account for the 30W Rule, so don't put it in) 
     * @param g Graticule to use
     * @return true if the stock value is stored, false if we need to go to the
     *         internet for it
     */
    public static boolean hasStockStored(Context con, Calendar c, Graticule g) {
//    	Calendar sCal = Info.makeAdjustedCalendar(c, g);
    	
        return getQuickCache(c, g) != null || getStore(con).getInfo(c, g) != null;
    }

    /**
     * Attempt to construct an Info object from stored info and return it,
     * explicitly without going to the internet.  If this can't be done, this
     * will return null.
     *
     * @param con Context used to retrieve the database, if needed
     * @param c Calendar object with the adventure date requested (this will
     *          account for the 30W Rule, so don't put it in) 
     * @param g Graticule to use
     * @return the Info object for the given data, or null if can't be built
     *         without going to the internet.
     */
    public static Info getStoredInfo(Context con, Calendar c, Graticule g) {
    	// First, check the quick cache.
//    	Calendar sCal = Info.makeAdjustedCalendar(c, g);

        // If it's in the quick cache, use it.
        Log.d(DEBUG_TAG, "Checking caches for " + DateTools.getDateString(c) + (g.uses30WRule() ? " with 30W rule" : " without 30W rule"));
        Info result = getQuickCache(c, g);
        if(result != null) {
            Log.d(DEBUG_TAG, "Data found in quickcache!");
            return cloneInfo(result, g);
        }
    	
        // Otherwise, check the stock cache.
        Info i = getStore(con).getInfo(c, g);
        
        if(i == null)
            return null;
            
        Log.d(DEBUG_TAG, "Data found in database!  Quickcaching...");
        // If it was in the main cache but not the quick cache, quick cache it.
        quickCache(i);
        return i;
    }
    
    /**
     * Attempt to get the stock value stored in the database for the given
     * already-adjusted date.  This won't go to the internet; that's the
     * responsibility of a StockRunner.
     * 
     * @param con Context used to retrieve the database, if needed 
     * @param c already-adjusted date to check
     * @return the String representation of the stock, or null if it's not there
     */
    public static String getStoredStock(Context con, Calendar c) {
        // We don't quickcache the stock values.
        Log.d(DEBUG_TAG, "Going to the database for a stock for " + DateTools.getDateString(c));
        
        return getStore(con).getStock(c);
    }
    
    /**
     * Puts the given data into the quick cache.  Note that the Calendar object
     * is the date of the stock, not the date of the expedition.
     * 
     * @param sCal stock calendar to store
     * @param stock stock value to store
     */
    private static void quickCache(Info i) {
        // Slide over!
        mTwoInfosAgo = mLastInfo;
        mLastInfo = i;
    }
    
    /**
     * Stores Info data away in the database.  This won't do anything if the
     * day's Info already exists therein.
     * 
     * @param con Context used to retrieve the database, if needed
     * @param i an Info bundle with everything we need
     */
    private synchronized static void storeInfo(Context con, Info i) {
    	// First, replace the last-known results.
    	quickCache(i);
    	
    	StockStoreDatabase store = getStore(con);
    	
    	// Then, write it to the database.
        store.storeInfo(i);
        store.cleanup();
    }
    
    private synchronized static void storeStock(Context con, Calendar cal, String stock) {
        StockStoreDatabase store = getStore(con);
        
        store.storeStock(cal, stock);
        store.cleanup();
    }
    
    /**
     * Cleans up the database with whatever cleanup needs to be done.
     * Generally, this means pruning it.
     * 
     * @param con Context used to retrieve the database, if needed
     */
    public synchronized static void cleanupDatabase(Context con) {
        getStore(con).cleanup();
    }

    /**
     * Wipes out the entire stock cache.  No, seriously.
     * 
     * @param con Context used to retrieve the database, if needed
     * @return true on success, false on failure
     */
    public synchronized static boolean deleteCache(Context con) {
        return getStore(con).deleteCache();
    }
    
    /**
     * Build an Info object.  Since this assumes we already have a stock price
     * AND the Graticule can tell us if we need to use the 30W rule, use the
     * REAL date on the Calendar object.
     * 
     * @param c date from which this hash comes
     * @param stockPrice effective stock price (already adjusted for the 30W Rule)
     * @param g the graticule in question
     * @return
     */
    protected static Info createInfo(Calendar c, String stockPrice, Graticule g) {
        // This creates the Info object that'll go right back to whatever was
        // calling it.  In general, this is the Handler in StockRunner.
        
        // So to that end, we first build up the hash.
        String hash = makeHash(c, stockPrice);
        
        // Then, get the latitude and longitude from that.
        double lat = getLatitude(g, hash);
        double lon = getLongitude(g, hash);
        
        // And finally...
        return new Info(lat, lon, g, c);
    }
    
    /**
     * Builds a new Info object by applying a new Graticule to an existing Info
     * object.  That is to say, change the destination of an Info object to
     * somewhere else.  As if it were the same day and same stock value (and
     * thus the same hash).  Note that this will throw an exception if the 
     * existing Info's 30W-alignment isn't the same as the new Graticule's,
     * because that would require a trip back to the internet, and by this
     * point, we should know that we don't need to do so.
     * 
     * @param i old Info object to clone
     * @param g new Graticule to apply
     * @throws InvalidParameterException the Info and Graticule do not lie on the same side of the 30W line.
     * @return
     */
    protected static Info cloneInfo(Info i, Graticule g) {
        // This sort of requires the 30W-itude of both to match.
        if(i.getGraticule().uses30WRule() != g.uses30WRule())
            throw new InvalidParameterException("The given Info and Graticule do not lie on the same side of the 30W line; this should not have happened.");
        
        // Get the destination set...
        double lat = (g.getLatitude() + i.getLatitudeHash()) * (g.isSouth() ? -1 : 1);
        double lon = (g.getLongitude() + i.getLongitudeHash()) * (g.isWest() ? -1 : 1);
        
        // Then...
        return new Info(lat, lon, g, i.getCalendar());
    }
    
    /**
     * Generate the hash string from the date and stock price.  The REAL date,
     * that is.  Not a 30W Rule-adjusted date.
     * 
     * @param c date to use
     * @param stockPrice stock price to use
     * @return the hash you're looking for
     */
    protected static String makeHash(Calendar c, String stockPrice) {
        // Just reset the hash. This can be handy alone if the graticule has
        // changed.  Remember, c is the REAL date, not the STOCK date!
        String monthStr;
        String dayStr;

        // Zero-pad the month and date...
        if (c.get(Calendar.MONTH) + 1 < 10)
            monthStr = "0" + (c.get(Calendar.MONTH) + 1);
        else
            monthStr = new Integer(c.get(Calendar.MONTH) + 1).toString();

        if (c.get(Calendar.DAY_OF_MONTH) < 10)
            dayStr = "0" + c.get(Calendar.DAY_OF_MONTH);
        else
            dayStr = new Integer(c.get(Calendar.DAY_OF_MONTH)).toString();

        // And here it goes!
        String fullLine = c.get(Calendar.YEAR) + "-" + monthStr + "-"
                + dayStr + "-" + stockPrice;
        return MD5Tools.MD5hash(fullLine);
    }

    private static Info getQuickCache(Calendar sCal, Graticule g) {
    	// We don't use Calendar.equals here, as that checks all properties,
    	// including potentially some we don't really care about.
        
        // At any rate, first off, the most recent date/30W combo.  Then, the
        // second-most.  Failing THAT, return null.
        if(mLastInfo != null) {
            Calendar stored = mLastInfo.getCalendar();
            
            if(stored.get(Calendar.MONTH) ==  sCal.get(Calendar.MONTH)
                    && stored.get(Calendar.DAY_OF_MONTH) ==  sCal.get(Calendar.DAY_OF_MONTH)
                    && stored.get(Calendar.YEAR) ==  sCal.get(Calendar.YEAR)
                    && mLastInfo.getGraticule().uses30WRule() == g.uses30WRule()) {
                Log.d(DEBUG_TAG, "Hash data is in quick cache (mLastInfo): " + mLastInfo.getLatitudeHash() + ", " + mLastInfo.getLongitudeHash());
                return mLastInfo;
            }
        }
        
        if(mTwoInfosAgo != null) {
            Calendar stored = mTwoInfosAgo.getCalendar();
            
            if(stored.get(Calendar.MONTH) ==  sCal.get(Calendar.MONTH)
                    && stored.get(Calendar.DAY_OF_MONTH) ==  sCal.get(Calendar.DAY_OF_MONTH)
                    && stored.get(Calendar.YEAR) ==  sCal.get(Calendar.YEAR)
                    && mTwoInfosAgo.getGraticule().uses30WRule() == g.uses30WRule()) {
                Log.d(DEBUG_TAG, "Hash data is in quick cache (mTwoInfosAgo): " + mTwoInfosAgo.getLatitudeHash() + ", " + mTwoInfosAgo.getLongitudeHash());
                return mTwoInfosAgo;
            }
        }
        
        return null;
    }
    
    /**
     * Gets the latitude value of the location for the current date. This is
     * attached to the current graticule integer value to produce the longitude.
     * 
     * @return the fractional latitude value
     */
    private static double getLatitudeHash(String hash) {
        String chunk = hash.substring(0, 16);
        return HexFraction.calculate(chunk);
    }

    /**
     * Gets the longitude value of the location for the current date. This is
     * attached to the current graticule integer value to produce the latitude.
     * 
     * @return the fractional longitude value
     */
    private static double getLongitudeHash(String hash) {
        String chunk = hash.substring(16, 32);
        return HexFraction.calculate(chunk);
    }

    private static double getLatitude(Graticule g, String hash) {
        int lat = g.getLatitude();
        if (g.isSouth()) {
            return (lat + getLatitudeHash(hash)) * -1;
        } else {
            return lat + getLatitudeHash(hash);
        }
    }

    private static double getLongitude(Graticule g, String hash) {
        int lon = g.getLongitude();
        if (g.isWest()) {
            return (lon + getLongitudeHash(hash)) * -1;
        } else {
            return lon + getLongitudeHash(hash);
        }
    }

}
