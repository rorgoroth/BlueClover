/*
 * BlueClover - 4chan browser https://github.com/nnuudev/BlueClover
 * Copyright (C) 2025 nnuudev
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
package org.floens.chan.ui.captcha;

import static org.floens.chan.utils.AndroidUtils.getAttrColor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;

import org.floens.chan.BuildConfig;
import org.floens.chan.R;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.core.site.SiteAuthentication;
import org.floens.chan.ui.theme.Theme;
import org.floens.chan.ui.theme.ThemeHelper;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.IOUtils;
import org.floens.chan.utils.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class InternalWebViewLayout extends WebView implements AuthenticationLayoutInterface {
    private static final String TAG = "ThisIsntEvenACaptchaLayout";

    private String link;

    public InternalWebViewLayout(Context context) {
        super(context);
    }

    public InternalWebViewLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public InternalWebViewLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    public void initialize(Loadable loadable, AuthenticationLayoutCallback callback) {
        loadable.site.requestModifier().modifyWebView(this);
        this.link = loadable.site.resolvable().desktopUrl(loadable, null);

        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        String userAgent = ChanSettings.customUserAgent.get();
        if (!userAgent.isEmpty()) {
            settings.setUserAgentString(userAgent);
        }

        setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(@NonNull ConsoleMessage consoleMessage) {
                Logger.i(TAG, consoleMessage.lineNumber() + ":" + consoleMessage.message()
                        + " " + consoleMessage.sourceId());
                return true;
            }
        });
        setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        });
    }

    @Override
    public void reset() {
        hardReset();
    }

    @Override
    public void hardReset() {
        loadUrl(link);
    }

    @Override
    public boolean requireResetAfterComplete() {
        return false;
    }

    @Override
    public void onDestroy() {
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        return new BaseInputConnection(this, false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // don't let the user swipe the screen o algo
            // there be dragons
            ViewParent disallowHere = this;
            while (disallowHere != null) {
                disallowHere.requestDisallowInterceptTouchEvent(true);
                disallowHere = disallowHere.getParent();
            }
        }
        return super.onTouchEvent(event);
    }
}
