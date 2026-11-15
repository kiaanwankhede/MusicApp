# Tiny Tunes

A calm, short daily music time for a young autistic child, to start the basics of Indian classical singing. The grown-up holds and works the tablet; the child never has to touch it. Nothing is scored, ever.

This version (2.0) covers **weeks 0 to 2** of the plan [Sur ki Neev](docs/plan.md). Weeks 3 and later need a real singer's recordings and come in a later update; until then the app keeps running the week-2 routine.

## Music time (4 to 8 minutes, the same six steps every day)
1. **Hello:** the diya's wick lights (the "sound is coming" sign), then the harmonium plays the hello tune once at his Sa.
2. **Copycat** (screen down): you copy his sounds, softer; a very soft tanpura (drone) may play underneath.
3. **Lamp:** in week 1 the diya follows his voice (free sing). In week 2: hum along with Sa, two "listen and wait" turns, and kite glides that always end on one soft Sa.
4. **Song** (screen down): his own rhyme with pats; from week 2 also "Three Steps" (Sa Re Ga, Ga Re Sa).
5. **Laya** (rhythm): pats, finger-taps, a silent wave, hands on lap.
6. **Quiet ending:** soft tanpura, three small diyas go out one by one, the hello tune once more, slowly and very softly. The ending is always the quietest part.

A 2-minute **minimum day** (Hello, one copycat turn, quiet ending) counts as a full day. App-off days are logged too.

- No words, numbers, scores or rewards in the child's part of the screen. No beeps or cheers. Shouting never changes what he sees.
- Every sound fades in after the 1-second wick cue and fades out (never a hard cut). New sounds start at half level for 3 sessions.
- The grown-up can tap **Next**, **Again**, **All done** (quiet goodbye) or **Stop** (he is upset: everything fades within about a second) at any time.
- **Calm** (hold 1 second): all sound fades to his calm option (soft tanpura, humming with you, or slow pats) and the screen dims. You can mark the sound that upset him; it is then left out for 7 days and comes back softly. From Calm you can carry on, say goodbye (no goodbye tune), or use All done or Stop.

## Before day 1 (week 0, in the Grown-ups corner: hold the lock for 2 seconds)
- **Volume ceiling**, set with him in the room: "Play the loudest sound", then Softer/Louder until it is clearly softer than your talking voice at his seat. In the installed app every music time sets and holds the tablet's media volume at that step.
- **His Sa** (home note): listen to him sing a rhyme on 3 different days, then choose and lock his Sa (C to F, with harmonium key names). Everything uses the locked Sa.
- **Microphone check**, Bluetooth off, screen pinning, printed picture cards, a singer booked for weeks 2 to 3, and a hearing check with your paediatrician.
- **Hear Sa** on the home screen: before bath and meals, tap it and hum along.
- **Singer & recordings:** makes backing tracks at his Sa (16-bit WAV) and a clip list to send the singer.
- **Log:** a 3-tap note after each music time, kept on the tablet; export it now and then.

Everything stays on the device: no ads, no account, no internet needed after install. No synthetic voices are used.

## Android APK
Every push to `main` builds an APK (GitHub Actions) and attaches it to a new Release ("Tiny Tunes v2.N" for this version): open the repo, then **Releases**, download `TinyTunes.apk` on the tablet and install it (allow "install unknown apps" once, and allow the microphone when the app asks; it asks on the home screen, before music time). It installs over the earlier Tiny Tunes; if Android refuses, uninstall the old app first.

## Run in a browser
`python3 -m http.server 8000` and open http://localhost:8000. In a browser the tablet volume cannot be set by the app: set the media volume by hand to the step you marked.

## Docs
- [The plan: Sur ki Neev](docs/plan.md) (what to do each week, and why)
- [Slide deck: Sur ki Neev](docs/presentation/README.md) (the plan in 14 slides, with real screens of this version; [PDF](docs/presentation/Sur-ki-Neev.pdf))
- `docs/design.html` (first research notes; superseded by the plan)

Not a medical product, and not a replacement for therapy, an audiologist or a music teacher.
