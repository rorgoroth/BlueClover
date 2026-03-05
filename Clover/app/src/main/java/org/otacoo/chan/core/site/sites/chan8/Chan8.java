/*
 * Clover - 4chan browser https://github.com/otacoo/Clover/
 * Copyright (c) 2026  otacoo
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.otacoo.chan.core.site.sites.chan8;

import androidx.annotation.Nullable;

import org.otacoo.chan.core.model.Post;
import org.otacoo.chan.core.model.orm.Board;
import org.otacoo.chan.core.model.orm.Loadable;
import org.otacoo.chan.core.site.Site;
import org.otacoo.chan.core.site.SiteIcon;
import org.otacoo.chan.core.site.common.CommonSite;
import org.otacoo.chan.core.site.common.lynxchan.LynxchanApi;
import org.otacoo.chan.core.site.common.lynxchan.LynxchanCommentParser;
import org.otacoo.chan.core.site.common.lynxchan.LynxchanEndpoints;
import org.otacoo.chan.core.site.http.HttpCall;

import okhttp3.HttpUrl;
import okhttp3.Request;

import org.otacoo.chan.core.net.Chan8RateLimit;

import java.util.Map;
import org.otacoo.chan.utils.Logger;

/**
 * Compatibility for 8chan.moe.
 * Note: Site has strong bot protections.
 */
public class Chan8 extends CommonSite {

    private static final String ROOT = "https://8chan.moe/";

    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        @Override
        public Class<? extends Site> getSiteClass() {
            return Chan8.class;
        }

        @Override
        public HttpUrl getUrl() {
            return HttpUrl.parse(ROOT);
        }

        @Override
        public String[] getNames() {
            return new String[]{"8chan.moe", "8chan", "Chan8"};
        }

        @Override
        public boolean respondsTo(HttpUrl url) {
            String host = url.host();
            return host.equals("8chan.moe") || host.equals("8chan.st") || host.equals("8chan.cc") || host.equals("dev.8ch.moe");
        }

        @Override
        public String desktopUrl(Loadable loadable, @Nullable Post post) {
            if (loadable.isCatalogMode()) {
                return getUrl().newBuilder().addPathSegment(loadable.boardCode).toString();
            } else if (loadable.isThreadMode()) {
                return getUrl().newBuilder()
                        .addPathSegment(loadable.boardCode).addPathSegment("res")
                        .addPathSegment(loadable.no + ".html")
                        .toString();
            } else {
                return getUrl().toString();
            }
        }
    };

    @Override
    public void setup() {
        setName("8chan");
        setIcon(SiteIcon.fromAssets("icons/8moe.webp"));

        // Boards are not fetched at startup. Users add boards by entering their code directly.
        setBoardsType(BoardsType.INFINITE);

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                return feature == Feature.POSTING || feature == Feature.POST_DELETE || feature == Feature.POST_REPORT || feature == Feature.LOGIN;
            }
        });

        setEndpoints(new LynxchanEndpoints(this, ROOT) {
            @Override
            public HttpUrl login() {
                // Use the currently active domain so the verification WebView opens
                // directly on the domain that hosts the POWBlock challenge (8chan.st).
                return HttpUrl.parse("https://" + Chan8RateLimit.getActiveDomain() + "/");
            }

            // Build URLs against the currently active domain so requests
            // automatically switch to 8chan.st if 8chan.moe is unreachable.
            private HttpUrl base() {
                return HttpUrl.parse("https://" + Chan8RateLimit.getActiveDomain() + "/");
            }

            @Override
            public HttpUrl catalog(Board board) {
                return base().newBuilder().addPathSegments(board.code + "/catalog.json").build();
            }

            @Override
            public HttpUrl thread(Board board, Loadable loadable) {
                return base().newBuilder().addPathSegments(board.code + "/res/" + loadable.no + ".json").build();
            }

            @Override
            public HttpUrl imageUrl(Post.Builder post, Map<String, String> arg) {
                String path = arg.get("path");
                if (path == null) return null;
                if (path.startsWith("http")) return HttpUrl.parse(path);
                if (path.startsWith("/")) path = path.substring(1);
                // 8chan.moe uses a flat /.media/ directory for both full images and thumbnails.
                // It does NOT use board-specific path segments (e.g. /test/src/...) like standard Lynxchan.
                return base().newBuilder().addPathSegments(path).build();
            }

            @Override
            public HttpUrl thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg) {
                String thumb = arg.get("thumb");
                if (thumb == null) return imageUrl(post, arg);
                if (thumb.startsWith("http")) return HttpUrl.parse(thumb);
                if (thumb.startsWith("/")) thumb = thumb.substring(1);
                // 8chan.moe thumbnails are in the same /.media/ directory.
                return base().newBuilder().addPathSegments(thumb).build();
            }
        });
        
        setActions(new Chan8Actions(this));
        setApi(new LynxchanApi(this));
        setParser(new LynxchanCommentParser());

        setRequestModifier(new CommonRequestModifier() {

            @Override
            public void modifyHttpCall(HttpCall httpCall, Request.Builder requestBuilder) {
                String url = requestBuilder.build().url().toString();
                // Cookies are handled automatically by OkHttp's CookieJar.
                // Only inject non-cookie headers here.
                requestBuilder.header("Referer", "https://" + Chan8RateLimit.getActiveDomain() + "/");

                if (url.contains("/.media/")) {
                    requestBuilder.removeHeader("Accept");
                    requestBuilder.header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
                }
                if (url.endsWith(".json") || url.contains(".json?")) {
                    requestBuilder.header("Accept", "application/json");
                }
            }

            @Override
            public void modifyRequestHeaders(java.util.Map<String, String> headers, String url) {
                // Cookies are handled automatically by OkHttp's CookieJar (via the
                // network interceptor in NetModule).  Only non-cookie headers go here.
                headers.put("Referer", "https://" + Chan8RateLimit.getActiveDomain() + "/");
                if (url.endsWith(".json") || url.contains(".json?")) {
                    headers.put("Accept", "application/json");
                }
            }
        });
    }
}
