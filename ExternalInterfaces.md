# External Interfaces #

Geohash Droid has at least one Activity that can be invoked from external apps, and consequently, one component that can be overridden if need be.

# GraticuleMap #

  * **Type:** Activity that can take an implied Intent
  * **Action:** net.exclaimindustries.geohashdroid.PICK\_GRATICULE
  * **Category:** DEFAULT
  * **Data result:** Graticule object (key: GraticuleMap.GRATICULE)

The map used to tap your graticule (the GraticuleMap activity) is set to respond to an Intent with an action of "net.exclaimindustries.geohashdroid.PICK\_GRATICULE", category DEFAULT.  This is also a constant, PICK\_GRATICULE, in the GeohashDroid class.  This is intended to be called from a startActivityForResult call.  When finished, if it was successful, it will return a code of RETURN\_OK and will have, in its data, a Graticule object (net.exclaimindustries.geohashdroid.Graticule) assigned to key GraticuleMap.GRATICULE.  If it doesn't succeed, it won't return RETURN\_OK and any other result should be ignored.

This means that, with Geohash Droid installed, one could make an Intent with a PICK\_GRATICULE action and use this implementation to pick a graticule, if you happen to need a way to do so.  Or, similarly, if you have a better way to do this in your app and you set it to listen for "net.exclaimindustries.geohashdroid.PICK\_GRATICULE" with a category of DEFAULT, Geohash Droid will pick it up when that map is requested.