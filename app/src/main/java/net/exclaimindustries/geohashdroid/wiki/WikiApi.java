/*
 * WikiApi.java
 * Copyright (C) 2026 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.wiki;

import retrofit2.Call;
import retrofit2.http.GET;

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
    }

    /**
     * GSON representation of the output from getWikiVersion().  Pass the
     * generator object (query.general.generator) into the constructor for
     * WikiVersionData.
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
}
