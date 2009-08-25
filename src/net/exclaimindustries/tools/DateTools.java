/**
 * DateTools.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.tools;

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
        // This grabs a YYYYMMDD string from a Calendar.  The month gets one
        // added to it because it's zero-indexed (January is zero).
        StringBuilder toReturn = new StringBuilder();
        
        toReturn.append(c.get(Calendar.YEAR));
        
        int month = c.get(Calendar.MONTH) + 1;
        if(month < 10) 
            toReturn.append("0" + month);
        else
            toReturn.append(new Integer(month).toString());
        
        int day = c.get(Calendar.DAY_OF_MONTH);
        if(day < 10)
            toReturn.append("0" + day);
        else
            toReturn.append(new Integer(day).toString());
        
        return toReturn.toString();
    }
}
