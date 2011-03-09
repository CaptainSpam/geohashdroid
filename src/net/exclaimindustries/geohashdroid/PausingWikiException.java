/**
 * PausingWikiException.java
 * Copyright (C)2011 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

/**
 * <code>PausingWikiException</code> comes about due to wiki problems that
 * require user input to clear (bad password, edit conflicts, etc) or indicate a
 * problem with the wiki itself (wiki returned bogus XML, wiki reports problems,
 * etc).  Either way, the user should be notified and given a way to manually
 * resume the queue.
 * 
 * @author Nicholas Killewald
 *
 */
public class PausingWikiException extends WikiException {
    private static final long serialVersionUID = 1L;
    
    @Override
    public String getMessage() {
        return "Pausing wiki exception, text ID " + mTextId + ", code " + mErrorCode;
    }
}
