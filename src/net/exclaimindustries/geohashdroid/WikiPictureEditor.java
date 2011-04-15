/**
 * WikiPictureEditor.java
 * Copyright (C)2009 Thomas Hirsch
 * Geohashdroid Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.os.Bundle;
import android.os.Handler;
import android.app.ProgressDialog;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ImageView;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import android.provider.MediaStore;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint.Style;

import android.util.DisplayMetrics;
import android.util.Log;
import android.location.Location;

import net.exclaimindustries.tools.BitmapTools;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;

import java.util.HashMap;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.io.ByteArrayOutputStream;
import android.net.Uri;

import java.util.Date;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;

/**
 * Displays a picture selector, an edit box and a send button, which shall upload the picture to the wiki and add it to the
 * Gallery for the expedition of the corresponding day.
 * 
 * @author Thomas Hirsch
 */
public class WikiPictureEditor extends WikiBaseActivity {

    /** Matches the gallery section. */
    private static final Pattern RE_GALLERY = Pattern.compile("^(.*<gallery[^>]*>)(.*?)(</gallery>.*)$",Pattern.DOTALL);
    /**
     * Matches the gallery section header.
     * TODO: Replace with API call to edit the section specifically?
     */
    private static final Pattern RE_GALLERY_SECTION = Pattern.compile("^(.*== Photos ==)(.*)$",Pattern.DOTALL);
    
    private static final String STORED_FILE = "StoredFile";
    private static final String STORED_LATITUDE = "StoredLatitude";
    private static final String STORED_LONGITUDE = "StoredLongitude";

    /** This gets declared at create time to save some calculation later. */
    private static int THUMB_DIMEN;
    /** The largest width we'll allow to be uploaded. */
    private static final int MAX_UPLOAD_WIDTH = 800;
    /** The largest height we'll allow to be uploaded. */
    private static final int MAX_UPLOAD_HEIGHT = 600;
    
    private static final int REQUEST_PICTURE = 0;
    
    private static final int INFOBOX_MARGIN = 16;
    private static final int INFOBOX_PADDING = 8;
    
    private Info mInfo;
//    private Location mLocation;
    
    /** The currently-displayed file. */
    private String mCurrentFile;
    
    /** The currently-displayed thumbnail. */
    private Bitmap mCurrentThumbnail;
    
    /** The current latitude and longitude. */
    private String mCurrentLatitude;
    private String mCurrentLongitude;
    
    private DecimalFormat mDistFormat = new DecimalFormat("###.######");
    
    private static Paint mBackgroundPaint;
    private static Paint mTextPaint;

    private static final String DEBUG_TAG = "WikiPictureEditor";
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        // Get some display metrics.  We need to scale the gallery thumbnails
        // accordingly, else they look too small on big screens and too big on
        // small screens.  We do this here to save calculations later, else
        // we'd be doing floating-point multiplication on EVERY SINGLE
        // THUMBNAIL, and we can't guarantee that won't be painful on every
        // Android phone.
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        THUMB_DIMEN = (int)(getResources().getDimensionPixelSize(R.dimen.nominal_icon_size) * metrics.density);
        
        Log.d(DEBUG_TAG, "Thumbnail dimensions: " + THUMB_DIMEN);

        mInfo = (Info)getIntent().getParcelableExtra(GeohashDroid.INFO);

        setContentView(R.layout.pictureselect);

        Button submitButton = (Button)findViewById(R.id.wikieditbutton);
        ImageButton galleryButton = (ImageButton)findViewById(R.id.GalleryButton);
        
        galleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Fire off the Gallery!
                startActivityForResult(
                        new Intent(
                                Intent.ACTION_PICK,
                                android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI),
                        REQUEST_PICTURE);
            }
        });

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
              // We don't want to let the Activity handle the dialog.  That WILL
              // cause it to show up properly and all, but after a configuration
              // change (i.e. orientation shift), it won't show or update any text
              // (as far as I know), as we can't reassign the handler properly.
              // So, we'll handle it ourselves.
              mProgress = ProgressDialog.show(WikiPictureEditor.this, "", "", true, true, WikiPictureEditor.this);
              mConnectionHandler = new PictureConnectionRunner(mProgressHandler, WikiPictureEditor.this);
              mWikiConnectionThread = new Thread(mConnectionHandler, "WikiConnectionThread");
              mWikiConnectionThread.start();
            }
          });
        
        // We can set the background on the thumbnail view right away, even if
        // it's not actually visible.
        ImageView thumbView = (ImageView)findViewById(R.id.ThumbnailImage);
        thumbView.setBackgroundResource(R.drawable.gallery_selected_default);
        thumbView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        
        // Now, let's see if we have anything retained...
        try {
            RetainedThings retain = (RetainedThings)getLastNonConfigurationInstance();
            if(retain != null) {
                // We have something retained!  Thus, we need to construct the
                // popup and update it with the right status, assuming the
                // thread's still going.
                if(retain.thread != null && retain.thread.isAlive()) {
                    mProgress = ProgressDialog.show(WikiPictureEditor.this, "", "", true, true, WikiPictureEditor.this);
                    mConnectionHandler = retain.handler;
                    mConnectionHandler.resetHandler(mProgressHandler);
                    mWikiConnectionThread = retain.thread;
                }
                
                // And in any event, put the image info back up.
                mCurrentFile = retain.currentFile;
                mCurrentThumbnail = retain.thumbnail;
                mCurrentLatitude = retain.latitude;
                mCurrentLongitude = retain.longitude;
                
                setThumbnail();
            } else {
                // If there was nothing to retain, maybe we've got a bundle.
                if(icicle != null) {
                    if(icicle.containsKey(STORED_FILE)) mCurrentFile = icicle.getString(STORED_FILE);
                    if(icicle.containsKey(STORED_LATITUDE)) mCurrentLatitude = icicle.getString(STORED_LATITUDE);
                    if(icicle.containsKey(STORED_LONGITUDE)) mCurrentLongitude = icicle.getString(STORED_LONGITUDE);
                    
                }
                
                // Rebuild it all in any event.
                buildThumbnail();
                setThumbnail();
            }
        } catch (Exception ex) {
            // If we got an exception, reset the thumbnail info with whatever
            // we have handy.
            buildThumbnail();
            setThumbnail();
        }
        
        // Rebuild the thumbnail and display it as need be.

    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        resetSubmitButton();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        // If the configuration changes (i.e. orientation shift), we want to
        // keep track of the thread we used to have, as well as the file and its
        // thumbnail.
        RetainedThings retain = new RetainedThings();
        
        if(mWikiConnectionThread != null && mWikiConnectionThread.isAlive()) {
            mDontStopTheThread  = true;
            retain.handler = mConnectionHandler;
            retain.thread = mWikiConnectionThread;
        }
        
        retain.currentFile = mCurrentFile;
        retain.thumbnail = mCurrentThumbnail;
        retain.latitude = mCurrentLatitude;
        retain.longitude = mCurrentLongitude;

        return retain;
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        
        outState.putString(STORED_FILE, mCurrentFile);
        outState.putString(STORED_LATITUDE, mCurrentLatitude);
        outState.putString(STORED_LONGITUDE, mCurrentLongitude);
    }

    private class PictureConnectionRunner extends WikiConnectionRunner {
      
        public PictureConnectionRunner(Handler h, Context c) {
            super(h, c);
        }

        public void run() {
            SharedPreferences prefs = getSharedPreferences(
                    GHDConstants.PREFS_BASE, 0);
            byte[] data = null;

            try {
                HttpClient httpclient = new DefaultHttpClient();

                String wpName = prefs
                        .getString(GHDConstants.PREF_WIKI_USER, "");
                if (!wpName.equals("")) {
                    addStatus(R.string.wiki_conn_login);
                    String wpPassword = prefs.getString(
                            GHDConstants.PREF_WIKI_PASS, "");
                    WikiUtils.login(httpclient, wpName, wpPassword);
                } else {
                    // This shouldn't happen.
                    error((String)getText(R.string.wiki_conn_anon_pic_error));
                    return;
                }

                String locationTag = "";
                // Hold on to whatever location we're going with.  This could
                // be useful later.
                Location sentLoc;
                
                CheckBox includelocation = (CheckBox)findViewById(R.id.includelocation);
                CheckBox stamplocation = (CheckBox)findViewById(R.id.stamplocation);
                try {
                    if(mCurrentLatitude == null || mCurrentLongitude == null
                            || mCurrentLatitude.trim().length() == 0
                            || mCurrentLongitude.trim().length() == 0)
                        throw new RuntimeException("Latitude or Longitude aren't defined in picture, control passes to catch block...");

                    // Get a location from the strings.  If any of this fails,
                    // fall to the exception handler.
                    double llat = Double.parseDouble(mCurrentLatitude);
                    double llon = Double.parseDouble(mCurrentLongitude);
                    sentLoc = new Location("");
                    sentLoc.setLatitude(llat);
                    sentLoc.setLongitude(llon);
                        
                    if(includelocation.isChecked()) {
                        // Parse the following out, first to a double, then
                        // back to a String using the formatter, just to make
                        // sure it doesn't get too long on us.
                        String lat = mLatLonFormat.format(sentLoc.getLatitude());
                        String lon = mLatLonFormat.format(sentLoc.getLongitude());
                        Log.d(DEBUG_TAG, "lat = " + lat + " lon = " + lon);
                        locationTag = " [http://www.openstreetmap.org/?lat="
                                + lat + "&lon=" + lon
                                + "&zoom=16&layers=B000FTF @" + lat + "," + lon
                                + "]";
                    }
                } catch (Exception ex) {
                    // If the picture itself doesn't have location data on it
                    // (that is, something threw an exception up there), go by
                    // the user's current location, if that's known.
                    addStatusAndNewline(R.string.wiki_conn_picture_location_unknown);
                    sentLoc = getLastLocation();
                    if(includelocation.isChecked()) {
                        if (sentLoc != null) {
                            locationTag = " [http://www.openstreetmap.org/?lat="
                                    + sentLoc.getLatitude()
                                    + "&lon="
                                    + sentLoc.getLongitude()
                                    + "&zoom=16&layers=B000FTF @"
                                    + mLatLonFormat.format(sentLoc.getLatitude())
                                    + ","
                                    + mLatLonFormat.format(sentLoc.getLongitude())
                                    + "]";
                        } else {
                            // Otherwise, we don't use anything at all.
                            addStatusAndNewline(R.string.wiki_conn_current_location_unknown);
                        }
                    }
                }

                addStatus(R.string.wiki_conn_shrink_image);

                // First, we want to scale the image to cut down on memory use
                // and upload time. The Geohashing wiki tends to frown upon
                // images over 150k, so scaling and compressing are the way to
                // go.
                Bitmap bitmap = BitmapTools
                        .createRatioPreservedDownscaledBitmapFromFile(
                                mCurrentFile, MAX_UPLOAD_WIDTH,
                                MAX_UPLOAD_HEIGHT, true);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                
                if(bitmap == null) {
                    error((String)getText(R.string.wiki_conn_pic_load_error));
                    return;
                }
                
                // Then, if need be, put an infobox on it.
                if(stamplocation.isChecked()) {
                    // Since we just got here from BitmapTools, this should be a
                    // read/write bitmap.
                    drawInfobox(bitmap, sentLoc);
                }
                
                // Now, compress it!
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, bytes);
                data = bytes.toByteArray();

                // Do recycling NOW, just to make sure we've booted it out of
                // memory as soon as possible.
                bitmap.recycle();
                System.gc();

                addStatusAndNewline(R.string.wiki_conn_done);

                addStatus(R.string.wiki_conn_upload_image);
                String now = new SimpleDateFormat("HH-mm-ss-SSS")
                        .format(new Date());
                String expedition = WikiUtils.getWikiPageName(mInfo);

                EditText editText = (EditText)findViewById(R.id.wikiedittext);

                String message = editText.getText().toString().trim()
                        + locationTag;

                String filename = expedition + "_" + now + ".jpg";
                String description = message + "\n\n" + WikiUtils.getWikiCategories(mInfo);
                
                HashMap<String, String> formfields = new HashMap<String, String>();
                
                // At this point, we need an edit token.  So, we'll try to get
                // the expedition page for our token.  See the MediaWiki API
                // documentation for the reasons why we have to do it this way.
                WikiUtils.getWikiPage(httpclient, expedition, formfields);
                WikiUtils.putWikiImage(httpclient, filename, description, formfields, data);

                addStatusAndNewline(R.string.wiki_conn_done);

                addStatus(R.string.wiki_conn_expedition_retrieving);
                addStatus(" " + expedition + "...");
                String page;

                page = WikiUtils.getWikiPage(httpclient, expedition,
                        formfields);
                if ((page == null) || (page.trim().length() == 0)) {
                    addStatusAndNewline(R.string.wiki_conn_expedition_nonexistant);
                    ;

                    // ok, let's create some.
                    addStatus(R.string.wiki_conn_expedition_creating);
                    WikiUtils.putWikiPage(httpclient, expedition,
                            WikiUtils.getWikiExpeditionTemplate(mInfo, WikiPictureEditor.this),
                            formfields);
                    addStatusAndNewline(R.string.wiki_conn_success);
                    addStatus(R.string.wiki_conn_expedition_reretrieving);
                    page = WikiUtils.getWikiPage(httpclient, expedition,
                            formfields);
                    addStatusAndNewline(R.string.wiki_conn_success);
                } else {
                    addStatusAndNewline(R.string.wiki_conn_success);
                }

                // Add in our message (same caveat as in WikiMessageEditor)...
                String summaryPrefix;
                if(mInfo.isRetroHash())
                    summaryPrefix = getText(R.string.wiki_post_picture_summary_retro).toString();
                else
                    summaryPrefix = getText(R.string.wiki_post_picture_summary).toString();
                
                formfields.put("summary", summaryPrefix + " " + message);

                
                String before = "";
                String after = "";
                
                Matcher galleryq = RE_GALLERY.matcher(page);
                if (galleryq.matches()) {
                    before = galleryq.group(1) + galleryq.group(2);
                    after = galleryq.group(3);
                } else {
                    // If we didn't match the gallery, find the Photos section
                    // and create a new gallery in it.
                    Matcher photosq = RE_GALLERY_SECTION.matcher(page);
                    if(photosq.matches()) {
                        before = photosq.group(1) + "\n<gallery>";
                        after = "</gallery>\n" + photosq.group(2);
                    } else {
                        // If we STILL can't find it, just tack it on to the end
                        // of the page.
                        before = page + "\n<gallery>";
                        after = "</gallery>\n";
                    }
                }

                String galleryentry = "\nImage:" + filename + " | " + message
                        + "\n";
                addStatus(R.string.wiki_conn_updating_gallery);
                WikiUtils.putWikiPage(httpclient, expedition, before
                        + galleryentry + after, formfields);
                addStatus(R.string.wiki_conn_success);

                finishDialog();
                
                reset();
                
                dismiss();
            } catch (OutOfMemoryError er) {
                // We CAN wind up with an OutOfMemoryError if, for instance, the
                // image is just too big for us to keep in memory. While we
                // generally want errors to cause this to fail completely, this
                // one we can turn into a message.
                Log.d(DEBUG_TAG, "ERROR: " + er.getMessage());
                error(er.getMessage());
            } catch (WikiException ex) {
                // Translate whatever the wiki exception gave us.
                String error = (String)getText(ex.getErrorTextId());
                Log.d(DEBUG_TAG, "WIKI EXCEPTION: " + error);
                error(error);
            } catch (Exception ex) {
                // Just display any other exceptions.
                Log.d(DEBUG_TAG, "EXCEPTION: " + ex.getMessage());
                if(ex.getMessage() != null)
                    error(ex.getMessage());
                else
                    error((String)getText(R.string.wiki_error_unknown));
                return;
            } finally {
                // In any event, clear the image data immediately, as we're done
                // with it.
                data = null;
            }

        }
    }
    
    /**
     * Since onRetainNonConfigurationInstance returns a plain ol' Object, this
     * just holds the pieces of data we're retaining.
     */
    private class RetainedThings {
        public Thread thread;
        public WikiConnectionRunner handler;
        public String currentFile;
        public Bitmap thumbnail;
        public String latitude;
        public String longitude;
    }
    
    protected void reset() {
        // Text gets wiped out.
        ((EditText)findViewById(R.id.wikiedittext)).setText("");
        
        // And the thumbnail gets reset (and the current selection is
        // forgotten).
        mCurrentThumbnail = null;
        mCurrentFile = null;
        mCurrentLongitude = null;
        mCurrentLatitude = null;
        setThumbnail();
        resetSubmitButton();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if(requestCode == REQUEST_PICTURE) {
            if(data != null) {
            
                Uri uri = data.getData();
                
                // If the uri's null, we failed.  Don't change anything.
                if(uri != null) {
                    Cursor cursor;
                    cursor = getContentResolver().query(uri, new String[] 
                         { MediaStore.Images.ImageColumns.DATA,
                            MediaStore.Images.ImageColumns.LATITUDE,
                            MediaStore.Images.ImageColumns.LONGITUDE }, 
                         null, null, null); 
                    cursor.moveToFirst(); 
                    mCurrentFile = cursor.getString(0);
                    // These two could very well be null or empty.  Nothing
                    // wrong with that.
                    mCurrentLatitude = cursor.getString(1);
                    mCurrentLongitude = cursor.getString(2);
                    cursor.close();
                }
            }

            // Always rebuild the thumbnail and reset submit, just in case.
            buildThumbnail();
            setThumbnail();
            resetSubmitButton();
            
            // We'll decode the bitmap at upload time so as not to keep a
            // potentially big chunky Bitmap around at all times.
        }
    }
    
    private void buildThumbnail() {
        // First things first, clear out the old thumbnail.
        mCurrentThumbnail = null;
        
        if(mCurrentFile == null) {
            return;
        }
        
        // We have the filename.  However, we're not guaranteed to have a
        // thumbnail generated yet, and we're not guaranteed to have the API
        // level required to force the thumbnail to be generated.  So, let's
        // make our own.
        Bitmap bitmap = BitmapTools
                .createRatioPreservedDownscaledBitmapFromFile(mCurrentFile,
                        THUMB_DIMEN, THUMB_DIMEN, false);
        
        // If the bitmap wound up null, we're sunk.
        if(bitmap == null) {
            // Reset this to null at this point!  It's entirely possible that we
            // got here from the user restoring the activity after leaving it,
            // during which time the file could have been deleted.
            mCurrentFile = null;
        } else {
            mCurrentThumbnail = bitmap;
        }
    }

    private void setThumbnail() {
        // SET!
        ImageView thumbView = (ImageView)findViewById(R.id.ThumbnailImage);
        
        if(mCurrentThumbnail != null) {
            // If we have a thumbnail, by all means, put it in!
            thumbView.setImageBitmap(mCurrentThumbnail);
            thumbView.setVisibility(View.VISIBLE);
        } else {
            // Otherwise, make it vanish entirely.  This is handy for, say,
            // clearing the thumbnail after an upload.
            thumbView.setVisibility(View.GONE);
        }
    }
    
    private void resetSubmitButton() {
        // Resets the submit button to whatever state and visibility it ought to
        // be.
        
        // First, check for username/password here.  That way, when we get back
        // from the settings screen, it'll update the message accordingly.
        Button submitButton = (Button)findViewById(R.id.wikieditbutton);
        TextView warning  = (TextView)findViewById(R.id.warningmessage);

        SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);
        String wpName = prefs.getString(GHDConstants.PREF_WIKI_USER, "");
        
        
        if ((wpName==null) || (wpName.trim().length() == 0)) {
            // If there's no username or password, the button is gone and the
            // warning goes up.
            submitButton.setEnabled(false);
            submitButton.setVisibility(View.GONE);
            warning.setVisibility(View.VISIBLE);
        } else if(mCurrentFile == null || mCurrentFile.trim().length() == 0) {
            // If there IS a username or password, but there's no currently
            // selected image, the button is visible but disabled.
            submitButton.setEnabled(false);
            submitButton.setVisibility(View.VISIBLE);
            warning.setVisibility(View.GONE);
        } else {
            // Otherwise, the button is visible and enabled.
            submitButton.setEnabled(true);
            submitButton.setVisibility(View.VISIBLE);
            warning.setVisibility(View.GONE);
        }
    }

    @Override
    protected void doDismiss() {
        super.doDismiss();
        
        // On success, we want to clear out the current selection.  So,
        // let's do just that.
        mCurrentThumbnail = null;
        mCurrentFile = null;
        mCurrentLongitude = null;
        mCurrentLatitude = null;
        setThumbnail();
        resetSubmitButton();
    }
    
    private void drawInfobox(Bitmap bm, Location loc) {
        // First, we need to draw something.  Get a Canvas.
        Canvas c = new Canvas(bm);
        
        // Now, draw!  We want to use the same colors as the Infobox uses.
        makePaints();
        
        if(loc != null) {
            // Assemble all our data.  Our four strings will be the final
            // destination, our current location, and the distance.
            String infoTo = getString(R.string.infobox_final) + " " + UnitConverter.makeFullCoordinateString(this, mInfo.getFinalLocation(), false, UnitConverter.OUTPUT_LONG);
            String infoYou = getString(R.string.infobox_you) + " " + UnitConverter.makeFullCoordinateString(this, loc, false, UnitConverter.OUTPUT_LONG);
            String infoDist = getString(R.string.infobox_dist) + " " + UnitConverter.makeDistanceString(this, mDistFormat, mInfo.getDistanceInMeters(loc));
            
            // Then, to the render method!
            String[] strings = {infoTo, infoYou, infoDist};
            drawStrings(strings, c, mTextPaint, mBackgroundPaint);
        } else {
            // Otherwise, just throw up an unknown.
            String[] strings = {getString(R.string.location_unknown)};
            drawStrings(strings, c, mTextPaint, mBackgroundPaint);
        }
    }
    
    private void makePaints() {
        // These are for efficiency's sake so we don't rebuild paints uselessly.
        if(mBackgroundPaint == null) {
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setStyle(Style.FILL);
            mBackgroundPaint.setColor(getResources().getColor(R.color.infobox_background));
        }
        
        if(mTextPaint == null) {
            mTextPaint = new Paint();
            mTextPaint.setColor(getResources().getColor(R.color.infobox_text));
            mTextPaint.setTextSize(getResources().getDimension(R.dimen.infobox_picture_fontsize));
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
