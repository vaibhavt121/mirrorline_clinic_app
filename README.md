# Screen Mirror (freeze-and-flip)

A phone app (no root) that captures the current screen of **any** app and shows
it **flipped horizontally**. It is a snapshot, not a live feed -- that is exactly
why it works without root (a live same-screen flip hits an unavoidable capture
feedback loop).

## How to use
1. Install and open the app.
2. Tap **1. Allow display over other apps** and grant it.
3. Tap **2. Start Mirror** and approve the screen-capture prompt.
4. A small **Mirror** button now floats in the top-right corner over every app.
5. Open any app, tap **Mirror** -> the current screen freezes and flips.
6. In the flipped view: **Mirror: ON/OFF** toggles the flip, **Close** returns
   to the floating button.
7. Stop it anytime from the notification (**Stop**).

## Build an APK
### GitHub Actions (no tools to install)
Push this project to a GitHub repo, open the **Actions** tab, wait for **Build
APK** to finish, then download the **screen-mirror-debug-apk** artifact and
unzip it to get `app-debug.apk`.

### Android Studio
Open the folder, let Gradle sync, then **Build > Build APK(s)**.

## Honest limits
- Each tap is a freeze-frame; it does not keep updating while flipped. Tap again
  for a fresh capture.
- Secure surfaces (some banking apps, DRM video) capture as black by Android
  design.
- The captured frame reflects whatever was on screen the instant you tapped.
