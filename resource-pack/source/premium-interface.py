"""Original Arcane Archive inventory art. The pack generator delivers 32px slot textures."""
ROWS = {
    "menu": 6, "choice": 4, "detail": 3, "codex": 6, "slots": 3, "settings": 4,
    "lab": 6, "result": 6, "supply": 3, "journal": 6, "warning": 1, "scout": 5,
    "status": 6, "track": 3, "admin": 6, "graphics": 4, "controls": 4, "pack": 3,
    "death": 5, "owned": 6, "evolution": 4, "synergy": 4, "resonance": 4, "filters": 6,
}

# Rebuilt silhouettes for small-scale collisions identified in the previous atlas.
# Paths use a 32-unit canvas. Layers tagged 0 cut through the main silhouette.
REBUILT = {
    "blood_crown": [(1, [(1,5),(8,12),(14,4),(16,10),(21,4),(24,12),(31,5),(27,23),(23,28),(8,28),(4,23)]),
                    (0,[(12,19),(16,14),(20,19),(16,24)])],
    "blood_god": [(1,[(16,0),(28,16),(30,22),(26,29),(16,32),(6,29),(2,22),(4,16)]),
                  (0,[(16,11),(23,20),(22,25),(16,28),(10,25),(9,20)])],
    "phoenix_heart": [(1,[(0,7),(10,12),(16,6),(22,12),(32,7),(29,20),(23,19),(16,32),(9,19),(3,20)]),
                      (0,[(13,14),(16,11),(19,14),(16,21)])],
    "center_of_universe": [(1,[(0,16),(4,8),(16,2),(26,5),(32,14),(27,25),(15,30),(5,26)]),
                           (0,[(6,17),(10,11),(18,8),(24,10),(26,15),(22,21),(13,24),(8,22)]),
                           (1,[(13,15),(18,11),(21,16),(17,23)] )],
    "wound_record": [(1,[(4,2),(26,2),(29,5),(29,29),(7,29),(3,25)]),
                     (0,[(7,18),(12,13),(16,17),(21,10),(25,12),(18,23),(13,19),(9,23)])],
    "berserk": [(1,[(16,0),(22,9),(28,6),(29,17),(32,25),(22,31),(9,31),(0,25),(4,11),(10,14)]),
                (0,[(7,23),(11,22),(14,15),(18,25),(22,19),(26,21),(19,29),(16,27),(13,22),(10,25)])],
    "heart_harvester": [(1,[(1,10),(7,5),(14,7),(16,10),(20,6),(25,5),(31,10),(31,17),(24,25),(16,31),(5,23),(1,17)]),
                        (0,[(7,16),(14,18),(22,10),(25,11),(23,17),(17,21),(16,27),(13,26),(14,22),(7,21)])],
    "dual_wielder": [(1,[(0,2),(7,3),(16,12),(25,3),(32,2),(29,9),(20,18),(27,26),(22,31),(15,23),(7,31),(3,27),(11,18),(3,9)])],
    "storm_blade": [(1,[(29,0),(30,9),(17,19),(21,21),(14,25),(7,30),(3,28),(10,20),(8,17),(13,14),(15,16)]),
                    (1,[(0,13),(10,4),(9,10),(15,8),(8,15),(7,13)])],
    "survival_instinct": [(1,[(1,9),(7,2),(11,10),(21,10),(25,2),(31,9),(28,25),(16,31),(5,25)]),
                          (0,[(6,14),(14,17),(16,22),(11,21)]),(0,[(26,14),(18,17),(16,22),(21,21)])],
    "delayed_blast": [(1,[(5,6),(11,3),(23,3),(27,7),(28,13),(32,14),(32,21),(28,22),(25,29),(9,29),(3,25),(3,8)]),
                      (0,[(15,8),(19,8),(19,16),(24,19),(22,23),(15,19)])],
    "world_erosion": [(1,[(0,6),(11,6),(13,12),(22,4),(31,6),(27,15),(23,14),(21,20),(16,19),(13,30),(2,28),(4,19),(0,17)]),
                      (0,[(8,10),(15,12),(11,18),(16,20),(12,22),(7,19)])],
    "defense": [(1,[(2,8),(9,8),(9,3),(23,3),(23,8),(30,8),(29,22),(16,32),(3,22)]),
                (0,[(9,13),(23,13),(22,19),(16,26),(10,19)])],
    "tank_engine": [(1,[(1,19),(4,14),(10,14),(12,8),(23,8),(24,12),(31,12),(31,17),(23,18),(31,22),(29,28),(3,28),(0,24)]),
                    (0,[(6,22),(26,22),(26,25),(6,25)])],
    "shadow_dash": [(1,[(0,28),(8,22),(7,18),(16,16),(12,11),(24,9),(30,0),(32,10),(26,22),(14,29),(6,31)]),
                    (0,[(12,24),(22,19),(28,13),(24,23)])],
    "void_lord": [(1,[(16,0),(23,10),(29,6),(32,14),(27,26),(16,32),(5,26),(0,14),(3,6),(9,10)]),
                   (0,[(16,9),(23,18),(16,26),(9,18)])],
}

METAL = {
    "SILVER": ("#172a37","#778fa3","#dceefa"),
    "GOLD": ("#281718","#a56e46","#ffe5a0"),
    "PRISMATIC": ("#191630","#6853ad","#f2d7ff"),
}

def frame(tier, socket=False):
    """Each material has a different construction, legible by silhouette without colour."""
    from PIL import Image, ImageDraw
    panel = Image.new("RGBA",(128,128))
    d = ImageDraw.Draw(panel)
    dark, alloy, edge = METAL[tier]
    if tier == "SILVER":
        hull = [(11,20),(29,9),(103,9),(117,22),(117,103),(105,117),(24,117),(10,103)]
        d.polygon(hull,fill=dark,outline="#080f18",width=6)
        d.line((15,96,15,23,30,13,101,13,112,24),fill=alloy,width=8)
        d.line((113,33,113,101,103,113,25,113),fill=alloy,width=6)
        d.line((14,34,26,22,52,22),fill=edge,width=3)
        for x,y in [(25,30),(103,30),(25,97),(103,97)]:
            d.rectangle((x-3,y-3,x+3,y+3),fill=alloy)
    elif tier == "GOLD":
        d.polygon([(29,13),(99,13),(113,28),(106,94),(64,125),(22,94),(15,28)],fill=dark,outline=alloy,width=7)
        d.polygon([(16,23),(40,14),(35,29),(17,62),(5,49)],fill=alloy,outline=edge,width=3)
        d.polygon([(112,23),(88,14),(93,29),(111,62),(123,49)],fill=alloy,outline=edge,width=3)
        for x in (25,37,91,103):
            d.line((x,89,64,117),fill=alloy,width=3)
        d.polygon([(64,97),(76,108),(64,118),(52,108)],fill=edge)
    else:
        d.polygon([(63,1),(104,21),(125,62),(105,99),(64,127),(22,103),(3,64),(25,24)],fill=dark,outline=alloy,width=5)
        for hull, color in [
            ([(2,61),(18,31),(33,38),(23,81),(16,93)],"#6654be"),
            ([(126,61),(112,31),(97,38),(107,81),(114,93)],"#558ea0"),
            ([(64,1),(82,18),(64,29),(45,18)],edge),
            ([(64,127),(42,106),(64,96),(84,106)],"#a4e6e2"),
        ]:
            d.polygon(hull,fill=color,outline=edge,width=2)
        d.arc((20,23,106,108),195,345,fill="#9caee7",width=3)
        d.arc((20,22,106,108),15,165,fill="#765fa3",width=3)
    if socket:
        d.polygon([(41,66),(64,89),(87,66),(83,92),(64,109),(45,92)],fill=alloy,outline=edge,width=3)
        d.polygon([(54,41),(74,41),(80,53),(64,72),(48,53)],fill=dark,outline=edge,width=3)
    return panel

def write_folio(path, orthographic, tier, color, key):
    from PIL import Image, ImageChops, ImageColor, ImageDraw, ImageFilter
    mask = orthographic
    if key in REBUILT:
        mask = Image.new("L",(128,128))
        draw = ImageDraw.Draw(mask)
        for solid, points in REBUILT[key]:
            draw.polygon([(x*4,y*4) for x,y in points],fill=255*solid)
    mask = mask.crop(mask.getbbox())
    scale = min(82 / mask.width,78 / mask.height)
    mask = mask.resize((round(mask.width*scale),round(mask.height*scale)),Image.Resampling.LANCZOS)
    glyph = Image.new("L",(128,128))
    glyph.paste(mask,((128-mask.width)//2,(114-mask.height)//2))
    master = frame(tier)
    shadow = Image.new("RGBA",master.size,"#070c17")
    shadow.putalpha(glyph.filter(ImageFilter.MaxFilter(9)))
    master.alpha_composite(shadow)
    shades = Image.new("RGBA",master.size)
    rgb = ImageColor.getrgb(color)
    for y in range(128):
        for x in range(128):
            bright = max(0,1 - abs(x*.75 + y - 68)/38)
            falloff = max(0,1 - y/128)
            multifacet = 0.35 if 89 < 0.38*x + y < 99 else 0
            values = [int(min(255,c*(.52 + .33*falloff + .28*bright - multifacet) + 48 + 24*bright)) for c in rgb]
            shades.putpixel((x,y),tuple(values)+(255,))
    lip = ImageChops.subtract(glyph,glyph.filter(ImageFilter.MinFilter(5)))
    light = Image.new("RGBA",master.size,"#f3f7fb")
    light.putalpha(lip.point(lambda n: int(n*.58)))
    shades.putalpha(glyph)
    master.alpha_composite(shades)
    master.alpha_composite(light)
    master.resize((32,32),Image.Resampling.LANCZOS).save(path,optimize=True)
    return master

# Every slot seal uses an original outline and tool glyph. Empty cells supply most spacing.
SEALS = {
    "sector_match": ("#c9ac83", [(64,9),(110,29),(118,79),(64,120),(10,79),(18,29)],
                     [(30,81,30,50,64,28,98,50,98,81),(30,81,98,81),(52,81,52,64,76,64,76,81)]),
    "sector_archive": ("#a4d6c7", [(12,25),(43,12),(64,26),(85,12),(116,25),(116,104),(85,92),(64,112),(43,92),(12,104)],
                       [(64,42,64,94),(25,43,44,40,55,48),(73,48,85,40,103,43),(25,69,44,66,55,74),(73,74,85,66,103,69)]),
    "sector_combat": ("#eea581", [(13,21),(54,8),(70,16),(113,21),(114,72),(89,98),(64,120),(16,86)],
                      [(28,95,98,30,100,20,88,22,26,85),(31,27,94,94),(31,27,33,44),(76,89,89,76)]),
    "sector_settings": ("#c0b6ed", [(46,8),(82,8),(89,22),(108,24),(118,44),(110,63),(120,80),(108,103),(88,101),(78,120),(47,120),(37,101),(17,100),(8,80),(17,61),(9,43),(18,24),(36,22)],
                        [(24,47,103,47),(24,79,103,79),(49,34,49,60),(77,66,77,92)]),
    "sector_admin": ("#eab585", [(26,13),(101,13),(118,42),(118,92),(94,115),(28,115),(10,92),(10,42)],
                     [(31,86,96,86),(36,75,36,50,64,31,92,50,92,75),(50,54,50,75),(78,54,78,75)]),
    "owned": ("#bae9cd", [(14,32),(64,11),(113,32),(112,93),(64,119),(16,93)],[(14,50,64,76,112,50),(64,76,64,109),(56,61,72,61,72,81,56,81,56,61)]),
    "evolution": ("#abe0cc", [(64,6),(97,29),(107,81),(82,113),(40,116),(17,83),(25,37)],[(37,82,64,56,91,82),(37,61,64,35,91,61),(64,35,64,98)]),
    "synergy": ("#e6bc91", [(18,25),(51,12),(100,16),(116,49),(107,106),(61,119),(14,97)],[(32,87,64,36,98,87,32,87),(64,36,64,83),(44,67,84,67)]),
    "resonance": ("#d1aaff", [(64,4),(98,21),(119,65),(98,104),(64,123),(26,105),(9,65),(28,23)],[(27,53,44,67,54,38,70,87,82,54,100,67),(38,89,51,100,76,101,89,89)]),
    "graphics": ("#9de4e9", [(14,29),(26,15),(100,15),(114,29),(114,92),(98,108),(30,108),(14,92)],[(28,79,47,58,60,70,79,43,101,82),(30,92,98,92)]),
    "diagnostics": ("#efc596", [(21,17),(97,17),(111,34),(111,97),(97,111),(23,111),(12,94),(12,33)],[(27,74,46,74,57,43,69,88,80,63,99,63),(28,93,95,93)]),
    "selected": ("#a1f2c6", [(33,9),(93,9),(116,31),(116,94),(94,117),(33,117),(10,94),(10,31)],[(30,63,54,87,96,39)]),
    "sealed": ("#acb8c8", [(28,20),(98,20),(112,34),(112,93),(98,107),(28,107),(14,93),(14,34)],[(44,56,44,43,53,35,76,35,84,43,84,56),(40,56,88,56,88,90,40,90,40,56),(64,66,64,79)]),
}

def seal(key):
    from PIL import Image, ImageDraw
    edge, hull, commands = SEALS[key]
    raster = Image.new("RGBA",(128,128))
    ink = ImageDraw.Draw(raster)
    ink.polygon(hull,fill="#101a27",outline="#080d15",width=7)
    ink.polygon([(64+(x-64)*.93,64+(y-64)*.93) for x,y in hull],fill="#1a2838",outline=edge,width=3)
    ink.polygon([(x,.35*y+14 if i < 3 else .74*y+14) for i,(x,y) in enumerate(hull)],
                fill="#263749")
    for path in commands:
        ink.line(path,fill="#090d16",width=12,joint="curve")
        ink.line(path,fill=edge,width=7,joint="curve")
    return raster
