# Geohash Droid
A Geohashing app for Android devices.

(*not* Geocaching)

This is an Android app for Randall Munroe's Geohashing activity (see [its wiki](http://wiki.xkcd.com/geohashing/)).  It downloads stock values for the current day's hash points, puts them on a map for you to visit, and uploads pictures and live comments to the aforementioned wiki.

It's also in the process of a major overhaul.  If you want to check out the code behind what's currently on the Google Play store, look at the legacy branch.

## If you want to build this yourself

The overhauled Geohash Droid should be kinda-sorta functional now, in a very testing sense.  However, since it uses the Google Maps API v2, you're going to need to get your own API key to use it.  [Google provides instructions](https://developers.google.com/maps/documentation/android/start?hl=en) to get a key; all you have to do past that is make your own string resource called "api_map_key_v2" with the key string, and GHD should compile.

If you're building the legacy branch, on the other hand, you'll need a string resource called "api_map_key" that points to an API v1 key.  I don't think Google is giving those out anymore, so really, you might just want to avoid the legacy branch unless you're planning on porting it to use API v2 keys and functionality.

Beyond that, it should be a straightforward build in Android Studio/Gradle.  You'll need (at least) the v23 Android SDK with the Google Play services for the maps stuff.  The legacy branch should build under the v4 API, though it may require v7 for some things.  Just stick with the master branch.

## Notes for future me to consider

* Make GraticulePickerFragment not be a Fragment.  I really don't think that's gaining me anything, but I could be wrong.
* Make the extra fragments (wiki and detailed info on tablets) enter the screen better.  Doing so will require me manually recalculating the centering/zooming tools if the map is still the same size as before but the focus is shifted to the left/top more.
