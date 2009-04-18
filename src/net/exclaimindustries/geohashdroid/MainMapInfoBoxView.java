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
	/** The last known Info bundle. */
	protected Info lastInfo = null;
	/** The last known location. */
	protected Location lastLoc = null;
	
	/** The decimal format for the coordinates. */
	protected DecimalFormat mLatLonFormat = new DecimalFormat("###.000");
	/** The decimal format for distances. */
	protected DecimalFormat mDistFormat = new DecimalFormat("###.###");

	public MainMapInfoBoxView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public MainMapInfoBoxView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public MainMapInfoBoxView(Context context) {
		super(context);
	}

	/* (non-Javadoc)
	 * @see android.view.View#setVisibility(int)
	 */
	@Override
	public void setVisibility(int visibility) {
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
		
		// The final destination coordinates
		String finalLine = getContext().getString(R.string.infobox_final) + " "
			+ mLatLonFormat.format(Math.abs(info.getLatitude())) + (info.getLatitude() >= 0 ? 'N' : 'S') + " "
			+ mLatLonFormat.format(Math.abs(info.getLongitude())) + (info.getLongitude() >= 0 ? 'E' : 'W');
		
		// Your current location coordinates
		String youLine;
		if(loc != null) {
			youLine = getContext().getString(R.string.infobox_you) + " "
				+ (mLatLonFormat.format(Math.abs(loc.getLatitude()))) + (loc.getLatitude() >= 0 ? 'N' : 'S') + " "
				+ (mLatLonFormat.format(Math.abs(loc.getLongitude()))) + (loc.getLongitude() >= 0 ? 'E' : 'W');
		} else {
			youLine = getContext().getString(R.string.infobox_you) + " " + getContext().getString(R.string.standby_title);
		}
		
		// The distance to the final destination (as the crow flies)
		String distanceLine = getContext().getString(R.string.infobox_dist) + " "
			+ (loc != null ? (DistanceConverter.makeDistanceString(getContext(), mDistFormat, info.getDistanceInMeters(loc))) : getContext().getString(R.string.standby_title));
		
		setText(finalLine + "\n" + youLine + "\n" + distanceLine);
	}
}
