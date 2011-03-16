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
    private static final String DEBUG_TAG = "WikiPostService";
    
    /**
     * Listens for connection Intents.  And, as appropriate, informs the main
     * service to any also-appropriate changes to said connection. 
     */
    public static class ConnectivityListener extends BroadcastReceiver {

        private static final String DEBUG_TAG = "WikiPostService.ConnectivityListener";
        
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(DEBUG_TAG, "INTENT HAS BEEN RECEIVIFIED");
            
        }
        
    }
    
    /** A message post. */
    public static final int EXTRA_TYPE_MESSAGE = 0;
    /** A picture post. */
    public static final int EXTRA_TYPE_PICTURE = 1;
    
    /**
     * What sort of post this is.  It's either this or we figure it out by 
     * implication, which can get sloppy.
     * 
     * This should be an int, and one of the types in the EXTRA_TYPE_ statics.
     */
    public static final String EXTRA_TYPE = "Type";
    /**
     * The Info object for a post.  The post page will be determined from here.
     *
     * This should be an Info parcelable.
     */
    public static final String EXTRA_INFO = "Info";
    /**
     * The latitude the user is at for a post.  If this or the longitude are not
     * defined, location is assumed to be unknown.  Note that if posting a
     * picture, the picture's stored location will NOT be consulted; retrieve it
     * beforehand and add it in before sending the Intent.
     *
     * This should be a double.
     */
    public static final String EXTRA_LATITUDE = "Latitude";
    /**
     * The longitude the user is at for a post.  If this or the latitude are not
     * defined, location is assumed to be unknown.  Note that if posting a
     * picture, the picture's stored location will NOT be consulted; retrieve it
     * beforehand and add it in before sending the Intent.
     *
     * This should be a double.
     */
    public static final String EXTRA_LONGITUDE = "Longitude";
    /**
     * The text for a post.
     *
     * This should be a string.
     */
    public static final String EXTRA_POST_TEXT = "PostText";
    /**
     * The post's time, as a measure of milliseconds past the epoch.  If this is
     * not defined, the post will be made with a standard MediaWiki signature,
     * meaning it will be stamped with the time it gets sent, NOT necessarily
     * the time it was made.  That is, get this sorted out BEFORE sending off
     * the Intent.
     *
     * This should be a long.
     */
    public static final String EXTRA_TIMESTAMP = "Timestamp";
    /**
     * The on-filesystem location of a picture to post.  If this is defined AND
     * is not an empty or all-whitespace string, it is assumed this will be
     * picture-posting mode, and thus an Intent with an invalid picture WILL
     * fail.
     *
     * This should be a string.
     */
    public static final String EXTRA_PICTURE_FILE = "PicFile";
    /**
     * Whether or not an infobox will be stamped onto a picture.  This is only
     * consulted if EXTRA_PICTURE_LOCATION is defined.  If this is not defined,
     * it will default to false.
     *
     * This should be a boolean.
     */
    public static final String EXTRA_OPTION_PICTURE_STAMP = "PicStamp";
    /**
     * Whether or not coordinates will be included with a post.  If true, the
     * coordinates given in EXTRA_LATITUDE and EXTRA_LONGITUDE will be appended
     * to the post with a link to a map.  If false, this won't be appended.
     * If not defined, this defaults to true.
     *
     * Note carefully, simply including a latitude and longitude will NOT imply
     * this is true.  However, NOT including a latitude or longitude WILL imply
     * this is false;  This option also has no bearing on
     * EXTRA_OPTION_PICTURE_STAMP; that will be posted anyway if its option is
     * set.
     *
     * This should be a boolean.
     */
    public static final String EXTRA_OPTION_COORDS = "IncludeCoords";

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
     * @see net.exclaimindustries.tools.QueueService#onHandleIntent(android.content.Intent)
     */
    @Override
    protected ReturnCode onHandleIntent(Intent i) {
        // Let's do this!
        WikiServiceHandler handler = null;
        
        int type = i.getIntExtra(EXTRA_TYPE, -1);
        
        // Determine what type we need.
        switch(type)
        {
            case EXTRA_TYPE_MESSAGE:
                Log.d(DEBUG_TAG, "Loading a handler for a message post...");
                handler = new WikiMessageHandler();
                break;
            case EXTRA_TYPE_PICTURE:
                Log.d(DEBUG_TAG, "Loading a handler for a picture post...");
                handler = new WikiPictureHandler();
                break;
            default:
                // If we didn't get a type, report an error and continue on.
                Log.w(DEBUG_TAG, "This Intent doesn't have a valid Type extra!  Ignoring...");
                return ReturnCode.CONTINUE;
        }
        
        // FIRE!
        try {
            handler.handlePost(this, i);
        } catch (WikiException e) {
            // Oops.  Something went wrong.  The severity dictates what we do.
            // TODO: Also, we need to throw up a notification as need be.
            switch(e.getSeverity()) {
                case TEMPORARY:
                    mTemporaryPause = true;
                    return ReturnCode.PAUSE;
                case PAUSING:
                    mTemporaryPause = false;
                    return ReturnCode.PAUSE;
                case FATAL:
                    mTemporaryPause = false;
                    return ReturnCode.STOP;
            }
        }
        
        // If nothing went wrong, roll on!
        return ReturnCode.CONTINUE;
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

    /* (non-Javadoc)
     * @see net.exclaimindustries.tools.QueueService#deserializeFromDisk(java.io.InputStream)
     */
    @Override
    protected Intent deserializeFromDisk(InputStream is) {
        // TODO Auto-generated method stub
        return null;
    }
}
