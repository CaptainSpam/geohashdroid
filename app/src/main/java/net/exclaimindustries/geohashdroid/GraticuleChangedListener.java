/**
 * GraticuleChangedListener.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

/**
 * A GraticuleChangedListener listens for a change in Graticules.
 * 
 * @author Nicholas Killewald
 */
public interface GraticuleChangedListener {
    public void graticuleUpdated(Graticule g);
}
