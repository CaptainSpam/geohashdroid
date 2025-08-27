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
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.util.Graticule;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.geohashdroid.util.UnitConverter;
import net.exclaimindustries.tools.DOMUtil;
import net.exclaimindustries.tools.DateTools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
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

    private static final String COOKIES_HEADER = "Set-Cookie";

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
     * A bucketload of the usual stuff we grab from a wiki request.
     */
    private static class WikiResponse {
        Document document;
        Element rootElem;
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
     * Returns the content of a http request as an XML Document.  This is to be
     * used only when we know the response to a request will be XML.  Otherwise,
     * this will probably throw an exception.
     *
     * @param httpclient an active HTTP session
     * @param httpreq    an HTTP request (GET or POST)
     * @return a Document containing the contents of the response
     */
    private static Document getHttpDocument(@NonNull CloseableHttpClient httpclient,
                                            @NonNull HttpUriRequest httpreq) throws Exception {
        HttpResponse response = httpclient.execute(httpreq);

        HttpEntity entity = response.getEntity();

        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(entity.getContent());
    }

    /**
     * Opens the given connection and parses its resulting JSON.  Also throws
     * WikiExceptions if something goes wrong.  Note that JSON parsing failures
     * are covered by a WikiException.
     *
     * @param connection the already-prepared HttpURLConnection to connect
     * @return the resulting JSON
     * @throws IOException the connection failed somehow
     * @throws WikiException the connection succeeded, but the wiki threw an error
     */
    @NonNull
    private static JSONObject getJsonFromConnection(@NonNull HttpURLConnection connection) throws IOException, WikiException {
        try {
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                Log.e(DEBUG_TAG, "Error response from server: " + responseCode);
                // Something else will get whatever happened here.
                throw new IOException("Error response from server: " + responseCode);
            }

            // Hoover up that data!
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
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
        } catch (JSONException jse) {
            // If anything JSON-wise threw an exception here, assume the wiki is
            // returning bad JSON.  We have an exception code for that.
            Log.e(DEBUG_TAG, "JSONException in getJsonFromConnection!", jse);
            throw new WikiException(R.string.wiki_error_json);
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Gets a standard {@link WikiResponse} object for a wiki request.  Because
     * I was getting sick of all that boilerplate.
     *
     * @param httpclient an active HTTP session
     * @param httpreq    an HTTP request (GET or POST)
     * @return a WikiResponse containing WikiResponsey stuff
     */
    @NonNull
    private static WikiResponse getWikiResponse(@NonNull CloseableHttpClient httpclient,
                                                @NonNull HttpUriRequest httpreq) throws Exception {
        WikiResponse toReturn = new WikiResponse();

        toReturn.document = getHttpDocument(httpclient, httpreq);

        toReturn.rootElem = toReturn.document.getDocumentElement();
        if(doesResponseHaveError(toReturn.rootElem)) {
            throw new WikiException(getErrorTextId(findErrorCode(toReturn.rootElem)));
        }

        return toReturn;
    }

    /**
     * Returns whether or not a given wiki page or file exists.
     *
     * @param pagename   the name of the wiki page
     * @return true if the page exists, false if not
     * @throws WikiException problem with the wiki, translate the ID
     * @throws Exception     anything else happened, use getMessage
     */
    public static boolean doesWikiPageExist(@NonNull String pagename) throws Exception {
        Uri.Builder builder = Uri.parse(WIKI_API_URL).buildUpon();
        builder.appendQueryParameter("action", "query")
                .appendQueryParameter("format", "json")
                .appendQueryParameter("titles", pagename);

        HttpURLConnection connection = (HttpURLConnection) (new URL(builder.build().toString()).openConnection());
        JSONObject json = getJsonFromConnection(connection);
        JSONObject pageInfo = getFirstPageFrom(json);

        return !(pageInfo.has("missing") || pageInfo.has("invalid"));
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
        // This shouldn't require any special login data or params.
        Uri.Builder builder = Uri.parse(WIKI_API_URL).buildUpon();
        builder.appendQueryParameter("action", "query")
                .appendQueryParameter("format", "json")
                .appendQueryParameter("meta", "siteinfo")
                .appendQueryParameter("siprop", "general");

        HttpURLConnection connection = (HttpURLConnection) (new URL(builder.build().toString()).openConnection());
        JSONObject json = getJsonFromConnection(connection);

        try {
            String version = json
                    .getJSONObject("query")
                    .getJSONObject("general")
                    .getString("generator");

            Log.d(DEBUG_TAG, "The wiki says its version is: " + version);
            return new WikiVersionData(version);
        } catch(JSONException e) {
            Log.e(DEBUG_TAG, "JSONException in getWikiVersion!", e);
            throw new WikiException(R.string.wiki_error_json);
        }
    }

    /**
     * Returns the raw content of a wiki page in a single string.  Optionally,
     * also attaches the fields for future resubmission to a HashMap (namely, an
     * edittoken and a timestamp).
     *
     * @param pagename the name of the wiki page
     * @param cookies cookies fetched from a previous login call
     * @param formfields if not null, this hashmap will be emptied and filled with the correct HTML form fields to resubmit the page
     * @return the raw code of the wiki page, or null if the page doesn't exist
     * @throws WikiException problem with the wiki, translate the ID
     * @throws Exception     anything else happened, use getMessage
     */
    @Nullable
    public static String getWikiPage(@NonNull String pagename,
                                     @NonNull List<HttpCookie> cookies,
                                     @Nullable HashMap<String, String> formfields) throws Exception {
        // Build up a new-style csrf request.
        Uri.Builder uriBuilder = Uri.parse(WIKI_API_URL).buildUpon();
        uriBuilder
                .appendQueryParameter("action", "query")
                .appendQueryParameter("format", "json")
                .appendQueryParameter("prop", "info|revisions")
                .appendQueryParameter("rvprop", "content")
                .appendQueryParameter("rvslots", "*")
                .appendQueryParameter("rvlimit", "1")
                .appendQueryParameter("titles", pagename)
                .appendQueryParameter("meta", "tokens")
                .appendQueryParameter("type", "csrf");

        HttpURLConnection connection = (HttpURLConnection) new URL(uriBuilder.toString()).openConnection();
        addCookiesToConnection(connection, cookies);

        JSONObject json = getJsonFromConnection(connection);

        // We hopefully have a page and some tokens.
        JSONObject page = getFirstPageFrom(json);
        String token;
        try {
            token = json
                    .getJSONObject("query")
                    .getJSONObject("tokens")
                    .getString("csrftoken");
        } catch (JSONException e) {
            Log.e(DEBUG_TAG, "JSONException in getWikiPage!", e);
            throw new WikiException(R.string.wiki_error_json);
        }

        // If we got an "invalid" attribute, the page not only doesn't exist,
        // but it CAN'T exist, and is therefore an error.
        if(page.has("invalid"))
            throw new WikiException(R.string.wiki_error_invalid_page);

        if(formfields != null) {
            // If we have a formfields hash ready, populate it with some values.
            formfields.clear();
            formfields.put("summary", "An expedition message sent via Geohash Droid for Android.");
            formfields.put("token", token);

            if(page.has("touched"))
                formfields.put("basetimestamp", page.getString("touched"));
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
     * Replaces an entire wiki page.
     *
     * @param pagename the name of the wiki page
     * @param content the new content of the wiki page to be submitted
     * @param cookies cookies fetched from a previous login call; will be repopulated with new cookies from this call
     * @param formfields a hashmap with the fields needed (besides pagename and content; those will be filled in this method)
     * @throws WikiException problem with the wiki, translate the ID
     * @throws Exception     anything else happened, use getMessage
     */
    public static void putWikiPage(@NonNull String pagename,
                                   @NonNull String content,
                                   @NonNull List<HttpCookie> cookies,
                                   @NonNull HashMap<String, String> formfields) throws Exception {
        // If there's no edit token in the hash map, we can't do anything.
        if(!formfields.containsKey("token")) {
            throw new WikiException(R.string.wiki_error_protected);
        }

        HttpURLConnection connection = (HttpURLConnection) (new URL(WIKI_API_URL).openConnection());
        addCookiesToConnection(connection, cookies);

        // As this is a POST request, we'll be using form fields.
        Map<String, String> newFormFields = new LinkedHashMap<>();
        newFormFields.put("action", "edit");
        newFormFields.put("title", pagename);
        newFormFields.put("text", content);
        newFormFields.put("format", "json");
        newFormFields.putAll(formfields);

        addFormFieldsToConnection(connection, newFormFields);

        JSONObject json = getJsonFromConnection(connection);

        // Get the result code.  Hopefully it worked.
        String result = json
                .getJSONObject("edit")
                .getString("result");

        if(!result.equals("Success")) {
            // Uh oh.
            Log.e(DEBUG_TAG, "Invalid response from putWikiPage: " + result);
            throw new WikiException(R.string.wiki_error_unknown);
        }
    }

    /**
     * Uploads an image to the wiki
     *
     * @param httpclient  an active HTTP session, wiki login has to have happened before.
     * @param filename    the name of the new image file
     * @param description the description of the image. An initial description will be used as page content for the image's wiki page
     * @param formfields  a formfields hash as modified by getWikiPage containing an edittoken we can use (see the MediaWiki API for reasons why)
     * @param data        a ByteArray containing the raw image data (assuming jpeg encoding, currently).
     */
    public static void putWikiImage(@NonNull CloseableHttpClient httpclient,
                                    @NonNull String filename,
                                    @NonNull String description,
                                    @NonNull HashMap<String, String> formfields,
                                    @NonNull byte[] data) throws Exception {
        if(!formfields.containsKey("token")) {
            throw new WikiException(R.string.wiki_error_unknown);
        }

        HttpPost httppost = new HttpPost(WIKI_API_URL);

        // First, we need an edit token.  Let's get one.
        ArrayList<NameValuePair> tnvps = new ArrayList<>();
        tnvps.add(new BasicNameValuePair("action", "query"));
        tnvps.add(new BasicNameValuePair("prop", "info"));
        tnvps.add(new BasicNameValuePair("intoken", "edit"));
        tnvps.add(new BasicNameValuePair("titles", "UPLOAD_AN_IMAGE"));
        tnvps.add(new BasicNameValuePair("format", "xml"));

        httppost.setEntity(new UrlEncodedFormEntity(tnvps, "utf-8"));

        WikiResponse response = getWikiResponse(httpclient, httppost);

        // Hopefully, a token exists.  If not, a problem exists.
        String token;
        Element page;
        try {
            page = DOMUtil.getFirstElement(response.rootElem, "page");
            token = DOMUtil.getSimpleAttributeText(page, "edittoken");
        } catch(Exception e) {
            throw new WikiException(R.string.wiki_error_xml);
        }

        // We very much need an edit token here.
        if(token == null) {
            throw new WikiException(R.string.wiki_error_xml);
        }

        // TOKEN GET!  Now we've got us enough to get our upload on!
        MultipartEntityBuilder builder = MultipartEntityBuilder.create()
                .addPart("action", new StringBody("upload", ContentType.TEXT_PLAIN))
                .addPart("filename", new StringBody(filename, ContentType.create("text/plain", "utf-8")))
                .addPart("comment", new StringBody(description, ContentType.create("text/plain", "utf-8")))
                .addPart("watch", new StringBody("true", ContentType.TEXT_PLAIN))
                .addPart("ignorewarnings", new StringBody("true", ContentType.TEXT_PLAIN))
                .addPart("token", new StringBody(token, ContentType.TEXT_PLAIN))
                .addPart("format", new StringBody("xml", ContentType.TEXT_PLAIN))
                .addPart("file", new ByteArrayBody(data, ContentType.create("image/jpeg", "utf-8"), filename));

        httppost.setEntity(builder.build());

        getWikiResponse(httpclient, httppost);
    }

    /**
     * Logs into the server and retrieves valid login cookies for the session.
     * You'll need to pass the cookie list from this call to the next method.
     *
     * @param wpName a wiki user name
     * @param wpPassword the matching password to this user name
     * @return a List of HttpCookies populated with fresh login cookies
     * @throws WikiException the wiki threw an error, which may include authentication issues
     * @throws Exception anything else went wrong
     */
    @NonNull
    public static List<HttpCookie> login(@NonNull String wpName,
                                         @NonNull String wpPassword) throws Exception {
        Uri apiUri = Uri.parse(WIKI_API_URL);
        // Step one: The login token itself.
        Uri.Builder builder = apiUri.buildUpon();
        builder.appendQueryParameter("action", "query")
                .appendQueryParameter("format", "json")
                .appendQueryParameter("meta", "tokens")
                .appendQueryParameter("type", "login");

        Log.d(DEBUG_TAG, "Requesting login token...");
        HttpURLConnection connection = (HttpURLConnection) (new URL(builder.build().toString()).openConnection());
        JSONObject json = getJsonFromConnection(connection);
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

        // With the login token received, we should also have at least one
        // cookie from the server with the session.
        List<HttpCookie> loginCookies = getCookiesFromConnection(connection);
        if(loginCookies.isEmpty()) {
            Log.e(DEBUG_TAG, "There weren't any session cookies in the headers?");
            throw new WikiException(R.string.wiki_error_unknown);
        }

        // Right!  With cookies and a token in hand, we want to perform an
        // actual login.
        connection = (HttpURLConnection) (new URL(apiUri.toString()).openConnection());
        addCookiesToConnection(connection, loginCookies);

        // As this is a POST request, we'll be using form fields.
        Map<String, String> formFields = new LinkedHashMap<>();
        formFields.put("action", "clientlogin");
        formFields.put("username", wpName);
        formFields.put("password", wpPassword);
        formFields.put("loginreturnurl", WIKI_API_URL);
        formFields.put("logintoken", token);
        formFields.put("format", "json");

        addFormFieldsToConnection(connection, formFields);

        Log.d(DEBUG_TAG, "Login token obtained, authenticating...");
        json = getJsonFromConnection(connection);
        String status;
        try {
            status = json
                    .getJSONObject("clientlogin")
                    .getString("status");
        } catch(JSONException e) {
            Log.e(DEBUG_TAG, "JSONException in login!", e);
            throw new WikiException(R.string.wiki_error_json);
        }

        // Once the login is successful, the cookies suddenly change out from
        // under us.  Update as need be.
        loginCookies = getCookiesFromConnection(connection);

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

        // At this point, the session indicated by the session cookies is now
        // authenticated.
        return loginCookies;
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

    private static boolean doesResponseHaveError(@Nullable Element elem) {
        if(elem == null) return false;

        try {
            DOMUtil.getFirstElement(elem, "error");
        } catch(Exception ex) {
            return false;
        }

        return true;
    }

    private static String findErrorCode(@Nullable Element elem) {
        if(elem == null) return "UnknownError";

        try {
            Element error = DOMUtil.getFirstElement(elem, "error");
            return DOMUtil.getSimpleAttributeText(error, "code");
        } catch(Exception ex) {
            return "UnknownError";
        }
    }

    /**
     * Retrieves the wiki page name for the given data.  This accounts for
     * globalhashes, too.
     *
     * @param info Info from which a page name will be derived
     * @return said pagename
     */
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
     * Convenience method for extracting a list of cookies from a connection.
     *
     * @param connection HttpURLConnection from which to extract HttpCookies
     * @return a list of HttpCookies (may be empty)
     */
    @NonNull
    private static List<HttpCookie> getCookiesFromConnection(@NonNull HttpURLConnection connection) {
        List<String> cookieHeaders = connection.getHeaderFields().get(COOKIES_HEADER);

        List<HttpCookie> cookies = new ArrayList<>();

        if(cookieHeaders != null) {
            for(String cookie : cookieHeaders) {
                cookies.addAll(HttpCookie.parse(cookie));
            }
        }

        return cookies;
    }

    /**
     * Convenience method to add a list of cookies to an existing connection.
     *
     * @param connection HttpURLConnection to which HttpCookies are to be added
     * @param cookies the aforementioned HttpCookies
     */
    private static void addCookiesToConnection(
            @NonNull HttpURLConnection connection,
            @NonNull List<HttpCookie> cookies) {
        connection.setRequestProperty("Cookie", TextUtils.join(";", cookies));
    }

    /**
     * Convenience method for adding a bunch of form fields to an existing
     * connection.  This WILL have the side effect of setting the Content-type
     * to application/x-www-form-urlencoded, the request method to POST, and
     * other things necessary for form posting.  That's also why this is a
     * private method.
     *
     * @param connection HttpURLConnection to which form fields are to be added
     * @param formFields the aforementioned form fields
     * @throws IOException any of a wide variety of things that shouldn't have happened happened
     */
    private static void addFormFieldsToConnection(
            @NonNull HttpURLConnection connection,
            @NonNull Map<String, String> formFields) throws IOException {
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-type", "application/x-www-form-urlencoded;charset=" + StandardCharsets.UTF_8.name());
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setChunkedStreamingMode(0);

        StringBuilder sb = new StringBuilder();
        for(Map.Entry<String, String> entry : formFields.entrySet()) {
            if(sb.length() != 0)
                sb.append("&");

            sb.append(URLEncoder.encode(
                    entry.getKey(),
                    StandardCharsets.UTF_8.name()));
            sb.append("=");
            sb.append(URLEncoder.encode(
                    entry.getValue(),
                    StandardCharsets.UTF_8.name()));
        }

        try (OutputStream os = connection.getOutputStream()) {
            os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch(Exception e) {
            Log.e(DEBUG_TAG, "Exception during form field writing?  What?", e);
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
        Log.d(DEBUG_TAG, "Getting first page from: " + response.toString());

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
