package org.otacoo.chan.core.di;

import android.content.Context;

import org.codejargon.feather.Provides;
import org.otacoo.chan.core.cache.FileCache;
import org.otacoo.chan.core.net.ChanInterceptor;
import org.otacoo.chan.core.settings.ChanSettings;
import org.otacoo.chan.utils.Logger;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;

public class NetModule {
    private static final long FILE_CACHE_DISK_SIZE = 50 * 1024 * 1024;
    private static final String FILE_CACHE_NAME = "filecache";
    private static final int TIMEOUT = 30000;
    private static final String TAG = "NetModule";

    @Provides
    @Singleton
    public OkHttpClient provideOkHttpClient(UserAgentProvider userAgentProvider) {
        CookieJar cookieJar = new CookieJar() {
            private final java.net.CookieManager cookieManager = new java.net.CookieManager();
            {
                cookieManager.setCookiePolicy(java.net.CookiePolicy.ACCEPT_ALL);
            }

            @Override
            public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                try {
                    for (Cookie cookie : cookies) {
                        java.net.HttpCookie httpCookie = new java.net.HttpCookie(cookie.name(), cookie.value());
                        httpCookie.setDomain(cookie.domain());
                        httpCookie.setPath(cookie.path());
                        httpCookie.setSecure(cookie.secure());
                        httpCookie.setHttpOnly(cookie.httpOnly());
                        if (cookie.expiresAt() > System.currentTimeMillis()) {
                            httpCookie.setMaxAge((cookie.expiresAt() - System.currentTimeMillis()) / 1000);
                        }
                        cookieManager.getCookieStore().add(url.uri(), httpCookie);
                    }
                } catch (Exception e) {
                }
            }

            @Override
            public List<Cookie> loadForRequest(HttpUrl url) {
                List<Cookie> result = new ArrayList<>();
                try {
                    List<java.net.HttpCookie> httpCookies = cookieManager.getCookieStore().get(url.uri());
                    for (java.net.HttpCookie httpCookie : httpCookies) {
                        Cookie.Builder builder = new Cookie.Builder()
                                .name(httpCookie.getName())
                                .value(httpCookie.getValue());
                        if (httpCookie.getDomain() != null) builder.domain(httpCookie.getDomain());
                        if (httpCookie.getPath() != null) builder.path(httpCookie.getPath());
                        if (httpCookie.getSecure()) builder.secure();
                        if (httpCookie.isHttpOnly()) builder.httpOnly();
                        if (httpCookie.getMaxAge() > 0) {
                            builder.expiresAt(System.currentTimeMillis() + httpCookie.getMaxAge() * 1000);
                        }
                        result.add(builder.build());
                        Logger.i(TAG, "Loaded cookie " + httpCookie.getName() + " for " + url.host());
                    }
                } catch (Exception e) {
                }
                return result;
            }
        };

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .cookieJar(cookieJar)
                .addInterceptor(new ChanInterceptor(userAgentProvider));

        if (ChanSettings.dnsOverHttps.get()) {
            try {
                // We need a temporary client to build the DnsOverHttps
                OkHttpClient dnsClient = new OkHttpClient.Builder().build();
                DnsOverHttps dns = new DnsOverHttps.Builder()
                        .client(dnsClient)
                        .url(HttpUrl.parse("https://cloudflare-dns.com/dns-query"))
                        .bootstrapDnsHosts(Arrays.asList(
                                InetAddress.getByName("162.159.36.1"),
                                InetAddress.getByName("162.159.46.1"),
                                InetAddress.getByName("1.1.1.1"),
                                InetAddress.getByName("1.0.0.1"),
                                InetAddress.getByName("162.159.132.53"),
                                InetAddress.getByName("2606:4700:4700::1111"),
                                InetAddress.getByName("2606:4700:4700::1001"),
                                InetAddress.getByName("2606:4700:4700::0064"),
                                InetAddress.getByName("2606:4700:4700::6400")
                        ))
                        .build();
                builder.dns(dns);
            } catch (UnknownHostException e) {
                Logger.e(TAG, "Error Dns over https", e);
            }
        }

        return builder.build();
    }

    @Provides
    @Singleton
    public FileCache provideFileCache(Context applicationContext, UserAgentProvider userAgentProvider, OkHttpClient okHttpClient) {
        return new FileCache(new File(getCacheDir(applicationContext), FILE_CACHE_NAME), FILE_CACHE_DISK_SIZE, userAgentProvider.getUserAgent(), okHttpClient);
    }

    private File getCacheDir(Context applicationContext) {
        // See also res/xml/filepaths.xml for the fileprovider.
        if (applicationContext.getExternalCacheDir() != null) {
            return applicationContext.getExternalCacheDir();
        } else {
            return applicationContext.getCacheDir();
        }
    }
}
