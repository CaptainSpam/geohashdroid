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
import android.app.ProgressDialog;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.content.SharedPreferences;

import android.location.Location;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;

/**
 * Displays an edit box and a send button, which shall upload the message
 * entered to the appropriate expedition page in the Geohashing wiki. 
 * 
 * @author Thomas Hirsch
 */
public class WikiMessageEditor extends WikiBaseActivity {
    private Info mInfo;

    private static final String DEBUG_TAG = "MessageEditor";
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        mInfo = (Info)getIntent().getParcelableExtra(GeohashDroid.INFO);

        setContentView(R.layout.wikieditor);

        Button submitButton = (Button)findViewById(R.id.wikieditbutton);
        
        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Assemble an Intent and send it off to the service.  Act fast,
                // we're in the middle of the main thread here!
                // TODO: Actually DO we want to be in the middle of the main
                // thread?  This should be a quick series of operations, but if
                // we shunt this off to another thread, we'll have to deal with
                // making sure the user can't double-press the submit button and
                // other things like that.
                Intent i = new Intent(WikiMessageEditor.this, WikiPostService.class);
                
                i.putExtra(WikiPostService.EXTRA_TYPE, WikiPostService.EXTRA_TYPE_MESSAGE);
                i.putExtra(WikiPostService.EXTRA_INFO, mInfo);
                
                EditText message = (EditText)findViewById(R.id.wikiedittext);
                i.putExtra(WikiPostService.EXTRA_POST_TEXT, message.getText().toString());
                
                CheckBox includelocation = (CheckBox)findViewById(R.id.includelocation);
                
                if(includelocation.isChecked()) {
                    i.putExtra(WikiPostService.EXTRA_OPTION_COORDS, true);
                    
                    Location loc = getLastLocation();
                    if(loc != null) {
                        i.putExtra(WikiPostService.EXTRA_LATITUDE, loc.getLatitude());
                        i.putExtra(WikiPostService.EXTRA_LONGITUDE, loc.getLongitude());
                    }
                } else {
                    i.putExtra(WikiPostService.EXTRA_OPTION_COORDS, false);
                }
                
                SharedPreferences prefs = getSharedPreferences(
                        GHDConstants.PREFS_BASE, 0);
                
                boolean phoneTime = prefs.getBoolean(GHDConstants.PREF_WIKI_PHONE_TIME, false);
                
                if(phoneTime) {
                    i.putExtra(WikiPostService.EXTRA_TIMESTAMP, System.currentTimeMillis());
                }
                
                // Dispatch!
                Log.d(DEBUG_TAG, "Sending service intent now...");
                startService(i);
                
                reset();
                
                // Good!  Now toast!
                Toast pumpernickel = Toast.makeText(WikiMessageEditor.this, R.string.wiki_toast_message_sending,
                        Toast.LENGTH_SHORT);
                pumpernickel.show();
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
    
    /**
     * Since onRetainNonConfigurationInstance returns a plain ol' Object, this
     * just holds the pieces of data we're retaining.
     */
    private class RetainedThings {
        public Thread thread;
        public WikiConnectionRunner handler;
    }
    
    protected void reset() {
        // Wipe out the text.
        ((EditText)findViewById(R.id.wikiedittext)).setText("");
        resetSubmitButton();
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

}