/*
 * KnownLocation.java
 * Copyright (C) 2016 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * This represents a single known location.  It's got a LatLng and a name, as
 * well as a way to serialize itself out to a preference, mostly by making
 * itself into a JSON chunk.
 */
public class KnownLocation {
    private String mName;
    private LatLng mLocation;

    private static final String DEBUG_TAG = "KnownLocation";

    /**
     * Private version of the constructor used during {@link #deserialize(JSONObject)}.
     */
    private KnownLocation() { }

    /**
     * Builds up a new KnownLocation.
     *
     * @param name the name of this mLocation
     * @param location a LatLng where it can be found
     */
    public KnownLocation(@NonNull String name, @NonNull LatLng location) {
        mName = name;
        mLocation = location;
    }

    /**
     * Deserializes a single JSONObject into a KnownLocation.
     *
     * @param obj the object to deserialize
     * @return a new KnownLocation, or null if something went wrong
     */
    @Nullable
    public static KnownLocation deserialize(@NonNull JSONObject obj) {
        KnownLocation toReturn = new KnownLocation();

        try {
            toReturn.mName = obj.getString("name");
            toReturn.mLocation = new LatLng(obj.getDouble("lat"), obj.getDouble("lon"));
            return toReturn;
        } catch(JSONException je) {
            Log.e(DEBUG_TAG, "Couldn't deserialize a mLocation for some reason!", je);
            return null;
        }
    }

    /**
     * Gets all KnownLocations from Preferences and returns them as a List.
     *
     * @param c a Context
     * @return a List full of KnownLocations (or an empty List)
     */
    @NonNull
    public static List<KnownLocation> getAllKnownLocations(@NonNull Context c) {
        List<KnownLocation> toReturn = new ArrayList<>();

        // To the preferences!
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
        String blob = prefs.getString(GHDConstants.PREF_KNOWN_LOCATIONS, "[]");

        // I really hope this is a JSONArray...
        JSONArray arr;
        try {
            arr = new JSONArray(blob);
        } catch(JSONException je) {
            Log.e(DEBUG_TAG, "Couldn't parse the known locations JSON blob!", je);
            return toReturn;
        }

        // What's more, I really hope every entry in the JSONArray is a
        // JSONObject that happens to be a KnownLocation...
        for(int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                KnownLocation kl = deserialize(obj);
                if(kl != null) toReturn.add(kl);
            } catch(JSONException je) {
                Log.e(DEBUG_TAG, "Item " + i + " in the known locations JSON blob wasn't a JSONObject!", je);
            }
        }

        return toReturn;
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
            toReturn.put("name", mName);
            toReturn.put("lat", mLocation.latitude);
            toReturn.put("lon", mLocation.longitude);
        } catch(JSONException je) {
            // This really, REALLY shouldn't happen.  Really.
            Log.e("KnownLocation", "JSONException trying to add data into the to-return object?  The hell?", je);
        }

        return toReturn;
    }

    /**
     * Stores a bunch of KnownLocations to preferences.  Note that this <b>replaces</b>
     * all currently-stored KnownLocations.
     *
     * @param c a Context
     * @param locations a List of KnownLocations
     */
    public static void storeKnownLocations(Context c, List<KnownLocation> locations) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
        SharedPreferences.Editor edit = prefs.edit();

        JSONArray arr = new JSONArray();

        for(KnownLocation kl : locations) {
            arr.put(kl.serialize());
        }

        // Man, that's easy.
        edit.putString(GHDConstants.PREF_KNOWN_LOCATIONS, arr.toString());
        edit.apply();
    }

    /**
     * Gets the name of this KnownLocation.
     *
     * @return a name
     */
    public String getName() {
        return mName;
    }

    /**
     * Gets the LatLng this KnownLocation represents.
     *
     * @return a LatLng
     */
    public LatLng getLatLng() {
        return mLocation;
    }

    /**
     * Makes a MarkerOptions out of this KnownLocation (when added to the map,
     * you get an actual Marker back).  This can be directly placed on the map,
     * but you might want to stick it in something that can build a cluster or
     * something.
     *
     * @return a MarkerOptions representing this KnownLocation
     */
    public MarkerOptions makeMarker() {
        // TODO: Make makeMarker make a Marker.
        return null;
    }
}
