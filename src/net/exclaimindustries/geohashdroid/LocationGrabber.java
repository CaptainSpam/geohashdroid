/**
 * LocationGrabber.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.widget.TextView;

/**
 * The <code>LocationGrabber</code> activity grabs the user's current single-
 * shot location and returns it as a result.  This will grab from whatever it
 * can if the providers are available.
 * 
 * @author Nicholas Killewald
 */
public class LocationGrabber extends Activity implements LocationListener {
	/** Result returned when location grabbing failed for some reason. */
	public static final int RESULT_FAIL = 1;
	
	private LocationManager mManager;
	
	private HashMap<String, Boolean> mEnabledProviders;
    private final static String DEBUG_TAG = "LocationGrabber";
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Unlike the StockGrabber, the popup is ALWAYS displayed. StockGrabber
        // could find its stuff in the cache and thus be able to return almost
        // immediately, but we have no reason to assume a near-instant result
        // from the location. Also unlike StockGrabber, we don't have an extra
        // thread to worry about; everything happens with LocationManager.
        displaySelf();

        // Now, grab a LocationManager.
        mManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

        // Set up the hash of providers. Yes, there's only two.
        List<String> providers = mManager.getProviders(false);
        if (providers.isEmpty()) {
            // FAIL! No providers are available!
            failure(RESULT_FAIL);
        }

        mEnabledProviders = new HashMap<String, Boolean>();

        // Stuff all the providers into the HashMap, along with their current,
        // respective statuses.
        for (String s : providers)
            mEnabledProviders.put(s, mManager.isProviderEnabled(s));

        // Then, register for responses and get ready for fun!
        for (String s : providers)
            mManager.requestLocationUpdates(s, 0, 0, this);
	}

	@Override
    protected void onDestroy() {
        super.onDestroy();
	    // If we're being destroyed, shut down all the providers.  We'll come
        // back here if this was a config change and restart the GPS search, or
        // if we're done, we'll finish anyway.
        mManager.removeUpdates(this);
	    mEnabledProviders.clear();
    }
	
    private void displaySelf() {
        // Same as with StockGrabber...
        // Remove the title so it looks sorta right (the Dialog theme doesn't
        // *quite* get it right, so no title looks a lot better).
        requestWindowFeature(Window.FEATURE_NO_TITLE); 
        
        // Throw up content and away we go!
        setContentView(R.layout.genericbusydialog);
        
        TextView textView = (TextView)findViewById(R.id.Text);
        textView.setText(R.string.location_label);
    }
    
    private void failure(int resultCode)
    {
        Log.i(DEBUG_TAG, "Couldn't get location");
        Intent i = new Intent();
        setResult(resultCode, i);
		mManager.removeUpdates(this);
        finish();
    }
    
    private void success(Location l)
    {
        if(l != null)
            Log.i(DEBUG_TAG, "Location appears to be " + l.getLatitude() + "," + l.getLongitude());
        else
        {
            Log.e(DEBUG_TAG, "Location given to the success method is null!");
            failure(RESULT_FAIL);
            return;
        }
    	Intent i = new Intent();
    	i.putExtra(GeohashDroid.LATITUDE, l.getLatitude());
    	i.putExtra(GeohashDroid.LONGITUDE, l.getLongitude());
    	setResult(RESULT_OK, i);
		mManager.removeUpdates(this);
    	finish();
    }

	@Override
	public void onLocationChanged(Location location) {
		// DONE!  We have our one result, so let's send it home!
		success(location);
	}

	@Override
	public void onProviderDisabled(String provider) {
		mEnabledProviders.put(provider, false);
		if(!areAnyProvidersStillAlive())
			failure(RESULT_FAIL);
	}

	@Override
	public void onProviderEnabled(String provider) {
		mEnabledProviders.put(provider, true);
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
        if (status == LocationProvider.OUT_OF_SERVICE) {
        	// OUT_OF_SERVICE implies the provider is down for the count.
        	// Anything else means the provider is available, but maybe not
        	// enabled.
        	mEnabledProviders.put(provider, false);
        } else {
            mEnabledProviders.put(provider, mManager.isProviderEnabled(provider));
        }
        
        if(!areAnyProvidersStillAlive())
        	failure(RESULT_FAIL);
	}
	
	private boolean areAnyProvidersStillAlive()
	{
		// Somehow, I feel the method name is pretty self-explanatory.
		if(mEnabledProviders.isEmpty()) return false;
		
        for (String s : mEnabledProviders.keySet()) {
            if (mEnabledProviders.get(s)) {
                return true;
            }
        }
        
        return false;
	}
}
