/**
 * WikiMessageEditor.java
 * Copyright (C)2009 Thomas Hirsch
 * Geohashdroid Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid.wiki;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.util.UnitConverter;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.tools.DateTools;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
/**
 * Displays an edit box and a send button, which shall upload the message
 * entered to the appropriate expedition page in the Geohashing wiki. 
 * 
 * @author Thomas Hirsch
 */
public class WikiMessageEditor extends WikiBaseActivity {

    private static final Pattern RE_EXPEDITION  = Pattern.compile("^(.*)(==+ ?Expedition ?==+.*?)(==+ ?.*? ?==+.*?)$",Pattern.DOTALL);
    
    private HashMap<String, String> mFormfields;
    
    private DecimalFormat mDistFormat = new DecimalFormat("###.######");

    private static final String DEBUG_TAG = "MessageEditor";
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        mInfo = (Info)getIntent().getParcelableExtra("Info");

        setContentView(R.layout.wikieditor);

        Button submitButton = (Button)findViewById(R.id.wikieditbutton);
        
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
        
        // In the event the text changes, update the submit button accordingly.
        TextWatcher tw = new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                resetSubmitButton();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                    int after) {
                // Blah!
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                    int count) {
                // BLAH!
            }

        };
        
        EditText editText = (EditText)findViewById(R.id.wikiedittext);
        editText.addTextChangedListener(tw);
        
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
    protected void onResume() {
        super.onResume();
        
        // Check for username/password here.  That way, when we get back from
        // the settings screen, it'll update the message accordingly.
        SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);
        TextView warning = (TextView)findViewById(R.id.warningmessage);
        String wpName = prefs.getString(GHDConstants.PREF_WIKI_USER, "");
        if ((wpName == null) || (wpName.trim().length() == 0)) {
            warning.setVisibility(View.VISIBLE);
        } else {
            warning.setVisibility(View.GONE);
        }
        
        resetSubmitButton();
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
            SharedPreferences prefs = getSharedPreferences(
                    GHDConstants.PREFS_BASE, 0);
            
            boolean phoneTime = true;

            try {
                HttpClient httpclient = new DefaultHttpClient();

                String wpName = prefs
                        .getString(GHDConstants.PREF_WIKI_USER, "");
                if (!wpName.trim().equals("")) {
                    addStatus(R.string.wiki_conn_login);
                    String wpPassword = prefs.getString(
                            GHDConstants.PREF_WIKI_PASS, "");
                    WikiUtils.login(httpclient, wpName, wpPassword);
                    addStatusAndNewline(R.string.wiki_conn_success);
                } else {
                    addStatusAndNewline(R.string.wiki_conn_anon_warning);
                }

                String expedition = WikiUtils.getWikiPageName(mInfo);

                String locationTag = "";

                // Location! Is the checkbox ticked (and do we have a location
                // handy)?
                CheckBox includelocation = (CheckBox)findViewById(R.id.includelocation);
                if (includelocation.isChecked()) {
                    Location lastLoc = getLastLocation();
                    if (lastLoc != null) {
                        String pos = mLatLonFormat.format(lastLoc.getLatitude()) + ","
                                + mLatLonFormat.format(lastLoc.getLongitude());
                        locationTag = " [http://www.openstreetmap.org/?lat="
                                + mLatLonLinkFormat.format(lastLoc.getLatitude()) + "&lon="
                                + mLatLonLinkFormat.format(lastLoc.getLongitude())
                                + "&zoom=16&layers=B000FTF @" + pos + "]";
                        addStatus(R.string.wiki_conn_current_location);
                        addStatus(" " + pos + "\n");
                    } else {
                        addStatusAndNewline(R.string.wiki_conn_current_location_unknown);
                    }
                }

                addStatus(R.string.wiki_conn_expedition_retrieving);
                addStatus(" " + expedition + "...");
                String page;

                mFormfields = new HashMap<String, String>();
                page = WikiUtils.getWikiPage(httpclient, expedition,
                        mFormfields);
                if ((page == null) || (page.trim().length() == 0)) {
                    addStatusAndNewline(R.string.wiki_conn_expedition_nonexistant);

                    // ok, let's create some.
                    addStatus(R.string.wiki_conn_expedition_creating);
                    WikiUtils.putWikiPage(httpclient, expedition,
                            WikiUtils.getWikiExpeditionTemplate(mInfo, WikiMessageEditor.this),
                            mFormfields);
                    addStatusAndNewline(R.string.wiki_conn_success);

                    addStatus(R.string.wiki_conn_expedition_reretrieving);

                    page = WikiUtils.getWikiPage(httpclient, expedition,
                            mFormfields);
                    addStatusAndNewline(R.string.wiki_conn_success);
                } else {
                    addStatusAndNewline(R.string.wiki_conn_success);
                }

                EditText editText = (EditText)findViewById(R.id.wikiedittext);
                
                // Change the summary so it has our message.
                String summaryPrefix;
                
                // We shouldn't say this is live, per se, if this is a
                // retrohash.
                if(mInfo.isRetroHash())
                    summaryPrefix = getText(R.string.wiki_post_message_summary_retro).toString();
                else
                    summaryPrefix = getText(R.string.wiki_post_message_summary).toString();
                
                mFormfields.put("summary", summaryPrefix + " " + editText.getText().toString()); 
                
                String before = "";
                String after = "";

                Matcher expeditionq = RE_EXPEDITION.matcher(page);
                if (expeditionq.matches()) {
                    before = expeditionq.group(1) + expeditionq.group(2);
                    after = expeditionq.group(3);
                } else {
                    before = page;
                }

                String localtime = DateTools.getWikiDateString(Calendar.getInstance());

                String message = "\n*" + editText.getText().toString().trim()
                        + "  -- ~~~" + locationTag + " "
                        + (phoneTime ? localtime : "~~~~~") + "\n";

                addStatus(R.string.wiki_conn_insert_message);
                WikiUtils.putWikiPage(httpclient, expedition, before + message
                        + after, mFormfields);
                addStatusAndNewline(R.string.wiki_conn_done);

                finishDialog();
                
                dismiss();
            } catch (WikiException ex) {
                String error = (String)getText(ex.getErrorTextId());
                Log.d(DEBUG_TAG, "WIKI EXCEPTION: " + error);
                error(error);
            } catch (Exception ex) {
                Log.d(DEBUG_TAG, "EXCEPTION: " + ex.getMessage());
                if(ex.getMessage() != null)
                    error(ex.getMessage());
                else
                    error((String)getText(R.string.wiki_error_unknown));
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
    
    private void resetSubmitButton() {
        // Make sure the submit button is disabled if there's no text ready.
        // That's all.  We can send things anonymously.
        Button submitButton = (Button)findViewById(R.id.wikieditbutton);
        EditText message  = (EditText)findViewById(R.id.wikiedittext);
        
        if(message == null || message.getText().toString().length() <= 0)
        {
            submitButton.setEnabled(false);
        }
        else
        {
            submitButton.setEnabled(true);
        }
    }
    
    protected void doDismiss() {
        super.doDismiss();
        
        reset();
    }

    protected void reset() {
        // Wipe out the text.
        ((EditText)findViewById(R.id.wikiedittext)).setText("");
        resetSubmitButton();
    }
    
    @Override
    protected void locationUpdated() {
        super.locationUpdated();
        // Coordinates!  Update 'em!
        updateCoords();
    }
    
    private void updateCoords() {
        // Unlike in the wiki picture activity, we only have to concern
        // ourselves with the user's current location.  No picture location, nor
        // any need to change a text string.  Yay!
        Location lastLoc = getLastLocation();
        TextView tv;
        
        if(lastLoc != null)
        {
            tv = (TextView)(findViewById(R.id.coordstring));
            tv.setText(UnitConverter.makeFullCoordinateString(this, lastLoc, false, UnitConverter.OUTPUT_SHORT));
            
            tv = (TextView)(findViewById(R.id.diststring));
            tv.setText(UnitConverter.makeDistanceString(this, mDistFormat, mInfo.getDistanceInMeters(lastLoc)));
        } else {
            tv = (TextView)(findViewById(R.id.coordstring));
            tv.setText(R.string.standby_title);
            
            tv = (TextView)(findViewById(R.id.diststring));
            tv.setText(R.string.standby_title);
        }
    }
}