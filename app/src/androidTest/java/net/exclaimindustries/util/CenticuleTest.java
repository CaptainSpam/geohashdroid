/*
 * CenticuleTest.java
 * Copyright (C) 2024 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.util;

import android.location.Location;

import com.google.android.gms.maps.model.LatLng;

import net.exclaimindustries.geohashdroid.util.Centicule;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * This tests {@link Centicule}, hopefully confirming that my ridiculous int
 * idea is working correctly.
 */
@RunWith(BlockJUnit4ClassRunner.class)
public class CenticuleTest {
    @Test
    public void constructor_fromLocationInNorthEast() {
        Location loc = new Location("");
        loc.setLatitude(37.6732187);
        loc.setLongitude(121.98432152);
        Centicule c = new Centicule(loc);

        assertEquals("37.6N", c.getLatitudeString(false));
        assertEquals("121.9E", c.getLongitudeString(false));
    }

    @Test
    public void constructor_fromLocationInSouthWest() {
        Location loc = new Location("");
        loc.setLatitude(-37.6732187);
        loc.setLongitude(-121.98432152);
        Centicule c = new Centicule(loc);

        assertEquals("37.6S", c.getLatitudeString(false));
        assertEquals("121.9W", c.getLongitudeString(false));
    }

    @Test
    public void constructor_fromLocationNearZero() {
        Location loc = new Location("");
        loc.setLatitude(-0.332523);
        loc.setLongitude(0.2378843);
        Centicule c = new Centicule(loc);

        assertEquals("0.3S", c.getLatitudeString(false));
        assertEquals("0.2E", c.getLongitudeString(false));
    }

    @Test
    public void constructor_fromLatLngInNorthEast() {
        LatLng ll = new LatLng(37.6732187, 121.98432152);
        Centicule c = new Centicule(ll);

        assertEquals("37.6N", c.getLatitudeString(false));
        assertEquals("121.9E", c.getLongitudeString(false));
    }

    @Test
    public void constructor_fromLatLngInSouthWest() {
        LatLng ll = new LatLng(-37.6732187, -121.98432152);
        Centicule c = new Centicule(ll);

        assertEquals("37.6S", c.getLatitudeString(false));
        assertEquals("121.9W", c.getLongitudeString(false));
    }

    @Test
    public void constructor_fromLatLngNearZero() {
        LatLng ll = new LatLng(0.79876521, -0.1779846);
        Centicule c = new Centicule(ll);

        assertEquals("0.7N", c.getLatitudeString(false));
        assertEquals("0.1W", c.getLongitudeString(false));
    }

    @Test
    public void constructor_fromComponentPartsInNorthEast() {
        Centicule c = new Centicule(37, 4, false, 121, 7, false);

        assertEquals("37.4N", c.getLatitudeString(false));
        assertEquals("121.7E", c.getLongitudeString(false));
    }

    @Test
    public void constructor_fromComponentPartsInSouthWest() {
        Centicule c = new Centicule(37, 4, true, 121, 7, true);

        assertEquals("37.4S", c.getLatitudeString(false));
        assertEquals("121.7W", c.getLongitudeString(false));
    }

    @Test
    public void constructor_fromComponentPartsNearZero() {
        Centicule c = new Centicule(0, 4, true, 0, 7, false);

        assertEquals("0.4S", c.getLatitudeString(false));
        assertEquals("0.7E", c.getLongitudeString(false));
    }

    @Test
    public void constructor_fromCompleteLatLonInNorthEast() {
        Centicule c = new Centicule(374, false, 1217, false);

        assertEquals("37.4N", c.getLatitudeString(false));
        assertEquals("121.7E", c.getLongitudeString(false));
    }

    @Test
    public void constructor_fromCompleteLatLonInSouthWest() {
        Centicule c = new Centicule(374, true, 1217, true);

        assertEquals("37.4S", c.getLatitudeString(false));
        assertEquals("121.7W", c.getLongitudeString(false));
    }

    @Test
    public void constructor_fromCompleteLatLonNearZero() {
        Centicule c = new Centicule(1, true, 9, false);

        assertEquals("0.1S", c.getLatitudeString(false));
        assertEquals("0.9E", c.getLongitudeString(false));
    }

    @Test
    public void constructor_fromDoublesInNorthEast() {
        Centicule c = new Centicule(37.9879087, 121.093837);

        assertEquals("37.9N", c.getLatitudeString(false));
        assertEquals("121.0E", c.getLongitudeString(false));
    }

    @Test
    public void constructor_fromDoublesInSouthWest() {
        Centicule c = new Centicule(-37.9879087, -121.093837);

        assertEquals("37.9S", c.getLatitudeString(false));
        assertEquals("121.0W", c.getLongitudeString(false));
    }

    @Test
    public void constructor_fromDoublesNearZero() {
        // Remember, this one really needs to be NEAR zero; behavior is not
        // defined for values that are EXACTLY zero.
        Centicule c = new Centicule(0.1102487, -0.003247);

        assertEquals("0.1N", c.getLatitudeString(false));
        assertEquals("0.0W", c.getLongitudeString(false));
    }

    @Test
    public void constructor_fromStringsInNorthEast() {
        Centicule c = new Centicule("37.76182", "121.8604");

        assertEquals("37.7N", c.getLatitudeString(false));
        assertEquals("121.8E", c.getLongitudeString(false));
    }

    @Test
    public void constructor_fromStringsInSouthWest() {
        Centicule c = new Centicule("-37.76182", "-121.8604");

        assertEquals("37.7S", c.getLatitudeString(false));
        assertEquals("121.8W", c.getLongitudeString(false));
    }

    @Test
    public void constructor_fromStringsAtZero() {
        Centicule c = new Centicule("-0.5334", "0.1779");

        assertEquals("0.5S", c.getLatitudeString(false));
        assertEquals("0.1E", c.getLongitudeString(false));
    }

    @Test
    public void createOffset_ReturnsSameCenticuleForZeroes() {
        Centicule c = new Centicule(374, false, 1217, true);

        Centicule test = c.createOffset(0, 0);

        assertEquals(c, test);
    }

    @Test
    public void createOffset_OffsetsEastWithoutCrossingPrimeMeridian() {
        Centicule c = new Centicule(374, false, 1217, true);

        Centicule test = c.createOffset(0, 23);

        assertEquals("119.4W", test.getLongitudeString(false));
        assertEquals("37.4N", test.getLatitudeString(false));
    }

    @Test
    public void createOffset_OffsetsEastCrossingPrimeMeridian() {
        Centicule c = new Centicule(374, false, 1217, true);

        Centicule test = c.createOffset(0, 1404);

        assertEquals("18.6E", test.getLongitudeString(false));
        assertEquals("37.4N", test.getLatitudeString(false));
    }

    @Test
    public void createOffset_OffsetsWestWithoutCrossingPrimeMeridian() {
        Centicule c = new Centicule(374, false, 1217, false);

        Centicule test = c.createOffset(0, -305);

        assertEquals("91.2E", test.getLongitudeString(false));
        assertEquals("37.4N", test.getLatitudeString(false));
    }

    @Test
    public void createOffset_OffsetsWestCrossingPrimeMeridian() {
        Centicule c = new Centicule(374, false, 1217, false);

        Centicule test = c.createOffset(0, -1404);

        assertEquals("18.6W", test.getLongitudeString(false));
        assertEquals("37.4N", test.getLatitudeString(false));
    }

    @Test
    public void createOffset_OffsetsEastToNegativeZero() {
        Centicule c = new Centicule(374, false, 1217, true);

        Centicule test = c.createOffset(0, 1217);

        assertEquals("0.0W", test.getLongitudeString(false));
        assertEquals("37.4N", test.getLatitudeString(false));
    }

    @Test
    public void createOffset_OffsetsEastToPositiveZero() {
        Centicule c = new Centicule(374, false, 1217, true);

        Centicule test = c.createOffset(0, 1218);

        assertEquals("0.0E", test.getLongitudeString(false));
        assertEquals("37.4N", test.getLatitudeString(false));
    }

    @Test
    public void createOffset_OffsetsWestToNegativeZero() {
        Centicule c = new Centicule(374, false, 1217, false);

        Centicule test = c.createOffset(0, -1218);

        assertEquals("0.0W", test.getLongitudeString(false));
        assertEquals("37.4N", test.getLatitudeString(false));
    }

    @Test
    public void createOffset_OffsetsWestToPositiveZero() {
        Centicule c = new Centicule(374, false, 1217, false);

        Centicule test = c.createOffset(0, -1217);

        assertEquals("0.0E", test.getLongitudeString(false));
        assertEquals("37.4N", test.getLatitudeString(false));
    }

    @Test
    public void createOffset_OffsetsAroundTheWorldEast() {
        Centicule c = new Centicule(374, false, 1795, false);

        Centicule test = c.createOffset(0, 10);

        assertEquals("179.4W", test.getLongitudeString(false));
        assertEquals("37.4N", test.getLatitudeString(false));
    }

    @Test
    public void createOffset_OffsetsAroundTheWorldWest() {
        Centicule c = new Centicule(374, false, 1795, true);

        Centicule test = c.createOffset(0, -560);

        assertEquals("124.4E", test.getLongitudeString(false));
        assertEquals("37.4N", test.getLatitudeString(false));
    }
    @Test
    public void createOffset_OffsetsNorthWithoutCrossingEquator() {
        Centicule c = new Centicule(374, false, 1217, true);

        Centicule test = c.createOffset(100, 0);

        assertEquals("47.4N", test.getLatitudeString(false));
        assertEquals("121.7W", test.getLongitudeString(false));
    }

    @Test
    public void createOffset_OffsetsNorthCrossingEquator() {
        Centicule c = new Centicule(4, true, 1217, true);

        Centicule test = c.createOffset(10, 0);

        assertEquals("0.5N", test.getLatitudeString(false));
        assertEquals("121.7W", test.getLongitudeString(false));
    }

    @Test
    public void createOffset_OffsetsSouthWithoutCrossingEquator() {
        Centicule c = new Centicule(374, false, 1217, true);

        Centicule test = c.createOffset(-100, 0);

        assertEquals("27.4N", test.getLatitudeString(false));
        assertEquals("121.7W", test.getLongitudeString(false));
    }

    @Test
    public void createOffset_OffsetsSouthCrossingEquator() {
        Centicule c = new Centicule(5, false, 1217, true);

        Centicule test = c.createOffset(-10, 0);

        assertEquals("0.4S", test.getLatitudeString(false));
        assertEquals("121.7W", test.getLongitudeString(false));
    }

    @Test
    public void createOffset_OffsetsNorthToPositiveZero() {
        Centicule c = new Centicule(47, true, 1217, true);

        Centicule test = c.createOffset(48, 0);

        assertEquals("0.0N", test.getLatitudeString(false));
        assertEquals("121.7W", test.getLongitudeString(false));
    }

    @Test
    public void createOffset_OffsetsNorthToNegativeZero() {
        Centicule c = new Centicule(47, true, 1217, true);

        Centicule test = c.createOffset(47, 0);

        assertEquals("0.0S", test.getLatitudeString(false));
        assertEquals("121.7W", test.getLongitudeString(false));
    }

    @Test
    public void createOffset_OffsetsSouthToPositiveZero() {
        Centicule c = new Centicule(174, false, 1217, true);

        Centicule test = c.createOffset(-174, 0);

        assertEquals("0.0N", test.getLatitudeString(false));
        assertEquals("121.7W", test.getLongitudeString(false));
    }

    @Test
    public void createOffset_OffsetsSouthToNegativeZero() {
        Centicule c = new Centicule(174, false, 1217, true);

        Centicule test = c.createOffset(-175, 0);

        assertEquals("0.0S", test.getLatitudeString(false));
        assertEquals("121.7W", test.getLongitudeString(false));
    }

    @Test(expected = IllegalArgumentException.class)
    public void createOffset_ThrowsExceptionIfGoingOverNorthPole() {
        Centicule c = new Centicule(1609, false, 1217, true);

        c.createOffset(200, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createOffset_ThrowsExceptionIfGoingOverSouthPole() {
        Centicule c = new Centicule(1608, true, 1217, true);

        c.createOffset(-200, 0);
    }
    
    @Test
    public void getCenterLatLng_MakesLatLngForNorthEastCenticule() {
        Centicule c = new Centicule(378, false, 1213, false);

        LatLng test = c.getCenterLatLng();

        assertEquals(37.85d, test.latitude, 0.00001d);
        assertEquals(121.35d, test.longitude, 0.00001d);
    }

    @Test
    public void getCenterLatLng_MakesLatLngForSouthWestCenticule() {
        Centicule c = new Centicule(378, true, 1213, true);

        LatLng test = c.getCenterLatLng();

        assertEquals(-37.85d, test.latitude, 0.00001d);
        assertEquals(-121.35d, test.longitude, 0.00001d);
    }
    
    @Test
    public void uses30WRule_ReturnsTrueFor30WCenticule() {
        Centicule c = new Centicule(374, false, 1216, false);

        assertTrue(c.uses30WRule());
    }

    @Test
    public void uses30WRule_ReturnsFalseForNon30WCenticule() {
        Centicule c = new Centicule(374, false, 1216, true);

        assertFalse(c.uses30WRule());
    }
}
