# Geohash Droid
A Geohashing app for Android devices.

(*not* Geocaching)

This is an Android app for Randall Munroe's Geohashing activity (see [its wiki](http://wiki.xkcd.com/geohashing/)).  It downloads stock values for the current day's hash points, puts them on a map for you to visit, and uploads pictures and live comments to the aforementioned wiki.

It's also in the process of a major overhaul, so the current master branch isn't working right now.  If you want to check out the code behind what's currently on the Google Play store, look at the legacy branch.

## Notes for future me to consider

* Make GraticulePickerFragment not be a Fragment.  I really don't think that's gaining me anything, but I could be wrong.
* Figure out why CentralMap sometimes starts up and never gets an initial stock value unless the mode, graticule, or date are changed, or the mode is restarted in any way (screen rotation, enter/exit preferences, SelectAGraticuleMode, etc).  It's as if there's a timing issue somewhere that's causing it to ignore the stock response at first.  Maybe something with the waiting list?
