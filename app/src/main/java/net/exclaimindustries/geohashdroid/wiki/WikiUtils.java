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
import net.exclaimindustries.tools.DOMUtil;
import net.exclaimindustries.tools.DateTools;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

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

import javax.xml.parsers.DocumentBuilderFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cz.msebera.android.httpclient.HttpEntity;
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
            } catch (NumberFormatException nfe) {
                // Those BETTER be ints.
                localValid = false;
            } catch (NullPointerException npe) {
                // There BETTER be enough groups.
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
            throw new WikiException(findErrorCode(toReturn.rootElem));
        }

        return toReturn;
    }

    /**
     * Returns whether or not a given wiki page or file exists.
     *
     * @param httpclient an active HTTP session
     * @param pagename   the name of the wiki page
     * @return true if the page exists, false if not
     * @throws WikiException problem with the wiki, translate the ID
     * @throws Exception     anything else happened, use getMessage
     */
    public static boolean doesWikiPageExist(@NonNull CloseableHttpClient httpclient,
                                            @NonNull String pagename) throws Exception {
        // It's GET time!  This is basically the same as the content request, but
        // we really don't need ANY data other than whether or not the page
        // exists, so we won't call for anything.
        HttpGet httpget = new HttpGet(WIKI_API_URL + "?action=query&format=xml&titles="
                + URLEncoder.encode(pagename, "UTF-8"));

        WikiResponse response = getWikiResponse(httpclient, httpget);

        Element pageElem;
        try {
            pageElem = DOMUtil.getFirstElement(response.rootElem, "page");
        } catch(Exception e) {
            throw new WikiException(WikiException.INTERNAL_ERROR_BAD_XML);
        }

        // "invalid" or "missing" both resolve to the same answer: No.  Anything
        // else means yes.
        return !(pageElem.hasAttribute("invalid") || pageElem.hasAttribute("missing"));
    }

    /**
     * Gets the version of the wiki.  This may be needed if there is an
     * impending upgrade that breaks certain API calls and we want to make sure
     * we're calling the right one depending on if the Geohashing wiki has
     * upgraded yet.  In times of stable APIs, this probably won't be used.
     *
     * @param httpclient an active HTTP session
     * @return a {@link WikiVersionData} containing all the version data you'll need
     * @throws WikiException problem with the wiki, translate the ID
     * @throws Exception     anything else happened, use getMessage
     */
    @NonNull
    public static WikiVersionData getWikiVersion(@NonNull CloseableHttpClient httpclient) throws Exception {
        // SiteInfo call!
        HttpGet httpget = new HttpGet(WIKI_API_URL + "?action=query&format=xml&meta=siteinfo&siprop=general");

        WikiResponse response = getWikiResponse(httpclient, httpget);

        Element generalElem;
        try {
            generalElem = DOMUtil.getFirstElement(response.rootElem, "general");
        } catch(Exception e) {
            throw new WikiException(WikiException.INTERNAL_ERROR_BAD_XML);
        }

        // If the generator attribute isn't there, there's a problem.
        if(!generalElem.hasAttribute("generator")) {
            throw new WikiException(WikiException.INTERNAL_ERROR_BAD_XML);
        }

        // Finally, we've got us a WikiVersionData!
        return new WikiVersionData(generalElem.getAttribute("generator"));
    }

    /**
     * Returns the raw content of a wiki page in a single string.  Optionally,
     * also attaches the fields for future resubmission to a HashMap (namely, an
     * edittoken and a timestamp).
     *
     * @param httpclient an active HTTP session
     * @param pagename   the name of the wiki page
     * @param formfields if not null, this hashmap will be filled with the correct HTML form fields to resubmit the page.
     * @return the raw code of the wiki page, or null if the page doesn't exist
     * @throws WikiException problem with the wiki, translate the ID
     * @throws Exception     anything else happened, use getMessage
     */
    public static String getWikiPage(@NonNull CloseableHttpClient httpclient,
                                     @NonNull String pagename,
                                     @Nullable HashMap<String, String> formfields) throws Exception {
        // We can use a GET statement here.
        HttpGet httpget = new HttpGet(WIKI_API_URL + "?action=query&format=xml&prop="
                + URLEncoder.encode("info|revisions", "UTF-8")
                + "&rvprop=content&format=xml&intoken=edit&titles="
                + URLEncoder.encode(pagename, "UTF-8"));

        String page;
        WikiResponse response = getWikiResponse(httpclient, httpget);

        Element pageElem;
        Element text;
        try {
            pageElem = DOMUtil.getFirstElement(response.rootElem, "page");
        } catch(Exception e) {
            throw new WikiException(WikiException.INTERNAL_ERROR_BAD_XML);
        }

        // If we got an "invalid" attribute, the page not only doesn't exist,
        // but it CAN'T exist, and is therefore an error.
        if(pageElem.hasAttribute("invalid"))
            throw new WikiException(WikiException.INTERNAL_ERROR_INVALID_PAGE);

        if(formfields != null) {
            // If we have a formfields hash ready, populate it with a couple
            // values.
            formfields.put("summary", "An expedition message sent via Geohash Droid for Android.");
            if(pageElem.hasAttribute("edittoken"))
                formfields.put("token", DOMUtil.getSimpleAttributeText(pageElem, "edittoken"));
            if(pageElem.hasAttribute("touched"))
                formfields.put("basetimestamp", DOMUtil.getSimpleAttributeText(pageElem, "touched"));
        }

        // If we got a "missing" attribute, the page hasn't been made yet, so we
        // return null.
        if(pageElem.hasAttribute("missing"))
            return null;

        // Otherwise, get the text and fill out the form fields.
        try {
            text = DOMUtil.getFirstElement(pageElem, "rev");
        } catch(Exception e) {
            throw new WikiException(WikiException.INTERNAL_ERROR_BAD_XML);
        }

        page = DOMUtil.getSimpleElementText(text);

        return page;
    }

    /**
     * Replaces an entire wiki page
     *
     * @param httpclient an active HTTP session
     * @param pagename   the name of the wiki page
     * @param content    the new content of the wiki page to be submitted
     * @param formfields a hashmap with the fields needed (besides pagename and content; those will be filled in this method)
     * @throws WikiException problem with the wiki, translate the ID
     * @throws Exception     anything else happened, use getMessage
     */
    public static void putWikiPage(@NonNull CloseableHttpClient httpclient,
                                   @NonNull String pagename, String content,
                                   @NonNull HashMap<String, String> formfields) throws Exception {
        // If there's no edit token in the hash map, we can't do anything.
        if(!formfields.containsKey("token")) {
            throw new WikiException(WikiException.INTERNAL_ERROR_PROTECTED);
        }

        HttpPost httppost = new HttpPost(WIKI_API_URL);

        ArrayList<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("action", "edit"));
        nvps.add(new BasicNameValuePair("title", pagename));
        nvps.add(new BasicNameValuePair("text", content));
        nvps.add(new BasicNameValuePair("format", "xml"));
        for(String s : formfields.keySet()) {
            nvps.add(new BasicNameValuePair(s, formfields.get(s)));
        }

        httppost.setEntity(new UrlEncodedFormEntity(nvps, "utf-8"));

        getWikiResponse(httpclient, httppost);

        // And really, that's it.  We're done!
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
            throw new WikiException(WikiException.INTERNAL_ERROR_GENERIC);
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
            throw new WikiException(WikiException.INTERNAL_ERROR_BAD_XML);
        }

        // We very much need an edit token here.
        if(token == null) {
            throw new WikiException(WikiException.INTERNAL_ERROR_BAD_XML);
        }

        // TOKEN GET!  Now we've got us enough to get our upload on!
        MultipartEntityBuilder builder = MultipartEntityBuilder.create()
                .addPart("action", new StringBody("upload", ContentType.TEXT_PLAIN))
                .addPart("filename", new StringBody(filename, ContentType.TEXT_PLAIN))
                .addPart("comment", new StringBody(description, ContentType.TEXT_PLAIN))
                .addPart("watch", new StringBody("true", ContentType.TEXT_PLAIN))
                .addPart("ignorewarnings", new StringBody("true", ContentType.TEXT_PLAIN))
                .addPart("token", new StringBody(token, ContentType.TEXT_PLAIN))
                .addPart("format", new StringBody("xml", ContentType.TEXT_PLAIN))
                .addPart("file", new ByteArrayBody(data, ContentType.create("image/jpeg", "utf-8"), filename));

        httppost.setEntity(builder.build());

        getWikiResponse(httpclient, httppost);
    }

    /**
     * Retrieves valid login cookies for an HTTP session.  These will be added
     * to the CloseableHttpClient value passed in, so re-use it for future wiki
     * transactions.
     *
     * @param httpclient an active HTTP session.
     * @param wpName     a wiki user name.
     * @param wpPassword the matching password to this user name.
     * @throws WikiException problem with the wiki, translate the ID
     * @throws Exception     anything else happened, use getMessage
     */
    public static void login(@NonNull CloseableHttpClient httpclient,
                             @NonNull String wpName,
                             @NonNull String wpPassword) throws Exception {
        HttpPost httppost = new HttpPost(WIKI_API_URL);

        // Login changes depending on version.  Once we know that the GHD wiki
        // has upgraded, this will probably go away.
        WikiVersionData version = getWikiVersion(httpclient);

        if(!version.valid) {
            throw new WikiException(WikiException.INTERNAL_ERROR_GENERIC);
        }

        if(version.minorVersion >= 27) {
            // The new style.  This one requires the clientLogin action.  I'm
            // really hoping I won't have to implement a CAPTCHA or 2FA
            // interface for this, else we're going to have some serious issues.
            // For now, though, grab a token.
            Log.d(DEBUG_TAG, "The wiki is running 1.27 or higher, going with the new login method...");
            HttpGet httpget = new HttpGet(WIKI_API_URL + "?action=query&format=xml&meta=tokens&type=login");

            WikiResponse response = getWikiResponse(httpclient, httpget);

            Element tokenElem;
            String token;
            try {
                tokenElem = DOMUtil.getFirstElement(response.rootElem, "tokens");
                token = DOMUtil.getSimpleAttributeText(tokenElem, "logintoken");
            } catch(Exception e) {
                Log.d(DEBUG_TAG, "Couldn't get a token!");
                throw new WikiException(WikiException.INTERNAL_ERROR_BAD_XML);
            }

            // Okay, now let's try a login.  I hope this works.
            ArrayList<NameValuePair> nvps = new ArrayList<>();
            nvps.add(new BasicNameValuePair("action", "clientlogin"));
            nvps.add(new BasicNameValuePair("username", wpName));
            nvps.add(new BasicNameValuePair("password", wpPassword));
            nvps.add(new BasicNameValuePair("loginreturnurl", WIKI_API_URL));
            nvps.add(new BasicNameValuePair("logintoken", token));
            nvps.add(new BasicNameValuePair("format", "xml"));

            httppost.setEntity(new UrlEncodedFormEntity(nvps, "utf-8"));

            Log.d(DEBUG_TAG, "Token obtained, trying login...");
            response = getWikiResponse(httpclient, httppost);

            Element login;
            String status;
            try {
                login = DOMUtil.getFirstElement(response.rootElem, "clientlogin");
                status = DOMUtil.getSimpleAttributeText(login, "status");

                // If we got a clientlogin response but no status in it, I
                // just... what?
                if(status == null) throw new WikiException(WikiException.INTERNAL_ERROR_GENERIC);
            } catch(WikiException we) {
                throw we;
            } catch (Exception e) {
                throw new WikiException(WikiException.INTERNAL_ERROR_BAD_XML);
            }

            // Our result will hopefully either be PASS or FAIL.  If it's UI or
            // REDIRECT, we don't cover those cases just yet.  I really hope we
            // don't have to cover those on the Geohashing wiki.
            if(status.equals("UI") || status.equals("REDIRECT")) {
                Log.w(DEBUG_TAG, "The wiki gave us a " + status + " result on login!  The bug reports will be rolling in soon...");
                throw new WikiException(WikiException.INTERNAL_ERROR_FANCY_SCHMANSY_LOGIN);
            }

            // Fail means, well, failure.
            if(status.equals("FAIL")) {
                Log.d(DEBUG_TAG, "Login failure, telling the user this...");
                throw new WikiException(WikiException.INTERNAL_ERROR_BAD_LOGIN);
            }

            // If this ISN'T just PASS at this point, that's very very bad.
            if(!status.equals("PASS")) {
                Log.e(DEBUG_TAG, "The wiki gave us a " + status + " result on login, and I have no clue what that means.");
                throw new WikiException(WikiException.INTERNAL_ERROR_GENERIC);
            }

            // Otherwise, we're good!
            Log.d(DEBUG_TAG, "Success!");

        } else {
            Log.d(DEBUG_TAG, "The wiki is still on 1.26 or lower, using the old login method...");

            // The old style.  Login is all we need.
            ArrayList<NameValuePair> nvps = new ArrayList<>();
            nvps.add(new BasicNameValuePair("action", "login"));
            nvps.add(new BasicNameValuePair("lgname", wpName));
            nvps.add(new BasicNameValuePair("lgpassword", wpPassword));
            nvps.add(new BasicNameValuePair("format", "xml"));

            httppost.setEntity(new UrlEncodedFormEntity(nvps, "utf-8"));

            Log.d(DEBUG_TAG, "Trying login...");
            WikiResponse response = getWikiResponse(httpclient, httppost);

            // The result comes in as an XML chunk.  Since we're expecting the
            // cookies to be set properly, all we care about is the "result"
            // attribute of the "login" element.
            Element login;
            String result;
            try {
                login = DOMUtil.getFirstElement(response.rootElem, "login");
                result = DOMUtil.getSimpleAttributeText(login, "result");
            } catch(Exception e) {
                throw new WikiException(WikiException.INTERNAL_ERROR_BAD_XML);
            }

            Log.d(DEBUG_TAG, "After login, result is " + result);

            // Now, get the result.  If it was a success, cookies got added.  If it
            // was NeedToken, this is a 1.16 wiki (as it should be now) and we need
            // another request to get the final token.
            if(result != null && result.equals("NeedToken")) {
                Log.d(DEBUG_TAG, "Token needed, trying again...");
                // Okay, do the same thing again, this time with the token we got
                // the first time around.  Cookies will be set this time around, I
                // think.
                String token = DOMUtil.getSimpleAttributeText(login, "token");

                httppost = new HttpPost(WIKI_API_URL);

                nvps = new ArrayList<>();
                nvps.add(new BasicNameValuePair("action", "login"));
                nvps.add(new BasicNameValuePair("lgname", wpName));
                nvps.add(new BasicNameValuePair("lgpassword", wpPassword));
                nvps.add(new BasicNameValuePair("lgtoken", token));
                nvps.add(new BasicNameValuePair("format", "xml"));

                httppost.setEntity(new UrlEncodedFormEntity(nvps, "utf-8"));

                Log.d(DEBUG_TAG, "Sending it out...");
                response = getWikiResponse(httpclient, httppost);

                Log.d(DEBUG_TAG, "Response has returned!");

                // Again!
                try {
                    login = DOMUtil.getFirstElement(response.rootElem, "login");
                    result = DOMUtil.getSimpleAttributeText(login, "result");
                } catch(Exception e) {
                    throw new WikiException(WikiException.INTERNAL_ERROR_BAD_XML);
                }
            }

            // Check it.  If NeedToken was returned again, then the wiki is just
            // telling us nonsense and we've got a right to throw an exception.
            if(result != null && result.equals("Success")) {
                Log.d(DEBUG_TAG, "Success!");
            } else {
                Log.d(DEBUG_TAG, "FAILURE!  Result was " + result);
                throw new WikiException(result != null ? result : WikiException.INTERNAL_ERROR_GENERIC);
            }
        }
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
            return " [https://openstreetmap.org/?lat="
                    + mLatLonLinkFormat.format(loc.getLatitude())
                    + "&lon="
                    + mLatLonLinkFormat.format(loc.getLongitude())
                    + "&zoom=16&layers=B000FTF @"
                    + mLatLonFormat.format(loc.getLatitude())
                    + ","
                    + mLatLonFormat.format(loc.getLongitude())
                    + "]";
        } else {
            return "";
        }
    }
}