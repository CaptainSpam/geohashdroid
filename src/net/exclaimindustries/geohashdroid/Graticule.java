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
 * A <code>Graticule</code> represents, well, a graticule. A 1x1 square degree
 * space on the earth's surface. The very heart of Geohashing*.  The base
 * implementation of a Graticule is designed to be immutable owing to a few odd
 * things that happen around the equator and Prime Meridian.
 * </p>
 * 
 * <p>
 * *: Well, maybe not the heart. At least the kidneys for sure.
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
     * Constructs a new Graticule with the given Location object. This seems
     * like it's most likely what you want, but in the current version of this,
     * the Graticule is most likely made through the int constructor due to how
     * the information is gathered in the main interface.
     * 
     * @param location
     *            Location to make a new Graticule out of
     */
    public Graticule(Location location) {
        if (location.getLatitude() < 0)
            mSouth = true;
        if (location.getLongitude() < 0)
            mWest = true;
        this.setLatitude(Math.abs((int)location.getLatitude()));
        this.setLongitude(Math.abs((int)location.getLongitude()));
    }

    /**
     * Constructs a new Graticule with the given GeoPoint object. Similar deal
     * to the Location version of this.
     * 
     * @param point
     *            GeoPoint to make a new Graticule out of
     */
    public Graticule(GeoPoint point) {
        if (point.getLatitudeE6() < 0)
            mSouth = true;
        if (point.getLongitudeE6() < 0)
            mWest = true;
        setLatitude(Math.abs((int)(point.getLatitudeE6() / 1000000)));
        setLongitude(Math.abs((int)(point.getLongitudeE6() / 1000000)));
    }

    /**
     * <p>
     * Constructs a new Graticule with the given latitude and longitude. Note
     * that values that shoot around the planet will be clamped to 89 degrees
     * latitude and 179 degrees longitude (positive or negative).
     * </p>
     * 
     * <p>
     * With this constructor, you <b>MUST</b> specify if this is south or west
     * (that is, negative values). This is to account for the "negative zero"
     * graticules, for those living on the Prime Meridian or equator, as you
     * can't very well input -0 as a Java int and have it distinct from 0.
     * </p>
     * 
     * <p>
     * This will also ignore any negatives in your inputs (-75 will become 75).
     * </p>
     * 
     * @param latitude
     *            latitude to set
     * @param south
     *            true if south, false if north
     * @param longitude
     *            longitude to set
     * @param west
     *            true if west, false if east
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
     * doubles. This can thus make Graticules directly from GPS inputs. Note
     * that values that shoot around the planet will be clamped to 89 degrees
     * latitude and 179 degrees longitude (positive or negative).
     * </p>
     * 
     * <p>
     * Negative values will be interpreted as south and west. Please don't use
     * this if you're standing directly on the equator and/or Prime Meridian and
     * GPS gives you a direct zero.
     * </p>
     * 
     * @param latitude
     *            latitude to set
     * @param longitude
     *            longitude to set
     */
    public Graticule(double latitude, double longitude) {
        if (latitude < 0)
            mSouth = true;
        else
            mSouth = false;
        if (longitude < 0)
            mWest = true;
        else
            mWest = false;
        this.setLatitude(Math.abs((int)latitude));
        this.setLongitude(Math.abs((int)longitude));
    }

    /**
     * Constructs a new Graticule with the given String forms of the latitude
     * and longitude.
     * 
     * @param latitude
     *            latitude to set
     * @param longitude
     *            longitude to set
     * @throws NullPointerException
     *             either of the input strings were empty
     * @throws NumberFormatException
     *             either of the input strings weren't numbers
     */
    public Graticule(String latitude, String longitude)
            throws NullPointerException, NumberFormatException {
        if (latitude.charAt(0) == '-')
            mSouth = true;
        else
            mSouth = false;
        if (longitude.charAt(0) == '-')
            mWest = true;
        else
            mWest = false;
        this.setLatitude(Math.abs(new Integer(latitude)));
        this.setLongitude(Math.abs(new Integer(longitude)));
    }

    /**
     * <p>
     * Constructs a new Graticule offset from an existing one.  That is to say,
     * copy an existing Graticule and move it by however many degrees as is
     * specified.  Under the current implementation, if this gets offset past
     * the edges of the earth, it will clamp to 89 degrees latitude and 179
     * degrees longitude (positive or negative).
     * </p>
     *
     * <p>
     * Note carefully that moving one degree west of zero longitude will go to
     * "negative zero" longitude.  Same with latitude.  There is a distinction.
     * Therefore, be very careful when crossing the Prime Meridian and/or the
     * equator.
     * </p>
     *
     * @param g
     *            Graticule to copy
     * @param latOff
     *            number of degrees north to offset (negative is south)
     * @param lonOff
     *            number of degrees east to offset (negative is west)
     * @return
     *             a brand spakin' new Graticule, offset as per suggestion
     */
    public static Graticule createOffsetFrom(Graticule g, int latOff, int lonOff) {
        // We already have all the data we need from the old Graticule.  But,
        // we need to account for passing through the Prime Meridian and/or
        // equator.  If the sign changes, decrement the amount of the change by
        // one.  This logic is gratiutously loopy.
        boolean goingSouth = (latOff < 0);
        boolean goingWest = (lonOff < 0);
        latOff = Math.abs(latOff);
        lonOff = Math.abs(lonOff);

        int finalLat = g.getLatitude();
        int finalLon = g.getLongitude();
        boolean finalSouth = g.isSouth();
        boolean finalWest = g.isWest();

        // Skip the following if latitude is unaffected.
        if (latOff != 0) {
            if (g.isSouth() == goingSouth) {
                // Going the same direction, no equator-hacking needed.
                finalLat = g.getLatitude() + latOff;
            } else {
                // Going opposite directions, check for equator-hacking.
                if (g.getLatitude() < latOff) {
                    // We cross the equator!
                    latOff--;
                    finalSouth = !finalSouth;
                }
                finalLat = Math.abs(g.getLatitude() - latOff);
            }
        }

        if (lonOff != 0) {
            if (g.isWest() == goingWest) {
                // Going the same direction, no Meridian-hacking needed.
                finalLon = g.getLongitude() + lonOff;
            } else {
                // Going opposite directions, check for Meridian-hacking.
                if (g.getLongitude() < lonOff) {
                    // We cross the Prime Meridian!
                    lonOff--;
                    finalWest = !finalWest;
                }
                finalLon = Math.abs(g.getLongitude() - lonOff);
            }
        }

        // Now make the new Graticule object and return it.
        return new Graticule(finalLat, finalSouth, finalLon, finalWest);
    }

    /**
     * Returns true if the 30W Rule is in effect. Which is to say, anything east
     * of -30 longitude uses yesterday's stock value, regardless of if the DJIA
     * was updated to that point.
     * 
     * @return true if the 30W Rule is in effect, false otherwise
     */
    public boolean uses30WRule() {
        return ((mLongitude < 30 && isWest()) || !isWest());
    }

    private void setLatitude(int latitude) {
        // Work out invalid entries by clamping 'em down.
        if (latitude > 89)
            latitude = 89;
        
        this.mLatitude = latitude;
    }

    /**
     * Returns the absolute value of the current latitude. Run this against
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
        if (mSouth) {
            return "-" + mLatitude;
        } else {
            return new Integer(mLatitude).toString();
        }
    }

    private void setLongitude(int longitude) {
        // Clamp!  Clamp!  Clamp!
        if (longitude > 179)
            longitude = 179;
       
        this.mLongitude = longitude;
    }

    /**
     * Returns the absolute value of the current longitude. Run this against
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
        if (mWest) {
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
        // graticules. So...
        if (isSouth()) {
            lat = (getLatitude() * -1000000) - 500000;
        } else {
            lat = (getLatitude() * 1000000) + 500000;
        }

        if (isWest()) {
            lon = (getLongitude() * -1000000) - 500000;
        } else {
            lon = (getLongitude() * 1000000) + 500000;
        }

        return new GeoPoint(lat, lon);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object o) {
        // First, this better be a Graticule.
        if (!(o instanceof Graticule))
            return false;

        Graticule g = (Graticule)o;

        // If everything matches up, these are identical. Two int checks and
        // two boolean checks are probably a lot faster than two String checks,
        // right?
        if (g.getLatitude() != getLatitude()
                || g.getLongitude() != getLongitude()
                || g.isSouth() != isSouth() || g.isWest() != isWest())
            return false;
        else
            return true;
    }

}
