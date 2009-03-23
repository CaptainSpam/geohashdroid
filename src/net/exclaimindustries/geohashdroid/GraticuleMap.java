/**
 * GraticuleMap.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ZoomControls;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;

/**
 * The <code>GraticuleMap</code> is the Activity that allows the user to select
 * a graticule by tapping on the map, rather than inputting it manually or
 * relying on GPS or cell tower location, which I've learned the hard way isn't
 * particularly reliable all the time.
 * 
 * @author Nicholas Killewald
 *
 */
public class GraticuleMap extends MapActivity implements GraticuleChangedListener {
	private static final String CENTERLAT = "centerLatitude";
	private static final String CENTERLON = "centerLongitude";
	private static final String ZOOM = "zoomLevel";
	
	private static final int MENU_SETTINGS = 0;
	private static final int MENU_MAP_MODE = 1;
	private static final int MENU_CANCEL = 2;
	
	private static final int GRATS_WIDE = 3;
	private static final int GRATS_TALL = 2;
	
	static final String GRATICULE = "Graticule";
	
	private Graticule mGraticule = null;

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		setContentView(R.layout.gratmap);
		
		GraticuleMapView mapView = (GraticuleMapView)findViewById(R.id.Map);
		
		// Zoom buttons!  Go!
		LinearLayout zoomLayout = (LinearLayout)findViewById(R.id.ZoomLayout);
		ZoomControls zoomView = (ZoomControls)mapView.getZoomControls();
		zoomLayout.addView(zoomView, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		mapView.displayZoomControls(true);
		
		// Initial location!  Go!
		// Step one, zoom.  We want a wide area to view.  Like, say, at least
		// three graticules wide or two graticules tall.
		MapController control = mapView.getController();
		
		if(icicle != null) {
			// If we had a graticule stored from an instance bundle, go with it.
			if(icicle.containsKey(GRATICULE))
				mGraticule = (Graticule)icicle.get(GRATICULE);
			
			if(icicle.containsKey(CENTERLAT) && icicle.containsKey(CENTERLON))
				control.setCenter(new GeoPoint(icicle.getInt(CENTERLAT), icicle.getInt(CENTERLON)));
			else
				goToGraticule(mGraticule, control);
			
			if(icicle.containsKey(ZOOM))
				control.setZoom(icicle.getInt(ZOOM));
			else
				control.zoomToSpan(1000000 * GRATS_TALL, 1000000 * GRATS_WIDE);
		} else {
			// We will auto-pan to (and auto-mark) the last known graticule.  That
			// is, whatever was passed in via the Intent.  If there was no Graticule
			// in the Intent, we need to default somewhere.  As much as I would like to
			// default it to wherever I, the author of this program, am currently
			// living, that would confuse more people than it would help, given
			// that at the time of this writing, I'm somewhere in the middle of
			// Kentucky.  There's no real land/water masses to identify where the
			// user is, and frankly, nobody really cares about Kentucky (including
			// me).  So that said, the "default" map location is, oh, Boston, MA.
			Intent i = getIntent();
			
			if(i.hasExtra(GRATICULE)) {
				mGraticule = (Graticule)i.getSerializableExtra(GRATICULE);
			} else {
				mGraticule = null;
			}
			
			control.zoomToSpan(1000000 * GRATS_TALL, 1000000 * GRATS_WIDE);
			goToGraticule(mGraticule, control);
		}
		
		updateBox(mGraticule, null);
		
		// The overlays!  Go!
		// We'll construct the overlay using this itelf as what receives
		// graticule tap updates.
		GraticuleOutlineOverlay outOverlay = new GraticuleOutlineOverlay(getApplicationContext(), mGraticule);
		mapView.getOverlays().add(outOverlay);
		mapView.setOutlineOverlay(outOverlay);
		
		GraticuleHighlightOverlay hiOverlay = new GraticuleHighlightOverlay(getApplicationContext(), mGraticule, this);
		
		mapView.getOverlays().add(hiOverlay);
		
		// The button!  Go!
		updateButton(mGraticule);
		
		Button button = (Button)findViewById(R.id.ConfirmButton);
		button.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// A click means we send the Graticule back as an answer to
				// whatever called this.
				Intent i = new Intent();
				i.putExtra(GRATICULE, mGraticule);
				setResult(RESULT_OK, i);
				finish();
			}
			
		});
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
		MenuItem item;
		
		item = menu.add(Menu.NONE, MENU_MAP_MODE, 0, R.string.menu_item_mode_sat);
		item.setIcon(android.R.drawable.ic_menu_mapmode);
		
		item = menu.add(Menu.NONE, MENU_CANCEL, 1, R.string.cancel_label);
		item.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		
		item = menu.add(Menu.NONE, MENU_SETTINGS, 2, R.string.menu_item_settings);
		item.setIcon(android.R.drawable.ic_menu_preferences);
		
		return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		
		// Reset our menu items as need be.
		resetMapModeMenuItem(menu);
		
		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		super.onMenuItemSelected(featureId, item);
		
		switch(item.getItemId()) {
		case MENU_MAP_MODE:
			{
				MapView mapView = (MapView)findViewById(R.id.Map);
				mapView.setSatellite(!mapView.isSatellite());
				return true;
			}
		case MENU_CANCEL:
			{
				// Cancel means we don't report a graticule.
				setResult(RESULT_CANCELED);
				finish();
				return true;
			}
		case MENU_SETTINGS:
			{
				// Settings!  Now!
				startActivity(new Intent(this, PreferenceEditScreen.class));
				return true;
			}
		}
		
		return false;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		
		// Just remember where we were, how we were zoomed, and what we had
		// selected.
		MapView mapView = (MapView)findViewById(R.id.Map);
		GeoPoint center = mapView.getMapCenter();
		outState.putInt(CENTERLAT, center.getLatitudeE6());
		outState.putInt(CENTERLON, center.getLongitudeE6());
		outState.putInt(ZOOM, mapView.getZoomLevel());
		
		outState.putSerializable(GRATICULE, mGraticule);
		
		
	}

	private void goToGraticule(Graticule g, MapController control) {

		if(g == null) {
			// If there is no Graticule, head off to Boston.
			goToBoston(control);
		} else {
			// Otherwise, center on the center of that Graticule.
			control.animateTo(g.getCenter());
		}
		
	}
	
	private void goToBoston(MapController control) {
		control.setCenter(new GeoPoint(42500000, -71500000));
	}
	
	private void updateBox(Graticule g, Location l) {
		GraticuleMapInfoBoxView box = (GraticuleMapInfoBoxView)findViewById(R.id.InfoBox);
		box.update(g, l);
	}
	
	private void updateButton(Graticule g) {
		Button button = (Button)findViewById(R.id.ConfirmButton);
		if(g == null) {
			button.setEnabled(false);
		} else {
			button.setEnabled(true);
		}
	}

	@Override
	public void graticuleUpdated(Graticule g) {
		// Aha!  A change!
		mGraticule = g;
		updateBox(mGraticule, null);
		updateButton(mGraticule);
		
		// Re-center on the graticule when selected.  The main map on the XKCD
		// website does this, so I figure, why not?
		MapView mapView = (MapView)findViewById(R.id.Map);
		MapController mcontrol = mapView.getController();
		
		mcontrol.animateTo(g.getCenter());
	}
	
	private void resetMapModeMenuItem(Menu menu) {
		// We want it to say the opposite of whatever's currently in action.
		MapView mapView = (MapView)findViewById(R.id.Map);
		
		if(mapView.isSatellite()) {
			menu.findItem(MENU_MAP_MODE).setTitle(R.string.menu_item_mode_street);
		} else {
			menu.findItem(MENU_MAP_MODE).setTitle(R.string.menu_item_mode_sat);
		}
	}

}
