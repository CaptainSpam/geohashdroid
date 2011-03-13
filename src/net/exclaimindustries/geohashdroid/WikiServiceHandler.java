package net.exclaimindustries.geohashdroid;

import java.text.DecimalFormat;

import android.content.Context;
import android.content.Intent;

/**
 * Classes extending WikiServiceHandler can handle various types of wiki posts.
 * WikiPostService will decide which one to instantiate as it runs through the
 * queue.
 * 
 * @author Nicholas Killewald
 */
public abstract class WikiServiceHandler {
    /** This format is used for all latitude/longitude texts in the wiki. */
    protected static final DecimalFormat mLatLonFormat = new DecimalFormat("###.0000");
    
    /**
     * Handles the given post.  That is, posts it.
     * 
     * If all goes well, the service can just keep barging ahead when this
     * returns.  If all doesn't go well, that's where the exceptions come in.
     * Each one tells the service what to do INSTEAD of barging ahead.
     * 
     * @param context the Context from which things like shared settings can
     *                be read
     * @param intent the Intent containing all the post information
     * @throws WikiException something went kerflooey; the specific type can be
     *                       caught to determine what to do
     */
    public abstract void handlePost(Context context, Intent intent) throws WikiException;
}