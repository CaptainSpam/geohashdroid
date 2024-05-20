/*
 * Info.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid.util;

import java.security.InvalidParameterException;
import java.util.Date;
import java.util.Calendar;
import java.util.Objects;

import android.content.Context;
import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import androidx.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;

import net.exclaimindustries.tools.DateTools;

import org.json.JSONException;
import org.json.JSONObject;

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
 * <code>Info</code> objects are immutable and are meant to be generated from
 * HashBuilder as the last step once it has all the data it needs.  It can,
 * however, be built from anything else as need be.
 * </p>
 * 
 * @author Nicholas Killewald
 * 
 */
public class Info implements Parcelable {
    private double mLatHash;
    private double mLonHash;
    private Somethingicule mSomethingicule;
    private Calendar mDate;
    private boolean mRetroHash;
    private boolean mValid;

    /**
     * Creates an Info object with the given data.  Remember that this now takes
     * the latitude/longitude <i>hash components</i>, not the final coordinates.
     * 
     * @param latHash
     *            the destination's latitude hash, as a double
     * @param lonHash
     *            the destination's longitude hash, as a double
     * @param somethingicule
     *            the Somethingicule
     * @param date
     *            the date
     */
    public Info(double latHash,
                double lonHash,
                @NonNull Somethingicule somethingicule,
                @NonNull Calendar date) {
        mLatHash = latHash;
        mLonHash = lonHash;
        mSomethingicule = somethingicule;
        setDate(date);
        mValid = true;
    }
    
    /**
     * Creates an Info object with the given somethingicule and date, but which
     * is invalid (i.e. has no valid latitude/longitude data).  This is used
     * when StockRunner reports an error; this way, any Handler can at least
     * figure out what was going on in the first place.
     * 
     * @param somethingicule the Somethingicule
     * @param date the date
     */
    public Info(@NonNull Somethingicule somethingicule,
                @NonNull Calendar date) {
        mLatHash = 0;
        mLonHash = 0;
        mSomethingicule = somethingicule;
        setDate(date);
        mValid = false;
    }

    /**
     * Deparcelizes an Info object.  Obviously, this is used internally when we
     * need to rebuild Info from a Parcel, such as during Service operations.
     * 
     * @param in the parcel to deparcelize
     */
    private Info(Parcel in) {
        readFromParcel(in);
    }


    /**
     * Gets the latitude of the final destination.
     * 
     * @return the latitude
     */
    public double getLatitude() {
        return mSomethingicule.getLatitudeForHash(mLatHash);
    }

    /**
     * Gets the longitude of the final destination.
     * 
     * @return the longitude
     */
    public double getLongitude() {
        return mSomethingicule.getLongitudeForHash(mLonHash);
    }

    /**
     * Gets the fractional part of the latitude of the final destination.  That
     * is, the part determined by the hash.
     *
     * @return the fractional part of the latitude
     */
    public double getLatitudeHash() {
        return mLatHash;
    }

    /**
     * Gets the fractional part of the longitude of the final destination.  That
     * is, the part determined by the hash.
     *
     * @return the fractional part of the longitude
     */
    public double getLongitudeHash() {
        return mLonHash;
    }

    /**
     * Returns the final destination as a LatLng object, convenient for the Maps
     * v2 API.
     *
     * @return a LatLng based on the data obtained from the connection
     */
    @NonNull
    public LatLng getFinalDestinationLatLng() {
        return mSomethingicule.makePointFromHash(mLatHash, mLonHash);
    }

    /**
     * Returns the final destination as a Location object, convenient for
     * general Android-y location-based stuff.
     * 
     * @return a providerless Location based on the data obtained from the
     *         connection
     */
    @NonNull
    public Location getFinalLocation() {
        Location loc = new Location("");
        loc.setLatitude(getLatitude());
        loc.setLongitude(getLongitude());

        return loc;
    }

    /**
     * Gets the Somethingicule held by this Info.
     * 
     * @return the Somethingicule
     */
    @NonNull
    public Somethingicule getSomethingicule() {
        return mSomethingicule;
    }

    /**
     * Gets the Calendar used to generate this set of information.
     * 
     * @return the Calendar
     */
    @NonNull
    public Calendar getCalendar() {
        return mDate;
    }

    /**
     * Gets the Date object from the Calendar object used to generate this set
     * of information.
     * 
     * @return the Date of the Calendar
     */
    @NonNull
    public Date getDate() {
        return mDate.getTime();
    }
    
    /**
     * Gets the distance, in meters, from the given Location and the final
     * destination.
     * 
     * @param loc
     *            Location to compare
     * @return the distance, in meters, to the final destination
     */
    public float getDistanceInMeters(@NonNull Location loc) {
        return loc.distanceTo(getFinalLocation());
    }

    /**
     * Returns a calendar representing the date from which the stock price was
     * pulled from a given date/graticule pair.  That is, back a day for the 30W
     * Rule or globalhashes and rewinding to Friday if it falls on a weekend.
     * 
     * @param c date to adjust
     * @param g Somethingicule to use to determine if the 30W Rule is in effect
     *          (if null, assumes this is a globalhash which always uses 30W)
     * @return a new adjusted Calendar
     */
    @NonNull
    public static Calendar makeAdjustedCalendar(@NonNull Calendar c,
                                                @NonNull Somethingicule g) {
        // This adjusts the calendar for both the 30W Rule and to clamp all
        // weekend stocks to the preceding Friday.  This saves a few database
        // entries, as the weekend will always be Friday's value.  Note that
        // this doesn't account for holidays when the US stocks aren't trading.
        
        // First, clone the calendar.  We don't want to muck about with the
        // original for various reasons.
        Calendar cal = (Calendar)(c.clone());
        
        // Second, 30W Rule hackery.  If g is null, assume we're in a globalhash
        // (that is, adjustment is needed).  If the date is May 26, 2008 or
        // earlier (and this isn't a globalhash), ignore it anyway (the 30W Rule
        // only applies to non-globalhashes AFTER it was created).
        if(g.uses30WRuleAtDate(cal))
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

    /**
     * Determines if this Info represents a point whose date follows the 30W
     * Rule.  Note that globalhashes always follow the 30W Rule.
     * 
     * @return true if 30W or global, false if not
     */
    public boolean uses30WRule() {
        // TODO: THIS CHANGED!  Make sure all uses are still valid!
        return mSomethingicule.uses30WRuleAtDate(mDate);
    }
    
    /**
     * Determines if this Info represents a globalhash (and thus doesn't have
     * any sort of valid Graticule data).  Note that there's no way to set up
     * inspections to understand that isGlobalHash() == true implies
     * getGraticule() == null, so there may be issues that mean this rarely gets
     * called.
     * 
     * @return true if global, false if not
     * @deprecated Anything that uses this should be changed to use the Somethingicule data directly
     */
    public boolean isGlobalHash() {
        // TODO: isGlobalHash should be invalid; change all calls to either use
        // base Somethingicule calls or instanceof checks if it's really
        // necessary.
        return mSomethingicule instanceof Globalhashicule;
    }
    
    /**
     * Determines if this Info represents a retrohash; that is, a geohash or
     * globalhash from a date in the past.  Note that this will return false for
     * geohashes from the future (i.e. a weekend when we already have the stock
     * values).
     * 
     * @return true if a retrohash, false if a current hash
     */
    public boolean isRetroHash() {
        return mRetroHash;
    }
    
    /**
     * Determines if this Info is valid.  A valid Info has latitude and
     * longitude data and can thus be sent straight to the map.  An invalid one
     * doesn't and shouldn't be used for hashing, but CAN be used in a
     * StockRunner handler to know what the date and graticule was.
     * 
     * @return true if valid, false if not
     */
    public boolean isValid() {
        return mValid;
    }
    
    public static final Parcelable.Creator<Info> CREATOR = new Parcelable.Creator<Info>() {
        public Info createFromParcel(Parcel in) {
            return new Info(in);
        }

        public Info[] newArray(int size) {
            return new Info[size];
        }
    };

    
    @Override
    public int describeContents() {
        // We don't do anything special with this.
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // Let's make us a parcel.  Order is important, remember!
        dest.writeDouble(mLatHash);
        dest.writeDouble(mLonHash);
        dest.writeParcelable(mSomethingicule, flags);
        dest.writeInt(mDate.get(Calendar.YEAR));
        dest.writeInt(mDate.get(Calendar.MONTH));
        dest.writeInt(mDate.get(Calendar.DAY_OF_MONTH));
        dest.writeInt(mRetroHash ? 1 : 0);
    }
    
    /**
     * Reads an incoming Parcel and deparcelizes it.  I'm going to keep using
     * the term "deparcelize" and its most logical forms until it catches on.
     * 
     * @param in parcel to deparcelize
     */
    public void readFromParcel(Parcel in) {
        // Same order!  Go!
        mLatHash = in.readDouble();
        mLonHash = in.readDouble();
        // The parcel, when written, has class data written with it, so this
        // should inflate the correct class.  I think.
        mSomethingicule = in.readParcelable(Somethingicule.class.getClassLoader());

        mDate = Calendar.getInstance();

        // In order, this better be year, month, day-of-month.
        mDate.set(in.readInt(), in.readInt(), in.readInt());

        mRetroHash = (in.readInt() == 1);
    }
    
    private void setDate(@NonNull Calendar cal) {
        // First, actually set the date.
        mDate = cal;
        
        // Then, determine if this is before or after today's date.  Since a
        // straight comparison also takes time into account, we need to force
        // today's date to midnight.
        Calendar today = Calendar.getInstance();
        
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        
        // Yes, this means that if the hash is in the future, mRetroHash will
        // be false.  The only way that can happen is if this is a weekend hash
        // and we're checking on Friday or something.
        mRetroHash = cal.before(today);
    }

    /**
     * Serializes this Info (and whatever's inside it) to a JSONObject.
     *
     * @return a new JSONObject of this Info
     * @throws JSONException if something goes weird with JSON creation
     */
    @NonNull
    public JSONObject serializeToJSON() throws JSONException {
        JSONObject output = new JSONObject();

        output.put("latHash", mLatHash);
        output.put("lonHash", mLonHash);
        output.put("timestamp",
                Long.valueOf(getDate().getTime()).toString());

        output.put("graticule", mSomethingicule.serializeToJSON());

        return output;
    }

    /**
     * Deserializes an Info from a JSONObject.
     *
     * @param input a JSONObject
     * @return a brand new, deserialized Info
     * @throws JSONException if something's amiss with JSON
     */
    @NonNull
    public static Info deserializeFromJSON(@NonNull JSONObject input) throws JSONException {
        // We at least know the calendar didn't change between versions.
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(
                Long.parseLong(input.getString("timestamp")));

        double lat, lon;
        Somethingicule grat;
        // The JSON format and internal assumptions about Info changed at some
        // point.  Fortunately, reconstructing that from old versions is
        // relatively easy, given only graticules and globalhashes existed back
        // then.
        if(input.has("latitude") && input.has("longitude")) {
            // The latitude and longitude keys are the giveaway (newer versions
            // would have latHash and lonHash).
            lat = input.getDouble("latitude");
            lon = input.getDouble("longitude");

            JSONObject gratObj = input.optJSONObject("graticule");
            if(gratObj != null) {
                // There was a graticule here.  We can math out what the hash
                // components were.  The JSON format of an earlier Graticule
                // didn't change, so we can just use the general deserializer.
                // We may want to remember to change this if the format ever
                // changes.
                grat = Somethingicule.Deserializer.deserialize(gratObj);

                Graticule actuallyGraticule = (Graticule)grat;

                lat = Math.abs(lat) - actuallyGraticule.getLatitude();
                lon = Math.abs(lon) - actuallyGraticule.getLongitude();
            } else {
                // This was a globalhash.  We already had the hash components,
                // so they remain the same.
                grat = Globalhashicule.getInstance();
            }
        } else {
            // If there's no latitude/longitude in the object, assume this is a
            // newer Info.  If not, then this will merrily throw exceptions all
            // over the place.
            lat = input.getDouble("latHash");
            lon = input.getDouble("lonHash");

            JSONObject gratObj = input.optJSONObject("graticule");
            if(gratObj != null) {
                // This really shouldn't be null if it's the new version.
                grat = Somethingicule.Deserializer.deserialize(gratObj);
            } else {
                // This shouldn't happen, given there was never a released
                // version of Info that used latHash/lonHash and still used null
                // for globalhash, but we can assume it's a globalhash anyway.
                grat = Globalhashicule.getInstance();
            }
        }

        return new Info(lat, lon, grat, cal);
    }

    /**
     * <p>
     * Builds a new Info object by applying a new Graticule to this Info object.
     * That is to say, this changes the destination of this Info object to
     * somewhere else, as if it were the same day and same stock value (and
     * thus the same hash).  Note that this will throw an exception if this
     * Info's 30W-alignment isn't the same as the new Graticule's, because that
     * might require a trip back to the internet.
     * </p>
     *
     * @param g new Graticule to apply
     * @throws InvalidParameterException this Info and Graticule do not lie on
     *                                   the same side of the 30W line
     * @return a new, improved Info object
     */
    @NonNull
    public Info cloneWithNewSomethingicule(@NonNull Somethingicule g) {
        if(uses30WRule() != g.uses30WRuleAtDate(mDate)) {
            throw new InvalidParameterException("The given Info and Graticule do not lie on the same side of the 30W line; this should not have happened.");
        }

        return new Info(mLatHash, mLonHash, g, getCalendar());
    }

    /**
     * Determines which Info of those given is closest to the also-given
     * Location.  The presence of the single Info param is because this is
     * generally called from the results of StockService with nearby points.
     * Any of the Infos (the single or the array) may be null; if both are null,
     * this will throw an exception.
     *
     * @param loc Location to compare against
     * @param info a single Info
     * @param nearby a bunch of Infos
     * @return the closest Info
     * @throws IllegalArgumentException info was null and nearby was either null or empty
     */
    @NonNull
    public static Info measureClosest(@NonNull Location loc,
                                      @Nullable Info info,
                                      @Nullable Info[] nearby)
        throws IllegalArgumentException {
        if(nearby == null || nearby.length == 0) {
            // If we were only given the single Info, return it.  Unless it's
            // null.
            if(info == null) {
                // If it's null, throw a fit.
                throw new IllegalArgumentException("You need to include at least one Info in measureClosest!");
            } else {
                return info;
            }
        }

        Info nearest = null;
        float bestDistance = Float.MAX_VALUE;

        // First, if we got a single Info, start with that.
        if(info != null) {
            nearest = info;
            bestDistance = loc.distanceTo(info.getFinalLocation());
        }

        // Now, loop through all the nearby Infos to see if any of those are any
        // better.
        for(Info i : nearby) {
            if(i == null) continue;

            float dist = loc.distanceTo(i.getFinalLocation());

            if(dist < bestDistance) {
                nearest = i;
                bestDistance = dist;
            }
        }

        // nearest can't be null here.  nearest gets assigned to be the single
        // info if it's not null, or at least one of the nearbys.  The only way
        // nearest can be null is if the distance of ALL the nearbys is equal to
        // Float.MAX_VALUE, which is just absurd.
        if(nearest == null)
            throw new IllegalArgumentException("You have impossible graticules that are somehow infinitely away from anything!");

        // And hey presto, we've got us a winner!
        return nearest;
    }

    /**
     * Gets the wiki page name for this expedition.
     *
     * @return a page name suitable for the wiki API
     * @throws IllegalStateException this Info is invalid
     */
    @NonNull
    public String getWikiPageName() {
        if(!mValid) {
            throw new IllegalStateException("This Info is invalid, and thus cannot have a wiki post");
        }

        return DateTools.getHyphenatedDateString(getCalendar())
                + mSomethingicule.getWikiPageSuffix();
    }

    /**
     * <p>
     * Gets the wiki expedition template from the Somethingicule.
     * </p>
     *
     * <p>
     * TODO: The wiki doesn't appear to have an Expedition template for
     * globalhashing yet.
     * </p>
     *
     * @param c a Context, just in case this is a globalhash
     * @return the template, as a string
     */
    @NonNull
    public String getWikiExpeditionTemplate(@NonNull Context c) {
        return mSomethingicule.makeWikiTemplate(this, c);
    }

    /**
     * Gets the text for the categories to put on the wiki for pictures.
     *
     * @return said categories
     */
    @NonNull
    public String getWikiCategories() {
        String date = DateTools.getHyphenatedDateString(getCalendar());

        String toReturn = "[[Category:Meetup on "
                + date + "]]\n";

        return toReturn + mSomethingicule.makeWikiCategories();
    }

    @Override
    @NonNull
    public String toString() {
        // This is mostly used for debugging purposes, so we may as well make it
        // useful.
        return "Info for Somethingicule "
                + mSomethingicule.toString()
                + " on " + DateTools.getDateString(mDate)
                + "; point is at "
                + getLatitude() + "," + getLongitude();
    }

    @Override
    public boolean equals(Object o) {
        if(o == this) return true;
        if(!(o instanceof Info)) return false;

        final Info other = (Info)o;

        // See if the Somethingicules match...
        if(!(mSomethingicule.equals(other.mSomethingicule))) return false;

        // ...and also check the date, latitude, and longitude.
        //noinspection RedundantIfStatement
        if(!mDate.equals(other.mDate)
                || (getLatitudeHash() != other.getLatitudeHash())
                || (getLongitudeHash() != other.getLongitudeHash()))
            return false;

        // Otherwise, we match!
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mValid,
                mRetroHash,
                mLatHash,
                mLonHash,
                mSomethingicule,
                mDate);
    }
}
