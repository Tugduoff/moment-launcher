# Moment launcher - design spec (from Figma jUgpF9fQn576lPQNa6muqR)

Source: file "Moment - launcher mockups", page "Mockups" (0:1). Frame "Components" (2:11) + 8 screens.
Screenshots: `docs/figma/components.png`, `1-home-asleep.png` ... `8-rain-chill.png` (412x915 @1x).
Units: Figma px == dp. Phone frame 412 x 915 dp, corner radius 36 (frame only, ignore on device).
No design system library; tokens are Figma variables (per-mode values below). Colors are opaque unless noted (#RRGGBBAA = alpha last).

## 1. Colors (per mode)

Token names: `bg`, `textPrimary`, `textSecondary`, `pill` (surface/pill: chip, search, action, big tile, secondary btn), `card` (surface/card: hint bubble, suggestion card), `stroke` (stroke/subtle, 1dp), `accentFill`/`accentOn` (primary button).

| Mode | bg | textPrimary | textSecondary | pill | stroke | chip dot |
|---|---|---|---|---|---|---|
| Light (screens 1-4) | #F7F4EE | #2A2622 | #8A837A | #FFFFFF | #E6DFD4 | #2A2622 |
| Study (5) | #ECEFF4 (flat) | #2A2622 | #8A837A | #FFFFFF | #E6DFD4 | #2A2622 |
| Car (6, dark) | #121317 | #F2F2F5 | #8E909B | #23252B | #34363E | #F2F2F5 |
| Game (7) | gradient, see below | #FFFFFF | #E9DEFF | #FFFFFF @ 18% (#FFFFFF2E) | #FFFFFF @ 30% (#FFFFFF4D) | #FFFFFF |
| Rain/chill (8) | vertical gradient #7E8CA2 (top) -> #CBD3DE (bottom) | #1F2A36 | #46566A | #FFFFFF @ 40% (#FFFFFF66) | #FFFFFF @ 50% (#FFFFFF80) | #1F2A36 |

- Light-only: card = #FFFFFF; accentFill = #2A2622 (primary btn bg); accentOn = #FFFFFF (primary btn text).
- Hint-bubble dot (8dp circle): #FA8C59 (orange). Suggestion card shadow: 0 8 24 rgba(51,38,26,0.08), y=8dp blur=24dp.
- Status bar text/icons use textPrimary of the mode (icons are 2dp strokes, color = textPrimary; Wifi/Battery icons are 16 and 18 dp, listed under 4).
- Study "Hidden apps" pill: 1dp DASHED border #8A837A (textSecondary), no fill, opacity 0.9 on whole row, radius 16.

### Game background (screen 7), two stacked layers
1. Base: linear-gradient angle 114.24deg (css, 0deg = up, clockwise), stops: #FF2BD6 @14.286%, #6A2BFF @50%, #00D4FF @85.714% (pink -> violet -> cyan).
2. Overlay on top: vertical (180deg) stops: rgba(0,0,0,0.10) @0%, rgba(10,5,31,0.40) @45%, rgba(10,3,31,0.88) @100% (darkens to navy bottom).
3. Blurred glows (above gradient, below content), blur = gaussian stdDev 45:
   - cyan: circle r=160 color #22D3FF alpha 0.45, center (380, 580) in screen coords (node 320x320 at x=220,y=420).
   - pink: circle r=150 color #FF3FD2 alpha 0.45, center (70, 410) (node 300x300 at x=-80,y=260).
(Compose: draw circles with `BlurEffect`/`Modifier.blur(45.dp)` or radial gradients approximating the same.)

### Rain background (screen 8)
- Vertical gradient #7E8CA2 -> #CBD3DE (top -> bottom).
- "Soft light": white ellipse rx=230 ry=140 alpha 0.35, blur stdDev 40, centre (190, 260) screen coords (node 460x280 at x=-40,y=120).
- Rain overlay (412x915, under content): 42 "streaks" + 9 "droplets".
  - Streak: rounded rect width 1.5, height 25-61 (random), radius 0.75, rotated 12deg clockwise about its top-left, fill white alpha 0.19-0.48 (random).
    Samples (x, y, h, alpha): (39.65,219.58,43.6,.348) (23.78,790.98,41.7,.332) (112.31,703.13,47.6,.303) (204.38,211.57,50.3,.203) (230.97,438.53,50.9,.227) (393.82,872.90,31.0,~.4). Pseudo-random scatter across the full screen is fine.
  - Droplet: ellipse ~5-12 wide x 6-14 tall (w:h ~ 1:1.15), fill white alpha 0.30 + 1.5dp white stroke alpha 0.5. Samples centre/size: (391.3,786.6, 8.2x9.4), (208,629,7.9x9.1), (372,494.8,10.5x12.1), (263.7,740.2,11.9x13.7), (43.5,861.2,8.1x9.3), (268.8,361.7,8.9x10.2), (320.3,457.8,10.3x11.8), (358.2,645.4,8.1x9.3), (156.6,424.6,5.4x6.2).

## 2. Typography
Font: **DM Sans** (Google Fonts; variable, optical size axis opsz=14). Weights: Light 300, Regular 400, Medium 500. Letter-spacing 0 unless noted. line-height "auto" = font default (use ~1.2x if needed).

| Element | size sp | weight | line-height | letter-spacing | color |
|---|---|---|---|---|---|
| Clock "08:47" | 88 | Light | 96 | -2.64 sp (-0.03em) | textPrimary |
| Date "Tuesday 27 October" | 17 | Regular | auto (~22) | 0 | textSecondary |
| Status-bar time | 14 | Medium | auto | 0 | textPrimary |
| App row label | 24 | Regular | auto (row 44, text ~31) | 0 | textPrimary |
| App row hint (right-aligned) | 14 | Regular | auto | 0 | textSecondary |
| Context chip | 13 | Medium | auto | 0 | textPrimary |
| Hint bubble | 14 | Medium | auto | 0 | textPrimary |
| Action title ("Pay with Izly") | 17 | Medium | auto | 0 | textPrimary |
| Action detail | 13 | Regular | auto | 0 | textSecondary |
| Big tile title ("Navigation") | 28 | Medium | auto | 0 | textPrimary |
| Big tile detail ("Home") | 15 | Regular | auto | 0 | textSecondary |
| Suggestion card title | 19 | Medium | 26 | 0 | textPrimary |
| Suggestion card detail | 14 | Regular | 20 | 0 | textSecondary |
| Button label (all 3 styles) | 15 | Medium | auto | 0 | Primary: accentOn; Secondary: textPrimary; Ghost: textSecondary |
| Search placeholder | 16 | Regular | auto | 0 | textSecondary (icon at opacity 0.6) |
| Hidden-apps text | 14 | Regular | auto | 0 | textSecondary |
| "Open anyway (10 s)" | 14 | Medium, underlined | auto | 0 | textPrimary |
| Car caption "Search is hidden while driving" | 13 | Regular | auto | 0 | textSecondary |

## 3. Layout (dp)
Screen 412 x 915. Content column: x = 28 (side padding 28 both sides, content width 356), top padding 14 (status bar top), bottom padding 28. Everything is a vertical column with a flexible spacer (weight 1f) between the clock block and the bottom block.

Vertical stack (y from top), screens 1-3 (no chip):
- Status bar: y=14, h=24 (time left; wifi 16dp + battery 18dp right, 6dp gap).
- Spacer 48 -> Clock block y=86 (clock text h=96, 2dp gap, date h~22 -> block h=120).
- Screens 4-8: after date, spacer 12, then Context chip (y=222, h=31) => clock block h=167.
- Flexible spacer.
- "Now" list (App rows / Action cards), 6dp gap between items, anchored to bottom block.
- Spacer 24 (10 in screen 3, 20 in Study/Car).
- Companion row: h=100, mascot 96x100 left aligned (x=28), y=723..823 on screens 1-5, 7, 8.
- Spacer 12 -> Search bar y=835, h=52 (ends at 887; bottom padding 28).

Components:
- **App row**: h=44, padding vertical 8, label left, hint right (SpaceBetween), no bg/border. Row pitch 50 (44 + 6 gap).
- **Context chip**: h=31, padding 12h/6v, radius 14, 1dp stroke, bg pill, 6dp dot + 6dp gap + text 13sp. Width hugs.
- **Search bar**: 356x52, radius 26, 1dp stroke, bg pill, padding h 18, gap 10, search icon 20dp (opacity 0.6) + placeholder.
- **Action card**: 356x72, radius 22, padding 14 (left) / 16 (right) / 14 v, gap 14, 1dp stroke, bg pill. Icon holder 44x44 circle-ish (radius 22), 1.5dp stroke (stroke token), icon 24dp centered. Text column (gap 2) weight 1f; trailing chevron 24dp opacity 0.5.
- **Big tile** (car): 356x116, radius 28, padding h 28, gap 20, icon 36dp, text column gap 4. Tiles stacked with 12dp gap (block 372 tall: y=395..767 on car screen), then spacer 20, then companion row y=787 (no search bar).
- **Hint bubble**: 198x40 (hugs), radius TL18 TR18 BR18 **BL4**, 1dp stroke, bg card, padding 14h/10v, gap 8, 8dp dot (#FA8C59). Sits to the right of mascot in companion row: gap 4 (x=100 from row start), vertically centred (y=30).
- **Suggestion card** (screen 3): 356x214, at y=499, padding 20 (inner 314 wide), column gap 12 (+ 2dp spacer before buttons), radius TL24 TR24 BR24 **BL6**, 1dp stroke, bg card, shadow above. Contains title (2 lines, 26 lh), detail (2 lines, 20 lh), then buttons row (gap 8).
- **Buttons**: radius 20, padding 18h/10v, text 15 Medium. Primary: fill #2A2622 (h40, w88 "Accept"). Secondary ("Edit", w66): fill pill #FFFFFF + 1dp stroke #E6DFD4 (h42 incl. border). Ghost ("Dismiss", w91): no fill/border, text #8A837A.
- **Hidden-apps row** (Study): 356x40, radius 16, padding 14h/10v, gap 8, lock icon 16dp (opacity 0.6), text weight 1f, underlined action text right.
- Study layout: chip "Study * library" (w124); list y=459 (4 rows); spacer 10; hidden row y=663; spacer 20; companion y=723.

Icons (24dp viewBox, stroke 2, round caps/joins, stroke = textPrimary; drawn as stroke-only paths):
- Search: circle c(11,11) r7 + line (20,20)-(16.5,16.5).
- Navigate: `M3 11L21 3L13 21L11 13L3 11Z`.
- Chevron: `M9 18L15 12L9 6`.
- Card: rounded rect x2..22, y5..19, r2 + line (2,10)-(22,10).
- Lock: body `M6 11H18 ...` rounded rect x4..20, y11..21 r2 + shackle `M8 11V7a4 4 0 0 1 8 0V11`.
- Wifi: 3 arcs at y 9->5.2, 12.5->9.6, 16->14.6 (x2..22, 5..19, 8.5..15.5) + dot (12,19.5) r1.
- Battery: body rect x2..20,y7..17 r2, nub line (22,11)-(22,13), inner fill rect x4..15,y9..15 (filled).
- Music (36dp): note stems `M13.5 27V7.5L31.5 4.5V24` + two circles r4.5 at (9,27) and (27,24). Phone (36dp): standard handset glyph (Feather "phone" scaled 1.5). Both stroke 2 textPrimary.
(In big tiles icons are 36dp, same style; in dark car mode stroke #F2F2F5.)

## 4. Mascot ("Companion"), 96 x 100 dp artboard
Origin top-left of a 96x100 box. All 7 states share the base, then add a face + accessories. Coordinates below are artboard units (1 unit = 1 dp at 96x100; scale uniformly for other sizes). "Body diameter" D = 76 (body width 10..86); height 70 (y 20..90).

### Base (identical in every state, draw in this order)
1. Ground shadow: ellipse c(48,94) rx26 ry4, black alpha 0.12.
2. Stem: stroke path `M48 22 C48 16 49 12 51 8`, width 3, round cap, #5E9E62.
3. Leaf: fill #8FCB8A, path `M51 10 C57 3 66 4 68 9 C62 13 55 14 51 10 Z` (pointed ellipse, ~17 wide, tilted right; starts at stem tip).
4. Body: **flat fill #FFE2A6** (no gradient, no outline, no body shadow other than ground shadow). Egg/rounded path:
   `M48 20 C72 20 86 38 86 60 C86 80 71 90 48 90 C25 90 10 80 10 60 C10 38 24 20 48 20 Z`
   (width 76 = 1.0 D, height 70 = 0.92 D, widest at y=60, i.e. 0.57 of body height from top).
5. Blush cheeks: ellipses c(29,66) and c(67,66), rx6 ry3.5, fill #F7A98B alpha 0.7 (0.16D wide, 0.09D tall; 19/76 apart from center-line 48).
Eye reference points: left eye centre (34,56), right (62,56) (eye spacing 28 = 0.37 D, at 0.51 body height); mouth centre x=48.
Face ink colour: #33291F.

### Per-state face (drawn after base)
- **Asleep**: closed eyes = downward arcs, stroke 3 round cap #33291F: left `M28 56 C32 59.33 36 59.33 40 56`, right `M56 56 C60 59.33 64 59.33 68 56` (smile-shaped U arcs, 12 wide, 3 deep). Mouth: tiny ellipse c(48,72) rx2.5 ry2, fill #33291F alpha 0.6. **Z's**: `M72 24 H80 L72 33 H80` (stroke 2.4, round cap/join, #9A8F80; ~8x9) and smaller `M83 9 H89 L83 16 H89` (stroke 2, same colour; ~6x7). Upper-right of head, outside body.
- **Awake**: eyes = filled circles c(34,56) and c(62,56) r5 fill #33291F, each with white highlight circle r1.6 at (35.8,54.2) and (63.8,54.2). Mouth = smile arc `M42 70 C46 73.33 50 73.33 54 70`, stroke 3, round cap, #33291F (12 wide).
- **Talking / surprised**: same open eyes + highlights. Mouth open: ellipse c(48,72) rx6 ry5, fill #33291F, plus tongue ellipse c(48,74.5) rx3.5 ry2, fill #F07A6A (mouth `M48 77 ...`: 12 wide x 10 tall).
- **Study (glasses)**: awake eyes + highlights, mouth = flat line `M43 72 H53`, stroke 3 round cap #33291F. Glasses: two circles c(34,56) and c(62,56) r10, fill white alpha 0.35, stroke 2.5 #33291F; bridge line `M44 56 H52` stroke 2.5 round cap. (Lens diameter 20 = 0.26 D.)
- **Car (seatbelt)**: awake eyes + highlights, mouth smile `M42 69 C46 72.33 50 72.33 54 69` (stroke 3). Seatbelt drawn on top of body: strap line `M84 71 L32 88`, stroke width 8, round cap, #3B3F4A (dark diagonal across lower body, right-high to left-low); buckle: small rounded square ~13x11 rotated ~-17deg, centre ~(58,79.5), fill #C9CCD3, path `M60.41 73.98 L52.81 76.46 C51.75 76.8 51.18 77.93 51.52 78.98 L53.07 83.73 C53.41 84.78 54.54 85.36 55.59 85.02 L63.19 82.54 C64.25 82.2 64.82 81.07 64.48 80.02 L62.93 75.27 C62.59 74.22 61.46 73.64 60.41 73.98 Z`.
- **Game (headphones)**: drawn after blush, before eyes. Band: arc `M12 58 C12 22 84 22 84 58`, stroke 5 round cap #2B2B35. Ear cups (rounded rects 14x22, r6): left x4..18, y50..72 fill **#FF3FD2** (pink) path `M12 50H10C6.69 50 4 52.69 4 56V66C4 69.31 6.69 72 10 72H12C15.31 72 18 69.31 18 66V56C18 52.69 15.31 50 12 50Z`; right x78..92, y50..72 fill **#22D3FF** (cyan), same shape mirrored. Eyes: awake open eyes + highlights. Mouth: half-moon/open grin, fill #33291F: `M40 69 C45.33 75.67 50.67 75.67 56 69 H40 Z` (16 wide, ~5 deep).
- **Chill (sleepy/content + drop)**: eyes = happy-closed upward arcs (cap shape), stroke 3 round cap #33291F: left `M28 58 C32 54 36 54 40 58`, right `M56 58 C60 54 64 54 68 58`. Mouth smile `M42 70 C46 73.33 50 73.33 54 70` stroke 3. Drop (sweat/rain drop) fill #8EC5F2, teardrop with tip at (74,14), bulb c(74,21.5) r4: `M74 14 C74 14 70 19 70 21.5 C70 22.56 70.42 23.58 71.17 24.33 C71.92 25.08 72.94 25.5 74 25.5 C75.06 25.5 76.08 25.08 76.83 24.33 C77.58 23.58 78 22.56 78 21.5 C78 19 74 14 74 14 Z` (upper right of head, 8 wide x 11.5 tall).

Compose: parse paths with `PathParser().parsePathString(d).toPath()`; stroke paths use `Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)`; scale canvas by `size.width / 96f`. State->screen mapping: Asleep (1, 4), Awake (2), Talking (3), Study (5), Car (6), Game (7), Chill (8). Screen 4 uses Asleep; screens 1 and 4 same.

## 5. Copy (exact)
Common: date "Tuesday 27 October" (screens 1-6; 7 = "Friday 30 October"; 8 = "Sunday 1 November"). Search placeholder: "Search apps, or ask" (all except Car). Status-bar time equals clock time.

1. **Home - asleep**: clock 08:47. Rows: "Messages"; "Spotify" - "Morning mix"; "Calendar" - "9:00 Algorithms"; "Camera". Mascot asleep.
2. **Home - awake**: clock 11:58. Same list as screen 1 (Messages, Spotify/"Morning mix", Calendar/"9:00 Algorithms", Camera). Mascot awake + hint bubble "I have an idea for lunch".
3. **Home - talking**: clock 12:02. No list. Suggestion card: title "You come here most weekdays at noon. Make it 'Uni restaurant'?"; detail "Adds Izly and directions to 17 bd Jourdan on weekdays around noon."; buttons "Accept" (primary), "Edit" (secondary), "Dismiss" (ghost). Mascot talking.
4. **Uni restaurant - lunch**: clock 12:10. Chip "Uni restaurant · lunch" (w169). Action 1: "Pay with Izly" / "Opens your payment QR code" (card icon). Action 2: "Directions to 17 bd Jourdan" / "Walking route in Maps" (navigate icon). Rows: "Messages" (no hint), "Spotify" - "Lunch mix". Mascot asleep.
5. **Study mode** (bg #ECEFF4): clock 15:30. Chip "Study · library". Rows: "Notion" - "Algorithms notes"; "Course portal"; "Calculator"; "Spotify" - "Focus". Hidden-apps row: lock icon, "Instagram, TikTok hidden", underlined "Open anyway (10 s)". Mascot study (glasses).
6. **Car mode** (dark): clock 18:05, date "Tuesday 27 October". Chip "Driving". Big tiles: "Navigation" / "Home" (navigate icon); "Music" / "Resume playlist" (music icon); "Calls" / "Favourites" (phone icon). Mascot car (seatbelt) with caption right of it "Search is hidden while driving" (gap 4, vertically centred, 13sp). No search bar. Companion row y=787.
7. **Game mode** (gradient): clock 21:40, date "Friday 30 October". Chip "Game mode". Rows: "Discord" - "Squad voice"; "Steam"; "Twitch"; "Brawl Stars". Mascot game (headphones).
8. **Rain / chill mode**: clock 16:20, date "Sunday 1 November". Chip "Rainy afternoon". Rows: "Spotify" - "Rainy day mix"; "Notes"; "Books". Mascot chill.
