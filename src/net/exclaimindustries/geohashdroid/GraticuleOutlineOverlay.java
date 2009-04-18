/**
 * GraticuleOutlineOverlay.java
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
import android.view.MotionEvent;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Projection;

/**
 * The <code>GraticuleOutlineOverlay</code> draws an outline of the currently-
 * highlighted graticule in the event of trackball scrolling.  That is, it
 * outlines what will be selected if the user presses the trackball button at
 * that moment.
 *  
 * @author Nicholas Killewald
 */
public class GraticuleOutlineOverlay extends GraticuleOverlay {
	private Context mContext;
	private Graticule mGraticule;
	private boolean mHandleTrackball = true;
	
	private static Paint outlinePaint = null;
	
	/**
	 * Constructs a new GraticuleOutlineOverlay with the given Graticule
	 * outlined.  Note that if the Graticule is null, this won't draw
	 * anything until the user trackballs to something.
	 * 
	 * @param c Context from whence this came (needed for resource information)
	 * @param g Graticule to draw (or null to not draw anything yet)
	 */
	public GraticuleOutlineOverlay(Context c, Graticule g) {
		mContext = c;
		mGraticule = g;
		
		// Predefine the not-going-to-change outline paint so we're not just
		// constantly recreating it every draw.
		if(outlinePaint == null) {
			outlinePaint = new Paint();
			
			outlinePaint.setColor(mContext.getResources().getColor(R.color.graticule_outline));
			outlinePaint.setStrokeWidth(2);
			outlinePaint.setStrokeJoin(Join.ROUND);
		}
	}
	
	@Override
	public void draw(Canvas canvas, MapView mapView, boolean shadow) {
		super.draw(canvas, mapView, shadow);
		
		// We don't do shadows.
		if(shadow) return;
		
		Projection p = mapView.getProjection();
		
		drawGraticule(canvas, p, mGraticule);
	}
	
	/**
	 * Whether or not we should handle trackball events.  That is, if it should
	 * highlight the graticule when moved.
	 * 
	 * @param flag true to select, false to not
	 */
	public void setTrackballHandling(boolean flag) {
		mHandleTrackball = flag;
	}
	
	/* (non-Javadoc)
	 * @see com.google.android.maps.Overlay#onTap(com.google.android.maps.GeoPoint, com.google.android.maps.MapView)
	 */
	@Override
	public boolean onTap(GeoPoint p, MapView mapView) {
		// When tapped, shut off the outline and pass this to the next handler
		// (most likely, a GraticuleHighlightOverlay).  This includes trackball
		// clicks; that will select a graticule and thus remove the need for
		// the outline.
		super.onTap(p, mapView);
		mGraticule = null;
		return false;
	}

	/* (non-Javadoc)
	 * @see com.google.android.maps.Overlay#onTouchEvent(android.view.MotionEvent, com.google.android.maps.MapView)
	 */
	@Override
	public boolean onTouchEvent(MotionEvent e, MapView mapView) {
		// Same with a tap event; if we're touch-scrolling, that overrides
		// whatever the trackball last saw.
		super.onTouchEvent(e, mapView);
		mGraticule = null;
		return false;
	}
	
	// Note, we do NOT handle trackball events here.  Reason being, trackball
	// handling is done on the MapView AFTER all the overlays have had their
	// say, so we'd get it before the map moves.

	/**
	 * Update the graticule from the given MapView.  Specifically, this is what
	 * should be called AFTER the MapView handles panning.
	 * 
	 * @param mapView MapView to look at for a center point
	 */
	public void updateGraticule(MapView mapView) {
		// Grab whatever the center of the map is.
		GeoPoint center = mapView.getMapCenter();
		Graticule g = new Graticule(center);
		
		// If it's changed, change the graticule and order up a redraw.
		if(mHandleTrackball && (mGraticule == null || !mGraticule.equals(g))) {
			mGraticule = g;
			mapView.invalidate();
		}
		
	}

	/* (non-Javadoc)
	 * @see net.exclaimindustries.geohashdroid.GraticuleOverlay#drawGraticule(android.graphics.Canvas, com.google.android.maps.Projection, net.exclaimindustries.geohashdroid.Graticule)
	 */
	@Override
	protected void drawGraticule(Canvas c, Projection pr, Graticule g) {
		// Only the outline this time.
		drawGraticuleOutline(c,pr,g,outlinePaint);
	}

	/**
	 * Manually set the Graticule after construction.  Note that the MapView
	 * will need to be invalidated afterward.
	 * 
	 * @param g new Graticule to set
	 */
	public void setGraticule(Graticule g) {
		mGraticule = g;
	}
}
