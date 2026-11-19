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
