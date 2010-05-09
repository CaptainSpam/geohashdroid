/**
 * FixedMyLocationOverlay.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Paint.Style;
import android.graphics.drawable.Drawable;
import android.location.Location;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Projection;

/**
 * <p>
 * FixedMyLocationOverlay is just a MyLocationOverlay with fixes in place for
 * phones with dodgy firmware that don't properly implement the Google Maps API
 * add-on, like the Motorola CLIQ.
 * </p>
 * 
 * @author Nicholas Killewald
 */
public class FixedMyLocationOverlay extends MyLocationOverlay {
    // Gets set to true if an error occurs when drawing the point the normal
    // way.  This means we're using a Motorola CLIQ or any other device that
    // doesn't properly implement the MyLocationListener drawable.
    private boolean mBugged = false;
    
    // We want to hold on to all of these so we don't need to keep re-obtaining
    // them over and over and over again.
    private Drawable mIndicator;
    private int mIndicatorWidth;
    private int mIndicatorHeight;
    private Point mIndicatorLeft;
    private Point mIndicatorCenter;
    private Paint mIndicatorAccuracyPaint;

    public FixedMyLocationOverlay(Context context, MapView mapView) {
        super(context, mapView);
    }

    @Override
    protected void drawMyLocation(Canvas canvas, MapView mapView, Location lastFix, GeoPoint myLocation, long when) {
        // This needs to be overridden to account for devices like the Motorola
        // CLIQ which can't deal with the MyLocationOverlay object.  This was
        // all found in the Motorola developer forums.
        if(!mBugged) {
            // Try to draw this the normal way first.  If we fail, we'll know
            // this went wrong.
            try {
                super.drawMyLocation(canvas, mapView, lastFix, myLocation, when);
            } catch(Exception e) {
                mBugged = true;
            }
        }

        // So, if we're bugged at this point, let's do it the hard way...
        if(mBugged) {
            if (mIndicator == null) {
                // If this is the first time through, get all our paints and
                // values and such so we don't need to do this repeatedly.
                mIndicatorAccuracyPaint = new Paint();
                mIndicatorAccuracyPaint.setAntiAlias(true);
                mIndicatorAccuracyPaint.setStrokeWidth(2.0f);
               
                mIndicator = mapView.getContext().getResources().getDrawable(R.drawable.ic_maps_indicator_current_position);
                mIndicatorWidth = mIndicator.getIntrinsicWidth();
                mIndicatorHeight = mIndicator.getIntrinsicHeight();
                mIndicatorCenter = new Point();
                mIndicatorLeft = new Point();
            }
            
            // Now, commence drawing!
            Projection projection = mapView.getProjection();
            
            // First, we need the location and accuracy of the fix.
            double latitude = lastFix.getLatitude();
            double longitude = lastFix.getLongitude();
            float accuracy = lastFix.getAccuracy();
           
            // We also need to calculate out how far degrees are in this
            // particular projection for the accuracy-o-meter thingy.  This
            // should be close enough.
            float[] result = new float[1];

            Location.distanceBetween(latitude, longitude, latitude, longitude + 1, result);
            float longitudeLineDistance = result[0];
            
            GeoPoint leftGeo = new GeoPoint((int)(latitude*1e6), (int)((longitude-accuracy/longitudeLineDistance)*1e6));
            projection.toPixels(leftGeo, mIndicatorLeft);
            projection.toPixels(myLocation, mIndicatorCenter);
            int radius = mIndicatorCenter.x - mIndicatorLeft.x;

            // And now, paint!
            mIndicatorAccuracyPaint.setColor(0xff6666ff);
            mIndicatorAccuracyPaint.setStyle(Style.STROKE);
            canvas.drawCircle(mIndicatorCenter.x, mIndicatorCenter.y, radius, mIndicatorAccuracyPaint);
    
            mIndicatorAccuracyPaint.setColor(0x186666ff);
            mIndicatorAccuracyPaint.setStyle(Style.FILL);
            canvas.drawCircle(mIndicatorCenter.x, mIndicatorCenter.y, radius, mIndicatorAccuracyPaint);
                       
            mIndicator.setBounds(mIndicatorCenter.x - mIndicatorWidth / 2, mIndicatorCenter.y - mIndicatorHeight / 2, mIndicatorCenter.x + mIndicatorWidth / 2, mIndicatorCenter.y + mIndicatorHeight / 2);
            mIndicator.draw(canvas);
        }
    }
    
}
