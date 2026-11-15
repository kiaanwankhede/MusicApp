# Tiny Tunes 🎵

A calm daily practice app that teaches a young autistic child the basics of Indian classical singing, one small level at a time (plan: [Riyaz ki Shuruaat](docs/presentation/README.md)).

## This version: Level 1 · Find Sa (test version)
- **Listen:** a soft tanpura plays and a harmonium gives Sa (his home note, D by default).
- **Your turn:** the tablet's microphone listens. A diya moves up and down with his voice and glows when he sings Sa; holding Sa for about a second counts as "found". Sa in a higher or lower octave also counts.
- No scores, no "wrong" sounds, and an **Again** and a **Stop** button on screen at all times.
- By default the tanpura fades out while he sings, so the microphone hears only him.

## Grown-ups corner (hold the lock for 2 seconds)
- His Sa (C to F, shown with harmonium key names), **Hear Sa** button.
- Volume limit, tanpura volume, "tanpura while he sings", calm mode.
- How close counts as Sa (Easy / Normal / Strict), tries per practice, microphone sensitivity.
- **Microphone check:** hum Sa to see that the tablet hears it, then stay quiet to see "Quiet".
- Practice log for the last days (stays on the tablet).

Everything stays on the device: no ads, no account, no internet needed after install.

## Android APK
Every push to `main` builds an APK automatically (GitHub Actions) and attaches it to a new Release: open the repo → **Releases** → download `TinyTunes.apk` on the tablet and install (allow "install unknown apps" once, and allow the microphone when the app asks).

## Run in a browser
`python3 -m http.server 8000` and open http://localhost:8000.

## Docs
- **[New plan: Sur ki Neev, based on fresh research (Nov 2026) →](docs/plan.md)**. This replaces the earlier plan. The app has not been changed to match it yet.
- [Earlier design presentation with mock screens](docs/presentation/README.md)
- `docs/design.html`: first market research and design notes.

Not a medical product or a replacement for therapy or a music teacher.
