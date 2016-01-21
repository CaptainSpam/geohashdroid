/*
 * KnownLocation.java
 * Copyright (C) 2016 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * This represents a single known location.  It's got a LatLng and a name, as
 * well as a way to serialize itself out to a preference, mostly by making
 * itself into a JSON chunk.
 */
public class KnownLocation {
    private String name;
    private LatLng location;

    private static final String DEBUG_TAG = "KnownLocation";

    /**
     * Private version of the constructor used during {@link #deserialize(JSONObject)}.
     */
    private KnownLocation() { }

    /**
     * Builds up a new KnownLocation.
     *
     * @param name the name of this location
     * @param location a LatLng where it can be found
     */
    public KnownLocation(@NonNull String name, @NonNull LatLng location) {
        this.name = name;
        this.location = location;
    }

    /**
     * Deserializes a JSONObject into a KnownLocation.
     *
     * @param obj the object to deserialize
     * @return a new KnownLocation, or null if something went wrong
     */
    @Nullable
    public static KnownLocation deserialize(@NonNull JSONObject obj) {
        KnownLocation toReturn = new KnownLocation();

        try {
            toReturn.name = obj.getString("name");
            toReturn.location = new LatLng(obj.getDouble("lat"), obj.getDouble("lon"));
            return toReturn;
        } catch(JSONException je) {
            Log.e(DEBUG_TAG, "Couldn't deserialize a location for some reason!", je);
            return null;
        }
    }

    /**
     * Serializes this out into a single JSONObject.
     *
     * @return a JSONObject that can be used to store this data.
     */
    @NonNull
    public JSONObject serialize() {
        JSONObject toReturn = new JSONObject();

        try {
            toReturn.put("name", name);
            toReturn.put("lat", location.latitude);
            toReturn.put("lon", location.longitude);
        } catch(JSONException je) {
            // This really, REALLY shouldn't happen.  Really.
            Log.e("KnownLocation", "JSONException trying to add data into the to-return object?  The hell?", je);
        }

        return toReturn;
    }

    /**
     * Makes a Marker out of this KnownLocation.  This can be directly placed on
     * the map, but you probably want to stick it in something that can build a
     * cluster or something.
     *
     * @return a Marker representing this KnownLocation
     */
    public Marker makeMarker() {
        // TODO: Make makeMarker make a Marker.
        return null;
    }
}
