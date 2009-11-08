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
import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.webkit.WebView;

import java.text.SimpleDateFormat;

/**
 * Displays an edit box and a send button, which shall upload the message entered to the appropriate expedition page in the
 * Geohashing wiki. 
 * 
 * @author Thomas Hirsch
 */
public class WikiViewer extends Activity {
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);  

        Info mInfo;
        if (icicle != null && icicle.containsKey(GeohashDroid.INFO)) {
            mInfo = (Info)icicle.getSerializable(GeohashDroid.INFO);
        } else {
            mInfo = (Info)getIntent().getSerializableExtra(GeohashDroid.INFO);
        }
        
        String date = new SimpleDateFormat("yyyy-MM-dd").format(mInfo.getCalendar().getTime());
        Graticule grat = mInfo.getGraticule();
        String lat  = grat.getLatitudeString();
        String lon  = grat.getLongitudeString();

        setContentView(R.layout.wikiviewer);
        
        WebView webview = (WebView) findViewById(R.id.wikiviewer);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.loadUrl("http://wiki.xkcd.com/geohashing/"+date+" "+lat+" "+lon);
    }
}
