/**
 * GraticuleHighlightOverlay.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Join;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Projection;

/**
 * This is the overlay that draws the current graticule on the map and handles
 * map taps to select a new Graticule.
 * 
 * @author Nicholas Killewald
 */
public class GraticuleHighlightOverlay extends GraticuleOverlay {
	private Graticule mGraticule;
	private GraticuleChangedListener mListener;
	private Context mContext;
	private boolean mHandleTaps = true;
	
	/**
	 * Constructs a new GraticuleHighlightOverlay with the given Graticule selected.
	 * Note that if the Graticule is null, this won't draw anything until the
	 * user taps something.
	 * 
	 * @param c Context from whence this came (needed for resource information)
	 * @param g Graticule to draw (or null to not draw anything yet)
	 * @param gcl listener to pay attention to taps when new graticules are selected
	 */
	public GraticuleHighlightOverlay(Context c, Graticule g, GraticuleChangedListener gcl) {
		mContext = c;
		mGraticule = g;
		mListener = gcl;
	}

	@Override
	public void draw(Canvas canvas, MapView mapView, boolean shadow) {
		super.draw(canvas, mapView, shadow);
		
		// We don't do shadows.
		if(shadow) return;
		
		Projection p = mapView.getProjection();
		
		drawGraticule(canvas, p, mGraticule);
	}

	@Override
	public boolean onTap(GeoPoint p, MapView mapView) {
		// All we need to know is where the user tapped.  Then we update the
		// Graticule and let the listener know.
		if(mHandleTaps) {
			mGraticule = new Graticule(p);
			mListener.graticuleUpdated(mGraticule);
			mapView.invalidate();
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Manually set the Graticule after construction.  Note that the MapView
	 * will need to be invalidated afterward.
	 * 
	 * @param g new Graticule to set
	 */
	public void setGraticule(Graticule g) {
		mGraticule = g;
		mListener.graticuleUpdated(mGraticule);
	}
	
	/**
	 * Whether or not this overlay should handle taps.  That is, if it should
	 * select the graticule or just display it.
	 * 
	 * @param flag true to select, false to not
	 */
	public void setTapHandling(boolean flag) {
		mHandleTaps = flag;
	}

	@Override
	protected void drawGraticule(Canvas c, Projection pr, Graticule g) {
		// Fill it in first...
		Paint paint = new Paint();
		paint.setColor(mContext.getResources().getColor(R.color.graticule_fill));
		drawGraticuleFill(c,pr,g,paint);
		
		// Then, outline it.
		paint.setColor(mContext.getResources().getColor(R.color.graticule_stroke));
		paint.setStrokeWidth(2);
		paint.setStrokeJoin(Join.ROUND);
		drawGraticuleOutline(c,pr,g,paint);
	}
}
