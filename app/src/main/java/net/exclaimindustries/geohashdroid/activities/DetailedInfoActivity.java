/*
 * DetailedInfoActivity.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.fragments.DetailedInfoFragment;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.tools.AndroidUtil;

import java.text.DateFormat;

/**
 * In the event this is a phone, <code>DetailedInfoActivity</code> holds the
 * {@link DetailedInfoFragment} that gets displayed.
 */
public class DetailedInfoActivity extends Activity
        implements DetailedInfoFragment.CloseListener {
    /**
     * The key for the Intent extra containing the Info object.  This should
     * very seriously NOT be null.  If it is, you did something very wrong.
     */
    public static final String INFO = "info";

    private static final String SHOW_RADAR_ACTION = "com.google.android.radar.SHOW_RADAR";

    private Info mInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.detail_activity);

        // Grab the fragment.  We know it's there, it's right there in the
        // layout.
        FragmentManager manager = getFragmentManager();
        DetailedInfoFragment frag = (DetailedInfoFragment) manager.findFragmentById(R.id.detail_fragment);

        // We'd BETTER have an Intent.
        Intent intent = getIntent();

        // And that intent BETTER have an Info.
        mInfo = intent.getParcelableExtra(INFO);

        // Since the fragment's part of the layout, we can't set an argument
        // anymore.  So, just update the Info.
        frag.setCloseListener(this);
        frag.setInfo(mInfo);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.detail_activity, menu);

        // Now, some menu items may not be available if we can't get to them.
        // Like, for instance, Send To Maps.  If Google Maps (or anything that
        // can receive the Intent) isn't there, we can't do that.
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse("geo:0,0?q=loc:0,0"));
        if(!AndroidUtil.isIntentAvailable(this, i))
            menu.removeItem(R.id.action_send_to_maps);

        // Or the Radar intent.
        if(!AndroidUtil.isIntentAvailable(this, SHOW_RADAR_ACTION))
            menu.removeItem(R.id.action_send_to_radar);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_preferences: {
                // We've got preferences, so we've got an Activity.
                Intent i = new Intent(this, PreferencesActivity.class);
                startActivity(i);
                return true;
            }
            case R.id.action_send_to_maps: {
                // To the map!
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
            case R.id.action_send_to_radar: {
                Intent i = new Intent(SHOW_RADAR_ACTION);
                i.putExtra("latitude", (float) mInfo.getLatitude());
                i.putExtra("longitude", (float) mInfo.getLongitude());
                startActivity(i);
            }
        }

        return false;
    }

    @Override
    public void detailedInfoClosing() {
        // Easy enough, just finish the Activity.
        finish();
    }
}
