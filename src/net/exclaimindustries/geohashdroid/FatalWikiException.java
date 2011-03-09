/**
 * FatalWikiException.java
 * Copyright (C)2011 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

/**
 * A <code>FatalWikiException</code> is the sort that shouldn't ever happen.
 * This is for the really really bad internal errors that we can't really
 * recover from, so we want to wipe the entire queue and stop.  The user should
 * be informed of this, of course.
 * 
 * @author Nicholas Killewald
 *
 */
public class FatalWikiException extends WikiException {
    private static final long serialVersionUID = 1L;
    
    @Override
    public String getMessage() {
        return "Fatal wiki exception, text ID " + mTextId + ", code " + mErrorCode;
    }
}
