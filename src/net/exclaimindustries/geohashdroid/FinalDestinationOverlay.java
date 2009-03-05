/**
 * FinalDestinationOverlay.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

/**
 * The FinalDestinationOverlay draws the final destination flag on the map.
 * 
 * @author Nicholas Killewald
 *
 */
public class FinalDestinationOverlay extends Overlay {
	private Drawable mDrawable;
	private GeoPoint mDestination;
	
	public FinalDestinationOverlay(Drawable d, GeoPoint p) {
		mDrawable = d;
		mDestination = p;
	}
	
	@Override
	public void draw(Canvas canvas, MapView mapView, boolean shadow) {
		super.draw(canvas, mapView, shadow);
		
		// All we're responsible for is coming up with a point and throwing it
		// to the protected method.
		Projection p = mapView.getProjection();
		
		// We need to offset the image to the left one half of its width and up
		// its entire height.  So, let's do that.
		int x;
		int y;
		
		if(!shadow) {
			x = p.toPixels(mDestination, null).x - (mDrawable.getIntrinsicWidth() / 2);
			y = p.toPixels(mDestination, null).y - (mDrawable.getIntrinsicHeight());
		} else {
			// x needs to be adjusted for the skew, depending on the sign.
			// TODO: Check the skewing algorithm; this can't possibly be right
			// in all cases (i.e. SHADOW_X_SKEW changing).
			int sign = (SHADOW_X_SKEW > 0 ? 1 : -1);
			float scalefactor = 1 - Math.abs(SHADOW_X_SKEW);
			x = (int) (p.toPixels(mDestination, null).x - (sign * (mDrawable.getIntrinsicWidth() * scalefactor)));
			y = (int) (p.toPixels(mDestination, null).y - (mDrawable.getIntrinsicHeight() * SHADOW_Y_SCALE));
		}
		
		// And now we can draw!
		drawAt(canvas, mDrawable, x, y, shadow);
	}
}
