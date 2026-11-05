# Voice-over script (3:00 film, 13 scenes)

Record **one file per scene**: `vo/01.m4a` … `vo/13.m4a` (m4a, wav or mp3 all work). Leave about 0.3 s of silence at the start of each. Then export the MP4 from the Motion page and run:

```bash
scripts/mix_voice.sh moment_film.mp4 vo/ moment_final.mp4
```

It places each file at its scene's start and lowers the music under your voice. Word budgets assume about 2.3 words per second.

| # | Starts | Length | Max words | Scene | Suggested line |
|---|---|---|---|---|---|
| 01 | 0:00 | 10 s | 23 | Problem | My home screen looks the same at lunch, in the car, and at one in the morning. Thirty icons, every time. |
| 02 | 0:10 | 10 s | 23 | Meet Moment | So I built Moment: a clock, a few rows, and a little companion that sleeps until it's needed. |
| 03 | 0:20 | 15 s | 34 | Pattern | It notices I eat at the uni restaurant most weekdays at noon, and proposes one card. I accept. Nothing changes without my OK. |
| 04 | 0:35 | 15 s | 34 | Talk | I can just talk to it. "Add Spotify to Gym." It's an agent: it edits my contexts, remembers what matters, and sets reminders. |
| 05 | 0:50 | 18 s | 41 | Wardrobe | And it dresses up. Muse Image edits the companion itself, so outfits stack as layers. I pick its face and its colours. |
| 06 | 1:08 | 17 s | 39 | Alive | It lives on its own: music on, it nods along. Real rain outside, out comes the umbrella. After midnight, it gets sleepy. |
| 07 | 1:25 | 17 s | 39 | Style | Make it yours: a clock font separate from the app font, and a carousel of living effects. Neon, fireworks, sakura, code rain. |
| 08 | 1:42 | 13 s | 29 | Themes | Save the whole look as a theme, tie it to a context, and share it with a friend as a code. |
| 09 | 1:55 | 15 s | 34 | Focus | At the library, distracting apps step aside. If I really want YouTube, I wait ten seconds. Notifications are held, then summed up after. |
| 10 | 2:10 | 12 s | 27 | Briefings | Every morning, the weather and my first meetings. Every evening, my steps and what's tomorrow. |
| 11 | 2:22 | 13 s | 29 | Basics | It's a real launcher: widgets per context, swipe down, swipe up, double-tap to lock, and the best match right above my thumb. |
| 12 | 2:35 | 15 s | 34 | Privacy | Rules on the phone decide when. Muse decides what. Only short labels ever reach the model, never my location or history. |
| 13 | 2:50 | 10 s | 23 | Outro | Moment. What this moment needs. Built solo with Muse Spark, Muse Image and Muse Voice. |
