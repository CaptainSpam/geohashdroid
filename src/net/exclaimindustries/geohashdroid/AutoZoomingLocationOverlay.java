/**
 * AutoZoomingLocationOverlay.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;

/**
 * <p>
 * An AutoZoomingLocationOverlay can take a Handler and notify it with updates
 * to the location of the phone and its current compass orientation.  It won't,
 * however, send back the location on the first fix.
 * </p>
 * 
 * <p>
 * This is really really specialized to GeohashDroid, but if anyone else has a
 * need for it, well, that's why it's a separate class.
 * </p>
 * 
 * @author Nicholas Killewald
 */
public class AutoZoomingLocationOverlay extends MyLocationOverlay {
	private Handler mHandler;
	private boolean mFirstFix = false;
	// This is set to true if we've recieved a GPS fix so we know to ignore any
	// cell tower fixes insofar as handling is concerned.  This is reset to
	// false if GPS is disabled for whatever reason, and starts out false so we
	// can go to the towers until we get our first fix.  This is to solve what
	// I like to call the "Glasgow Problem", where for some reason when I tried
	// this in Lexington, KY, I kept getting network fixes somewhere in the
	// city of Glasgow, KY, some hundred or so miles away.  I'm certain there's
	// already a name for this sort of problem, but I like naming a problem
	// after a city in Kentucky, mainly because I like to think most of my
	// problems are related to living in the state of Kentucky. :-)
	private boolean mHaveGPSFix = false;
	
	/**
	 * Message indicating the location was changed and the object sent back is,
	 * in fact, a GeoPoint.
	 */
	public static final int LOCATION_CHANGED = 1;
	/**
	 * Message indicating the orientation was changed and the object sent back
	 * is, in fact, the new orientation.
	 */
	public static final int ORIENTATION_CHANGED = 2;
	/**
	 * Message indicating that this is the first location fix, and thus the
	 * Normal View menu item should become active, if it wasn't before.
	 */
	public static final int FIRST_FIX = 3;
	
	public AutoZoomingLocationOverlay(Context context, MapView mapView) {
		super(context, mapView);
	}
	
	/**
	 * Sets the handler which will handle what needs handling.
	 * 
	 * @param handler the handler of choice
	 */
	public void setHandler(Handler handler) {
		mHandler = handler;
	}

	@Override
	public void onProviderDisabled(String provider) {
		super.onProviderDisabled(provider);
		
		// If GPS just went down, reset our flag.  There's no corresponding
		// override to onProviderEnabled; once the first fix comes in, the flag
		// will be reset there.
		if(provider.equals(android.location.LocationManager.GPS_PROVIDER))
			mHaveGPSFix = false;
	}

	/* (non-Javadoc)
	 * @see com.google.android.maps.MyLocationOverlay#onStatusChanged(java.lang.String, int, android.os.Bundle)
	 */
	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		super.onStatusChanged(provider, status, extras);
		
		// If GPS goes all unavailable, we no longer have a GPS fix.  It's that
		// simple.
		if(provider.equals(android.location.LocationManager.GPS_PROVIDER) && status != android.location.LocationProvider.AVAILABLE) {
			mHaveGPSFix = false;
		}
		
		// However, we DON'T do the inverse.  We don't need to.  mHaveGPSFix is
		// reset once the fix comes in, after it's available again.
	}

	@Override
	public synchronized void onLocationChanged(Location location) {
		super.onLocationChanged(location);
		
		if(location == null) return;
		
		// First, set the fix flag if we need to.
		if(location.getProvider().equals(android.location.LocationManager.GPS_PROVIDER))
			mHaveGPSFix = true;
		
		// Then, return if this is a tower update and we're tracking GPS.
		if(mHaveGPSFix && !location.getProvider().equals(android.location.LocationManager.GPS_PROVIDER))
			return;
		
		// On a location change, let the main activity know just where the
		// heck we think we are.  Hopefully, it'll zoom as need be.
		if(!mFirstFix) {
			Message mess = Message.obtain(mHandler,
					LOCATION_CHANGED,
					new GeoPoint(
							(int)(location.getLatitude() * 1000000),
							(int)(location.getLongitude() * 1000000)
							)
			);
			
			// Dispatch!
			mess.sendToTarget();
		} else {
			mFirstFix = false;
			
			// The first fix message doesn't send anything.  It just tells the
			// handler to turn on the menu item.
			Message mess = Message.obtain(mHandler,FIRST_FIX);
			
			mess.sendToTarget();
		}
	}

	@Override
	public synchronized void onSensorChanged(int sensor, float[] values) {
		// TODO Auto-generated method stub
		super.onSensorChanged(sensor, values);
	}

	@Override
	public synchronized boolean runOnFirstFix(Runnable runnable) {
		mFirstFix = true;
		return super.runOnFirstFix(runnable);
	}
}
