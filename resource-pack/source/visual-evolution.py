"""Paper 8.2.1 visual models. All coordinates are Java item-model units."""
import math

COLORS = {
    "metal":"#4d6580","dark":"#171c2e","light":"#ceb9a0","trace":"#71d5d7",
    "aether":"#885af1","ember":"#f17c4c","jade":"#72cc97","ivory":"#ede5c7","brass":"#c18b56",
}
FAMILIES = dict(zip("검술 공허 그림자 금속 기계 대지 마법 생명 성장 시간 이동 화염".split(),
                    "sword void shadow metal machine earth arcane life growth time motion flame".split()))
FACES = ("north","south","east","west","up","down")


class Mesh:
    def __init__(self):
        self.elements = []

    def bar(self,x,y,z,w,h,d,finish="metal",axis="z",angle=0):
        # Keep intersecting trim an imperceptible distance off coincident outward faces.
        bias = len(self.elements)*.0002
        center = (x,y,z)
        half = (w/2,h/2,d/2)
        low = [round(v-r+bias,5) for v,r in zip(center,half)]
        high = [round(v+r-bias,5) for v,r in zip(center,half)]
        item = {"from":low,"to":high,"faces":{face:{"texture":"#"+finish,"uv":[0,0,16,16]} for face in FACES}}
        if angle:
            item["rotation"]={"axis":axis,"angle":angle,"origin":center}
        self.elements.append(item)
        return self

    def ring(self,x,y,z,r,finish="brass",axis="z"):
        for offset in range(8):
            angle = offset*math.pi/4
            u,v = r*math.sin(angle),r*math.cos(angle)
            point = (x+u,y+v,z) if axis == "z" else (x+u,y,z+v)
            turn = 45 if offset % 2 else 0
            if offset in (1,5): turn = -45
            if axis == "y": turn = -turn
            self.bar(*point,r*.9,.65,.85,finish,axis,turn)
            # Java elements accept only 0, ±22.5 or ±45 degrees. At 90° swap transverse dimensions.
            if offset in (2,6):
                item=self.elements[-1]
                horizontal=0
                vertical=1 if axis == "z" else 2
                for name in ("from","to"):
                    a= item[name][horizontal]-point[horizontal]
                    b= item[name][vertical]-point[vertical]
                    item[name][horizontal]=round(point[horizontal]+b,5)
                    item[name][vertical]=round(point[vertical]+a,5)
        return self

    def gem(self,x,y,z,side,finish="trace"):
        return self.bar(x,y,z,side,side,side,finish,"z",45).bar(x,y,z,side*.71,side*.82,side*1.19,finish,"x",-45)

    def ribs(self,x,y,z,finish="brass"):
        for j in (-1,0,1):
            self.bar(x+j*1.4,y,z,1,3.2,2,finish,"x",j*22.5)
        return self

    def model(self):
        finishes = sorted({box["faces"]["north"]["texture"][1:] for box in self.elements})
        return {"ambientocclusion":False,"gui_light":"front",
                "textures":{f:"augmentwild:fx/evolution/"+f for f in finishes},"elements":self.elements}


def scythe(m, x=8,y=8,z=8):
    for dx,dy,w,h,turn in [(-3,2,3.8,.65,22.5),(-.7,3,3.8,.72,0),(2,2.2,3.8,.95,-45),(3.2,.4,2.2,1.5,-45)]:
        m.bar(x+dx,y+dy,z,w,h,.87,"aether","z",turn).bar(x+dx,y+dy+.22,z+.07,w*.9,h*.38,.98,"ivory","z",turn)
    return m


def sword(m,x,y,z,finish="ivory",length=8):
    return (m.bar(x,y,z,1.15,length,.68,finish).bar(x,y+length*.46,z,.86,.86,.54,finish,"z",45)
            .bar(x,y-length*.44,z,3.4,.65,1,"brass").bar(x,y-length*.58,z,.7,1.35,.72,"dark"))


def conduit(m,x=8,y=8,z=8,finish="ember"):
    for dx,angle in [(-2.5,22.5),(2.5,-22.5)]:
        m.bar(x+dx,y,z,1.5,8.8,1.6,"dark","z",angle).bar(x+dx,y,z+.7,.56,7.4,.49,finish,"z",angle)
    return m.gem(x,y,z,2.65,finish)


def ward(m,y=8):
    return (m.bar(8,y,8,7.4,8.4,1.4,"metal","z",45).bar(8,y,8.8,6.3,7.2,.68,"brass","z",45)
            .gem(8,y,9.35,2.36,"trace"))


def crown(m):
    m.bar(8,6.4,8,9.1,1.85,6.9,"dark").bar(8,7.1,8,9.35,.63,7.2,"brass")
    for x,y,z,a in [(4.3,9,5.4,-22.5),(6.4,9.8,5.2,-22.5),(8,11,5.1,0),(9.6,9.8,5.2,22.5),(11.7,9,5.4,22.5)]:
        m.bar(x,y,z,1.06,4.1,.85,"ivory","z",a)
    return m.gem(8,8.9,4.45,1.3,"trace")


def hand(m):
    m.bar(8,5.4,8,4.6,7,4.4,"dark").bar(8,4.7,8.7,5.4,4.1,4.9,"metal")
    m.bar(8,8,8,5.8,2.4,5.1,"brass").bar(8,10.2,8,6.8,3.6,5.2,"metal")
    m.bar(8,10.2,10.8,5.9,2.7,.9,"light").gem(8,10.5,11.4,1.3,"ember")
    for i,x in enumerate((5.65,7.2,8.82,10.43)):
        h=(2.4,3.1,2.9,2.1)[i]
        m.bar(x,12.9,7.8,1.27,h,1.95,"brass","x",-22.5)
        m.bar(x,13.65,6.65,1.19,1.75,1.66,"light","x",-45)
        m.bar(x,11.9,6.15,1.08,1.28,1.63,"metal","x",22.5)
    return m.bar(4.4,10.3,7,1.75,3.4,2,"brass","z",22.5).bar(4.8,11.8,6.12,1.5,1.82,1.8,"light","x",-45)


def engine(m):
    m.bar(8,5,8,6.8,2.2,12,"dark").bar(8,8,8.9,4.8,4.8,8.3,"metal")
    m.bar(8,8,12.8,4.6,4.4,.9,"light").gem(8,8,13.5,1.45,"ember")
    m.bar(8,11.2,10.5,1.8,3,1.9,"brass").bar(8,12.4,10.5,2.5,.6,2.6,"dark")
    m.bar(8,9.2,4.3,5.7,4.6,3.3,"dark").bar(8,11.5,4.3,6.1,.7,3.8,"light")
    m.bar(8,4.9,14.1,6.2,1.8,1.5,"brass","x",22.5)
    for x in (4.2,11.8):
        m.bar(x,6.1,8.5,.5,.78,8.4,"brass")
        for z in (5.2,8.1,11):
            m.bar(x,4.55,z,.8,2.6,2.6,"metal","x",45)
    return m


def crate(m,premium=False):
    if premium:
        m.bar(8,2,8,11.6,1.1,10.4,"dark").bar(8,3.4,8,7.2,1.8,7.2,"brass","y",45)
        for u in (4.2,11.8):
            m.bar(u,5,8,1.4,5.5,8.5,"brass","z",22.5 if u<8 else -22.5)
            m.bar(8,5,u,8.4,5.1,1.2,"metal","x",22.5 if u<8 else -22.5)
        m.gem(8,5.8,8,3.55,"aether").gem(8,5.65,12.1,1.3,"ivory")
    else:
        m.bar(8,2.4,8,12.7,1.4,10.7,"dark")
        for x in (2.58,13.42):
            m.bar(x,5,8,1.55,5.2,10.45,"metal")
        for z in (3.27,12.73):
            m.bar(8,5,z,11.62,5.15,1.08,"metal")
        for x in (4.2,11.8):
            for z in (3,13):
                m.bar(x,5,z,1.45,5.25,1.18,"brass")
                m.bar(x,6.5,z+(.67 if z>8 else -.67),.47,.42,.33,"ivory")
        m.gem(8,5.1,13.25,1.05,"trace")
    return m


def lid(m,premium=False):
    # Separate lid mesh rests above the model origin. Back hinge lives at (8,8,3).
    if premium:
        m.bar(8,8.3,8,8.4,1.55,8.4,"brass","y",45).gem(8,9.4,8,2.25,"aether")
        for x in (4.5,11.5):
            m.bar(x,8.4,8,1.15,.95,7.7,"ivory","z",22.5 if x<8 else -22.5)
        return m.bar(8,7.6,12.55,2.2,1,.9,"dark").gem(8,7.2,12.8,.83,"trace")
    return (m.bar(8,8.3,8,12.7,1.65,10.6,"metal").bar(8,9.25,8,10.8,.58,8.9,"dark")
            .bar(4.4,9.45,8,1.1,.68,9,"brass").bar(11.6,9.45,8,1.1,.68,9,"brass")
            .bar(8,7.52,13.2,2.3,1.5,.95,"brass").gem(8,7.6,13.75,.72,"trace"))


def lattice(m,stage,finish="jade"):
    m.bar(8,2.7,8,8.55,1.2,7.7,"dark").gem(8,6.8,8,2.1,finish)
    for add in range(stage):
        r=2.1 + add*.4
        for axis in (0,1):
            x,z = (8+r,8-r) if axis else (8-r,8+r)
            m.bar(x,4.8+add*1.1,z,1.4,3.5,1.4,("brass","metal","light","ivory")[add],"z",(-1 if axis else 1)*22.5)
        m.bar(8,4+add*1.45,8,5.7-add*.72,.7,5.7-add*.72,finish,"y",45)
    return m


GROWTH_FORMS = ("metal","machine","sword","arcane","life")


def growth_form(m,family,stage):
    if family == "metal":
        m.bar(8,4,8,6,4.8,4,"dark").gem(8,6,10,1.8,"trace")
        for ring in range(stage):
            m.bar(8,4+ring*2,8,7+ring*.8,1.35,5+ring*.6,("brass","metal","light","ivory")[ring])
    elif family == "machine":
        m.bar(8,3.5,8,8,1.25,8,"dark").gem(8,7,8,2.3,"ember")
        for index in range(stage):
            x,z=((5,6),(11,6),(5,10),(11,10))[index]
            m.bar(x,6,z,1.6,5.1,1.5,"brass","x",(-1)**index*22.5).gem(x,8.8,z,1.22,"trace")
    elif family == "sword":
        sword(m,8,8,8,"ivory",7.5)
        for index in range(stage):
            direction=-1 if index%2 else 1
            m.bar(8+direction*(1.2+index*.6),8+index*.6,8,1.22,6.8,.68,"trace","z",direction*22.5)
    elif family == "life":
        m.gem(8,7.8,8,2.85,"jade")
        for direction in range(stage):
            x,z=((6,6),(10,6),(6,10),(10,10))[direction]
            m.bar(x,6,z,1.29,4.4,1.25,"jade","x",(-1)**direction*22.5).gem(x,8,z,1.33,"ivory")
    else:
        m.gem(8,6,8,2.35,"aether")
        for ring in range(min(stage,3)):
            m.ring(8,4+ring*2.25,8,3.7-ring*.38,("brass","trace","aether")[ring],"y")
        if stage == 4:
            for x,z in ((5,5),(5,11),(11,5),(11,11)):
                m.bar(x,10.5,z,1.01,1.01,1.01,"ivory","z",45)
    return m


def pieces():
    def unit(fn): return fn(Mesh())
    models = {
        "sig_shadow_dash":unit(lambda m: scythe(m).bar(6,8,8,5.6,.49,1.42,"dark","z",-22.5)),
        "sig_earthquake_mage":unit(lambda m:m.bar(8,6.2,8,10,.62,1.1,"ember","y",22.5)
            .bar(5.6,6.8,7.65,4.1,.8,2.1,"dark","z",-22.5).bar(10,6.55,9.55,6,.72,1,"brass","y",-45)
            .bar(6,8.1,6.6,2.35,3.9,2.57,"metal","x",22.5).bar(9.65,7.35,9.32,2.62,1.92,2.23,"light","z",-45)),
        "sig_time_rewind":unit(lambda m:m.ring(8,8,8,5.4,"brass","y").ring(8,8.65,8,3.8,"metal","y").gem(8,9,8,1.55)),
        "sig_void_lord":unit(lambda m:m.gem(8,8,8,4.2,"aether").ring(8,8,8,4.6,"dark").ring(8,8,8,5.4,"aether","y")),
        "sig_bomb_alchemy":unit(lambda m:m.gem(8,7.2,8,4.7,"dark").ring(8,7,8,4.1,"brass","y")
            .bar(8,10.27,8,2.6,1.7,2.5,"metal").gem(8,11.5,8,1.03,"ember").gem(8,7.4,10.8,1.52,"ember")),
        "sig_duel_contract":unit(lambda m:m.ring(8,7.4,8,5.35,"brass","y").bar(8,7.5,8,7.4,.72,1.12,"ivory","y",45)
            .bar(8,7.6,8,7,.73,.93,"ivory","y",-45).gem(8,8,8,1.31,"aether")),
        "sig_calamity_giant":unit(hand),
        "sig_calamity_train":unit(engine),
        "sig_thousand_swords":unit(lambda m: sword(sword(sword(m,4.6,8,7,"trace",6),11.4,8.8,7.5,"trace",6),8,8.7,10)),
        "sig_steel_king":unit(crown),
        "sig_steel_heart":unit(lambda m:ward(m).bar(8,7.7,9.8,4.2,.68,1.02,"ivory").gem(8,9.4,9.7,1.62,"ember")),
        "sig_magic_missile":unit(lambda m:m.gem(8,9.4,8,2.56).bar(8,6.8,8,2.7,5.9,1.8,"brass","x",-22.5).ribs(8,5.6,8,"trace")),
        "sig_berserk_engine":unit(lambda m:m.ring(8,8,8,4.2,"metal").gem(8,8,9.1,3.1,"ember").ribs(8,4.7,8)),
        "sig_infernal_conductor":unit(lambda m:conduit(m).bar(8,3.6,8,7,1.2,5,"metal").gem(8,11,8,1.75,"ember")),
        "sig_tank_engine":unit(lambda m:m.bar(8,6,8,10,3,8,"dark").bar(8,8,8,6.4,2.8,5.9,"metal")
            .bar(8,9.3,10,2,1.6,8,"brass","x",-22.5).bar(4,6,8,1.23,1.4,7,"trace").bar(12,6,8,1.23,1.4,7,"trace")),
        "sig_sword_wind":unit(lambda m:sword(scythe(m,8,5.8),8,8,10,"trace")),
        "sig_infinite_barrage":unit(lambda m:m.bar(8,4.4,8,8,1.86,6,"metal").ribs(8,8,8,"brass")
            .gem(8,8.5,8,1.91).bar(8,10.6,9.1,3.8,1.29,6,"ivory","x",-22.5)),
        "sig_hell_lord":unit(lambda m:conduit(m,8,7,8).ring(8,5,8,4.7,"dark","y")
            .bar(8,10.4,8,1.8,6,1.7,"ember","x",-22.5)),
        "sig_life_echo":unit(lambda m:m.gem(8,8,8,3.38,"jade").ring(8,7.3,8,4.35,"ivory")
            .bar(8,7.45,9,7.8,.6,.62,"trace").bar(8,8.5,9,2.1,.62,.61,"trace","z",45)),
        "sig_stacking":unit(lambda m:lattice(m,4)),
        "rig_time_rewind_a":unit(lambda m:m.bar(8,8.18,10.08,.62,.68,5.4,"ivory").gem(8,8.35,8,.72,"trace")),
        "rig_time_rewind_b":unit(lambda m:m.bar(8,8.3,9.48,.88,.65,3.75,"brass").gem(8,8.42,8,.67,"ember")),
        "rig_calamity_giant_a":unit(lambda m:m.bar(8,7.7,8,5.3,4.7,5.2,"dark").bar(8,8.9,10.6,4.55,3.1,1,"brass").ribs(8,6.8,10.7,"metal")),
        "rig_calamity_giant_b":unit(lambda m:m.bar(8,7,8,3.7,2.3,2.1,"brass","x",-22.5)
            .bar(7,8.2,7.95,1.4,2.2,1.9,"light","x",-45).bar(9,8.2,7.95,1.4,2.2,1.9,"light","x",-45)),
        "rig_calamity_train_a":unit(lambda m:m.ring(8,8,8,3.8,"brass").gem(8,8,8,1.25,"ember")),
        "rig_calamity_train_b":unit(lambda m:m.bar(8,8,8,7.2,.8,.88,"ivory").gem(5.4,8,8,1.28,"brass").gem(10.4,8,8,1.28,"brass")),
        "rig_thousand_swords_a":unit(lambda m:sword(m,8,8.45,8,"trace")),
        "rig_thousand_swords_b":unit(lambda m:sword(m,8,8.4,8,"ivory")),
        "supply":unit(crate), "premium":unit(lambda m:crate(m,True)),
        "supply_lid":unit(lid), "premium_lid":unit(lambda m:lid(m,True)),
        "supply_closed":unit(lambda m:lid(crate(m))), "premium_closed":unit(lambda m:lid(crate(m,True),True)),
        **{f"tier_growth_{i}":unit(lambda m,i=i:lattice(m,i)) for i in range(1,5)}
    }
    forms = {
        "sword":lambda m:sword(m,8,8,8), "shadow":scythe,
        "void":lambda m:m.ring(8,8,8,4.4,"aether").gem(8,8,8,2.3,"dark"),
        "metal":ward, "machine":lambda m:m.ribs(8,8,8).gem(8,8,8,1.5,"ember"),
        "earth":lambda m:m.bar(7,7,8,3,5,3.8,"metal","z",-22.5).bar(9,8,8.4,3.5,5,2.4,"brass","z",45),
        "arcane":lambda m:m.ring(8,8,8,4.6,"trace").gem(8,8,8,2.7,"aether"),
        "life":lambda m:m.gem(8,8,8,3.15,"jade").bar(8,8,9.3,6.5,.53,.68,"ivory"),
        "growth":lambda m:lattice(m,2), "time":lambda m:m.ring(8,8,8,4.8,"brass").bar(8,9.5,9,.58,4.28,.69,"ivory"),
        "motion":lambda m:m.bar(8,8,8,7,.95,2.1,"trace","z",22.5).bar(9,6,8.25,7,1.02,1.9,"ivory","z",22.5),
        "flame":lambda m:conduit(m,8,7.8,8),
    }
    models.update({"family_"+key:unit(fn) for key,fn in forms.items()})
    for key in GROWTH_FORMS:
        for stage in range(1,5):
            models[f"growth_{key}_{stage}"]=growth_form(Mesh(),key,stage)
    return models


def bake(assets,write,item):
    from PIL import Image, ImageColor, ImageDraw
    for finish,color in COLORS.items():
        path=assets/f"textures/fx/evolution/{finish}.png"
        path.parent.mkdir(parents=True,exist_ok=True)
        texture=Image.new("RGB",(32,32))
        rgb=ImageColor.getrgb(color)
        for y in range(32):
            for x in range(32):
                bevel=1.27 if min(x,y)<2 else .48 if max(x,y)>29 else 1
                grain=.89+((x*37+y*17)%23)/150
                glint=.24 if (x+2*y)%49 in (0,1) else 0
                texture.putpixel((x,y),tuple(round(min(255,v*(bevel*grain+glint))) for v in rgb))
        brush=ImageDraw.Draw(texture)
        if finish in ("brass","light","metal","ivory"):
            for x,y in [(4,4),(27,4),(4,27),(27,27)]:
                brush.rectangle((x,y,x+1,y+1),fill=tuple(round(v*.65) for v in rgb))
        texture.save(path,optimize=True)
    for key,mesh in pieces().items():
        write(assets/f"models/fx/{key}.json",mesh.model())
        item(f"fx/{key}",f"fx/{key}")
