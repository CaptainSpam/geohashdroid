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
 * @author Nicholas Killewald
 */
public class WikiException extends Exception {
    private static final long serialVersionUID = 1L;

    private int mTextId;
    
    public WikiException(int textId) {
        super();
        mTextId = textId;
    }
    
    @Override
    public String getMessage() {
        return "Wiki exception, text ID " + mTextId + " (you shouldn't see this)";
    }
    
    public int getErrorTextId() {
        return mTextId;
    }
}
