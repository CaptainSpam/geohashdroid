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
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Gallery;
import android.widget.BaseAdapter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;

import android.provider.MediaStore;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

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

    /** The medium-density thumbnail dimensions.  This gets scaled. */
    private static final int NOMINAL_THUMB_DIMEN = 140;
    /** This gets declared at create time to save some calculation later. */
    private static int THUMB_DIMEN;
    
    private static final int REQUEST_PICTURE = 0;
    
    private Cursor mCursor;

    private Info mInfo;
    private Location mLocation;
    
    /** The currently-displayed file. */
    private String mCurrentFile;
    
    /** The currently-displayed thumbnail. */
    private Bitmap mThumbnail;

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
        THUMB_DIMEN = (int)(NOMINAL_THUMB_DIMEN * metrics.density);

        mInfo = (Info)getIntent().getParcelableExtra(GeohashDroid.INFO);
        
        double lat = getIntent().getDoubleExtra(GeohashDroid.LATITUDE, 200);
        double lon = getIntent().getDoubleExtra(GeohashDroid.LONGITUDE, 200);
        
        // If either of those were invalid (that is, 200), we don't have a
        // location.  If they're both valid, we do have one.
        if(lat > 90 || lat < -90 || lon > 180 || lon < -180)
            mLocation = null;
        else {
            mLocation = new Location((String)null);
            mLocation.setLatitude(lat);
            mLocation.setLongitude(lon);
        }

        setContentView(R.layout.pictureselect);

        String [] proj = {MediaStore.Images.Media.MINI_THUMB_MAGIC,
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.LATITUDE,
                MediaStore.Images.Media.LONGITUDE};
        // This is not-equals because that returns zero if it IS from Camera,
        // which sorts it BEFORE everything else, which returns one.
        // TODO: Does this work across all languages?  That is, if we're using
        // a German phone, will this show up as "Kamera"?
        String order = MediaStore.Images.Media.BUCKET_DISPLAY_NAME + " != 'Camera', "
            + MediaStore.Images.Media.BUCKET_ID + ","
            + MediaStore.Images.Media.DATE_TAKEN + " DESC,"
            + MediaStore.Images.Media.DATE_ADDED + " DESC";
        mCursor = managedQuery( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null, order);

//        Gallery gallery = (Gallery)findViewById(R.id.gallery);
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

//        gallery.setAdapter(new ImageAdapter(this));

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
//        TypedArray a = obtainStyledAttributes(R.styleable.Gallery1);
//        thumbView.setBackgroundResource(a.getResourceId(
//                R.styleable.Gallery1_android_galleryItemBackground, 0));
//        a.recycle();
        
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
            }
        } catch (Exception ex) {}
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Check for username/password here.  That way, when we get back from
        // the settings screen, it'll update the message accordingly.
        Button submitButton = (Button)findViewById(R.id.wikieditbutton);

        SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);
        TextView warning  = (TextView)findViewById(R.id.warningmessage);
        String wpName = prefs.getString(GHDConstants.PREF_WIKI_USER, "");
        if ((wpName==null) || (wpName.trim().length() == 0)) {
            submitButton.setEnabled(false);
            submitButton.setVisibility(View.GONE);
            warning.setVisibility(View.VISIBLE);
        } else {
            submitButton.setEnabled(true);
            submitButton.setVisibility(View.VISIBLE);
            warning.setVisibility(View.GONE);
        }
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        // If the configuration changes (i.e. orientation shift), we want to
        // keep track of the thread we used to have.  That'll be used to
        // populate the new popup next time around, if need be.
        if(mWikiConnectionThread != null && mWikiConnectionThread.isAlive()) {
            mDontStopTheThread  = true;
            RetainedThings retain = new RetainedThings();
            retain.handler = mConnectionHandler;
            retain.thread = mWikiConnectionThread;
            return retain;
        } else {
            return null;
        }
    }

    private class ImageAdapter extends BaseAdapter {
        private int mGalleryItemBackground;
        private Context mContext;

        public ImageAdapter(Context c) {
            mContext = c;
//            TypedArray a = obtainStyledAttributes(R.styleable.Gallery1);
//            mGalleryItemBackground = a.getResourceId(
//                    R.styleable.Gallery1_android_galleryItemBackground, 0);
//            a.recycle();
        }

        public int getCount() {
          return mCursor==null ? 0 : mCursor.getCount();
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            mCursor.moveToPosition(position);
            return mCursor.getInt(mCursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            Log.d(DEBUG_TAG, "getView for " + position);
          ImageView i = new ImageView(mContext);
          if (convertView == null) {
               mCursor.moveToPosition(position);
               // TODO: There HAS to be a better way to do this.
               // With the image ID in hand, we should be able to query the
               // thumbnail provider for the thumbnail ID, which we can then
               // retrieve.
               int id = mCursor.getInt(mCursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns._ID));
               
               String proj[] = {MediaStore.Images.Thumbnails._ID};
               String where = MediaStore.Images.Thumbnails.IMAGE_ID + " = " + id
                   + " AND " + MediaStore.Images.Thumbnails.KIND + " = " + MediaStore.Images.Thumbnails.MINI_KIND;
               Cursor thumber = managedQuery(MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI, proj, where, null, null);
               
               if(!thumber.moveToFirst())
                   Log.w(DEBUG_TAG, "Couldn't find thumbnail for image " + id);
               else {
                   int thumbid = thumber.getInt(thumber.getColumnIndexOrThrow(MediaStore.Images.Thumbnails._ID));
                   
                    i.setImageURI(Uri.withAppendedPath(MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI, ""+thumbid));
                    i.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    i.setLayoutParams(new Gallery.LayoutParams(THUMB_DIMEN, THUMB_DIMEN));
                    // The preferred Gallery item background
                    i.setBackgroundResource(mGalleryItemBackground);
               }
               thumber.close();
          }
          return i;
        }
    }
    
    private class PictureConnectionRunner extends WikiConnectionRunner {
      
        public PictureConnectionRunner(Handler h, Context c) {
            super(h, c);
        }

        public void run() {
            SharedPreferences prefs = getSharedPreferences(
                    GHDConstants.PREFS_BASE, 0);
            Uri uri;
            byte[] data = null;

            try {

                // Before we do anything, grab the image from the mCursor. If we
                // get a configuration change, that mCursor will be invalid.
//                Gallery gallery = (Gallery)findViewById(R.id.gallery);
//                int position = gallery.getSelectedItemPosition();
//                mCursor.moveToPosition(position);
//                int id = mCursor
//                        .getInt(mCursor
//                                .getColumnIndexOrThrow(MediaStore.Images.Media._ID));
//                uri = Uri.withAppendedPath(
//                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "" + id);
                int id = -1;
                uri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "" + -1);
                if(id == -1)
                {
                    error("You can't send images yet.");
                    return;
                }
                
                Log.d(DEBUG_TAG, "URI: " + uri.toString());

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

                CheckBox includelocation = (CheckBox)findViewById(R.id.includelocation);
                if (includelocation.isChecked()) {
                    try {
                        // First, see if the picture itself has location data.
                        int latcol = mCursor
                                .getColumnIndexOrThrow(MediaStore.Images.Media.LATITUDE);
                        int loncol = mCursor
                                .getColumnIndexOrThrow(MediaStore.Images.Media.LONGITUDE);
                        // Check these just to make sure.
                        String rawLat = mCursor.getString(latcol);
                        String rawLon = mCursor.getString(loncol);
                        
                        if(rawLat == null || rawLon == null)
                            throw new RuntimeException("Latitude or Longitude aren't defined in picture, control passes to catch block...");
                        
                        // Parse the following out, first to a double, then
                        // back to a String using the formatter, just to make
                        // sure it doesn't get too long on us.
                        String lat = mLatLonFormat.format(Double.parseDouble(rawLat));
                        String lon = mLatLonFormat.format(Double.parseDouble(rawLon));
                        Log.d(DEBUG_TAG, "lat = " + lat + " lon = " + lon);
                        locationTag = " [http://www.openstreetmap.org/?lat="
                                + lat + "&lon=" + lon
                                + "&zoom=16&layers=B000FTF @" + lat + "," + lon
                                + "]";
                    } catch (Exception ex) {
                        // If the picture itself doesn't have location data on
                        // it (that is, something threw an exception up there),
                        // go by the user's current location, if that's known.
                        addStatusAndNewline(R.string.wiki_conn_picture_location_unknown);
                        if (mLocation != null) {
                            locationTag = " [http://www.openstreetmap.org/?lat="
                                    + mLocation.getLatitude()
                                    + "&lon="
                                    + mLocation.getLongitude()
                                    + "&zoom=16&layers=B000FTF @"
                                    + mLatLonFormat.format(mLocation.getLatitude())
                                    + ","
                                    + mLatLonFormat.format(mLocation.getLongitude())
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
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                        getContentResolver(), uri);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();

                // The max we'll allow is 800x600, which should REALLY help with
                // the filesize (TODO: tweak this). If both dimensions are
                // smaller than that, we can let it go.
                if (bitmap.getHeight() > 600 || bitmap.getWidth() > 800) {
                    // So, we determine how we're going to scale this, mostly
                    // because there's no method in Bitmap to maintain aspect
                    // ratio for us. It's either going to wind up with a width
                    // of 800 or a height of 600 (or both).
                    double scaledByWidthRatio = 800.0 / bitmap.getWidth();
                    double scaledByHeightRatio = 600.0 / bitmap.getHeight();

                    int newWidth = bitmap.getWidth();
                    int newHeight = bitmap.getHeight();

                    if (bitmap.getHeight() * scaledByWidthRatio <= 600) {
                        // Scale it by making the width 800, as scaling the
                        // height by the same amount makes it less than or equal
                        // to 600.
                        newWidth = 800;
                        newHeight = (int)(bitmap.getHeight() * scaledByWidthRatio);
                    } else {
                        // Otherwise, go by making the height 600.
                        newWidth = (int)(bitmap.getWidth() * scaledByHeightRatio);
                        newHeight = 600;
                    }

                    // Now, do the scaling! GC will take care of the bitmap
                    // we're about to replace. I hope.
                    bitmap = Bitmap.createScaledBitmap(bitmap, newWidth,
                            newHeight, true);
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
                // TODO: This only applies to 1.16 MediaWikis, so we can't do
                // this just yet.
//                WikiUtils.getWikiPage(httpclient, expedition, mFormfields);
//                WikiUtils.putWikiImage(httpclient, filename, description, mFormfields, data);

                WikiUtils.putWikiImage(httpclient, filename, description, data);
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
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if(requestCode == REQUEST_PICTURE) {
            if(data == null) return;
            
            Uri uri = data.getData();
            
            // If the uri's null, we failed.  Don't change anything.
            if(uri != null) {
                Cursor cursor;
                cursor = getContentResolver().query(uri, new String[] 
                     { android.provider.MediaStore.Images.ImageColumns.DATA }, 
                     null, null, null); 
                cursor.moveToFirst(); 
                mCurrentFile = cursor.getString(0); 
                cursor.close();

                // We have the filename.  However, we're not guaranteed to have
                // a thumbnail generated yet, and we're not guaranteed to have
                // the API level required to force the thumbnail to be
                // generated.  So, let's make our own.
                Bitmap bitmap = BitmapFactory.decodeFile(mCurrentFile);
                
                // If the bitmap wound up null, we're sunk.
                if(bitmap == null) return;
                
                // Scale the bitmap for thumbnail size, if needed.
                Bitmap thumbie = BitmapTools.createRatioPreservedDownScaledBitmap(bitmap, THUMB_DIMEN, THUMB_DIMEN);
                
                if(thumbie != null)
                    mThumbnail = thumbie;
                else
                    mThumbnail = bitmap;
                
                setThumbnail();
                
                // We'll decode the bitmap at upload time so as not to keep a
                // potentially big chunky Bitmap around at all times.
            }
        }
    }

    private void setThumbnail() {
        // SET!
        ImageView thumbView = (ImageView)findViewById(R.id.ThumbnailImage);
        
        if(mThumbnail != null) {
            // If we have a thumbnail, by all means, put it in!
            thumbView.setImageBitmap(mThumbnail);
            thumbView.setVisibility(View.VISIBLE);
        } else {
            // Otherwise, make it vanish entirely.  This is handy for, say,
            // clearing the thumbnail after an upload.
            thumbView.setVisibility(View.GONE);
        }
    }
}
