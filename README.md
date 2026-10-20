# Waypoints 📍🧭

Save places, find your way back to them, and share them. **English (default), Urdu and Arabic** — change the language in Settings (Urdu/Arabic switch the screen to right-to-left). Portrait only. **Works fully offline.**

## Waypoints
- **Save here** – one tap stores your current position (auto-named "WP 7"); rename it later.
- **+ Add** – full form: name, category (14 icons: general, home, camp, water, vehicle, viewpoint, danger, food, fuel, mosque, hospital, peak, hotel/rest, shop), favourite ★, latitude / longitude, elevation, notes. Text you type is **black on a white box**.
- Coordinates can be typed in almost any style: `33.6844`, `33.6844 N`, `33°41'3.8"N`, `N 33 41.064`, `-73.05` …
- **Use my position** / **Average for 10 s** (weights the GPS readings by their accuracy – better than a single reading) / **Project a point** from a bearing and a distance (from your position or from the coordinates you entered).
- **List**: search, sort (nearest, newest, name, category), filter by category or favourites; every row shows distance and direction from you, and a ➤ button to start navigating.
- **Detail**: coordinates in Decimal degrees, Degrees + minutes, DMS, **UTM** and plain "lat, lon" (tap a line to copy), distance / bearing / height difference from you, notes, saved date. Buttons: Navigate, Edit, Share (text with a Google Maps link), Open in Maps, Update to my position, Delete.

## Compass
- A compass dial that turns with the phone, a big **arrow that points to the waypoint**, the waypoint's icon on the ring, and the **distance** in large numbers.
- Also: bearing, which way to turn ("turn left / right / straight ahead / turn around"), your heading, ETA from your speed, and how much higher/lower the waypoint is. The arrow and ring turn gold when you face it.
- **Arrival alert**: the phone vibrates when you come within 10 / 25 / 50 / 100 m (or off).
- Option to show your other waypoints as dots around the dial.
- Works held flat or upright. If the phone has no compass sensor, the direction comes from GPS while you are moving.

## Settings
Language · units (m/km or ft/mi) · coordinate format (decimal, degrees+minutes, DMS, UTM) · true or magnetic north · arrival alert distance · keep screen on in the compass · **Export all as GPX or CSV**, **Import GPX**, delete all.
GPX opens in Google Earth, Garmin, Strava, Komoot and most GPS apps – export one now and then as a backup. CSV opens in Excel / Sheets (Urdu and Arabic show correctly).

## Good to know
- Waypoints are stored in a small file on the phone and written safely (a power cut cannot corrupt the list).
- GPS height can be off by several metres. Accuracy is shown next to your position; wait for ±10 m or better, or use "Average for 10 s", for important points.
- Needs the Location permission. Android 7.0 (API 24) or newer.

Brought to you By: Black Falcon 🦅 (shown in the lower right corner)

## Build
Push to GitHub -> Actions -> "Build APK" -> download `Waypoints-APK`.
Local: `gradle assembleDebug` (JDK 17, Gradle 8.7+).
