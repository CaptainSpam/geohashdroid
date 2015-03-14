You want your versions?  We got yer versions.

# Still The Beta Era #
## 0.7.17 (June 6, 2012, b82c9dc6f747da6656ae94242a5f7bfd83c31bb3) ##
  * The Send To Maps buttons now use a more accurate query that actually plots the coordinates, rather than find the closest interesting feature to the point.
  * In the case of custom firmware that lacks the Maps app or anything else that responds to the "geo:" scheme, the Send To Maps buttons will be disabled.

## 0.7.16 (May 3, 2012, ce51786f085e2eca79df9aacece74da8022d5991) ##
  * Made popups for when you're close enough to the point where GPS accuracy can't tell if you're any closer.  This also turns the distance text green on the infoboxen and details screen.
  * On both the wiki screens, it'll now display the coordinates that will be posted should you choose to do so.  This helps with picture posting, as it isn't always clear that, if the pictures have geolocation data, it'll use THAT instead of your current location.
  * If a picture is more than 15 minutes old, it'll no longer add the [live](live.md) tag to the wiki when posting it.  Because it's not live anymore.
  * When forcing posts to be made with the phone's date and time (instead of just using the five-tilde format in MediaWiki), all months will now be in English so they LOOK like real wiki timestamps.
  * Cleaned up a LOT of stuff behind the scenes.

## 0.7.15 (December 30, 2011, f289ef03d6bc38c529950d6ac1aee90f018e77ad) ##
  * Fixed a problem where, under the Galaxy Nexus (and maybe other phones), the graticule input fields could break to two lines under certain inputs.  That is to say, the text fields got a wee bit wider and should hopefully scale up properly from now on.

## 0.7.14 (November 30, 2011, 79879c88617e5f8a5fb26de82f5ffb4e93d93162) ##
  * Added support for the power of RADAR!  If you have an app that can accept the Radar intent, you can feed the current hashpoint into it from the map and detail screens.

## 0.7.13 (November 16, 2011, 0cb5a106ac6cdc78b6c40c4de982e135fd71ed63) ##
  * Fixed a localization problem with coordinates.  As far as I can tell, even in countries where decimal values are delimited by commas (i.e. where "one thousand eighty-nine and sixty-four hundredths" is written as 1.089,64), geographic coordinates are still period-delimited.  If anyone has any definitive evidence otherwise, please let me know.

## 0.7.12 (August 28, 2011, [r615](https://code.google.com/p/geohashdroid/source/detail?r=615)) ##
  * Added the Today button.  This'll always lock the date to today, regardless of any oddness with switching out of the app or whatever the DateButton says.

## 0.7.11 (June 25, 2011, [r610](https://code.google.com/p/geohashdroid/source/detail?r=610)) ##
  * Replaced the DatePicker widget with my custom DateButton.  Seems the bug I fixed in DatePicker (upstream) isn't being propagated to all devices, so this is the safest way to solve the issue where the wrong date is sent to the map under certain conditions.
  * Fixed a few parts of the wiki screens.  Now the layouts don't go all screwy and the submit button gets disabled properly on WikiMessageEditor if there's no text.

## 0.7.10 (March 20, 2011, [r584](https://code.google.com/p/geohashdroid/source/detail?r=584)) ##
  * Fixed the wiki login and posting problem that came up after the Geohashing Wiki upgraded to MediaWiki 1.16.
  * Made a few changes regarding backend parcelizing and such.

## 0.7.9 (December 27, 2010, [r540](https://code.google.com/p/geohashdroid/source/detail?r=540)) ##
  * New option in the picture uploader to allow an infobox to be stamped on the image.  Yay!
  * The wiki editor screens will now keep the GPS tracker live so the location is more accurate when the message is actually sent.

## 0.7.8 (September 9, 2010, [r503](https://code.google.com/p/geohashdroid/source/detail?r=503) (back on trunk now)) ##
  * The jumbo-sized infobox will now display your location and the distance, not the final destination and your position.  This makes a lot more sense.
  * The initial location and stock grabbers (that is, the popups when you start) should be far more robust.  Which is to say, they won't give up and stop when you change orientation, a phone call comes, etc, etc.

## 0.7.7 (August 15, 2010, [r490](https://code.google.com/p/geohashdroid/source/detail?r=490) (also on the 0.7.4-backports branch)) ##
  * The picture uploader now requests a picture from the phone's Gallery app.  This is organized a lot better than our old flickable gallery interface and fits the phone's interface better, as well as allow for pictures to be taken on the spot as need be.
  * Hopefully put an end to all the OutOfMemoryErrors (bitmap size exceeds VM budget) with a different scaling method.
  * Fixed a minor scaling bug regardless.
  * Fixed minutes and seconds not showing up properly in locales that use a comma as a decimal separator.

## 0.7.6 (July 21, 2010, [r471](https://code.google.com/p/geohashdroid/source/detail?r=471) (on the 0.7.4-backports branch)) ##
  * Discovered a fairly major memory leak happening with GeohashService.  A leak that, given a fairly short amount of time, locked up or rebooted the phone.  Since I'm pretty certain it's somewhere in GeohashService and in nothing else I've added since then, 0.7.6 is effectively 0.8.2 without the background service and with all the other non-service-related additions backported in (meaning, just about everything besides power saver mode).  Will investigate muchly.
  * Reverted to 0.7.x because I took out the service.  Hence the out-of-orderness.

## 0.8.2 (July 1, 2010, [r456](https://code.google.com/p/geohashdroid/source/detail?r=456)) ##
  * The Obligatory Froyo Update!  Froyoers can now install Geohash Droid on SD cards.
  * When posting a message or picture to the wiki when using an old hashpoint, the summary will say [retro](retro.md) instead of [live](live.md).
  * (note that 0.8.1's final release didn't change anything from pre3)

## 0.8.1-pre3 (June 11, 2010, [r450](https://code.google.com/p/geohashdroid/source/detail?r=450)) ##
  * Added power saver mode.  This will cut down the number of GPS fixes by delaying them when you're not looking at the map or the details screen.

## 0.8.1-pre2 (June 7, 2010, [r446](https://code.google.com/p/geohashdroid/source/detail?r=446)) ##
  * Added a popup on successful wiki posts.  It was sort of ambiguous otherwise if you've never seen a wiki error that the popup vanishing meant it was successful.

## 0.8.1-pre1 (June 2, 2010, [r443](https://code.google.com/p/geohashdroid/source/detail?r=443)) ##
  * Fixed a possible problem with GeohashService never sending data if GPS is flaky.
  * Globalhash support!

## 0.8.0 (May 25, 2010, [r430](https://code.google.com/p/geohashdroid/source/detail?r=430)) ##
  * Fixed a few more minor crashing bugs.  That's why pre-releases exist.
  * Added a notification icon.

## 0.8.0-pre2 (May 4, 2010, [r396](https://code.google.com/p/geohashdroid/source/detail?r=396)) ##
  * Added a workaround to the DatePicker rollover bug.

## 0.8.0-pre1 (April 25, 2010, [r393](https://code.google.com/p/geohashdroid/source/detail?r=393)) ##
  * Hoooo boy.
  * Introduced GeohashService.  This allows the tracking part of Geohash Droid to run in the background while you go do other things.  It'll sit there in the notifications bar, waiting for you to jump back to it.
  * Switched to using geo.crox.net for stock data, using the base implementation as a backup.  More servers means more reliable!
  * Fixed what may very well have been the elusive stock corruption bug.  Finally figured out what might've been causing it. ([issue 13](https://code.google.com/p/geohashdroid/issues/detail?id=13))
  * Hopefully fixed the problem with the image uploader nuking the old photo gallery. ([issue 22](https://code.google.com/p/geohashdroid/issues/detail?id=22))
  * Fixed a problem where the image uploader was never looking at geotags in the image itself when attaching coordinates to the post.
  * Removed the blurring effects during stock and location grabbing.

## 0.7.5 (May 5, 2010, [r408](https://code.google.com/p/geohashdroid/source/detail?r=408)) ##
  * Backported a few of the 0.8.0 fixes to 0.7.5.  To wit:
    * Workaround for the DatePicker rollover bug.
    * Using geo.crox.net for stock data.
    * Fixed the stock corruption bug.
    * Fixed the wiki photo uploader walking over old photo galleries.
    * Fixed the wiki photo uploader not looking at geotags in the images themselves.

## 0.7.4 (February 21, 2010, [r351](https://code.google.com/p/geohashdroid/source/detail?r=351)) ##
  * The pinch-zoom-capable version of the Maps app also allows Google Maps API apps to pinch-zoom.  Thus, added in a quick fix to make the map a bit more cooperative with it.
  * Fixed an issue with the wiki where pictures were being uploaded but weren't showing up on the wiki pages.  This was also dealt with by a change to the templates on the wiki's end.

## 0.7.3 (February 2, 2010, [r341](https://code.google.com/p/geohashdroid/source/detail?r=341)) ##
  * Wiki fixes, round one!
  * The wiki input boxes are now multiline.  This was a mistake I made when removing the deprecated attributes.
  * Fixed a bug where pictures that didn't have coordinate data embedded were being uploaded with "null,null" as the location.  Now it will revert to your current location if possible, and if not, it will just ignore it entirely ([issue 10](https://code.google.com/p/geohashdroid/issues/detail?id=10)).
  * When posting coordinates to the wiki, now they round off to four decimal points, instead of whatever Java thinks is the full precision for a double.  Note that the full precision is still used as the link to openstreetmap.org is still full-size ([issue 12](https://code.google.com/p/geohashdroid/issues/detail?id=12)).
  * The thumbnail sizes in the picture upload interface now scales properly to screen size.

## 0.7.2 (January 27, 2010, [r332](https://code.google.com/p/geohashdroid/source/detail?r=332)) ##
  * Added multiple screen size support.  Now larger screens have crisper graphics and smaller screens are actually usable.
  * Added the "Send to Maps" feature.  This sends the point to the Maps app, which can then be used for navigation purposes.  Unless it puts you in the middle of nowhere like it frequently does here in central Kentucky.  Then you learn the hard way that not even Google's willing to navigate THAT.

## 0.7.1 (January 17, 2010, [r311](https://code.google.com/p/geohashdroid/source/detail?r=311)) ##
  * Added Thomas Hirsch's wiki features!  Now you can update the Geohashing Wiki directly from the app itself!
  * Fixed a crashing problem with the Motorola CLIQ, due in part to Motorola not shipping the phones with the proper graphics for MyLocationOverlay.
  * Fixed a 30W Rule problem for retrohashes from before the 30W Rule was invented.

## 0.7.0 (November 17, 2009, [r246](https://code.google.com/p/geohashdroid/source/detail?r=246)) ##
  * Made the preferences screen a bit more dynamic.
  * My mistake, wiki features will be in 0.7.1.

## 0.7.0-pre3 (October 7, 2009, [r244](https://code.google.com/p/geohashdroid/source/detail?r=244)) ##
  * Getting closer...
  * The nearby points can now be tapped to change to them in case the currently selected one isn't that useful.
  * Next, the wiki features.

## 0.7.0-pre2 (September 28, 2009, [r230](https://code.google.com/p/geohashdroid/source/detail?r=230)) ##
  * Fixed a serious cache bug in 0.7.0-pre1.  Tip to new developers: That's why pre-releases exist and aren't pushed out to the Marketplace!
  * The detail screens are now scrollable in the event that the name of the month makes the top title spill to two lines and knock the button off the screen (note that the hard back button on the phone still works regardless).
  * The "Use Closest" feature will now update the graticule inputs on the main screen BEFORE moving to MainMap, meaning they'll be updated when you go back there.
  * Finally, _FINALLY_ compiled Geohash Droid as a 1.5 app, not a 1.1 app.  So those of you lagging behind with 1.1 phones now have another reason to update.

## 0.7.0-pre1 (September 17, 2009, [r223](https://code.google.com/p/geohashdroid/source/detail?r=223)) ##
_**WARNING:**_ Do **NOT** use version 0.7.0-pre1 for realworld Geohashing use!  There is a serious bug in the cache mechanism!

  * HashBuilder now no longer does excessive MD5 calculations for stored data, either in the database or the quick cache.  This should be faster.
  * Improved the stability of getting the current location and getting the stocks by making them stop immediately if paused or interrupted by anything.
  * Changed the look of the aforementioned windows.  Now they have little spinny progress bars and they blur the background.  They also don't have explicit Cancel buttons; press the back button on the phone to cancel them manually.
  * Added "Use Closest" to replace "Auto-Detect" on the main screen.  This uses your current location to pick whatever graticule has the closest point for you.
  * Added an option to display nearby points on the map in case the one for your current graticule doesn't work out.  In pre2, tapping them will do something.
  * Lots of little changes all around.

## 0.6.7 (August 1, 2009, [r164](https://code.google.com/p/geohashdroid/source/detail?r=164)) ##
  * Completely overhauled HashMaker.  In fact, removed it entirely and replaced it with  HashBuilder, which is slightly more flexible in-code.  For the most part, this doesn't concern the end-user.
  * Added a stock price cache so that it doesn't keep going back to the web for every single stock request.
  * Made stock checking more intelligent; if the requested stock is on a weekend (or Saturday through Monday for 30W Rule users), a request for Friday's stock will be made instead, and will be cached as such.
  * All sorts of minor backend fixes.

## 0.6.6 (June 10, 2009, [r118](https://code.google.com/p/geohashdroid/source/detail?r=118)) ##
  * Did a lot of placement fixes due to slight issues with Cupcake.

## 0.6.6-pre1 (June 1, 2009, [r110](https://code.google.com/p/geohashdroid/source/detail?r=110)) ##
  * Added a background image to the main interface and the detail window.
  * As such, removed the logo icon from the main interface.
  * Also as such, rearranged the main interface to take advantage of the extra space.
  * Also also as such, made the main interface a bit more comfortable to navigate.

## 0.6.5 (May 26, 2009, [r101](https://code.google.com/p/geohashdroid/source/detail?r=101)) ##
  * No real changes from pre2.
  * In pre2, fixed several infobox size issues with the new minutes/seconds readouts.
  * Added minutes/seconds readouts to the detail screen.

## 0.6.5-pre1 (May 20, 2009, [r91](https://code.google.com/p/geohashdroid/source/detail?r=91)) ##
  * Added coordinate display options to allow minute and second readouts.
  * Well, just added that to the infoboxes so far.  Detail screen will be later.
  * And what's more, the jumbo infobox comes out a bit smaller to make the new (very long) minute/second readouts fit.  Huh.  0.6.5-pre2 should fix that.

## 0.6.4 (April 25, 2009, [r78](https://code.google.com/p/geohashdroid/source/detail?r=78)) ##
  * Added the settings menu to the detailed info screen.
  * The detailed info screen will now update on resume, handy for coming back from the settings menu (see above).
  * Reduced the precision of the accuracy readout.  Imperial measurements were looking really wonky.

## 0.6.4-pre1 (April 25, 2009, [r71](https://code.google.com/p/geohashdroid/source/detail?r=71)) ##
  * Added accuracy readouts to both infoboxes and the detailed info screen.
  * Fixed a few problems regarding how the detailed info screen deals with cell tower-based location (as opposed to GPS-based).
  * The phone won't fall asleep with the detailed info screen up anymore.
  * Numerous optimizations across the board.

## 0.6.3 (April 8, 2009, [r49](https://code.google.com/p/geohashdroid/source/detail?r=49)) ##
  * Put an icon on the detail screen option.

## 0.6.3-pre1 (April 8, 2009, [r47](https://code.google.com/p/geohashdroid/source/detail?r=47)) ##
  * Added a detailed information activity.  This provides a bit more data and in a much bigger font than the infobox.
  * Added a jumbo infobox, just in case the smaller one wasn't enough for you.
  * Removed the title bar from the main map to give a bit more screen real estate.

## 0.6.2-[r2](https://code.google.com/p/geohashdroid/source/detail?r=2) (also March 25, 2009, [r35](https://code.google.com/p/geohashdroid/source/detail?r=35)) ##
  * After testing results from The People(tm), added a button that links to the description of Geohashing from the wiki.
  * Next mini-release session will use something other than -r# to avoid confusing the wiki markup with revision numbers.

## 0.6.2-[r1](https://code.google.com/p/geohashdroid/source/detail?r=1) (March 25, 2009) ##

  * Finally got better icons for graticule-by-location and graticule map.
  * GraticuleMap now responds to a net.exclaimindustries.geohashdroid.PICK\_GRATICULE Intent, making it usable by anything else that may need a graticule picked (hey, I can dream), or allowing it to be replaced by anyone else who can make a better graticule picker.
  * First version beta'd by someone else.  Thanks, jelloboi!

## 0.6.1 (March 18, 2009) ##

  * Made all the wakeup activities in MainMap happen on onResume instead of onStart, which prevents a crash from happening at wakeup time if the user forces the phone to sleep.
  * If the phone loses GPS during MainMap for whatever reason (most likely: lost signal), it will now fall back to cell towers for both autozooming and the infobox.  Assuming the user has cell tower locating turned on, of course.

## 0.6.0 (March 15, 2009) ##

  * First tagged beta release (so next time, I'll be able to keep track of what changed)