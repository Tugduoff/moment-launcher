# Voice-over script (3:00 film)

Read at a calm pace. Each line has to fit inside its scene; the start time is when the scene begins.
Record **one file per scene** (`vo/01.m4a` … `vo/17.m4a`, any of m4a/wav/mp3) so retakes are easy: `scripts/mix_voice.sh` places each file at its scene's start and ducks the music under your voice.
Leave ~0.3 s of silence at the start of each file.

| # | Starts | Scene | Line |
|---|---|---|---|
| 01 | 0:00 | Thirty icons (8 s) | My home screen looks the same at lunch, in the car, and at one in the morning. Thirty icons, every time. |
| 02 | 0:08 | Meet Moment (7 s) | So I built Moment: a home screen that shows what this moment needs, and hides the rest. |
| 03 | 0:15 | It sleeps (9 s) | Most of the time, it's just a clock, a few rows, and this little companion, asleep. |
| 04 | 0:24 | An idea (14 s) | But it notices patterns. I eat at the uni restaurant most weekdays at noon, so it wakes up with an idea. Muse Spark turns it into one card, and nothing changes without my OK. |
| 05 | 0:38 | Dressed by Muse (13 s) | I accept. Now I get Pay with Izly and directions, one tap each. And Muse Image dresses the companion for the place: a chef's hat. |
| 06 | 0:51 | Library focus (12 s) | At the library, it hides YouTube and the noisy apps. If I really want one, I wait ten seconds first. |
| 07 | 1:03 | In the car (9 s) | When the car's Bluetooth connects: three big buttons. Nothing else. |
| 08 | 1:12 | Late night (9 s) | Late at night, away from home: a ride home, share my location, call a friend. |
| 09 | 1:21 | Rain and games (10 s) | When it actually rains in Paris, it goes cozy. Headphones in? Game mode, and it nods along. |
| 10 | 1:31 | Just say it (18 s) | Or I just tell it: "I go to the gym Tuesday and Thursday evenings, and I play guitar." The Muse Spark agent checks my time, contexts and apps with real tool calls, and stages two contexts for me to approve. |
| 11 | 1:49 | Snap a timetable (14 s) | New semester? I snap my timetable. Muse Spark reads the photo and proposes my whole week. I tick what I want, and add all. |
| 12 | 2:03 | The wardrobe (14 s) | And yes, it gets dressed. "Put on a red cowboy hat." "Now play the guitar." Accessories stack, and the wardrobe keeps them. |
| 13 | 2:17 | Postcards (10 s) | Then Muse Image paints a postcard of us, ready to share. |
| 14 | 2:27 | A living rig (10 s) | It's not a GIF. Muse Image drew the body once; the phone draws the face live, sixty times a second, so every outfit still blinks and talks. |
| 15 | 2:37 | Rules, then Muse (10 s) | Rules on the phone decide when. Muse decides what. Only labels leave the phone, never my location or history. |
| 16 | 2:47 | Built on Muse (8 s) | Three Muse models, Spark, Image and Voice Transcribe, doing six jobs. |
| 17 | 2:55 | Try Moment (5 s) | That's Moment. Built solo, open source. Thanks! |

Then: export the MP4 from the Motion page (Export button) and run

```bash
scripts/mix_voice.sh moment_film.mp4 vo/ moment_final.mp4
```
