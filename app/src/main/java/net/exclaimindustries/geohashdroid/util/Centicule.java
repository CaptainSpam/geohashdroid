/*
 * Centicule.java
 * Copyright (C) 2024 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.util;

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolygonOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Objects;

import androidx.annotation.NonNull;

/**
 * A <code>Centicule</code> is the hip, lean cousin to <code>Graticule</code>
 * that only covers a 0.1x0.1 square degree space on the earth's surface.  That
 * is, it's a Graticule, but the hash value is attached one decimal place later.
 *
 * @author Nicholas Killewald
 */
public class Centicule implements Somethingicule {
    /** The latitude goes from 0 to 899, inclusive. */
    private int mLatitude;
    /**
     * The longitude goes from 0 to 1799, inclusive.  You see where I'm going
     * with this, right?
     */
    private int mLongitude;

    private boolean mSouth = false;
    private boolean mWest = false;

    private final static DecimalFormat FORMAT_CENTICULE = new DecimalFormat("##0.0");

    /**
     * Constructs a new Centicule with the given Location object
     *
     * @param location Location to make a new Centicule out of
     */
    public Centicule(Location location) {
        this(location.getLatitude(), location.getLongitude());
    }

    /**
     * Constructs a new Centicule with the given LatLng object, because the v2
     * API said so.
     *
     * @param latLng LatLng to make a new Centicule out of
     */
    public Centicule(LatLng latLng) {
        this(latLng.latitude, latLng.longitude);
    }

    /**
     * <p>
     * Constructs a new Centicule with the given component values. Note that
     * values that shoot around the planet will be clamped to 89.9 degrees
     * latitude and 179.9 degrees longitude (positive or negative).
     * </p>
     *
     * <p>
     * With this constructor, you <b>MUST</b> specify if this is south or west
     * (that is, negative values). This is to account for the "negative zero"
     * centicules, for those living on the Prime Meridian or equator, as you
     * can't very well input -0 as a Java int and have it distinct from 0.
     * </p>
     *
     * <p>
     * This will also ignore any negatives in your inputs (-75 will become 75).
     * </p>
     *
     * @param latitude integer part of the latitude
     * @param latitudeFraction fractional part of the latitude
     * @param south true if south, false if north
     * @param longitude integer part of the longitude
     * @param longitudeFraction fractional part of the longitude
     * @param west true if west, false if east
     * @throws IllegalArgumentException Either latitudeFraction or longitudeFraction are higher than 9
     */
    public Centicule(int latitude, int latitudeFraction, boolean south, int longitude, int longitudeFraction, boolean west) {
        this.mSouth = south;
        this.mWest = west;

        this.setLatitudeAndFraction(latitude, latitudeFraction);
        this.setLongitudeAndFraction(longitude, longitudeFraction);
    }

    /**
     * <p>
     * Constructs a new Centicule with the given "complete" latitude and
     * longitude.  By "complete", I mean taking the full centicule values (i.e.
     * 45.9/157.3) and multiplying them by 10 to make the internal integer
     * format used in the Centicule class (that is, latitudes from 0 to 899,
     * longitudes from 0 to 1799, inclusive).  Note that values that shoot
     * around the planet will be clamped to 89.9 degrees latitude and 179.9
     * degrees longitude (899 and 1799, respectively, positive or negative).
     * </p>
     *
     * <p>
     * With this constructor, you <b>MUST</b> specify if this is south or west
     * (that is, negative values). This is to account for the "negative zero"
     * centicules, for those living on the Prime Meridian or equator, as you
     * can't very well input -0 as a Java int and have it distinct from 0.
     * </p>
     *
     * <p>
     * This will also ignore any negatives in the inputs (-750 will become 750).
     * </p>
     *
     * @param completeLatitude complete latitude
     * @param south true if south, false if north
     * @param completeLongitude complete longitude
     * @param west true if west, false if east
     */
    public Centicule(int completeLatitude, boolean south, int completeLongitude, boolean west) {
        this.mSouth = south;
        this.mWest = west;

        setCompleteLatitude(completeLatitude);
        setCompleteLongitude(completeLongitude);
    }

    /**
     * <p>
     * Constructs a new Centicule with the given latitude and longitude as
     * doubles. This can thus make Centicule directly from GPS inputs. Note
     * that values that shoot around the planet will be clamped to 89.9 degrees
     * latitude and 179.9 degrees longitude (positive or negative).
     * </p>
     *
     * <p>
     * Negative values will be interpreted as south and west. Please don't use
     * this if you're standing directly on the equator and/or Prime Meridian and
     * GPS gives you a direct zero.
     * </p>
     *
     * @param latitude latitude to set
     * @param longitude longitude to set
     */
    public Centicule(double latitude, double longitude) {
        mSouth = latitude < 0;
        mWest = longitude < 0;
        setCompleteLatitude((int)(Math.abs(latitude) * 10));
        setCompleteLongitude((int)(Math.abs(longitude) * 10));
    }

    /**
     * <p>
     * Constructs a new Centicule with the given String forms of the latitude
     * and longitude.  This is expecting doubles, using negatives for south and
     * west.  Yes, this goes through Double.parseDouble().
     * </p>
     *
     * <p>
     * TODO: This should really be able to handle N/E/S/W suffixes.
     * </p>
     *
     * @param latitude latitude to set
     * @param longitude longitude to set
     * @throws NumberFormatException either of the input strings weren't numbers
     */
    public Centicule(@NonNull String latitude, @NonNull String longitude)
            throws NumberFormatException {
        // As uneasy as floating point precision makes me, Double.parseDouble is
        // probably the best bet here, as I could not for the life of me figure
        // out a regex that Java appreciated and I wasn't in the mood to try
        // parsing the strings one character at a time.
        if(latitude.charAt(0) == '-') mSouth = true;
        if(longitude.charAt(0) == '-') mWest = true;
        mLatitude = (int)(Math.abs(Double.parseDouble(latitude) * 10));
        mLongitude = (int)(Math.abs(Double.parseDouble(longitude) * 10));
    }

    @NonNull
    @Override
    public Centicule createOffset(int latOff, int lonOff) {
        if(latOff == 0 && lonOff == 0) return this;

        // The units of latOff and lonOff are 0.1 degrees this time around, but
        // otherwise it's similar to Graticule's version of this.
        boolean goingSouth = (latOff < 0);
        latOff = Math.abs(latOff);

        int finalLat = mLatitude;
        int finalLon = mLongitude;
        boolean finalSouth = mSouth;
        boolean finalWest = mWest;

        // Skip the following if latitude is unaffected.
        if (latOff != 0) {
            if (mSouth == goingSouth) {
                // Going the same direction, no equator-hacking needed.
                finalLat += latOff;
            } else {
                // Going opposite directions, check for equator-hacking.
                if (mLatitude < latOff) {
                    // We cross the equator!
                    latOff--;
                    finalSouth = !finalSouth;
                }
                finalLat = Math.abs(mLatitude - latOff);
            }

            if(finalLat > 899) {
                throw new IllegalArgumentException("That centicule does not exist, as it goes over the poles (" + finalLat + (finalSouth ? "S" : "N") + ")");
            }
        }

        // For the purposes of math, we're treating longitude as a value between
        // 0 and 3599, similar to how Graticule does it.  That is, 179.9W is 0,
        // 0W is 1799, 0E is 1800, and 179.9E is 3599.
        if(finalWest)
            // We're somewhere in the west; invert the current longitude and add
            // 1799 to get it in line with our calculations.
            finalLon = -finalLon + 1799;
        else
            // We're somewhere in the east; just add 1800, as the values are
            // already positive.
            finalLon += 1800;

        // Now, add the longitudinal offset (in centicules).
        finalLon += lonOff;
        // If we've overshot the planet longitudinally, clamp things back down
        // to the 0-3599 range with modulo.
        finalLon %= 3600;

        if(finalLon < 0) finalLon = 3600 - Math.abs(finalLon);

        if(finalLon >= 1800) {
            finalWest = false;
            finalLon -= 1800;
        } else {
            finalWest = true;
            finalLon -= 1799;
        }

        finalLon = Math.abs(finalLon);

        return new Centicule(finalLat, finalSouth, finalLon, finalWest);
    }

    @Override
    public boolean uses30WRule() {
        return mLongitude < 300 || (!mWest);
    }

    @Override
    public boolean uses30WRuleAtDate(Calendar cal) {
        return cal.after(LIMIT_30W) && uses30WRule();
    }

    private void setCompleteLatitude(int completeLatitude) {
        // In this case, we're assuming we don't have to convert anything.
        mLatitude = completeLatitude;

        if(mLatitude > 899) {
            mLatitude = 899;
        }
    }

    private void setLatitudeAndFraction(int latitude, int latitudeFraction) {
        if(Math.abs(latitudeFraction) > 9) {
            throw new IllegalArgumentException("Invalid latitude fraction: " + latitudeFraction);
        }

        setCompleteLatitude(Math.abs(latitude * 10) + Math.abs(latitudeFraction));
    }

    private void setCompleteLongitude(int completeLongitude) {
        mLongitude = completeLongitude;

        if(mLongitude > 1799) {
            mLongitude = 1799;
        }
    }

    private void setLongitudeAndFraction(int longitude, int longitudeFraction) {
        if(Math.abs(longitudeFraction) > 9) {
            throw new IllegalArgumentException("Invalid longitude fraction: " + longitudeFraction);
        }

        setCompleteLongitude(Math.abs(longitude * 10) + Math.abs(longitudeFraction));
    }

    @NonNull
    @Override
    public String getLatitudeString(boolean useNegativeValues) {
        String formattedLat = FORMAT_CENTICULE.format(mLatitude * 0.1);
        if (mSouth) {
            if(useNegativeValues) {
                return "-" + formattedLat;
            } else {
                return formattedLat + "S";
            }
        } else {
            if(useNegativeValues) {
                return formattedLat;
            } else {
                return formattedLat + "N";
            }
        }
    }

    @NonNull
    @Override
    public String getLongitudeString(boolean useNegativeValues) {
        String formattedLon = FORMAT_CENTICULE.format(mLongitude * 0.1);
        if (mWest) {
            if(useNegativeValues) {
                return "-" + formattedLon;
            } else {
                return formattedLon + "W";
            }
        } else {
            if(useNegativeValues) {
                return formattedLon;
            } else {
                return formattedLon + "E";
            }
        }
    }

    @Override
    public double getLatitudeForHash(double latHash) {
        if(latHash < 0 || latHash > 1) {
            throw new IllegalArgumentException("Invalid latHash value (less than 0 or greater than 1)");
        }

        return ((mLatitude * 0.1) + (latHash * 0.1)) * (mSouth ? -1 : 1);
    }

    @Override
    public double getLongitudeForHash(double lonHash) {
        if(lonHash < 0 || lonHash > 1) {
            throw new IllegalArgumentException("Invalid lonHash value (less than 0 or greater than 1)");
        }

        return ((mLongitude * 0.1) + (lonHash * 0.1)) * (mWest ? -1 : 1);
    }

    private double getLatitudeAsDouble() {
        return mSouth
                ? -mLatitude * 0.1
                : mLatitude * 0.1;
    }

    private double getLongitudeAsDouble() {
        return mWest
                ? -mLongitude * 0.1
                : mLongitude * 0.1;
    }

    @NonNull
    @Override
    public String getTitleString(boolean useNegativeValues) {
        return getLatitudeString(useNegativeValues)
                + ' '
                + getLongitudeString(useNegativeValues);
    }

    @NonNull
    @Override
    public LatLng getCenterLatLng() throws IllegalArgumentException {
        double lat = getLatitudeAsDouble() + (0.05 * (mSouth ? -1 : 1));
        double lon = getLongitudeAsDouble() + (0.05 * (mWest ? -1 : 1));

        return new LatLng(lat, lon);
    }

    @NonNull
    @Override
    public PolygonOptions getPolygon() throws IllegalArgumentException {
        PolygonOptions toReturn = new PolygonOptions();

        double top, left, bottom, right;

        if(mSouth) {
            bottom = -getLatitudeAsDouble() - 0.1;
            top = -getLatitudeAsDouble();
        } else {
            bottom = getLatitudeAsDouble();
            top = getLatitudeAsDouble() + 0.1;
        }

        if(mWest) {
            right = -getLongitudeAsDouble() - 0.1;
            left = -getLongitudeAsDouble();
        } else {
            right = getLongitudeAsDouble();
            left = getLongitudeAsDouble() + 0.1;
        }

        // Now, draw the polygon.  Er... make the options.
        toReturn.add(new LatLng(top, left))
                .add(new LatLng(top, right))
                .add(new LatLng(bottom, right))
                .add(new LatLng(bottom, left));

        // Shove this into a GoogleMap, and style it as need be.
        return toReturn;
    }

    @NonNull
    @Override
    public LatLng makePointFromHash(double latHash, double lonHash) {
        return new LatLng(getLatitudeForHash(latHash), getLongitudeForHash(lonHash));
    }

    @NonNull
    @Override
    public String getWikiPageSuffix() {
        // After doing some moderate research on the wiki, it looks like there
        // isn't a specific page name template for a centicule.  Any mention of
        // centicules in expeditions seem to just point out when a hashpoint was
        // in a given centicule, though it was still the same point for the
        // parent graticule.  To that end, this will just use the graticule's
        // wiki page until someone tells me different.
        //
        // Well, until someone with some authority or demonstrated precedent
        // tells me different, I feel I should specify.
        return "_"
                + (mSouth ? "-" : "") + (mLatitude / 10)
                + "_"
                + (mWest ? "-" : "") + (mLongitude / 10);
    }

    @NonNull
    @Override
    public JSONObject serializeToJSON() throws JSONException {
        JSONObject output = new JSONObject();

        output.put("type", Type.CENTICULE.name());
        output.put("latitude", mLatitude);
        output.put("longitude", mLongitude);
        output.put("isSouth", mSouth);
        output.put("isWest", mWest);

        return output;
    }

    /**
     * Deserializes a JSONObject into a brand new Centicule.
     *
     * @return a new Centicule
     * @throws JSONException something went wrong, JSON style
     */
    @NonNull
    public static Centicule deserializeFromJSON(JSONObject input) throws JSONException {
        return new Centicule(input.getInt("latitude"),
                input.getBoolean("isSouth"),
                input.getInt("longitude"),
                input.getBoolean("isWest"));
    }

    /**
     * Deparcelizinate a Centicule.
     *
     * @param in the parcel to deparcelize
     */
    private Centicule(Parcel in) {
        readFromParcel(in);
    }

    public static final Parcelable.Creator<Centicule> CREATOR = new Parcelable.Creator<Centicule>() {
        public Centicule createFromParcel(Parcel in) {
            return new Centicule(in);
        }

        public Centicule[] newArray(int size) {
            return new Centicule[size];
        }
    };

    public void readFromParcel(Parcel in) {
        // This is like Graticule, only there's four things, as we also need to
        // stash away the fractional parts.  Otherwise, deparcelizing is the
        // same.
        int absLat = in.readInt();
        int absLon = in.readInt();

        if(absLat < 900) {
            mSouth = true;
            mLatitude = 899 - absLat;
        } else {
            mSouth = false;
            mLatitude = absLat - 900;
        }

        if(absLon < 1800) {
            mWest = true;
            mLongitude = 1799 - absLon;
        } else {
            mWest = false;
            mLongitude = absLon - 1800;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // Same logic as Graticule, just with extra stuff for fractional parts.
        if(mSouth)
            dest.writeInt(Math.abs(mLatitude - 899));
        else
            dest.writeInt(mLatitude + 900);

        if(mWest)
            dest.writeInt(Math.abs(mLongitude - 1799));
        else
            dest.writeInt(mLongitude + 1800);
    }

    @Override
    public boolean equals(Object o) {
        // First, this better be a Centicule.
        if(o == this) return true;
        if (!(o instanceof Centicule))
            return false;

        final Centicule c = (Centicule)o;

        // If everything matches up, these are identical. Two double checks and
        // two boolean checks are probably a lot faster than two String checks,
        // right?
        return !(c.getLatitudeAsDouble() != getLatitudeAsDouble()
                || c.getLongitudeAsDouble() != getLongitudeAsDouble()
                || c.mSouth != mSouth || c.mWest != mWest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Type.CENTICULE, mLatitude, mLongitude, mSouth, mWest);
    }

    @Override
    @NonNull
    public String toString() {
        return "Centicule for " + getTitleString(false);
    }
}
