/*
 * WikiException.java
 * Copyright (C)2010 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid.wiki;

import android.util.Log;

import net.exclaimindustries.geohashdroid.R;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

/**
 * A <code>WikiException</code> is thrown when some problem happens with the
 * wiki.  This can be anything from bad XML to an error in logging in to
 * whatever.  This provides means to get an error text ID and type of
 * notification required for the given problem.
 * 
 * @author Nicholas Killewald
 */
public class WikiException extends Exception {
    public enum NotificationType {
        NEEDS_LOGIN,
        GENERAL,
    }

    private static final String DEBUG_TAG = "WikiException";

    /** Internal error string for an unknown issue with the wiki. */
    public static final String INTERNAL_ERROR_GENERIC = "UNKNOWN";
    /**
     * Internal error string for a notification to report if the user tries to
     * post a picture without a wiki login.
     */
    public static final String INTERNAL_ERROR_NO_ANON_PICS = "NO_ANON_PICS";
    /** Internal error string for something failing in login. */
    public static final String INTERNAL_ERROR_BAD_LOGIN = "BAD_LOGIN";
    /**
     * Internal error string for the wiki requiring some sort of fancy schmansy
     * login this app doesn't support yet.
     */
    public static final String INTERNAL_ERROR_FANCY_SCHMANSY_LOGIN = "FANCY_SCHMANSY_LOGIN";
    /** Internal error string for bad XML from the server. */
    public static final String INTERNAL_ERROR_BAD_XML = "BAD_XML";
    /** Internal error string for the wiki claiming this was an invalid page. */
    public static final String INTERNAL_ERROR_INVALID_PAGE = "INVALID_PAGE";
    /**
     * Internal error string for a protected page in some way that didn't report
     * a standard wiki error.
     */
    public static final String INTERNAL_ERROR_PROTECTED = "PROTECTED";

    private static final long serialVersionUID = 2L;

    @NonNull private final String mWikiErrorCode;

    /**
     * Constructs a new WikiException.
     *
     * @param code error code returned from the wiki, or some special code
     *             invented to handle internal problems
     */
    public WikiException(@NonNull String code) {
        super();
        mWikiErrorCode = code;
    }

    @Override
    public String getMessage() {
        return "Wiki exception, code \""
                + mWikiErrorCode
                + "\" (you shouldn't see this)";
    }

    /**
     * Gets the ID of the text string associated with this wiki exception.  If
     * the error code wasn't recognized by this version of WikiException, this
     * will return wiki_unknown_error.  As per more recent versions of Gradle,
     * text IDs may NOT necessarily be stable, so don't count on it being as
     * such for things like switch statements.
     *
     * @return the text ID of the exception's cause
     */
    @StringRes
    public int getErrorTextId() {
        // If we don't recognize the error (or shouldn't get it at all), we use
        // this, because we don't have the slightest clue what's wrong.
        int error = R.string.wiki_error_unknown;

        if(mWikiErrorCode == null) return error;

        // First, general errors.  These are the only general ones we care
        // about; there's more, but those aren't likely to come up.
        switch(mWikiErrorCode) {
            case "unsupportednamespace":
                error = R.string.wiki_error_illegal_namespace;
                break;
            case "protectednamespace-interface":
            case "protectednamespace":
            case "customcssjsprotected":
            case "cascadeprotected":
            case "protectedpage":
                error = R.string.wiki_error_protected;
                break;
            case "confirmemail":
                error = R.string.wiki_error_email_confirm;
                break;
            case "permissiondenied":
                error = R.string.wiki_error_permission_denied;
                break;
            case "blocked":
            case "autoblocked":
                error = R.string.wiki_error_blocked;
                break;
            case "ratelimited":
                error = R.string.wiki_error_rate_limit;
                break;
            case "readonly":
                error = R.string.wiki_error_read_only;
                break;

            // Then, login errors.  These come from the result attribute.
            case "Illegal":
            case "NoName":
            case "CreateBlocked":
                error = R.string.wiki_error_bad_username;
                break;
            case "NotExists":
                error = R.string.wiki_error_username_nonexistant;
                break;
            case "EmptyPass":
            case "WrongPass":
            case "WrongPluginPass":
                error = R.string.wiki_error_bad_password;
                break;
            case "Throttled":
                error = R.string.wiki_error_throttled;
                break;

            // Next, edit errors.  These come from the error element, code
            // attribute.
            case "protectedtitle":
                //noinspection DuplicateBranchesInSwitch
                error = R.string.wiki_error_protected;
                break;
            case "cantcreate":
            case "cantcreate-anon":
                error = R.string.wiki_error_no_create;
                break;
            case "spamdetected":
                error = R.string.wiki_error_spam;
                break;
            case "filtered":
                error = R.string.wiki_error_filtered;
                break;
            case "contenttoobig":
                error = R.string.wiki_error_too_big;
                break;
            case "noedit":
            case "noedit-anon":
                error = R.string.wiki_error_no_edit;
                break;
            case "editconflict":
                error = R.string.wiki_error_conflict;
                break;

            // All-caps errors are internal to Geohash Droid and not actually
            // returned by the wiki.
            case INTERNAL_ERROR_NO_ANON_PICS:
                error = R.string.wiki_conn_anon_pic_error;
                break;
            case INTERNAL_ERROR_GENERIC:
                error = R.string.wiki_generic_error;
                break;
            case INTERNAL_ERROR_BAD_LOGIN:
                error = R.string.wiki_error_bad_login;
                break;
            case INTERNAL_ERROR_FANCY_SCHMANSY_LOGIN:
                error = R.string.wiki_error_fancy_schmansy_login;
                break;
            case INTERNAL_ERROR_BAD_XML:
                error = R.string.wiki_error_xml;
                break;
            case INTERNAL_ERROR_PROTECTED:
                //noinspection DuplicateBranchesInSwitch
                error = R.string.wiki_error_protected;
                break;
            case INTERNAL_ERROR_INVALID_PAGE:
                error = R.string.wiki_error_invalid_page;
                break;

            // If all else fails, log what we got.
            default:
                Log.d(DEBUG_TAG, "Unknown error code came back: "
                        + mWikiErrorCode);
                break;
        }

        return error;
    }

    /**
     * Gets the type of notification that should be displayed for this
     * exception.
     *
     * @return a NotificationType
     */
    public NotificationType getNotificationType() {
        switch(mWikiErrorCode) {
            case INTERNAL_ERROR_NO_ANON_PICS:
            case INTERNAL_ERROR_BAD_LOGIN:
            case "Illegal":
            case "NoName":
            case "CreateBlocked":
            case "EmptyPass":
            case "WrongPass":
            case "WrongPluginPass":
            case "NotExists":
                return NotificationType.NEEDS_LOGIN;
            default:
                return NotificationType.GENERAL;
        }
    }
}
