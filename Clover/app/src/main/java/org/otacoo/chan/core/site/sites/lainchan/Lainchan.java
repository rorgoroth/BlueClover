/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.otacoo.chan.core.site.sites.lainchan;

import androidx.annotation.Nullable;

import org.otacoo.chan.core.model.Post;
import org.otacoo.chan.core.model.orm.Board;
import org.otacoo.chan.core.model.orm.Loadable;
import org.otacoo.chan.core.site.Site;
import org.otacoo.chan.core.site.SiteIcon;
import org.otacoo.chan.core.site.common.CommonSite;
import org.otacoo.chan.core.site.common.vichan.VichanActions;
import org.otacoo.chan.core.site.common.vichan.VichanApi;
import org.otacoo.chan.core.site.common.vichan.VichanCommentParser;
import org.otacoo.chan.core.site.common.vichan.VichanEndpoints;
import org.otacoo.chan.core.site.FileUploadLimits;
import org.otacoo.chan.core.site.common.MultipartHttpCall;
import org.otacoo.chan.core.site.http.HttpCall;

import okhttp3.HttpUrl;
import okhttp3.Request;

public class Lainchan extends CommonSite {
    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        @Override
        public Class<? extends Site> getSiteClass() {
            return Lainchan.class;
        }

        @Override
        public HttpUrl getUrl() {
            return HttpUrl.parse("https://lainchan.org/");
        }

        @Override
        public String[] getNames() {
            return new String[]{"lainchan"};
        }

        @Override
        public String desktopUrl(Loadable loadable, @Nullable Post post) {
            if (loadable.isCatalogMode()) {
                return getUrl().newBuilder().addPathSegment(loadable.boardCode).toString();
            } else if (loadable.isThreadMode()) {
                return getUrl().newBuilder()
                        .addPathSegment(loadable.boardCode).addPathSegment("res")
                        .addPathSegment(String.valueOf(loadable.no) + ".html")
                        .toString();
            } else {
                return getUrl().toString();
            }
        }
    };

    @Override
    public void setup() {
        setName("Lainchan");
        setIcon(SiteIcon.fromAssets("icons/lainchan.webp"));

        setBoards(
                Board.fromSiteNameCode(this, "Programming", "λ"),
                Board.fromSiteNameCode(this, "Do It Yourself", "Δ"),
                Board.fromSiteNameCode(this, "Security", "sec"),
                Board.fromSiteNameCode(this, "Technology", "Ω"),
                Board.fromSiteNameCode(this, "Games and Interactive Media", "inter"),
                Board.fromSiteNameCode(this, "Literature", "lit"),
                Board.fromSiteNameCode(this, "Musical and Audible Media", "music"),
                Board.fromSiteNameCode(this, "Visual Media", "vis"),
                Board.fromSiteNameCode(this, "Humanity", "hum"),
                Board.fromSiteNameCode(this, "Drugs 3.0", "drug"),
                Board.fromSiteNameCode(this, "Consciousness and Dreams", "zzz"),
                Board.fromSiteNameCode(this, "layer", "layer"),
                Board.fromSiteNameCode(this, "Questions and Complaints", "q"),
                Board.fromSiteNameCode(this, "Random", "r"),
                Board.fromSiteNameCode(this, "Lain", "lain")
        );

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                return feature == Feature.POSTING;
            }
        });

        setEndpoints(new VichanEndpoints(this,
                "https://lainchan.org",
                "https://lainchan.org"));
        setRequestModifier(new CommonRequestModifier() {
            @Override
            public void modifyHttpCall(HttpCall httpCall, Request.Builder requestBuilder) {
                if (!(httpCall instanceof MultipartHttpCall)) {
                    return;
                }

                Request request = requestBuilder.build();
                HttpUrl url = request.url();
                String path = url.encodedPath();
                if (!path.endsWith("/post.php")) {
                    return;
                }

                requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36");
                requestBuilder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
                requestBuilder.header("Accept-Language", "en-US,en;q=0.9");
                requestBuilder.header("Cache-Control", "max-age=0");
                requestBuilder.header("Origin", "https://lainchan.org");
                requestBuilder.header("Upgrade-Insecure-Requests", "1");
                requestBuilder.header("Sec-Fetch-Dest", "document");
                requestBuilder.header("Sec-Fetch-Mode", "navigate");
                requestBuilder.header("Sec-Fetch-Site", "same-origin");
                requestBuilder.header("Sec-Fetch-User", "?1");
            }
        });
        setActions(new VichanActions(this));
        setApi(new VichanApi(this));
        setParser(new VichanCommentParser());
    }

    @Override
    public FileUploadLimits fileUploadLimits() {
        // Lainchan: maximum filesize is 75MB (3 * 25MB), max dimensions 20000x20000, 3 files
        long maxFileSize = 25 * 1024 * 1024; // 25 MB per file
        return new FileUploadLimits(maxFileSize, maxFileSize, 20000, 3);
    }
}
