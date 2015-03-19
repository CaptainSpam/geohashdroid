/**
 * DateTools.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.tools;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * <code>DateTools</code> contains any method useful in the manipulation or use
 * of dates.  All without subclassing Calendar, for some reason.
 *
 * @author Nicholas Killewald
 */
public class DateTools {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH);
    private static final SimpleDateFormat HYPHENATED_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
    private static final SimpleDateFormat WIKI_DATE_FORMAT = new SimpleDateFormat("HH:mm, d MMMM yyyy (z)", Locale.ENGLISH);

    /**
     * Generates a YYYYMMDD string from a given Calendar object.
     *
     * @param c Calendar from which to get the string
     * @return a YYYYMMDD string
     */
    public static String getDateString(Calendar c) {
        String date = DATE_FORMAT.format(c.getTime());
        return date;
    }
    
    /**
     * Generates a YYYY-MM-DD string from a given Calendar object.
     *
     * @param c Calendar from which to get the string
     * @return a YYYY-MM-DD string
     */
    public static String getHyphenatedDateString(Calendar c) {
        // Turns out the SimpleDateFormat class does all the tricky work for me.
        // Huh.
        String date = HYPHENATED_DATE_FORMAT.format(c.getTime());
        return date;
    }
    
    /**
     * Generates a date string similar to what MediaWiki would produce for a
     * five-tilde signature.  That is, something like "13:25, 25 March 2012
     * (EDT)", <i>specifically</i> in English.
     * 
     * Note that this is specifically calibrated for the Geohashing wiki.  If
     * you're going to use this outside of Geohash Droid, you may want to make
     * sure the wiki you're posting to uses this same format and language.
     * 
     * @param c a Calendar from which to get the string
     * @return a wiki-signature-like date string
     */
    public static String getWikiDateString(Calendar c) {
        String date = WIKI_DATE_FORMAT.format(c.getTime());
        return date;
    }
}
