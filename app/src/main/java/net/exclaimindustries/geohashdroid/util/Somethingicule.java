/*
 * Somethingicule.java
 * Copyright (C) 2024 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.util;

import android.content.Context;
import android.os.Parcelable;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolygonOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.GregorianCalendar;

import androidx.annotation.NonNull;

/**
 * <p>
 * <code>Somethingicule</code> is the common interface for graticules,
 * centicules, and whatever else might come up for subdividing the planet for
 * Geohashing purposes (besides globalhashes).
 * </p>
 *
 * <p>
 * Note that Somethingicules are immutable.
 * </p>
 *
 * <p>
 * Also, a lot of the code in Geohash Droid was written with graticules in mind,
 * so if you come across comments that mention graticules without context (and
 * you aren't in the literal Graticule class), assume it applies to
 * Somethingicules.  I wasn't about to go change every single reference.
 * </p>
 */
public interface Somethingicule extends Parcelable {
    /** The earliest date at which the 30W Rule is used. */
    Calendar LIMIT_30W = new GregorianCalendar(2008, Calendar.MAY, 26);

    /**
     * The type of Somethingicule this is.  Used for deserialization.
     */
    enum Type {
        /** A classic 1x1 degree graticule. */
        GRATICULE,
        /** A spicy 0.1x0.1 degree centicule. */
        CENTICULE,
        /** A beefy worldwide globalhash. */
        GLOBALHASHICULE,
    }

    class Deserializer {
        /**
         * Deserializes a JSONObject into an appropriate Somethingicule.
         *
         * @param input the JSONObject to deserialize
         * @return a deserialized Somethingicule
         * @throws JSONException some part of the object couldn't be coerced into what it needs to be
         * @throws IllegalArgumentException something is very very wrong and an illegal Somethingicule type somehow got involved
         */
        @NonNull
        public static Somethingicule deserialize(@NonNull JSONObject input) throws JSONException {
            // If the type field doesn't exist, assume this is a Graticule from
            // before type fields existed.  If it isn't, I guess exceptions will
            // be thrown.
            Type type = Type.GRATICULE;
            if(input.has("type")) {
                type = Type.valueOf(input.getString("type"));
            }

            switch(type) {
                case GRATICULE:
                    return Graticule.deserializeFromJSON(input);
                case CENTICULE:
                    return Centicule.deserializeFromJSON(input);
                case GLOBALHASHICULE:
                    // There's no data in a serialized Globalhashicule besides
                    // the type.  Just return the instance.
                    return Globalhashicule.getInstance();
                default:
                    throw new IllegalArgumentException("Couldn't determine type of Somethingicule from type: " + type);
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
    Somethingicule createOffset(int latOff, int lonOff);

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
     * Returns true if the 30W Rule is in effect at the specified date.  In most
     * cases, the 30W Rule is ignored if the date is before May 26, 2008, but
     * globalhashes always use it.
     *
     * @param cal date against which to check
     * @return true if the 30W Rule is in effect for the date, false otherwise
     */
    boolean uses30WRuleAtDate(Calendar cal);

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
     * Returns the center of this Somethingicule as a LatLng.
     *
     * @return a LatLng representing the center of this Somethingicule.
     * @throws IllegalArgumentException this Somethingicule has no "center" per se (likely because it's a Globalhashicule)
     */
    @NonNull
    LatLng getCenterLatLng() throws IllegalArgumentException;

    /**
     * Make a Maps v2 PolygonOptions out of this Somethingicule.  You can then
     * style it yourself and toss it into a map as need be.
     *
     * @return a PolygonOptions set up as this Somethingicule sits.
     * @throws IllegalArgumentException this Somethingicule doesn't support a polygon (likely because it's a Globalhashicule)
     */
    @NonNull
    PolygonOptions getPolygon() throws IllegalArgumentException;

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
     * Makes the wiki page suffix for this Somethingicule.  You want to attach
     * this to the end of the current hyphenated date string from the date of an
     * Info object.  Remember, this is in the format of a wiki page name, so
     * you want to use underscores for spaces.
     *
     * @return the page suffix for a wiki post
     */
    @NonNull
    String getWikiPageSuffix();

    /**
     * Makes the initial wiki template for this Somethingicule, as a string.
     *
     * @param info Info object for this expedition
     * @param c a Context, just in case it needs to fetch the globalhash template
     * @return the wiki template
     */
    @NonNull
    String makeWikiTemplate(@NonNull Info info, @NonNull Context c);

    /**
     * Gets the text for the categories to put on the wiki for pictures for this
     * Somethingicule.  This is mostly just called from Info.
     *
     * @return the wiki categories
     */
    @NonNull
    String makeWikiCategories();

    /**
     * Serializes this Somethingicule into a JSONObject.
     *
     * @return a new JSONObject
     * @throws JSONException if something truly wacky happens with JSON creation
     */
    @NonNull
    JSONObject serializeToJSON() throws JSONException;
}
