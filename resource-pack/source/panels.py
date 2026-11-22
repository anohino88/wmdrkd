"""Transparent title overlays in Minecraft inventory GUI coordinates."""
import json
from pathlib import Path
from PIL import Image, ImageDraw

def bake(pack):
    root = pack / "assets/augmentwild"
    screens = json.loads(Path(__file__).with_name("screens.json").read_text())
    providers = [{"type": "space", "advances": {"\ue000": -8, "\ue001": -169}}]
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
            for dx, dy in ((0,0),(17,17)):
                k = 1 if dx == 0 else -1
                draw.line([(x+dx+3*k,y+dy),(x+dx,y+dy),(x+dx,y+dy+3*k)],fill=accent)
        # Fix the bitmap provider's advance to 177 px, independent of the layout.
        im.putpixel((175, 8), (52, 76, 87, 255))
        path = root / f"textures/gui/panels/{name}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        im.save(path, optimize=True)
        providers.append({"type":"bitmap","file":f"augmentwild:gui/panels/{name}.png",
                          "ascent":13,"height":h,"chars":[chr(0xe010+index)]})
    path = root / "font/interface.json"
    path.parent.mkdir(exist_ok=True)
    path.write_text(json.dumps({"providers":providers},separators=(",",":"))+"\n")
