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
import android.net.Uri;
import android.text.format.DateFormat;
import android.util.Log;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.util.Graticule;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.geohashdroid.util.UnitConverter;
import net.exclaimindustries.tools.DateTools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.client.methods.HttpUriRequest;
import cz.msebera.android.httpclient.entity.ContentType;
import cz.msebera.android.httpclient.entity.mime.MultipartEntityBuilder;
import cz.msebera.android.httpclient.entity.mime.content.ByteArrayBody;
import cz.msebera.android.httpclient.entity.mime.content.StringBody;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.message.BasicNameValuePair;

/**
 * Various stateless utility methods to query a mediawiki server
 */
public class WikiUtils {
    /**
     * The base URL for all wiki activities.  Remember the trailing slash!
     */
    private static final String WIKI_BASE_URL = "https://geohashing.site/";

    /**
     * The URL for the MediaWiki API.  There's no trailing slash here.
     */
    private static final String WIKI_API_URL = WIKI_BASE_URL + "/api.php";

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
     * Gets the JSON from the wiki request.  Also throws WikiExceptions if
     * something goes wrong.  Note that JSON parsing failures are covered by a
     * WikiException.
     *
     * @param httpClient an active HTTP session
     * @param httpReq    an HTTP request (GET or POST)
     * @return the resulting JSONObject
     * @throws IOException   the connection failed somehow
     * @throws WikiException the connection succeeded, but the wiki threw an error
     */
    @NonNull
    private static JSONObject getJsonFromClient(@NonNull CloseableHttpClient httpClient,
                                                @NonNull HttpUriRequest httpReq) throws Exception {
        try {
            HttpResponse response = httpClient.execute(httpReq);

            int responseCode = response.getStatusLine().getStatusCode();
            if(responseCode != 200) {
                Log.e(DEBUG_TAG, "Error response from server: " + responseCode);
                // Something else will get whatever happened here.
                throw new IOException("Error response from server: " + responseCode);
            }

            // Hoover up that data!
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            response.getEntity().getContent()));
            StringBuilder buffer = new StringBuilder();
            String line = br.readLine();
            while(line != null) {
                buffer.append(line);
                line = br.readLine();
            }

            // What we should have is a chunky blob of JSON.
            JSONObject json = new JSONObject(buffer.toString());

            // Check it for an error first!
            if(json.has("error")) {
                throw new WikiException(
                        getErrorTextId(
                                json
                                        .getJSONObject("error")
                                        .getString("code")));
            }

            return json;
        } catch(JSONException jse) {
            // If anything JSON-wise threw an exception here, assume the wiki is
            // returning bad JSON.  We have an exception code for that.
            Log.e(DEBUG_TAG, "JSONException in getJsonFromClient!", jse);
            throw new WikiException(R.string.wiki_error_json);
        }
    }

    /**
     * Returns whether or not a given wiki page or file exists.
     *
     * @param client an active HTTP session
     * @param pageName   the name of the wiki page
     * @return true if the page exists, false if not
     * @throws WikiException problem with the wiki, translate the ID
     * @throws Exception     anything else happened, use getMessage
     */
    public static boolean doesWikiPageExist(@NonNull CloseableHttpClient client,
                                            @NonNull String pageName) throws Exception {
        // It's GET time!  This is basically the same as the content request, but
        // we really don't need ANY data other than whether or not the page
        // exists, so we won't call for anything.
        Uri.Builder builder = Uri.parse(WIKI_API_URL).buildUpon();
        builder.appendQueryParameter("action", "query")
                        .appendQueryParameter("format", "json")
                        .appendQueryParameter("titles", pageName);

        HttpGet httpGet = new HttpGet(builder.build().toString());
        JSONObject json = getJsonFromClient(client, httpGet);

        try {
            JSONObject pages = json
                    .getJSONObject("query")
                    .getJSONObject("pages");

            // This query CAN take multiple pages, hence why it returns an
            // object capable of holding equally multiple pages.  We just want
            // the one.
            JSONArray ids = pages.names();
            if(ids == null || ids.length() != 1) {
                throw new WikiException(R.string.wiki_error_json);
            }
            JSONObject pageInfo = ids.getJSONObject(0);
            // "invalid" or "missing" both resolve to the same answer: No.
            // Anything else means yes.
            return !(pageInfo.has("missing") || pageInfo.has("invalid"));
        } catch(JSONException e) {
            throw new WikiException(R.string.wiki_error_json);
        }
    }

    /**
     * Gets the version of the wiki.  This may be needed if there is an
     * impending upgrade that breaks certain API calls and we want to make sure
     * we're calling the right one depending on if the Geohashing wiki has
     * upgraded yet.  In times of stable APIs, this probably won't be used.
     *
     * @param client an active HTTP session
     * @return a {@link WikiVersionData} containing all the version data you'll need
     * @throws WikiException problem with the wiki, translate the ID
     * @throws Exception     anything else happened, use getMessage
     */
    @NonNull
    public static WikiVersionData getWikiVersion(@NonNull CloseableHttpClient client) throws Exception {
                // This shouldn't require any special login data or params.
                        Uri.Builder builder = Uri.parse(WIKI_API_URL).buildUpon();
                builder.appendQueryParameter("action", "query")
                                .appendQueryParameter("format", "json")
                                .appendQueryParameter("meta", "siteinfo")
                                .appendQueryParameter("siprop", "general");

        // SiteInfo call!
        HttpGet httpGet = new HttpGet(builder.build().toString());
        JSONObject json = getJsonFromClient(client, httpGet);

        try {
            String version = json
                    .getJSONObject("query")
                    .getJSONObject("general")
                    .getString("generator");

            Log.d(DEBUG_TAG, "The wiki says its version is: " + version);
            return new WikiVersionData(version);
        } catch(JSONException e) {
            throw new WikiException(R.string.wiki_error_json);
        }
    }

    /**
     * Returns the raw content of a wiki page in a single string.  Optionally,
     * also attaches the fields for future resubmission to a HashMap (namely, an
     * edittoken and a timestamp).
     *
     * @param client an active HTTP session
     * @param pageName   the name of the wiki page
     * @param formFields if not null, this hashmap will be filled with the correct HTML form fields to resubmit the page.
     * @return the raw code of the wiki page, or null if the page doesn't exist
     * @throws WikiException problem with the wiki, translate the ID
     * @throws Exception     anything else happened, use getMessage
     */
    @Nullable
    public static String getWikiPage(@NonNull CloseableHttpClient client,
                                     @NonNull String pageName,
                                     @Nullable HashMap<String, String> formFields) throws Exception {
        // Build up a new-style csrf request.
        Uri.Builder uriBuilder = Uri.parse(WIKI_API_URL).buildUpon();
        uriBuilder
                .appendQueryParameter("action", "query")
                .appendQueryParameter("format", "json")
                .appendQueryParameter("prop", "info|revisions")
                .appendQueryParameter("rvprop", "content")
                .appendQueryParameter("rvslots", "*")
                .appendQueryParameter("rvlimit", "1")
                .appendQueryParameter("titles", pageName)
                .appendQueryParameter("meta", "tokens")
                .appendQueryParameter("type", "csrf");

        HttpGet httpGet = new HttpGet(uriBuilder.build().toString());
        JSONObject json = getJsonFromClient(client, httpGet);

        // We hopefully have a page and some tokens.
        JSONObject page = getFirstPageFrom(json);
        String token;
        try {
            token = json
                    .getJSONObject("query")
                    .getJSONObject("tokens")
                    .getString("csrftoken");
        } catch(JSONException e) {
            Log.e(DEBUG_TAG, "JSONException in getWikiPage!", e);
            throw new WikiException(R.string.wiki_error_json);
        }

        // If we got an "invalid" attribute, the page not only doesn't exist,
        // but it CAN'T exist, and is therefore an error.
        if(page.has("invalid"))
            throw new WikiException(R.string.wiki_error_invalid_page);

        if(formFields != null) {
            // If we have a formfields hash ready, populate it with a couple
            // values.
            formFields.clear();
            formFields.put("summary", "An expedition message sent via Geohash Droid for Android.");
            formFields.put("token", token);
            if(page.has("touched"))
                formFields.put("basetimestamp", page.getString("touched"));
        }

        // If we got a "missing" attribute, the page hasn't been made yet, so we
        // return null.
        if(page.has("missing"))
            return null;

        // Otherwise, grab the text from the revision data and return it.
        try {
            return page
                    .getJSONArray("revisions")
                    .getJSONObject(0)
                    .getJSONObject("slots")
                    .getJSONObject("main")
                    .getString("*");
        } catch(JSONException e) {
            Log.e(DEBUG_TAG, "JSONException in getWikiPage!", e);
            throw new WikiException(R.string.wiki_error_json);
        }
    }

    /**
     * Replaces an entire wiki page
     *
     * @param client an active HTTP session
     * @param pageName   the name of the wiki page
     * @param content    the new content of the wiki page to be submitted
     * @param formFields a hashmap with the fields needed (besides pagename and content; those will be filled in this method)
     * @throws WikiException problem with the wiki, translate the ID
     * @throws Exception     anything else happened, use getMessage
     */
    public static void putWikiPage(@NonNull CloseableHttpClient client,
                                   @NonNull String pageName, String content,
                                   @NonNull HashMap<String, String> formFields) throws Exception {
        // If there's no edit token in the hash map, we can't do anything.
        if(!formFields.containsKey("token")) {
            throw new WikiException(R.string.wiki_error_protected);
        }

        HttpPost httpPost = new HttpPost(WIKI_API_URL);

        ArrayList<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("action", "edit"));
        nvps.add(new BasicNameValuePair("title", pageName));
        nvps.add(new BasicNameValuePair("text", content));
        nvps.add(new BasicNameValuePair("format", "json"));
        for(String s : formFields.keySet()) {
            nvps.add(new BasicNameValuePair(s, formFields.get(s)));
        }

        httpPost.setEntity(new UrlEncodedFormEntity(nvps, "utf-8"));

        getJsonFromClient(client, httpPost);

        // And really, that's it.  We're done!
    }

    /**
     * Uploads an image to the wiki
     *
     * @param client  an active HTTP session, wiki login has to have happened before.
     * @param filename    the name of the new image file
     * @param description the description of the image. An initial description will be used as page content for the image's wiki page
     * @param data        a ByteArray containing the raw image data (assuming jpeg encoding, currently).
     */
    public static void putWikiImage(@NonNull CloseableHttpClient client,
                                    @NonNull String filename,
                                    @NonNull String description,
                                    @NonNull byte[] data) throws Exception {
        // At this point, WikiService still has an edit token for the page
        // itself, but that token isn't valid for uploading this image.  That's
        // why we didn't pass formfields into this.  So, we need to fetch that.
        Uri apiUri = Uri.parse(WIKI_API_URL);

        Uri.Builder builder = apiUri.buildUpon();
        builder.appendQueryParameter("action", "query")
                .appendQueryParameter("format", "json")
                .appendQueryParameter("meta", "tokens")
                .appendQueryParameter("type", "csrf");

        HttpGet httpGet = new HttpGet(builder.build().toString());
        JSONObject json = getJsonFromClient(client, httpGet);

        String token;
        try {
            token = json
                    .getJSONObject("query")
                    .getJSONObject("tokens")
                    .getString("csrftoken");
        } catch(JSONException e) {
            Log.e(DEBUG_TAG, "JSONException in putWikiImage!", e);
            throw new WikiException(R.string.wiki_error_json);
        }

        HttpPost httpPost = new HttpPost(WIKI_API_URL);

        // TOKEN GET!  Now we've got us enough to get our upload on!
        MultipartEntityBuilder mpBuilder = MultipartEntityBuilder.create()
                .addPart("action", new StringBody("upload", ContentType.TEXT_PLAIN))
                .addPart("filename", new StringBody(filename, ContentType.create("text/plain", "utf-8")))
                .addPart("comment", new StringBody(description, ContentType.create("text/plain", "utf-8")))
                .addPart("watch", new StringBody("true", ContentType.TEXT_PLAIN))
                .addPart("ignorewarnings", new StringBody("true", ContentType.TEXT_PLAIN))
                .addPart("token", new StringBody(token, ContentType.TEXT_PLAIN))
                .addPart("format", new StringBody("json", ContentType.TEXT_PLAIN))
                .addPart("file", new ByteArrayBody(data, ContentType.create("image/jpeg", "utf-8"), filename));

        httpPost.setEntity(mpBuilder.build());

        getJsonFromClient(client, httpPost);
    }

    /**
     * Retrieves valid login cookies for an HTTP session.  These will be added
     * to the CloseableHttpClient value passed in, so re-use it for future wiki
     * transactions.
     *
     * @param client an active HTTP session.
     * @param wpName     a wiki user name.
     * @param wpPassword the matching password to this user name.
     * @throws WikiException problem with the wiki, translate the ID
     * @throws Exception     anything else happened, use getMessage
     */
    public static void login(@NonNull CloseableHttpClient client,
                             @NonNull String wpName,
                             @NonNull String wpPassword) throws Exception {
        Uri apiUri = Uri.parse(WIKI_API_URL);
        // Step one: The login token itself.
        Uri.Builder builder = apiUri.buildUpon();
        builder.appendQueryParameter("action", "query")
                .appendQueryParameter("format", "json")
                .appendQueryParameter("meta", "tokens")
                .appendQueryParameter("type", "login");

        Log.d(DEBUG_TAG, "Requesting login token...");
        HttpGet httpGet = new HttpGet(builder.build().toString());
        JSONObject json = getJsonFromClient(client, httpGet);

        String token;
        try {
            token = json
                    .getJSONObject("query")
                    .getJSONObject("tokens")
                    .getString("logintoken");
        } catch(JSONException e) {
            Log.e(DEBUG_TAG, "JSONException in login!", e);
            throw new WikiException(R.string.wiki_error_json);
        }

        // Okay, now let's try a login.  I hope this works.
        ArrayList<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("action", "clientlogin"));
        nvps.add(new BasicNameValuePair("username", wpName));
        nvps.add(new BasicNameValuePair("password", wpPassword));
        nvps.add(new BasicNameValuePair("loginreturnurl", WIKI_API_URL));
        nvps.add(new BasicNameValuePair("logintoken", token));
        nvps.add(new BasicNameValuePair("format", "json"));

        HttpPost httpPost = new HttpPost(WIKI_API_URL);
        httpPost.setEntity(new UrlEncodedFormEntity(nvps, "utf-8"));

        Log.d(DEBUG_TAG, "Token obtained, trying login...");
        json = getJsonFromClient(client, httpPost);

        String status;
        try {
            status = json
                    .getJSONObject("clientlogin")
                    .getString("status");
        } catch(JSONException e) {
            Log.e(DEBUG_TAG, "JSONException in login!", e);
            throw new WikiException(R.string.wiki_error_json);
        }


        // Our result will hopefully either be PASS or FAIL.  If it's UI or
        // REDIRECT, we don't cover those cases just yet.  I really hope we
        // don't have to cover those on the Geohashing wiki.
        if(status.equals("UI") || status.equals("REDIRECT")) {
            Log.w(DEBUG_TAG, "The wiki gave us a " + status + " result on login!  The bug reports will be rolling in soon...");
            throw new WikiException(R.string.wiki_error_fancy_schmansy_login);
        }

        // Fail means, well, failure.
        if(status.equals("FAIL")) {
            Log.d(DEBUG_TAG, "Login failure, telling the user this...");
            throw new WikiException(R.string.wiki_error_bad_login);
        }

        // If this ISN'T just PASS at this point, that's very very bad.
        if(!status.equals("PASS")) {
            Log.e(DEBUG_TAG, "The wiki gave us a " + status + " result on login, and I have no clue what that means.");
            throw new WikiException(R.string.wiki_error_unknown);
        }

        // Otherwise, we're good!
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

            return toReturn + getWikiCategories(info);

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

    /**
     * Convenience method for returning the first page element from a query for
     * page data.
     *
     * @param response the JSON response from the query
     * @return a JSONObject corresponding to the first page in that query
     * @throws JSONException this wasn't a page query response, no pages were
     *                       returned (not even the negative-id placeholder
     *                       pages used if the page is invalid or missing), or
     *                       the JSON was otherwise malformed
     */
    @NonNull
    private static JSONObject getFirstPageFrom(@NonNull JSONObject response) throws JSONException {
        Log.d(DEBUG_TAG, "Getting first page from: " + response);

        JSONObject pages = response
                .getJSONObject("query")
                .getJSONObject("pages");

        // This query CAN take multiple pages, hence why it returns an object
        // capable of holding equally multiple pages (which, annoyingly, isn't
        // an array).  We just want the one.
        JSONArray ids = pages.names();
        if(ids == null) {
            // This shouldn't happen, but if it does, force it to throw a
            // JSONException next.
            ids = new JSONArray();
        }
        return pages.getJSONObject(ids.getString(0));
    }
}
