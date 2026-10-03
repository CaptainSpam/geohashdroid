/*
 * WikiApi.java
 * Copyright (C) 2026 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.wiki;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

/**
 * The Retrofit API interface for the wiki.  I mean, it's not a full MediaWiki
 * API interface.  It's just the bits of it we need for Geohash Droid.
 */
public class WikiApi {
    /** The various queries used in Geohash Droid. */
    public interface WikiQuery {
        /** Queries the wiki version. */
        @GET("api.php?action=query&format=json&meta=siteinfo&siprop=general")
        Call<WikiVersionResponse> getWikiVersion();

        /**
         * Fetches a login token.  Remember, this doesn't take any login params;
         * the result from this will need to be used with a clientlogin to get
         * the proper session cookies.
         */
        @GET("api.php?action=query&format=json&meta=tokens&type=login")
        Call<LoginTokenResponse> getLoginToken();

        /**
         * Fetches the contents of a single wiki page, as well as a CSRF token
         * for editing it right afterward.  This is one of those "this is made
         * specifically for Geohash Droid" sort of things, what with always
         * fetching a token to edit it.
         *
         * @param pagename the name of the wiki page to fetch
         */
        @GET("api.php?action=query&format=json&prop=info|revisions&rvprop=content&rvslots=*&rvlimit=1&meta=tokens&type=csrf")
        Call<GetWikiPageResponse> getWikiPage(@Query("titles") String pagename);

        /**
         * Fetches very basic info about a single wiki page, enough to tell if
         * the page exists or not.  Useful if you don't want to fetch the entire
         * page (or image).
         *
         * @param pagename the name of the wiki page to fetch
         */
        @GET("api.php?action=query&format=json")
        Call<GetWikiPageExistenceResponse> getWikiPageExistence(@Query("titles") String pagename);

        /**
         * Fetches a CSRF token for uploading an image.
         */
        @GET("api.php?action=query&format=json&meta=tokens&type=csrf")
        Call<GetImageUploadTokenResponse> getImageUploadToken();

        /**
         * Carries through with a login, which will return a pass/fail and
         * update all requisite cookies.
         *
         * @param action the API action (always "clientlogin")
         * @param loginreturnurl the return URL (always WikiUtils.WIKI_API_URL)
         * @param format the format (always "json")
         * @param username the username
         * @param password the password
         * @param logintoken a logintoken from getLoginToken()
         */
        @FormUrlEncoded
        @POST("api.php")
        Call<ClientLoginResponse> postClientLogin(@Field("action") String action,
                                                  @Field("loginreturnurl") String loginreturnurl,
                                                  @Field("format") String format,
                                                  @Field("username") String username,
                                                  @Field("password") String password,
                                                  @Field("logintoken") String logintoken);

        /**
         * Posts a wiki page.
         *
         * @param action the API action (always "edit")
         * @param pagename the name of the wiki page
         * @param content the entire contents of the page
         * @param format the format (always "json")
         * @param csrfToken the CSRF token
         * @param summary the summary (always a note about GHD)
         * @param touched the last time the page was touched
         */
        @FormUrlEncoded
        @POST("api.php")
        Call<PostWikiPageResponse> postWikiPage(@Field("action") String action,
                                                @Field("title") String pagename,
                                                @Field("text") String content,
                                                @Field("format") String format,
                                                @Field("token") String csrfToken,
                                                @Field("summary") String summary,
                                                @Field("basetimestamp") String touched);

        /**
         * Posts an image.  It's multipart, so get ready to do some multipart
         * shenanigans.  All parts are plain strings, except fileData.
         *
         * @param action the API action (always "upload")
         * @param filename the filename as it'll appear in the wiki
         * @param comment a comment for the file
         * @param ignorewarnings whether warnings are ignored (always "true")
         * @param token the edit token
         * @param format the format (always "json")
         * @param file the actual file data
         */
        @Multipart
        @POST("api.php")
        Call<PostWikiImageResponse> postWikiImage(@Part("action") RequestBody action,
                                                  @Part("filename") RequestBody filename,
                                                  @Part("comment") RequestBody comment,
                                                  @Part("ignorewarnings") RequestBody ignorewarnings,
                                                  @Part("token") RequestBody token,
                                                  @Part("format") RequestBody format,
                                                  @Part() MultipartBody.Part file);
    }

    /**
     * Convenience method to make a postClientLogin object with the static
     * constants pre-defined.
     */
    public static Call<ClientLoginResponse> makePostClientLogin(
            @NonNull WikiQuery wikiQuery,
            @NonNull String username,
            @NonNull String password,
            @NonNull String logintoken) {
        return wikiQuery.postClientLogin(
                "clientlogin",
                WikiUtils.WIKI_API_URL,
                "json",
                username,
                password,
                logintoken);
    }

    /**
     * Convenience method to make a postWikiPage object with the static
     * constants pre-defined.
     */
    public static Call<PostWikiPageResponse> makePostWikiPage(
            @NonNull WikiQuery wikiQuery,
            @NonNull String pagename,
            @NonNull String content,
            @NonNull String csrfToken,
            @NonNull String summary,
            @Nullable String touched) {
        return wikiQuery.postWikiPage(
                "edit",
                pagename,
                content,
                "json",
                csrfToken,
                summary,
                touched
        );
    }

    @NonNull
    private static RequestBody makeStringPart(@NonNull String value) {
        return RequestBody.create(value, MediaType.get("text/plain"));
    }

    /**
     * Convenience method to perform the aforementioned multipart shenanigans
     * for postWikiImage with the static constants pre-defined.
     */
    public static Call<PostWikiImageResponse> makePostWikiImage(
            @NonNull WikiQuery wikiQuery,
            @NonNull String filename,
            @NonNull String comment,
            @NonNull String token,
            @NonNull byte[] fileData) {
        RequestBody actionPart = makeStringPart("upload");
        RequestBody filenamePart = makeStringPart(filename);
        RequestBody commentPart = makeStringPart(comment);
        RequestBody ignorewarningsPart = makeStringPart("true");
        RequestBody tokenPart = makeStringPart(token);
        RequestBody formatPart = makeStringPart("json");
        RequestBody fileDataBody = MultipartBody.create(
                fileData,
                MediaType.get("image/jpeg"));
        MultipartBody.Part fileDataPart = MultipartBody.Part.createFormData("file", filename, fileDataBody);

        return wikiQuery.postWikiImage(
                actionPart,
                filenamePart,
                commentPart,
                ignorewarningsPart,
                tokenPart,
                formatPart,
                fileDataPart
        );
    }

    /**
     * Base class of all GSON wiki responses.  This adds in the potential for an
     * error field.
     */
    public static class BaseWikiResponse {
        private ErrorObj error;

        public static class ErrorObj {
            private String code;
        }

        /** Returns true if there's an error, false otherwise. */
        public boolean hasError() {
            return error != null;
        }

        /** Assuming there's an error, return it. */
        public String getErrorCode() {
            return error != null ? error.code : null;
        }
    }

    /**
     * GSON representation of the response from getWikiVersion().  Use
     * getVersionData() to get a WikiVersionData object from it.
     */
    public static class WikiVersionResponse extends BaseWikiResponse {
        private QueryObj query;

        public static class QueryObj {
            private GeneralObj general;

            public static class GeneralObj {
                private String generator;
            }
        }

        /**
         * Gets the generator string from this version check (that is, the raw
         * version string).  You probably don't want this.  You almost certainly
         * want getVersionData().
         *
         * @return the raw version string
         */
        public String getGenerator() {
            return this.query.general.generator;
        }

        /**
         * Parses the generator string from this version check into a
         * WikiVersionData object.
         *
         * @return a WikiVersionData object
         */
        public WikiUtils.WikiVersionData getVersionData() {
            return new WikiUtils.WikiVersionData(this.getGenerator());
        }
    }

    /** GSON representation of the response from getLoginToken(). */
    public static class LoginTokenResponse extends BaseWikiResponse {
        private QueryObj query;

        public static class QueryObj {
            private TokensObj tokens;

            public static class TokensObj {
                private String logintoken;
            }
        }

        /**
         * Gets the login token fetched from this request.  There will also be
         * cookies, and said cookies are vital for this to work, but those
         * should be handled elsewhere.
         *
         * @return the retrieved login token
         */
        public String getLoginToken() {
            return this.query.tokens.logintoken;
        }
    }

    /**
     * GSON representation of the response from postClientLogin().  This should
     * only contain a status response; anything important will instead wind up
     * in the cookies.  Still, we need to check the response to make sure the
     * login succeeded.
     */
    public static class ClientLoginResponse extends BaseWikiResponse {
        private ClientLoginObj clientlogin;

        public static class ClientLoginObj {
            private String status;
        }

        /**
         * Gets the status of the login.  In general on the GHD wiki, this
         * should be either "PASS" or "FAIL".  If it's "UI" or "REDIRECT",
         * that's bad, and if it's anything else, that's worse.
         *
         * @return the status of the login
         */
        public String getStatus() {
            return clientlogin.status;
        }
    }

    /**
     * GSON representation of the response from getWikiPage().  This not only
     * includes the page content (if possible), but also flags for the page
     * existing and/or being valid.
     */
    public static class GetWikiPageResponse extends BaseWikiResponse {
        private QueryObj query;

        public static class QueryObj {
            private TokensObj tokens;
            private Map<String, PageObj> pages;

            public static class TokensObj {
                private String csrftoken;
            }

            public static class PageObj {
                private String missing;
                private String invalid;
                private String touched;
                private RevisionObj[] revisions;

                public static class RevisionObj {
                    private SlotsObj slots;

                    public static class SlotsObj {
                        private MainObj main;

                        public static class MainObj {
                            @SerializedName("*")
                            private String contents;
                        }
                    }
                }

                public boolean isMissing() {
                    return missing != null;
                }

                public boolean isInvalid() {
                    return invalid != null;
                }
            }
        }

        /**
         * Gets the associated CSRF token.  If there's valid login cookies, this
         * should be something that isn't just "+\".
         */
        public String getCsrfToken() {
            return this.query.tokens.csrftoken;
        }

        @NonNull
        private QueryObj.PageObj getFirstPageObj() {
            // We've got a map, and we know how to use it.  Specifically, we
            // have a map that should have exactly one item in it, as per the
            // query we made to get here.  If it has more, well, that's a
            // problem.
            ArrayList<String> ids = new ArrayList<>(query.pages.keySet());
            return Objects.requireNonNull(query.pages.get(ids.get(0)));
        }

        /**
         * Gets the content from the requested page, if it exists.  If the page
         * is missing or invalid, this will return an empty string, which is
         * likely what you want for a missing page (so it can be made anew) and
         * likely the most graceful way to make the app not crash if you didn't
         * check isInvalid first.  You should've checked it first.
         */
        @NonNull
        public String getPageContent() {
            QueryObj.PageObj page = getFirstPageObj();

            if(page.isMissing() || page.isInvalid()) {
                return "";
            }

            // I miss the nullish coalescer.
            if(page.revisions == null || page.revisions.length == 0) {
                return "";
            }

            QueryObj.PageObj.RevisionObj rev = page.revisions[0];
            if(rev.slots == null || rev.slots.main == null || rev.slots.main.contents == null) {
                return "";
            }

            return rev.slots.main.contents;
        }

        /**
         * Gets the flag that indicates whether or not this page exists on the
         * wiki.  If this is false, the page needs to be created anew.
         */
        public boolean isMissing() {
            return getFirstPageObj().missing != null;
        }

        /**
         * Gets the flag that indicates whether or not this page is valid.  If
         * this is false, a page with this name either can't exist, isn't
         * accessible by the given user, or something else is wrong that simply
         * trying to create the page won't fix.
         */
        public boolean isInvalid() {
            return getFirstPageObj().invalid != null;
        }

        /** Gets the touched value, which is mostly for timestamping. */
        @Nullable
        public String getTouched() {
            return getFirstPageObj().touched;
        }
    }

    /**
     * GSON representation of the response from getWikiPageExistence().
     * This is a lot like GetWikiPageResponse, just with a lot less data.
     */
    public static class GetWikiPageExistenceResponse extends BaseWikiResponse {
        private QueryObj query;

        public static class QueryObj {
            private Map<String, PageObj> pages;

            public static class PageObj {
                private String missing;
                private String invalid;
            }
        }

        @NonNull
        private QueryObj.PageObj getFirstPageObj() {
            // We've got a map, and we know how to use it.  Specifically, we
            // have a map that should have exactly one item in it, as per the
            // query we made to get here.  If it has more, well, that's a
            // problem.
            ArrayList<String> ids = new ArrayList<>(query.pages.keySet());
            return Objects.requireNonNull(query.pages.get(ids.get(0)));
        }

        /**
         * Gets the flag that indicates whether or not this page exists on the
         * wiki.  If this is false, the page needs to be created anew.
         */
        public boolean isMissing() {
            return getFirstPageObj().missing != null;
        }

        /**
         * Gets the flag that indicates whether or not this page is valid.  If
         * this is false, a page with this name either can't exist, isn't
         * accessible by the given user, or something else is wrong that simply
         * trying to create the page won't fix.
         */
        public boolean isInvalid() {
            return getFirstPageObj().invalid != null;
        }
    }

    /**
     * GSON representation of the response from putWikiPage().  This really just
     * has a result string, as we don't care past that.
     */
    public static class PostWikiPageResponse extends BaseWikiResponse {
        private EditObj edit;

        public static class EditObj {
            private String result;
        }

        /** Gets the result.  Hopefully it's "Success". */
        public String getResult() {
            return edit.result;
        }
    }

    /** GSON representation of an image upload CSRF token. */
    public static class GetImageUploadTokenResponse extends BaseWikiResponse {
        private QueryObj query;

        public static class QueryObj {
            private TokensObj tokens;

            public static class TokensObj {
                private String csrftoken;
            }
        }

        /** Gets the CSRF token.  Feed this to putWikiImage. */
        public String getCsrfToken() { return query.tokens.csrftoken; }
    }

    /**
     * GSON representation of the response from putWikiImage().
     */
    public static class PostWikiImageResponse extends BaseWikiResponse {
        private UploadObj upload;

        public static class UploadObj {
            private String result;
        }

        /**
         * Gets the result, which is hopefully "Success".  If not, then... um...
         * it's not a success.
         */
        public String getResult() { return upload.result; }
    }
}
