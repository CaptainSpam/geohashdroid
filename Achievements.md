# Introduction #

The People(tm) demand achievements!  So I figure I should do something about this.  Finally.  We need achievement support!

# How this is going to work #

The basic idea is that there will be an additional button on the wiki post and upload picture screens.  Said button will most likely read "Achievements" with the number of achievements selected in parentheses afterward (so, "Achievements (4)" if you picked four of them; there'll be no parentheses for zero achievements).  The button will go to a series of lists (a new Activity, most likely a LocationAwareActivity, which will go to the following hierarchy:

  * Add achievement...
    * (list of categories of achievements, as per the wiki's Achievements article)
      * (list of achievements under each category)
  * (list of currently-selected achievements)

When the list is first loaded, maybe we should try to load the page and determine what achievements are already there.  We ONLY want to be able to ADD achievements at any given upload.  If an achievement needs to be removed from an already-uploaded page, the user can do that through the normal web interface.  Thus, if the achievements are already in the page, we can remove them from our list.  Of course, in any event, if the achievement already exists on the page, don't upload another copy of it.

The "Add achievement..." list item will go to the categories.

The categories will be simple text strings (sorted alphabetically) and one icon representative of what's inside it.  I'll have to decide these on the fly.

The achievements will be text strings with large text indicating the name and small text indicating a quick description.  And the appropriate icon.  And alphabetically sorted.

Pressing an achievement button will add itself to the initial list (and will back the user out to that list).  If, however, the achievement needs more information besides the date, final destination coordinates, and graticule (and/or anything else that can be determined from context), there'll be a popup asking for this data before going back to the main menu.

Pressing an achievement button on the main menu will ask the user if it should be removed.

# Concerns #

There's no interface on the wiki to get a list of achievements from which to choose.  Meaning I'll have to maintain it manually and hope they don't add new ones frequently.  This also means I'll need a list of what additional options are needed for each achievement if they need any.  So, a robust Achievement class will be needed, as well as a way to parse a built-in XML file for the job.

This also means the icons will have to be included with the app itself to display in the list.  I'd rather not do something like what the My Maps Editor did and download a bunch of them on first startup and dump them on the SD card (that screws up the Gallery and does other weird things if you edit the images).  That could get really slow and would frankly be rude to the operators of the wiki.  Of course, including them in the app itself could significantly increase the size of GHD, and I've gotten feedback indicating people like the small size.

# Timeframe #

Not a clue.  I'm trying to work out the high-end design first.