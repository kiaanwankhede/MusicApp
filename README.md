# Tiny Tunes

An Android app to start a young child on the basics of Indian classical singing (sargam), in short lessons the way a teacher does it: **listen, then sing it back**. The app listens through the microphone and shows him the note he sings.

Version 3 is a complete restart.

**[See the screens →](docs/screens/README.md)**

## How it works
1. **Find Sa.** In Indian music anyone can choose Sa (the home note). On first start, he (or you) sings one long comfortable note, and the app makes that note Sa. Any octave counts, so a man, a woman and a child can all sing the same Sa.
2. **Lessons** (13, one step at a time): Sa · Sa Sa Sa · Sa Re · Re Sa · Sa Re Sa · Sa Re Ma · Ma Re Sa · Sa Re Ga · Ga Re Sa · Sa Re Ga Ma Pa · Pa Ma Ga Re Sa · Sa to upper Sa · and back down.
   - **Listen:** a real voice sings the notes (with a soft tanpura), and the note bubbles light up.
   - **Your turn:** he sings. A glowing ball follows his voice over coloured note lines. When he holds the right note, its bubble turns gold. If he is quiet for 8 seconds, the voice sings the note again.
   - **Done:** stars, and the app **plays back his own singing** ("That was him singing!").
3. **Free singing:** the ball follows his voice and shows the note name; holding Sa makes stars fall.

## The singing voice
- **Built-in:** a real man's voice, from a free recording on Freesound ("Male singing Indian Sargam" by rachitgupta, CC0 public domain). It covers Sa, Re, Ma and Dha, so lessons 1 to 7 are sung by a real voice from the start. Other notes play on harmonium.
- **Record your own** (Grown-ups → Record a voice, about 2 minutes): anyone in the family says or sings each note long (Saaa, Reee…). You don't need to sing well; the app tunes each recording to the exact note. Once recorded, every lesson uses that voice.

## Recognising notes
- Works for men's, women's and children's voices (65 to 1100 Hz).
- Children are rarely exactly on the note: "Easy" (the default) accepts about three-quarters of a semitone either side.
- Tested with real recordings: a child singing a scale (every note recognised), a man singing sargam, and a 14 dB quieter child. Room noise is never counted.

## Install
Every push to `main` builds an APK and attaches it to a new Release ("Tiny Tunes v3.N"): open **Releases**, download `TinyTunes.apk` on the tablet and install it (allow "install unknown apps"). It installs over the older Tiny Tunes. Allow the microphone when asked.

## Docs (earlier work)
- [Research plan: Sur ki Neev](docs/plan.md) and its [slide deck](docs/presentation/README.md) describe the earlier version 2 design, not this app.

Everything stays on the tablet. Not a medical product.
