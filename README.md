# Blue Clover - imageboard browser for Android

---

## THIS APP IS DEPRECATED. USE ANYTHING ELSE.

This app was officially abandoned in August 2022 after the introduction of a *second* captcha on 4chan. As you probably already know, it still got a couple more minor updates well into 2024. Alas, in February 2025, and to the surprise of absolutely nobody, 4chan added a *third* captcha on top of the two existing ones, breaking this app again.

**There are no plans to support this new captcha.**

Instead, two different zero-effort workarounds have been added to the last apk:

- There's a new default captcha option ("Internal WebView"). This will simply open the current thread on an internal WebView, where you'll be able to solve whatever number of random captchas 4chan has at the time. This WebView uses the internal cookie storage, so successfully making a post *and waiting 15 more minutes* (?) is, at the time of writing this, enough to make the previous captcha option work again (tap on 4chan on the list of boards to change back to the slider captcha).

  Please note, however, that this WebView does not interact at all with the rest of the app interface: you can use the comment field for convenience but you'll have to copy and paste your comment manually, you'll have to tap on Back to return after you're done posting, and you'll have to mark your posts as yours manually if you want to receive notifications.

  Some specific versions of the Android WebView, such as those used by default in certain emulators, will not work at all and posts will simply disappear without even showing an error. If you experience this problem, try updating your system WebView or changing the User-Agent to that of your version of Chrome.

  Please note: this WebView doesn't support attaching files, so **you won't be able to post images with it**, only text posts are supported. You won't be able to create new threads either, so you can't use this method to get a working cookie to create threads with Blue Clover. It's dead, Jim.

- Additionally, the former captcha window ("Slider captcha") now has an *invisible* debug button somewhere near the top left corner. Clicking on this *invisible* button will open a prompt allowing the user to interact with the page in case that's your thing. For example, the *4chan_pass* cookie could be set by entering something like this:

  `javascript:document.cookie="4chan_pass=COOKIE_COPIED_FROM_A_BROWSER"`

  If you didn't have a working cookie on the app before the latest changes on 4chan, getting a working cookie from a browser and importing it like this is the only way to create threads with Blue Clover anymore.

Have fun!

---

### Download APK: [ [latest release](https://github.com/nnuudev/BlueClover/releases/latest) | [all releases](https://github.com/nnuudev/BlueClover/releases) ]

Blue Clover is a fast Android app for browsing 4chan on Android. It adds inline replying, thread watching, notifications, themes, filters and a whole lot more. Blue Clover is licensed under the GPL and will always be free.

The app is based on [Clover-dev 3.0.2 0e32fb7](https://github.com/chandevel/Clover/commit/0e32fb74d5ea4fbfe3248e559e64037bdf9acf17) and some of its more relevant [changes](https://raw.githubusercontent.com/nnuudev/BlueClover/dev/CHANGES.txt) are:

- ~~*New captcha support!*~~ Internal WebView
- Page counter on thread view
- Board flags support
- Quick external image attaching
- Image renaming and reencoding
- Immersive mode for image gallery
- Shortcuts to external archives

Some parts of the code were backported from [Kuroba](https://github.com/Adamantcheese/Kuroba) or merged from old [pull requests](https://github.com/chandevel/Clover/pulls?q=is%3Apr), check the [commit log](https://github.com/nnuudev/BlueClover/commits/dev) for proper attribution when applicable.

## License
Blue Clover is [GPLv3](https://github.com/nnuudev/BlueClover/blob/dev/COPYING.txt), [licenses of the used libraries](https://github.com/nnuudev/BlueClover/blob/dev/Clover/app/src/main/assets/html/licenses.html).