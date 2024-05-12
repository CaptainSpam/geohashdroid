/*
 * GlobalhashiculeTest.java
 * Copyright (C) 2024 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.util;

import net.exclaimindustries.geohashdroid.util.Centicule;
import net.exclaimindustries.geohashdroid.util.Globalhashicule;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;

/**
 * This tests {@link Globalhashicule}, which doesn't have very much to test, but
 * is still worth testing.
 */
@RunWith(BlockJUnit4ClassRunner.class)
public class GlobalhashiculeTest {
    @Test(expected = IllegalArgumentException.class)
    public void createOffset_ThrowsException() {
        Globalhashicule g = Globalhashicule.getInstance();

        g.createOffset(40, 40);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getCenterLatLng_ThrowsException() {
        Globalhashicule g = Globalhashicule.getInstance();

        g.getCenterLatLng();
    }

    @Test
    public void getLatitudeForHash_ReturnsCorrectLatitudeForHash() {
        Globalhashicule g = Globalhashicule.getInstance();

        assertEquals(87.169878, g.getLatitudeForHash(.9842771), .00001);
    }

    @Test
    public void getLongitudeForHash_ReturnsCorrectLongitudeForHash() {
        Globalhashicule g = Globalhashicule.getInstance();

        assertEquals(-176.622012, g.getLongitudeForHash(.0093833), .00001);
    }
}
