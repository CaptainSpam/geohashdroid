/**
 * 9:56:42 PM
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;

/**
 * A <code>PlainActivity</code> is the base of basic, non-special Geohash Droid
 * Activities.  Because Nine-Patch images can't be used the way I want them to
 * be in terms of window backgrounds, this base class exists to apply the
 * appropriate theme depending on the screen orientation.  It may wind up
 * vanishing once I have a better solution.
 *  
 * @author Nicholas Killewald
 */
public abstract class PlainActivity extends Activity {

    /**
     * This simply sets up the theme depending on orientation, plus whatever it
     * is the superclass does in this sort of situation.
     * 
     * @param savedInstanceState an incoming Bundle of joy
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        int orientation = getResources().getConfiguration().orientation;
        
        switch(orientation) {
            case Configuration.ORIENTATION_LANDSCAPE:
                setTheme(R.style.Theme_GeohashDroid_Landscape);
                break;
            case Configuration.ORIENTATION_PORTRAIT:
                setTheme(R.style.Theme_GeohashDroid_Portrait);
                break;
            default:
                setTheme(android.R.style.Theme_Light);
        }
    }
    
}
