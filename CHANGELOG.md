# Changelog

## 1.2.1

- Short videos: detection of the player inside YouTube (Shorts), Instagram (Reels) and Facebook (Reels), and of their web pages in Chrome, verified on the phone. The app checks again once a short-video app's screen settles, so switching to the Shorts/Reels tab is no longer missed.

## 1.2.0

- **Short videos**: a new card at the top of Apps & sites closes the short-video part of apps while the rest stays open — YouTube Shorts, Instagram Reels, Facebook Reels, Snapchat Spotlight and their web pages — and short-video apps entirely (TikTok, Likee, Kwai…).
  - Its own rule: always blocked, a daily limit (15 min by default) or allowed hours, per weekday; pauses and warnings as usual.
  - When closed: Back out of the player with a small "Short videos are closed" card; the block screen if Back doesn't get out.
  - Time in Reels/Shorts counts for both Short videos and the app itself.
  - Switching a platform off or loosening the rule needs the confirmation step.

## 1.1.0

- **Home-screen widgets** (Settings › Widgets, or long-press the home screen › Widgets):
  - **Today**: time used today, and each app/site with what's left or "blocked", most urgent first; pauses left; a warning if protection is off.
  - **Prayer times**: the next prayer with a countdown (or the Salah First lock in progress), today's five times with the next one highlighted, the Hijri and Gregorian dates.
  - Both follow the app's language (Arabic right-to-left, English left-to-right) and light/dark mode, and open the matching screen when tapped.
- Overview: the three summary tiles share one height.
- The app is named «الوقت سيف» in every language.
- Onboarding: the Device admin button opens Android's screen again; the restricted-settings step quotes Android's "App was denied access" message.

## 1.0.0 — first release

The Android version of the [Time Is a Sword](https://github.com/mahm0udnasr/time-is-a-sword) browser extension.

- **Apps and websites as one target**: an app and its website share one budget (e.g. Instagram app + instagram.com).
- **Rules per weekday**: always blocked, daily limit, allowed hours, or limit + hours. **Groups** share one budget.
- **Accurate time**: counted only while the target is in front with the screen on and unlocked; never twice.
- **Warnings** at 5 and 1 minutes left; **block screen** with a hadith, a saying of the salaf or a verse (with kashida).
- **Limited pauses**: 5 minutes, 3 times a day, behind the confirmation step.
- **Salah First**: every app except an allowlist and every website closes around each prayer; times from your location (calculated on the phone) or a typed city.
- **Anti-cheat**: looser changes need a 30-second countdown and typing a reminder (no paste); the app's Settings pages are guarded; device admin; automatic date & time required for limits; browsers the app can't read are closed while web rules are on.
- **Onboarding** wizard and a protection-status card; health check notification if protection stops.
- **Status notification** with today's time left; **backup** export/import; Arabic first, English, light/dark.
- Everything stays on the phone: no account, no analytics. The only network call is the prayer times for a typed city.
