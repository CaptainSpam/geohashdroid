package net.exclaimindustries.geohashdroid;

import android.location.Location;

oneway interface GeohashServiceCallback {
    /**
     * A location update!  How 'bout that?  Note that this only sends the
     * Location object itself for the time being.  All calculations such as the
     * distance or whatnot have to be done on the client end or via further
     * calls to the service.  Also note that the Location can be null.
     */
    void locationUpdate(in Location location);
}
