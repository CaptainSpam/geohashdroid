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
import android.os.Message;
import android.app.Activity;
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
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
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
public class WikiPictureEditor extends Activity implements OnCancelListener {

    private static final Pattern RE_GALLERY = Pattern.compile("^(.*<gallery[^>]*>)(.*?)(</gallery>.*)$",Pattern.DOTALL);

    private ProgressDialog mProgress;    

    private WikiConnectionHandler mConnectionHandler;
    private Thread mWikiConnectionThread;
    
    private Cursor mCursor;
    private int column_index;

    private static Info mInfo;
    private static Location mLocation;
    private HashMap<String, String> mFormfields;

    private boolean mDontStopTheThread = false;

    static final int PROGRESS_DIALOG = 0;
    static final String STATUS_DISMISS = "Done.";
    static final String DEBUG_TAG = "WikiPictureEditor";


    private final Handler mProgressHandler = new Handler() {
        public void handleMessage(Message msg) {
          String status = (String)(msg.obj);
          mProgress.setMessage(status);
          if (status.equals(STATUS_DISMISS)) {
            mProgress.dismiss();
          }
        }
      };
    
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

        String [] proj = {MediaStore.Images.Thumbnails._ID}; 
        for (int i=0;i<proj.length;i++) 
        mCursor = managedQuery( MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI, proj, null, null, null);
        if (mCursor!=null) {
          column_index = mCursor.getColumnIndexOrThrow(MediaStore.Images.Thumbnails._ID); 
        }

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
              mConnectionHandler = new WikiConnectionHandler(mProgressHandler);
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

    class ImageAdapter extends BaseAdapter {
        int mGalleryItemBackground;
        Context mContext;

        ImageAdapter(Context c) {
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
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
          ImageView i = new ImageView(mContext);
          if (convertView == null) {
               mCursor.moveToPosition(position);
                    int id = mCursor.getInt(column_index);
                    i.setImageURI(Uri.withAppendedPath(MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI, ""+id));
                    i.setScaleType(ImageView.ScaleType.FIT_XY);
                    i.setLayoutParams(new Gallery.LayoutParams(140, 140));
                    // The preferred Gallery item background
                    i.setBackgroundResource(mGalleryItemBackground);
          }
          return i;
        }
    } 
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(!mDontStopTheThread && mWikiConnectionThread != null && mWikiConnectionThread.isAlive()) {
            // If we want to stop the thread (default, assuming this isn't a
            // configuration change), AND the thread is defined, AND it's still
            // alive, abort it via WikiUtils.  Otherwise, this IS a config
            // change, so keep the thread running.  We'll catch up with it on
            // the next run.
            mConnectionHandler.abort();
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        // If the dialog is canceled (that is, the user pressed Back; any other
        // manner just dismisses the dialog, not cancels it), we want to stop
        // the connection immediately.  When we call WikiUtils.abort(), that'll
        // abort the connection, which will cause it to throw exceptions all
        // over the place.  Fortunately, the thread that runs the connection
        // will catch just about everything and bail out of the thread once it
        // stops.
        if(mWikiConnectionThread != null && mWikiConnectionThread.isAlive())
            mConnectionHandler.abort();
    }
    
    private class WikiConnectionHandler implements Runnable {
      Handler handler;
      private String mOldStatus = "";
      
      WikiConnectionHandler(Handler h) {
        this.handler = h;
      }
      
      
      public void resetHandler(Handler h) {
          this.handler = h;
          setStatus(mOldStatus);
      }
      
      public void abort() {
          WikiUtils.abort();
      }

      private void setStatus(String status) {
          Message msg = handler.obtainMessage();
          msg.obj = status;
          handler.sendMessage(msg);
        } 
        private void addStatus(String status) {
          mOldStatus = mOldStatus + status;
          setStatus(mOldStatus);
        }
        private void addStatus(int resId) {
            addStatus(getText(resId).toString());
        }
        private void addStatusAndNewline(int resId) {
            addStatus(resId);
            addStatus("\n");
        }
        private void dismiss() {
          setStatus(STATUS_DISMISS);
        } 


      public void run() { 
        SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);

        HttpClient httpclient = null;
        
        Uri uri;
        
        // Before we do anything, grab the image from the mCursor.  If we get a
        // configuration change, that mCursor will be invalid.
        try {
          Gallery gallery = (Gallery)findViewById(R.id.gallery);
          int position = gallery.getSelectedItemPosition();
          mCursor.moveToPosition(position);
          int id = mCursor.getInt(column_index);
          uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ""+id);
        } catch (Exception ex) {
          Log.d(DEBUG_TAG, "EXCEPTION: " + ex.getMessage());
          addStatusAndNewline(R.string.wiki_conn_connection_failed);
          addStatus(ex.getMessage());
          return;
        }
        
        
        try {
          httpclient = new DefaultHttpClient();
        } catch (Exception ex) {
          Log.d(DEBUG_TAG, "EXCEPTION: " + ex.getMessage());
          addStatusAndNewline(R.string.wiki_conn_connection_failed);
          addStatus(ex.getMessage());
          return;
        }

        String wpName = prefs.getString(GHDConstants.PREF_WIKI_USER, "");
        if (!wpName.equals("")) {
            addStatus(R.string.wiki_conn_login);
          String wpPassword = prefs.getString(GHDConstants.PREF_WIKI_PASS, "");
          try {
            String fail = WikiUtils.login(httpclient, wpName, wpPassword);
            if (fail != WikiUtils.LOGIN_GOOD) {
                addStatus(fail+"\n");
                return;
              } else {
                addStatusAndNewline(R.string.wiki_conn_success);
            }
          } catch (Exception ex) {
              Log.d(DEBUG_TAG, "EXCEPTION: " + ex.getMessage());
              addStatusAndNewline(R.string.wiki_conn_failure);
              addStatus(ex.getMessage());
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
        
          addStatusAndNewline(R.string.wiki_conn_shrink_image);
          
          Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
          ByteArrayOutputStream bytes = new ByteArrayOutputStream();

          bitmap.compress(Bitmap.CompressFormat.JPEG, 75, bytes);
          data = bytes.toByteArray();
        } catch (Exception ex) {
          Log.d(DEBUG_TAG, "EXCEPTION: " + ex.getMessage());
          addStatusAndNewline(R.string.wiki_conn_failure);
          addStatus(ex.getMessage());
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
        
        String message = editText.getText().toString().trim()+locationTag;
        
        String filename = expedition+"_"+now+".jpg";
        String description = message+"\n\n"+
                             "[[Category:Meetup on "+date+"]]\n" +
                             "[[Category:Meetup in "+lat+" "+lon+"]]";

        try {
          WikiUtils.putWikiImage(httpclient, filename, description, data);
        } catch (Exception ex) {
          Log.d(DEBUG_TAG, "EXCEPTION: " + ex.getMessage());
          addStatusAndNewline(R.string.wiki_conn_failure);
          addStatus(ex.getMessage());
          return;
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
              addStatusAndNewline(R.string.wiki_conn_failure);
              addStatus(ex.getMessage());
              return;
            }
            addStatus(R.string.wiki_conn_expedition_reretrieving);
            try {
              page = WikiUtils.getWikiPage(httpclient, expedition, mFormfields);
              addStatusAndNewline(R.string.wiki_conn_success);
            } catch (Exception ex) {
              Log.d(DEBUG_TAG, "EXCEPTION: " + ex.getMessage());
              addStatusAndNewline(R.string.wiki_conn_failure);
              addStatus(ex.getMessage());
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

          String galleryentry = "\nImage:"+filename+" | "+message+"\n";
          addStatus(R.string.wiki_conn_updating_gallery);
          WikiUtils.putWikiPage(httpclient, expedition, before+galleryentry+after, mFormfields);
          addStatus(R.string.wiki_conn_success);
        } catch (Exception ex) {
          Log.d(DEBUG_TAG, "EXCEPTION: " + ex.getMessage());
          addStatusAndNewline(R.string.wiki_conn_failure);
          addStatus(ex.getMessage());
          return;
        }

        dismiss();
      }
  }
    
    
    /**
     * Since onRetainNonConfigurationInstance returns a plain ol' Object, this
     * just holds the pieces of data we're retaining.
     */
    private class RetainedThings {
        public Thread thread;
        public WikiConnectionHandler handler;
    }

}
