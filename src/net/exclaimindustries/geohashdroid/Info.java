/**
 * Info.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.io.Serializable;
import java.util.Date;
import java.util.Calendar;

import android.location.Location;

import com.google.android.maps.GeoPoint;

/**
 * <p>
 * An <code>Info</code> object holds all the relevant info that involves the 
 * map. This, for the most part, involves the final destination, the current
 * date, and the graticule. It also includes utility methods for calculating
 * data from this information, most importantly the distance between some
 * location and the final destination.
 * </p>
 * 
 * <p>
 * <code>Info</code> objects are immutable and are meant to be generated from a
 * HashMaker object as the last step once it has all the data it needs.
 * </p>
 * 
 * @author Nicholas Killewald
 * 
 */
public class Info implements Serializable {
    private static final long serialVersionUID = 2L;

    private double mLatitude;
    private double mLongitude;
    private Graticule mGraticule;
    private Calendar mDate;
    // Note that this is stored as a String, not a float.  We never actually use
    // the stock value as a float; it always gets fed directly into the hash as
    // a String.  And, for hashing purposes, if it were a float, we would need
    // to ensure it gets padded to two decimal points if need be.  So, it stands
    // as a String.
    private String mStock;

    /**
     * Creates an Info object with the given data. That's it.
     * 
     * @param latitude
     *            the destination's latitude, as a double
     * @param longitude
     *            the destination's longitude, as a double
     * @param graticule
     *            the graticule
     * @param date
     *            the date
     * @param stock
     *            the stock value
     */
    public Info(double latitude, double longitude, Graticule graticule,
            Calendar date, String stock) {
        mLatitude = latitude;
        mLongitude = longitude;
        mGraticule = graticule;
        mDate = date;
        mStock = stock;
    }

    /**
     * Gets the latitude of the final destination.
     * 
     * @return the latitude
     */
    public double getLatitude() {
        return mLatitude;
    }

    /**
     * Gets the longitude of the final destination.
     * 
     * @return the longitude
     */
    public double getLongitude() {
        return mLongitude;
    }

    /**
     * Returns the final destination as a GeoPoint object, which can be so
     * unbelievably handy when plotting this on a map, given that uses GeoPoints
     * and not doubles.
     * 
     * @return a new GeoPoint based on the data obtained from the connection
     */
    public GeoPoint getFinalDestination() {
        return new GeoPoint((int)(getLatitude() * 1000000),
                (int)(getLongitude() * 1000000));
    }

    /**
     * Returns the final destination as a new Location object, which isn't quite
     * as useful as a GeoPoint object, but you never know, it could come in
     * handy.
     * 
     * @return a new providerless Location based on the data obtained from the
     *         connection
     */
    public Location getFinalLocation() {
        Location loc = new Location("");
        loc.setLatitude(getLatitude());
        loc.setLongitude(getLongitude());

        return loc;
    }

    /**
     * Gets the graticule.
     * 
     * @return the graticule
     */
    public Graticule getGraticule() {
        return mGraticule;
    }

    /**
     * Gets the Calendar used to generate this set of information.
     * 
     * @return the Calendar
     */
    public Calendar getCalendar() {
        return mDate;
    }

    /**
     * Gets the Date object from the Calendar object used to generate this set
     * of information.
     * 
     * @return the Date of the Calendar
     */
    public Date getDate() {
        return mDate.getTime();
    }
    
    /**
     * Gets the stored stock price as a String, suitable for hashing.
     * 
     * <p>
     * Be careful; this is the stock price used for the hash.  This is not
     * necessarily the stock price for the date stored in this Info's Calendar
     * object if the graticule falls under the 30W Rule.
     * </p>
     * 
     * @return the stock as a String
     */
    public String getStockString() {
        return mStock;
    }
    
    /**
     * <p>
     * Gets the stored stock price as a float, suitable for things which
     * GeohashDroid wasn't made for, so I'm not sure why this would be called.
     * </p>
     * 
     * <p>
     * Be careful; this is the stock price used for the hash.  This is not
     * necessarily the stock price for the date stored in this Info's Calendar
     * object if the graticule falls under the 30W Rule.
     * </p>
     * 
     * @return the stock as a float
     * @throws NumberFormatException the stock value somehow isn't parseable as
     *                               a float
     */
    public float getStockFloat() throws NumberFormatException {
        return Float.parseFloat(mStock);
    }

    /**
     * Gets the distance, in meters, from the given Location and the final
     * destination.
     * 
     * @param loc
     *            Location to compare
     * @return the distance, in meters, to the final destination
     */
    public float getDistanceInMeters(Location loc) {
        return loc.distanceTo(getFinalLocation());
    }

    /**
     * Gets the distance, in meters, from the given GeoPoint and the final
     * destination.
     * 
     * @param point
     *            GeoPoint to compare
     * @return the distance, in meters, to the final destination
     */
    public float getDistanceInMeters(GeoPoint point) {
        return locationFromGeoPoint(point).distanceTo(getFinalLocation());
    }
    
    /**
     * Returns a calendar representing the date from which the stock price was
     * pulled.  That is, back a day for the 30W Rule and rewinding to Friday if
     * it falls on a weekend.
     * 
     * @return a new adjusted Calendar
     */
    public Calendar getStockCalendar() {
        // This adjusts the calendar for both the 30W Rule and to clamp all
        // weekend stocks to the preceding Friday.  This saves a few database
        // entries, as the weekend will always be Friday's value.  Note that
        // this doesn't account for holidays when the US stocks aren't trading.
        
        // First, clone the calendar.  We don't want to muck about with the
        // original for various reasons.
        Calendar cal = (Calendar)(mDate.clone());
        
        // Second, 30W Rule hackery.
        if(mGraticule.uses30WRule())
            cal.add(Calendar.DAY_OF_MONTH, -1);
        
        // Third, if this new date is a weekend, clamp it back to Friday.
        if(cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY)
            // Saturday: Back one day
            cal.add(Calendar.DAY_OF_MONTH, -1);
        else if(cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
            // SUNDAY SUNDAY SUNDAY!!!!!!: Back two days
            cal.add(Calendar.DAY_OF_MONTH, -2);
        
        // There!  Done!
        return cal;
    }

    private static Location locationFromGeoPoint(GeoPoint point) {
        // It turns out GeoPoint doesn't have the distanceTo method that
        // Location does. So, this method converts a GeoPoint into a Location
        // that doesn't have a provider.
        Location loc = new Location("");

        loc.setLatitude((float)(point.getLatitudeE6() / 1000000.0f));
        loc.setLongitude((float)(point.getLongitudeE6() / 1000000.0f));

        return loc;
    }
}
