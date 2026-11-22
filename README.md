# Tiny Tunes

An Android app to start a young child on the basics of Indian classical singing (sargam), in short lessons the way a teacher does it: **listen, then sing it back**. The app listens through the microphone and shows him the note he sings.

Version 3 is a complete restart.

**[See the screens →](docs/screens/README.md)**

## How it works
1. **Find Sa.** In Indian music anyone can choose Sa (the home note). On first start, he (or you) sings one long, comfortable note, and the app makes that note Sa. Any octave counts, so a man, a woman and a child can all sing the same Sa.
2. **Lessons** (13, one step at a time): Sa · Sa Sa Sa · Sa Re · Re Sa · Sa Re Sa · Sa Re Ma · Ma Re Sa · Sa Re Ga · Ga Re Sa · Sa Re Ga Ma Pa · Pa Ma Ga Re Sa · Sa to upper Sa · and back down.
   - **Listen:** a real voice sings the notes (with a soft tanpura), and the note bubbles light up.
   - **Your turn:** starts the moment the voice stops, so an immediate echo counts. A glowing ball follows his voice over coloured note lines; when he holds the right note, its bubble turns gold. The whole phrase has to come in one go (no more than 3 seconds between notes).
   - **If nothing right comes** for about 6 seconds, the voice sings the whole phrase again at his next little pause. After two tries the lesson ends kindly.
   - **Done:** if the phrase ended away from Sa, the voice sings Sa once (the music always comes home). Then stars fall and the app plays back the singing it heard, only the moments when someone was singing.
   - A lesson needs 2 stars before the next one is suggested; later lessons are dimmed (all can still be opened).
   - One grey line tells the grown-up what to do: hum along while listening; stay quiet and point during his turn (the app can't tell your voice from his).
3. **Free singing:** the ball follows his voice and shows the note name; holding Sa for about a second makes stars fall.

## The singing voice
- **Built-in:** a real man's voice, from a free recording on Freesound ("Male singing Indian Sargam" by rachitgupta, CC0 public domain). It covers Sa, Re, Ma and Dha, so lessons 1 to 7 are sung by a real voice from the start. Other notes play on harmonium.
- **Record your own** (Grown-ups → Record a voice, about 2 minutes): anyone in the family says or sings each note long (Saaa, Reee…). You don't need to sing well; the app tunes each recording to the exact note. A lesson is sung in the recorded voice once all of its notes are recorded, so record all 8 with the same voice.

## Sound safety
- Soft levels, no sudden sounds, no sound effects; shouting never changes what he sees.
- **Tablet volume limit** (Grown-ups, default Medium): during music the tablet's own media volume is turned down to the limit if it is higher.
- No drone while he sings, so the app never mistakes its own tanpura for his Sa.

## Recognising notes
- Works for men's, women's and children's voices (65 to 1100 Hz).
- Children are rarely exactly on the note: "Easy" (the default) accepts about three-quarters of a semitone either side.
- Tested with real recordings: a child singing a scale (every note recognised, also with room echo and fan noise), a man singing sargam, a much quieter child, an immediate echo of the voice, a rhyme sung instead of the lesson, and grown-up speech (talking never earns a star). Room noise is never counted.
- The Android Back button (or an edge swipe) goes back one screen inside the app instead of closing music time.

## Install
Every push to `main` builds an APK and attaches it to a new Release ("Tiny Tunes v3.N"): open **Releases**, download `TinyTunes.apk` on the tablet and install it (allow "install unknown apps"). It installs over the older Tiny Tunes. Allow the microphone when asked.

## Docs (earlier work)
- [Research plan: Sur ki Neev](docs/plan.md) and its [slide deck](docs/presentation/README.md) describe the earlier version 2 design, not this app.

Everything stays on the tablet. Not a medical product.
