/**
 * Graticule.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.io.Serializable;

import com.google.android.maps.GeoPoint;

import android.location.Location;

/**
 * <p>
 * A <code>Graticule</code> represents, well, a graticule.  A 1x1 square degree
 * space on the earth's surface.  The very heart of Geohashing.
 * </p>
 * 
 * <p>
 * Well, maybe not the heart.  Maybe the kidneys.
 * </p>
 * 
 * @author Nicholas Killewald
 */
public class Graticule implements Serializable {
	private static final long serialVersionUID = 1L;
	private int mLatitude;
	private int mLongitude;
	
	// These are to account for the "negative zero" graticules.
	private boolean mSouth = false;
	private boolean mWest = false;
	
	/**
	 * Constructs a new Graticule with the given Location object.  This seems
	 * like it's most likely what you want, but in the current version of this,
	 * the Graticule is most likely made through the int constructor due to how
	 * the information is gathered in the main interface.
	 * 
	 * @param location Location to make a new Graticule out of
	 */
	public Graticule(Location location) {
		if(location.getLatitude() < 0) mSouth = true;
		if(location.getLongitude() < 0) mWest = true;
		this.setLatitude(Math.abs((int)location.getLatitude()));
		this.setLongitude(Math.abs((int)location.getLongitude()));
	}
	
	/**
	 * Constructs a new Graticule with the given GeoPoint object.  Similar deal
	 * to the Location version of this.
	 * 
	 * @param point GeoPoint to make a new Graticule out of
	 */
	public Graticule(GeoPoint point) {
		if(point.getLatitudeE6() < 0) mSouth = true;
		if(point.getLongitudeE6() < 0) mWest = true;
		setLatitude(Math.abs((int)(point.getLatitudeE6() / 1000000)));
		setLongitude(Math.abs((int)(point.getLongitudeE6() / 1000000)));
	}
	
	/**
	 * <p>
	 * Constructs a new Graticule with the given latitude and longitude.  Note
	 * that this will try to correct invalid values by assuming you want to
	 * wrap around the earth.
	 * </p>
	 * 
	 * <p>
	 * With this constructor, you <b>MUST</b> specify if this is south or west (that
	 * is, negative values).  This is to account for the "negative zero"
	 * graticules, for those living on the prime meridian or equator, as you
	 * can't very well input -0 as a Java int and have it distinct from 0.
	 * </p>
	 * 
	 * <p>
	 * This will also ignore any negatives in your inputs (-75 will become 75).
	 * </p>
	 * 
	 * @param latitude latitude to set
	 * @param south true if south, false if north
	 * @param longitude longitude to set
	 * @param west true if west, false if east
	 */
	public Graticule(int latitude, boolean south, int longitude, boolean west) {
		this.mSouth = south;
		this.mWest = west;
		this.setLatitude(Math.abs(latitude));
		this.setLongitude(Math.abs(longitude));
	}
	
	/**
	 * <p>
	 * Constructs a new Graticule with the given latitude and longitude as
	 * floats.  This can thus make Graticules directly from GPS inputs.  Note
	 * that this will try to correct invalid values by assuming you want to
	 * wrap around the earth only in that direction.  For example, (95N, 75W)
	 * will become (85N, 75W).
	 * </p>
	 * 
	 * <p>
	 * Negative values will be interpreted as south and west.  Please don't use
	 * this if you're standing directly on the equator and/or prime meridian
	 * and GPS gives you a direct zero.
	 * </p>
	 * 
	 * @param latitude latitude to set
	 * @param longitude longitude to set
	 */
	public Graticule(double latitude, double longitude) {
		if(latitude < 0) mSouth = true;
		if(longitude < 0) mWest = true;
		this.setLatitude(Math.abs((int)latitude));
		this.setLongitude(Math.abs((int)longitude));
	}
	
	/**
	 * Constructs a new Graticule with the given String forms of the latitude
	 * and longitude.
	 * 
	 * @param latitude latitude to set
	 * @param longitude longitude to set
	 * @throws NullPointerException either of the input strings were empty
	 * @throws NumberFormatException either of the input strings weren't numbers
	 */
	public Graticule(String latitude, String longitude) throws NullPointerException, NumberFormatException {
		if(latitude.charAt(0) == '-') mSouth = true;
		if(longitude.charAt(0) == '-') mWest = true;
		this.setLatitude(Math.abs(new Integer(latitude)));
		this.setLongitude(Math.abs(new Integer(longitude)));
	}
	
	/**
	 * Returns true if the 30W Rule is in effect.  Which is to say, anything
	 * east of -30 longitude uses yesterday's stock value, regardless of if the
	 * DJIA was updated to that point.
	 * 
	 * @return true if the 30W Rule is in effect, false otherwise
	 */
	public boolean uses30WRule() {
		return ((mLongitude < 30 && isWest()) || !isWest());
	}

	private void setLatitude(int latitude) {
		// We want to translate invalid entries to best guesses.  To that end,
		// we assume that shooting off one end just wraps to the other side (so
		// an input of 100 will result in 80, and -100 will result in -80).
		
		// If this is less than 90, save it as-is.  This MUST be unsigned as
		// per the constructor.
		if(latitude > 90) {
			// If true, swap the south flag.
			boolean endsUpNegative = false;
			
			if((latitude / 180) % 2 == 0) {
				// Even number of rotations, this will wind up negative.
				endsUpNegative = true;
			}
			
			if((latitude / 90) % 2 == 1) {
				// If odd, we subtract the modulo from 90 to get the base.
				latitude = (90 - latitude % 90);
			} else {
				// If even, the base is the modulo from 90.
				latitude %= 90;
			}
			
			if(!endsUpNegative)
				mSouth = !mSouth;
		}
		this.mLatitude = latitude;
	}
	
	/**
	 * Returns the absolute value of the current latitude.  Run this against
	 * isSouth() to figure out what the negative should be.
	 * 
	 * @return the absolute value of the current latitude
	 */
	public int getLatitude() {
		return mLatitude;
	}

	/**
	 * Returns the current latitude as a String to account for negative zero
	 * graticule wackiness.
	 * 
	 * @return the current latitude as a String
	 */
	public String getLatitudeString() {
		if(mSouth) {
			return "-" + mLatitude;
		} else {
			return new Integer(mLatitude).toString();
		}
	}

	private void setLongitude(int longitude) {
		// We also want to translate this to best guesses.  Problem being, we
		// don't just wrap back and forth like with latitude.  If we shoot off
		// the negative axis, -180 immediately becomes 179 (note: positive),
		// and similar with the positive axis (180 becomes -179).
		if(longitude > 180) {
			// Mash it down to what it should be.  We're assuming a full 360-
			// degree earth, so we offset the value.  I have a whiteboard full
			// of probably unnecessary equations that shows this.
			longitude = ((longitude + 180) % 360 - 180);
			
			// If we wound up negative, flip the sign.
			if(longitude < 0) {
				mWest = !mWest;
				longitude = Math.abs(longitude);
			}			
		}
		
		// Note that for graticule purposes, 180 and -180 are completely
		// invalid.  In those cases, we'll just call them 179 and -179,
		// respectively.
		if(longitude == 180)
			this.mLongitude = 179;
		else
			this.mLongitude = longitude;
	}
	
	/**
	 * Returns the absolute value of the current longitude.  Run this against
	 * isEast() to figure out what the negative should be.
	 * 
	 * @return the absolute value of the current longitude
	 */
	public int getLongitude() {
		return mLongitude;
	}

	/**
	 * Returns the current longitude as a String to account for negative zero
	 * graticule madness.
	 * 
	 * @return the current longitude as a String
	 */
	public String getLongitudeString() {
		if(mWest) {
			return "-" + mLongitude;
		} else {
			return new Integer(mLongitude).toString();
		}
	}
	
	/**
	 * Returns whether or not this is a southern latitude (negative).
	 * 
	 * @return true if south, false if north
	 */
	public boolean isSouth() {
		return mSouth;
	}
	
	/**
	 * Returns whether or not this is an western longitude (negative).
	 * 
	 * @return true if west, false if east.
	 */
	public boolean isWest() {
		return mWest;
	}
	
	/**
	 * Returns the center of this Graticule as a GeoPoint.
	 * 
	 * @return a GeoPoint representing the center of this Graticule.
	 */
	public GeoPoint getCenter() {
		int lat;
		int lon;
		
		// The concept of "center" changes when we're dealing with negative
		// graticules.  So...
		if(isSouth()) {
			lat = (getLatitude() * -1000000) - 500000;
		} else {
			lat = (getLatitude() * 1000000) + 500000;
		}
		
		if(isWest()) {
			lon = (getLongitude() * -1000000) - 500000;
		} else {
			lon = (getLongitude() * 1000000) + 500000;
		}
		
		return new GeoPoint(lat, lon);
	}
	
}
