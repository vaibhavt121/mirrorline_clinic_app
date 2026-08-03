# Mirror Clinic (demo)

A minimal, buildable Android app that demonstrates a **horizontal "mirror mode"**
applied to an entire app's UI, unrooted. Flip the switch in the top bar and every
icon, label, list, text field and control mirrors -- while every control still
responds where you *see* it.

## Why this approach

Flipping the *whole phone OS* on its *own screen*, unrooted, is not possible on
stock Android (MediaProjection re-captures your own flipped output in a feedback
loop, and no unrooted API exposes a horizontal-flip display transform). Flipping
*your own app's UI* is trivial and 100% reliable, because a single Compose
`graphicsLayer { scaleX = -1f }` flips the drawing **and** Compose inverts the
same transform for touch hit-testing -- so no coordinate remapping is needed.

See `MirrorApp.kt` -- the whole effect is one modifier.

## Get an installable APK -- two options

### Option A: GitHub Actions (no tools to install)
1. Create a new GitHub repo and push this folder to it
   (or upload the zip contents).
2. Open the **Actions** tab. The "Build APK" workflow runs automatically.
3. When it finishes, open the run and download the **mirror-clinic-debug-apk**
   artifact. Unzip it to get `app-debug.apk`.
4. Copy it to your phone and install (enable "Install unknown apps").

### Option B: Android Studio
1. Open this folder in Android Studio (Giraffe or newer).
2. Let it sync Gradle.
3. **Build > Build App Bundle(s) / APK(s) > Build APK(s)**, or just Run on a device.

## Notes
- Text appears reversed when mirrored -- that is inherent to a true horizontal
  flip. In a physical mirror / beam-splitter rig, the reflection reads correctly.
- To flip a live CameraX preview too, use `PreviewView` in **COMPATIBLE**
  (TextureView) mode so the flip applies; SurfaceView-backed previews render on a
  separate surface and will not flip with a view transform.
