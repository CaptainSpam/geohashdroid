/*
 * LocationUtil.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.tools;

import android.location.Location;

/**
 * <code>LocationUtil</code> holds any interesting {@link Location}-related
 * thingamajigs I can come up with.
 */
public class LocationUtil {
    /**
     * The default time a {@link Location} is considered "new enough".
     * Currently a half hour.
     */
    public static final long NEW_ENOUGH = 1000 * 60 * 30;

    /**
     * Returns whether or not the given {@link Location} is "new enough", as
     * determined by the {@link #NEW_ENOUGH} field.
     *
     * @param l Location to check
     * @return true if it's new enough, false if it's too old
     */
    public static boolean isLocationNewEnough(Location l) {
        return isLocationNewEnough(l, NEW_ENOUGH);
    }

    /**
     * Returns whether or not the given {@link Location} is "new enoough", as
     * determined by the supplied age.
     *
     * @param l Location to check
     * @param age the oldest that l can be "new enough", in millis
     * @return true if it's new enough, false if it's too old
     */
    public static boolean isLocationNewEnough(Location l, long age) {
        return System.currentTimeMillis() - l.getTime() < age;
    }
}
