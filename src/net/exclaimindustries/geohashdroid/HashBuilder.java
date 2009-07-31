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
import java.util.Calendar;

import net.exclaimindustries.tools.HexFraction;
import net.exclaimindustries.tools.MD5Tools;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

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
    
//    private static final String DEBUG_TAG = "HashBuilder";
    
    private static StockStoreDatabase mStore;

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
    
    	private Calendar mCal;
    	private Graticule mGrat;
    	private Handler mHandler;
    	private HttpGet mRequest;
    	private int mStatus;
    	
    	private StockRunner(Calendar c, Graticule g, Handler h) {
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
            Calendar sCal = (Calendar)mCal.clone();
            if(mGrat.uses30WRule())
                sCal.add(Calendar.DAY_OF_MONTH, -1);
            
            // Grab a lock on our lock object.
        	synchronized(locker) {
        		// First, if this exists in the cache, use it instead of going
        		// off to the internet.  This method uses the ACTUAL date, so
        	    // we can ignore sCal for now.
        		toReturn = getStoredInfo(mCal, mGrat);
        		if(toReturn != null) {
                    // Hey, whadya know, we've got something!  Send this data
        		    // back to the Handler and return!
        		    mStatus = ALL_OKAY;
        		    sendMessage(toReturn);
        			return;
        		}
        		
        		// Otherwise, we need to start heading off to the net.
        		mStatus = BUSY;
        		try {
        		    stock = fetchStock(sCal);
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

    		// We assemble an Info object and get ready to return it.  This uses
        	// the REAL date so we display the right thing on the detail screen
        	// (or anywhere else; the point is, we can report to the user if
        	// they're in the influence of the 30W Rule).
            toReturn = createInfo(mCal, stock, mGrat);
                
    		// Good!  Now, we can stash this away in the database for later.
    		storeData(toReturn);
        	
        	// And we're done!
        	mStatus = ALL_OKAY;
        	sendMessage(toReturn);
        }
        
        private void sendMessage(Object toReturn) {
            Message m = Message.obtain(mHandler, mStatus, toReturn);
            m.sendToTarget();
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

            // Good, good! Now, to the web!
            String location = "http://irc.peeron.com/xkcd/map/data/"
                    + sCal.get(Calendar.YEAR) + "/" + sMonthStr + "/" + sDayStr;
            
            HttpClient client = new DefaultHttpClient();
            mRequest = new HttpGet(location);
            HttpResponse response;
            try {
                response = client.execute(mRequest);
            } catch (IOException e) {
                // If there was an exception, but we aborted, return a blank
                // response (aborting throws an IOException).  If not, there was
                // a legitimate problem, so throw back the exception.
                if(mStatus == ABORTED) {
                    return "";
                }
                throw e;
            }
            
            // If we aborted at this point, just return. The calling method MUST
            // NOT read anything else from here at this point.
            if (mStatus == ABORTED)
                return "";

            // Response obtained! Now let's get to digging...
            if (response.getStatusLine().getStatusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                throw new FileNotFoundException();
            } else if (response.getStatusLine().getStatusCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException();
            }

            String result = getStringFromStream(response.getEntity().getContent());

            // With that done, we try to convert the output to the float. If this
            // fails, we got bogus data.
            try {
                new Float(result);
            } catch (NumberFormatException nfe) {
                // I'm recasting this as an IOException because this means there's
                // a serious communication problem with the server.
                throw new IOException(
                        "The stock server was contacted, but it wasn't returning parseable stock data.");
            }

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
    	        mRequest.abort();
    	        mStatus = ABORTED;
    	    }
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
     * Requests a <code>StockRunner</code> object to perform a stock-fetching
     * operation.
     * 
     * @param c Calendar object with the adventure date requested (this will
     *          account for the 30W Rule, so don't put it in) 
     * @param g Graticule to use
     * @param h Handler to handle the response once it comes in
     */
    public static StockRunner requestStockRunner(Calendar c, Graticule g, Handler h) {
        return new StockRunner(c, g, h);
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
        return mStore.getStock(c, g) != null;
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
        String result = mStore.getStock(c, g);
        
        if(result == null) return null;
        
        return createInfo(c, result, g);
    }
    
    /**
     * Stores stock data away in the database.  This won't do anything if the
     * day's stock value already exists therein.
     * 
     * @param i an Info bundle with everything we need
     */
    private synchronized static void storeData(Info i) {
        mStore.storeInfo(i);
        mStore.cleanup();
    }
    
    /**
     * Cleans up the database with whatever cleanup needs to be done.
     * Generally, this means pruning it.
     */
    public synchronized static void cleanupDatabase() {
        mStore.cleanup();
    }

    /**
     * Wipes out the entire stock cache.  No, seriously.
     */
    public synchronized static void deleteCache() {
        mStore.deleteCache();
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
        return new Info(lat, lon, g, c, stockPrice);
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
