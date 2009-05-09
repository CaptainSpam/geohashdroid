/**
 * HashMaker.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.util.Calendar;
import java.net.*;
import java.io.*;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import net.exclaimindustries.tools.HexFraction;
import net.exclaimindustries.tools.MD5Tools;

/**
 * Retrieves and constructs the hash for a given day. By default, this pulls in
 * the current date's hash (or the most recent date if today can't be retrieved
 * for whatever reason). It can also be told to find any past date's hash.
 * 
 * This implementation uses the peeron.com site to get the DJIA
 * (http://irc.peeron.com/xkcd/map/data/2008/12/03).
 * 
 * @author Nicholas Killewald
 */
public class HashMaker implements Serializable {
    private static final long serialVersionUID = 1L;
    // The Calendar object holding the current (or effective) date. We'll get
    // stock data appropriately.
    private Calendar cal;
    // The Calendar object holding the effective stock date for the 30W Rule.
    private Calendar sCal;

    // The Graticule we're looking at.
    private Graticule graticule;

    // The DJIA stock value for the date specified. Depending on the
    // Graticule, this may be yesterday's stock.
    private String stock;
    // The raw hash of the date and stock.
    private String hash;
    // Our request (needed to abort it if need be).
    private static HttpGet mRequest;
    // Whether the request was aborted.
    private boolean mAborted = false;

    /**
     * Creates a new HashMaker using the current date. This goes to the web for
     * stock data right away.
     * 
     * @param graticule
     *            initial Graticule to use for this
     * @throws FileNotFoundException
     *             the date could not be found on the server (usually means the
     *             data isn't up yet)
     * @throws IOException
     *             some other sort of IOException occurred
     */
    public HashMaker(Graticule graticule) throws FileNotFoundException,
            IOException {
        // Grab today from Calendar.
        this(graticule, Calendar.getInstance());
    }

    /**
     * Creates a new HashMaker using the given Calendar object. This goes to the
     * web for stock data right away.
     * 
     * @param graticule
     *            initial Graticule to use for this
     * @param cal
     *            Calendar from which the date shall spring
     * @throws FileNotFoundException
     *             the date could not be found on the server (usually means the
     *             data isn't up yet)
     * @throws IOException
     *             some other sort of IOException occurred
     */
    public HashMaker(Graticule graticule, Calendar cal)
            throws FileNotFoundException, IOException {
        this.graticule = graticule;
        setCalendar(cal);
    }

    /**
     * Creates a new HashMaker using the given date data. Months are from 0 to
     * 11, like in a Calendar object. Or, more appropriately, a DatePicker
     * object. Aaaaand this goes to the web for stock data right away.
     * 
     * @param graticule
     *            initial Graticule to use for this
     * @param year
     *            four-digit year
     * @param month
     *            current month, 0 to 11 (NOT 1 to 12)
     * @param day
     *            current day of the month
     * @throws FileNotFoundException
     *             the date could not be found on the server (usually means the
     *             data isn't up yet)
     * @throws IOException
     *             some other sort of IOException occurred
     */

    public HashMaker(Graticule graticule, int year, int month, int day)
            throws FileNotFoundException, IOException {
        this.graticule = graticule;

        Calendar cal = Calendar.getInstance();
        cal.setLenient(true);
        cal.set(year, month, day);

        setCalendar(cal);
    }

    private synchronized void fetchStock() throws FileNotFoundException,
            IOException {
        mAborted = false;
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
        HttpResponse response = client.execute(mRequest);
        mRequest = null;

        // If we aborted at this point, just return. The calling method MUST
        // NOT read anything else from here at this point.
        if (mAborted)
            return;

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
        stock = result;
    }

    private void makeHash() {
        // Just reset the hash. This can be handy alone if the graticule has
        // changed.
        String monthStr;
        String dayStr;

        if (cal.get(Calendar.MONTH) + 1 < 10)
            monthStr = "0" + (cal.get(Calendar.MONTH) + 1);
        else
            monthStr = new Integer(cal.get(Calendar.MONTH) + 1).toString();

        if (cal.get(Calendar.DAY_OF_MONTH) < 10)
            dayStr = "0" + cal.get(Calendar.DAY_OF_MONTH);
        else
            dayStr = new Integer(cal.get(Calendar.DAY_OF_MONTH)).toString();

        String fullLine = cal.get(Calendar.YEAR) + "-" + monthStr + "-"
                + dayStr + "-" + stock;
        hash = MD5Tools.MD5hash(fullLine);
    }

    /**
     * Returns the stock value retrieved, as a float. Note that this is NOT
     * reliable for computing the hash ("8934.10" becomes 8934.1, which changes
     * the hash significantly).
     * 
     * @return the stock value as a float
     */
    public float getStockAsFloat() {
        return new Float(stock);
    }

    /**
     * Returns the stock value retrieved, as a String. This is what you should
     * be using for anything that involves the hash.
     * 
     * @return the stock value, ripe for hashing
     */
    public String getStock() {
        return stock;
    }

    /**
     * Returns the raw hash value, before any splitting or whatnot.
     * 
     * @return the raw hash value
     */
    public String getHash() {
        return hash;
    }

    /**
     * Gets the current effective date.
     * 
     * @return the current effective date
     */
    public Calendar getCalendar() {
        return cal;
    }

    /**
     * Gets the current effective stock date, accounting for the 30W Rule. This
     * does NOT return the nearest Friday on a weekend, though.
     * 
     * @return the current effective stock date
     */
    public Calendar getStockCalendar() {
        return sCal;
    }

    /**
     * Sets a new Calendar to use as this date. This will also reset the current
     * stock calendar based on the 30W Rule AND refetch the stock.
     * 
     * @param cal
     *            new Calendar for a date
     * @throws FileNotFoundException
     *             the date could not be found on the server (usually means the
     *             data isn't up yet)
     * @throws IOException
     *             some other sort of IOException occurred
     */
    public synchronized void setCalendar(Calendar cal)
            throws FileNotFoundException, IOException {
        this.cal = cal;

        resetStockCalendar();
        fetchStock();
        makeHash();
    }

    private void resetStockCalendar() {
        if (graticule.uses30WRule()) {
            sCal = (Calendar)cal.clone();
            sCal.add(Calendar.DAY_OF_MONTH, -1);
        } else
            sCal = cal;
    }

    /**
     * Gets the Graticule object currently used by this.
     * 
     * @return the Graticule object currently used by this
     */
    public Graticule getGraticule() {
        return graticule;
    }

    /**
     * Sets a new Graticule to use. This will also reset the hash, and if the
     * 30W Rule has changed, reset the stock calendar and refetch the stock.
     * 
     * @param graticule
     *            the new Graticule
     */
    public synchronized void setGraticule(Graticule graticule)
            throws FileNotFoundException, IOException {
        boolean refetch = false;
        if (this.graticule.uses30WRule() != graticule.uses30WRule()) {
            refetch = true;
        }

        this.graticule = graticule;

        if (refetch) {
            resetStockCalendar();
            fetchStock();
        }
        makeHash();
    }

    /**
     * Gets the latitude value of the location for the current date. This is
     * attached to the current graticule integer value to produce the longitude.
     * 
     * @return the fractional latitude value
     */
    public double getLatitudeHash() {
        String chunk = hash.substring(0, 16);
        return HexFraction.calculate(chunk);
    }

    /**
     * Gets the longitude value of the location for the current date. This is
     * attached to the current graticule integer value to produce the latitude.
     * 
     * @return the fractional longitude value
     */

    public double getLongitudeHash() {
        String chunk = hash.substring(16, 32);
        return HexFraction.calculate(chunk);
    }

    /**
     * Gets the full latitude value for all this mess.
     * 
     * @return the latitude
     */
    public double getLatitude() {
        int lat = graticule.getLatitude();
        if (graticule.isSouth()) {
            return (lat + getLatitudeHash()) * -1;
        } else {
            return lat + getLatitudeHash();
        }
    }

    /**
     * Gets the full longitude value for all this mess.
     * 
     * @return the latitude
     */
    public double getLongitude() {
        int lon = graticule.getLongitude();
        if (graticule.isWest()) {
            return (lon + getLongitudeHash()) * -1;
        } else {
            return lon + getLongitudeHash();
        }
    }

    /**
     * Aborts the stock connection, if one is in effect. Note that if this is
     * called, the caller MUST NOT read any hash or stock data from this object,
     * as it's unreliable.
     */
    public void abort() {
        if (mRequest != null) {
            mRequest.abort();
        }
        mAborted = true;
    }

    /**
     * Checks whether or not this was aborted.
     * 
     * @return true if aborted, false if not.
     */
    public boolean wasAborted() {
        return mAborted;
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

        // At the end of output,

        return tempstring.toString();
    }

    /**
     * Returns one Info object worth of useful data generated from this
     * HashMaker. That's passed to everything as need be. Which makes a lot more
     * sense than passing a HashMaker all over the place, since HashMaker should
     * just be a worker object, not a data storage object. I think.
     * 
     * @return an Info object with everything that the rest of Geohash Droid
     *         will need.
     */
    public Info makeInfo() {
        return new Info(getLatitude(), getLongitude(), getGraticule(),
                getCalendar());
    }
}
