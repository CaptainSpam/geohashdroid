/**
 * WikiUtils.java
 * Copyright (C)2009 Thomas Hirsch
 * Geohashdroid Copyright (C)2009 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.wiki;

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

import javax.xml.parsers.DocumentBuilderFactory;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.util.UnitConverter;
import net.exclaimindustries.geohashdroid.util.Graticule;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.tools.DOMUtil;
import net.exclaimindustries.tools.DateTools;

import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicNameValuePair;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import android.content.Context;
import android.location.Location;
import android.text.format.DateFormat;
import android.util.Log;

/**
 * Various stateless utility methods to query a mediawiki server
 */
public class WikiUtils {
    /**
     * The base URL for all wiki activities.  Remember the trailing slash!
     */
    private static final String WIKI_BASE_URL = "http://wiki.xkcd.com/wgh/";

    /**
     * The URL for the MediaWiki API.  There's no trailing slash here.
     */
    private static final String WIKI_API_URL = WIKI_BASE_URL + "api.php";

    private static final String DEBUG_TAG = "WikiUtils";

    // The most recent request issued by WikiUtils.  This allows the abort()
    // method to work.
    private static HttpUriRequest mLastRequest;

    /**
     * This format is used for all latitude/longitude texts in the wiki.
     */
    public static final DecimalFormat mLatLonFormat = new DecimalFormat("###.0000", new DecimalFormatSymbols(Locale.US));

    /**
     * This format is used for all latitude/longitude <i>links</i> in the wiki.
     * This differs from mLatLonFormat in that it doesn't clip values to four
     * decimal points.
     */
    protected static final DecimalFormat mLatLonLinkFormat = new DecimalFormat("###.00000000", new DecimalFormatSymbols(Locale.US));

    /**
     * Aborts the current wiki request.  Well, technically, it's the most recent
     * wiki request.  If it's already done, nothing happens.  This will, of
     * course, cause exceptions in whatever's servicing the request.
     */
    public static void abort() {
        if(mLastRequest != null)
            mLastRequest.abort();
    }

    /**
     * Returns the wiki base URL.  That is, the base of where all requests will
     * be sent.
     *
     * @return the wiki base URL
     */
    public static String getWikiBaseUrl() {
        return WIKI_BASE_URL;
    }

    /**
     * Returns the URL for the MediaWiki API.  This is where any queries should
     * go, in standard HTTP query form.
     *
     * @return the MediaWiki API URL
     */
    public static String getWikiApiUrl() {
        return WIKI_API_URL;
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
    private static Document getHttpDocument(HttpClient httpclient,
                                            HttpUriRequest httpreq) throws Exception {
        // Remember the last request. We might want to abort it later.
        mLastRequest = httpreq;

        HttpResponse response = httpclient.execute(httpreq);

        HttpEntity entity = response.getEntity();

        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(entity.getContent());
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
    public static boolean doesWikiPageExist(HttpClient httpclient, String pagename) throws Exception {
        // It's GET time!  This is basically the same as the content request, but
        // we really don't need ANY data other than whether or not the page
        // exists, so we won't call for anything.
        HttpGet httpget = new HttpGet(WIKI_API_URL + "?action=query&format=xml&titles="
                + URLEncoder.encode(pagename, "UTF-8"));

        Document response = getHttpDocument(httpclient, httpget);

        // Now for some of the usual checking that should look familiar...
        Element root = response.getDocumentElement();

        // Error check!
        if(doesResponseHaveError(root)) {
            throw new WikiException(getErrorTextId(findErrorCode(root)));
        }

        Element pageElem;
        try {
            pageElem = DOMUtil.getFirstElement(root, "page");
        } catch(Exception e) {
            throw new WikiException(R.string.wiki_error_xml);
        }

        // "invalid" or "missing" both resolve to the same answer: No.  Anything
        // else means yes.
        return !(pageElem.hasAttribute("invalid") || pageElem.hasAttribute("missing"));
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
    public static String getWikiPage(HttpClient httpclient, String pagename, HashMap<String, String> formfields) throws Exception {
        // We can use a GET statement here.
        HttpGet httpget = new HttpGet(WIKI_API_URL + "?action=query&format=xml&prop="
                + URLEncoder.encode("info|revisions", "UTF-8")
                + "&rvprop=content&format=xml&intoken=edit&titles="
                + URLEncoder.encode(pagename, "UTF-8"));

        String page;
        Document response = getHttpDocument(httpclient, httpget);

        // Good, good.  First, figure out if the page even exists.
        Element root = response.getDocumentElement();

        // Error check!
        if(doesResponseHaveError(root)) {
            throw new WikiException(getErrorTextId(findErrorCode(root)));
        }

        Element pageElem;
        Element text;
        try {
            pageElem = DOMUtil.getFirstElement(root, "page");
        } catch(Exception e) {
            throw new WikiException(R.string.wiki_error_xml);
        }

        // If we got an "invalid" attribute, the page not only doesn't exist,
        // but it CAN'T exist, and is therefore an error.
        if(pageElem.hasAttribute("invalid"))
            throw new WikiException(R.string.wiki_error_invalid_page);

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
            throw new WikiException(R.string.wiki_error_xml);
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
    public static void putWikiPage(HttpClient httpclient, String pagename, String content, HashMap<String, String> formfields) throws Exception {
        // If there's no edit token in the hash map, we can't do anything.
        if(!formfields.containsKey("token")) {
            throw new WikiException(R.string.wiki_error_protected);
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

        Document response = getHttpDocument(httpclient, httppost);

        Element root = response.getDocumentElement();

        // First, check for errors.
        if(doesResponseHaveError(root)) {
            throw new WikiException(getErrorTextId(findErrorCode(root)));
        }

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
    public static void putWikiImage(HttpClient httpclient, String filename, String description, HashMap<String, String> formfields, byte[] data) throws Exception {
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
        Document response = getHttpDocument(httpclient, httppost);

        Element root = response.getDocumentElement();

        // Hopefully, a token exists.  If not, a problem exists.
        String token;
        Element page;
        try {
            page = DOMUtil.getFirstElement(root, "page");
            token = DOMUtil.getSimpleAttributeText(page, "edittoken");
        } catch(Exception e) {
            throw new WikiException(R.string.wiki_error_xml);
        }

        // TOKEN GET!  Now we've got us enough to get our upload on!
        Part[] nvps = new Part[]{
                new StringPart("action", "upload", "utf-8"),
                new StringPart("filename", filename, "utf-8"),
                new StringPart("comment", description, "utf-8"),
                new StringPart("watch", "true", "utf-8"),
                new StringPart("ignorewarnings", "true", "utf-8"),
                new StringPart("token", token, "utf-8"),
                new StringPart("format", "xml", "utf-8"),
                new FilePart("file", new ByteArrayPartSource(filename, data), "image/jpeg", "utf-8"),
        };
        httppost.setEntity(new MultipartEntity(nvps, httppost.getParams()));

        response = getHttpDocument(httpclient, httppost);

        root = response.getDocumentElement();

        // First, check for errors.
        if(doesResponseHaveError(root)) {
            throw new WikiException(getErrorTextId(findErrorCode(root)));
        }
    }

    /**
     * Retrieves valid login cookies for an HTTP session.  These will be added
     * to the HttpClient value passed in, so re-use it for future wiki
     * transactions.
     *
     * @param httpclient an active HTTP session.
     * @param wpName     a wiki user name.
     * @param wpPassword the matching password to this user name.
     * @throws WikiException problem with the wiki, translate the ID
     * @throws Exception     anything else happened, use getMessage
     */
    public static void login(HttpClient httpclient, String wpName, String wpPassword) throws Exception {
        HttpPost httppost = new HttpPost(WIKI_API_URL);

        ArrayList<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("action", "login"));
        nvps.add(new BasicNameValuePair("lgname", wpName));
        nvps.add(new BasicNameValuePair("lgpassword", wpPassword));
        nvps.add(new BasicNameValuePair("format", "xml"));

        httppost.setEntity(new UrlEncodedFormEntity(nvps, "utf-8"));

        Log.d(DEBUG_TAG, "Trying login...");
        Document response = getHttpDocument(httpclient, httppost);

        // The result comes in as an XML chunk.  Since we're expecting the
        // cookies to be set properly, all we care about is the "result"
        // attribute of the "login" element.
        Element root = response.getDocumentElement();
        Element login;
        String result;
        try {
            login = DOMUtil.getFirstElement(root, "login");
            result = DOMUtil.getSimpleAttributeText(login, "result");
        } catch(Exception e) {
            throw new WikiException(R.string.wiki_error_xml);
        }

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
            response = getHttpDocument(httpclient, httppost);

            Log.d(DEBUG_TAG, "Response has returned!");
            // Again!
            root = response.getDocumentElement();

            try {
                login = DOMUtil.getFirstElement(root, "login");
                result = DOMUtil.getSimpleAttributeText(login, "result");
            } catch(Exception e) {
                throw new WikiException(R.string.wiki_error_xml);
            }
        }

        // Check it.  If NeedToken was returned again, then the wiki is just
        // telling us nonsense and we've got a right to throw an exception.
        if(result != null && result.equals("Success")) {
            Log.d(DEBUG_TAG, "Success!");
        } else {
            Log.d(DEBUG_TAG, "FAILURE!");
            throw new WikiException(getErrorTextId(result));
        }
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
    private static int getErrorTextId(String code) {
        // If we don't recognize the error (or shouldn't get it at all), we use
        // this, because we don't have the slightest clue what's wrong.
        int error = R.string.wiki_error_unknown;

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

    private static boolean doesResponseHaveError(Element elem) {
        try {
            DOMUtil.getFirstElement(elem, "error");
        } catch(Exception ex) {
            return false;
        }

        return true;
    }

    private static String findErrorCode(Element elem) {
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
    public static String getWikiPageName(Info info) {
        String date = DateTools.getHyphenatedDateString(info.getCalendar());

        if(info.isGlobalHash()) {
            return date + "_global";
        } else {
            Graticule grat = info.getGraticule();
            String lat = grat.getLatitudeString(true);
            String lon = grat.getLongitudeString(true);

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
    public static String getWikiExpeditionTemplate(Info info, Context c) {
        String date = DateTools.getHyphenatedDateString(info.getCalendar());

        if(info.isGlobalHash()) {
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
            Graticule grat = info.getGraticule();
            String lat = grat.getLatitudeString(true);
            String lon = grat.getLongitudeString(true);

            return "{{subst:Expedition|lat=" + lat + "|lon=" + lon + "|date=" + date + "}}";
        }
    }

    /**
     * Retrieves the text for the categories to put on the wiki for pictures.
     *
     * @param info Info from which categories will be generated
     * @return said categories
     */
    public static String getWikiCategories(Info info) {
        String date = DateTools.getHyphenatedDateString(info.getCalendar());

        String toReturn = "[[Category:Meetup on "
                + date + "]]\n";

        if(info.isGlobalHash()) {
            return toReturn + "[[Category:Globalhash]]";
        } else {
            Graticule grat = info.getGraticule();
            String lat = grat.getLatitudeString(true);
            String lon = grat.getLongitudeString(true);

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
    public static String makeLocationTag(Location loc) {
        if(loc != null) {
            return " [http://www.openstreetmap.org/?lat="
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