/*
 * BlueClover - 4chan browser https://github.com/nnuudev/BlueClover
 * Copyright (C) 2021 nnuudev
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
package org.otacoo.chan.ui.controller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.otacoo.chan.controller.Controller;
import org.otacoo.chan.core.di.NetModule;
import org.otacoo.chan.core.site.Site;
import org.otacoo.chan.core.site.sites.chan4.Chan4;
import org.otacoo.chan.ui.helper.RefreshUIMessage;
import org.otacoo.chan.ui.view.AuthWebView;
import org.otacoo.chan.utils.Logger;

import de.greenrobot.event.EventBus;

public class EmailVerificationController extends Controller {
    private static final String TAG = "EmailVerificationController";

    private AuthWebView webView;
    private String initialUrl;
    private String title = "Email Verification";
    private String[] requiredCookies;
    private boolean isFinished = false;
    private Site site;

    public EmailVerificationController(Context context) {
        this(context, "https://sys.4chan.org/signin");
    }

    public EmailVerificationController(Context context, String url) {
        super(context);
        this.initialUrl = url;
    }

    public EmailVerificationController(Context context, String url, String title) {
        super(context);
        this.initialUrl = url;
        this.title = title;
    }

    public void setRequiredCookies(String... cookies) {
        this.requiredCookies = cookies;
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void setSite(Site s) {
        this.site = s;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.title = title;
        navigation.swipeable = false;

        // Set view synchronously so pushController doesn't crash with "Controller has no view".
        // The WebView will be added into this container after async cookie clearing.
        FrameLayout container = new FrameLayout(context);
        container.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        view = container;

        AuthWebView.runOnWebViewThread(this::setupWebView);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        if (!alive) return;

        // Clear stale cookies before creating the WebView so the signin page gets a clean session.
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(success -> {
            Logger.d(TAG, "removeAllCookies result=" + success);
            cookieManager.flush();
            AuthWebView.runOnWebViewThread(this::createAndLoadWebView);
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createAndLoadWebView() {
        if (!alive) return;

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);

        // AuthWebView handles JavaScript, DOM storage, and basic cookie settings.
        webView = new AuthWebView(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        WebSettings settings = webView.getSettings();
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().flush();
                }

                String cookies = CookieManager.getInstance().getCookie(url);
                Logger.d(TAG, "onPageFinished url=" + url + " cookies=" + cookies);

                boolean is8chan = initialUrl != null && (initialUrl.contains("8chan.moe") || initialUrl.contains("8chan.st") || initialUrl.contains("8chan.cc"));

                if (is8chan) {
                    checkCookies();
                } else if (url != null && url.contains("sys.4chan.org/signin") && url.contains("action=verify")) {
                    String pageTitle = view.getTitle();
                    if (pageTitle != null && (pageTitle.contains("Verified") || pageTitle.contains("Success"))) {
                        completeVerification();
                    }
                }
            }
        });

        // Re-inject site cookies after clearing (e.g. pass cookies, Cloudflare cookies)
        if (site != null) {
            site.requestModifier().modifyWebView(webView);
        }

        // Add WebView into the container that was set as view in onCreate
        if (view instanceof FrameLayout) {
            ((FrameLayout) view).addView(webView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }
        webView.loadUrl(initialUrl);

        if (requiredCookies != null && requiredCookies.length > 0) {
            Toast.makeText(context, "Please solve the verification challenge to continue.", Toast.LENGTH_LONG).show();
        }
    }

    private void checkCookies() {
        if (isFinished || !alive) return;

        AuthWebView.runOnWebViewThread(() -> {
            String url = webView.getUrl();
            if (url == null) url = initialUrl;
            
            String cookies = CookieManager.getInstance().getCookie(url);
            boolean hasToken = cookies != null && cookies.contains("POW_TOKEN");
            // Match any TOS cookie (e.g. TOS20250413)
            boolean hasTOS = cookies != null && java.util.regex.Pattern.compile("\\bTOS\\w*=").matcher(cookies).find();

            if (hasToken && hasTOS) {
                completeVerification();
            } else {
                webView.postDelayed(this::checkCookies, 1000);
            }
        });
    }

    private void completeVerification() {
        if (isFinished) return;
        isFinished = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().flush();
        }
        
        // Only sync WebView cookies into the java.net/OkHttp jar for 8chan.
        boolean is8chan = initialUrl != null && (initialUrl.contains("8chan.moe") || initialUrl.contains("8chan.st") || initialUrl.contains("8chan.cc"));
        if (is8chan) {
            NetModule.syncCookiesToJar(initialUrl);
        }

        Toast.makeText(context, "Verification successful!", Toast.LENGTH_SHORT).show();
        EventBus.getDefault().post(new RefreshUIMessage("Verification successful"));

        if (navigationController != null) {
            navigationController.popController();
        } else if (presentedByController != null) {
            stopPresenting();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // If this was a 4chan verification session, persist any 4chan_pass cookie that was
        // set by the site so it survives future CookieManager clears.
        if (site instanceof Chan4) {
            Chan4 chan4 = (Chan4) site;
            String sysCookies = CookieManager.getInstance().getCookie("https://sys.4chan.org");
            if (sysCookies != null) {
                for (String part : sysCookies.split(";\\s*")) {
                    String trimmed = part.trim();
                    if (trimmed.startsWith("4chan_pass=")) {
                        String value = trimmed.substring("4chan_pass=".length());
                        if (!value.isEmpty()) {
                            chan4.getPassWebCookie().set(value);
                        }
                        break;
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().flush();
        }

        if (webView != null) {
            webView.destroy();
        }
    }
}
