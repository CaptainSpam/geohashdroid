/**
 * WikiUtils.java
 * Copyright (C)2009 Thomas Hirsch
 * Geohashdroid Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.HttpEntity;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.StringEntity;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Iterator;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import net.exclaimindustries.tools.http.MultipartEntity;
import net.exclaimindustries.tools.http.Part;
import net.exclaimindustries.tools.http.StringPart;
import net.exclaimindustries.tools.http.FilePart;
import net.exclaimindustries.tools.http.ByteArrayPartSource;

/** Various stateless utility methods to query a mediawiki server
 */
public class WikiUtils {
  private static Pattern re_form_field_names  = Pattern.compile("<input[^>]*?name=\"([^>]*?)\"[^>]*?value=\"([^>]*?)\"[^>]*?/>");
  private static Pattern re_form_field_values = Pattern.compile("<input[^>]*?value=\"([^>]*?)\"[^>]*?name=\"([^>]*?)\"[^>]*?/>");
  private static Pattern re_textarea    = Pattern.compile("<textarea.*?>(.*?)</textarea>",Pattern.DOTALL);
  private static Pattern re_login_fail  = Pattern.compile("<h2>Login error:</h2>(.*?)</div>",Pattern.DOTALL);
  private static Pattern re_login_good  = Pattern.compile("Login successful",Pattern.DOTALL);
  
/** Returns the content of a http request in a single string. 
     @param  httpclient an active HTTP session 
     @param  httpreq    an HTTP request (GET or POST)
     @return            the body of the http reply
  */
  public static String getHttpPage(HttpClient httpclient, HttpUriRequest httpreq) throws Exception {
    HttpResponse response = httpclient.execute(httpreq);
    HttpEntity entity = response.getEntity();
          
    if (entity!=null) {
      BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
      String page = "";
      while (true) {
        String line = in.readLine();
        if (line==null) break;
        page += line+"\n";
      }
      in.close();
      return page;
    } else {
      return null;
    }
  }

  /** Returns the raw content of a wiki page in a single string. 
     @param  httpclient an active HTTP session 
     @param  pagename   the name of the wiki page (must be URL formatted already)
     @param  formfields if not null, this hashmap will be filled with the correct HTML form fields to resubmit the page.
     @return            the raw code of the wiki page.
  */
  public static String getWikiPage(HttpClient httpclient, String pagename, HashMap<String, String> formfields) throws Exception {
    HttpGet httpget = new HttpGet("http://wiki.xkcd.com/wgh/index.php?title="+pagename+"&action=edit");
    String page = getHttpPage(httpclient, httpget);
    if ((page == null) || (page=="")) {
      return null;
    } else {
      if (formfields!=null) {
        Matcher inq   = re_form_field_names.matcher(page);
        Matcher ivq   = re_form_field_values.matcher(page);
        formfields.clear();
        while (inq.find()) {
          formfields.put(inq.group(1), inq.group(2));
        }
        while (ivq.find()) {
          formfields.put(ivq.group(2), ivq.group(1));
        }
        formfields.put("wpSummary", "+live expedition message (geohashdroid).");
        formfields.remove("wpPreview");
        formfields.remove("wpDiff");
        formfields.remove("wpMinoredit");
        formfields.remove("wpWatchthis");
        formfields.remove("search");
        formfields.remove("go");
        formfields.remove("fulltext");
      }
      Matcher areaq = re_textarea.matcher(page);
      if (areaq.find()) {
        String content = areaq.group(1);
        content = content.replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&amp;", "&").replaceAll("&quot;", "\""); //need utility method, urgently!
        return content;
      }
      return null;
    }
  }
  
  /** Replaces an entire wiki page
     @param  httpclient an active HTTP session 
     @param  pagename   the name of the wiki page (must be URL formatted already)
     @param  content    the new content of the wiki page to be submitted
     @param  formfields if not null, this hashmap will be filled with the correct HTML form fields to resubmit the page.
  */
  public static void putWikiPage(HttpClient httpclient, String pagename, String content, HashMap<String, String> formfields) throws Exception {
    HttpPost httppost = new HttpPost("http://wiki.xkcd.com/wgh/index.php?title="+pagename+"&action=submit");
    Part[] nvps = new Part[formfields.size()+1];
    Iterator<Entry<String,String>> i = formfields.entrySet().iterator();
    int n=0;
    while (i.hasNext()) {
      Entry<String, String> e = i.next();
      nvps[n++] = new StringPart(e.getKey(), e.getValue(), "utf-8");
    }
    nvps[n++] = new StringPart("wpTextbox1", content, "utf-8");
    httppost.setEntity(new MultipartEntity(nvps, httppost.getParams()));
        
    String page = getHttpPage(httpclient, httppost);
  }  
  
  /** Uploads an image to the wiki
     @param  httpclient  an active HTTP session, wiki login has to have happened before.
     @param  filename    the name of the new image file
     @param  description the description of the image. An initial description will be used as page content for the image's wiki page
     @param  data        a ByteArray containing the raw image data (assuming jpeg encoding, currently).
  */
  public static void putWikiImage(HttpClient httpclient, String filename, String description, byte[] data) throws Exception {
    HttpPost httppost = new HttpPost("http://wiki.xkcd.com/geohashing/Special:Upload");
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
    
    String page = getHttpPage(httpclient, httppost);
  }
  
  /** Retrieves valid login cookies for an HTTP session.
     @param  httpclient  an active HTTP session.
     @param  wpName      a wiki user name.
     @param  wpPassword  the matching password to this user name.
     @return             WikiUtils.LOGIN_GOOD if successful, an error message String otherwise.
  */
  public final static String LOGIN_GOOD = null;
  public static String login(HttpClient httpclient, String wpName, String wpPassword) throws Exception {
    HttpPost httppost = 
      new HttpPost("http://wiki.xkcd.com//wgh/index.php?title=Special:Userlogin&amp;action=submitlogin&amp;type=login");
                
    ArrayList <NameValuePair> nvps = new ArrayList <NameValuePair>();
    nvps.add(new BasicNameValuePair("wpName", wpName));
    nvps.add(new BasicNameValuePair("wpPassword", wpPassword));
    nvps.add(new BasicNameValuePair("wpRemember", "no"));
    nvps.add(new BasicNameValuePair("wpLoginattempt", "Log in"));
                
    httppost.setEntity(new UrlEncodedFormEntity(nvps, "utf-8"));

    String page = getHttpPage(httpclient, httppost);

    if (page!=null) {
      Matcher failq = re_login_fail.matcher(page);
      Matcher goodq = re_login_good.matcher(page);
      if (failq.find()) {
        String failmsg = failq.group(1).trim();
        return failmsg;
      } else if (goodq.find()) {
        return LOGIN_GOOD;
      } else {
        return "failed to parse login reply.";
      }
    } else {
      return "no reply to login request.";
    }
  }
}