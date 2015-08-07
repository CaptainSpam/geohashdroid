# Geohash Droid
A Geohashing app for Android devices.

(*not* Geocaching)

This is an Android app for Randall Munroe's Geohashing activity (see [its wiki](http://wiki.xkcd.com/geohashing/)).  It downloads stock values for the current day's hash points, puts them on a map for you to visit, and uploads pictures and live comments to the aforementioned wiki.

It's also in the process of a major overhaul.  If you want to check out the code behind what's currently on the Google Play store, look at the legacy branch.

## If you want to build this yourself

The overhauled Geohash Droid should be kinda-sorta functional now, in a very testing sense.  However, since it uses the Google Maps API v2, you're going to need to get your own API key to use it.  [Google provides instructions](https://developers.google.com/maps/documentation/android/start?hl=en) to get a key; all you have to do past that is make your own string resource called "api_map_key_v2" with the key string, and GHD should compile.

Beyond that, it should be a straightforward build in Android Studio/Gradle.  You'll need (at least) the v21 Android SDK with the Google Play services for the maps stuff.

## Notes for future me to consider

* Make GraticulePickerFragment not be a Fragment.  I really don't think that's gaining me anything, but I could be wrong.
* Figure out why CentralMap sometimes starts up and never gets an initial stock value unless the mode, graticule, or date are changed, or the mode is restarted in any way (screen rotation, enter/exit preferences, SelectAGraticuleMode, etc).  It's as if there's a timing issue somewhere that's causing it to ignore the stock response at first.  Maybe something with the waiting list?
