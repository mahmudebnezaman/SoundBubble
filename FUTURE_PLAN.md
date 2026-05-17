# SoundBubble — Future Plan

## Current Version: 1.0.4

---

## Already Implemented

### Core
- [x] Floating bubble overlay (foreground service, visible over all apps and lock screen)
- [x] Tap to show system volume panel
- [x] Tap to silence incoming call ring (OEM-style — volume restored automatically when call ends)
- [x] Drag to reposition with left/right edge snapping
- [x] Position persisted across sessions and device reboots
- [x] Auto-start on device boot (if previously enabled)

### Volume Control
- [x] 5 independent stream sliders: Ring, Media, Alarm, Call, Notification
- [x] Ringer mode switcher: Normal / Vibrate / Silent
- [x] Live volume updates via ContentObserver (reflects system-side changes instantly)
- [x] Silent mode DND access — graceful prompt if permission not granted

### Bubble Customisation
- [x] Size: 40–100 dp
- [x] Opacity: 20–100%
- [x] Color: 10 presets + 5 cycling custom colors
- [x] Shape: Circle or vertical pill (Button)
- [x] Pill thickness: 30–70%

### UX & Polish
- [x] Inactivity animation — fades + slides half off-screen after 4s (Button shape)
- [x] Touch scale feedback on tap and drag
- [x] Responsive layout: 2-column on tablets, 1-column on phones
- [x] Battery optimization whitelist guide
- [x] Overlay permission setup guide
- [x] In-app update checker via Google Play

---

## Roadmap

### High Priority — Quick Wins

- [ ] **Quick Settings tile**
  Add a system tile to the notification shade so users can start/stop the bubble without opening the app. Uses `TileService`.

- [ ] **Swipe gesture on bubble**
  Swipe up/down directly on the bubble to raise/lower media volume. Removes the need to open the system volume panel for the most common use case.

- [ ] **Haptic feedback on tap**
  Brief vibration confirmation when the bubble is tapped. Uses `VibrationEffect` (no extra permission needed).

- [ ] **Full RGB color picker**
  Replace the 10-preset palette with a free HSV/hex color picker so users can choose any color.

- [ ] **Light theme**
  App is currently dark-only. Add a light theme and a system-default option (`AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM`).

---

### Medium Priority — Feature Depth

- [ ] **Volume presets / profiles**
  Save named volume configurations (e.g. "Work", "Sleep", "Gym") and switch between them with a single tap from the main screen or bubble long-press.

- [ ] **Long-press action on bubble**
  Configurable second action triggered by long press. Default suggestion: toggle silent/vibrate mode. Tap remains the existing action.

- [ ] **Bubble auto-hide in full-screen apps**
  Detect when a fullscreen window (game, video player) is active and automatically hide the bubble. Restore it when the user leaves. Requires monitoring `WindowManager` focus or using an `AccessibilityService`.

- [ ] **Per-stream volume ceiling (Volume Lock)**
  Let users set a maximum allowed volume per stream (e.g. cap Media at 80% to protect hearing). Enforce via ContentObserver — if volume exceeds the cap, reduce it automatically.

- [ ] **Gradient bubble color**
  Two-color gradient fill for the bubble circle/pill instead of flat color. Linear or radial gradient, configured in Bubble Settings.

- [ ] **More bubble shapes**
  Add horizontal pill and rounded square as additional shape options alongside Circle and Button.

---

### Advanced — Bigger Features

- [ ] **Schedule-based volume profiles**
  Define time-based rules (e.g. "Silent every weeknight at 11 PM", "Work profile on weekdays 9–6"). Uses `AlarmManager` with exact alarms. No extra permissions beyond what's already declared.

- [ ] **Media session controls from bubble**
  Long-press (or a configurable gesture) to show play/pause and skip controls for the active media session. Uses `MediaSessionManager` — requires `BIND_NOTIFICATION_LISTENER_SERVICE` or notification listener permission.

- [ ] **Audio output switcher**
  Quick-switch audio routing between speaker, wired headset, and Bluetooth devices directly from the bubble without entering system settings. Uses `AudioManager.getDevices()` (API 23+, no extra permission).

- [ ] **Backup & restore settings**
  Export all bubble settings to a JSON file and import on another device or after reinstall. Uses `SAF (Storage Access Framework)` — no extra permissions needed on modern Android.

- [ ] **Focus mode integration (Android 12+)**
  Hook into Digital Wellbeing focus modes. When "Do Not Disturb" or a Focus mode activates, automatically apply a matching SoundBubble profile. Uses system broadcast listening.

---

## Known Limitations / Technical Debt

- Adaptive brightness briefly spikes to full on unlock while `FLAG_SHOW_WHEN_LOCKED` is set — accepted trade-off to support bubble visibility during incoming calls on the lock screen.
- Color picker is limited to 15 options (10 presets + 5 cycling); no free RGB input yet.
- App is dark theme only — no light theme or system-default option.
- `ContentObserver` on `Settings.System.CONTENT_URI` is broad; could be scoped to specific volume keys for efficiency.

---

## Version History

| Version | Notes |
|---------|-------|
| 1.0.4 | Current release |
| 1.0.3 | Battery optimization handling, service reliability improvements |
| 1.0.2 | Overlay flag and device streaming configuration updates |
| 1.0.1 | Initial public release |
