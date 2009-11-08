/**
 * DateTools.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.tools;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * <code>DateTools</code> contains any method useful in the manipulation or use
 * of dates.  All without subclassing Calendar.
 *
 * @author Nicholas Killewald
 */
public class DateTools {

    /**
     * Generates a YYYYMMDD string from a given Calendar object.
     *
     * @param c Calendar from which to get the string
     * @return a YYYYMMDD string
     */
    public static String getDateString(Calendar c) {
        String date = new SimpleDateFormat("yyyyMMdd").format(c.getTime());
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
        String date = new SimpleDateFormat("yyyy-MM-dd").format(c.getTime());
        return date;
    }
}
