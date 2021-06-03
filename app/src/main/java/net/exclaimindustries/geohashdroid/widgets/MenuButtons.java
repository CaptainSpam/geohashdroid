/*
 * ZoomButtons.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.widgets;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import net.exclaimindustries.geohashdroid.R;

import androidx.annotation.NonNull;

/**
 * The <code>MenuButtons</code> container handles the buttons in the lower-right
 * of CentralMap.  It pops out when tapped, revealing more buttons that, sadly,
 * do not make even more buttons appear.
 */
public class MenuButtons extends RelativeLayout {
    private static final String DEBUG_TAG = "ZoomButtons";

    private final ImageButton mCancelMenu;
    private final ImageButton mZoomFitBoth;
    private final ImageButton mZoomUser;
    private final ImageButton mZoomDestination;

    private final LinearLayout mTopLevelContainer;
    private final LinearLayout mZoomContainer;

    private boolean mAlreadyLaidOut = false;

    // Keep these here so we don't have to keep recalculating them every time we
    // need them.
    private float mTopLevelContainerWidth = 0.0f;
    private float mZoomContainerWidth = 0.0f;

    /** An enum of whatever button was just pressed. */
    public enum ButtonPressed {
        /** Zoom to fit both the user and the hashpoint on screen at once. */
        ZOOM_FIT_BOTH,
        /** Zoom to the user's location. */
        ZOOM_USER,
        /** Zoom to the hashpoint. */
        ZOOM_DESTINATION,
    }

    private enum MenuType {
        ZOOM,
    }

    /**
     * This should be implemented by anything that's waiting to respond to the
     * zoom buttons.  So, ExpeditionMode, really.
     */
    public interface MenuButtonListener {
        /**
         * Called when a zoom button is pressed.  Not, mind you, when either the
         * menu button itself or the cancel button are pressed.
         *
         * @param container this, for convenience
         * @param which an enum specifying which button just got pressed
         */
        void zoomButtonPressed(View container, ButtonPressed which);
    }

    private MenuButtonListener mListener;

    public MenuButtons(Context c) {
        this(c, null);
    }

    public MenuButtons(Context c, AttributeSet attrs) {
        super(c, attrs);

        inflate(c, R.layout.zoom_buttons, this);

        // Gather up all our sub-buttons...
        ImageButton mZoomMenu = findViewById(R.id.zoom_button_menu);
        mCancelMenu = findViewById(R.id.zoom_button_cancel);
        mZoomFitBoth = findViewById(R.id.zoom_button_fit_both);
        mZoomUser = findViewById(R.id.zoom_button_you);
        mZoomDestination = findViewById(R.id.zoom_button_destination);

        mZoomContainer = findViewById(R.id.group_zoom);
        mTopLevelContainer = findViewById(R.id.group_toplevel);

        // ...and make them do something.
        mZoomFitBoth.setOnClickListener(v -> {
            if(mListener != null)
                mListener.zoomButtonPressed(MenuButtons.this, ButtonPressed.ZOOM_FIT_BOTH);
            hideMenus();
        });

        mZoomUser.setOnClickListener(v -> {
            if(mListener != null)
                mListener.zoomButtonPressed(MenuButtons.this, ButtonPressed.ZOOM_USER);
            hideMenus();
        });

        mZoomDestination.setOnClickListener(v -> {
            if(mListener != null)
                mListener.zoomButtonPressed(MenuButtons.this, ButtonPressed.ZOOM_DESTINATION);
            hideMenus();
        });

        mZoomMenu.setOnClickListener(v -> showMenu(MenuType.ZOOM));

        mCancelMenu.setOnClickListener(v -> hideMenus());

        // Wait for layout, as usual...
        getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if(!mAlreadyLaidOut) {
                mAlreadyLaidOut = true;
                // Get hold of the basic widths of everything.  We'll just
                // re-use that a lot.
                float padding = 2 * getResources()
                        .getDimension(R.dimen.margin_zoom_button);

                mTopLevelContainerWidth = mCancelMenu.getWidth() + padding;
                mZoomContainerWidth = mZoomContainer.getWidth() + padding;

                // First layout, make all the buttons be off-screen.  The
                // right mode will be set back on as need be.
                mTopLevelContainer.setTranslationX(mTopLevelContainerWidth);
                mZoomContainer.setTranslationX(mZoomContainerWidth);

                hideMenus();
            }
        });
    }

    /** Resets MenuButtons back to its initial state. */
    public void reset() {
        hideMenus();

        for(ButtonPressed b : ButtonPressed.values()) {
            setButtonEnabled(b, true);
        }
    }

    private void showMenu(@NonNull MenuType menu) {
        if(mAlreadyLaidOut) {
            // Only do this if we're laid out.  Otherwise, this'll go haywire
            // with the widget sizes if mButtonWidth isn't defined.  Same with
            // hideMenus, really.
            mTopLevelContainer.animate().translationX(mTopLevelContainerWidth);

            hideSubMenus();

            switch(menu) {
                case ZOOM:
                    mZoomContainer.animate().translationX(0.0f);
                    break;
            }
        }
    }

    private void hideMenus() {
        if(mAlreadyLaidOut) {
            // Submenus out!  Top-level buttons in!
            hideSubMenus();

            mTopLevelContainer.animate().translationX(0.0f);
        }
    }

    private void hideSubMenus() {
        mZoomContainer.animate().translationX(mZoomContainerWidth);
    }

    /**
     * Sets whatever's going to listen to the buttons.
     *
     * @param listener said listener
     */
    public void setListener(MenuButtonListener listener) {
        mListener = listener;
    }

    /**
     * Enables or disables a button.  Note that this won't do the logic to make
     * sure "fit both" is disabled when either "your location" or "final
     * destination" are, so do that yourself.
     *
     * @param button button to disable
     * @param enabled true to enable, false to disable
     */
    public void setButtonEnabled(ButtonPressed button, final boolean enabled) {
        View toDisable = null;

        switch(button) {
            case ZOOM_FIT_BOTH:
                toDisable = mZoomFitBoth;
                break;
            case ZOOM_USER:
                toDisable = mZoomUser;
                break;
            case ZOOM_DESTINATION:
                toDisable = mZoomDestination;
                break;
        }

        // Of course, if this wasn't a valid button, toDisable will remain null,
        // meaning the caller screwed it up.
        if(toDisable == null) {
            Log.w(DEBUG_TAG, "There's no such zoom button with an ID of " + button + "!");
            return;
        }

        // But with a button in hand...
        final View reallyToDisable = toDisable;
        ((Activity)getContext()).runOnUiThread(() -> reallyToDisable.setEnabled(enabled));
    }
}
