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
     * The return string is whatever the server gave us.  In effect, if this
     * is anything other than "Success", we stop the queue.  If it's
     * "Retry", we wait until there's a change in network connectivity and
     * try again then.
     * 
     * Also note that "Success" only means "keep the queue going".  It may
     * very well be returned if the Intent has invalid data and must be
     * skipped over.
     *
     * @param context the Context from which things like shared settings can
     *                be read
     * @param intent the Intent containing all the post information
     * @return the error code from the server (or "Success" if nothing is
     *         wrong, or "Retry" if we need to wait and try again)
     */
    public abstract String handlePost(Context context, Intent intent);
}