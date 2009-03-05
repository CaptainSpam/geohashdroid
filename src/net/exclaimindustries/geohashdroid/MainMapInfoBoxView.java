/**
 * MainMapInfoBoxView.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.text.DecimalFormat;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

/**
 * The MainMapInfoBoxView displays the InfoBox on the map.  It keeps all the data
 * up-to-date and makes sure to turn itself off if need be.
 * 
 * @author Nicholas Killewald
 *
 */
public class MainMapInfoBoxView extends TextView {
	private Info lastInfo = null;
	private Location lastLoc = null;
	
	// Handy!
	private static final double METERS_PER_FEET = 3.2808399;
	private static final int FEET_PER_MILE = 5280;

	public MainMapInfoBoxView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		// TODO Auto-generated constructor stub
	}

	public MainMapInfoBoxView(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
	}

	public MainMapInfoBoxView(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}

	/* (non-Javadoc)
	 * @see android.view.View#setVisibility(int)
	 */
	@Override
	public void setVisibility(int visibility) {
		// TODO Auto-generated method stub
		super.setVisibility(visibility);
		
		// If we're being set visible again, immediately update the box.
		// This probably isn't strictly necessary, but it's defensive.
		if(visibility == View.VISIBLE && lastInfo != null) {
			update(lastInfo, lastLoc);
		}
	}

	/**
	 * Updates the InfoBox with the given bundle of Info, plus the Location
	 * from wherever the user currently is.
	 * 
	 * @param info Info bundle that contains, well, info
	 * @param loc Location where the user currently is.  If null, this assumes the user's location is unknown.
	 */
	public void update(Info info, Location loc) {
		// If this isn't visible right now, skip this step.
		lastInfo = info;
		lastLoc = loc;
		
		if(getVisibility() != View.VISIBLE) return;
		
		// Get the final destination.  We'll translate it to N/S and E/W
		// instead of positive/negative.  We'll also narrow it down to three
		// decimal points.
		DecimalFormat latlonformat = new DecimalFormat("###.000");
		
		// The final destination coordinates
		String finalLine = getContext().getString(R.string.infobox_final) + " "
			+ latlonformat.format(Math.abs(info.getLatitude())) + (info.getLatitude() >= 0 ? 'N' : 'S') + " "
			+ latlonformat.format(Math.abs(info.getLongitude())) + (info.getLongitude() >= 0 ? 'E' : 'W');
		
		// Your current location coordinates
		String youLine;
		if(loc != null) {
			youLine = getContext().getString(R.string.infobox_you) + " "
				+ (latlonformat.format(Math.abs(loc.getLatitude()))) + (loc.getLatitude() >= 0 ? 'N' : 'S') + " "
				+ (latlonformat.format(Math.abs(loc.getLongitude()))) + (loc.getLongitude() >= 0 ? 'E' : 'W');
		} else {
			youLine = getContext().getString(R.string.infobox_you) + " " + getContext().getString(R.string.standby_title);
		}
		
		// The distance to the final destination (as the crow flies)
		String distanceLine = getContext().getString(R.string.infobox_dist) + " "
			+ (loc != null ? (makeDistanceString(info.getDistanceInMeters(loc))) : getContext().getString(R.string.standby_title));
		
		setText(finalLine + "\n" + youLine + "\n" + distanceLine);
	}
	
	private String makeDistanceString(float meters) {
		// Whatever the case, the number format is the same.
		DecimalFormat distformat = new DecimalFormat("###.###");
		
		// Determine if we're using metric or imperial measurements.  Or, in
		// theory, other sorts later on.
		SharedPreferences prefs = getContext().getSharedPreferences(GeohashDroid.PREFS_BASE, 0);
		String units = prefs.getString(getResources().getString(R.string.pref_units_key), "Metric");
		
		if(units.equals("Metric")) {
			// Meters are easy, if not only for the fact that, by default, the
			// Location object returns distances in meters.  And the fact that
			// it's in powers of ten.
			if(meters >= 1000) {
				return distformat.format(meters / 1000) + "km";
			} else {
				return distformat.format(meters) + "m";
			}
		} else if(units.equals("Imperial")) {
			// Convert!
			double feet = meters * METERS_PER_FEET;
			
			if(feet >= FEET_PER_MILE) {
				return distformat.format(feet / FEET_PER_MILE) + "mi";
			} else {
				return distformat.format(feet) + "ft";
			}
		} else {
			return units + "???";
		}
	}
}
