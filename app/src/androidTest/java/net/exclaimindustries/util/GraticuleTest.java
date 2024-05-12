/*
 * GraticuleTest.java
 * Copyright (C) 2024 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.util;

import android.location.Location;

import com.google.android.gms.maps.model.LatLng;

import net.exclaimindustries.geohashdroid.util.Graticule;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * This tests {@link Graticule}.  This is mostly for createOffset, but you never
 * know, there might be other things worth testing.
 */
@RunWith(BlockJUnit4ClassRunner.class)
public class GraticuleTest {
    @Test
    public void constructor_fromLocationInNorthEast() {
        Location loc = new Location("");
        loc.setLatitude(37.6732187);
        loc.setLongitude(121.98432152);
        Graticule g = new Graticule(loc);

    assertEquals("37N", g.getLatitudeString(false));
    assertEquals("121E", g.getLongitudeString(false));
    }

    @Test
    public void constructor_fromLocationInSouthWest() {
        Location loc = new Location("");
        loc.setLatitude(-37.6732187);
        loc.setLongitude(-121.98432152);
        Graticule g = new Graticule(loc);

        assertEquals("37S", g.getLatitudeString(false));
        assertEquals("121W", g.getLongitudeString(false));
    }

    @Test
    public void constructor_fromLocationNearZero() {
        Location loc = new Location("");
        loc.setLatitude(-0.332523);
        loc.setLongitude(0.2378843);
        Graticule g = new Graticule(loc);

        assertEquals("0S", g.getLatitudeString(false));
        assertEquals("0E", g.getLongitudeString(false));
    }

    @Test
    public void constructor_fromLatLngInNorthEast() {
        LatLng ll = new LatLng(37.6732187, 121.98432152);
        Graticule g = new Graticule(ll);

        assertEquals("37N", g.getLatitudeString(false));
        assertEquals("121E", g.getLongitudeString(false));
    }

    @Test
    public void constructor_fromLatLngInSouthWest() {
        LatLng ll = new LatLng(-37.6732187, -121.98432152);
        Graticule g = new Graticule(ll);

        assertEquals("37S", g.getLatitudeString(false));
        assertEquals("121W", g.getLongitudeString(false));
    }

    @Test
    public void constructor_fromLatLngNearZero() {
        LatLng ll = new LatLng(0.79876521, -0.1779846);
        Graticule g = new Graticule(ll);

        assertEquals("0N", g.getLatitudeString(false));
        assertEquals("0W", g.getLongitudeString(false));
    }

    @Test
    public void constructor_fromComponentPartsInNorthEast() {
        Graticule g = new Graticule(37, false, 121, false);

        assertEquals("37N", g.getLatitudeString(false));
        assertEquals("121E", g.getLongitudeString(false));
    }

    @Test
    public void constructor_fromComponentPartsInSouthWest() {
        Graticule g = new Graticule(37, true, 121, true);

        assertEquals("37S", g.getLatitudeString(false));
        assertEquals("121W", g.getLongitudeString(false));
    }

    @Test
    public void constructor_fromComponentPartsNearZero() {
        Graticule g = new Graticule(0, true, 0, false);

        assertEquals("0S", g.getLatitudeString(false));
        assertEquals("0E", g.getLongitudeString(false));
    }

    @Test
    public void constructor_fromDoublesInNorthEast() {
        Graticule g = new Graticule(37.9879087, 121.093837);

        assertEquals("37N", g.getLatitudeString(false));
        assertEquals("121E", g.getLongitudeString(false));
    }

    @Test
    public void constructor_fromDoublesInSouthWest() {
        Graticule g = new Graticule(-37.9879087, -121.093837);

        assertEquals("37S", g.getLatitudeString(false));
        assertEquals("121W", g.getLongitudeString(false));
    }

    @Test
    public void constructor_fromDoublesNearZero() {
        // Remember, this one really needs to be NEAR zero; behavior is not
        // defined for values that are EXACTLY zero.
        Graticule g = new Graticule(0.1102487, -0.003247);

        assertEquals("0N", g.getLatitudeString(false));
        assertEquals("0W", g.getLongitudeString(false));
    }

    @Test
    public void constructor_fromStringsInNorthEast() {
        Graticule g = new Graticule("37", "121");

        assertEquals("37N", g.getLatitudeString(false));
        assertEquals("121E", g.getLongitudeString(false));
    }

    @Test
    public void constructor_fromStringsInSouthWest() {
        Graticule g = new Graticule("-37", "-121");

        assertEquals("37S", g.getLatitudeString(false));
        assertEquals("121W", g.getLongitudeString(false));
    }

    @Test
    public void constructor_fromStringsAtZero() {
        Graticule g = new Graticule("-0", "0");

        assertEquals("0S", g.getLatitudeString(false));
        assertEquals("0E", g.getLongitudeString(false));
    }

    @Test
    public void createOffset_ReturnsSameGraticuleForZeroes() {
        Graticule g = new Graticule(37, false, 121, true);

        Graticule test = g.createOffset(0, 0);

        assertEquals(g, test);
    }

    @Test
    public void createOffset_OffsetsEastWithoutCrossingPrimeMeridian() {
        Graticule g = new Graticule(37, false, 121, true);

        Graticule test = g.createOffset(0, 20);

        assertEquals("101W", test.getLongitudeString(false));
        assertEquals("37N", test.getLatitudeString(false));
    }

    @Test
    public void createOffset_OffsetsEastCrossingPrimeMeridian() {
        Graticule g = new Graticule(37, false, 121, true);

        Graticule test = g.createOffset(0, 140);

        assertEquals("18E", test.getLongitudeString(false));
        assertEquals("37N", test.getLatitudeString(false));
    }

    @Test
    public void createOffset_OffsetsWestWithoutCrossingPrimeMeridian() {
        Graticule g = new Graticule(37, false, 121, false);

        Graticule test = g.createOffset(0, -30);

        assertEquals("91E", test.getLongitudeString(false));
        assertEquals("37N", test.getLatitudeString(false));
    }

    @Test
    public void createOffset_OffsetsWestCrossingPrimeMeridian() {
        Graticule g = new Graticule(37, false, 121, false);

        Graticule test = g.createOffset(0, -140);

        assertEquals("18W", test.getLongitudeString(false));
        assertEquals("37N", test.getLatitudeString(false));
    }

    @Test
    public void createOffset_OffsetsEastToNegativeZero() {
        Graticule g = new Graticule(37, false, 121, true);

        Graticule test = g.createOffset(0, 121);

        assertEquals("0W", test.getLongitudeString(false));
        assertEquals("37N", test.getLatitudeString(false));
    }

    @Test
    public void createOffset_OffsetsEastToPositiveZero() {
        Graticule g = new Graticule(37, false, 121, true);

        Graticule test = g.createOffset(0, 122);

        assertEquals("0E", test.getLongitudeString(false));
        assertEquals("37N", test.getLatitudeString(false));
    }

    @Test
    public void createOffset_OffsetsWestToNegativeZero() {
        Graticule g = new Graticule(37, false, 121, false);

        Graticule test = g.createOffset(0, -122);

        assertEquals("0W", test.getLongitudeString(false));
        assertEquals("37N", test.getLatitudeString(false));
    }

    @Test
    public void createOffset_OffsetsWestToPositiveZero() {
        Graticule g = new Graticule(37, false, 121, false);

        Graticule test = g.createOffset(0, -121);

        assertEquals("0E", test.getLongitudeString(false));
        assertEquals("37N", test.getLatitudeString(false));
    }

    @Test
    public void createOffset_OffsetsAroundTheWorldEast() {
        Graticule g = new Graticule(37, false, 171, false);

        Graticule test = g.createOffset(0, 10);

        assertEquals("178W", test.getLongitudeString(false));
        assertEquals("37N", test.getLatitudeString(false));
    }

    @Test
    public void createOffset_OffsetsAroundTheWorldWest() {
        Graticule g = new Graticule(37, false, 121, true);

        Graticule test = g.createOffset(0, -60);

        assertEquals("178E", test.getLongitudeString(false));
        assertEquals("37N", test.getLatitudeString(false));
    }
    @Test
    public void createOffset_OffsetsNorthWithoutCrossingEquator() {
        Graticule g = new Graticule(37, false, 121, true);

        Graticule test = g.createOffset(10, 0);

        assertEquals("47N", test.getLatitudeString(false));
        assertEquals("121W", test.getLongitudeString(false));
    }

    @Test
    public void createOffset_OffsetsNorthCrossingEquator() {
        Graticule g = new Graticule(5, true, 121, true);

        Graticule test = g.createOffset(10, 0);

        assertEquals("4N", test.getLatitudeString(false));
        assertEquals("121W", test.getLongitudeString(false));
    }

    @Test
    public void createOffset_OffsetsSouthWithoutCrossingEquator() {
        Graticule g = new Graticule(37, false, 121, true);

        Graticule test = g.createOffset(-10, 0);

        assertEquals("27N", test.getLatitudeString(false));
        assertEquals("121W", test.getLongitudeString(false));
    }

    @Test
    public void createOffset_OffsetsSouthCrossingEquator() {
        Graticule g = new Graticule(5, false, 121, true);

        Graticule test = g.createOffset(-10, 0);

        assertEquals("4S", test.getLatitudeString(false));
        assertEquals("121W", test.getLongitudeString(false));
    }

    @Test
    public void createOffset_OffsetsNorthToPositiveZero() {
        Graticule g = new Graticule(5, true, 121, true);

        Graticule test = g.createOffset(6, 0);

        assertEquals("0N", test.getLatitudeString(false));
        assertEquals("121W", test.getLongitudeString(false));
    }

    @Test
    public void createOffset_OffsetsNorthToNegativeZero() {
        Graticule g = new Graticule(5, true, 121, true);

        Graticule test = g.createOffset(5, 0);

        assertEquals("0S", test.getLatitudeString(false));
        assertEquals("121W", test.getLongitudeString(false));
    }

    @Test
    public void createOffset_OffsetsSouthToPositiveZero() {
        Graticule g = new Graticule(10, false, 121, true);

        Graticule test = g.createOffset(-10, 0);

        assertEquals("0N", test.getLatitudeString(false));
        assertEquals("121W", test.getLongitudeString(false));
    }

    @Test
    public void createOffset_OffsetsSouthToNegativeZero() {
        Graticule g = new Graticule(10, false, 121, true);

        Graticule test = g.createOffset(-11, 0);

        assertEquals("0S", test.getLatitudeString(false));
        assertEquals("121W", test.getLongitudeString(false));
    }

    @Test(expected = IllegalArgumentException.class)
    public void createOffset_ThrowsExceptionIfGoingOverNorthPole() {
        Graticule g = new Graticule(160, false, 121, true);

        g.createOffset(20, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createOffset_ThrowsExceptionIfGoingOverSouthPole() {
        Graticule g = new Graticule(160, true, 121, true);

        g.createOffset(-20, 0);
    }

    @Test
    public void getCenterLatLng_MakesLatLngForNorthEastGraticule() {
        Graticule g = new Graticule(37, false, 121, false);

        LatLng test = g.getCenterLatLng();

        assertEquals(37.5d, test.latitude, 0.00001d);
        assertEquals(121.5d, test.longitude, 0.00001d);
    }

    @Test
    public void getCenterLatLng_MakesLatLngForSouthWestGraticule() {
        Graticule g = new Graticule(37, true, 121, true);

        LatLng test = g.getCenterLatLng();

        assertEquals(-37.5d, test.latitude, 0.00001d);
        assertEquals(-121.5d, test.longitude, 0.00001d);
    }

    @Test
    public void uses30WRule_ReturnsTrueFor30WGraticule() {
        Graticule g = new Graticule(37, false, 121, false);

        assertTrue(g.uses30WRule());
    }

    @Test
    public void uses30WRule_ReturnsFalseForNon30WGraticule() {
        Graticule g = new Graticule(37, false, 121, true);

        assertFalse(g.uses30WRule());
    }

    @Test
    public void getLatitudeForHash_ReturnsCorrectLatitudeForHash() {
        Graticule g = new Graticule(37, false, 121, true);

        assertEquals(37.9842771, g.getLatitudeForHash(.9842771), .00001);
    }

    @Test
    public void getLongitudeForHash_ReturnsCorrectLongitudeForHash() {
        Graticule g = new Graticule(37, false, 121, true);

        assertEquals(-121.0093833, g.getLongitudeForHash(.0093833), .00001);
    }
}
