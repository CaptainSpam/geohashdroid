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
import android.app.Dialog;
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
public class WikiPictureEditor extends Activity {

    private static Pattern re_gallery     = Pattern.compile("^(.*<gallery[^>]*>)(.*?)(</gallery>.*)$",Pattern.DOTALL);

    private Button submitButton;
    private CheckBox includeLocation;
    private CheckBox includeTimestamp;
    private EditText editText;
    private Gallery gallery;
    private ProgressDialog progress;    

    private HttpClient httpclient;
    private String pagename;
    protected WikiConnectionHandler connectionHandler;
    
    private Cursor cursor;
    private int column_index;
    private Context context;

    private static Info mInfo;
    private static Location mLocation;
    private HashMap<String, String> formfields;

    static final int PROGRESS_DIALOG = 0;
    static final String STATUS_DISMISS = "Done.";
    static final String TAG = "PictureEditor";

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
        Log.d(TAG, "proj.length = "+proj.length);
        for (int i=0;i<proj.length;i++) 
          Log.d(TAG, "proj."+i+"  "+proj[i]);
        cursor = managedQuery( MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI, proj, null, null, null);
        Log.d(TAG, "cursor = "+cursor);
        if (cursor!=null) {
          column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Thumbnails._ID); 
          Log.d(TAG, "column_index = "+column_index);
        }

        gallery      = (Gallery)findViewById(R.id.gallery);
        submitButton = (Button)findViewById(R.id.wikieditbutton);
        editText     = (EditText)findViewById(R.id.wikiedittext);

        SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);
        TextView warning  = (TextView)findViewById(R.id.warningmessage);
        String wpName = prefs.getString(GHDConstants.PREF_WIKI_USER, "");
        if ((wpName==null) || (wpName.trim()=="")) {
          submitButton.setEnabled(false);
          submitButton.setVisibility(View.GONE);
          warning.setText("Posting images anonymously is not allowed. Pleare register an user name in the wiki first.");
        }

        gallery.setAdapter(new ImageAdapter(this));
                
        submitButton.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            showDialog(PROGRESS_DIALOG);
          }
        });
    }

    class ImageAdapter extends BaseAdapter {
        int mGalleryItemBackground;

        ImageAdapter(Context c) {
            context = c;
            TypedArray a = obtainStyledAttributes(R.styleable.Gallery1);
            mGalleryItemBackground = a.getResourceId(
                    R.styleable.Gallery1_android_galleryItemBackground, 0);
            a.recycle();
        }

        public int getCount() {
          return cursor==null?0:cursor.getCount();
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
          ImageView i = new ImageView(context);
          if (convertView == null) {
               cursor.moveToPosition(position);
                    int id = cursor.getInt(column_index);
                    i.setImageURI(Uri.withAppendedPath(MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI, ""+id));
                    i.setScaleType(ImageView.ScaleType.FIT_XY);
                    i.setLayoutParams(new Gallery.LayoutParams(140, 140));
                    // The preferred Gallery item background
                    i.setBackgroundResource(mGalleryItemBackground);
          }
          return i;
        }
    } 

    final Handler progressHandler = new Handler() {
      public void handleMessage(Message msg) {
        String status = msg.getData().getString("status");
        progress.setMessage(status);
        if (status.equals(STATUS_DISMISS)) {
          dismissDialog(PROGRESS_DIALOG);
        }
      }
    };

    protected Dialog onCreateDialog(int id) {
      if (id==PROGRESS_DIALOG) {
        progress = new ProgressDialog(WikiPictureEditor.this);
        connectionHandler = new WikiConnectionHandler(progressHandler);
        connectionHandler.start();
        return progress;
      } else {
        return null;
      }
    }
    
    class WikiConnectionHandler extends Thread {
      Handler handler;
      private String oldstatus="";
      
      WikiConnectionHandler(Handler h) {
        this.handler = h;
      }

      protected void setStatus(String status) {
        Message msg = handler.obtainMessage();
        Bundle b = new Bundle();
        b.putString("status",status);
        msg.setData(b);
        handler.sendMessage(msg);
      } 
      protected void addStatus(String status) {
        oldstatus = oldstatus + status;
        setStatus(oldstatus);
      }
      protected void dismiss() {
        setStatus(STATUS_DISMISS);
      } 


      public void run() { 
        String error = null;
        SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);

        HttpClient httpclient = null;
        try {
          httpclient = new DefaultHttpClient();
        } catch (Exception ex) {
          addStatus("Connection failed.\n"+ex.getMessage());
          return;
        }

        String wpName = prefs.getString(GHDConstants.PREF_WIKI_USER, "");
        if (!wpName.equals("")) {
          addStatus("Attempting login...");
          String wpPassword = prefs.getString(GHDConstants.PREF_WIKI_PASS, "");
          try {
            String fail = WikiUtils.login(httpclient, wpName, wpPassword);
            if (fail != WikiUtils.LOGIN_GOOD) {
              addStatus(fail+"\n");
              return;
            } else {
              addStatus("good.\n");
            }
          } catch (Exception ex) {
            addStatus("failed.\n"+ex.getMessage());
            return;
          }
        } else {
          addStatus("Cannot upload pictures as anonymous.\n");
          return;
        }

        addStatus("Compressing image...");
        byte[] data = null;
        
        String locationTag = "";
        
        try {
          int position = gallery.getSelectedItemPosition();
          cursor.moveToPosition(position);
          int id = cursor.getInt(column_index);
          Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ""+id);
          
          try {
            int latcol =  cursor.getColumnIndexOrThrow(MediaStore.Images.Media.LATITUDE); 
            int loncol =  cursor.getColumnIndexOrThrow(MediaStore.Images.Media.LONGITUDE); 
            String lat = cursor.getString(latcol);
            String lon = cursor.getString(loncol);
            Log.d(TAG, "lat = "+lat+" lon = "+lon);            
            addStatus("lat = "+lat+" lon = "+lon); //DEBUG also
            locationTag = " [http://www.openstreetmap.org/?lat="+lat+"&lon="+lon+"&zoom=16&layers=B000FTF @"+lat+","+lon+"]";
          } catch (Exception ex) {
            addStatus("Picture location unknown.\n");
            if (mLocation != null) {
              locationTag = "@ "+mLocation.toString();
              addStatus("Current location: "+mLocation.toString()+"\n");
            }
          }
        
          Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
          ByteArrayOutputStream bytes = new ByteArrayOutputStream();

          bitmap.compress(Bitmap.CompressFormat.JPEG, 75, bytes);
          data = bytes.toByteArray();
        } catch (Exception ex) {
          addStatus("failed. "+ex.getMessage());
          return;
        }
        addStatus("done.\n");
        
        addStatus("Uploading image...");
        String date = new SimpleDateFormat("yyyy-MM-dd").format(mInfo.getCalendar().getTime());
        String now  = new SimpleDateFormat("HH-mm-ss-SSS").format(new Date());
        Graticule grat = mInfo.getGraticule();
        String lat  = grat.getLatitudeString();
        String lon  = grat.getLongitudeString();
        String expedition = date+"_"+lat+"_"+lon;
        
        String message = editText.getText().toString().trim()+locationTag;
        
        String filename = expedition+"_"+now+".jpg";
        String description = message+"\n\n"+
                             "[[Category:Meetup on "+date+"]]\n" +
                             "[[Category:Meetup in "+lat+" "+lon+"]]";

        try {
          WikiUtils.putWikiImage(httpclient, filename, description, data);
        } catch (Exception ex) {
          addStatus("failed. "+ex.getMessage());
          return;
        }
        addStatus("done.\n");
        
        addStatus("Retrieving expedition "+expedition+"...");
        String page;
        try {
          formfields=new HashMap<String,String>();        
          page = WikiUtils.getWikiPage(httpclient, expedition, formfields);
          if ((page==null) || (page.trim().length()==0)) {
            addStatus("non-existant.\n");

            //ok, let's create some.
            addStatus("Creating expedition page...");
            try {
              WikiUtils.putWikiPage(httpclient, expedition, "{{subst:Expedition|lat="+lat+"|lon="+lon+"|date="+date+"}}", formfields);
              addStatus("done.\n");
            } catch (Exception ex) {
              addStatus("failed.\n"+ex.getMessage());
              return;
            }
            addStatus("Re-retrieving expedition...");
            try {
              page = WikiUtils.getWikiPage(httpclient, expedition, formfields);
              addStatus("fetched.\n");
            } catch (Exception ex) {
              addStatus("failed.\n"+ex.getMessage());
              return;
            }
          } else {
            addStatus("fetched.\n");
          }

          String before = "";
          String after  = "";
            
          Matcher galleryq = re_gallery.matcher(page);
          if (galleryq.matches()) {
            before = galleryq.group(1)+galleryq.group(2);
            after  = galleryq.group(3);
          } else {
            before = page+"\n<gallery>";
            after  = "</gallery>\n";
          }

          String galleryentry = "\nImage:"+filename+" | "+message+"\n";
          addStatus("Updating gallery...");
          WikiUtils.putWikiPage(httpclient, expedition, before+galleryentry+after, formfields);
          addStatus("Done.\n");
        } catch (Exception ex) {
          error = "failed.\n"+ex.getMessage();
        }

        dismiss();
      }
  }
}