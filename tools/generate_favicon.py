"""Regenerate the favicon with Pillow: python tools/generate_favicon.py."""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1]
static = root / "src/main/resources/static"
scale = 16
image = Image.new("RGBA", (32 * scale, 32 * scale), (0, 0, 0, 0))
draw = ImageDraw.Draw(image)
def box(values):
    return tuple(round(v * scale) for v in values)
draw.rounded_rectangle(box((1, 1, 21, 21)), radius=2 * scale, fill="#1768b3")
draw.line([box(p) for p in [(4.5, 6), (7, 16), (11, 9), (15, 16), (17.5, 6)]], fill="white", width=2 * scale, joint="curve")
draw.ellipse(box((12.5, 12.5, 31.5, 31.5)), fill="#f2f6fc")
draw.ellipse(box((14, 14, 30, 30)), fill="#102b4d")
draw.arc(box((17.5, 17, 26.5, 27)), 45, 315, fill="white", width=round(2.5 * scale))
image.save(static / "favicon.ico", sizes=[(n, n) for n in (16, 24, 32, 48, 64, 128, 256)])
# SVG source follows the same simple geometry without relying on installed fonts.
(static / "favicon.svg").write_text('''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">
  <rect x="1" y="1" width="20" height="20" rx="2" fill="#1768b3"/>
  <path d="M4.5 6 7 16 11 9 15 16 17.5 6" fill="none" stroke="white" stroke-width="2" stroke-linejoin="round"/>
  <circle cx="22" cy="22" r="9.5" fill="#f2f6fc"/>
  <circle cx="22" cy="22" r="8" fill="#102b4d"/>
  <path d="M25.18 18.46 A4.5 5 0 1 0 25.18 25.54" fill="none" stroke="white" stroke-width="2.5"/>
</svg>
''', encoding="utf-8")
preview = Image.new("RGB", (480, 180), "#f2f6fc")
for offset, size in [(0, 16), (160, 32), (320, 128)]:
    icon = image.resize((size, size), Image.Resampling.LANCZOS).resize((128, 128), Image.Resampling.NEAREST)
    preview.paste(icon, (offset + 16, 20), icon)
(root / "target").mkdir(exist_ok=True)
preview.save(root / "target/favicon-preview.png")
with Image.open(static / "favicon.ico") as ico:
    assert ico.ico.sizes() == {(n, n) for n in (16, 24, 32, 48, 64, 128, 256)}
print("Created ICO with seven sizes (16-256px), SVG, and size preview.")
