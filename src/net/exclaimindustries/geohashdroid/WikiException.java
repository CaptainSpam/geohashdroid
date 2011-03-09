/**
 * WikiException.java
 * Copyright (C)2010 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

/**
 * A <code>WikiException</code> is thrown when some problem happens with the
 * wiki.  This can be anything from bad XML to an error in logging in to
 * whatever.  This stores a text ID to be translated by the Activity that needs
 * to display it.
 * 
 * It's probably not a good idea to catch this in the course of WikiPostService.
 * Catch a subclass instead, as that explains what the service should do a bit
 * better.
 * 
 * @author Nicholas Killewald
 */
public class WikiException extends Exception {
    private static final long serialVersionUID = 2L;

    protected int mTextId;
    protected String mErrorCode;
    
    public WikiException() {
        super();
        mTextId = R.string.wiki_error_unknown;
        mErrorCode = "UNKNOWN";
    }
    
    /**
     * Makes a new WikiException with the given text ID and a default error code
     * of "UNKNOWN".  This is generally for exceptions that didn't return
     * anything from the wiki.
     * 
     * @param textId text ID to use
     */
    public WikiException(int textId) {
        super();
        mTextId = textId;
        mErrorCode = "UNKNOWN";
    }

    /**
     * Makes a new WikiException with the given text ID and error code.
     * 
     * @param textId text ID to use
     * @param errorCode error code the wiki returned
     */
    public WikiException(int textId, String errorCode) {
        super();
        mTextId = textId;
        mErrorCode = errorCode;
    }
    
    @Override
    public String getMessage() {
        return "Wiki exception, text ID " + mTextId + ", code " + mErrorCode;
    }
    
    /**
     * Gets the text ID for a string that represents this error.
     * 
     * @return a text ID
     */
    public int getErrorTextId() {
        return mTextId;
    }
    
    /**
     * Gets the error code the wiki returned.  Hopefully this won't be
     * "Success", else that'd get confusing.
     * 
     * @return the error code the wiki returned
     */
    public String getErrorCode() {
        return mErrorCode;
    }
}
