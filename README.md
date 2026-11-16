# MealSnap

A calorie tracker for Android (built for a Pixel 9 Pro) where logging a meal starts with a photo.
Take a picture of your plate **before you eat**, and Claude estimates the calories and macros.
Your day's total is always one tap away.

## How it works

1. Tap **Snap meal** on the Today screen. The Pixel camera opens.
2. Take the photo. MealSnap shrinks it and sends it to Claude, which returns the foods it sees,
   portion sizes, calories, protein, carbs and fat.
3. Check the numbers, fix anything that looks off (or add a note like "oat-milk latte" and tap
   **Re-estimate**), then **Save meal**.
4. **Today** shows a calorie ring against your daily goal plus protein/carbs/fat totals.
   **History** shows the last 7 days and every earlier day.

No photo handy? The small gallery button next to **Snap meal** picks an existing picture, and
every number can be typed in by hand, so the app works without an API key too.

## Install on your Pixel

1. Get the APK: download **MealSnap-apk** from the latest run on the repo's *Actions* tab
   (unzip it to get `app-release.apk`), or build it yourself (below).
2. Open the APK on the phone. Android will ask to allow installs from that app (Chrome, Files, …):
   allow it once, then tap **Install**. Play Protect may say the app is unrecognised because it
   isn't from the Play Store; choose **Install anyway**.
3. Open **MealSnap → Settings** and paste an Anthropic API key
   (create one at <https://console.anthropic.com>). Set your daily calorie goal while you're there.

Every build is signed with the same key, so a newer APK installs over the old one and keeps your meals.

## Cost and privacy

- Each photo is one Claude API request (a downscaled ~1568 px JPEG plus a short prompt), roughly a cent or
  two on the default model. Settings lets you switch to Sonnet or Haiku to make it cheaper.
- Photos and the meal log are stored only on the phone (app-private storage). A photo leaves the
  phone only when it is sent to Anthropic for analysis. Your API key is stored in the app's private
  settings and excluded from Android backups.
- Calorie numbers from a photo are estimates. Treat them as a guide, not a measurement.

## Build it yourself

Requires JDK 17+ and the Android SDK (platform 36, build-tools 36).

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:assembleRelease        # app/build/outputs/apk/release/app-release.apk
```

## Under the hood

| Part | Choice |
|------|--------|
| UI | Kotlin, Jetpack Compose, Material 3 with Material You colours |
| Camera | System camera via `ActivityResultContracts.TakePicture` (no camera permission needed) |
| Storage | Room (meals), private files (photos), DataStore (settings) |
| AI | Official Anthropic Java SDK, structured JSON output, default model `claude-opus-5-5` at low effort, with server-side refusal fallback enabled |
| Min Android | 12 (API 31), targets API 36 |

`app/debug.keystore` is the standard public Android debug key, committed on purpose so that every
build (local or CI) is signed identically. This is a sideloaded personal app; it is not set up for Play Store release.
