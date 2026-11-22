"""Offline panel illustrations in native inventory coordinates. The server uses native titles."""
import json
from pathlib import Path
from PIL import Image, ImageDraw

def bake(pack):
    root = pack / "assets/augmentwild"
    screens = json.loads(Path(__file__).with_name("screens.json").read_text())
    for index, (name, (rows, accent, slots)) in enumerate(screens.items()):
        h = 18 * rows + 18
        im = Image.new("RGBA", (176, h))
        draw = ImageDraw.Draw(im)
        draw.rounded_rectangle((1, 1, 174, 14), 4, fill="#14212d", outline=accent)
        draw.line((133, 6, 168, 6), fill="#41586a")
        for x in range(index % 4 + 1):
            draw.polygon([(166-5*x,5),(168-5*x,7),(166-5*x,9),(164-5*x,7)], fill=accent)
        for slot in slots:
            x, y = 7 + slot % 9 * 18, 17 + slot // 9 * 18
            # Inset backing and bevel remain inside their native 18 px control cell.
            draw.rounded_rectangle((x+1,y+1,x+16,y+16),radius=3,fill=(21,33,46,170))
            draw.line((x+4,y+2,x+12,y+2),fill=(145,171,186,150))
            draw.line((x+4,y+15,x+12,y+15),fill=(4,11,18,215))
            for dx, dy in ((0,0),(17,17)):
                k = 1 if dx == 0 else -1
                draw.line([(x+dx+3*k,y+dy),(x+dx,y+dy),(x+dx,y+dy+3*k)],fill=accent)
        path = root / f"textures/gui/panels/{name}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        im.save(path, optimize=True)
    # Keep panel artwork available for previews without registering oversized font glyphs.
    (root / "font/interface.json").unlink(missing_ok=True)
