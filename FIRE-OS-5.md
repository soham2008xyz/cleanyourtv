# Fire OS 5 fork (Fire TV Stick 2nd gen, AFTT)

The launcher on Fire OS 5 (Android 5.1, SDK 22), which upstream's `minSdk 26` can't install on, and where Amazon's
home can't be replaced or disabled. Tested on a Fire TV Stick 2nd gen (AFTT), Fire OS 5, build LVY48F.

## What it changes

- **minSdk 22.** It builds on the Fire OS 6 fork by Mohamedkrs (cherry-picked, `ef716924`): desugared `java.time`,
  static Outfit cuts with `LegacyFonts`, `FireHomeWatcher`, and the `getChangedPackages` / `Files` / hardware
  bitmap fallbacks. The rest of the API 23-24 calls go through `Compat`. `TintedTextView` keeps the top pills' icon
  tint, which Android 5 ignores in XML. `BootId` stands in for `BOOT_COUNT` with the kernel's boot id.
- **Fire OS 5 crash fix.** Fire OS hooks `getSharedPreferences` (`com.fireos.eve`) and calls back into the
  activity before `attachBaseContext` has run. `LauncherLocale` therefore reads the config through the
  application context.
- **Home.** Amazon's accessibility manager won't bind a third-party service, so `HomeKeyService` is out.
  `FireHomeWatcher` follows the system log (`READ_LOGS`, granted over ADB) for Amazon's home being started by
  the Home key. On 5.1 Android then holds back **every** start from a background app for 5 s:
  `AppTask.moveToFront` is "stopped", and an alarm's `PendingIntent` waits too. `HomeCover`, a black
  `SYSTEM_ALERT_WINDOW` overlay, hides Amazon's home and its ads until the launcher resumes. It also goes
  on Back or after 8 s, so it can't trap the screen.
- **Install:** `TV_HOST=<tv ip> launcher/install-fire-os-5.sh`. It disables nothing and changes no Home setting.
  Undo with `adb uninstall com.gathin.tvhome`.

Checked on the stick:
- Real remote, confirmed by the person with the launcher installed: Home from an app brings the launcher
  back, long-press Home is left to Fire OS, and wake from standby works.
- Injected Home from Settings gives the black cover, then the launcher about 5 s later.
- `READ_LOGS` survives a reboot.
- After a cold boot, the launcher takes over by itself about 44 s after `sys.boot_completed`: Fire OS 5 delivers
  `BOOT_COMPLETED` late, even at priority 999, and Amazon's home shows until then.
- About 78 MB PSS on the 1 GB stick.
- Top pills' icon tint follows focus.

Costs: the APK is about 944 KB, over upstream's 576 KB gate. Each Home press shows a black screen for about 5 s.
There is one background service, blocked on reading the log.

## Now Playing (`now_playing`)

Merged from the `now-playing` branch. The media session APIs are API 21, so only two calls changed:
`MediaSessionManager` is fetched by name, and the pill's tint is cleared through `Compat.setCompoundTint`.
It still needs notification access for `NowPlayingListener`. There's no `cmd` on 5.1, and `settings put secure`
does nothing here, so write the setting through the content provider. With `now_playing` true, let Home come
up once first, which switches the listener on. Then read the current value. The stick has an empty
`enabled_notification_listeners` row, so update it; anything already in it has to be kept, joined with `:`
(use `content insert` with the same binds if there's no row at all):

```bash
tools/tv.py "content query --uri content://settings/secure --where \"name='enabled_notification_listeners'\""
tools/tv.py "content update --uri content://settings/secure --bind value:s:com.gathin.tvhome/com.gathin.tvhome.NowPlayingListener --where \"name='enabled_notification_listeners'\""
```

**Then reboot.** Fire OS 5 doesn't pick up the change while running, not even after reinstalling the APK. After a
reboot, `dumpsys notification` lists the listener as enabled and live, the value survives, and it stays bound
across later `adb install -r`.

Checked on the AFTT with Spotify (`com.spotify.tv.android`), which publishes a full media session with
title, artist and artwork:
- Playing: the pill shows the artwork and "Title · Artist", and follows the next song while Home is open.
- Up from the dock focuses the pill, and the artwork keeps its colours on the white focused pill.
- OK opens Spotify at once, as the start comes from the foreground (no five-second hold).
- Paused in Spotify, then Home: the pill is gone. This needed a fix in `NowPlaying.update`: the first update
  after `start()` is always sent. Before, a pill left from before Home paused stayed, as nothing playing matched
  the reset `shown == null`. This affects the `now-playing` branch too.
- With the pill up, the date next to the clock is cut short ("WEDNESDAY 11 NOVE…").

Amazon's own Alexa media player and the TTS server also have sessions, idle; Prime Video and the others weren't tried.

# Findings

Branch `fire-os5-sdk22`. Tested on a Fire TV Stick 2nd gen (AFTT), Fire OS 5, build LVY48F, Android 5.1.1 / SDK 22, over ADB.

## 1. What needs SDK 23–26 (lint run at `minSdk 22`)

A throwaway `minSdk 22` edit, then `./gradlew lintRelease`, gave **129 errors, all `NewApi`** (full text:
`work/lint-minsdk22.txt`). The edit was reverted with `git checkout app/build.gradle`, so the tree is clean.
Libraries are not the blocker: RecyclerView 1.3.2 and DynamicAnimation 1.0.0 declare `minSdk 14`, and
the transitive androidx.core 1.13.1 declares `minSdk 19`. The code has only 7 `Build.VERSION` guards
(Q, TIRAMISU, P, S, 28), all for newer APIs, and no `@RequiresApi`.

### Trivial (mechanical swap, about 100 of the 129 errors: 79 java.time/java.util.function plus about 24 one-line swaps)
- **java.time and java.util.function** (`Instant`, `Duration`, `LocalDate[Time]`, `ZoneId`,
  `DateTimeFormatter`, `Predicate`, `Consumer`, `Supplier`, `Map.computeIfAbsent/getOrDefault/putIfAbsent`,
  `List.sort`, `Comparator.comparing`, `removeIf`): 79 of the 129. Fixed in one place by turning on
  core library desugaring (`coreLibraryDesugaringEnabled true` plus `com.android.tools:desugar_jdk_libs`).
  That is a new build dependency and adds DEX that breaks the size gate (section 3). The other way is
  to rewrite them by hand with `Calendar`, `System.currentTimeMillis`, plain loops and small local
  interfaces. That's mechanical, but there are 79 sites to change.
- `File#toPath` / `Files.readAllLines` (API 26): replace with a `BufferedReader`.
- `Context#getSystemService(Class)` (10×, API 23): use the string service names.
- `Context#checkSelfPermission` (API 23): Android 5.1 grants permissions at install, so treat them as granted.
- `getColorStateList` (API 23): `getResources().getColorStateList` or `ContextCompat`.
- `View#forceHasOverlappingRendering` (API 24): leave it out below 24.
- `Bitmap.Config.HARDWARE` (API 26, `WallpaperPlayer`): `ARGB_8888`. Uses more heap on a 1 GB stick.
- `UserManager#isUserUnlocked` (API 24, HomeKeyService, GuardKeep, GuardRevival): there's no
  file-based encryption on 5.1, so treat it as always unlocked. `directBootAware`,
  `localeConfig` and `<queries>` are ignored there without harm.

### Needs a fallback (real code, about 26 call sites)
- **Fonts.** `UiFont` calls `Resources#getFont` (API 26), and layouts take `android:fontFamily` from
  `@string/font_*`, which points at `@font/outfit*` XML families. Framework font XML is API 26. Below 26
  that needs `ResourcesCompat.getFont` (androidx.core is already on the classpath) or `.ttf` files in
  assets loaded with `Typeface.createFromAsset`, and every XML `fontFamily` set in code instead.
- **Tinted compound drawables.** `TextView#set/getCompoundDrawableTintList` (8×) and `android:drawableTint`
  in `styles.xml` (API 23): tint the drawables in code with `DrawableCompat`/`setColorFilter`.
- **Package change tracking.** `PackageManager#getChangedPackages` (API 26, `MainActivity`): full rescan on
  `PACKAGE_ADDED/REMOVED/CHANGED` broadcasts.
- **Network state.** `registerDefaultNetworkCallback` (API 24) and `getActiveNetwork` (API 23, `OpenOnStart`,
  the network pill): `registerNetworkCallback` (21) or `CONNECTIVITY_ACTION` plus `getActiveNetworkInfo`.
- **Locale.** `Configuration#getLocales` / `LocaleList` (API 24): `Configuration.locale`.
- **TV inputs.** `TvInputInfo#isHidden` / `loadCustomLabel` (API 24): skip them. The stick has no HDMI-in anyway.
- **`AccessibilityService#disableSelf`** (API 24, `GuardKeep` standby path): clear the service from
  `enabled_accessibility_services` (it holds `WRITE_SECURE_SETTINGS` via `pm grant`), or drop that feature.

### Built without error but silently ignored on 22 (118 lint warnings, `UnusedAttribute`/`InlinedApi`)
No focus attributes (`focusedByDefault`, `defaultFocusHighlightEnabled`, `android:foreground`) were flagged,
so D-pad focus doesn't lose anything. These do:
- **`Settings.Global.BOOT_COUNT`** (API 24), read by `HomeKeyService.java:399` (the redirect's boot probe)
  and `OpenOnStart.java:34`. On 22 it reads `-1`, so boot detection is lost **on the Home-redirect path
  itself**. That needs a fallback, `BOOT_COMPLETED` or `SystemClock.elapsedRealtime`.
- `Intent.ACTION_USER_UNLOCKED` (API 24, HomeKeyService, GuardRevival): never sent on 22. Use `BOOT_COMPLETED`.
- `AudioManager.ADJUST_TOGGLE_MUTE` (API 23, RemoteKeys mute): use `setStreamMute`.
- `NET_CAPABILITY_VALIDATED` (API 23), `Settings.ACTION_VPN_SETTINGS` (API 24): the network/VPN pill degrades.
- `PackageManager.MATCH_DISABLED_COMPONENTS` (API 24): same value as `GET_DISABLED_COMPONENTS`, so it works.
- `autoSize*` text (API 26, `layout-iw`), `directBootAware`, `localeConfig`, banner `width/height`: cosmetic or harmless.

### Impossible inside the app
Nothing in the APK is impossible. What can't be done is on the platform side (section 2).

### Prior art
`SHOWCASE.md` already lists an **API 22 fork**,
[Android 5.1 Hisense adapter](https://github.com/Drmufeng/cleanyourtv/tree/adapter/android-5.1), with
"legacy fallbacks", stock Home left enabled, and the physical remote and cold boot not verified. That
is the starting point rather than a new port. The repo's own note for that set
(`docs/devices/hisense-led55mu7000u.md`) covers only ads and the refused `pm disable-user`. It says
nothing about accessibility key filtering, `cmd` or a Home redirect on 5.1, so there's no repo evidence either way. Fire forks in `SHOWCASE.md` begin at Fire OS 6
(`minSdk 25`, a log-watching Home watcher with `READ_LOGS`). None covers Fire OS 5.

## Accessibility is blocked on Fire OS 5 (`HomeKeyService` can't work)
- `settings put secure …` **silently does nothing** on this firmware: exit 0, value unchanged.
  `settings list` doesn't exist either. Writes go through `content insert/update --uri content://settings/secure`.
- With `enabled_accessibility_services=probe.key/.ProbeService` and `accessibility_enabled=1` written that way,
  `dumpsys accessibility` still says `accessibilityEnabled=false, services:{}`. That held after a package
  reinstall and after a toggle. After a reboot, `accessibility_enabled` came back as **0** in a new row
  (`_id` 221→222): something at boot rewrites it.
- The system runs `com.amazon.android.server.accessibility.AmazonAccessibilityManagerService` (from
  `fosservices.jar`), and it doesn't bind a third-party service. So `HomeKeyService`, `InputKeyService`,
  `stock_home_redirect` and key filtering are all unavailable here.

## Home *can* be caught another way: the system log
- Home goes `PhoneWindowManager.handleShortPressOnHome` → `FireTVKeyPolicyManager.handleHomeKey`. That sends
  `amazon.intent.action.HOME_PRESSED` only to `com.amazon.vizzini` and `com.amazon.tv.devicecontrol`,
  plus `CLOSE_SYSTEM_DIALOGS`. Amazon gates `CLOSE_SYSTEM_DIALOGS` behind `DISABLE_KEYGUARD`, which this
  firmware raises to `signature|system|amazon` ("Permission Denial … requires DISABLE_KEYGUARD"), and
  `pm grant` refuses it.
- `READ_LOGS` is `signature|system|development`, so **`pm grant <pkg> android.permission.READ_LOGS` works**.
  Every Home press logs
  `I/ActivityManager: START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME] … cmp=com.amazon.tv.launcher/.ui.HomeActivity_vNext …}`.
- The probe's started service follows `logcat -v brief -T 1 ActivityManager:I *:S` and starts its own activity
  on that line. With an **injected** `KEYCODE_HOME` from Settings, it ended up resumed over Amazon's home
  (`mResumedActivity … probe.key/.Screen`). `AmazonProfileService.shouldAbortActivityStart()` logged
  "not aborted". This is the same method as the Fire OS 6 fork in SHOWCASE (Mohamedkrs, `READ_LOGS` + boot receiver).
- A press of the real remote's Home was confirmed by the person with the probe. Cold boot was checked with the launcher (see above).

## The Hisense API 22 adapter, in detail
Drmufeng `adapter/android-5.1`: 2 commits on upstream `22dc48fe` (launcher 2.6, which is old). The approach:
- Desugaring (`desugar_jdk_libs 2.0.3`), giving a 913,343 B APK.
- System fonts in `values/fonts.xml`, with Outfit moved to `values-v26`.
- `SDK_INT` guards with `@TargetApi` helpers for `getChangedPackages`, `Bitmap.Config.HARDWARE`,
  `isUserUnlocked`, `getLocales`, `TvInputInfo`, `forceHasOverlappingRendering` and `setCompoundDrawableTintList`.
- `registerNetworkCallback` in place of `registerDefaultNetworkCallback`.
- `checkCallingOrSelfPermission`.
- A `BufferedReader` in place of `Files.readAllLines`.
- `drawableTint` moved to `values-v23`.
- No hardware layer on `GlassView` below Q: on MSD6A828 the cached layer kept the old slab size.
- A `BOOT_COMPLETED` receiver that starts `MainActivity`.
- `stock_home_redirect` matching on the *package* with a 1 s debounce.

It relies on accessibility, so on Fire OS 5 only its API fallbacks carry over. Its own notes say the
physical remote and cold boot were never verified.

## BOOT_COUNT, in detail
`Settings.Global.BOOT_COUNT` (`"boot_count"`) is written by ActivityManagerService from Android 7.0. On 5.1 it's
missing, so `getInt(…, -1)` returns -1 every time. Three things read it:
- `HomeKeyService.probeAtBoot` (`HomeKeyService.java:399`). Through `firstBindAfterBoot` (`boot >= 0`) the
  boot probe **never runs**.
- `OpenOnStart.boot` (`OpenOnStart.java:34`), so `OpenOnStart.due` is always false.
- `MainActivity.java:3240` (boot_to_live_tv / open_on_start). So `open_on_start` and `boot_to_live_tv`
  **never fire**.

None of these crash; they just never fire. Fix: one `BootId` helper that returns `BOOT_COUNT` when it's ≥ 0,
otherwise a stable per-boot value. `/proc/sys/kernel/random/boot_id` is world-readable on 5.1 and is a new
UUID each boot; hash it to a non-negative int. On Fire OS 5 the accessibility probe is moot anyway, but the
log watcher needs the same "once per boot" logic.
