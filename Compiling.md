# The Compiler And You #

Geohash Droid is fairly easy to compile.  There aren't clever compiler tricks or whatnot in the entire ordeal, and only one or two things to remember; otherwise, just grab a stock Android compiler, fire it up, and let it go.  Even easier with the Eclipse plugin if you want.

# What To Remember #

Before you can expect to compile, though, there's just a couple things to note:
  * In the SVN repo as per this writing, Geohash Droid is an Android 2.2 (Froyo) app, using the Google Maps API (API level 8).  It'll run on phones as low as Android 1.5, but I use the screen resolution separation tricks offered since 1.6, as well as the install-on-SD feature offered since 2.2.  Compiling it with 1.5 (API level 3) will most likely just make it complain at you when it gets to the resources, and 1.6 (API level 4) will complain about the installLocation attribute in the Manifest.  Using anything higher will probably work, but I make no guarantees.
  * Also note, there's no IDE-specific files in the repo.  That includes things like an Eclipse .project file; if you plan on importing it into Eclipse directly through Subversion, you may want to make sure the new project wizard runs so you can explicitly tell it it's an Android project, else it might get confused.
  * You will need to supply your own [maps API key](http://code.google.com/android/add-ons/google-apis/maps-overview.html).  To plug it in, you will need to place it in a string resource file of your choosing with the identifier `api_map_key`.  Don't put it in res/values/strings.xml; that'll just confuse you.  Just trust me on this.  You can, for instance, put it in a new file called res/values/private.xml.  A sample XML file with the key might be as simple as:

```
<?xml version="1.0" encoding="UTF-8"?>
<resources>
<string name="api_map_key">PASTE_ACTUAL_KEY_HERE</string>
</resources>
```