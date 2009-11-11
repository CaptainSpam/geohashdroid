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
import android.os.Message;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
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
public class WikiMessageEditor extends Activity {

    private static final Pattern RE_EXPEDITION  = Pattern.compile("^(.*)(==+ ?Expedition ?==+.*?)(==+ ?.*? ?==+.*?)$",Pattern.DOTALL);

    private Button submitButton;
    private EditText editText;
    private ProgressDialog progress;    

    protected WikiConnectionHandler connectionHandler;
    
    private static Info mInfo;
    private HashMap<String, String> mFormfields;

    static final int PROGRESS_DIALOG = 0;
    static final String STATUS_DISMISS = "Done.";
    static final String TAG = "MessageEditor";
    
    private static Location mLocation;
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        if (icicle != null && icicle.containsKey(GeohashDroid.INFO)) {
            mInfo = (Info)icicle.getSerializable(GeohashDroid.INFO);
        } else {
            mInfo = (Info)getIntent().getSerializableExtra(GeohashDroid.INFO);
        }
        if (icicle != null && icicle.containsKey(GeohashDroid.LOCATION)) {
            mLocation = (Location)icicle.getSerializable(GeohashDroid.LOCATION);
        } else {
            mLocation = (Location)getIntent().getSerializableExtra(GeohashDroid.LOCATION);
        }

        setContentView(R.layout.wikieditor);

        submitButton = (Button)findViewById(R.id.wikieditbutton);
        editText     = (EditText)findViewById(R.id.wikiedittext);

        SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);
        TextView warning = (TextView)findViewById(R.id.warningmessage);
        String wpName = prefs.getString(GHDConstants.PREF_WIKI_USER, "");
        if ((wpName == null) || (wpName.trim().length() == 0)) {
          warning.setText(R.string.wiki_editor_not_logged_in);
        }
        
        submitButton.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            showDialog(PROGRESS_DIALOG);
          }
        });
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
        progress = new ProgressDialog(WikiMessageEditor.this);
        connectionHandler = new WikiConnectionHandler(progressHandler);
        connectionHandler.start();
        return progress;
      } else {
        return null;
      }
    }
    
    class WikiConnectionHandler extends Thread {
      Handler handler;
      private String mOldStatus="";
      
      WikiConnectionHandler(Handler h) {
        this.handler = h;
      }

      protected void setStatus(String status) {
        Message msg = handler.obtainMessage();
        Bundle b = new Bundle();
        b.putString("status", status);
	msg.setData(b);
        handler.sendMessage(msg);
      } 
      protected void addStatus(String status) {
        mOldStatus = mOldStatus + status;
        setStatus(mOldStatus);
      }
      protected void dismiss() {
        setStatus(STATUS_DISMISS);
      } 

      public void run() { 
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
          addStatus("WARNING: Posting anonymously.\n");
        }

        String date = new SimpleDateFormat("yyyy-MM-dd").format(mInfo.getCalendar().getTime());
        Graticule grat = mInfo.getGraticule();
        String lat  = grat.getLatitudeString();
        String lon  = grat.getLongitudeString();
        String expedition = date+"_"+lat+"_"+lon;

        String locationTag = "";
        if (mLocation != null) {
          String pos = mLocation.getLatitude()+","+mLocation.getLongitude();
          locationTag = " [http://www.openstreetmap.org/?lat="+mLocation.getLatitude()+"&lon="+mLocation.getLongitude()+"&zoom=16&layers=B000FTF @"+pos+"]";
          addStatus("Current location: "+pos+"\n");
        } else {
          addStatus("Current location unknown.\n");
        }

        addStatus("Retrieving expedition "+expedition+"...");
        String page;
        try {
          mFormfields = new HashMap<String,String>();
          page = WikiUtils.getWikiPage(httpclient, expedition, mFormfields);
          if ((page==null) || (page.trim().length()==0)) {
            addStatus("non-existant.\n");

            //ok, let's create some.
            addStatus("Creating expedition page...");
            try {
              WikiUtils.putWikiPage(httpclient, expedition, "{{subst:Expedition|lat="+lat+"|lon="+lon+"|date="+date+"}}", mFormfields);
              addStatus("done.\n");
            } catch (Exception ex) {
              addStatus("failed.\n"+ex.getMessage());
              return;
            }
 
            addStatus("Re-retrieving expedition...");
            try {
              page = WikiUtils.getWikiPage(httpclient, expedition, mFormfields);
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
            
          Matcher expeditionq = RE_EXPEDITION.matcher(page);
          if (expeditionq.matches()) {
            before = expeditionq.group(1)+expeditionq.group(2);
            after  = expeditionq.group(3);
          } else {
            before = page;
          }
            
          String message = "\n*"+editText.getText().toString().trim()+"  -- ~~~"+locationTag+" ~~~~~\n";
            
          addStatus("Inserting message...");
          WikiUtils.putWikiPage(httpclient, expedition, before+message+after, mFormfields);
          addStatus("Done.\n");
         
            
        } catch (Exception ex) {
          addStatus("failed.\n"+ex.getMessage());
        }
        dismiss();
      }
  }
}