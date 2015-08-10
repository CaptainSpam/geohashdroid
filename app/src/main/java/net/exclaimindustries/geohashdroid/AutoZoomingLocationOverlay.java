/**
 * AutoZoomingLocationOverlay.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Paint.Style;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Projection;

/**
 * <p>
 * An AutoZoomingLocationOverlay can take a Handler and notify it with updates
 * to the location of the phone and its current compass orientation. It won't,
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
    // cell tower fixes insofar as handling is concerned. This is reset to
    // false if GPS is disabled for whatever reason, and starts out false so we
    // can go to the towers until we get our first fix. This is to solve what
    // I like to call the "Glasgow Problem", where for some reason when I tried
    // this in Lexington, KY, I kept getting network fixes somewhere in the
    // city of Glasgow, KY, some hundred or so miles away. I'm certain there's
    // already a name for this sort of problem, but I like naming a problem
    // after a city in Kentucky, mainly because I like to think most of my
    // problems are related to living in the state of Kentucky. :-)
    private boolean mHaveGPSFix = false;

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
     * Message indicating that this is, in fact, the first location fix, and
     * thus the Normal View menu item should become active, if it wasn't before.
     */
    public static final int FIRST_FIX = 3;
    /**
     * Message indicating that the fix, in fact, has been lost. In this case,
     * the handler should indicate a "Stand by..." sort of message.
     */
    public static final int LOST_FIX = 4;

    public AutoZoomingLocationOverlay(Context context, MapView mapView) {
        super(context, mapView);
    }

    /**
     * Sets the handler which will handle what needs handling.
     * 
     * @param handler
     *            the handler of choice
     */
    public void setHandler(Handler handler) {
        mHandler = handler;
    }

    @Override
    public void onProviderDisabled(String provider) {
        super.onProviderDisabled(provider);

        // If GPS just went down, reset our flag. There's no corresponding
        // override to onProviderEnabled; once the first fix comes in, the flag
        // will be reset there.
        if (provider.equals(android.location.LocationManager.GPS_PROVIDER))
            mHaveGPSFix = false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.google.android.maps.MyLocationOverlay#onStatusChanged(java.lang.String
     * , int, android.os.Bundle)
     */
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        super.onStatusChanged(provider, status, extras);

        // If GPS goes all unavailable, we no longer have a GPS fix. It's that
        // simple.
        if (provider.equals(android.location.LocationManager.GPS_PROVIDER)
                && status != android.location.LocationProvider.AVAILABLE) {
            mHaveGPSFix = false;
        }

        // However, we DON'T do the inverse. We don't need to. mHaveGPSFix is
        // reset once the fix comes in, after it's available again.
    }

    @Override
    public synchronized void onLocationChanged(Location location) {
        super.onLocationChanged(location);

        // If the location is null, we've lost any fix we had before and should
        // fall back to a "Stand by..." message.
        if (location == null) {
            Message mess = Message.obtain(mHandler, LOST_FIX);
            mess.sendToTarget();
            return;
        }

        // First, set the fix flag if we need to.
        if (location.getProvider().equals(
                android.location.LocationManager.GPS_PROVIDER))
            mHaveGPSFix = true;

        // Then, return if this is a tower update and we're tracking GPS.
        if (mHaveGPSFix
                && !location.getProvider().equals(
                        android.location.LocationManager.GPS_PROVIDER))
            return;

        // On a location change, let the main activity know just where the
        // heck we think we are. Hopefully, it'll zoom as need be.
        if (!mFirstFix) {
            Message mess = Message.obtain(mHandler, LOCATION_CHANGED,
                    new GeoPoint((int)(location.getLatitude() * 1000000),
                            (int)(location.getLongitude() * 1000000)));

            // Dispatch!
            mess.sendToTarget();
        } else {
            mFirstFix = false;

            // The first fix message doesn't send anything. It just tells the
            // handler to turn on the menu item.
            Message mess = Message.obtain(mHandler, FIRST_FIX);

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
