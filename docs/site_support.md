TODO: Extend

### **How Sites Are Implemented TL;DR:**
1. Extend `CommonSite`
2. Implement `setup()` method
3. Set name, icon, boards in `SiteRegistry`
4. Create `CommonSiteUrlHandler` for URL matching
5. Create Endpoints class (extends `CommonEndpoints`)
6. Create API class (extends `CommonApi`)
7. Create CommentParser (if needed, otherwise make use of existing imgboard engine ones)
8. Define features (POSTING, LOGIN, etc.)

## **Site Registry**

### [SiteRegistry.java](Clover/app/src/main/java/org/otacoo/chan/core/site/SiteRegistry.java)

```
URL_HANDLERS:
  - Chan4.URL_HANDLER (4chan.org)
  - Sushichan.URL_HANDLER
  - Lainchan.URL_HANDLER (lainchan.org)
  - Chan8.URL_HANDLER (8chan.moe)

SITE_CLASSES (by ID):
  - 0 → Chan4
  - 1 → Sushichan
  - 10 → Lainchan
  - 20 → Chan8
```

---

## **Key Implementation Details**

### ** Typical Post Flow:**
1. User enters text, selects file, solves captcha
2. `Reply` object created with comment, file attachments, captcha challenge/response
3. `SiteActions.post(Reply, PostListener)` called
4. `setupPost()` adds form parameters to `MultipartHttpCall`
5. `HttpCallManager.makeHttpCall()` executes via OkHttp3
6. `handlePost()` parses response
7. If auth needed: `ReplyResponse.requireAuthentication = true`
8.  UI shows captcha layout matching `SiteAuthentication.Type`

