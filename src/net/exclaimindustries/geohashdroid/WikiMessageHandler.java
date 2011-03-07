/**
 * WikiMessageHandler.java
 * Copyright (C)2011 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.util.Log;
import net.exclaimindustries.geohashdroid.WikiPostService.WikiPostHandler;

/**
 * This is the handler for posting a wiki post.
 * 
 * This is going to get ugly, isn't it?
 * 
 * @author Nicholas Killewald
 *
 */
public class WikiMessageHandler implements WikiPostHandler {
    private static final String DEBUG_TAG = "WikiMessageHandler";

    /* (non-Javadoc)
     * @see net.exclaimindustries.geohashdroid.WikiPostService.WikiPostHandler#handlePost(android.content.Intent)
     */
    @Override
    public String handlePost(Context context, Intent intent) {
        // We'll be dealing with all this in juuuuuust a few lines...
        Info info = null;
        Location loc = null;
        String text = null;
        long timestamp = -1;
        boolean include_coords = true;
        
        boolean phoneTime = false;
        String username = "";
        String password = "";
        
        /*
         * PART ONE: Validating data and reading it into local variables.
         */
        
        // INCOMING INTENT!  Grab some data.  If any of it is invalid, log an
        // error and return success so we can skip this one.

        
        // Info MUST exist and MUST be an Info object.
        if(!intent.hasExtra(WikiPostService.EXTRA_INFO)) {
            Log.e(DEBUG_TAG, "The Intent has no Info bundle!");
            return "Success";
        }
        
        try {
            info = (Info)(intent.getParcelableExtra(WikiPostService.EXTRA_INFO));
        } catch(Exception ex) {
            Log.e(DEBUG_TAG, "Couldn't deparcelize an Info from the intent!");
            return "Success";
        }
        
        // Latitude and Longitude MAY exist.  If either don't, the location is
        // unknown.
        if(intent.getDoubleExtra(WikiPostService.EXTRA_LATITUDE, -100) > -100
                && intent.getDoubleExtra(WikiPostService.EXTRA_LONGITUDE, -190) > -190) {
            loc = new Location("");
            loc.setLatitude(intent.getDoubleExtra(WikiPostService.EXTRA_LATITUDE, 0));
            loc.setLongitude(intent.getDoubleExtra(WikiPostService.EXTRA_LONGITUDE, 0));
        }
        
        // The timestamp MAY exist.  If it doesn't, the post will be signed with
        // four tildes.
        if(intent.hasExtra(WikiPostService.EXTRA_TIMESTAMP))
            timestamp = intent.getLongExtra(WikiPostService.EXTRA_TIMESTAMP, 0);
        
        // The post's text MUST exist.  This is sort of the entire point of the
        // wiki post.
        if(!intent.hasExtra(WikiPostService.EXTRA_POST_TEXT)
                || intent.getStringExtra(WikiPostService.EXTRA_POST_TEXT).trim().length() == 0) {
            Log.e(DEBUG_TAG, "There's no text in this post!");
            return "Success";
        }
        
        text = intent.getStringExtra(WikiPostService.EXTRA_POST_TEXT);
        
        // The "include coords" flag MAY exist.  It defaults to true.
        include_coords = intent.getBooleanExtra(WikiPostService.EXTRA_OPTION_COORDS, true);
        
        /*
         * PART TWO: Digging up and validating the prefs.
         */
        SharedPreferences prefs = context.getSharedPreferences(
                GHDConstants.PREFS_BASE, 0);
        
        // TODO: Probably want a different way to go about this.
        phoneTime = prefs.getBoolean(GHDConstants.PREF_WIKI_PHONE_TIME, false);
        
        // These CAN be blank (text-only posts can be anonymous).  They do,
        // however, both need to be defined if we're going to log in at all.
        username = prefs.getString(GHDConstants.PREF_WIKI_USER, "");
        password = prefs.getString(GHDConstants.PREF_WIKI_PASS, "");
        
        /*
         * PART THREE: That whole business of actually posting something.
         */
        
        // First, it's a try block.  Exceptions at the end will just return the
        // appropriate response.
//        try {
//            
//        } catch(WikiException wex) {
//            
//        }
        
        return "Success";
    }

}
