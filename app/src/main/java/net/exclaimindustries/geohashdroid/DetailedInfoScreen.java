/**
 * DetailedInfoScreen.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.text.DateFormat;

import net.exclaimindustries.geohashdroid.util.ClosenessActor;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.tools.AndroidUtil;
import net.exclaimindustries.tools.LocationAwareActivity;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * The <code>DetailedInfoScreen</code> displays, in detail, just where the user
 * is right now. With big ol' text and lots of decimal places. This is ideal
 * for, say, taking pictures of the phone when at the point, at least until the
 * picture-taking-and-tagging function is coded up.
 * 
 * @author Nicholas Killewald
 */
public class DetailedInfoScreen extends LocationAwareActivity {

    // Two minutes (in milliseconds). If the last known check is older than
    // that, we ignore it.
    private static final int LOCATION_VALID_TIME = 120000;

    private static final String INFO = "info";

    private static final String LONGITUDE = "longitude";

    private static final String LATITUDE = "latitude";
    
    private static final String SHOW_RADAR_ACTION = "com.google.android.radar.SHOW_RADAR";

    private Info mInfo;
    
    private ClosenessActor mCloseness;

//    private static final String DEBUG_TAG = "DetailedInfoScreen";

    private static final int MENU_SETTINGS = 3;
    private static final int MENU_SEND_TO_MAPS = 6;
    private static final int MENU_SEND_TO_RADAR = 7;

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Lay 'er out!
        setContentView(R.layout.detail);
        
        // Set up the actor!
        mCloseness = new ClosenessActor(this);

        // Get us some info!
        if (icicle != null && icicle.containsKey(INFO)) {
            mInfo = (Info)icicle.getParcelable(INFO);
        } else {
            mInfo = (Info)getIntent().getParcelableExtra(GeohashDroid.INFO);
        }

        // Lay out the initial info. The rest remains on standby for now.

        // Today's date, in long form.
        TextView tv = (TextView)findViewById(R.id.Date);
        tv.setText(DateFormat.getDateInstance(DateFormat.LONG).format(
                mInfo.getCalendar().getTime()));

        // The actual updates are requested at onResume.

        // And make sure we quit when told.
        Button button = (Button)findViewById(R.id.Okay);
        button.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                finish();
            }

        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuItem item;

        // We can send this to a map!
        item = menu.add(Menu.NONE, MENU_SEND_TO_MAPS, 0,
                R.string.menu_item_send_to_maps);
        item.setIcon(android.R.drawable.ic_menu_myplaces);
        
        // (if we have a map)
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse("geo:0,0?q=loc:0,0"));
        boolean isAvailable = AndroidUtil.isIntentAvailable(this, i);
        item.setEnabled(isAvailable);

        // We can send this to radar!
        item = menu.add(Menu.NONE, MENU_SEND_TO_RADAR, 1, 
                R.string.menu_item_radar);
        item.setIcon(android.R.drawable.ic_menu_compass);
        
        // (if we have radar)
        isAvailable = AndroidUtil.isIntentAvailable(this,
                SHOW_RADAR_ACTION);
        item.setEnabled(isAvailable);
        
        // And we can go to settings!
        item = menu.add(Menu.NONE, MENU_SETTINGS, 2,
                R.string.menu_item_settings);
        item.setIcon(android.R.drawable.ic_menu_preferences);

        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        super.onMenuItemSelected(featureId, item);

        switch (item.getItemId()) {
            case MENU_SEND_TO_MAPS: {
                // Send out the final destination's latitude and longitude to
                // the Maps app (or anything else listening for this intent).
                // Should be fairly simple.
                Intent i = new Intent();
                i.setAction(Intent.ACTION_VIEW);
                
                // Assemble the location.  This is a simple latitude,longitude
                // setup.
                String location = mInfo.getLatitude() + "," + mInfo.getLongitude();
                
                // Then, toss the location out the door and hope whatever map
                // we're using is paying attention.
                i.setData(Uri.parse("geo:0,0?q=loc:"
                        + location
                        + "("
                        + this.getString(
                                R.string.send_to_maps_point_name,
                                DateFormat.getDateInstance(DateFormat.LONG).format(
                                        mInfo.getCalendar().getTime())) + ")&z=15"));
                startActivity(i);
                
                return true;
            }
            case MENU_SEND_TO_RADAR: {
            	Intent i = new Intent(SHOW_RADAR_ACTION);              
                i.putExtra(LATITUDE, (float)mInfo.getLatitude());
                i.putExtra(LONGITUDE, (float)mInfo.getLongitude());          
                startActivity(i);
            }
        }
        return false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // All we need to do is store the info object. Simple!
        outState.putParcelable(INFO, mInfo);
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();

        updateDest();

        // Populate the location with the last known data, if it's no older
        // than two minutes, GPS taking precedence.
        if(isLastLocationNewEnough(LOCATION_VALID_TIME)) {
            updateInfo(getLastLocation());
        } else {
            updateInfo(null);
        }
    }
    
    private void updateDest() {
        // This is called during onResume, either on first run or when we get
        // back from the preferences screen, possibly with a new coordinate
        // format.
        TextView tv;
        
        // The final destination.
        tv = (TextView)findViewById(R.id.DestLat);
        tv.setText(UnitConverter.makeLatitudeCoordinateString(this, mInfo.getFinalLocation().getLatitude(), false, UnitConverter.OUTPUT_DETAILED));

        tv = (TextView)findViewById(R.id.DestLon);
        tv.setText(UnitConverter.makeLongitudeCoordinateString(this, mInfo.getFinalLocation().getLongitude(), false, UnitConverter.OUTPUT_DETAILED));
    }

    private void updateInfo(Location loc) {
        // This updates the current location and distance info. Unless loc is
        // null, in which case we use the standby label.
        if (loc == null) {
            String s = getResources().getString(R.string.standby_title);

            TextView tv = (TextView)findViewById(R.id.YouLat);
            tv.setText(s);
            tv = (TextView)findViewById(R.id.YouLon);
            tv.setText(s);
            tv = (TextView)findViewById(R.id.Distance);
            tv.setText(s);
            tv.setTextColor(getResources().getColor(R.color.details_text));
            tv = (TextView)findViewById(R.id.Accuracy);
            tv.setText("");
        } else {
            TextView tv = (TextView)findViewById(R.id.YouLat);
            tv.setText(UnitConverter.makeLatitudeCoordinateString(this, loc.getLatitude(), false, UnitConverter.OUTPUT_DETAILED));
            tv = (TextView)findViewById(R.id.YouLon);
            tv.setText(UnitConverter.makeLongitudeCoordinateString(this, loc.getLongitude(), false, UnitConverter.OUTPUT_DETAILED));
            tv = (TextView)findViewById(R.id.Distance);
            tv.setText(UnitConverter.makeDistanceString(this, GHDConstants.DIST_FORMAT,
                    mInfo.getDistanceInMeters(loc)));
            tv.setTextColor(getDistanceColor(loc));
            tv = (TextView)findViewById(R.id.Accuracy);
            tv.setText(getResources().getString(R.string.details_accuracy,
                    UnitConverter.makeDistanceString(this,
                            GHDConstants.ACCURACY_FORMAT, loc.getAccuracy())));
        }

    }

    @Override
    protected void locationUpdated() {
        mCloseness.actOnLocation(mInfo, getLastLocation());
        updateInfo(getLastLocation());
    }
    
    /**
     * Gets the appropriate color for the distance text.  That is, normal if the
     * user is out of range, something else if the user is within the accuracy
     * of the GPS signal AND said accuracy isn't low to begin with.
     *
     * @param loc
     *            where the user is (can be null)
     * @return
     *            the appropriate color (NOT a resource reference)
     */
    private int getDistanceColor(Location loc) {
        if(loc == null) {
            return getResources().getColor(R.color.details_text);
        } else {
            float accuracy = loc.getAccuracy();
            
            // For testing purposes, the joke that goes here can be found in
            // MainMapInfoBox's version of this method.
            if(accuracy == 0) accuracy = 5;

            if(loc != null
                    && accuracy < GHDConstants.LOW_ACCURACY_THRESHOLD
                    && mInfo.getDistanceInMeters(loc) <= accuracy) {
                    return getResources().getColor(R.color.details_in_range);
                } else {
                    return getResources().getColor(R.color.details_text);
                }
        }
    }

}
