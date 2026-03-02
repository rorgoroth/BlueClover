/*
 * Clover - 4chan browser https://github.com/otacoo/Clover/
 * Copyright (C) 2014  Floens https://github.com/Floens/Clover/
 * Copyright (C) 2026 otacoo
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
package org.otacoo.chan.core.site.common.vichan;

import static org.otacoo.chan.Chan.inject;

import org.otacoo.chan.utils.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Vichan applies garbage looking fields to the post form, to combat bots.
 * Load up the normal html, parse the form, and get these fields for our post.
 */
public class VichanAntispam {
    private static final String TAG = "VichanAntispam";

    private HttpUrl url;

    @Inject
    OkHttpClient okHttpClient;

    private List<String> fieldsToIgnore = new ArrayList<>();

    public VichanAntispam(HttpUrl url) {
        this.url = url;
        inject(this);
    }

    public void addDefaultIgnoreFields() {
        // Only ignore empty standard form fields that we explicitly submit in setupPost
        // Don't ignore fields with actual values - they might be antispam tokens
        fieldsToIgnore.addAll(Arrays.asList("board", "thread", "name", "email",
                "subject", "body", "file", "spoiler", "json_response",
                "file_url1", "file_url2", "file_url3", "password"));
    }

    public void ignoreField(String name) {
        fieldsToIgnore.add(name);
    }

    public Map<String, String> get(String comment) {
        Map<String, String> res = new java.util.LinkedHashMap<>();

        // Bootstrap request to establish PHP session
        // The server only sets PHPSESSID on requests to /post.php
        try {
            HttpUrl bootstrapUrl = url.newBuilder().encodedPath("/post.php").build();
            Request bootstrapRequest = new Request.Builder()
                    .url(bootstrapUrl)
                    .build();
            try (Response bootstrapResp = okHttpClient.newCall(bootstrapRequest).execute()) {
            }
        } catch (IOException e) {
            Logger.e(TAG, "Bootstrap request failed", e);
        }

        Request request = new Request.Builder()
                .url(url)
                .build();
        
        try (Response response = okHttpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (body != null) {
                String html = body.string();
                Document document = Jsoup.parse(html);
                Elements forms = document.body().getElementsByTag("form");
                for (int formIdx = 0; formIdx < forms.size(); formIdx++) {
                    Element form = forms.get(formIdx);
                    String formName = form.attr("name");
                    boolean hasTextarea = !form.getElementsByTag("textarea").isEmpty();
                    // Usually the post form has name="post" or no name but contains a textarea.
                    if (form.attr("name").equals("post") || !form.getElementsByTag("textarea").isEmpty()) {
                        Elements inputs = form.getElementsByTag("input");
                        Elements textareas = form.getElementsByTag("textarea");

                        for (Element input : inputs) {
                            String name = input.attr("name");
                            String value = input.val();
                            String type = input.attr("type").toLowerCase(Locale.ENGLISH);

                            // Skip fields with no name, file inputs
                            if (name.isEmpty() || type.equals("file")) {
                                continue;
                            }

                            // Unchecked checkboxes and radio buttons shouldn't be submitted
                            if ((type.equals("checkbox") || type.equals("radio")) && !input.hasAttr("checked")) {
                                continue;
                            }
                            
                            // Skip fields that are in the ignore list AND would be submitted in setupPost
                            // This prevents submitting the same field twice
                            if (fieldsToIgnore.contains(name)) {
                                continue;
                            }
                            
                            // Include any other field with a value - likely an antispam token
                            res.put(name, value);
                        }
                        
                        // Treat the renamed textarea as our comment field.
                        for (Element textarea : textareas) {
                            String name = textarea.attr("name");
                            if (!name.isEmpty() && !fieldsToIgnore.contains(name)) {
                                res.put(name, comment != null ? comment : "");
                            }
                        }

                        break;
                    }
                }
            }
        } catch (IOException e) {
            Logger.e(TAG, "IOException parsing vichan bot fields", e);
        } catch (Exception e) {
            Logger.e(TAG, "Error parsing vichan bot fields", e);
        }

        if (!res.isEmpty()) {
            Logger.i(TAG, "Found antispam fields: " + res.keySet());
        }

        return res;
    }
}
