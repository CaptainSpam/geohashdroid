/*
 * HexFractionTest.java
 * Copyright (C) 2021 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.tools;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;

/**
 * This tests {@link HexFraction}.  At present, this just runs down a series of
 * sixteen tests, each of which is a single repeated hexit sixteen times.
 * Hopefully this breaks spectacularly if something's amiss.
 */
@RunWith(BlockJUnit4ClassRunner.class)
public class HexFractionTest {
    @Test
    public void calculatesAllZeroes() {
        assertEquals(0d, HexFraction.calculate("0000000000000000"), 0d);
    }

    @Test
    public void calculatesAllOnes() {
        assertEquals(0.06666666666666667d, HexFraction.calculate("1111111111111111"),0d);
    }

    @Test
    public void calculatesAllTwos() {
        assertEquals(0.13333333333333333d, HexFraction.calculate("2222222222222222"),0d);
    }

    @Test
    public void calculatesAllThrees() {
        assertEquals(0.2d, HexFraction.calculate("3333333333333333"),0d);
    }

    @Test
    public void calculatesAllFours() {
        assertEquals(0.26666666666666666d, HexFraction.calculate("4444444444444444"),0d);
    }

    @Test
    public void calculatesAllFives() {
        assertEquals(0.3333333333333333d, HexFraction.calculate("5555555555555555"),0d);
    }

    @Test
    public void calculatesAllSixes() {
        assertEquals(0.4d, HexFraction.calculate("6666666666666666"),0d);
    }

    @Test
    public void calculatesAllSevens() {
        assertEquals(0.4666666666666667d, HexFraction.calculate("7777777777777777"),0d);
    }

    @Test
    public void calculatesAllEights() {
        assertEquals(0.5333333333333333d, HexFraction.calculate("8888888888888888"),0d);
    }

    @Test
    public void calculatesAllNines() {
        assertEquals(0.6d, HexFraction.calculate("9999999999999999"),0d);
    }

    @Test
    public void calculatesAllAs() {
        assertEquals(0.6666666666666666d, HexFraction.calculate("AAAAAAAAAAAAAAAA"),0d);
    }

    @Test
    public void calculatesAllBs() {
        assertEquals(0.7333333333333333d, HexFraction.calculate("BBBBBBBBBBBBBBBB"),0d);
    }

    @Test
    public void calculatesAllCs() {
        assertEquals(0.8d, HexFraction.calculate("CCCCCCCCCCCCCCCC"),0d);
    }

    @Test
    public void calculatesAllDs() {
        assertEquals(0.8666666666666667d, HexFraction.calculate("DDDDDDDDDDDDDDDD"),0d);
    }

    @Test
    public void calculatesAllEs() {
        assertEquals(0.9333333333333333d, HexFraction.calculate("EEEEEEEEEEEEEEEE"),0d);
    }

    @Test
    public void calculatesAllFs() {
        assertEquals(1.0d, HexFraction.calculate("FFFFFFFFFFFFFFFF"), 0d);
    }
}
