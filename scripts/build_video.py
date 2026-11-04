"""Turns demo_raw.mp4 + demo_markers.txt (from scripts/demo_run.sh) into a captioned 1920x1080 cut.

Each beat gets a caption on the left and the phone on the right; Muse wait times are sped up so
each beat fits its target length. Needs Pillow and an ffmpeg binary (FFMPEG env var or on PATH).
Usage: python scripts/build_video.py demo_raw.mp4 demo_markers.txt out.mp4
"""
import os, subprocess, sys, tempfile
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
FONT = ROOT / "app/src/main/res/font/dm_sans.ttf"
MASCOT = ROOT / "app/src/main/res/drawable-nodpi/mascot_awake.png"
FFMPEG = os.environ.get("FFMPEG", "ffmpeg")
W, H, FPS = 1920, 1080, 30
BG, INK, INK2 = (247, 244, 238), (42, 38, 34), (138, 131, 122)
PH_H = 1000; PH_W = round(PH_H * 1080 / 2424); PH_X = W - PH_W - 230; PH_Y = (H - PH_H) // 2

# beat name -> (title, subtitle, target seconds)
BEATS = {
    "morning": ("A home screen that fits the moment.", "08:47. Asleep. Nothing to say, so it says nothing.", 8),
    "library": ("At the library, it hides the noise.", "Distracting apps hidden. “Open anyway” costs you 10 seconds.", 9),
    "noon": ("It notices patterns, on the phone.", "Three lunches at 17 bd Jourdan around noon. Rules, no cloud.", 6),
    "idea": ("Muse Spark drafts the context.", "One JSON-schema call. Only labels leave the phone.", 12),
    "accept": ("Accept, and it dresses for the place.", "Muse Spark picks a chef hat. Muse Image draws it. The face stays alive.", 18),
    "talk": ("Talk to it.", "“Put on a red cowboy hat” → Muse Spark intent: dress → Muse Image edit.", 17),
    "guitar": ("Accessories stack.", "Each Muse Image edit builds on what it already wears.", 15),
    "car": ("Car Bluetooth connects.", "Three big buttons. Search disappears.", 7),
    "night": ("23:30, away from home.", "Ride home, share location, call a friend.", 6),
    "game": ("Headphones on, evening.", "Game mode. It nods to the beat.", 6),
    "rain": ("And when it rains…", "Something cozy.", 6),
}


def font(size, weight=400):
    f = ImageFont.truetype(str(FONT), size)
    try:
        f.set_variation_by_axes([14, weight])
    except Exception:
        pass
    return f


def wrap(draw, text, f, width):
    words, lines, cur = text.split(), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if draw.textlength(t, font=f) <= width: cur = t
        else: lines.append(cur); cur = w
    return lines + [cur]


def panel(title, sub, path):
    im = Image.new("RGB", (W, H), BG)
    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle((PH_X, PH_Y + 14, PH_X + PH_W, PH_Y + PH_H + 14), 52, fill=(51, 38, 26, 60))
    im.paste(sh.filter(ImageFilter.GaussianBlur(28)), (0, 0), sh.filter(ImageFilter.GaussianBlur(28)))
    d = ImageDraw.Draw(im)
    x, y, tw = 150, 380, PH_X - 150 - 120
    for line in wrap(d, title, font(66, 500), tw):
        d.text((x, y), line, font=font(66, 500), fill=INK); y += 80
    y += 18
    for line in wrap(d, sub, font(34), tw):
        d.text((x, y), line, font=font(34), fill=INK2); y += 46
    d.text((x, H - 110), "Moment  ·  Muse Spark  ·  Muse Image  ·  Muse Voice Transcribe", font=font(24), fill=INK2)
    im.save(path)


def card(lines, path):
    im = Image.new("RGB", (W, H), BG); d = ImageDraw.Draw(im)
    m = Image.open(MASCOT).convert("RGBA").resize((300, 300))
    im.paste(m, ((W - 300) // 2, 230), m)
    y = 560
    for text, size, weight, color in lines:
        f = font(size, weight); w = d.textlength(text, font=f)
        d.text(((W - w) / 2, y), text, font=f, fill=color); y += size + 26
    im.save(path)


def mask(path):
    m = Image.new("L", (PH_W, PH_H), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, PH_W - 1, PH_H - 1), 48, fill=255)
    m.save(path)


def run(*args):
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", *args], check=True)


def main(raw, markers, out):
    marks = [(float(t), n) for t, n in (l.split() for l in Path(markers).read_text().split("\n") if l.strip())]
    tmp = Path(tempfile.mkdtemp()); parts = []
    mask(tmp / "mask.png")
    card([("Moment", 96, 500, INK), ("A calm home screen with one living companion.", 38, 400, INK2)], tmp / "intro.png")
    card([("Moment", 80, 500, INK), ("Rules decide when. Muse decides what. You approve.", 36, 400, INK2),
          ("github.com/Tugduoff/moment-launcher", 30, 400, INK2)], tmp / "outro.png")
    run("-loop", "1", "-t", "4", "-i", str(tmp / "intro.png"), "-r", str(FPS), "-pix_fmt", "yuv420p", "-c:v", "libx264", str(tmp / "p00.mp4"))
    parts.append(tmp / "p00.mp4")
    for i, (t, name) in enumerate(marks):
        if name not in BEATS or i + 1 >= len(marks): continue
        end = marks[i + 1][0]; title, sub, target = BEATS[name]
        speed = max(1.0, (end - t) / target)
        panel(title, sub, tmp / f"{name}.png")
        seg = tmp / f"p{i + 1:02d}.mp4"
        run("-loop", "1", "-i", str(tmp / f"{name}.png"), "-ss", f"{t:.2f}", "-to", f"{end:.2f}", "-i", raw,
            "-loop", "1", "-i", str(tmp / "mask.png"),
            "-filter_complex",
            f"[1:v]setpts=(PTS-STARTPTS)/{speed:.3f},fps={FPS},scale={PH_W}:{PH_H},format=rgba[ph];"
            f"[2:v]format=gray,scale={PH_W}:{PH_H}[m];[ph][m]alphamerge[phm];"
            f"[0:v][phm]overlay={PH_X}:{PH_Y}:shortest=1,format=yuv420p[v]",
            "-map", "[v]", "-r", str(FPS), "-c:v", "libx264", "-crf", "18", str(seg))
        parts.append(seg)
        print(f"{name}: {end - t:.1f}s raw -> {(end - t) / speed:.1f}s (x{speed:.2f})")
    run("-loop", "1", "-t", "6", "-i", str(tmp / "outro.png"), "-r", str(FPS), "-pix_fmt", "yuv420p", "-c:v", "libx264", str(tmp / "p99.mp4"))
    parts.append(tmp / "p99.mp4")
    (tmp / "list.txt").write_text("".join(f"file '{p}'\n" for p in parts))
    run("-f", "concat", "-safe", "0", "-i", str(tmp / "list.txt"), "-c", "copy", out)
    print("wrote", out)


if __name__ == "__main__":
    main(*sys.argv[1:4])
