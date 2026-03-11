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
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.otacoo.chan.controller.Controller;
import org.otacoo.chan.core.di.NetModule;
import org.otacoo.chan.ui.helper.RefreshUIMessage;
import org.otacoo.chan.ui.view.AuthWebView;

import de.greenrobot.event.EventBus;

public class EmailVerificationController extends Controller {
    private AuthWebView webView;
    private String initialUrl;
    private String title = "Email Verification";
    private String[] requiredCookies;
    private boolean isFinished = false;

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
    @Override
    public void onCreate() {
        super.onCreate();

        navigation.title = title;
        navigation.swipeable = false;

        AuthWebView.runOnWebViewThread(this::setupWebView);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        if (!alive) return;

        webView = new AuthWebView(context);
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                
                String pageTitle = view.getTitle();
                boolean is8chan = initialUrl != null && (initialUrl.contains("8chan.moe") || initialUrl.contains("8chan.st") || initialUrl.contains("8chan.cc"));

                if (is8chan) {
                    checkCookies();
                } else if (url.contains("sys.4chan.org/signin") && url.contains("action=verify")) {
                    if (pageTitle != null && (pageTitle.contains("Verified") || pageTitle.contains("Success"))) {
                        completeVerification();
                    }
                }
            }
        });

        // Load the verification page
        webView.loadUrl(initialUrl);

        view = webView;

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

        CookieManager.getInstance().flush();
        // Sync WebView cookies into the java.net jar so OkHttp and isLoggedIn() see them.
        NetModule.syncCookiesToJar(initialUrl);

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
        if (webView != null) {
            webView.destroy();
        }
    }
}
