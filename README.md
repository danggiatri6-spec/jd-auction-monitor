# 京东夺宝岛监控助手

Android app built with Kotlin and Jetpack Compose.

## MVP
- Add auction items by title and JD item URL.
- Set the auction end date and time.
- Schedule local reminders 30, 10, 5 and 1 minute before the end, plus at the end.
- View remaining time and remove watched items.
- No JD password is collected or stored.

## Scope and limitations
This first version does not automatically sign in to JD, scrape auction pages, monitor live prices, or place bids. JD may require authentication, CAPTCHA, and anti-automation checks. Verify item details and prices in the official JD app/site. This version provides manual tracking and local reminders.

## Build
Open this repository in Android Studio, sync Gradle, and run the app configuration. GitHub Actions builds a debug APK on pushes and pull requests; download it from the workflow run's Artifacts section.

Android 13+ requires notification permission. Exact alarms may require special access; otherwise Android may delay inexact reminders.


## JD sign-in and browsing (v1.1)
The app now includes an embedded Android WebView that opens the official JD Paipai/auction domain. Sign in on JD's own page, navigate to 夺宝岛, search there using the site's own search controls, open a specific item detail page, then tap "添加当前商品到监控". The app reads the visible page title and URL; review or enter the auction end time before saving.

JD may change its site, block embedded browsers, require CAPTCHA, or restrict login. If sign-in is rejected in WebView, open the same official page in the JD app or system browser; the app does not bypass JD security checks or store account credentials. End-time extraction is best-effort, so verify it manually before relying on reminders.
