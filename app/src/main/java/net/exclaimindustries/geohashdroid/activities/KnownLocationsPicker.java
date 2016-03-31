/*
 * KnownLocationsPicker.java
 * Copyright (C) 2016 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.VisibleRegion;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.KnownLocation;
import net.exclaimindustries.geohashdroid.util.UnitConverter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * KnownLocationsPicker is another map-containing Activity.  This one allows the
 * user to set "known locations", which can trigger notifications if the day's
 * hashpoint is near one of them.  This isn't CentralMap, mind; it doesn't have
 * the entire CentralMapMode architecture or stock-grabbing functionality.  And
 * for that, I'm thankful.
 */
public class KnownLocationsPicker
        extends BaseMapActivity
        implements GoogleMap.OnMapLongClickListener,
                   GoogleMap.OnMarkerClickListener,
                   GoogleMap.OnInfoWindowClickListener {
    private static final String DEBUG_TAG = "KnownLocationsPicker";

    // These get passed into the dialog.
    private static final String NAME = "name";
    private static final String LATLNG = "latLng";
    private static final String RANGE = "range";
    private static final String EXISTING = "existing";

    // This is for restoring the map from an instance bundle.
    private static final String CLICKED_MARKER = "clickedMarker";

    private static final String EDIT_DIALOG = "editDialog";

    public enum LookupErrorCode {
        OKAY,
        IO_ERROR,
        NO_GEOCODER,
        INTERNAL_ERROR
    }

    /**
     * This dialog pops up when either adding or editing a KnownLocation.
     */
    public static class EditKnownLocationDialog extends DialogFragment {
        private LatLng mLocation;
        private KnownLocation mExisting;

        @Override
        @SuppressLint("InflateParams")
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // It's either this or we make a callback mechanism for something
            // that literally only gets used once.
            if(!(getActivity() instanceof KnownLocationsPicker))
                throw new IllegalStateException("An EditKnownLocationDialog can only be instantiated from the KnownLocationsPicker activity!");

            final KnownLocationsPicker pickerActivity = (KnownLocationsPicker)getActivity();

            // The arguments MUST be defined, else we're not doing anything.
            Bundle args = getArguments();
            if(args == null || !args.containsKey(RANGE) || !args.containsKey(LATLNG) || !args.containsKey(NAME)) {
                throw new IllegalArgumentException("Missing arguments to EditKnownLocationDialog!");
            }

            // Time to inflate!
            LayoutInflater inflater = ((LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE));

            View dialogView = inflater.inflate(R.layout.edit_known_location_dialog, null);

            String name;
            int range;

            // Right!  Go to the arguments first.
            name = args.getString(NAME);
            range = convertRangeToPosition(args.getDouble(RANGE));
            mExisting = args.getParcelable(EXISTING);
            mLocation = args.getParcelable(LATLNG);

            // If there's a saved instance state, that overrides the name and
            // range.  Not the location, though.  That's locked in at this
            // point.
            if(savedInstanceState != null) {
                name = savedInstanceState.getString(NAME);
                range = savedInstanceState.getInt(RANGE);
            }

            // Now then!  Let's create this mess.  First, if this is a location
            // that already exists, the user can delete it.  Otherwise, that
            // button goes away.
            View deleteButton = dialogView.findViewById(R.id.delete_location);
            if(mExisting == null) {
                deleteButton.setVisibility(View.GONE);
            } else {
                deleteButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickerActivity.deleteActiveKnownLocation(mExisting);
                        dismiss();
                    }
                });
            }

            // The input takes on whatever name it needs to.
            final EditText nameInput = (EditText)dialogView.findViewById(R.id.input_location_name);
            nameInput.setText(name);

            // The spinner needs an adapter.  Fortunately, a basic one will do,
            // as soon as we figure out what units we're using.
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(pickerActivity);
            String units = prefs.getString(GHDConstants.PREF_DIST_UNITS, GHDConstants.PREFVAL_DIST_METRIC);
            ArrayAdapter<CharSequence> adapter;

            if(units.equals(GHDConstants.PREFVAL_DIST_METRIC))
                adapter = ArrayAdapter.createFromResource(pickerActivity, R.array.known_locations_ranges_metric, android.R.layout.simple_spinner_item);
            else
                adapter = ArrayAdapter.createFromResource(pickerActivity, R.array.known_locations_ranges_imperial, android.R.layout.simple_spinner_item);

            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            final Spinner spinner = (Spinner)dialogView.findViewById(R.id.spinner_location_range);
            spinner.setAdapter(adapter);
            spinner.setSelection(range);

            // There!  Now, let's make it a dialog.
            return new AlertDialog.Builder(pickerActivity)
                    .setView(dialogView)
                    .setTitle(mExisting != null ? R.string.known_locations_title_edit : R.string.known_locations_title_add)
                    .setPositiveButton(R.string.ok_label, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            pickerActivity.confirmKnownLocationFromDialog(
                                    nameInput.getText().toString(),
                                    mLocation,
                                    convertPositionToRange(spinner.getSelectedItemPosition()),
                                    mExisting);
                            dismiss();
                        }
                    })
                    .setNegativeButton(R.string.cancel_label, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            pickerActivity.removeActiveKnownLocation();
                            dismiss();
                        }
                    })
                    .create();
        }

        private double convertPositionToRange(int id) {
            return (double)getResources().getIntArray(R.array.known_locations_values)[id];
        }

        private int convertRangeToPosition(double range) {
            int pos = 0;
            for(int i : getResources().getIntArray(R.array.known_locations_values)) {
                if(range <= i)
                    return pos;

                pos++;
            }

            return pos;
        }
    }

    private class LocationSearchTask extends AsyncTask<String, Void, LookupErrorCode> {
        private List<Address> mAddresses = new ArrayList<>();
        private VisibleRegion mVis;
        private float mBearing;

        public LocationSearchTask(VisibleRegion vis, float bearing) {
            super();

            mVis = vis;
            mBearing = bearing;
        }

        @Override
        protected LookupErrorCode doInBackground(String... params) {
            if(mGeocoder == null) return LookupErrorCode.NO_GEOCODER;

            LookupErrorCode toReturn = LookupErrorCode.OKAY;

            // As initial tests proved, we really should try to narrow down the
            // location to roughly where the user is looking at the time.
            // Remember that the projection can do all sorts of crazy stuff, so
            // let's get the biggest rectangle we can from there.
            double lowerLeftLat, lowerLeftLon, upperRightLat, upperRightLon;

            // All we need is more or less an estimate of what the proper
            // rectangle is.  Since we have the visible region AND we know what
            // the rotation is, we can guess at a decent rectangle quickly.  And
            // more than a bit hackishly.  Come with me on this journey.
            if(mBearing >= 0.0f && mBearing < 45.0f) {
                // 0 - 45: The near-left and far-right coordinates are directly
                // what we want, more or less.
                lowerLeftLat = mVis.nearLeft.latitude;
                lowerLeftLon = mVis.nearLeft.longitude;
                upperRightLat = mVis.farRight.latitude;
                upperRightLon = mVis.farRight.longitude;
            } else if(mBearing >= 45.0f && mBearing < 90.0f) {
                // 45 - 90: Near-left works for the left boundary, but we need
                // near-right for the bottom.  Similarly, far-left is the top
                // and far-right is the right.
                lowerLeftLat = mVis.nearRight.latitude;
                lowerLeftLon = mVis.nearLeft.longitude;
                upperRightLat = mVis.farLeft.latitude;
                upperRightLon = mVis.farRight.longitude;
            } else if(mBearing >= 90.0f && mBearing < 135.0f) {
                // And we continue rotating in that manner.
                lowerLeftLat = mVis.nearRight.latitude;
                lowerLeftLon = mVis.nearRight.longitude;
                upperRightLat = mVis.farLeft.latitude;
                upperRightLon = mVis.farLeft.longitude;
            } else if(mBearing >= 135.0f && mBearing < 180.0f) {
                lowerLeftLat = mVis.farRight.latitude;
                lowerLeftLon = mVis.nearRight.longitude;
                upperRightLat = mVis.nearLeft.latitude;
                upperRightLon = mVis.farLeft.longitude;
            } else if(mBearing >= 180.0f && mBearing < 225.0f) {
                lowerLeftLat = mVis.farRight.latitude;
                lowerLeftLon = mVis.farRight.longitude;
                upperRightLat = mVis.nearLeft.latitude;
                upperRightLon = mVis.nearLeft.longitude;
            } else if(mBearing >= 225.0f && mBearing < 270.0f) {
                lowerLeftLat = mVis.farLeft.latitude;
                lowerLeftLon = mVis.farRight.longitude;
                upperRightLat = mVis.nearRight.latitude;
                upperRightLon = mVis.nearLeft.longitude;
            } else if(mBearing >= 270.0f && mBearing < 315.0f) {
                lowerLeftLat = mVis.farLeft.latitude;
                lowerLeftLon = mVis.farLeft.longitude;
                upperRightLat = mVis.nearRight.latitude;
                upperRightLon = mVis.nearRight.longitude;
            } else {
                lowerLeftLat = mVis.nearLeft.latitude;
                lowerLeftLon = mVis.farRight.longitude;
                upperRightLat = mVis.farRight.latitude;
                upperRightLon = mVis.nearLeft.longitude;
            }

            // I really hope we're not calling this with a bunch of Strings, but
            // sure, let's be defensive, why not?
            try {
                for(String s : params) {
                    if(isCancelled()) break;

                    List<Address> result = mGeocoder.getFromLocationName(
                            s,
                            10,
                            lowerLeftLat,
                            lowerLeftLon,
                            upperRightLat,
                            upperRightLon);

                    // If there was no result, well, broaden the search.
                    if(result == null || result.isEmpty())
                        result = mGeocoder.getFromLocationName(s, 10);

                    if(result != null)
                        mAddresses.addAll(result);
                }
            } catch (IOException ioe) {
                toReturn = LookupErrorCode.IO_ERROR;
            } catch (IllegalArgumentException iae) {
                toReturn = LookupErrorCode.INTERNAL_ERROR;
            }

            // Remember, we're returning the error code, not the list of
            // addresses, since we want to report that error if need be.
            return toReturn;
        }

        @Override
        protected void onPostExecute(LookupErrorCode code) {
            // Got a response!  Go go go!
            searchResults(code, mAddresses);
        }
    }

    private GoogleMap mMap;
    private Geocoder mGeocoder;
    private LocationSearchTask mSearchTask;

    private boolean mMapIsReady = false;

    private BiMap<Marker, KnownLocation> mMarkerMap;

    private List<KnownLocation> mLocations;
    private Marker mMapClickMarker;
    private MarkerOptions mMapClickMarkerOptions;

    private Marker mActiveMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // We've got a layout, so let's use the layout.
        setContentView(R.layout.known_locations);

        // Now, we'll need to get the list of KnownLocations right away so we
        // can put them on the map.  Well, I guess not RIGHT away.  We still
        // have to wait on the map callbacks, but still, let's fetch them now.
        mLocations = KnownLocation.getAllKnownLocations(this);

        // We need a map.
        mMarkerMap = HashBiMap.create();

        // Prep a client (it'll get going during onStart)!
        mGoogleClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        // We need a Geocoder!  Well, not really; if we can't get one, remove
        // the search option.
        if(Geocoder.isPresent()) {
            mGeocoder = new Geocoder(this);

            // A valid Geocoder also means we can attach the click listner.
            final EditText input = (EditText)findViewById(R.id.search);
            findViewById(R.id.search_go).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    searchForLocation(input.getText().toString());
                }
            });

        } else {
            findViewById(R.id.search_box).setVisibility(View.GONE);
        }

        // Our friend the map needs to get ready, too.
        MapFragment mapFrag = (MapFragment)getFragmentManager().findFragmentById(R.id.map);
        mapFrag.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;

                // I could swear you could do this in XML...
                UiSettings set = mMap.getUiSettings();

                // The My Location button has to go off, as the search bar sort
                // of takes up that space.
                set.setMyLocationButtonEnabled(false);

                // Also, get rid of the map toolbar.  That just doesn't make any
                // sense here if we've already got a search widget handy.
                set.setMapToolbarEnabled(false);

                // Get ready to listen for clicks!
                mMap.setOnMapLongClickListener(KnownLocationsPicker.this);
                mMap.setOnInfoWindowClickListener(KnownLocationsPicker.this);

                // Were we waiting on a long-tapped marker?
                if(mMapClickMarkerOptions != null) {
                    // Well, then put the marker back on the map!
                    mMapClickMarker = mMap.addMarker(mMapClickMarkerOptions);
                    mActiveMarker = mMapClickMarker;
                    mMapClickMarker.showInfoWindow();
                }

                // Activate My Location if permissions are right.
                if(checkLocationPermissions(0))
                    permissionsGranted();

                // Same as CentralMap, we need to wait on both this AND the Maps
                // API to be ready.
                mMapIsReady = true;
                doReadyChecks();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Service up!
        mGoogleClient.connect();
    }

    @Override
    protected void onStop() {
        // Service down!
        mGoogleClient.disconnect();

        if(mSearchTask != null)
            mSearchTask.cancel(true);

        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // If we were looking at a click marker, hold on to it.
        if(mMapClickMarkerOptions != null) {
            outState.putParcelable(CLICKED_MARKER, mMapClickMarkerOptions);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        // Did we have a click marker?  Once the map's ready, we'll put it back
        // in place.
        if(savedInstanceState.containsKey(CLICKED_MARKER)) {
            mMapClickMarkerOptions = savedInstanceState.getParcelable(CLICKED_MARKER);
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        if(!isFinishing())
            doReadyChecks();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(permissions.length <= 0 || grantResults.length <= 0)
            return;

        // CentralMap will generally be handling location permissions.  So...
        if(grantResults[0] == PackageManager.PERMISSION_DENIED) {
            // If permissions get denied here, we ignore them and just don't
            // enable My Location support.
            mPermissionsDenied = true;
        } else {
            // Permissions... HO!!!!
            permissionsGranted();
            mPermissionsDenied = false;
        }
    }

    private void permissionsGranted() {
        try {
            mMap.setMyLocationEnabled(true);
        } catch(SecurityException se) {
            // This shouldn't happen (permissionsGranted is called AFTER we get
            // permissions), but Android Studio simply is NOT going to be happy
            // unless I surround it with a try/catch, so...
            checkLocationPermissions(0);
        }
    }

    private boolean doReadyChecks() {
        if(mMapIsReady && mGoogleClient != null && mGoogleClient.isConnected()) {
            // The map should be centered on the currently-known locations.
            // Otherwise, well, default to dead zero, I guess.
            Log.d(DEBUG_TAG, "There are " + mLocations.size() + " known location(s).");

            CameraUpdate cam;

            if(!mLocations.isEmpty()) {
                // Also, let's put the initial markers down.
                initKnownLocations();

                // The initial zoom should either be enough to hold all known
                // locations, or just wherever the long-tap marker was if we're
                // coming in from a restart.
                if(mMapClickMarkerOptions != null) {
                    // This will still be not null; the map ready callback
                    // hasn't reset that.
                    cam = CameraUpdateFactory.newCameraPosition(
                            CameraPosition.builder()
                                .target(mMapClickMarkerOptions.getPosition())
                                .zoom(14.0f)
                                .build()
                    );
                } else {
                    LatLngBounds.Builder builder = LatLngBounds.builder();

                    for(KnownLocation kl : mLocations) {
                        builder.include(kl.getLatLng());
                    }

                    LatLngBounds bounds = builder.build();
                    cam = CameraUpdateFactory.newLatLngBounds(bounds, getResources().getDimensionPixelSize(R.dimen.map_zoom_padding));
                }

                mMap.animateCamera(cam);
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onMapLongClick(LatLng latLng) {
        // If there's already a marker, clear it out.
        if(mMapClickMarker != null) {
            mMapClickMarker.remove();
            mMapClickMarker = null;
            mMapClickMarkerOptions = null;
        }

        // If the user long-taps the map, we place a marker on the map and offer
        // the user the option to add that as a known location.  We want to keep
        // track of the MarkerOptions object because that's Parcelable, allowing
        // us to stash it away if we need to save the activity's bundle state.
        mMapClickMarkerOptions = createMarker(latLng, null);

        mMapClickMarker = mMap.addMarker(mMapClickMarkerOptions);
        mMapClickMarker.showInfoWindow();
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        // Is this marker associated with a KnownLocation?  If so, we use the
        // data from that to init the dialog, AND we keep track of it being an
        // active KnownLocation/Marker pair.
        String name = "";
        double range = 5.0;

        KnownLocation loc = null;
        if(mMarkerMap.containsKey(marker)) {
            // Got it!
            loc = mMarkerMap.get(marker);
            name = loc.getName();
            range = loc.getRange();
        }

        mActiveMarker = marker;

        // Now, we've got a dialog to pop up!
        Bundle args = new Bundle();
        args.putString(NAME, name);
        args.putParcelable(EXISTING, loc);
        args.putParcelable(LATLNG, marker.getPosition());
        args.putDouble(RANGE, range);

        EditKnownLocationDialog dialog = new EditKnownLocationDialog();
        dialog.setArguments(args);
        dialog.show(getFragmentManager(), EDIT_DIALOG);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        return false;
    }

    @NonNull
    private MarkerOptions createMarker(@NonNull LatLng latLng, @Nullable String title) {
        // This builds up the basic marker for a potential KnownLocation.  By
        // "potential", I mean something that isn't stored yet as a
        // KnownLocation, such as search results or map taps.  KnownLocation
        // ITSELF has a makeMarker method.
        if(title == null || title.isEmpty())
            title = UnitConverter.makeFullCoordinateString(this, latLng, false, UnitConverter.OUTPUT_SHORT);

        return new MarkerOptions()
                .position(latLng)
                .flat(true)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.known_location_tap_marker))
                .anchor(0.5f, 0.5f)
                .title(title)
                .snippet(getString(R.string.known_locations_tap_to_add));
    }

    private void initKnownLocations() {
        mMarkerMap = HashBiMap.create();

        for(KnownLocation kl : mLocations) {
            // Each KnownLocation gives us a MarkerOptions we can use.
            Marker newMark = mMap.addMarker(makeExistingMarker(kl));
            mMarkerMap.put(newMark, kl);
        }
    }

    @NonNull
    private MarkerOptions makeExistingMarker(@NonNull KnownLocation loc) {
        return loc.makeMarker(this).snippet(getString(R.string.known_locations_tap_to_edit));
    }

    private void confirmKnownLocationFromDialog(@NonNull String name,
                                                @NonNull LatLng location,
                                                double range,
                                                @Nullable KnownLocation existing) {
        // Okay, we got location data in.  Make one!
        KnownLocation newLoc = new KnownLocation(name, location, range);

        // Is this new or a replacement?
        if(existing != null) {
            // Replacement!  Or rather, remove the old one and re-add the new
            // one in place.
            int oldIndex = mLocations.indexOf(existing);
            mLocations.remove(oldIndex);
            mLocations.add(oldIndex, newLoc);

            // Since this is an existing KnownLocation, the marker should be in
            // that map, ripe for removal.
            mMarkerMap.inverse().remove(existing).remove();
        } else {
            // Brand new!
            mLocations.add(newLoc);
        }

        // In both cases, store the data and add a new marker.
        Marker newMark = mMap.addMarker(makeExistingMarker(newLoc));
        mMarkerMap.forcePut(newMark, newLoc);
        KnownLocation.storeKnownLocations(this, mLocations);

        // And remove the marker from the map.  The visual one this time.
        // TODO: Null-checking shouldn't be necessary here.
        if(mActiveMarker != null) mActiveMarker.remove();

        // And end the active parts.
        removeActiveKnownLocation();
    }

    private void deleteActiveKnownLocation(@NonNull KnownLocation existing) {
        // This better exist, else we're in trouble.
        if(!mMarkerMap.containsValue(existing)) return;

        // Clear it from the map and from the marker list.
        Marker marker = mMarkerMap.inverse().get(existing);
        marker.remove();
        mMarkerMap.remove(marker);

        // Then, remove it from the location list and push that back to the
        // preferences.
        mLocations.remove(existing);
        KnownLocation.storeKnownLocations(this, mLocations);

        // Also, clear out the active location and marker.
        removeActiveKnownLocation();
    }

    private void removeActiveKnownLocation() {
        mActiveMarker = null;
        mMapClickMarkerOptions = null;
    }

    private void searchForLocation(@NonNull String input) {
        // If we didn't init a Geocoder by this point, that means the search box
        // shouldn't have been available.
        if(mGeocoder == null) return;

        // Same if this was a blank input.
        if(input.trim().isEmpty()) return;

        // Disable the input field and search button until we're done.
        findViewById(R.id.search).setEnabled(false);
        findViewById(R.id.search_go).setEnabled(false);

        // Let's do it this way: We try to search, and if the Activity goes away
        // by the time it comes back, we act like it never happened.  That's the
        // simplest way around it.
        if(mSearchTask != null) {
            mSearchTask.cancel(false);
        }

        // Fire up a task!  Remember, getProjection and getCameraPosition need
        // to be called on main, so we pass those in to the AsyncTask.
        mSearchTask = new LocationSearchTask(mMap.getProjection().getVisibleRegion(), mMap.getCameraPosition().bearing);
        mSearchTask.execute(input);
    }

    private void searchResults(LookupErrorCode code, @NonNull List<Address> addresses) {
        // No matter what, a result means the searchy parts come back on.
        findViewById(R.id.search).setEnabled(true);
        findViewById(R.id.search_go).setEnabled(true);

        Log.d(DEBUG_TAG, "Addresses found: " + addresses.size());

        for(Address a : addresses) {
            Log.d(DEBUG_TAG, "Address: " + a.toString());
        }
    }
}
