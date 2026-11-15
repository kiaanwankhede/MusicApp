# Tiny Tunes 🎵

A calm music-learning web app (installable PWA) for a young autistic child. Lessons: **Stage 1** Sa Re Ga Ma Pa, **Stage 2** adds Dha Ni, **Stage 3** the full scale up to the upper Sa (changed in Grown-ups). Only the next key is enabled and glowing; tapping it plays the note and unlocks the following one, so a wrong tap is impossible.

- No ads, accounts, network calls or tracking. Settings and progress are stored only in the device's `localStorage`.
- A real piano-key layout: tapping a key **sings** its syllable (Sa, Re, Ga, Ma, Pa) with a natural neural singing voice (see `voices/README.md`), and the big syllable appears on screen.
- Parent area (hold the lock for 1.5s): **record your own voice** for each note (stored only on the device and used instead of the built-in voice), volume, guided/free play, calm mode, show/hide labels, counters.
- Works offline once opened (service worker). On a phone: open the site → *Add to Home Screen*.

## Run locally
`python3 -m http.server 8000` and open http://localhost:8000 (service worker needs http, not file://).

## Android APK
Every push to `main` builds an APK automatically (GitHub Actions) and attaches it to a new Release: open the repo → **Releases** → download `TinyTunes.apk` on your phone and install (allow "install unknown apps" once).

## Web hosting (optional)
Settings → Pages → deploy from branch `main`, folder `/ (root)`.

## Docs
**[Design presentation with mock screens →](docs/presentation/README.md)** (new level-by-level plan: Riyaz ki Shuruaat)

`docs/design.html` — market research, design principles and mock screens.

Not a medical product or a replacement for therapy; consult your child's care team.
