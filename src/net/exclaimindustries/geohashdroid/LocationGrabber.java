/**
 * LocationGrabber.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.app.Activity;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * The <code>LocationGrabber</code> activity grabs the user's current single-
 * shot location and returns it as a result.  This can have restrictions on it
 * (GPS only, within some accuracy, etc) to fine-tune the results.
 * 
 * @author Nicholas Killewald
 */
public class LocationGrabber extends Activity {
	/** Result returned when location grabbing failed for some reason. */
	public static final int RESULT_FAIL = 1;

	private LocationManager mManager;
	
//  private final static String DEBUG_TAG = "LocationGrabber";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		
		displaySelf();
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
	}
	
    private void displaySelf() {
        // Same as with StockGrabber...
        // Remove the title so it looks sorta right (the Dialog theme doesn't
        // *quite* get it right, so no title looks a lot better).
        requestWindowFeature(Window.FEATURE_NO_TITLE); 

        // Blur the background.  This may change.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        
        // Throw up content and away we go!
        setContentView(R.layout.genericbusydialog);
        
        TextView textView = (TextView)findViewById(R.id.Text);
        textView.setText(R.string.location_label);
    }
}
