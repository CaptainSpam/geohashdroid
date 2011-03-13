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
 * wiki. This can be anything from bad XML to an error in logging in to
 * whatever. This stores a text ID to be translated by the Activity that needs
 * to display it.
 * 
 * Be sure to check the severity. That tells you what to do.
 * 
 * @author Nicholas Killewald
 */
public class WikiException extends Exception {
    private static final long serialVersionUID = 2L;

    private int mTextId;
    private String mErrorCode;
    private Severity mSeverity;

    /**
     * An enum that declares the severity of this exception, and thus what ought
     * to be done about it.
     */
    public enum Severity {
        /**
         * TEMPORARY is thrown if the problem is one where waiting until the
         * connection comes back up may fix it.
         */
        TEMPORARY,
        /**
         * PAUSING comes about due to wiki problems that require user input to
         * clear (bad password, edit conflicts, etc) or indicate a problem with
         * the wiki itself (wiki returned bogus XML, wiki reports problems,
         * etc). Either way, the user should be notified and given a way to
         * manually resume the queue.
         */
        PAUSING,
        /**
         * FATALs are the sort that shouldn't ever happen. This is for the
         * really really bad internal errors that we can't really recover from,
         * so we want to wipe the entire queue and stop. The user should be
         * informed of this, of course.
         */
        FATAL
    }

    public WikiException(Severity severity) {
        this(severity, getDefaultTextId(severity), getDefaultTextString(severity));
    }

    /**
     * Makes a new WikiException with the given text ID and a default error code
     * of "UNKNOWN". This is generally for exceptions that didn't return
     * anything from the wiki.
     * 
     * @param severity
     *            the severity of the exception
     * @param textId
     *            text ID to use
     */
    public WikiException(Severity severity, int textId) {
        this(severity, textId, getDefaultTextString(severity));
    }

    /**
     * Makes a new WikiException with the given text ID and error code.
     * 
     * @param severity
     *            the severity of the exception
     * @param textId
     *            text ID to use
     * @param errorCode
     *            error code the wiki returned
     */
    public WikiException(Severity severity, int textId, String errorCode) {
        super();
        mSeverity = severity;
        mTextId = textId;
        mErrorCode = errorCode;
    }

    @Override
    public String getMessage() {
        String severity = "Some sort of";

        switch (mSeverity) {
            case TEMPORARY:
                severity = "Temporary";
                break;
            case PAUSING:
                severity = "Pausing";
                break;
            case FATAL:
                severity = "Fatal";
                break;
        }
        
        return severity + " wiki exception, text ID " + mTextId + ", code "
                + mErrorCode;
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
     * Gets the error code the wiki returned. Hopefully this won't be "Success",
     * else that'd get confusing.
     * 
     * @return the error code the wiki returned
     */
    public String getErrorCode() {
        return mErrorCode;
    }

    /**
     * Gets the severity of the exception.
     * 
     * @return the exception's severity
     */
    public Severity getSeverity() {
        return mSeverity;
    }
    
    private static int getDefaultTextId(Severity severity) {
        switch(severity) {
            case TEMPORARY:
                return R.string.wiki_error_temporary;
            case PAUSING:
                return R.string.wiki_error_unknown;
            case FATAL:
                return R.string.wiki_error_internal_fatal;
            default:
                return R.string.wiki_error_unknown;
        }
    }
    
    private static String getDefaultTextString(Severity severity) {
        switch(severity) {
            case TEMPORARY:
                return "TEMPORARY";
            case PAUSING:
                return "UNKNOWN";
            case FATAL:
                return "FATAL";
            default:
                return "UNKNOWN";
        }
    }
}
