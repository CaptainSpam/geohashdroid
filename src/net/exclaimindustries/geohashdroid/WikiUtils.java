/**
 * WikiUtils.java
 * Copyright (C)2009 Thomas Hirsch
 * Geohashdroid Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid;

import net.exclaimindustries.tools.DOMUtil;
import net.exclaimindustries.tools.DateTools;

import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.HttpEntity;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.HashMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;

import javax.xml.parsers.DocumentBuilderFactory;

import android.content.Context;
import android.text.format.DateFormat;
import android.util.Log;

/** Various stateless utility methods to query a mediawiki server
 */
public class WikiUtils {
  /** The base URL for all wiki activities.  Remember the trailing slash! */
  private static String WIKI_BASE_URL = "http://wiki.xkcd.com/wgh/";

  private static final String DEBUG_TAG = "WikiUtils";
  
  public final static String LOGIN_GOOD = null;
  
  // The most recent request issued by WikiUtils.  This allows the abort()
  // method to work.
  private static HttpUriRequest mLastRequest;
  
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
  
/** Returns the content of a http request in a single string. 
     @param  httpclient an active HTTP session 
     @param  httpreq    an HTTP request (GET or POST)
     @return            the body of the http reply
  */
  private static String getHttpPage(HttpClient httpclient, HttpUriRequest httpreq) throws Exception {
    // Remember the last request.  We might want to abort it later.
    mLastRequest = httpreq;
    
    HttpResponse response = httpclient.execute(httpreq);
   
    HttpEntity entity = response.getEntity();
          
    if (entity!=null) {
      BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
      StringBuilder page = new StringBuilder();
      while (true) {
        String line = in.readLine();
        if (line==null) break;
        page.append(line + "\n");
      }
      in.close();
      return page.toString();
    } else {
      return null;
    }
  }
  
    /**
     * Returns the content of a http request as an XML Document.  This is to be
     * used only when we know the response to a request will be XML.  Otherwise,
     * this will probably throw an exception.
     * 
     * @param httpclient an active HTTP session
     * @param httpreq an HTTP request (GET or POST)
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
   * Returns the raw content of a wiki page in a single string.  Optionally,
   * also attaches the fields for future resubmission to a HashMap (namely, an
   * edittoken and a timestamp).
   *  
   * @param  httpclient an active HTTP session 
   * @param  pagename   the name of the wiki page
   * @param  formfields if not null, this hashmap will be filled with the correct HTML form fields to resubmit the page.
   * @return            the raw code of the wiki page, or null if the page doesn't exist
   * @throws WikiException problem with the wiki, translate the ID
   * @throws Exception     anything else happened, use getMessage
   */
  public static String getWikiPage(HttpClient httpclient, String pagename, HashMap<String, String> formfields) throws Exception {
    // We can use a GET statement here.
    HttpGet httpget = new HttpGet(WIKI_BASE_URL + "api.php?action=query&prop="
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
    } catch (Exception e) {
        throw new WikiException(R.string.wiki_error_xml);
    }
    
    // If we got an "invalid" attribute, the page not only doesn't exist, but it
    // CAN'T exist, and is therefore an error.
    if(pageElem.hasAttribute("invalid"))
        throw new WikiException(R.string.wiki_error_invalid_page);
    
    if(formfields != null) {
        // If we have a formfields hash ready, populate it with a couple values.
        formfields.put("summary", "a live expedition message sent via geohashdroid for android.");
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
    } catch (Exception e) {
        throw new WikiException(R.string.wiki_error_xml);
    }
    
    page = DOMUtil.getSimpleElementText(text);

    return page;
}
  
  /** Replaces an entire wiki page
     @param  httpclient an active HTTP session 
     @param  pagename   the name of the wiki page
     @param  content    the new content of the wiki page to be submitted
     @param  formfields a hashmap with the fields needed (besides pagename and content; those will be filled in this method)
     @throws WikiException problem with the wiki, translate the ID
     @throws Exception     anything else happened, use getMessage
  */
  public static void putWikiPage(HttpClient httpclient, String pagename, String content, HashMap<String, String> formfields) throws Exception {
    // If there's no edit token in the hash map, we can't do anything.
    if(!formfields.containsKey("token")) {
        throw new WikiException(R.string.wiki_error_protected);
    }

    HttpPost httppost = new HttpPost(WIKI_BASE_URL + "api.php");
    
    ArrayList <NameValuePair> nvps = new ArrayList <NameValuePair>();
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
  
  /** Uploads an image to the wiki
    @param  httpclient  an active HTTP session, wiki login has to have happened before.
    @param  filename    the name of the new image file
    @param  description the description of the image. An initial description will be used as page content for the image's wiki page
    @param  data        a ByteArray containing the raw image data (assuming jpeg encoding, currently).
   */
  public static void putWikiImage(HttpClient httpclient, String filename, String description, byte[] data) throws Exception {
    HttpPost httppost = new HttpPost(WIKI_BASE_URL + "index.php?title=Special:Upload");
    //httppost.addHeader("Host", "wiki.xkcd.com"); shouldn't be necessary.
    //httppost.addHeader("Referer", "http://wiki.xkcd.com/geohashing/Special:Upload");
    Part[] nvps = new Part[]{
      new FilePart("wpUploadFile", new ByteArrayPartSource(filename, data), "image/jpeg", "utf-8"),
      new StringPart("wpSourceType", "file", "utf-8"),
      new StringPart("wpDestFile", filename, "utf-8"),
      new StringPart("wpUploadDescription", description, "utf-8"),
      new StringPart("wpWatchthis", "true", "utf-8"),
      new StringPart("wpIgnoreWarning", "true", "utf-8"),
      new StringPart("wpUpload", "Upload file", "utf-8"),
      new StringPart("wpDestFileWarningAck", "", "utf-8")
    };
    httppost.setEntity(new MultipartEntity(nvps, httppost.getParams()));

    getHttpPage(httpclient, httppost);
  }


/*
 * NOTE: The following works on a 1.16 wiki.  Problem being, the Geohashing
 * Wiki is a 1.15 wiki, so we need to use the manual method used above. 
 */
//  /** Uploads an image to the wiki
//     @param  httpclient  an active HTTP session, wiki login has to have happened before.
//     @param  filename    the name of the new image file
//     @param  description the description of the image. An initial description will be used as page content for the image's wiki page
//     @param  formfields  a formfields hash as modified by getWikiPage containing an edittoken we can use (see the MediaWiki API for reasons why)
//     @param  data        a ByteArray containing the raw image data (assuming jpeg encoding, currently).
//  */
//  public static void putWikiImage(HttpClient httpclient, String filename, String description, HashMap<String, String> formfields, byte[] data) throws Exception {
//    if(!formfields.containsKey("token")) {
//      throw new WikiException(R.string.wiki_error_unknown);
//    }
//      
//    HttpPost httppost = new HttpPost(WIKI_BASE_URL + "api.php");
//    //httppost.addHeader("Host", "wiki.xkcd.com"); shouldn't be necessary.
//    //httppost.addHeader("Referer", "http://wiki.xkcd.com/geohashing/Special:Upload");
//    Part[] nvps = new Part[]{
//      new StringPart("action", "upload", "utf-8"),
//      new StringPart("filename", filename, "utf-8"),
//      new StringPart("comment", description, "utf-8"),
//      new StringPart("watch", "true", "utf-8"),
//      new StringPart("ignorewarning", "true", "utf-8"),
//      new StringPart("token", formfields.get("token"), "utf-8"),
//      new StringPart("format", "xml", "utf-8"),
//      new FilePart("data", new ByteArrayPartSource(filename, data), "image/jpeg", "utf-8"),
//    };
//    httppost.setEntity(new MultipartEntity(nvps, httppost.getParams()));
//    
//    Document response = getHttpDocumentDebug(httpclient, httppost);
//    
//    Element root = response.getDocumentElement();
//    
//    // First, check for errors.
//    if(doesResponseHaveError(root)) {
//        throw new WikiException(getErrorTextId(findErrorCode(root)));
//    }
//  }
  
  /**
   * Retrieves valid login cookies for an HTTP session.  These will be added to
   * the HttpClient value passed in, so re-use it for future wiki transactions.
   *  
   * @param  httpclient  an active HTTP session.
   * @param  wpName      a wiki user name.
   * @param  wpPassword  the matching password to this user name.
   * @throws WikiException problem with the wiki, translate the ID
   * @throws Exception     anything else happened, use getMessage
   */
  public static void login(HttpClient httpclient, String wpName, String wpPassword) throws Exception {
    HttpPost httppost =  new HttpPost(WIKI_BASE_URL + "api.php");

    ArrayList <NameValuePair> nvps = new ArrayList <NameValuePair>();
    nvps.add(new BasicNameValuePair("action", "login"));
    nvps.add(new BasicNameValuePair("lgname", wpName));
    nvps.add(new BasicNameValuePair("lgpassword", wpPassword));
    nvps.add(new BasicNameValuePair("format", "xml"));
                
    httppost.setEntity(new UrlEncodedFormEntity(nvps, "utf-8"));

    Document response = getHttpDocument(httpclient, httppost);

    // The result comes in as an XML chunk.  Since we're expecting the cookies
    // to be set properly, all we care about is the "result" attribute of the
    // "login" element.
    Element root = response.getDocumentElement();
    Element login;
    String result;
    try {
        login = DOMUtil.getFirstElement(root, "login");
        result = DOMUtil.getSimpleAttributeText(login, "result");
    } catch (Exception e) {
        throw new WikiException(R.string.wiki_error_xml);
    }
    
    // Now, get the result.  If it was a success, cookies got added.  If it was
    // a failure, throw it.
    if(result.equals("Success"))
        return;
    else {
        throw new WikiException(getErrorTextId(result));
    }
  }
  
  /**
   * Gets the text ID that corresponds to a given error code.  If the code isn't
   * recognized, this returns wiki_error_unknown instead.  Note that this WON'T
   * understand a non-error condition; check to make sure it isn't first.
   * 
   * @param code String returned from the wiki
   * @return text ID that corresponds to that error
   */
  private static int getErrorTextId(String code) {
      // If we don't recognize the error (or shouldn't get it at all), we use
      // this, because we don't have the slightest clue what's wrong.
      int error = R.string.wiki_error_unknown;
      
      // First, general errors.  These are the only general ones we care about;
      // there's more, but those aren't likely to come up.
      if(code.equals("unsupportednamespace"))
          error = R.string.wiki_error_illegal_namespace;
      else if(code.equals("protectednamespace-interface") || code.equals("protectednamespace")
              || code.equals("customcssjsprotected") || code.equals("cascadeprotected")
              || code.equals("protectedpage"))
          error = R.string.wiki_error_protected;
      else if(code.equals("confirmemail"))
          error = R.string.wiki_error_email_confirm;
      else if(code.equals("permissiondenied"))
          error = R.string.wiki_error_permission_denied;
      else if(code.equals("blocked") || code.equals("autoblocked"))
          error = R.string.wiki_error_blocked;
      else if(code.equals("ratelimited"))
          error = R.string.wiki_error_rate_limit;
      else if(code.equals("readonly"))
          error = R.string.wiki_error_read_only;
      
      // Then, login errors.  These come from the result attribute.
      else if(code.equals("Illegal") || code.equals("NoName") || code.equals("CreateBlocked"))
          error = R.string.wiki_error_bad_username;
      else if(code.equals("EmptyPass") || code.equals("WrongPass") || code.equals("WrongPluginPass"))
          error = R.string.wiki_error_bad_password;
      else if(code.equals("Throttled"))
          error = R.string.wiki_error_throttled;
      
      // Next, edit errors.  These come from the error element, code attribute.
      else if(code.equals("protectedtitle"))
          error = R.string.wiki_error_protected;
      else if(code.equals("cantcreate") || code.equals("cantcreate-anon"))
          error = R.string.wiki_error_no_create;
      else if(code.equals("spamdetected"))
          error = R.string.wiki_error_spam;
      else if(code.equals("filtered"))
          error = R.string.wiki_error_filtered;
      else if(code.equals("contenttoobig"))
          error = R.string.wiki_error_too_big;
      else if(code.equals("noedit") || code.equals("noedit-anon"))
          error = R.string.wiki_error_no_edit;
      else if(code.equals("editconflict"))
          error = R.string.wiki_error_conflict;
      
      // If all else fails, log what we got.
      else
          Log.d(DEBUG_TAG, "Unknown error code came back: " + code);
      
      return error;
  }
  
  private static boolean doesResponseHaveError(Element elem) {
      try {
          DOMUtil.getFirstElement(elem, "error");
      } catch (Exception ex) {
          return false;
      }
      
      return true;
  }
  
  private static String findErrorCode(Element elem) {
      try {
          Element error = DOMUtil.getFirstElement(elem, "error");
          return DOMUtil.getSimpleAttributeText(error, "code");
      } catch (Exception ex) {
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
   * Retrieves the text for the Expedition template appropriate for the given
   * Info.
   * 
   * TODO: The wiki doesn't appear to have an Expedition template for
   * globalhashing yet.
   * 
   * @param info Info from which an Expedition template will be generated
   * @param c Context so we can grab the globalhash template if we need it
   * @return said template
   */
  public static String getWikiExpeditionTemplate(Info info, Context c) {
      String date = DateTools.getHyphenatedDateString(info.getCalendar());
      
      if(info.isGlobalHash()) {
          // Until a proper template can be made in the wiki itself, we'll have
          // to settle for this...
          InputStream is = c.getResources().openRawResource(R.raw.globalhash_template);
          InputStreamReader isr = new InputStreamReader(is);
          BufferedReader br = new BufferedReader(isr);
          
          // Now, read in each line and do all substitutions on it.
          String input;
          StringBuffer toReturn = new StringBuffer();
          try {
              while((input = br.readLine()) != null) {
                  input = input.replaceAll("%%LATITUDE%%", UnitConverter.makeLatitudeCoordinateString(c, info.getLatitude(), true, UnitConverter.OUTPUT_DETAILED));
                  input = input.replaceAll("%%LONGITUDE%%", UnitConverter.makeLongitudeCoordinateString(c, info.getLongitude(), true, UnitConverter.OUTPUT_DETAILED));
                  input = input.replaceAll("%%LATITUDEURL%%", new Double(info.getLatitude()).toString());
                  input = input.replaceAll("%%LONGITUDEURL%%", new Double(info.getLongitude()).toString());
                  input = input.replaceAll("%%DATENUMERIC%%", date);
                  input = input.replaceAll("%%DATESHORT%%", DateFormat.format("E MMM d yyyy", info.getCalendar()).toString());
                  input = input.replaceAll("%%DATEGOOGLE%%", DateFormat.format("d+MMM+yyyy", info.getCalendar()).toString());
                  toReturn.append(input).append("\n");
              }
          } catch (IOException e) {
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
}