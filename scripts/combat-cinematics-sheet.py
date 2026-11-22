"""Compose Blender renders into a public combat-effects filmstrip and loop."""
import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


FORMS = ("arc", "guard", "blast", "void")
TILE = 150
BACKGROUND = "#141b28"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"


def card(source, title):
    model = Image.open(source).convert("RGBA")
    model.thumbnail((TILE, TILE), Image.Resampling.LANCZOS)
    result = Image.new("RGB", (TILE, TILE + 24), BACKGROUND)
    result.paste(model, ((TILE-model.width)//2, 0), model)
    draw = ImageDraw.Draw(result)
    draw.text((8,TILE),title,font=ImageFont.truetype(FONT,14),fill="#dee6f2")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("frames",type=Path)
    parser.add_argument("output",type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True,exist_ok=True)
    strip = Image.new("RGB",(TILE*3,(TILE+24)*4),BACKGROUND)
    frames = []
    for frame in range(9):
        board = Image.new("RGB",(TILE*4,TILE+24),BACKGROUND)
        for index,name in enumerate(FORMS):
            image = card(args.frames / f"{name}-{frame:02}.png",name.upper())
            board.paste(image,(index*TILE,0))
            if frame in (0,4,8):
                strip.paste(image,(frame//4*TILE,index*(TILE+24)))
        frames.append(board)
    strip.save(args.output / "public-combat-frames.png",optimize=True)
    frames[0].save(args.output / "public-combat.gif",save_all=True,append_images=frames[1:],
                   duration=[100]*8+[240],loop=0)


if __name__ == "__main__":
    main()
