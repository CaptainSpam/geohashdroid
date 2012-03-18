/**
 * ClosenessActor.java
 * Copyright (C)2012 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.widget.Toast;

/**
 * <p>
 * A <code>ClosenessActor</code> is a class passed around to all Activities that
 * deal with tracking the user (so, the main map, both wiki editors, and the
 * details screen).  Its main use is to keep track of whether or not the user's
 * gotten close enough to the point that we can consider them "there"(within the
 * accuracy of GPS), and to do something when that happens.  Like, say, make a
 * Toast popup or something.
 * </p>
 * 
 * <p>
 * This exists solely because all the tracking Activities need it, but since the
 * map has to inherit from MapActivity, I can't make a base class from which all
 * of them can pick up this functionality.  Thus, we resort to has-a instead of
 * is-a.
 * </p>
 * 
 * <p>
 * TODO: Maybe this would work better as an abstract class so we can have
 * multiple types of closeness actors passed around as an array?
 * </p>
 * 
 * @author Nicholas Killewald
 *
 */
public class ClosenessActor {
    private Context mContext;
    /**
     * Cache this locally so we don't need to keep going to preferences on EVERY
     * location update. 
     */
    private boolean mBeenThere;
    
    /**
     * Makes a new ClosenessActor.  The only way this could be MORE exciting is
     * if you actually WERE an instance of a Java class, witnessing the birth of
     * another like you.
     * 
     * @param c Context on which to act and get prefs
     */
    public ClosenessActor(Context c) {
        mContext = c;
        
        mBeenThere = mContext.getSharedPreferences(GHDConstants.PREFS_BASE, 0).getBoolean(GHDConstants.PREF_CLOSENESS_REPORTED, false);
    }
    
    /**
     * Resets this ClosenessActor.  In effect, makes it forget if it's already
     * reported closeness.
     */
    public void reset() {
        SharedPreferences.Editor editor = mContext.getSharedPreferences(GHDConstants.PREFS_BASE, 0).edit();
        editor.putBoolean(GHDConstants.PREF_CLOSENESS_REPORTED, false);
        editor.commit();
        mBeenThere = false;
    }
    
    /**
     * Makes this ClosenessActor do some acting on the current location.
     *
     * @param info the active Info object
     * @param loc the current Location on which to act
     */
    public void actOnLocation(Info info, Location loc) {
        // First, if we've been down this path before, ignore it.
        if(mBeenThere) return;
        
        // Otherwise, do a quick measurement get toastin'!
        if(isCloseEnough(info, loc)) {
            // Let's abuse the preferences system to give us an effectively
            // static boolean!
            SharedPreferences.Editor editor = mContext.getSharedPreferences(GHDConstants.PREFS_BASE, 0).edit();
            editor.putBoolean(GHDConstants.PREF_CLOSENESS_REPORTED, true);
            editor.commit();
            mBeenThere = true;
            Toast.makeText(mContext, R.string.toast_close_enough, Toast.LENGTH_LONG);
        }
    }
    
    private boolean isCloseEnough(Info info, Location loc) {
        if(loc == null) {
            return false;
        } else {
            float accuracy = loc.getAccuracy();
            
            // Don't trust zero accuracy!  Just don't!
            if(accuracy == 0) accuracy = 5;
            
            if(accuracy < GHDConstants.LOW_ACCURACY_THRESHOLD
                    && info.getDistanceInMeters(loc) <= accuracy) {
                return true;
            } else {
                return false;
            }
        }
        
    }
}
