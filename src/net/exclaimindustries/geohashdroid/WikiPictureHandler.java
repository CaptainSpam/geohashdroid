/**
 * WikiPictureHandler.java
 * Copyright (C)2011 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.exclaimindustries.tools.BitmapTools;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint.Style;
import android.location.Location;
import android.util.Log;

/**
 * @author captainspam
 *
 */
public class WikiPictureHandler extends WikiServiceHandler {
    private static final String DEBUG_TAG = "WikiMessageHandler";
    
    /** Matches the gallery section. */
    private static final Pattern RE_GALLERY = Pattern.compile("^(.*<gallery[^>]*>)(.*?)(</gallery>.*)$",Pattern.DOTALL);
    /**
     * Matches the gallery section header.
     * TODO: Replace with API call to edit the section specifically?
     */
    private static final Pattern RE_GALLERY_SECTION = Pattern.compile("^(.*== Photos ==)(.*)$",Pattern.DOTALL);
    
    /** The largest width we'll allow to be uploaded. */
    private static final int MAX_UPLOAD_WIDTH = 800;
    /** The largest height we'll allow to be uploaded. */
    private static final int MAX_UPLOAD_HEIGHT = 600;
    
    private static final int INFOBOX_MARGIN = 16;
    private static final int INFOBOX_PADDING = 8;
    
    private static DecimalFormat mDistFormat = new DecimalFormat("###.######");
    
    private static Paint mBackgroundPaint;
    private static Paint mTextPaint;

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

        // INCOMING INTENT! Here we go again! But THIS time, there's more data
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

        // The post's text MAY exist. We'll allow just posting a picture.
        if (intent.hasExtra(WikiPostService.EXTRA_POST_TEXT)) {
            text = intent.getStringExtra(WikiPostService.EXTRA_POST_TEXT);
        }

        // The "include coords" flag MAY exist. It defaults to true.
        include_coords = intent.getBooleanExtra(
                WikiPostService.EXTRA_OPTION_COORDS, true);

        // The picture MUST exist. That's sort of the whole point. Don't throw
        // here; we throw if the picture WAS defined, but can't be opened for
        // whatever reason.
        if (!intent.hasExtra(WikiPostService.EXTRA_PICTURE_FILE)
                || intent.getStringExtra(WikiPostService.EXTRA_PICTURE_FILE)
                        .trim().length() == 0) {
            Log.e(DEBUG_TAG, "There's no picture defined in this intent!");
            return;
        }

        // The "stamp picture" flag MAY exist. It defaults to false.
        stamp_image = intent.getBooleanExtra(
                WikiPostService.EXTRA_OPTION_PICTURE_STAMP, false);

        /*
         * PART TWO: Digging up and validating the prefs.
         */
        SharedPreferences prefs = context.getSharedPreferences(
                GHDConstants.PREFS_BASE, 0);

        phoneTime = prefs.getBoolean(GHDConstants.PREF_WIKI_PHONE_TIME, false);

        // These MUST be defined (picture posts can't be anonymous). Failure
        // here throws a pause back.
        username = prefs.getString(GHDConstants.PREF_WIKI_USER, "").trim();
        password = prefs.getString(GHDConstants.PREF_WIKI_PASS, "");

        if (username.length() == 0 || password.length() == 0) {
            // Oops.
            throw new WikiException(WikiException.Severity.PAUSING,
                    R.string.wiki_conn_anon_pic_error);
        }

        /*
         * PART THREE: Heeeeeeere we go...
         */
        HttpClient httpclient = new DefaultHttpClient();
        byte[] data = null;

        // Log right on in!
        WikiUtils.login(httpclient, username, password);

        String locationTag = "";

        // Location now!
        if (include_coords && loc != null) {
            String pos = mLatLonFormat.format(loc.getLatitude()) + ","
                    + mLatLonFormat.format(loc.getLongitude());
            locationTag = " [http://www.openstreetmap.org/?lat="
                    + loc.getLatitude() + "&lon=" + loc.getLongitude()
                    + "&zoom=16&layers=B000FTF @" + pos + "]";
        }

        // Now then, we want to scale the image to cut down on memory use and
        // upload time. The Geohashing wiki tends to frown upon images over
        // 150k, so scaling and compressing are the way to go.
        Bitmap bitmap = BitmapTools
                .createRatioPreservedDownscaledBitmapFromFile(picture_file,
                        MAX_UPLOAD_WIDTH, MAX_UPLOAD_HEIGHT, true);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        // If we didn't get a picture out of that, we're boned. Apparently
        // either that's not really an image or the image up and vanished at
        // some point.
        if (bitmap == null) {
            throw new WikiException(WikiException.Severity.PAUSING,
                    R.string.wiki_conn_pic_load_error);
        }

        // Then, if need be, put an infobox on it.
        if (stamp_image) {
            // Since we just got here from BitmapTools, this should be a
            // read/write bitmap.
            drawInfobox(context, info, bitmap, loc);
        }

        // Now, compress it!
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, bytes);
        data = bytes.toByteArray();

        // Do recycling NOW, just to make sure we've booted it out of memory as
        // soon as possible.
        bitmap.recycle();
        System.gc();

        // Next, kick it out the door!
        String localtime;
        if (timestamp >= 0) {
            localtime = SIG_DATE_FORMAT.format(new Date(timestamp));
        } else if (phoneTime) {
            localtime = SIG_DATE_FORMAT.format(new Date());
        } else {
            localtime = "~~~~~";
        }

        String expedition = WikiUtils.getWikiPageName(info);

        String message = text + locationTag;

        String filename = expedition + "_" + localtime + ".jpg";
        String description = message + "\n\n"
                + WikiUtils.getWikiCategories(info);

        HashMap<String, String> formfields = new HashMap<String, String>();

        // At this point, we need an edit token. So, we'll try to get the
        // expedition page for our token. See the MediaWiki API documentation
        // for the reasons why we have to do it this way.
        WikiUtils.getWikiPage(httpclient, expedition, formfields);
        WikiUtils.putWikiImage(httpclient, filename, description, formfields, data);

        // With the picture smashed down and sent, we're ready to put this on
        // the expedition page itself.
        String page;

        // Get the page. Since we're editing it, this is sort of important.
        page = WikiUtils.getWikiPage(httpclient, expedition, formfields);
        if ((page == null) || (page.trim().length() == 0)) {
            // Unless, of course, the page doesn't exist in the first place, in
            // which case we make a new page.
            WikiUtils.putWikiPage(httpclient, expedition, WikiUtils
                    .getWikiExpeditionTemplate(info, context), formfields);
            page = WikiUtils.getWikiPage(httpclient, expedition, formfields);
        }

        // Add in our message (same retro/live caveat as in WikiMessageEditor).
        String summaryPrefix;
        if (info.isRetroHash())
            summaryPrefix = context.getText(
                    R.string.wiki_post_picture_summary_retro).toString();
        else
            summaryPrefix = context.getText(R.string.wiki_post_picture_summary).toString();

        formfields.put("summary", summaryPrefix + " " + message);

        String before = "";
        String after = "";

        // Dig right on in to figure out what needs replacing.
        Matcher galleryq = RE_GALLERY.matcher(page);
        if (galleryq.matches()) {
            before = galleryq.group(1) + galleryq.group(2);
            after = galleryq.group(3);
        } else {
            // If we didn't match the gallery, find the Photos section
            // and create a new gallery in it.
            Matcher photosq = RE_GALLERY_SECTION.matcher(page);
            if (photosq.matches()) {
                before = photosq.group(1) + "\n<gallery>";
                after = "</gallery>\n" + photosq.group(2);
            } else {
                // If we STILL can't find it, just tack it on to the end
                // of the page.
                before = page + "\n<gallery>";
                after = "</gallery>\n";
            }
        }

        String galleryentry = "\nImage:" + filename + " | " + message + "\n";

        // This doesn't get signed, so take it away now!
        WikiUtils.putWikiPage(httpclient, expedition, before + galleryentry + after, formfields);
    }
    
    private void drawInfobox(Context context, Info info, Bitmap bm, Location loc) {
        // First, we need to draw something.  Get a Canvas.
        Canvas c = new Canvas(bm);
        
        // Now, draw!  We want to use the same colors as the Infobox uses.
        makePaints(context);
        
        if(loc != null) {
            // Assemble all our data.  Our four strings will be the final
            // destination, our current location, and the distance.
            String infoTo = context.getString(R.string.infobox_final) + " " + UnitConverter.makeFullCoordinateString(context, info.getFinalLocation(), false, UnitConverter.OUTPUT_LONG);
            String infoYou = context.getString(R.string.infobox_you) + " " + UnitConverter.makeFullCoordinateString(context, loc, false, UnitConverter.OUTPUT_LONG);
            String infoDist = context.getString(R.string.infobox_dist) + " " + UnitConverter.makeDistanceString(context, mDistFormat, info.getDistanceInMeters(loc));
            
            // Then, to the render method!
            String[] strings = {infoTo, infoYou, infoDist};
            drawStrings(strings, c, mTextPaint, mBackgroundPaint);
        } else {
            // Otherwise, just throw up an unknown.
            String[] strings = {context.getString(R.string.location_unknown)};
            drawStrings(strings, c, mTextPaint, mBackgroundPaint);
        }
    }
    
    private void makePaints(Context context) {
        // These are for efficiency's sake so we don't rebuild paints uselessly.
        if(mBackgroundPaint == null) {
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setStyle(Style.FILL);
            mBackgroundPaint.setColor(context.getResources().getColor(R.color.infobox_background));
        }
        
        if(mTextPaint == null) {
            mTextPaint = new Paint();
            mTextPaint.setColor(context.getResources().getColor(R.color.infobox_text));
            mTextPaint.setTextSize(context.getResources().getDimension(R.dimen.infobox_picture_fontsize));
            mTextPaint.setAntiAlias(true);
        }
    }
    
    private void drawStrings(String[] strings, Canvas c, Paint textPaint, Paint backgroundPaint)
    {
        // FIXME: The math here is ugly and blunt and probably not too
        // efficient or flexible.  It might even fail.  This needs to be
        // fixed and made less-ugly later.
        
        // We need SOME strings.  If we've got nothing, bail out.
        if(strings.length < 1) return;
        
        // First, init our variables.  This is as good a place as any to do so.
        Rect textBounds = new Rect();
        int[] heights = new int[strings.length];
        int totalHeight = INFOBOX_MARGIN * 2;
        int longestWidth = 0;
        
        // Now, loop through the strings, adding to the height and keeping track
        // of the longest width.
        int i = 0;
        for(String s : strings) {
            textPaint.getTextBounds(s, 0, s.length(), textBounds);
            if(textBounds.width() > longestWidth) longestWidth = textBounds.width();
            totalHeight += textBounds.height();
            heights[i] = textBounds.height();
            i++;
        }
        
        // Now, we have us a rectangle.  Draw that.
        Rect drawBounds =  new Rect(c.getWidth() - longestWidth - (INFOBOX_MARGIN * 2),
                0,
                c.getWidth(),
                totalHeight);
        
        c.drawRect(drawBounds, backgroundPaint);
        
        // Now, place each of the strings.  We'll assume the topmost one is in
        // index 0.  They should all be left-justified, too.
        i = 0;
        int curHeight = 0;
        for(String s : strings) {
            Log.d(DEBUG_TAG, "Drawing " + s + " at " + (drawBounds.left + INFOBOX_MARGIN) + "," + (INFOBOX_MARGIN + (INFOBOX_PADDING * (i + 1)) + curHeight));
            c.drawText(s, drawBounds.left + INFOBOX_MARGIN, INFOBOX_MARGIN + (INFOBOX_PADDING * (i + 1)) + curHeight, textPaint);
            curHeight += heights[i];
            i++;
        }
    }

}
