/**
 * GeohashDroid.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;

/**
 * The main GeohashDroid class is where the entry point resides. It's where the
 * main window sits around and gathers data.
 * 
 * TODO: Good gravy, this is ugly and needs cleanup. Like, seriously. A lot of
 * this is major workaroundage for switching orientation (or other configs) when
 * a dialog is active. And I'm CERTAIN there has to be a better way to pull all
 * that off. Really.
 * 
 * @author Nicholas Killewald
 */
public class GeohashDroid extends Activity {
    // Top-level static strings; everything drags reference from here.
    public static final String PREFS_BASE = "GeohashDroid";
    public static final String DEFAULT_LAT = "DefaultLatitude";
    public static final String DEFAULT_LON = "DefaultLongitude";

    public static final String LONGITUDE = "longitude";
    public static final String LATITUDE = "latitude";
    public static final String YEAR = "year";
    public static final String MONTH = "month";
    public static final String DAY = "day";
    public static final String INFO = "info";

    // private static final String DEBUG_TAG = "GeohashDroid";

    private static final int DIALOG_SEARCHING = 0;
    private static final int DIALOG_SEARCH_FAIL = 1;
    private static final int DIALOG_FIND_STOCK = 2;
    private static final int DIALOG_STOCK_NOT_POSTED = 3;
    private static final int DIALOG_STOCK_ERROR = 4;
    private static final int DIALOG_ABOUT = 5;

    private static final int DIALOG_LAST_NUMBER = DIALOG_STOCK_ERROR;

    private static final int ALL_OKAY = -1;

    private static final int MENU_SETTINGS = 0;
    private static final int MENU_ABOUT = 1;

    private static final int REQUEST_PICK_GRATICULE = 0;

    /**
     * Action for picking a graticule. In Geohash Droid, this means to go to
     * GraticuleMap. Though, so long as it returns a
     * net.exclaimindustries.geohashdroid.Graticule object, I'd assume anything
     * could take its place if someone else writes a better graticule picker.
     */
    public static final String PICK_GRATICULE = "net.exclaimindustries.geohashdroid.PICK_GRATICULE";

    private EditText mLatitude;
    private EditText mLongitude;
    private Button mGoButton;

    private LocationManager mManager;
    private LocalLocationListener mListener;

    private HashFetcherRunner mHashFetcherRunner;
    private Thread mHashFetcherThread;

    private static int mLastDialog = ALL_OKAY;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        Resources res = getResources();

        // First things first, set up the default preferences.
        initPrefs();

        // If something's been retained, re-retain it. This is probably quite
        // a bit very very wrong.
        if (getLastNonConfigurationInstance() != null) {
            RetainedThings retainers = (RetainedThings)getLastNonConfigurationInstance();
            mHashFetcherThread = retainers.hashFetcherThread;
            mHashFetcherRunner = retainers.hashFetcherRunner;
            if (mHashFetcherRunner != null)
                mHashFetcherRunner.resetHandler(new HashFetchThreadHandler(
                        Looper.myLooper()));
            mManager = retainers.locationManager;
            mListener = retainers.locationListener;
        }

        // Set ourselves back up if we came in from elsewhere...
        mLatitude = (EditText)findViewById(R.id.Latitude);
        mLongitude = (EditText)findViewById(R.id.Longitude);

        /*
         * Order of priority:
         * 
         * 1. If we're coming back with Intent data (fell back to here from the
         * map?), use that. 2. If we're coming back from a saved instance state
         * (usually back from an onPause), keep that. 3. If we have
         * DefaultLatitude and DefaultLongitude settings AND they're both
         * Strings, use that. 4. Otherwise, go to a blank.
         */

        // Check savedInstanceState first.
        if (savedInstanceState != null) {
            // Go get the data!
            mLatitude.setText(savedInstanceState.getString(LATITUDE));
            mLongitude.setText(savedInstanceState.getString(LONGITUDE));
        }

        // If we came back with Intent data, repopulate with a new graticule.
        // (and blow up the old data)
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            if (extras.containsKey(LATITUDE))
                mLatitude.setText(extras.getInt(LATITUDE));
            if (extras.containsKey(LONGITUDE))
                mLongitude.setText(extras.getInt(LONGITUDE));
        }

        // If we had neither a savedInstanceState nor a bundle of extras, we
        // look into preferences. Assuming we do, of course.
        SharedPreferences prefs = getSharedPreferences(PREFS_BASE, 0);
        boolean rememberOn = true;
        try {
            rememberOn = prefs.getBoolean(res
                    .getString(R.string.pref_remembergraticule_key), true);
        } catch (Exception e) {
            rememberOn = true;
        }

        if (savedInstanceState == null && extras == null && rememberOn) {
            // Store the current values in case something goes wrong with
            // pulling the Strings later.
            String backupLat = mLatitude.getText().toString();
            String backupLon = mLongitude.getText().toString();

            if (prefs.contains(DEFAULT_LAT) && prefs.contains(DEFAULT_LON)) {
                try {
                    mLatitude.setText(prefs.getString(DEFAULT_LAT, ""));
                    mLongitude.setText(prefs.getString(DEFAULT_LON, ""));
                } catch (Exception e) {
                    // In this case, the preferences are set, but they're
                    // somehow not Strings. Clear us back to blanks.
                    mLatitude.setText(backupLat);
                    mLongitude.setText(backupLon);
                }
            }
        }

        // Now attach the listeners and reset the Go button, and stand back
        // and watch the fun!
        attachListeners();
        resetGoButton();

        // Rebuild the dialogs if any were around when we left.
        if (mLastDialog != ALL_OKAY) {
            // If we have one of the error dialogs up, show them.
            if (mLastDialog == DIALOG_SEARCH_FAIL
                    || mLastDialog == DIALOG_STOCK_NOT_POSTED
                    || mLastDialog == DIALOG_STOCK_ERROR)
                showDialog(mLastDialog);
            else if (mHashFetcherThread != null && mHashFetcherThread.isAlive()) {
                mLastDialog = DIALOG_FIND_STOCK;
                showDialog(DIALOG_FIND_STOCK);
            } else if (mLastDialog == DIALOG_SEARCHING && mListener != null
                    && !mListener.isDone()) {
                mListener.resetHandler(new LocationListenerHandler(Looper
                        .myLooper()));
                mLastDialog = DIALOG_SEARCHING;
                showDialog(DIALOG_SEARCHING);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuItem item;

        // Build us up a menu. Of only one setting for now.
        item = menu.add(Menu.NONE, MENU_SETTINGS, 0,
                R.string.menu_item_settings);
        item.setIcon(android.R.drawable.ic_menu_preferences);

        item = menu.add(Menu.NONE, MENU_ABOUT, 1, R.string.menu_item_about);
        item.setIcon(android.R.drawable.ic_menu_info_details);

        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        super.onMenuItemSelected(featureId, item);

        switch (item.getItemId()) {
            case MENU_SETTINGS:
                // Pop up our settings window!
                startActivity(new Intent(this, PreferenceEditScreen.class));
                return true;
            case MENU_ABOUT:
                showDialog(DIALOG_ABOUT);
                return true;
        }

        return false;
    }

    private boolean initPrefs() {
        // Initializes preferences. Returns true if anything changed, false if
        // everything went well.
        boolean toReturn = false;

        SharedPreferences prefs = getSharedPreferences(PREFS_BASE, 0);
        SharedPreferences.Editor editor = prefs.edit();

        Resources res = getResources();

        // AutoZoom defaults to on.
        if (!prefs.contains(res.getString(R.string.pref_autozoom_key))) {
            editor.putBoolean(res.getString(R.string.pref_autozoom_key), true);
            toReturn = true;
        }

        // Graticule remembering defaults to on.
        if (!prefs.contains(res.getString(R.string.pref_remembergraticule_key))) {
            editor.putBoolean(res
                    .getString(R.string.pref_remembergraticule_key), true);
            toReturn = true;
        }

        editor.commit();

        return toReturn;
    }

    @Override
    public Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_SEARCHING: {
                // Box that says that we're searching, and allows a cancel.
                AlertDialog.Builder build = new AlertDialog.Builder(this);
                build.setMessage(R.string.search_label);
                build.setTitle(R.string.search_title);
                build.setNegativeButton(R.string.cancel_label,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                GeohashDroid.this
                                        .dismissDialog(DIALOG_SEARCHING);
                                mManager.removeUpdates(mListener);
                                mLastDialog = ALL_OKAY;
                            }
                        });
                return build.create();
            }
            case DIALOG_SEARCH_FAIL: {
                // Box that says the graticule auto-detect failed.
                AlertDialog.Builder build = new AlertDialog.Builder(this);
                build.setMessage(R.string.error_search_failed);
                build.setTitle(R.string.error_title);
                build.setNegativeButton(R.string.darn_label,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                GeohashDroid.this
                                        .dismissDialog(DIALOG_SEARCH_FAIL);
                                mLastDialog = ALL_OKAY;
                            }
                        });
                return build.create();
            }
            case DIALOG_FIND_STOCK: {
                // Box that says we're looking for stock data.
                AlertDialog.Builder build = new AlertDialog.Builder(this);
                build.setMessage(R.string.stock_label);
                build.setTitle(R.string.standby_title);
                build.setNegativeButton(R.string.cancel_label,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                // Abort the connection and drop the dialog.
                                if (mHashFetcherThread != null
                                        && mHashFetcherThread.isAlive()
                                        && mHashFetcherRunner.mHashMaker != null)
                                    mHashFetcherRunner.mHashMaker.abort();
                                GeohashDroid.this
                                        .dismissDialog(DIALOG_FIND_STOCK);
                                mLastDialog = ALL_OKAY;
                            }
                        });
                return build.create();
            }
            case DIALOG_STOCK_NOT_POSTED: {
                // Box that says we're looking for stock data.
                AlertDialog.Builder build = new AlertDialog.Builder(this);
                build.setMessage(R.string.error_not_yet_posted);
                build.setTitle(R.string.error_title);
                build.setNegativeButton(R.string.darn_label,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                GeohashDroid.this
                                        .dismissDialog(DIALOG_STOCK_NOT_POSTED);
                                mLastDialog = ALL_OKAY;
                            }
                        });
                return build.create();
            }
            case DIALOG_STOCK_ERROR: {
                // Box that says we're looking for stock data.
                AlertDialog.Builder build = new AlertDialog.Builder(this);
                build.setMessage(R.string.error_server_failure);
                build.setTitle(R.string.error_title);
                build.setNegativeButton(R.string.darn_label,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                GeohashDroid.this
                                        .dismissDialog(DIALOG_STOCK_ERROR);
                                mLastDialog = ALL_OKAY;
                            }
                        });
                return build.create();
            }
            case DIALOG_ABOUT: {
                Dialog d = new AboutDialog(this);

                return d;
            }
        }

        return null;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // We have to explicitly remove all dialogs, else we run into
        // problems when the Activity is re-shown.
        for (int i = 0; i <= DIALOG_LAST_NUMBER; i++) {
            try {
                removeDialog(i);
            } catch (Exception e) {
                // Whoops! That one didn't exist! But forget about it, we'll
                // come up with it later, if ever.
            }
        }

        // Stash away our variables for later. Latitude and longitude will be
        // overwritten if we're coming back from graticule auto-search.
        outState.putString(LATITUDE, ((EditText)(findViewById(R.id.Latitude)))
                .getText().toString());
        outState
                .putString(LONGITUDE,
                        ((EditText)(findViewById(R.id.Longitude))).getText()
                                .toString());
    }

    private void resetGoButton() {
        // Read both fields and act accordingly.
        if (mGoButton == null)
            return;

        try {
            Integer.parseInt(mLatitude.getText().toString());
            Integer.parseInt(mLongitude.getText().toString());
            // If we survived both of those calls, we must have two integers
            // inputted, and thus we're good to go. Invalid inputs, for the
            // time being, are translated by the Graticule class.
            mGoButton.setEnabled(true);
        } catch (Exception e) {
            // Otherwise, we're boned.
            mGoButton.setEnabled(false);
        }
    }

    private class LocalLocationListener implements LocationListener {
        boolean done = false;

        private LocationListenerHandler mHandler = new LocationListenerHandler(
                Looper.myLooper());
        private HashMap<String, Boolean> enabledProviders;

        public LocalLocationListener(HashMap<String, Boolean> enabledProviders) {
            this.enabledProviders = enabledProviders;
        }

        public void resetHandler(LocationListenerHandler handler) {
            mHandler = handler;
        }

        public boolean isDone() {
            return done;
        }

        @Override
        public void onLocationChanged(Location location) {
            if (done)
                return;
            // WE HAVE A LOCATION! Stop all updates, we are DONE!
            done = true;
            mManager.removeUpdates(this);
            Message mess = Message.obtain();
            mess.what = ALL_OKAY;
            Bundle bun = new Bundle();
            bun.putDouble(LONGITUDE, location.getLongitude());
            bun.putDouble(LATITUDE, location.getLatitude());
            mess.setData(bun);
            mHandler.sendMessage(mess);
        }

        @Override
        public void onProviderDisabled(String provider) {
            if (done)
                return;
            // Yoink!
            enabledProviders.put(provider, false);
            if (!areAnyProvidersStillAlive()) {
                done = true;
                mManager.removeUpdates(this);
                mHandler.sendEmptyMessage(DIALOG_SEARCH_FAIL);
            }
        }

        @Override
        public void onProviderEnabled(String provider) {
            if (done)
                return;
            // Well, if it got enabled...
            enabledProviders.put(provider, true);
            mManager.requestLocationUpdates(provider, 0, 0, this);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            if (done)
                return;
            // If the provider is gone one way or another, knock it to
            // false in the map. If it comes back, mark it true. In the
            // former case, if none are available any more, stop and throw
            // the error.
            if (status != LocationProvider.OUT_OF_SERVICE) {
                enabledProviders.put(provider, true);
            } else {
                enabledProviders.put(provider, false);
                if (!areAnyProvidersStillAlive()) {
                    // We're screwed!
                    done = true;
                    mManager.removeUpdates(this);
                    mHandler.sendEmptyMessage(DIALOG_SEARCH_FAIL);
                }
            }
        }

        private boolean areAnyProvidersStillAlive() {
            if (enabledProviders.isEmpty())
                return false;

            boolean stillAlive = false;

            for (String s : enabledProviders.keySet()) {
                if (enabledProviders.get(s)) {
                    stillAlive = true;
                }
            }

            return stillAlive;
        }

    }

    private class LocationListenerHandler extends Handler {
        public LocationListenerHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message message) {
            // So, when we get hold of a message, we either throw a new error
            // or clear the old dialog and update our graticules.
            mLastDialog = ALL_OKAY;
            dismissDialog(DIALOG_SEARCHING);
            if (message.what != ALL_OKAY) {
                mLastDialog = DIALOG_SEARCH_FAIL;
                showDialog(DIALOG_SEARCH_FAIL);
            } else {
                // Found it! Update the graticules!
                Bundle bun = message.getData();
                updateGraticule(bun.getDouble(LATITUDE), bun
                        .getDouble(LONGITUDE));
            }
        }
    }

    private void startLocationSearch() {
        // Let's start us a LocationManager and get some updates (exactly one
        // update)! Specifically, whatever response first is what we use. In
        // theory, since all we're looking for is resolution down to one full
        // degree, it shouldn't matter if we read from towers or GPS, either
        // should put us in the ballpark. We'll prefer GPS later during the
        // map.
        //
        // However, my own testing has found the towers sometimes identifying
        // themselves as being somewhere a couple hundred miles and several
        // degrees away (no idea how), but I'm willing to chalk that up to
        // being a fluke from living in Kentucky at the time. I'll change this
        // to prefer GPS if both are on if this shows up in other parts of the
        // world.
        mManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

        // See what's open.
        List<String> providers = mManager.getProviders(true);

        // If we don't have any active providers, well, we're kinda screwed.
        if (providers.isEmpty()) {
            mLastDialog = DIALOG_SEARCH_FAIL;
            dismissDialog(DIALOG_SEARCHING);
            showDialog(DIALOG_SEARCH_FAIL);
            return;
        }

        // If we DO have an active provider, let's get to work!
        // Assume all providers are available to start.
        HashMap<String, Boolean> enabledProviders = new HashMap<String, Boolean>();
        for (String s : providers) {
            enabledProviders.put(s, true);
        }

        // And here's the listener...
        mListener = new LocalLocationListener(enabledProviders);

        // Now, register all providers and get us going!
        for (String s : providers) {
            mManager.requestLocationUpdates(s, 0, 0, mListener);
        }
    }

    private void updateGraticule(double latitude, double longitude) {
        // After all that, we DO, in fact, have a graticule!
        // We kind of have to do this to account for the negative zero
        // graticules. Oh, the fun of a floating-point coordinate system!
        String latStr = (latitude < 0 ? "-" : "") + Math.abs((int)latitude);
        mLatitude.setText(latStr);
        String lonStr = (longitude < 0 ? "-" : "") + Math.abs((int)longitude);
        mLongitude.setText(lonStr);

        resetGoButton();
    }

    private void updateGraticule(Graticule g) {
        // This comes from the Graticule picker map.
        String latStr = (g.isSouth() ? "-" : "") + g.getLatitude();
        mLatitude.setText(latStr);
        String lonStr = (g.isWest() ? "-" : "") + g.getLongitude();
        mLongitude.setText(lonStr);

        resetGoButton();
    }

    private class HashFetcherRunner implements Runnable {
        private Handler mCallbackHandler;
        private HashMaker mHashMaker;

        public HashFetcherRunner(Handler handler) {
            mCallbackHandler = handler;
        }

        public void resetHandler(Handler handler) {
            mCallbackHandler = handler;
        }

        public Handler getHandler() {
            return mCallbackHandler;
        }

        @Override
        public void run() {
            // We have the information. Let's go get some hash.
            //
            // Wait, let me rephrase that...

            // TODO: Analyze potential threading issues. Since this is the
            // only thing modifying mHashMaker, there shouldn't be anything
            // explicitly wrong, but I really need to check to make sure.

            // Dig up the date...
            DatePicker date = (DatePicker)findViewById(R.id.Date);

            // Build a graticule object, which is a bit wacky due to the
            // negative
            // zero graticules...
            Graticule grat = new Graticule(mLatitude.getText().toString(),
                    mLongitude.getText().toString());

            // And make the maker!
            try {
                mHashMaker = new HashMaker(grat, date.getYear(), date
                        .getMonth(), date.getDayOfMonth());
            } catch (FileNotFoundException e) {
                // Failure, this isn't posted yet.
                mCallbackHandler.sendEmptyMessage(DIALOG_STOCK_NOT_POSTED);
                return;
            } catch (IOException e) {
                // Failure, something else happened.
                if (mHashMaker == null || !mHashMaker.wasAborted())
                    mCallbackHandler.sendEmptyMessage(DIALOG_STOCK_ERROR);
                return;
            }

            if (mHashMaker.wasAborted()) {
                return;
            }

            try {
                dismissDialog(DIALOG_FIND_STOCK);
            } catch (Exception e) {
            }

            if (!mHashMaker.wasAborted()) {
                Message message = Message.obtain();
                message.what = ALL_OKAY;
                message.obj = mHashMaker.makeInfo();
                mCallbackHandler.sendMessage(message);
            }
        }
    }

    private class HashFetchThreadHandler extends Handler {
        public HashFetchThreadHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message message) {
            // Once we get the message to throw up a new dialog, act on it.
            try {
                dismissDialog(DIALOG_FIND_STOCK);
            } catch (Exception e) {
            }
            if (message.what != ALL_OKAY) {
                switch (message.what) {
                    case DIALOG_STOCK_NOT_POSTED:
                    case DIALOG_STOCK_ERROR:
                        showDialog(message.what);
                }
            } else {
                // If, however, we got the all clear, then we're clear! Get
                // the HashMaker object and act!
                Info info = (Info)message.obj;

                // Stash the Graticule away in preferences. We always want to
                // remember the last one we used, even if we blank it out next
                // time around.
                // TODO: Actually, what we REALLY want is a "home graticule"
                // option that WON'T get overwritten by an option.
                SharedPreferences prefs = getSharedPreferences(PREFS_BASE, 0);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(DEFAULT_LAT, info.getGraticule()
                        .getLatitudeString());
                editor.putString(DEFAULT_LON, info.getGraticule()
                        .getLongitudeString());
                editor.commit();

                Intent i = new Intent(GeohashDroid.this, MainMap.class);

                i.putExtra(INFO, info);
                startActivityForResult(i, 0);
            }

        }
    }

    private void attachListeners() {
        // Now, register the Refresh button's activity...
        Button searchButton = (Button)findViewById(R.id.RefreshButton);

        // Owing to the way location updates work, we don't need a new thread
        // for the festivities. We get these updates asynchronously anyway, so
        // this can be on the main thread.
        searchButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mLastDialog = DIALOG_SEARCHING;
                showDialog(DIALOG_SEARCHING);
                startLocationSearch();
            }
        });

        // Then the map button...
        Button mapButton = (Button)findViewById(R.id.MapButton);

        mapButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // Intent i = new Intent(GeohashDroid.this, GraticuleMap.class);
                Intent i = new Intent(
                        net.exclaimindustries.geohashdroid.GeohashDroid.PICK_GRATICULE);

                Graticule g = null;

                try {
                    g = new Graticule(mLatitude.getText().toString(),
                            mLongitude.getText().toString());
                } catch (Exception e) {
                    // Don't do anything, we can handle it ourselves.
                }

                i.putExtra(GraticuleMap.GRATICULE, g);

                startActivityForResult(i, 0);
            }

        });

        // And the Go button...
        mGoButton = (Button)findViewById(R.id.GoButton);

        mGoButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                // Hash fetching, on the other hand, has to be its own thread.
                // Them's the breaks.
                mLastDialog = DIALOG_FIND_STOCK;
                showDialog(DIALOG_FIND_STOCK);
                mHashFetcherRunner = new HashFetcherRunner(
                        new HashFetchThreadHandler(Looper.myLooper()));
                mHashFetcherThread = new Thread(mHashFetcherRunner);
                mHashFetcherThread.start();
            }
        });

        // Disable the Go button if a field is empty...
        resetGoButton();

        // And the "What's Geohashing?" button...
        Button whatButton = (Button)findViewById(R.id.WhatButton);

        whatButton.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                Intent i = new Intent();
                i.setAction(Intent.ACTION_VIEW);
                i.setData(Uri
                        .parse("http://wiki.xkcd.com/geohashing/How_it_works"));
                startActivity(i);
            }
        });

        // Give us something to do on text changes...
        TextWatcher tw = new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                resetGoButton();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                    int after) {
                // Blah!
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                    int count) {
                // BLAH!
            }

        };

        // Both behave the same way, so both use the same class.
        mLongitude.addTextChangedListener(tw);
        mLatitude.addTextChangedListener(tw);
    }

    private class RetainedThings {
        // Yes, this is just a bucket of stuff to retain.
        private HashFetcherRunner hashFetcherRunner;
        private Thread hashFetcherThread;
        private LocationManager locationManager;
        private LocalLocationListener locationListener;
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        // Retain what needs retainin'.
        RetainedThings toReturn = new RetainedThings();
        toReturn.hashFetcherRunner = mHashFetcherRunner;
        toReturn.hashFetcherThread = mHashFetcherThread;
        toReturn.locationManager = mManager;
        toReturn.locationListener = mListener;
        return toReturn;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_PICK_GRATICULE: {
                // A return trip from the Graticule picker means we update the
                // Graticule, assuming it wasn't canceled.
                if (resultCode == RESULT_OK) {
                    Graticule g = (Graticule)data
                            .getSerializableExtra(GraticuleMap.GRATICULE);
                    updateGraticule(g);

                    // If we have an update like this, update the preferences,
                    // too.
                    SharedPreferences prefs = getSharedPreferences(PREFS_BASE,
                            0);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(DEFAULT_LAT, g.getLatitudeString());
                    editor.putString(DEFAULT_LON, g.getLongitudeString());
                    editor.commit();
                }
            }

        }
    }

}