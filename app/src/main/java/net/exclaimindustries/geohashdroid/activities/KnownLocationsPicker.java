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
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.KnownLocation;
import net.exclaimindustries.geohashdroid.util.UnitConverter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * KnownLocationsPicker is another map-containing Activity.  This one allows the
 * user to set "known locations", which can trigger notifications if the day's
 * hashpoint is near one of them.  This isn't CentralMap, mind; it doesn't have
 * the entire CentralMapMode architecture or stock-grabbing functionality.  And
 * for that, I'm thankful.
 */
public class KnownLocationsPicker
        extends BaseMapActivity
        implements GoogleMap.OnMapClickListener,
                   GoogleMap.OnMarkerClickListener,
                   GoogleMap.OnInfoWindowClickListener {
    // These get passed into the dialog.
    private static final String NAME = "name";
    private static final String LATLNG = "latLng";
    private static final String RANGE = "range";
    private static final String EXISTS = "exists";

    private static final String EDIT_DIALOG = "editDialog";

    /**
     * This dialog pops up when either adding or editing a KnownLocation.
     */
    public static class EditKnownLocationDialog extends DialogFragment {
        private LatLng mLocation;

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
            if(args == null || !args.containsKey(RANGE) || !args.containsKey(LATLNG) || !args.containsKey(NAME) || !args.containsKey(EXISTS)) {
                throw new IllegalArgumentException("Missing arguments to EditKnownLocationDialog!");
            }

            // Time to inflate!
            LayoutInflater inflater = ((LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE));

            View dialogView = inflater.inflate(R.layout.edit_known_location_dialog, null);

            String name;
            int range;
            boolean exists;

            // Right!  Go to the arguments first.
            name = args.getString(NAME);
            range = convertRangeToPosition(args.getDouble(RANGE));
            exists = args.getBoolean(EXISTS);
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
            if(!exists) {
                dialogView.findViewById(R.id.delete_location).setVisibility(View.GONE);
            } else {
                dialogView.findViewById(R.id.delete_location).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickerActivity.deleteActiveKnownLocation();
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
                    .setTitle(exists ? R.string.known_locations_title_edit : R.string.known_locations_title_add)
                    .setPositiveButton(R.string.ok_label, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            pickerActivity.confirmKnownLocationFromDialog(
                                    nameInput.getText().toString(),
                                    mLocation,
                                    convertPositionToRange(spinner.getSelectedItemPosition()));
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

    private static final String DEBUG_TAG = "KnownLocationsPicker";

    private GoogleMap mMap;

    private boolean mMapIsReady = false;

    private Map<Marker, KnownLocation> mMarkerMap;

    private List<KnownLocation> mLocations;
    private Marker mMapClickMarker;

    private KnownLocation mActiveKnownLocation;
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
        mMarkerMap = new HashMap<>();

        // Prep a client (it'll get going during onStart)!
        mGoogleClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

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

                // Get ready to listen for clicks!
                mMap.setOnMapClickListener(KnownLocationsPicker.this);
                mMap.setOnInfoWindowClickListener(KnownLocationsPicker.this);

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

        super.onStop();
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

            if(!mLocations.isEmpty()) {
                // Also, let's put the initial markers down.
                initKnownLocations();

                LatLngBounds.Builder builder = LatLngBounds.builder();

                for(KnownLocation kl : mLocations) {
                    builder.include(kl.getLatLng());
                }

                LatLngBounds bounds = builder.build();
                CameraUpdate cam = CameraUpdateFactory.newLatLngBounds(bounds, getResources().getDimensionPixelSize(R.dimen.map_zoom_padding));
                mMap.animateCamera(cam);
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {
        // If there's already a marker, just clear it out.
        if(mMapClickMarker != null) {
            mMapClickMarker.remove();
            mMapClickMarker = null;
            return;
        }

        // If the user taps the map (and NOT a marker or info window), we place
        // a marker on the map and offer the user the option to add that as a
        // known location.
        MarkerOptions options = createMarker(latLng, null);

        mMapClickMarker = mMap.addMarker(options);
        mMapClickMarker.showInfoWindow();
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        // Is this marker associated with a KnownLocation?  If so, we use the
        // data from that to init the dialog, AND we keep track of it being an
        // active KnownLocation/Marker pair.
        String name = "";
        double range = 5.0;
        if(mMarkerMap.containsKey(marker)) {
            // Got it!
            mActiveKnownLocation = mMarkerMap.get(marker);
            name = mActiveKnownLocation.getName();
            range = mActiveKnownLocation.getRange();
        }

        mActiveMarker = marker;

        // Now, we've got a dialog to pop up!
        Bundle args = new Bundle();
        args.putString(NAME, name);
        args.putBoolean(EXISTS, mActiveKnownLocation != null);
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
        mMarkerMap = new HashMap<>();

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

    private void confirmKnownLocationFromDialog(@NonNull String name, @NonNull LatLng location, double range) {
        // Okay, we got location data in.  Make one!
        KnownLocation newLoc = new KnownLocation(name, location, range);

        // Is this new or a replacement?
        if(mActiveKnownLocation != null && mActiveMarker != null) {
            // Replacement!  Or rather, remove the old one and re-add the new
            // one in place.
            int oldIndex = mLocations.indexOf(mActiveKnownLocation);
            mLocations.remove(oldIndex);
            mLocations.add(oldIndex, newLoc);

            // Remove the marker from the map, too.  Not, y'know, the visual
            // map.  The data structure one.
            mMarkerMap.remove(mActiveMarker);
        } else {
            // Brand new!
            mLocations.add(newLoc);
        }

        // In both cases, store the data and add a new marker.
        Marker newMark = mMap.addMarker(makeExistingMarker(newLoc));
        mMarkerMap.put(newMark, newLoc);
        KnownLocation.storeKnownLocations(this, mLocations);

        // And remove the marker from the map.  The visual one this time.
        mActiveMarker.remove();

        // And end the active parts.
        removeActiveKnownLocation();
    }

    private void deleteActiveKnownLocation() {
        if(mActiveKnownLocation == null || mActiveMarker == null) return;

        // Clear it from the map and from the marker list.
        mActiveMarker.remove();
        mMarkerMap.remove(mActiveMarker);

        // Then, remove it from the location list and push that back to the
        // preferences.
        mLocations.remove(mActiveKnownLocation);
        KnownLocation.storeKnownLocations(this, mLocations);

        // Also, clear out the active location and marker.
        removeActiveKnownLocation();
    }

    private void removeActiveKnownLocation() {
        mActiveMarker = null;
        mActiveKnownLocation = null;
    }
}
