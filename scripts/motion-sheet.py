"""Compose the 16 sampled Blender frames for each active skill into a contact-sheet GIF."""
import argparse
from pathlib import Path
from PIL import Image, ImageDraw

SKILLS = ("shadow_dash","earthquake_mage","time_rewind","calamity_giant","duel_contract","bomb_alchemy","void_lord")
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("frames",type=Path)
    parser.add_argument("output",type=Path)
    args = parser.parse_args()
    sequence = []
    for frame in range(1,17):
        canvas = Image.new("RGB",(680,365),"#111b26")
        draw = ImageDraw.Draw(canvas)
        for index,skill in enumerate(SKILLS):
            x,y = index%4*170,index//4*165
            with Image.open(args.frames/f"{skill}-{frame:02}.png") as source:
                tile = source.convert("RGBA").resize((148,148),Image.Resampling.LANCZOS)
            canvas.paste(tile,(x+11,y),tile)
            draw.text((x+10,y+144),skill.replace("_"," "),fill="#e6edef")
        draw.text((10,339),f"BLENDER   /   PRIMARY MODEL   /   TICK {frame:02d}",fill="#a9cdc6")
        sequence.append(canvas)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    sequence[0].save(args.output,save_all=True,append_images=sequence[1:],duration=100,loop=0)
if __name__ == "__main__":
    main()
