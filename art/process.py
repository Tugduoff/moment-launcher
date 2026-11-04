"""Cuts the Muse Image expressions out of their white background for the app.

Every expression gets the SAME crop box, so swapping expressions never makes the
companion jump. Output: app/src/main/res/drawable-nodpi/mascot_<name>.png
Usage: python art/process.py [contact_sheet.png]   (needs Pillow)
"""
import sys
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageFilter

HERE = Path(__file__).parent
RAW = HERE / "raw"
OUT = HERE.parent / "app/src/main/res/drawable-nodpi"
NAMES = ["asleep", "awake", "talking", "thinking", "glasses", "seatbelt", "headphones", "sleepy",
         "body", "body_glasses", "body_headphones", "body_seatbelt"]
SIZE = 360


def cut(path: Path) -> Image.Image:
    img = Image.open(path).convert("RGB")
    w, h = img.size
    # Flood-fill the background from the corners with a sentinel colour.
    filled = img.copy()
    for corner in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1), (w // 2, 2), (2, h // 2)]:
        ImageDraw.floodfill(filled, corner, (255, 0, 255), thresh=40)
    bg = Image.eval(ImageChops.difference(filled, Image.new("RGB", (w, h), (255, 0, 255))).convert("L"),
                    lambda v: 255 if v < 8 else 0)
    # Soft edge: fade the 3 px ring around the shape by how white each pixel is.
    ring = bg.filter(ImageFilter.MaxFilter(7))
    light = img.convert("L")
    alpha = Image.new("L", (w, h), 255)
    px_a, px_bg, px_ring, px_l = alpha.load(), bg.load(), ring.load(), light.load()
    for y in range(h):
        for x in range(w):
            if px_bg[x, y]:
                px_a[x, y] = 0
            elif px_ring[x, y]:
                px_a[x, y] = max(0, min(255, int((255 - px_l[x, y]) * 6)))
    out = img.convert("RGBA")
    out.putalpha(alpha)
    return out


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    cutouts = {n: cut(RAW / f"{n}.png") for n in NAMES if (RAW / f"{n}.png").exists()}
    # One shared, square crop box around everything any expression draws (z's, glasses, cups).
    box = None
    for im in cutouts.values():
        b = im.getchannel("A").point(lambda v: 255 if v > 20 else 0).getbbox()
        box = b if box is None else (min(box[0], b[0]), min(box[1], b[1]), max(box[2], b[2]), max(box[3], b[3]))
    cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
    side = max(box[2] - box[0], box[3] - box[1]) * 1.04
    crop = (int(cx - side / 2), int(cy - side / 2), int(cx + side / 2), int(cy + side / 2))
    sheet = Image.new("RGBA", (SIZE * len(cutouts), SIZE * 2), (247, 244, 238, 255))
    for i, (n, im) in enumerate(cutouts.items()):
        final = im.crop(crop).resize((SIZE, SIZE), Image.LANCZOS)
        final.save(OUT / (f"{n}.png" if n.startswith("body") else f"mascot_{n}.png"), optimize=True)
        sheet.alpha_composite(final, (i * SIZE, 0))
        dark = Image.new("RGBA", (SIZE, SIZE), (18, 19, 23, 255))
        dark.alpha_composite(final)
        sheet.alpha_composite(dark, (i * SIZE, SIZE))
        print("wrote", n)
    if len(sys.argv) > 1:
        sheet.convert("RGB").resize((sheet.width // 3, sheet.height // 3)).save(sys.argv[1])


if __name__ == "__main__":
    main()
