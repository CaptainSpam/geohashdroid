/**
 * WikiMessageEditor.java
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
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.content.Context;
import android.content.SharedPreferences;

import android.location.Location;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import java.util.HashMap;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.text.SimpleDateFormat;
/**
 * Displays an edit box and a send button, which shall upload the message
 * entered to the appropriate expedition page in the Geohashing wiki. 
 * 
 * @author Thomas Hirsch
 */
public class WikiMessageEditor extends WikiBaseActivity {

    private static final Pattern RE_EXPEDITION  = Pattern.compile("^(.*)(==+ ?Expedition ?==+.*?)(==+ ?.*? ?==+.*?)$",Pattern.DOTALL); 
    
    private static Info mInfo;
    private HashMap<String, String> mFormfields;

    static final String DEBUG_TAG = "MessageEditor";
    
    private static Location mLocation;
    
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

        setContentView(R.layout.wikieditor);

        Button submitButton = (Button)findViewById(R.id.wikieditbutton);

        SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);
        TextView warning = (TextView)findViewById(R.id.warningmessage);
        String wpName = prefs.getString(GHDConstants.PREF_WIKI_USER, "");
        if ((wpName == null) || (wpName.trim().length() == 0)) {
          warning.setVisibility(View.VISIBLE);
        }
        
        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
              // We don't want to let the Activity handle the dialog.  That WILL
              // cause it to show up properly and all, but after a configuration
              // change (i.e. orientation shift), it won't show or update any text
              // (as far as I know), as we can't reassign the handler properly.
              // So, we'll handle it ourselves.
              mProgress = ProgressDialog.show(WikiMessageEditor.this, "", "", true, true, WikiMessageEditor.this);
              mConnectionHandler = new MessageConnectionRunner(mProgressHandler, WikiMessageEditor.this);
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
                    mProgress = ProgressDialog.show(WikiMessageEditor.this, "", "", true, true, WikiMessageEditor.this);
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
            mDontStopTheThread = true;
            RetainedThings retain = new RetainedThings();
            retain.handler = mConnectionHandler;
            retain.thread = mWikiConnectionThread;
            return retain;
        } else {
            return null;
        }
    }
    
    private class MessageConnectionRunner extends WikiConnectionRunner {
      MessageConnectionRunner(Handler h, Context c) {
          super(h, c);
      }

      public void run() { 
        SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);

        HttpClient httpclient = null;
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
          addStatusAndNewline(R.string.wiki_conn_anon_warning);
        }

        String date = new SimpleDateFormat("yyyy-MM-dd").format(mInfo.getCalendar().getTime());
        Graticule grat = mInfo.getGraticule();
        String lat  = grat.getLatitudeString();
        String lon  = grat.getLongitudeString();
        String expedition = date+"_"+lat+"_"+lon;

        String locationTag = "";
        
        // Location!  Is the checkbox ticked (and do we have a location handy)?
        CheckBox includelocation = (CheckBox)findViewById(R.id.includelocation);
        if(includelocation.isChecked()) {
          if (mLocation != null) {
            String pos = mLocation.getLatitude()+","+mLocation.getLongitude();
            locationTag = " [http://www.openstreetmap.org/?lat=" + mLocation.getLatitude() + "&lon=" + mLocation.getLongitude() + "&zoom=16&layers=B000FTF @" + pos + "]";
            addStatus(R.string.wiki_conn_current_location);
            addStatus(" " + pos + "\n");
          } else {
            addStatusAndNewline(R.string.wiki_conn_current_location_unknown);
          }
        }

        addStatus(R.string.wiki_conn_expedition_retrieving);
        addStatus(" " + expedition + "...");
        String page;
        try {
          mFormfields = new HashMap<String,String>();
          page = WikiUtils.getWikiPage(httpclient, expedition, mFormfields);
          if ((page==null) || (page.trim().length()==0)) {
            addStatusAndNewline(R.string.wiki_conn_expedition_nonexistant);

            //ok, let's create some.
            addStatus(R.string.wiki_conn_expedition_creating);
            WikiUtils.putWikiPage(httpclient, expedition, "{{subst:Expedition|lat="+lat+"|lon="+lon+"|date="+date+"}}", mFormfields);
            addStatusAndNewline(R.string.wiki_conn_success);
 
            addStatus(R.string.wiki_conn_expedition_reretrieving);
            
            page = WikiUtils.getWikiPage(httpclient, expedition, mFormfields);
            addStatusAndNewline(R.string.wiki_conn_success);
          } else {
            addStatusAndNewline(R.string.wiki_conn_success);
          }
            
          String before = "";
          String after  = "";
            
          Matcher expeditionq = RE_EXPEDITION.matcher(page);
          if (expeditionq.matches()) {
            before = expeditionq.group(1)+expeditionq.group(2);
            after  = expeditionq.group(3);
          } else {
            before = page;
          }

          EditText editText = (EditText)findViewById(R.id.wikiedittext);
          
          CheckBox includetime = (CheckBox)findViewById(R.id.includetime);
          
          String message = "\n*"+editText.getText().toString().trim()+"  -- ~~~"
            + locationTag + (includetime.isChecked() ? " ~~~~~" : "") + "\n";
            
          addStatus(R.string.wiki_conn_insert_message);
          WikiUtils.putWikiPage(httpclient, expedition, before+message+after, mFormfields);
          addStatusAndNewline(R.string.wiki_conn_done);
         
          dismiss();
        } catch (Exception ex) {
          Log.d(DEBUG_TAG, "EXCEPTION: " + ex.getMessage());
          error(ex.getMessage());
          return;
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