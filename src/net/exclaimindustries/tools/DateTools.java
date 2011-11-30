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

/**
 * <code>DateTools</code> contains any method useful in the manipulation or use
 * of dates.  All without subclassing Calendar, for some reason.
 *
 * @author Nicholas Killewald
 */
public class DateTools {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");
    private static final SimpleDateFormat HYPHENATED_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

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
}
