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
 * An Info object holds all the relevant info that involves the map.  This, for
 * the most part, involves the final destination, the current date, and the
 * graticule.  It also includes utility methods for calculating data from this
 * information, most importantly the distance between some location and the
 * final destination.
 * </p>
 * 
 * <p>
 * This is meant to be generated from a HashMaker object as the last step once
 * it has all the data it needs.
 * </p>
 * 
 * @author Nicholas Killewald
 *
 */
public class Info implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private double mLatitude;
	private double mLongitude;
	private Graticule mGraticule;
	private Calendar mDate;
	
	/**
	 * Creates an Info object with the given data.  That's it.
	 * 
	 * @param finalDestination the destination
	 * @param graticule the graticule
	 * @param date the date
	 */
	public Info(double latitude, double longitude, Graticule graticule, Calendar date) {
		mLatitude = latitude;
		mLongitude = longitude;
		mGraticule = graticule;
		mDate = date;
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
     * unbelievably handy when plotting this on a map, given that uses
     * GeoPoints and not doubles.
     * 
     * @return a new GeoPoint based on the data obtained from the connection
     */
    public GeoPoint getFinalDestination() {
    	return new GeoPoint((int)(getLatitude() * 1000000), (int)(getLongitude() * 1000000));
    }
    
    /**
     * Returns the final destination as a new Location object, which isn't quite
     * as useful as a GeoPoint object, but you never know, it could come in handy.
     * 
     * @return a new providerless Location based on the data obtained from the connection
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
	 * Sets all the data in one fell swoop using the given HashMaker object.
	 * 
	 * @param hash the HashMaker from which all the data is to be extracted
	 */
	public void setFromHashMaker(HashMaker hash) {
		mLatitude = hash.getLatitude();
		mLongitude = hash.getLongitude();
		mGraticule = hash.getGraticule();
		mDate = hash.getCalendar();
	}
	
	/**
	 * Gets the distance, in meters, from the given Location and the final
	 * destination.
	 * 
	 * @param loc Location to compare
	 * @return the distance, in meters, to the final destination
	 */
	public float getDistanceInMeters(Location loc) {
		return loc.distanceTo(getFinalLocation());
	}
	
	/**
	 * Gets the distance, in meters, from the given GeoPoint and the final
	 * destination.
	 * 
	 * @param point GeoPoint to compare
	 * @return the distance, in meters, to the final destination
	 */
	public float getDistanceInMeters(GeoPoint point) {
		return locationFromGeoPoint(point).distanceTo(getFinalLocation());
	}
	
	private static Location locationFromGeoPoint(GeoPoint point) {
		// It turns out GeoPoint doesn't have the distanceTo method that
		// Location does.  So, this method converts a GeoPoint into a Location
		// that doesn't have a provider.
		Location loc = new Location("");
		
		loc.setLatitude((float)(point.getLatitudeE6() / 1000000.0f));
		loc.setLongitude((float)(point.getLongitudeE6() / 1000000.0f));
		
		return loc;
	}
}
