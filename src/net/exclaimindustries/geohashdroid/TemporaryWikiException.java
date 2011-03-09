/**
 * TemporaryWikiException.java
 * Copyright (C)2011 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

/**
 * A <code>TemporaryWikiException</code> is thrown if the problem is one where
 * waiting until the connection comes back up may fix it.
 * 
 * @author Nicholas Killewald
 *
 */
public class TemporaryWikiException extends WikiException {
    private static final long serialVersionUID = 1L;
    
    @Override
    public String getMessage() {
        return "Temporary wiki exception, text ID " + mTextId + ", code " + mErrorCode;
    }
}
