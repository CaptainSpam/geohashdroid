/*
 * Globalhashicule.java
 * Copyright (C) 2024 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.util;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolygonOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import androidx.annotation.NonNull;

/**
 * A <code>Globalhashicule</code> isn't really a Somethingicule in the sense
 * that it has latitude/longitude info, as a globalhash itself doesn't care
 * about that, but it IS a Somethingicule in that it can go where
 * Somethingicules should go without having to deal with the whole "null means
 * globalhash" thing.  As such, this doesn't have any instance data whatsoever
 * and just reacts to whatever inputs it gets.
 *
 * @author Nicholas Killewald
 */
public final class Globalhashicule implements Somethingicule {
    private static final Globalhashicule mInstance = new Globalhashicule();

    private Globalhashicule() {}

    /**
     * Gets an instance of a Globalhashicule.  Since Somethingicules are
     * immutable and globalhashes don't have any instance data to store, there's
     * at least SOME sense in making Globalhashicule a singleton.
     *
     * @return the Globalhashicule instance
     */
    public static Globalhashicule getInstance() {
        return mInstance;
    }

    @NonNull
    @Override
    public Somethingicule createOffset(int latOff, int lonOff) {
        // This doesn't make any sense.
        throw new IllegalArgumentException("It doesn't make any sense to offset a Globalhashicule");
    }

    @Override
    public boolean uses30WRule() {
        // Globalhashes always use 30W.
        return true;
    }

    @Override
    public boolean uses30WRuleAtDate(Calendar cal) {
        // Globalhashes ALWAYS use 30W, no matter what date it is.
        return true;
    }

    @NonNull
    @Override
    public String getLatitudeString(boolean useNegativeValues) {
        return "";
    }

    @NonNull
    @Override
    public String getLongitudeString(boolean useNegativeValues) {
        return "";
    }

    @Override
    public double getLatitudeForHash(double latHash) {
        return latHash * 180 - 90;
    }

    @Override
    public double getLongitudeForHash(double lonHash) {
        return lonHash * 360 - 180;
    }

    @NonNull
    @Override
    public String getTitleString(boolean useNegativeValues) {
        return "Globalhash";
    }

    @NonNull
    @Override
    public LatLng getCenterLatLng() throws IllegalArgumentException {
        throw new IllegalArgumentException("Globalhashes cover the entire planet; its center isn't useful");
    }

    @NonNull
    @Override
    public PolygonOptions getPolygon() throws IllegalArgumentException {
        throw new IllegalArgumentException("Globalhashes cover the entire planet; getting a polygon makes no sense");
    }

    @NonNull
    @Override
    public LatLng makePointFromHash(double latHash, double lonHash) {
        return new LatLng(getLatitudeForHash(latHash),
                getLongitudeForHash(lonHash));
    }

    @NonNull
    @Override
    public String getWikiPageSuffix() {
        return "_global";
    }

    @NonNull
    @Override
    public JSONObject serializeToJSON() throws JSONException {
        // While there's nothing really to serialize here, we CAN at least
        // serialize the type so that the Somethingicule deserializer knows
        // what's up.  Note there's no corresponding deserializeToJSON, as
        // there's nothing to deserialize; the deserializer can just call
        // getInstance() directly.
        JSONObject toReturn = new JSONObject();
        toReturn.put("type", Type.GLOBALHASHICULE.name());

        return toReturn;
    }

    public static final Parcelable.Creator<Globalhashicule> CREATOR = new Parcelable.Creator<Globalhashicule>() {
        public Globalhashicule createFromParcel(Parcel in) {
            // There's nothing parcelized here, so there's nothing to
            // deparcelize.  Just return the instance.
            return mInstance;
        }

        public Globalhashicule[] newArray(int size) {
            return new Globalhashicule[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int i) {
        // There's nothing to parcelize here.
    }

    @Override
    public boolean equals(Object o) {
        // There should only ever be one Globalhashicule, it has no contents,
        // and its identity is irrelevant, so an instanceof check is all we
        // really need.
        return o instanceof Globalhashicule;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Type.GLOBALHASHICULE, "I AM THE GLOBALHASHICULE, THERE'S ONLY ONE OF ME, SO YEAH");
    }

    @Override
    @NonNull
    public String toString() {
        return "Globalhashicule";
    }
}
