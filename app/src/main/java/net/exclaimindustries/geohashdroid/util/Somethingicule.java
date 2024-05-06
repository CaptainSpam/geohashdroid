/*
 * Somethingicule.java
 * Copyright (C) 2024 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.util;

import android.os.Parcelable;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolygonOptions;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.annotation.NonNull;

/**
 * <code>Somethingicule</code> is the common interface for graticules,
 * centicules, and whatever else might come up for subdividing the planet for
 * Geohashing purposes (besides globalhashes).
 */
public interface Somethingicule<T> extends Parcelable {
    class Deserializer {
        /**
         * Deserializes a JSONObject into an appropriate Somethingicule.
         *
         * @param input the JSONObject to deserialize
         * @return a deserialized Somethingicule
         * @throws IllegalArgumentException the JSONObject doesn't appear to be a serialized Somethingicule
         * @throws JSONException some part of the object couldn't be coerced into what it needs to be
         */
        @NonNull
        public static Somethingicule deserialize(@NonNull JSONObject input) throws JSONException {
            // All Somethingicules require these at a bare minimum.
            if(!input.has("latitude") || !input.has("longitude")
                || !input.has("isSouth") || !input.has("isWest")) {
                throw new IllegalArgumentException("This doesn't look like a Somethingicule!");
            }

            // Furthermore, if either latitudeFraction or longitudeFraction
            // exists (for a Centicule), the other MUST exist.
            if((input.has("latitudeFraction") && !input.has("longitudeFraction"))
                || (!input.has("latitudeFraction") && input.has("longitudeFraction"))) {
                throw new IllegalArgumentException("This almost looks like a Centicule, but is missing exactly one of latitudeFraction or longitudeFraction!");
            }

            // Now we can determine what we've got and deserialize it.
            if(input.has("latitudeFraction")) {
                // If there's a latitude fraction, the previous exception check
                // implies there's a longitude fraction at this point, so this
                // must be a Centicule.
                // TODO: Implement once Centicule is implemented.
                throw new IllegalArgumentException("Centicule isn't implemented yet!");
            } else {
                // No latitude fraction means this must be a Graticule.
                return Graticule.deserializeFromJSON(input);
            }
        }
    }

    /**
     * <p>
     * Constructs a new Somethingicule offset from this one.  That is to say,
     * copy this Somethingicule and move it by however many units (degrees for
     * Graticule, tenths-of-a-degree for Centicule, etc) as is specified.  Under
     * the current implementation, if this gets offset past the edges of the
     * earth, it will attempt to wrap around.  This allows people in the far
     * eastern regions of Russia to see the nearby meetup points if they happen
     * to live near the 180E/W longitude line.  It does not, however, allow for
     * penguins and Santa Claus, so don't try to fling yourself over the poles.
     * </p>
     *
     * <p>
     * Note carefully that moving unit west of zero longitude will go to
     * "negative zero" longitude.  Same with latitude.  There is a distinction.
     * Therefore, be very careful when crossing the Prime Meridian and/or the
     * equator.
     * </p>
     *
     * @param latOff number of units north to offset (negative is south)
     * @param lonOff number of units east to offset (negative is west)
     * @return a brand new whatever this is, offset as per suggestion
     * @throws IllegalArgumentException the resulting Somethingicule doesn't exist (i.e. trying to offset past the poles)
     */
    @NonNull
    T createOffset(int latOff, int lonOff);

    /**
     * Returns true if the 30W Rule is in effect. Which is to say, anything east
     * of -30 longitude uses yesterday's stock value, regardless of if the DJIA
     * was updated to that point.  Note that this only determines if the
     * somethingicule itself abides by the 30W Rule; if the date is May 26, 2008
     * or earlier, 30W is ignored.
     *
     * @return true if the 30W Rule is in effect, false otherwise
     */
    boolean uses30WRule();

    /**
     * Returns the latitude component as a String (i.e. 30N or -25). This will
     * be a String, not any numeric value.
     *
     * @param useNegativeValues true to return values as negative for south and positive for north, false to return values with N and S indicators
     * @return the latitude as a String
     */
    @NonNull
    String getLatitudeString(boolean useNegativeValues);

    /**
     * Returns the longitude component as a String (i.e. 25E or -76). This will
     * be a String, not any numeric value.
     *
     * @param useNegativeValues true to return values as negative for south and positive for north, false to return values with N and S indicators
     * @return the latitude as a String
     */
    @NonNull
    String getLongitudeString(boolean useNegativeValues);

    /**
     * Gets the full latitude for this Somethingicule for the given fractional
     * hash value.
     *
     * @param latHash the fractional latitude hash value
     * @return the full latitude for this Somethingicule
     * @throws IllegalArgumentException if latHash or lonHash are less than 0 or greater than 1
     */
    double getLatitudeForHash(double latHash);

    /**
     * Gets the full longitude for this Somethingicule for the given fractional
     * hash value.
     *
     * @param lonHash the fractional latitude hash value
     * @return the full longitude for this Somethingicule
     * @throws IllegalArgumentException if latHash or lonHash are less than 0 or greater than 1
     */
    double getLongitudeForHash(double lonHash);

    /**
     * Returns the "title" of this Somethingicule.  In general, this will be in
     * the form of "LAT LON".
     *
     * @param useNegativeValues true to return values as negative for west and positive for east, false to return values with E and W indicators
     * @return a title string for this Somethingicule
     */
    @NonNull
    String getTitleString(boolean useNegativeValues);

    /**
     * Returns whether or not this is a southern latitude (negative).
     *
     * @return true if south, false if north
     */
    boolean isSouth();

    /**
     * Returns whether or not this is an western longitude (negative).
     *
     * @return true if west, false if east.
     */
    boolean isWest();

    /**
     * Returns the center of this Somethingicule as a LatLng.
     *
     * @return a LatLng representing the center of this Somethingicule.
     */
    @NonNull
    LatLng getCenterLatLng();

    /**
     * Make a Maps v2 PolygonOptions out of this Somethingicule.  You can then
     * style it yourself and toss it into a map as need be.
     *
     * @return a PolygonOptions set up as this Somethingicule sits.
     */
    @NonNull
    PolygonOptions getPolygon();

    /**
     * <p>
     * Makes a LatLng out of this Somethingicule and component fractional hash
     * parts.  In other words, this forces the fractional bits into a proper
     * location based on this Somethingicule.
     * </p>
     *
     * <p>
     * TODO: HashBuilder could start calling this instead...
     * </p>
     *
     * @param latHash the fractional latitude portion of the hash
     * @param lonHash the fractional longitude portion of the hash
     * @return a new LatLng
     * @throws IllegalArgumentException if latHash or lonHash are less than 0 or greater than 1
     */
    @NonNull
    LatLng makePointFromHash(double latHash, double lonHash);

    /**
     * Serializes this Somethingicule into a JSONObject.
     *
     * @return a new JSONObject
     * @throws JSONException if something truly wacky happens with JSON creation
     */
    @NonNull
    JSONObject serializeToJSON() throws JSONException;
}