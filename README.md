# MealSnap

A calorie tracker for Android (built for a Pixel 9 Pro) where logging a meal is just a photo.
Snap your plate before you eat. MealSnap works out what the dish is, how much of it there is and
how many calories it has, and logs all of it for you. **It runs entirely on your phone: no account,
no API key, no subscription and no internet needed.**

<p>
  <img src="docs/screenshots/today.png" width="24%" alt="Today screen with calorie ring and food log">
  <img src="docs/screenshots/meal-details.png" width="24%" alt="A recognised meal with alternatives, portion chips and breakdown">
  <img src="docs/screenshots/trends.png" width="24%" alt="Weekly trends and daily log">
  <img src="docs/screenshots/settings.png" width="24%" alt="Settings showing on-device food recognition">
</p>

## How it works

1. Tap **Add meal** and choose **Take a photo** (opens the Pixel camera), **Upload a photo** (any picture
   already on the phone) or **Enter manually**.
2. The photo is recognised **on the phone**:
   - Google's food classifier (about 2,000 dishes, runs offline) names the dish.
   - A built-in nutrition table turns the dish and a typical serving into calories, protein, carbs, fat,
     fibre, sugar and sodium.
   - The most likely alternatives appear as chips ("Not right? It might be Cheeseburger"), and
     **portion chips** (½×, ¾×, 1×, 1½×, 2×) rescale everything, because a photo can't tell how much food there is.
3. The meal is **logged automatically** to Today, and you can open it any time to see the full breakdown
   or correct it.
4. **Today** shows a calorie ring against your goal, macro rings and a food log grouped by meal.
   **Trends** shows the last 7 days and every earlier day.

If a dish isn't recognised, type its name: matching foods from the table appear as you type, and
picking one fills in the calories, so you never have to look numbers up.

The look is inspired by the Fitbit app: teal activity rings, white rounded cards on a soft grey
canvas, a pill navigation bar, with light and dark themes.

## What it can and can't do (please read)

- Calories are **estimates**: a typical serving of the recognised dish. Adjust the portion chips to match
  your plate. Mixed plates with several dishes are reduced to the single most likely dish unless your phone
  supports the optional Gemini Nano mode below.
- The food model knows about 2,000 dishes but not everything. For example it has no plain *dosa*, *naan* or
  *roti* label; for those, type the name and pick it from the list. The table has about 230 common foods,
  including many Indian, Asian, Middle Eastern and Western dishes. Anything outside it gets a generic plate
  marked "low confidence".
- Recognition quality depends on the photo: good light, the dish filling the frame, top-down or
  slightly angled.

## Optional extras (never required)

- **Gemini Nano (Pixel AI):** if your phone supports it, Settings shows a one-time download. It can read a
  whole plate, name each item and guess its weight. It is checked at run time and silently skipped if the
  phone doesn't support it.
- **Claude:** if you already have an Anthropic API key you can add it in Settings for a more detailed
  breakdown of mixed plates (about 1 to 2 US cents per photo, billed by Anthropic). Leave it empty to
  stay fully on-device.

## Install on your Pixel

1. Get the APK: download **MealSnap-apk** from the latest run on the repo's *Actions* tab
   (unzip it to get `app-release.apk`), or build it yourself (below).
2. Open the APK on the phone. Android will ask to allow installs from that app (Chrome, Files, …):
   allow it once, then tap **Install**. Play Protect may say the app is unrecognised because it
   isn't from the Play Store; choose **Install anyway**.

Every build is signed with the same key, so a newer APK installs over the old one and keeps your meals.

## Privacy

Photos and the meal log are stored only on the phone (app-private storage), and recognition happens on
the phone. A photo leaves the device only if you add a Claude API key, in which case it is sent to Anthropic
for analysis. Any key you add is stored in the app's private settings and excluded from Android backups.

## Build it yourself

Requires JDK 17+ and the Android SDK (platform 36, build-tools 36).

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:testDebugUnitTest      # unit tests + screenshot tests (app/build/screenshots)
./gradlew :app:assembleRelease        # app/build/outputs/apk/release/app-release.apk
```

## Under the hood

| Part | Choice |
|------|--------|
| UI | Kotlin, Jetpack Compose, Material 3 with a custom Fitbit-inspired theme |
| Camera / upload | System camera (`TakePicture`, no camera permission needed) and the Android photo picker |
| Recognition | Bundled AIY food classifier (TFLite, 192 px, quantised) run with LiteRT; optional Gemini Nano via the ML Kit GenAI Prompt API; optional Claude via the Anthropic Java SDK |
| Nutrition | Built-in table of about 230 foods per 100 g plus typical servings; every row is unit-tested for calorie/macro consistency |
| Storage | Room (meals, with a tested 1 to 2 migration), private files (photos), DataStore (settings) |
| Tests | Parsing, matching, request shape, day maths, DB schema; Robolectric screenshot tests render every screen |
| Min Android | 12 (API 31), arm64 devices, targets API 36 |

See [NOTICE.md](NOTICE.md) for third-party licences. `app/debug.keystore` is the standard public Android
debug key, committed on purpose so that every build (local or CI) is signed identically. This is a
sideloaded personal app; it is not set up for Play Store release.
