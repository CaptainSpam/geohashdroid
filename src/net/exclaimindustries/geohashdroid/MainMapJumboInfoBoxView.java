/**
 * MainMapJumboInfoBoxView.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.text.DecimalFormat;

import android.content.Context;
import android.util.AttributeSet;

/**
 * <p>
 * This is MainMapInfoBoxView's big brother.  This one is all jumbo-sized with
 * a big ol' coordinate readout, ideal for taking pictures.  This and junior
 * are, in general, both placed in the map at the same time and are just made
 * visible or invisible depending on preferences.
 * </p>
 * 
 * <p>
 * This displays the coordinates to a higher degree of accuracy than junior,
 * as well as a more accurate distance.  Also, as jumbo is intended to fully
 * stretch across the top of the screen, the compass should be disabled when
 * this comes into play.
 * </p>
 * 
 * @author Nicholas Killewald
 *
 */
public class MainMapJumboInfoBoxView extends MainMapInfoBoxView {

	public MainMapJumboInfoBoxView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setFormats();
	}

	public MainMapJumboInfoBoxView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setFormats();
	}

	public MainMapJumboInfoBoxView(Context context) {
		super(context);
		setFormats();
	}
	
	private void setFormats() {
		mLatLonFormat = new DecimalFormat("###.000000");
		mDistFormat = new DecimalFormat("###.######");
	}
}
