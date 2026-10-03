/*
 * WikiUtils.java
 * Copyright (C)2009 Thomas Hirsch
 * Geohashdroid Copyright (C)2009 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.wiki;

import android.content.Context;
import android.location.Location;
import android.text.format.DateFormat;
import android.util.Log;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.util.Graticule;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.geohashdroid.util.UnitConverter;
import net.exclaimindustries.tools.DateTools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * <p>
 * Various stateless utility methods to query a mediawiki server.
 * </p>
 *
 * <p>
 * <b>NOTE:</b> All network-related methods here will be run synchronously.
 * That is, this is expecting the caller to be in its own, non-main thread.
 * </p>
 */
public class WikiUtils {
    /**
     * The base URL for all wiki activities.  Remember the trailing slash!
     */
    private static final String WIKI_BASE_URL = "https://geohashing.site/";

    /**
     * The URL for the MediaWiki API.  There's no trailing slash here.
     */
    static final String WIKI_API_URL = WIKI_BASE_URL + "api.php";

    /**
     * The base URL for viewing pages on the wiki.  On the Geohashing wiki, the
     * URL where the API is located isn't what the public sees as the URL for
     * viewing pages, thus we need this.  There IS a trailing slash.
     */
    private static final String WIKI_BASE_VIEW_URL = WIKI_BASE_URL + "geohashing/";

    private static final String DEBUG_TAG = "WikiUtils";

    /**
     * This is a bundle of version data, neatly pre-parsed for easy analysis.
     * This presumes the version will always come in the form of, for instance,
     * "MediaWiki 1.26.2-extrainfo".
     */
    public static class WikiVersionData {
        /**
         * Whether or not this object is valid.  It will be invalid if the
         * string given as input doesn't match the standard version format (i.e.
         * "MediaWiki 1.26.2-extrainfo").  If this is false, assume nothing else
         * in this object can be trusted.
         */
        public final boolean valid;
        /**
         * The raw output from the "generator" part of SiteInfo.  That is, this
         * is the unparsed result, i.e. "MediaWiki 1.26.2-extrainfo".
         */
        public final String rawResult;
        /**
         * The generator name (the name of the software itself).  In most cases,
         * this will be "MediaWiki".
         */
        public final String generatorName;
        /**
         * The raw version string.  That is, anything past the generator name,
         * i.e. "1.26.2-extrainfo".
         */
        public final String rawVersion;
        /**
         * The major version number.  This will probably be 1, unless the
         * MediaWiki team surprises us with MediaWiki 2 all of a sudden.  If
         * they do THAT, chances are this will break anyway.
         */
        public final int majorVersion;
        /**
         * The minor version number.  For example, if the version string is
         * "1.26.2-extrainfo", this will return 26.
         */
        public final int minorVersion;
        /**
         * The revision version number.  For example, if the version string is
         * "1.26.2-extrainfo", this will return 2.
         */
        public final int revision;
        /**
         * Anything after the revision version number.  For example, if the
         * version string is "1.26.2-extrainfo", this will return "extrainfo".
         * Note that this WILL chop off the leading hyphen, if one exists.  This
         * can be blank.
         */
        public final String additional;

        private static final Pattern RE_VERSION = Pattern.compile("(.*)\\s+((\\d+)\\.(\\d+)\\.(\\d+)(.*)?)");

        public WikiVersionData(@NonNull String input) {
            // Let's get parsing!
            rawResult = input;

            Matcher match = RE_VERSION.matcher(input);

            // If it didn't match, it's invalid.
            if(!match.matches()) {
                valid = false;
                generatorName = "";
                rawVersion = "";
                majorVersion = -1;
                minorVersion = -1;
                revision = -1;
                additional = "";
                return;
            }

            boolean localValid = true;

            // Now, assuming these regexes worked...
            generatorName = match.group(1);
            rawVersion = match.group(2);

            int localMajor = -1;
            int localMinor = -1;
            int localRevision = -1;

            try {
                localMajor = Integer.parseInt(Objects.requireNonNull(match.group(3)));
                localMinor = Integer.parseInt(Objects.requireNonNull(match.group(4)));
                localRevision = Integer.parseInt(Objects.requireNonNull(match.group(5)));
            } catch (NumberFormatException | NullPointerException nfe) {
                // Those BETTER be ints, and enough groups.
                localValid = false;
            }

            majorVersion = localMajor;
            minorVersion = localMinor;
            revision = localRevision;

            String localAdditional = match.group(6);
            // The additional part doesn't need to exist.
            if(localAdditional == null)
                localAdditional = "";

            // For convenience, if there's a dash at the start, chop it off.
            if(localAdditional.startsWith("-"))
                localAdditional = localAdditional.substring(1);

            additional = localAdditional;
            valid = localValid;
        }

        @NonNull
        @Override
        public String toString() {
            return valid ? rawResult : "Invalid version data";
        }
    }

    /**
     * The contents of a wiki page and various additional data it might use.
     * This is returned from getWikiPage and is used to contain data that will
     * be passed back into putWikiPage.
     */
    public static class WikiPageData {
        /** The name of the page. */
        @NonNull public final String pagename;

        /**
         * The page content itself.  Will be empty if the page doesn't exist.
         * Must not be empty (after trimming) when submitted to putWikiPage.
         */
        @NonNull public String content;

        /**
         * The CSRF token for editing purposes.  Will be "+\" if something's
         * gone very wrong.
         */
        @NonNull public final String csrfToken;

        /**
         * The base time stamp.  Will be null if the page doesn't exist.  Can be
         * null when submitted to putWikiPage.
         */
        @Nullable public final String touched;

        /**
         * The edit summary.  Will be prepopulated with a placeholder string on
         * construction.  Change as appropriate before passing to putWikiPage.
         */
        @NonNull public String summary;

        private WikiPageData(@NonNull String pagename,
                             @NonNull String content,
                             @NonNull String csrfToken,
                             @Nullable String touched) {
            this.pagename = pagename;
            this.content = content;
            this.csrfToken = csrfToken;
            this.touched = touched;
            this.summary = "An expedition message sent via Geohash Droid for Android";
        }
    }

    /**
     * A simple CookieJar implementation that only stores cookies for a single
     * session.  Since we invoke login on just about every action anyway, that's
     * all we really need.
     */
    private static class SessionCookieJar implements CookieJar {
        private final Map<String, Cookie> cookieMap = new HashMap<>();

        @Override
        public void saveFromResponse(@NonNull HttpUrl url,
                                     @NonNull List<Cookie> cookies) {
            // New cookies!  Keep track and replace any that have changed.
            for(Cookie c : cookies) {
                cookieMap.put(c.name(), c);
            }
        }

        @NonNull
        @Override
        public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
            // At load time, resolve expirations.  This class is meant to be
            // pretty ephemeral, but you never know, right?
            long nowMillis = System.currentTimeMillis();

            Iterator<Map.Entry<String, Cookie>> iter = cookieMap.entrySet().iterator();

            while(iter.hasNext()) {
                Map.Entry<String, Cookie> cur = iter.next();
                if(cur.getValue().expiresAt() <= nowMillis) {
                    iter.remove();
                }
            }

            return List.copyOf(cookieMap.values());
        }
    }

    /**
     * This format is used for all latitude/longitude texts in the wiki.
     */
    private static final DecimalFormat mLatLonFormat = new DecimalFormat("###.0000", new DecimalFormatSymbols(Locale.US));

    /**
     * This format is used for all latitude/longitude <i>links</i> in the wiki.
     * This differs from mLatLonFormat in that it doesn't clip values to four
     * decimal points.
     */
    private static final DecimalFormat mLatLonLinkFormat = new DecimalFormat("###.00000000", new DecimalFormatSymbols(Locale.US));

    /** A Retrofit object singleton. */
    private static Retrofit mRetrofit = null;

    /**
     * Ensures the Retrofit singleton is ready to go by creating it if it's
     * currently null, and then returning it.
     * @return the active Retrofit singleton
     */
    private static Retrofit getRetrofitInstance() {
        if(mRetrofit != null) {
            // It's already been defined, so it's ready to go.
            return mRetrofit;
        }

        mRetrofit = new Retrofit.Builder()
                .baseUrl(WIKI_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(new OkHttpClient()
                        .newBuilder()
                        .cookieJar(new SessionCookieJar())
                        .build())
                .build();

        return mRetrofit;
    }

    /**
     * Gets a WikiQuery instance from the Retrofit instance.
     * @return a fresh WikiQuery instance
     */
    @NonNull
    private static WikiApi.WikiQuery getWikiQuery() {
        return getRetrofitInstance().create(WikiApi.WikiQuery.class);
    }

    /**
     * Returns the wiki view URL.  Attach a wiki page name to this to send it to
     * a browser for viewing.  It will most likely be different from the API
     * URL.
     *
     * @return the wiki view URL
     */
    @NonNull
    public static String getWikiBaseViewUrl() {
        return WIKI_BASE_VIEW_URL;
    }

    /**
     * <p>
     * Processes a wiki response object (wrapped in a Response).  That is, first
     * it checks for these errors:
     * </p>
     *
     * <ul>
     *     <li>Does the response code indicate failure?</li>
     *     <li>Is the response body null?</li>
     *     <li>
     *         Assuming the response body is valid, does it include an error
     *         from the wiki itself?
     *     </li>
     * </ul>
     *
     * <p>
     * If any of that fails, a WikiException is thrown.  Otherwise, returns the
     * non-null response object extracted from the Response object.
     * </p>
     *
     * @param response a finished response (wrapped in its Response object) to process
     * @return the unwrapped WikiApi.BaseWikiResponse-derived object
     * @throws WikiException if there's any errors
     */
    @NonNull
    private static <T extends WikiApi.BaseWikiResponse> T processAndUnwrapResponse(@NonNull Response<T> response)
            throws WikiException {
        if(!response.isSuccessful()) {
            Log.e(DEBUG_TAG, "FAILURE!  Call failed with code " + response.code());
            throw new WikiException(R.string.wiki_error_unknown);
        }

        T responseObj = response.body();
        if(responseObj == null) {
            Log.e(DEBUG_TAG, "FAILURE!  The call was successful, but the response body is null?");
            throw new WikiException(R.string.wiki_error_json);
        }

        if(responseObj.hasError()) {
            throw new WikiException(getErrorTextId(responseObj.getErrorCode()));
        }

        return responseObj;
    }

    /**
     * Returns whether a given wiki page or file exists.
     *
     * @param pagename   the name of the wiki page
     * @return true if the page exists, false if not
     * @throws WikiException problem with the wiki, translate the ID
     * @throws Exception     anything else happened, use getMessage
     */
    public static boolean doesWikiPageExist(@NonNull String pagename) throws Exception {
        WikiApi.WikiQuery wikiQuery = getWikiQuery();

        Log.d(DEBUG_TAG, "Checking if " + pagename + " exists...");
        Call<WikiApi.GetWikiPageExistenceResponse> existenceCall = wikiQuery.getWikiPageExistence(pagename);
        Response<WikiApi.GetWikiPageExistenceResponse> existenceResponse = existenceCall.execute();
        WikiApi.GetWikiPageExistenceResponse existenceObj = processAndUnwrapResponse(existenceResponse);

        return !(existenceObj.isMissing() || existenceObj.isInvalid());
    }

    /**
     * Gets the version of the wiki.  This may be needed if there is an
     * impending upgrade that breaks certain API calls and we want to make sure
     * we're calling the right one depending on if the Geohashing wiki has
     * upgraded yet.  In times of stable APIs, this probably won't be used.
     *
     * @return a {@link WikiVersionData} containing all the version data you'll need
     * @throws WikiException problem with the wiki, translate the ID
     * @throws Exception     anything else happened, use getMessage
     */
    @NonNull
    public static WikiVersionData getWikiVersion() throws Exception {
        WikiApi.WikiQuery wikiQuery = getWikiQuery();

        Log.d(DEBUG_TAG, "Fetching wiki version data...");
        Call<WikiApi.WikiVersionResponse> versionCall = wikiQuery.getWikiVersion();
        Response<WikiApi.WikiVersionResponse> versionResponse = versionCall.execute();
        WikiVersionData versionData = processAndUnwrapResponse(versionResponse).getVersionData();

        Log.d(DEBUG_TAG, "The wiki says its version is: " + versionData.rawResult);
        return versionData;
    }

    /**
     * Fetches and returns the necessary data from a wiki page to edit it later.
     *
     * @param pagename the name of the wiki page
     * @return a WikiPageData with necessary data
     * @throws WikiException problem with the wiki, translate the ID
     * @throws Exception     anything else happened, use getMessage
     */
    @NonNull
    public static WikiPageData getWikiPage(@NonNull String pagename)
            throws Exception {
        WikiApi.WikiQuery wikiQuery = getWikiQuery();

        Log.d(DEBUG_TAG, "Fetching page content for " + pagename + "...");
        Call<WikiApi.GetWikiPageResponse> pageCall = wikiQuery.getWikiPage(pagename);
        Response<WikiApi.GetWikiPageResponse> pageResponse = pageCall.execute();
        WikiApi.GetWikiPageResponse pageData = processAndUnwrapResponse(pageResponse);

        if(pageData.isInvalid()) {
            // Uh oh.  This page can't exist.
            Log.e(DEBUG_TAG, "The wiki says that page is invalid!");
            throw new WikiException(R.string.wiki_error_invalid_page);
        }

        Log.d(DEBUG_TAG, "Page retrieved!");
        return new WikiPageData(
                pagename,
                pageData.getPageContent(),
                pageData.getCsrfToken(),
                pageData.getTouched());
    }

    /**
     * Replaces an entire wiki page.
     *
     * @param pageData all the data needed for the page, preferably initially retrieved from getWikiPage
     * @throws WikiException problem with the wiki, translate the ID
     * @throws Exception     anything else happened, use getMessage
     */
    public static void putWikiPage(@NonNull WikiPageData pageData)
            throws Exception {
        if(pageData.csrfToken.equals("+\\")) {
            // If the CSRF token is somehow "+\", that means something's wrong.
            throw new WikiException(R.string.wiki_error_protected);
        }

        if(pageData.pagename.isEmpty()) {
            // Now this REALLY shouldn't have happened.
            throw new IllegalArgumentException("putWikiPage needs a pagename!");
        }

        if(pageData.content.isEmpty()) {
            throw new IllegalArgumentException("Refusing to upload an empty wiki page!");
        }

        WikiApi.WikiQuery wikiQuery = getWikiQuery();

        Log.d(DEBUG_TAG, "Putting new page content for " + pageData.pagename + "...");
        Call<WikiApi.PostWikiPageResponse> pageCall = WikiApi.makePostWikiPage(
                wikiQuery,
                pageData.pagename,
                pageData.content,
                pageData.csrfToken,
                pageData.summary,
                pageData.touched);
        Response<WikiApi.PostWikiPageResponse> pageResponse = pageCall.execute();
        String result = processAndUnwrapResponse(pageResponse).getResult();

        if(!result.equals("Success")) {
            // Well, crap, something's wrong.
            Log.e(DEBUG_TAG, "Invalid response from putWikiPage: " + result);
            throw new WikiException(R.string.wiki_error_unknown);
        }
    }

    /**
     * Uploads an image to the wiki.
     *
     * @param filename the name of the new image file
     * @param description the description of the image. An initial description will be used as page content for the image's wiki page
     * @param data a ByteArray containing the raw JPEG-encoded image data
     */
    public static void putWikiImage(@NonNull String filename,
                                    @NonNull String description,
                                    @NonNull byte[] data) throws Exception {
        // At this point, WikiService still has an edit token for the page
        // itself, but that token isn't valid for uploading this image.  So, we
        // need to fetch that.
        WikiApi.WikiQuery wikiQuery = getWikiQuery();

        Log.d(DEBUG_TAG, "Fetching a fresh CSRF token for an image upload...");

        Call<WikiApi.GetImageUploadTokenResponse> tokenCall = wikiQuery.getImageUploadToken();
        Response<WikiApi.GetImageUploadTokenResponse> tokenResponse = tokenCall.execute();
        String token = processAndUnwrapResponse(tokenResponse).getCsrfToken();

        // Token!  Now, hopefully Retrofit makes multipart POST uploads
        // simpler than the last time I wrote this...
        Log.d(DEBUG_TAG, "Token retrieved!  Attempting an upload...");
        Call <WikiApi.PostWikiImageResponse> uploadCall = WikiApi.makePostWikiImage(
                wikiQuery,
                filename,
                description,
                token,
                data);
        Response<WikiApi.PostWikiImageResponse> uploadResponse = uploadCall.execute();
        String result = processAndUnwrapResponse(uploadResponse).getResult();

        if(!result.equals("Success")) {
            Log.e(DEBUG_TAG, "Invalid response from putWikiPage: " + result);
            throw new WikiException(R.string.wiki_error_unknown);
        }

        Log.d(DEBUG_TAG, "Success!");
    }

    /**
     * Logs into the server and retrieves valid login cookies for the session.
     * Future calls during this session will use those cookies to do stuff.
     *
     * @param wpName a wiki username
     * @param wpPassword the matching password to this username
     * @throws WikiException the wiki threw an error, which may include authentication issues
     * @throws Exception anything else went wrong
     */
    public static void login(@NonNull String wpName,
                             @NonNull String wpPassword) throws Exception {
        WikiApi.WikiQuery wikiQuery = getWikiQuery();

        // First, grab us a token.
        Call<WikiApi.LoginTokenResponse> tokenCall = wikiQuery.getLoginToken();

        // Remember, we're under control of WikiService at this point, and
        // WikiService makes its own thread to process network stuff.  Ergo, we
        // use the synchronous versions of the calls.
        Log.d(DEBUG_TAG, "Fetching a login token...");
        Response<WikiApi.LoginTokenResponse> tokenResponse = tokenCall.execute();

        String token = processAndUnwrapResponse(tokenResponse).getLoginToken();

        Log.d(DEBUG_TAG, "Success!  Using the token to do a client login...");

        // Token in hand, we can try a login with the user's credentials.
        Call<WikiApi.ClientLoginResponse> loginCall = WikiApi.makePostClientLogin(
                wikiQuery,
                wpName,
                wpPassword,
                token);

        Response<WikiApi.ClientLoginResponse> loginResponse = loginCall.execute();

        // Excellent!  Now, do we have a logged in set of cookies?
        String loginStatus = processAndUnwrapResponse(loginResponse).getStatus();

        // Our result will hopefully either be PASS or FAIL.  If it's UI or
        // REDIRECT, we don't cover those cases just yet.  I really hope we
        // don't have to cover those on the Geohashing wiki.
        if(loginStatus.equals("UI") || loginStatus.equals("REDIRECT")) {
            Log.w(DEBUG_TAG, "The wiki gave us a " + loginStatus + " result on login!  The bug reports will be rolling in soon...");
            throw new WikiException(R.string.wiki_error_fancy_schmansy_login);
        }

        // Fail means, well, failure.
        if(loginStatus.equals("FAIL")) {
            Log.d(DEBUG_TAG, "Login failure, telling the user this...");
            throw new WikiException(R.string.wiki_error_bad_login);
        }

        // If this ISN'T just PASS at this point, that's very very bad.
        if(!loginStatus.equals("PASS")) {
            Log.e(DEBUG_TAG, "The wiki gave us a " + loginStatus + " result on login, and I have no clue what that means.");
            throw new WikiException(R.string.wiki_error_unknown);
        }

        // Otherwise, we're good!  The cookies are in the client.
        Log.d(DEBUG_TAG, "Success!");
    }

    /**
     * Gets the text ID that corresponds to a given error code.  If the code
     * isn't recognized, this returns wiki_error_unknown instead.  Note that
     * this WON'T understand a non-error condition; check to make sure it isn't
     * first.
     *
     * @param code String returned from the wiki
     * @return text ID that corresponds to that error
     */
    private static int getErrorTextId(@Nullable String code) {
        // If we don't recognize the error (or shouldn't get it at all), we use
        // this, because we don't have the slightest clue what's wrong.
        int error = R.string.wiki_error_unknown;

        if(code == null) return error;

        // First, general errors.  These are the only general ones we care
        // about; there's more, but those aren't likely to come up.
        switch(code) {
            case "unsupportednamespace":
                error = R.string.wiki_error_illegal_namespace;
                break;
            case "protectednamespace-interface":
            case "protectednamespace":
            case "customcssjsprotected":
            case "cascadeprotected":
            case "protectedpage":
                error = R.string.wiki_error_protected;
                break;
            case "confirmemail":
                error = R.string.wiki_error_email_confirm;
                break;
            case "permissiondenied":
                error = R.string.wiki_error_permission_denied;
                break;
            case "blocked":
            case "autoblocked":
                error = R.string.wiki_error_blocked;
                break;
            case "ratelimited":
                error = R.string.wiki_error_rate_limit;
                break;
            case "readonly":
                error = R.string.wiki_error_read_only;
                break;

            // Then, login errors.  These come from the result attribute.
            case "Illegal":
            case "NoName":
            case "CreateBlocked":
                error = R.string.wiki_error_bad_username;
                break;
            case "NotExists":
                error = R.string.wiki_error_username_nonexistant;
                break;
            case "EmptyPass":
            case "WrongPass":
            case "WrongPluginPass":
                error = R.string.wiki_error_bad_password;
                break;
            case "Throttled":
                error = R.string.wiki_error_throttled;
                break;

            // Next, edit errors.  These come from the error element, code
            // attribute.
            case "protectedtitle":
                //noinspection DuplicateBranchesInSwitch
                error = R.string.wiki_error_protected;
                break;
            case "cantcreate":
            case "cantcreate-anon":
                error = R.string.wiki_error_no_create;
                break;
            case "spamdetected":
                error = R.string.wiki_error_spam;
                break;
            case "filtered":
                error = R.string.wiki_error_filtered;
                break;
            case "contenttoobig":
                error = R.string.wiki_error_too_big;
                break;
            case "noedit":
            case "noedit-anon":
                error = R.string.wiki_error_no_edit;
                break;
            case "editconflict":
                error = R.string.wiki_error_conflict;
                break;

            // If all else fails, log what we got.
            default:
                Log.d(DEBUG_TAG, "Unknown error code came back: " + code);
                break;
        }

        return error;
    }

    /**
     * Retrieves the wiki page name for the given data.  This accounts for
     * globalhashes, too.
     *
     * @param info Info from which a page name will be derived
     * @return said pagename
     */
    @NonNull
    public static String getWikiPageName(@NonNull Info info) {
        String date = DateTools.getHyphenatedDateString(info.getCalendar());

        Graticule g = info.getGraticule();

        if(g == null) {
            return date + "_global";
        } else {
            String lat = g.getLatitudeString(true);
            String lon = g.getLongitudeString(true);

            return date + "_" + lat + "_" + lon;
        }
    }

    /**
     * <p>
     * Retrieves the text for the Expedition template appropriate for the given
     * Info.
     * </p>
     *
     * <p>
     * TODO: The wiki doesn't appear to have an Expedition template for
     * globalhashing yet.
     * </p>
     *
     * @param info Info from which an Expedition template will be generated
     * @param c    Context so we can grab the globalhash template if we need it
     * @return said template
     */
    @NonNull
    public static String getWikiExpeditionTemplate(@NonNull Info info,
                                                   @NonNull Context c) {
        String date = DateTools.getHyphenatedDateString(info.getCalendar());

        Graticule g = info.getGraticule();

        if(g == null) {
            // Until a proper template can be made in the wiki itself, we'll
            // have to settle for this...
            InputStream is = c.getResources().openRawResource(R.raw.globalhash_template);
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);

            // Now, read in each line and do all substitutions on it.
            String input;
            StringBuilder toReturn = new StringBuilder();
            try {
                while((input = br.readLine()) != null) {
                    input = input.replaceAll("%%LATITUDE%%", UnitConverter.makeLatitudeCoordinateString(c, info.getLatitude(), true, UnitConverter.OUTPUT_DETAILED));
                    input = input.replaceAll("%%LONGITUDE%%", UnitConverter.makeLongitudeCoordinateString(c, info.getLongitude(), true, UnitConverter.OUTPUT_DETAILED));
                    input = input.replaceAll("%%LATITUDEURL%%", Double.valueOf(info.getLatitude()).toString());
                    input = input.replaceAll("%%LONGITUDEURL%%", Double.valueOf(info.getLongitude()).toString());
                    input = input.replaceAll("%%DATENUMERIC%%", date);
                    input = input.replaceAll("%%DATESHORT%%", DateFormat.format("E MMM d yyyy", info.getCalendar()).toString());
                    input = input.replaceAll("%%DATEGOOGLE%%", DateFormat.format("d+MMM+yyyy", info.getCalendar()).toString());
                    toReturn.append(input).append("\n");
                }
            } catch(IOException e) {
                // Don't do anything; just assume we're done.
            }

            return toReturn.toString() + getWikiCategories(info);

        } else {
            String lat = g.getLatitudeString(true);
            String lon = g.getLongitudeString(true);

            return "{{subst:Expedition|lat=" + lat + "|lon=" + lon + "|date=" + date + "}}";
        }
    }

    /**
     * Retrieves the text for the categories to put on the wiki for pictures.
     *
     * @param info Info from which categories will be generated
     * @return said categories
     */
    @NonNull
    public static String getWikiCategories(@NonNull Info info) {
        String date = DateTools.getHyphenatedDateString(info.getCalendar());

        String toReturn = "[[Category:Meetup on "
                + date + "]]\n";

        Graticule g = info.getGraticule();

        if(g == null) {
            return toReturn + "[[Category:Globalhash]]";
        } else {
            String lat = g.getLatitudeString(true);
            String lon = g.getLongitudeString(true);

            return toReturn + "[[Category:Meetup in " + lat + " "
                    + lon + "]]";
        }
    }

    /**
     * Makes a location tag for the wiki that links to OpenStreetMap.  Or just
     * returns an empty string if you gave it a null location.  That's entirely
     * valid; if the user's location isn't known, the tag should be empty.
     *
     * @param loc the Location
     * @return an OpenStreetMap wiki tag
     */
    @NonNull
    public static String makeLocationTag(@Nullable Location loc) {
        if(loc != null) {
            return " [https://openstreetmap.org/?mlat="
                    + mLatLonLinkFormat.format(loc.getLatitude())
                    + "&mlon="
                    + mLatLonLinkFormat.format(loc.getLongitude())
                    + "&zoom=16 @"
                    + mLatLonFormat.format(loc.getLatitude())
                    + ","
                    + mLatLonFormat.format(loc.getLongitude())
                    + "]";
        } else {
            return "";
        }
    }
}
