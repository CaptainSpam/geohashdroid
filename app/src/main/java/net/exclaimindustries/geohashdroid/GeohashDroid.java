/**
 * GeohashDroid.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.util.Calendar;

import net.exclaimindustries.geohashdroid.services.AlarmService;
import net.exclaimindustries.geohashdroid.util.ClosenessActor;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.Graticule;
import net.exclaimindustries.geohashdroid.util.HashBuilder;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.tools.DateButton;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.CompoundButton.OnCheckedChangeListener;

/**
 * The <code>GeohashDroid</code> class is where the entry point resides. It's
 * where the main window sits around and gathers data.
 * 
 * @author Nicholas Killewald
 */
public class GeohashDroid extends Activity {
    public static final String LONGITUDE = "net.exclaimindustries.geohashdroid.longitude";
    public static final String LATITUDE = "net.exclaimindustries.geohashdroid.latitude";
    public static final String INFO = "net.exclaimindustries.geohashdroid.info";
    public static final String CALENDAR = "net.exclaimindustries.geohashdroid.calendar";
    public static final String GRATICULE = "net.exclaimindustries.geohashdroid.graticule";
    public static final String LOCATION = "net.exclaimindustries.geohashdroid.location";
    public static final String RETROHASH = "net.exclaimindustries.geohashdroid.retrohash";

    private static final String DEBUG_TAG = "GeohashDroid";

    private static final int DIALOG_SEARCH_FAIL = 0;
    private static final int DIALOG_STOCK_NOT_POSTED = 1;
    private static final int DIALOG_STOCK_ERROR = 2;
    private static final int DIALOG_ABOUT = 3;

    private static final int DIALOG_LAST_NUMBER = DIALOG_STOCK_ERROR;

    private static final int ALL_OKAY = -1;

    private static final int MENU_GLOBALHASH = 0;
    private static final int MENU_SETTINGS = 1;
    private static final int MENU_ABOUT = 2;

    private static final int REQUEST_PICK_GRATICULE = 0;
    private static final int REQUEST_STOCK = 1;
    private static final int REQUEST_LOCATION = 2;
    private static final int REQUEST_MORE_STOCK = 3;

    private EditText mLatitude;
    private EditText mLongitude;
    private Button mGoButton;
    private CheckBox mAutoBox;
    private CheckBox mTodayBox;
    
    private boolean mGlobalhash;

    private static int mLastDialog = ALL_OKAY;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        HashBuilder.initialize(this);

        setContentView(R.layout.main);

        // First things first, set up the preferences AND act on any prefs we
        // might be interested in (i.e. stock prefetch).
        initPrefs();

        // Set ourselves back up if we came in from elsewhere...
        mLatitude = (EditText)findViewById(R.id.Latitude);
        mLongitude = (EditText)findViewById(R.id.Longitude);

        /*
         * Order of priority:
         * 
         * 1. If we're coming back with Intent data (fell back to here from the
         * map?), use that.
         * 2. If we're coming back from a saved instance state (usually back
         * from an onPause), keep that.
         * 3. If we have DefaultLatitude and DefaultLongitude settings AND
         * they're both Strings, use that.
         * 4. Otherwise, go to a blank.
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
        SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);
        boolean rememberOn = true;
        try {
            rememberOn = prefs.getBoolean(GHDConstants.PREF_REMEMBER_GRATICULE, true);
        } catch (Exception e) {
            rememberOn = true;
        }

        if (savedInstanceState == null && extras == null && rememberOn) {
            // Store the current values in case something goes wrong with
            // pulling the Strings later.
            String backupLat = mLatitude.getText().toString();
            String backupLon = mLongitude.getText().toString();

            if (prefs.contains(GHDConstants.PREF_DEFAULT_LAT) && prefs.contains(GHDConstants.PREF_DEFAULT_LON)) {
                try {
                    mLatitude.setText(prefs.getString(GHDConstants.PREF_DEFAULT_LAT, ""));
                    mLongitude.setText(prefs.getString(GHDConstants.PREF_DEFAULT_LON, ""));
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
        
        // Tick the checkboxes if need be.
        if(prefs.getBoolean(GHDConstants.PREF_CLOSEST, false)) {
            mAutoBox.setChecked(true);
        } else {
            mAutoBox.setChecked(false);
        }
        
        if(prefs.getBoolean(GHDConstants.PREF_TODAY, false)) {
            mTodayBox.setChecked(true);
            setTodayMode(true);
        } else {
            mTodayBox.setChecked(false);
            setTodayMode(false);
        }        
        
        // Set globalhash mode if need be.
        setGlobalhashMode(prefs.getBoolean(GHDConstants.PREF_GLOBALHASH_MODE, false));

        // Rebuild the dialogs if any were around when we left.
        if (mLastDialog != ALL_OKAY) {
            // If we have one of the error dialogs up, show them.
            if (mLastDialog == DIALOG_SEARCH_FAIL
                    || mLastDialog == DIALOG_STOCK_NOT_POSTED
                    || mLastDialog == DIALOG_STOCK_ERROR)
                showDialog(mLastDialog);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuItem item;

        // The globalhash option will be set up whenever the menu is called.
        // We'll just set it in "switch to globalhash" mode for now.
        item = menu.add(Menu.NONE, MENU_GLOBALHASH, 0,
                R.string.menu_item_globalhash);
        item.setIcon(android.R.drawable.ic_menu_compass);
        
        item = menu.add(Menu.NONE, MENU_SETTINGS, 1,
                R.string.menu_item_settings);
        item.setIcon(android.R.drawable.ic_menu_preferences);

        item = menu.add(Menu.NONE, MENU_ABOUT, 2, R.string.menu_item_about);
        item.setIcon(android.R.drawable.ic_menu_info_details);

        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        
        MenuItem item = menu.findItem(MENU_GLOBALHASH);
        
        // Reset the globalhash option as need be.
        if(mGlobalhash) {
            item.setTitle(R.string.menu_item_geohash);
            item.setIcon(android.R.drawable.ic_menu_mapmode);
        } else {
            item.setTitle(R.string.menu_item_globalhash);
            item.setIcon(android.R.drawable.ic_menu_compass);
        }
        
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
            case MENU_GLOBALHASH:
                // Toggle globalhash mode.
                if(mGlobalhash)
                    mGlobalhash = false;
                else
                    mGlobalhash = true;

                setGlobalhashMode(mGlobalhash);
                
                // Finally, set the preference.
                SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);
                SharedPreferences.Editor editor = prefs.edit();
                
                editor.putBoolean(GHDConstants.PREF_GLOBALHASH_MODE, mGlobalhash);
                editor.commit();

                return true;
        }

        return false;
    }

    private boolean initPrefs() {
        // Initializes preferences. Returns true if anything changed, false if
        // everything went well.
        boolean toReturn = false;

        SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);
        SharedPreferences.Editor editor = prefs.edit();

        // AutoZoom defaults to on.
        if (!prefs.contains(GHDConstants.PREF_AUTOZOOM)) {
            editor.putBoolean(GHDConstants.PREF_AUTOZOOM, true);
            toReturn = true;
        }

        // Graticule remembering defaults to on.
        if (!prefs.contains(GHDConstants.PREF_REMEMBER_GRATICULE)) {
            editor.putBoolean(GHDConstants.PREF_REMEMBER_GRATICULE, true);
            toReturn = true;
        }
        
        // The stock cache defaults to 15 entries.
        if(!prefs.contains(GHDConstants.PREF_STOCK_CACHE_SIZE)) {
            editor.putString(GHDConstants.PREF_STOCK_CACHE_SIZE, "15");
            toReturn = true;
        }
        
        // Nearby points defaults to off (it makes eight new overlays with
        // transparencies, it can be a bit hefty on the processor)
        if(!prefs.contains(GHDConstants.PREF_NEARBY_POINTS)) {
            editor.putBoolean(GHDConstants.PREF_NEARBY_POINTS, false);
            toReturn = true;
        }
        
        // The Closest checkbox defaults to off.
        if(!prefs.contains(GHDConstants.PREF_CLOSEST)) {
            editor.putBoolean(GHDConstants.PREF_CLOSEST, false);
            toReturn = true;
        }

        // The Today checkbox defaults to off.
        if(!prefs.contains(GHDConstants.PREF_TODAY)) {
            editor.putBoolean(GHDConstants.PREF_TODAY, false);
            toReturn = true;
        }
        
        // Stock prefetch defaults to off.  However, if it's on, we should start
        // it up right now.
        if(!prefs.contains(GHDConstants.PREF_STOCK_SERVICE)) {
            editor.putBoolean(GHDConstants.PREF_STOCK_SERVICE, false);
            toReturn = true;
        } else if(prefs.getBoolean(GHDConstants.PREF_STOCK_SERVICE, false)) {
            Intent i = new Intent(this, AlarmService.class).setAction(AlarmService.STOCK_ALARM_ON);
            startService(i);
        }

        editor.commit();

        return toReturn;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_SEARCH_FAIL: {
                // Box that says the graticule auto-detect failed.
                AlertDialog.Builder build = new AlertDialog.Builder(this);
                build.setMessage(R.string.error_search_failed);
                build.setTitle(R.string.error_title);
                build.setIcon(android.R.drawable.ic_dialog_alert);
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
            case DIALOG_STOCK_NOT_POSTED: {
                // Box that says we're looking for stock data.
                AlertDialog.Builder build = new AlertDialog.Builder(this);
                build.setMessage(R.string.error_not_yet_posted);
                build.setTitle(R.string.error_title);
                build.setIcon(android.R.drawable.ic_dialog_alert);
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
                build.setIcon(android.R.drawable.ic_dialog_alert);
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
        
        // Check over the checkbox.  If it's ticked on, turn on the go button.
        if ((mAutoBox != null && mAutoBox.isChecked()) || mGlobalhash) {
            mGoButton.setEnabled(true);
            return;
        }

        // Otherwise, check to see if we have valid inputs.
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
    
    private void resetGraticuleBoxes() {
        // Resets the graticule boxes and the map button.
        Button mapButton = (Button)findViewById(R.id.MapButton);
        
        // They're all disabled if the autobox is checked OR we're in globalhash
        // mode.
        if(!mAutoBox.isChecked() && !mGlobalhash) {
            mLatitude.setEnabled(true);
            mLongitude.setEnabled(true);
            mapButton.setEnabled(true);
        } else {
            mLatitude.setEnabled(false);
            mLongitude.setEnabled(false);
            mapButton.setEnabled(false);                
        }
    }

    private void updateGraticule(Graticule g) {
        // This comes from the Graticule picker map.
        String latStr = (g.isSouth() ? "-" : "") + g.getLatitude();
        mLatitude.setText(latStr);
        String lonStr = (g.isWest() ? "-" : "") + g.getLongitude();
        mLongitude.setText(lonStr);

        resetGoButton();
    }

    private void attachListeners() {
        // First the map button...
        Button mapButton = (Button)findViewById(R.id.MapButton);

        mapButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent i = new Intent(
                        net.exclaimindustries.geohashdroid.util.GHDConstants.PICK_GRATICULE);

                Graticule g = null;

                try {
                    g = new Graticule(mLatitude.getText().toString(),
                            mLongitude.getText().toString());
                } catch (Exception e) {
                    // Don't do anything, we can handle it ourselves.
                }

                i.putExtra(GRATICULE, g);

                startActivityForResult(i, REQUEST_PICK_GRATICULE);
            }

        });
        
        // The checkbox needs to be registered so it can disable the graticule
        // input and map picker as need be.
        mAutoBox = (CheckBox)findViewById(R.id.AutoBox);
        
        mAutoBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                // Disable or enable the interesting stuff as need be.
                resetGraticuleBoxes();
                resetGoButton();
                
                SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);
                SharedPreferences.Editor editor = prefs.edit();
                
                editor.putBoolean(GHDConstants.PREF_CLOSEST, isChecked);
                editor.commit();
            }
            
        });
        
        // The other checkbox also needs to do stuff.
        mTodayBox = (CheckBox)findViewById(R.id.TodayBox);
        
        mTodayBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                // First, set the date button.
                setTodayMode(isChecked);
                
                // Update prefs, of course.
                SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);
                SharedPreferences.Editor editor = prefs.edit();
                
                editor.putBoolean(GHDConstants.PREF_TODAY, isChecked);
                editor.commit();
            }

        });
        
        // Initialize the button, too.
        
        

        // And the Go button...
        mGoButton = (Button)findViewById(R.id.GoButton);

        mGoButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mGlobalhash) {
                    // Hey, we're in globalhash mode!  We can get started right
                    // away!
                    Calendar cal = getActiveCalendar();
                    Intent i = new Intent(GeohashDroid.this, StockGrabber.class);
                    i.putExtra(GRATICULE, (Graticule)null);
                    i.putExtra(CALENDAR, cal);
                    startActivityForResult(i, REQUEST_STOCK);
                } else if(!mAutoBox.isChecked()) {

                    // Without the auto-checker, go straight to whatever was
                    // input into the graticule boxen.
                    Calendar cal = getActiveCalendar();

                    Graticule grat = new Graticule(mLatitude.getText().toString(),
                            mLongitude.getText().toString());
                    
                    Intent i = new Intent(GeohashDroid.this, StockGrabber.class);
                    i.putExtra(GRATICULE, grat);
                    i.putExtra(CALENDAR, cal);
                    startActivityForResult(i, REQUEST_STOCK);
                } else {
                    // Otherwise, we need to figure out the location and go from
                    // there.
                    Intent i = new Intent(GeohashDroid.this, LocationGrabber.class);
                    startActivityForResult(i, REQUEST_LOCATION);
                }
            }
        });

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_PICK_GRATICULE: {
                // A return trip from the Graticule picker means we update the
                // Graticule, assuming it wasn't canceled.
                if (resultCode == RESULT_OK) {
                    Graticule g = (Graticule)data
                            .getParcelableExtra(GRATICULE);
                    updateGraticule(g);

                    // If we have an update like this, update the preferences,
                    // too.
                    SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE,
                            0);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(GHDConstants.PREF_DEFAULT_LAT, g.getLatitudeString(true));
                    editor.putString(GHDConstants.PREF_DEFAULT_LON, g.getLongitudeString(true));
                    editor.commit();
                }
                break;
            }
            case REQUEST_STOCK: {
                // Welcome back from the stock grabber!
                switch(resultCode) {
                    case RESULT_OK: {
                        Info i = (Info)data.getParcelableExtra(INFO);
                        dispatchMapIntent(i);
                        break;
                    }
                    case StockGrabber.RESULT_NOT_POSTED_YET:
                        showDialog(DIALOG_STOCK_NOT_POSTED);
                        break;
                    case StockGrabber.RESULT_SERVER_FAILURE:
                        showDialog(DIALOG_STOCK_ERROR);
                        break;
                    case RESULT_CANCELED:
                        // This doesn't really do anything.
                        break;
                }
                break;
            }
            case REQUEST_LOCATION: {
            	// Welcome back from the location grabber!
            	switch(resultCode) {
            		case RESULT_OK:
            		{
            		    // Now that we have a location, we have the "base"
            		    // graticule.  Start there, get the eight around it,
            		    // and figure out where everything is around that.
            			double lat = data.getDoubleExtra(LATITUDE, 0.0);
            			double lon = data.getDoubleExtra(LONGITUDE, 0.0);
            			calculateClosestInfo(lat, lon);
            			break;
            		}
            		case LocationGrabber.RESULT_FAIL:
            			showDialog(DIALOG_SEARCH_FAIL);
            			break;
            		case RESULT_CANCELED:
            			break;
            	}
            	break;
            }
            case REQUEST_MORE_STOCK: {
                switch(resultCode) {
                    case RESULT_OK:
                    {
                        // Got a stock THIS time.  Let's restart the closest
                        // calculator.
                        double lat = data.getDoubleExtra(LATITUDE, 0.0);
                        double lon = data.getDoubleExtra(LONGITUDE, 0.0);
                        calculateClosestInfo(lat, lon);
                        break;
                    }
                    case StockGrabber.RESULT_NOT_POSTED_YET:
                        showDialog(DIALOG_STOCK_NOT_POSTED);
                        break;
                    case StockGrabber.RESULT_SERVER_FAILURE:
                        showDialog(DIALOG_STOCK_ERROR);
                        break;
                    case RESULT_CANCELED:
                        // This doesn't really do anything.
                        break;
                }
                break;
            }
        }
    }
    
    private void dispatchMapIntent(Info info) {
        // Stash the Graticule away in preferences. We always want to remember
        // the last one we used, even if we blank it out next time around.  But,
        // we don't write anything if we're globalhashing.
        // TODO: Actually, what we REALLY want is a "home graticule" option that
        // WON'T get overwritten by an option.
        if(!info.isGlobalHash()) {
            SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(GHDConstants.PREF_DEFAULT_LAT, info.getGraticule()
                    .getLatitudeString(true));
            editor.putString(GHDConstants.PREF_DEFAULT_LON, info.getGraticule()
                    .getLongitudeString(true));
            editor.commit();
        }
        
        // Then, before we kick this off, reset the ClosenessActor.
        new ClosenessActor(this).reset();

        Intent i = new Intent(GeohashDroid.this, MainMap.class);

        i.putExtra(INFO, info);
        startActivity(i);
    }

    private void calculateClosestInfo(double latitude, double longitude) {
        // The doubles represent not only where we are, but also the "base"
        // graticule.  So, we go get that Info first, then we go for the rest.
        // All of these go to StockGrabber with REQUEST_MORE_STOCK, which will
        // in turn call this again if anything's needed (or fail if need be).
        Graticule base = new Graticule(latitude, longitude);
        
        Location loc = new Location("");
        loc.setLatitude(latitude);
        loc.setLongitude(longitude);
        
        Calendar cal = getActiveCalendar();
       
        Info inf = HashBuilder.getStoredInfo(this, cal, base);
        
        if(inf == null) {
            // Oops.  We don't have enough data.  Off to the stock grabber!
            Intent i = new Intent(GeohashDroid.this, StockGrabber.class);
            i.putExtra(GRATICULE, base);
            i.putExtra(CALENDAR, cal);
            i.putExtra(LATITUDE, latitude);
            i.putExtra(LONGITUDE, longitude);
            startActivityForResult(i, REQUEST_MORE_STOCK);
            
            // Stop and wait until we get back.  We'll start over at that point.
            // This may not be the most efficient way to do this, but success
            // guarantees that the data is cached.
            return;
        }
        
        // This is the closest Info bundle we've found yet.  Whatever this ends
        // up as becomes what goes to the map.
        Info closest = inf;
        float bestDistance = inf.getDistanceInMeters(loc);
        
        // Now, get a bunch of offset graticules, get info from each, and figure
        // out which one is the closest.
        for(int i = -1; i <= 1; i++) {
            for(int j = -1; j <= 1; j++) {
                if(i == 0 && j == 0)
                    continue;
                
                Graticule grat = Graticule.createOffsetFrom(base, j, i);
                inf = HashBuilder.getStoredInfo(this, cal, grat);
                
                if(inf == null) {
                    // Oops.  We don't have this data.  We must be on the 30W
                    // or 180E/W line.  Let's try again.  If this succeeds, then
                    // it's guaranteed we have BOTH available.
                    Log.d(DEBUG_TAG, "HashBuilder returned null info when checking nearby graticules, trying to get new data...");
                    
                    Intent in = new Intent(GeohashDroid.this, StockGrabber.class);
                    in.putExtra(GRATICULE, grat);
                    in.putExtra(CALENDAR, cal);
                    in.putExtra(LATITUDE, latitude);
                    in.putExtra(LONGITUDE, longitude);
                    startActivityForResult(in, REQUEST_MORE_STOCK);
                    return;
                }
                
                // Got it!  Let's get calculating!
                float newDist = inf.getDistanceInMeters(loc);
                if(newDist < bestDistance) {
                    closest = inf;
                    bestDistance = newDist;
                }
            }
        }
        
        // There!  Now, we have whatever the closest Info bundle was!
        updateGraticule(closest.getGraticule());
        dispatchMapIntent(closest);
    }
    
    private Calendar getActiveCalendar() {
        // This grabs the active Calendar object.  Depending on prefs, this
        // could be either today or what the DateButton says.
        SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);
        DateButton date = (DateButton)findViewById(R.id.DateButton);
        
        if(prefs.getBoolean(GHDConstants.PREF_TODAY, false)) {
            // If we're using today, set the button to today, too, in case the
            // user waited past midnight on the main screen.
            date.setToday();
            return Calendar.getInstance();
        } else {
            return date.getDate();            
        }
    }
    
    private void setGlobalhashMode(boolean flag) {
        // Sets things up for globalhash mode.  That is to say, turns off stuff
        // that shouldn't be on and sets up the boolean.
        LinearLayout gratlayout = (LinearLayout)findViewById(R.id.GraticuleRow);
        TextView globallabel = (TextView)findViewById(R.id.GlobalhashLabel);
        
        if(flag) {
            // Turn stuff off!
            mGlobalhash = true;
            
            mAutoBox.setEnabled(false);
            gratlayout.setVisibility(View.GONE);
            globallabel.setVisibility(View.VISIBLE);
        } else {
            // Turn stuff back on!
            mGlobalhash = false;
            
            mAutoBox.setEnabled(true);
            gratlayout.setVisibility(View.VISIBLE);
            globallabel.setVisibility(View.GONE); 
        }
        
        // Reset the graticule boxes and the go button...
        resetGraticuleBoxes();        
        resetGoButton();
        
        // And we're done!
    }
    
    private void setTodayMode(boolean flag) {
        // Sets things up for Today mode.  Which means to enable/disable the
        // date button and reset what date it thinks it is.
        DateButton date = (DateButton)findViewById(R.id.DateButton);
        
        if(flag) {
            // Off!
            date.setEnabled(false);
            
            // Also, make sure the date is set to today.  We'll actually ignore
            // that when the time comes in the event the user stays on the
            // main screen past midnight.
            date.setToday();
        } else {
            // On!
            date.setEnabled(true);
        }
    }

}
