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
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Gallery;
import android.widget.BaseAdapter;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;

import android.provider.MediaStore;
import android.database.Cursor;
import android.graphics.Bitmap;

import android.util.Log;
import android.location.Location;

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

    private static final Pattern RE_GALLERY = Pattern.compile("^(.*<gallery[^>]*>)(.*?)(</gallery>.*)$",Pattern.DOTALL);   

    private Cursor mCursor;

    private static Info mInfo;
    private static Location mLocation;
    private HashMap<String, String> mFormfields;

    static final String DEBUG_TAG = "WikiPictureEditor";
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mInfo = (Info)getIntent().getSerializableExtra(GeohashDroid.INFO);
        
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

        String [] proj = {
        		MediaStore.Images.Thumbnails._ID,
		        MediaStore.Images.Thumbnails.IMAGE_ID,
		        MediaStore.Images.Thumbnails.KIND };
        String where = MediaStore.Images.Thumbnails.KIND + "=" + MediaStore.Images.Thumbnails.MINI_KIND;

        mCursor = managedQuery( MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI, proj, where, null, null);

        Gallery gallery = (Gallery)findViewById(R.id.gallery);
        Button submitButton = (Button)findViewById(R.id.wikieditbutton);

        SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);
        TextView warning  = (TextView)findViewById(R.id.warningmessage);
        String wpName = prefs.getString(GHDConstants.PREF_WIKI_USER, "");
        if ((wpName==null) || (wpName.trim().length() == 0)) {
          submitButton.setEnabled(false);
          submitButton.setVisibility(View.GONE);
          warning.setVisibility(View.VISIBLE);
        }

        gallery.setAdapter(new ImageAdapter(this));

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
            TypedArray a = obtainStyledAttributes(R.styleable.Gallery1);
            mGalleryItemBackground = a.getResourceId(
                    R.styleable.Gallery1_android_galleryItemBackground, 0);
            a.recycle();
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
            ImageView i = new ImageView(mContext);
            if (convertView == null) {
                mCursor.moveToPosition(position);
              
                int id = mCursor.getInt(mCursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns._ID));
                i.setImageURI(Uri.withAppendedPath(MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI, ""+id));
                i.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                i.setLayoutParams(new Gallery.LayoutParams(140, 140));
                // The preferred Gallery item background
                i.setBackgroundResource(mGalleryItemBackground);
          }
          return i;
        }
    }
    
    private class PictureConnectionRunner extends WikiConnectionRunner {
      
        public PictureConnectionRunner(Handler h, Context c) {
            super(h, c);
        }

      public void run() { 
    	try {
          SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);

          HttpClient httpclient = null;
        
          Uri uri;
        
          // Before we do anything, grab the image from the mCursor.  If we get a
          // configuration change, that mCursor will be invalid.
          try {
            Gallery gallery = (Gallery)findViewById(R.id.gallery);
            int position = gallery.getSelectedItemPosition();
            mCursor.moveToPosition(position);
            int id = mCursor.getInt(mCursor.getColumnIndexOrThrow(MediaStore.Images.Thumbnails.IMAGE_ID));
            uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "" + id);
          } catch (Exception ex) {
            Log.d(DEBUG_TAG, "EXCEPTION: " + ex.getMessage());
            error(ex.getMessage());
            return;
          }
        
        
          try {
            httpclient = new DefaultHttpClient();
          } catch (Exception ex) {
            Log.d(DEBUG_TAG, "EXCEPTION: " + ex.getMessage());
            error(ex.getMessage());
            return;
          }

          String wpName = prefs.getString(GHDConstants.PREF_WIKI_USER, "");
          if (!wpName.equals("")) {
              addStatus(R.string.wiki_conn_login);
            String wpPassword = prefs.getString(GHDConstants.PREF_WIKI_PASS, "");
            try {
              String fail = WikiUtils.login(httpclient, wpName, wpPassword);
              if (fail != WikiUtils.LOGIN_GOOD) {
                  error(fail);
                  return;
                } else {
                  addStatusAndNewline(R.string.wiki_conn_success);
              }
            } catch (Exception ex) {
                Log.d(DEBUG_TAG, "EXCEPTION: " + ex.getMessage());
                error(ex.getMessage());
                return;
            }
          } else {
            addStatusAndNewline(R.string.wiki_conn_anon_pic_error);
            return;
          }

          byte[] data = null;
        
          String locationTag = "";
        
          try {
            CheckBox includelocation = (CheckBox)findViewById(R.id.includelocation);
            if(includelocation.isChecked()) {
              try {
                int latcol = mCursor.getColumnIndexOrThrow(MediaStore.Images.Media.LATITUDE); 
                int loncol = mCursor.getColumnIndexOrThrow(MediaStore.Images.Media.LONGITUDE);
                String lat = mCursor.getString(latcol);
                String lon = mCursor.getString(loncol);
                // TODO: This won't work right if the image doesn't have any
                // location data defined (the strings wind up as nulls).  Must
                // make sure that if this DOES wind up null, we throw an
                // exception to get to the catch statement.
                Log.d(DEBUG_TAG, "lat = "+lat+" lon = "+lon);
                locationTag = " [http://www.openstreetmap.org/?lat=" + lat + "&lon="
                  + lon + "&zoom=16&layers=B000FTF @" + lat + "," + lon + "]";
              } catch (Exception ex) {
                addStatusAndNewline(R.string.wiki_conn_picture_location_unknown);
                if (mLocation != null) {
                  locationTag = " [http://www.openstreetmap.org/?lat=" + mLocation.getLatitude()
                    + "&lon=" + mLocation.getLongitude() + "&zoom=16&layers=B000FTF @"
                    + mLocation.getLatitude() + "," + mLocation.getLongitude() + "]";
                } else {
                   addStatusAndNewline(R.string.wiki_conn_current_location_unknown);
                }
              }
            }
        
            addStatus(R.string.wiki_conn_shrink_image);
          
            // First, we want to scale the image to cut down on memory use and
            // upload time.  The Geohashing wiki tends to frown upon images over
            // 150k, so scaling and compressing are the way to go.
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
          
            // The max we'll allow is 800x600, which should REALLY help with the
            // filesize (TODO: tweak this). If both dimensions are smaller than
            // that, we can let it go.
            if(bitmap.getHeight() > 600 || bitmap.getWidth() > 800) {
                // So, we determine how we're going to scale this, mostly
                // because there's no method in Bitmap to maintain aspect ratio
                // for us.  It's either going to wind up with a width of 800 or
                // a height of 600 (or both).
                double scaledByWidthRatio = 800.0 / bitmap.getWidth();
                double scaledByHeightRatio = 600.0 / bitmap.getHeight();
                
                int newWidth = bitmap.getWidth();
                int newHeight = bitmap.getHeight();
                
                if(bitmap.getHeight() * scaledByWidthRatio <= 600) {
                    // Scale it by making the width 800, as scaling the height
                    // by the same amount makes it less than or equal to 600.
                    newWidth = 800;
                    newHeight = (int)(bitmap.getHeight() * scaledByWidthRatio);
                } else {
                    // Otherwise, go by making the height 600.
                    newWidth = (int)(bitmap.getWidth() * scaledByHeightRatio);
                    newHeight = 600;
                }
                
                // Now, do the scaling!  GC will take care of the bitmap we're
                // about to replace.  I hope.
                bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
            }

            // Now, compress it! 
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, bytes);
            data = bytes.toByteArray();
          
            // Do recycling NOW, just to make sure we've booted it out of memory
            // as soon as possible.
            bitmap.recycle();
          } catch (Exception ex) {
            Log.d(DEBUG_TAG, "EXCEPTION: " + ex.getMessage());
            error(ex.getMessage());
            return;
          }
          addStatusAndNewline(R.string.wiki_conn_done);
        
          addStatus(R.string.wiki_conn_upload_image);
          String date = new SimpleDateFormat("yyyy-MM-dd").format(mInfo.getCalendar().getTime());
          String now  = new SimpleDateFormat("HH-mm-ss-SSS").format(new Date());
          Graticule grat = mInfo.getGraticule();
          String lat  = grat.getLatitudeString();
          String lon  = grat.getLongitudeString();
          String expedition = date+"_"+lat+"_"+lon;
        
          EditText editText = (EditText)findViewById(R.id.wikiedittext);
        
          String message = editText.getText().toString().trim() + locationTag;
        
          String filename = expedition+"_"+now+".jpg";
          String description = message+"\n\n"+
                               "[[Category:Meetup on "+date+"]]\n" +
                               "[[Category:Meetup in "+lat+" "+lon+"]]";

          try {
            WikiUtils.putWikiImage(httpclient, filename, description, data);
          } catch (Exception ex) {
            Log.d(DEBUG_TAG, "EXCEPTION: " + ex.getMessage());
            error(ex.getMessage());
            return;
          } finally {
          	// In any event, clear the image data immediately, as we're done
            // with it.
            data = null;
          }
          addStatusAndNewline(R.string.wiki_conn_done);
        
          addStatus(R.string.wiki_conn_expedition_retrieving);
          addStatus(" " + expedition + "...");
          String page;
          try {
            mFormfields=new HashMap<String,String>();        
            page = WikiUtils.getWikiPage(httpclient, expedition, mFormfields);
            if ((page==null) || (page.trim().length()==0)) {
              addStatusAndNewline(R.string.wiki_conn_expedition_nonexistant);;

              //ok, let's create some.
              addStatus(R.string.wiki_conn_expedition_creating);
              try {
                WikiUtils.putWikiPage(httpclient, expedition, "{{subst:Expedition|lat="+lat+"|lon="+lon+"|date="+date+"}}", mFormfields);
                addStatusAndNewline(R.string.wiki_conn_success);
              } catch (Exception ex) {
                Log.d(DEBUG_TAG, "EXCEPTION: " + ex.getMessage());
                error(ex.getMessage());
                return;
              }
              addStatus(R.string.wiki_conn_expedition_reretrieving);
              try {
                page = WikiUtils.getWikiPage(httpclient, expedition, mFormfields);
                addStatusAndNewline(R.string.wiki_conn_success);
              } catch (Exception ex) {
                Log.d(DEBUG_TAG, "EXCEPTION: " + ex.getMessage());
                error(ex.getMessage());
                return;
              }
            } else {
              addStatusAndNewline(R.string.wiki_conn_success);
            }

            String before = "";
            String after  = "";
            
            Matcher galleryq = RE_GALLERY.matcher(page);
            if (galleryq.matches()) {
              before = galleryq.group(1)+galleryq.group(2);
              after  = galleryq.group(3);
            } else {
              before = page+"\n<gallery>";
              after  = "</gallery>\n";
            }

            String galleryentry = "\nImage:" + filename + " | " + message + "\n";
            addStatus(R.string.wiki_conn_updating_gallery);
            WikiUtils.putWikiPage(httpclient, expedition, before+galleryentry+after, mFormfields);
            addStatus(R.string.wiki_conn_success);
          } catch (Exception ex) {
            Log.d(DEBUG_TAG, "EXCEPTION: " + ex.getMessage());
            error(ex.getMessage());
            return;
          }
          
          dismiss();
        } catch (OutOfMemoryError er) {
    	  // We CAN wind up with an OutOfMemoryError if, for instance, the
    	  // image is just too big for us to keep in memory.  While we
    	  // generally want errors to cause this to fail completely, this
    	  // one we can turn into a message.
    	  Log.d(DEBUG_TAG, "ERROR: " + er.getMessage());
    	  error(er.getMessage());
        } catch (Exception ex) {
          Log.d(DEBUG_TAG, "EXCEPTION: " + ex.getMessage());
          error(ex.getMessage());
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

}
