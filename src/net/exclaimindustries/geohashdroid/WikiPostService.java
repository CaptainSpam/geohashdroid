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
