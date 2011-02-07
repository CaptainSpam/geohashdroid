/**
 * WikiPostService.java
 * Copyright (C)2011 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.io.InputStream;
import java.io.OutputStream;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import net.exclaimindustries.tools.QueueService;

/**
 * WikiPostService is the background service that posts anything that goes to
 * the Geohashing Wiki.  It keeps track of whether or not there's a data
 * connection to send the posts in the first place and ensures that all posts
 * get delivered later if that's the case.
 * 
 * @author Nicholas Killewald
 */
public class WikiPostService extends QueueService {
    /**
     * Listens for connection Intents.  And, as appropriate, informs the main
     * service to any also-appropriate changes to said connection. 
     */
    public static class ConnectivityListener extends BroadcastReceiver {

        private static final String DEBUG_TAG = "WikiPostService.ConnectivityListener";
        
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            Log.d(DEBUG_TAG, "INTENT HAS BEEN RECEIVIFIED");
            
        }
        
    };

    // This is used when we get a connectivity change.  This will be checked to
    // see if it actually DID change, and thus if we need to send the resume
    // command.  This starts off with whatever ConnectivityManager says for the
    // currently-active network.
    private boolean mIsConnected;

    // A temporary pause is one where we should try again immediately once we
    // get a connection.  That is, if the pause was due to things that WON'T be
    // solved via the connection coming back up (i.e. bad password), this will
    // be false, meaning a connection won't send a resume command.
    private boolean mTemporaryPause;

    public WikiPostService() {
        super();

        // Get a ConnectivityManager.
        ConnectivityManager connMan = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        // Get a NetworkInfo.
        NetworkInfo netInfo = connMan.getActiveNetworkInfo();

        // Get funky.
        if(netInfo != null && netInfo.isConnected()) {
            mIsConnected = true;
        } else {
            mIsConnected = false;
        }

        // mTemporaryPause should default to true at construction time.  If we
        // just came back from being destroyed somehow, we can just try again
        // and get the same error.
        mTemporaryPause = true;
    }

    /* (non-Javadoc)
     * @see net.exclaimindustries.tools.QueueService#deserializeFromDisk(java.io.InputStream)
     */
    @Override
    protected Intent deserializeFromDisk(InputStream is) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see net.exclaimindustries.tools.QueueService#onHandleIntent(android.content.Intent)
     */
    @Override
    protected ReturnCode onHandleIntent(Intent i) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see net.exclaimindustries.tools.QueueService#onQueueEmpty(boolean)
     */
    @Override
    protected void onQueueEmpty(boolean allProcessed) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see net.exclaimindustries.tools.QueueService#onQueuePause(android.content.Intent)
     */
    @Override
    protected void onQueuePause(Intent i) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see net.exclaimindustries.tools.QueueService#serializeToDisk(android.content.Intent, java.io.OutputStream)
     */
    @Override
    protected void serializeToDisk(Intent i, OutputStream os) {
        // TODO Auto-generated method stub

    }

}
