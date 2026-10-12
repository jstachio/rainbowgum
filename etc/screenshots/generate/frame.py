# Rounds the window corners and adds a soft drop shadow on a transparent background.
import sys
from PIL import Image, ImageDraw, ImageFilter
src, dst = sys.argv[1], sys.argv[2]
win = Image.open(src).convert('RGBA')
w, h = win.size
r, pad = 22, 48
mask = Image.new('L', (w, h), 0)
ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, h - 1), r, fill=255)
out = Image.new('RGBA', (w + 2 * pad, h + 2 * pad), (0, 0, 0, 0))
shadow = Image.new('L', out.size, 0)
ImageDraw.Draw(shadow).rounded_rectangle((pad, pad + 12, pad + w, pad + h + 12), r, fill=110)
shadow = shadow.filter(ImageFilter.GaussianBlur(20))
out.paste((0, 0, 0, 255), (0, 0), shadow)
win.putalpha(mask)
out.alpha_composite(win, (pad, pad))
# Hairline border so light windows separate from a white page.
ImageDraw.Draw(out).rounded_rectangle((pad, pad, pad + w - 1, pad + h - 1), r, outline=(128, 128, 128, 70), width=2)
out.save(dst, optimize=True)
