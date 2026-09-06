# Site Camera (Android Studio project)

A camera app that stamps every photo with:
- **Date & time** — top-left, automatic, always on.
- **Complaint / remark** — type it fresh before each shot.
- **Site name** — set once (tap the gear icon), reused on every photo, shown with a bullet in a bordered box like your reference image.

Photos are saved to **Pictures/SiteCamera** in your phone's gallery.

## Easiest way to get the APK: let GitHub build it for you (no software install)

This project includes a GitHub Actions workflow (`.github/workflows/build.yml`) that compiles the APK in the cloud automatically. You don't need Android Studio, a computer with much storage, or any Android knowledge for this option.

1. Go to [github.com](https://github.com) and sign in (create a free account if you don't have one).
2. Click **+ → New repository**. Name it e.g. `SiteCamera`, keep it **Public** (Actions minutes are free for public repos), click **Create repository**.
3. On the new repo's page, click **"uploading an existing file"** (or **Add file → Upload files**).
4. Unzip `SiteCamera-AndroidStudio-Project.zip` on your computer, then drag **all the contents of the `SiteCamera` folder** (not the folder itself — its contents: `app`, `.github`, `build.gradle`, `settings.gradle`, etc.) into the GitHub upload box.
5. Scroll down, click **Commit changes**.
6. Click the **Actions** tab at the top of the repo. You'll see a workflow run start automatically ("Build APK") — it takes about 3–5 minutes.
7. Once it shows a green checkmark, click into that run, scroll to **Artifacts**, and download **SiteCamera-debug-apk** — it's a zip containing your `app-debug.apk`.
8. Transfer that `.apk` to your Android phone (via USB, email, WhatsApp, Google Drive, etc.), tap it, and allow "install from unknown sources" if prompted.

That's it — no Android Studio required.

## Alternative: build it yourself in Android Studio

1. Install **Android Studio** (free, from developer.android.com) if you don't have it.
2. Open Android Studio → **Open** → select this `SiteCamera` folder.
3. Let Gradle sync (it will download the Android SDK components/dependencies automatically — first sync needs internet).
4. Plug in your phone (with USB debugging on) or use an emulator, then press **Run ▶**.
   - OR go to **Build → Build Bundle(s)/APK(s) → Build APK(s)** to just generate an installable `.apk` file (found under `app/build/outputs/apk/debug/`).
5. Install the APK on your phone (`Settings → allow install from this source` if prompted).

## First run

- The app will ask you to **set the site name once** (e.g. "KALYANI FUEL STATION GADDIGE MANGALORE"). It's saved on the device and reused for every photo after that.
- You can change it any time by tapping the gear icon next to the shutter button.
- Type your complaint/remark in the text box before tapping the shutter — it gets burned onto that specific photo.

## Notes / things you may want to tweak

- **App icon**: currently uses a default system icon placeholder so the project builds without extra image assets. Add your own `mipmap` icons via Android Studio's Image Asset tool if you want a custom logo.
- **Text wrapping**: long complaint text is drawn as a single line — if you expect long remarks, let me know and I can add multi-line wrapping.
- **GPS/location**: this version does not add GPS coordinates (your reference photo doesn't show any either). I can add a location line if you want it.
- Minimum Android version supported: **Android 7.0 (API 24)**.

## Why I couldn't hand you a ready-made .apk directly

Building an Android APK requires the Android SDK/build tools and Google's Maven repositories, which aren't available in the sandboxed environment I write code in. This project is fully written and ready — Android Studio just needs to compile it once on your machine (or a developer's), which takes a few minutes.
