# Submission draft (Cerebral Valley form)

**Team name:** Moment
**Track:** Indie Developer Track
**Problem statement:** Build for Your World
**Public GitHub repository:** https://github.com/Tugduoff/moment-launcher
**3-minute demo video:** _(YouTube/Loom link)_

## Project description

Moment is a calm Android home screen with one living companion. Instead of thirty icons, it shows a clock, the companion, at most four apps or actions for *this moment*, and search. At the uni restaurant at noon it shows "Pay with Izly" and "Directions to 17 bd Jourdan". When the car's Bluetooth connects it shows three big buttons. At the library it hides Instagram and YouTube, and "Open anyway" makes you wait 10 seconds. Late at night away from home it offers a ride home, location sharing and a call.

On-device rules decide *when* a context applies (time, place label, Bluetooth, visit patterns). Muse Spark decides *what* goes in it. When the companion notices "you come here most weekdays at noon", it wakes up and proposes a context on a card you accept, edit or dismiss. You can also type or say things like "At the library, hide social apps and show Notion", or "put on a cowboy hat". The companion is a rig: Muse Image drew its body, and the phone animates its face at 60 fps (blinks, glances, talking, sleeping z's). Muse Image dresses it at runtime, and accessories stack. It also paints a calm background for each context on its own, and postcards of itself to share.

It goes further than a fixed launcher: snap your class timetable and Muse Spark vision proposes a week of contexts; one sentence ("I go to the gym Tuesday and Thursday evenings, and I play guitar") becomes several staged contexts through a Muse Spark agent with real tool calling; live weather comes from Muse Spark with web search; steps and battery stay on the phone and make the companion react. Swipe sideways to see every context and pick one by hand.

## Describe your use of Meta products to build the submitted project

- **Muse Spark (muse-spark-1.3)** through Meta Model API's OpenAI-compatible Chat Completions, with **structured output** (`response_format: json_schema`) and `reasoning_effort: minimal` (≈3–6 s instead of ≈14 s). One schema-constrained call turns either a detected pattern or any sentence into one of five intents: a full context (name, chip, trigger, ≤4 apps/actions with deep-link kinds, apps to hide, companion mood, palette, greeting, outfit), open_app, dress, undress or answer. A second call sorts the installed apps once (distracting, transport, payment…) so Focus knows what to hide.
- **Muse Spark as an agent**: real tool calling (get_now, list_contexts, list_apps, propose_context, delete_context, open_app, wear, undress, remember, share_postcard, paint_background), up to 6 turns with parallel calls. One sentence like "I go to the gym Tuesday and Thursday evenings and I play guitar" becomes several staged contexts on one approval card. Facts it remembers personalize later turns.
- **Muse Spark + web_search (search grounding)** through the Responses API with a JSON schema gives live weather. When it really rains, Moment switches to rain mode by itself. Only the city name is sent.
- **Muse Image (muse-image-1.0)**. Offline, we generated the companion, then its 8 expressions and 4 faceless rig bodies by *editing* one base image with `/images/edits`, which keeps the character identical across assets. At runtime, accepting a context or asking "play the guitar" sends the companion's *current* body back to `/images/edits`. The phone cuts the result out with a green-screen key and de-spill, so accessories **stack** (cowboy hat, then guitar), while the procedural face keeps blinking and talking on top. "Share" makes Muse Image paint a postcard of the dressed companion in a scene for the current moment, ready to post. The companion also paints a calm **home screen background** per context with `/images/generations`, proactively the first time a context is used, or on request through the agent's `paint_background` tool.
- **Muse Voice Transcribe (muse-voice-transcribe-1.0)**: push-to-talk via `/asr/transcribe` in PUSH_TO_TALK mode, with the user's installed app names as **vocabulary keywords** and English + French **language bias**.
- Design: mockups in Figma; app built with Jetpack Compose.

## Why are Meta's models important to your solution?

The hard part of a context-aware launcher isn't detecting *that* you're at the uni restaurant at noon. Simple rules do that on the phone. The hard part is knowing *what* belongs on the screen there, and letting people change it in their own words. Muse Spark's constrained JSON output lets us turn "at the library, hide social apps and show Notion" straight into a valid, typed context the launcher can apply, with no fragile parsing and a confirm card in front of it. Sending only labels (never coordinates or history) keeps it private.

Muse Image makes the AI feel like a companion instead of a feature. Because it edits images while keeping the character consistent, one base drawing became a full set of expressions and rig bodies. At runtime, the companion can visibly dress for each new context or for whatever you ask. Muse Voice Transcribe, biased with your own app names, makes talking to it as easy as tapping it. Three Meta models, one character.

## Feedback on partner technologies
_(optional; fill in if you used Baseten / Modal / Fireworks)_

## Survey suggestions (adjust to your experience)
- Likely to build with Meta Model API: Very likely
- Overall opinion: Very favorable
- Biggest obstacle: Slow responses (default reasoning effort ~14 s; fixed with `reasoning_effort: minimal`)
- Compared with other models: _your call_
- What matters most: Quality of the answers; Easy integration (OpenAI-compatible API)

---

# Bonus prizes to aim for (from the participant guide)

- **Most Viral Project ($20K):** post the demo on X, then submit the post link on the hackathon platform. Ranked on likes + reposts + comments until **Nov 8 EOD**.
- **Best Creative Build ($5K):** Muse Image use (rig bodies, runtime outfits that stack, postcards). Flagged in round one.
- **Best Agentic Use-Case ($5K):** the Muse Spark tool-calling agent with the staged plan card.
- **Community Vote ($10K):** public gallery votes, announced Nov 16. Share the gallery link once it is up.

# YouTube (the 3-minute submission video)

Visibility: **Public** or **Unlisted** (the form needs a link judges can open; Public also helps views).

**Title:** Moment: a home screen that knows what this moment needs (Meta Muse Spark + Muse Image)

**Description:**
Moment is a calm Android launcher with one living companion. Instead of thirty icons, it shows a clock, the companion, at most four things for this moment, and search.

At the uni restaurant at noon: Pay with Izly, directions. In the car: three big buttons. At the library: distracting apps hidden. Late at night: a ride home, share location, call a friend.

Built solo in 9 days for the Meta Global AI Developer Hackathon:
• Muse Spark agent with real tool calling turns one sentence into staged contexts you approve
• Muse Spark vision reads a photo of your timetable
• Muse Spark + web search for live weather
• Muse Image drew the companion and dresses it at runtime (accessories stack), then paints shareable postcards
• Muse Voice Transcribe for push-to-talk

Code (MIT): https://github.com/Tugduoff/moment-launcher

0:00 The problem
(add chapters once the cut is final)

#MetaHackathon #MuseSpark #Android

# X post (Most Viral)

X caps videos at 2:20 on non-Premium accounts: post a short cut (30–60 s) natively, link the full video on YouTube.

**Main post (with the short video attached):**
My home screen used to have 30 icons. Now it has 4, and a little peach that knows where I am 🍑

Moment is an Android launcher that shows what this moment needs and hides the rest. Built solo for the @AIatMeta hackathon with Muse Spark + Muse Image.

Full demo + code ↓

**Reply 1:**
At the uni restaurant at noon it shows Pay + directions. In the car, three big buttons. At the library it hides Instagram, and "Open anyway" makes you wait 10 s.

It noticed I eat there every weekday and asked before changing anything.

**Reply 2:**
Ask it to "put on a cowboy hat, then play guitar" and Muse Image dresses it. Accessories stack, and the face keeps blinking on top (it's drawn live at 60 fps).

**Reply 3:**
"I go to the gym Tuesday and Thursday evenings" → the Muse Spark agent checks your apps and time, and stages two contexts on one card you approve.

3-min demo: <YouTube link>
Code (MIT): https://github.com/Tugduoff/moment-launcher
@cerebral_valley #MetaHackathon

Tips: post with the video natively (not a link) for reach; reply to every comment the first day; pin the post; ask friends to repost early. Then paste the post link in the submission form.
