/**
 * WikiPictureHandler.java
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

/**
 * @author captainspam
 *
 */
public class WikiPictureHandler extends WikiServiceHandler {
    private static final String DEBUG_TAG = "WikiMessageHandler";

    /* (non-Javadoc)
     * @see net.exclaimindustries.geohashdroid.WikiServiceHandler#handlePost(android.content.Context, android.content.Intent)
     */
    @Override
    public void handlePost(Context context, Intent intent) throws WikiException {
        // Here comes a bunch of oddball fields!
        Info info = null;
        Location loc = null;
        String text = "";
        long timestamp = -1;
        boolean include_coords = true;
        String picture_file = null;
        boolean stamp_image = false;

        boolean phoneTime = false;
        String username = "";
        String password = "";
        
        /*
         * PART ONE: Validating data and reading it into local variables.
         */

        // INCOMING INTENT!  Here we go again!  But THIS time, there's more data
        // to grab!

        // Info MUST exist and MUST be an Info object.
        if (!intent.hasExtra(WikiPostService.EXTRA_INFO)) {
            Log.e(DEBUG_TAG, "The Intent has no Info bundle!");
            return;
        }

        try {
            info = (Info)(intent.getParcelableExtra(WikiPostService.EXTRA_INFO));
        } catch (Exception ex) {
            Log.e(DEBUG_TAG, "Couldn't deparcelize an Info from the intent!");
            ex.printStackTrace();
            return;
        }

        // Latitude and Longitude MAY exist. If either don't, the location is
        // unknown.
        if (intent.getDoubleExtra(WikiPostService.EXTRA_LATITUDE, -100) > -100
                && intent.getDoubleExtra(WikiPostService.EXTRA_LONGITUDE, -190) > -190) {
            loc = new Location("");
            loc.setLatitude(intent.getDoubleExtra(
                    WikiPostService.EXTRA_LATITUDE, 0));
            loc.setLongitude(intent.getDoubleExtra(
                    WikiPostService.EXTRA_LONGITUDE, 0));
        }

        // The timestamp MAY exist. If it doesn't, the post will be signed with
        // four tildes.
        if (intent.hasExtra(WikiPostService.EXTRA_TIMESTAMP))
            timestamp = intent.getLongExtra(WikiPostService.EXTRA_TIMESTAMP, 0);

        // The post's text MAY exist.  We'll allow just posting a picture.
        if (intent.hasExtra(WikiPostService.EXTRA_POST_TEXT)) {
            text = intent.getStringExtra(WikiPostService.EXTRA_POST_TEXT);
        }

        // The "include coords" flag MAY exist. It defaults to true.
        include_coords = intent.getBooleanExtra(
                WikiPostService.EXTRA_OPTION_COORDS, true);
        
        // The picture MUST exist.  That's sort of the whole point.  Don't throw
        // here; we throw if the picture WAS defined, but can't be opened for
        // whatever reason.
        if(!intent.hasExtra(WikiPostService.EXTRA_PICTURE_FILE)
                || intent.getStringExtra(WikiPostService.EXTRA_PICTURE_FILE).trim().length() == 0)
        {
            Log.e(DEBUG_TAG, "There's no picture defined in this intent!");
            return;
        }
        
        // The "stamp picture" flag MAY exist.  It defaults to false.
        stamp_image = intent.getBooleanExtra(
                WikiPostService.EXTRA_OPTION_PICTURE_STAMP, false);
        
        /*
         * PART TWO: Digging up and validating the prefs.
         */
        SharedPreferences prefs = context.getSharedPreferences(
                GHDConstants.PREFS_BASE, 0);

        phoneTime = prefs.getBoolean(GHDConstants.PREF_WIKI_PHONE_TIME, false);

        // These MUST be defined (picture posts can't be anonymous).  Failure
        // here throws a pause back.
        username = prefs.getString(GHDConstants.PREF_WIKI_USER, "").trim();
        password = prefs.getString(GHDConstants.PREF_WIKI_PASS, "");
        
        if(username.length() == 0 || password.length() == 0) {
            // Oops.
            throw new WikiException(WikiException.Severity.PAUSING, R.string.wiki_conn_anon_pic_error);
        }
    }

}
