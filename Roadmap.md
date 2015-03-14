# Roadmap and To-Do List #

More to-do list than roadmap at this point, I suppose.

## 0.8.0 ##
  * **Tracklog**:
    * Record location data on an update-by-update (or every X updates) basis.
    * Generate a KML file from this for use on the wiki.
    * Add any point at which the user made a wiki post or picture as a placemark.
    * Most likely store this away in a database on the SD card (this probably should NOT go to the internal memory, given its limitations)
  * **[Achievements](Achievements.md)**:
    * Allow achievements to be set on a hash page.
    * Maybe put them on the user page as well?
    * Maybe don't allow achievements to be set if the user is currently anonymous?
  * **Queued wiki posts**:
    * Force wiki posting to the background.
    * If there's no data connection, wait until there is one, then post all the queued wiki posts.
    * Will need a service to do this.
    * Will need some way to handle exceptions (bad password, edit conflicts, etc).
  * **Better EXIF handling**
    * Preserve original EXIF tags on uploaded files.

## Far-Flung Future Features ##
  * **Latitude integration**:
    * Needs Latitude API first.
  * **Geohash Droid as a background service**:
    * Suggestion from the comments.
    * Check the day's meetup point in the morning, then alert the user if the phone gets near it.
    * I like the idea and it's quite feasible (once I look into services), but how to do it without draining the battery with constant GPS polling?
  * **Geohash Droid as a widget**:
    * Goes along with the background service idea.
  * **Search for location**:
    * In GraticuleMap, allow location searching to auto-pick a graticule.  Analyze all results first to make sure they fit in the same graticule, then if they do, select it.  If not, provide a list of graticules that fit the bill.
    * In MainMap, use it as a way to recenter the map.  Don't auto-select a new graticule as per the "change graticule on-the-fly" feature; that'd be confusing.  The "select center of map" part should do for that.
  * **Stuff to do near the location**
    * Similar to the graticule search website, allow for quick searching for stuff nearby the destination point (food, etc).

## General Optimizations and Bugfixes and Such ##
  * **Handle old location data better**:
    * Come up with some way to preserve the MyLocationOverlay object to avoid the redraw issue.
  * **Format code better**:
    * ONLY commit major formatting changes if there are no _real_ changes going in alongside it!