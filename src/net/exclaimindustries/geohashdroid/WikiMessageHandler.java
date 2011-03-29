/**
 * WikiMessageHandler.java
 * Copyright (C)2011 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.util.Date;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.util.Log;

/**
 * This is the handler for posting a wiki post.
 * 
 * This is going to get ugly, isn't it?
 * 
 * @author Nicholas Killewald
 * 
 */
public class WikiMessageHandler extends WikiServiceHandler {
    private static final Pattern RE_EXPEDITION = Pattern
            .compile("^(.*)(==+ ?Expedition ?==+.*?)(==+ ?.*? ?==+.*?)$",
                    Pattern.DOTALL);

    private static final String DEBUG_TAG = "WikiMessageHandler";

    /*
     * (non-Javadoc)
     * 
     * @see
     * net.exclaimindustries.geohashdroid.WikiServiceHandler#handlePost(android
     * .content.Context, android.content.Intent)
     */
    @Override
    public void handlePost(Context context, Intent intent) throws WikiException {
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

        // INCOMING INTENT! Grab some data. If any of it is invalid, log an
        // error and return success so we can skip this one.

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

        // The post's text MUST exist. This is sort of the entire point of the
        // wiki post.
        if (!intent.hasExtra(WikiPostService.EXTRA_POST_TEXT)
                || intent.getStringExtra(WikiPostService.EXTRA_POST_TEXT)
                        .trim().length() == 0) {
            Log.e(DEBUG_TAG, "There's no text in this post!");
            return;
        }

        text = intent.getStringExtra(WikiPostService.EXTRA_POST_TEXT);

        // The "include coords" flag MAY exist. It defaults to true.
        include_coords = intent.getBooleanExtra(
                WikiPostService.EXTRA_OPTION_COORDS, true);

        /*
         * PART TWO: Digging up and validating the prefs.
         */
        SharedPreferences prefs = context.getSharedPreferences(
                GHDConstants.PREFS_BASE, 0);

        phoneTime = prefs.getBoolean(GHDConstants.PREF_WIKI_PHONE_TIME, false);

        // These CAN be blank (text-only posts can be anonymous). They do,
        // however, both need to be defined if we're going to log in at all.
        username = prefs.getString(GHDConstants.PREF_WIKI_USER, "").trim();
        password = prefs.getString(GHDConstants.PREF_WIKI_PASS, "");

        /*
         * PART THREE: That whole business of actually posting something.
         */
        HttpClient httpclient = new DefaultHttpClient();

        // Right! Log in first, if we need to. If we're anon, forget
        // it.
        if (username.length() > 0) {
            WikiUtils.login(httpclient, username, password);
        }

        // Get the name of the expedition's wiki page.
        String expedition = WikiUtils.getWikiPageName(info);

        String locationTag = "";

        // Location check! If the location isn't null, we've got something
        // to add to the post.
        if (include_coords && loc != null) {
            String pos = mLatLonFormat.format(loc.getLatitude()) + ","
                    + mLatLonFormat.format(loc.getLongitude());
            locationTag = " [http://www.openstreetmap.org/?lat="
                    + loc.getLatitude() + "&lon=" + loc.getLongitude()
                    + "&zoom=16&layers=B000FTF @" + pos + "]";
        }

        // Moving along, here's where the page kicks in.
        String page;

        // These fields get fed in to WikiUtils once we're all set.
        HashMap<String, String> formFields = new HashMap<String, String>();

        // Get the page as it stands. We'll have to edit it from there.
        page = WikiUtils.getWikiPage(httpclient, expedition, formFields);
        if ((page == null) || (page.trim().length() == 0)) {
            // If it didn't exist, we're making this page anew!
            WikiUtils.putWikiPage(httpclient, expedition, WikiUtils
                    .getWikiExpeditionTemplate(info, context), formFields);

            // Good! Template is in place, so THAT'S our page.
            page = WikiUtils.getWikiPage(httpclient, expedition, formFields);
        }

        // Change the summary so it has our message.
        String summaryPrefix;

        // We shouldn't say this is live, per se, if this is a retrohash.
        if (info.isRetroHash())
            summaryPrefix = context.getText(
                    R.string.wiki_post_message_summary_retro).toString();
        else
            summaryPrefix = context.getText(R.string.wiki_post_message_summary)
                    .toString();

        // Now we have our summary!
        formFields.put("summary", summaryPrefix + " " + text);

        String before = "";
        String after = "";

        // Next, break the current page apart as per our expedition regex.
        Matcher expeditionq = RE_EXPEDITION.matcher(page);
        if (expeditionq.matches()) {
            before = expeditionq.group(1) + expeditionq.group(2);
            after = expeditionq.group(3);
        } else {
            before = page;
        }

        // Next, determine what manner of date we'll use. If we got one
        // with the Intent, use that. If not, and the pref is set to use
        // the phone's time, use that. If not, use five tildes and go with
        // whatever the wiki says.
        String localtime;
        if (timestamp >= 0) {
            localtime = SIG_DATE_FORMAT.format(new Date(timestamp));
        } else if (phoneTime) {
            localtime = SIG_DATE_FORMAT.format(new Date());
        } else {
            localtime = "~~~~~";
        }

        // Now, put that all together into our message!
        String message = "\n*" + text.trim() + "  -- ~~~" + locationTag + " "
                + localtime + "\n";

        // Throw 'er up!
        WikiUtils.putWikiPage(httpclient, expedition, before + message + after,
                formFields);

        // Done!
        return;

    }

}
