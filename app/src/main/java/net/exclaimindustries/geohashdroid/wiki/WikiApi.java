/*
 * WikiApi.java
 * Copyright (C) 2026 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.wiki;

import androidx.annotation.NonNull;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.POST;

/**
 * The Retrofit API interface for the wiki.  I mean, it's not a full MediaWiki
 * API interface.  It's just the bits of it we need for Geohash Droid.
 */
public class WikiApi {
    /** The various queries used in Geohash Droid. */
    public interface Query {
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
    }

    /**
     * Convenience method to make a postClientLogin object with the static
     * constants pre-defined.
     */
    public static Call<ClientLoginResponse> makePostClientLogin(
            @NonNull WikiApi.Query query,
            String username,
            String password,
            String logintoken) {
        return query.postClientLogin(
                "clientlogin",
                WikiUtils.WIKI_API_URL,
                "json",
                username,
                password,
                logintoken);
    }

    /**
     * GSON representation of the response from getWikiVersion().  Use
     * getVersionData() to get a WikiVersionData object from it.
     */
    public static class WikiVersionResponse {
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
    public static class LoginTokenResponse {
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
    public static class ClientLoginResponse {
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
}
