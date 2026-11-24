"""Rotated Minecraft cuboid silhouettes. Coordinates are in item model units."""
import math

PALETTES = {
    "shadow_dash": "#8552e8", "earthquake_mage": "#efad58", "time_rewind": "#60ecdf",
    "void_lord": "#bc68ff", "bomb_alchemy": "#f9a057", "duel_contract": "#e7ce85",
    "calamity_giant": "#fb744e", "steel_king": "#94e5ee", "calamity_train": "#ffd187",
    "infinite_barrage": "#91d4ff", "hell_lord": "#ff7958", "thousand_swords": "#e8d9ff",
    "life_echo": "#8cf4cf", "stacking": "#adef9f", "steel_heart": "#b9e9f8",
    "magic_missile": "#9fc9ff", "berserk_engine": "#ff9179", "infernal_conductor": "#ff9258",
    "tank_engine": "#a5d1f9", "sword_wind": "#9eedeb",
}

ACTIVE = ("shadow_dash", "earthquake_mage", "time_rewind", "void_lord", "bomb_alchemy",
          "duel_contract", "calamity_giant")


def build(kind, mesh):
    def part(x,y,z,w,h,d,color="light",axis="z",angle=0):
        # Keep to Minecraft's conservative element rotation set. A quarter turn can
        # be represented by swapping the unrotated box dimensions around its pivot.
        angle = math.remainder(angle,180)
        if abs(angle) > 45:
            if axis == "x": h,d = d,h
            if axis == "y": w,d = d,w
            if axis == "z": w,h = h,w
            angle -= math.copysign(90,angle)
        angle = round(angle/22.5)*22.5
        mesh(x,y,z,w,h,d,color,axis,angle)

    def hoop(radius,y,count,color="metal",pitch=0,width=.55):
        for i in range(count):
            degrees = i * 360 / count
            a = math.radians(degrees)
            chord = radius * 2 * math.sin(math.pi / count) * .84
            part(8 + radius*math.sin(a),y,8 + radius*math.cos(a),chord,width,width,
                 "light" if i % (count//4) == 0 else color,"y", -math.remainder(degrees+pitch,360))

    def crystal(x,y,z,size,axis="z",angle=45,tone="light"):
        part(x,y,z,size,size,size,tone,axis,angle)
        part(x,y+size*.68,z+.23,size*.42,size*.48,size*.42,"accent",axis,-angle)

    def fissure():
        for i, (x,z,angle) in enumerate([(3.2,5.3,-45),(5.7,6.9,22.5),(7.9,7.5,-45),
                                        (10.3,8.4,-22.5),(12.3,10.3,-45),(13.4,12.6,45)]):
            part(x,5+.06*i,z,2.65,.36,.43,"light","y",angle)
            part(x,5.4+i*.15,z+.7,1.3,1.1+.3*(i%3),.95,"metal","z",(-1)**i*22.5)
        for x,z,angle in [(4,9,30),(7,11,-40),(9,4,28),(12,6,-25)]:
            part(x,6.8,z,.74,2.2,.82,"dark","z",angle)

    def blades(count, spread, kind):
        for i in range(count):
            x = 8 + (i - (count-1)/2) * spread
            y = 8 - abs(i - (count-1)/2) * .68
            angle = (i - (count-1)/2) * 20
            part(x,y,8+i*.12,.67,6.3,.33,"light","z",angle)
            part(x+.12,y-.41,8.39+i*.12,1.95,.42,.67,"metal","z",angle)
            part(x,y+2.85,8.12+i*.12,.55,.62,.31,"accent","z",angle-45)
        if kind == "thousand_swords":
            hoop(5.6,5.3,8,"accent",0,.39)

    def beam(a,b,height,depth,tone):
        x,z=a
        u,v=b
        part((x+u)/2,7.9+height,(z+v)/2,math.hypot(u-x,v-z)+depth*.68,
             .36,depth,tone,"y",-math.degrees(math.atan2(v-z,u-x)))

    def rim(radius,y,tone,depth,steps=8):
        for i in range(steps):
            angle=(i+.5)*math.tau/steps
            chord=2*radius*math.tan(math.pi/steps)+depth*.37
            part(8+radius*math.sin(angle),y+i*.006,8+radius*math.cos(angle),
                 chord,.34,depth,tone,"y",math.degrees(angle))

    if kind in ("shadow_dash","crescent"):
        for i in range(9):
            theta=math.radians(-100+25*i)
            x,z=8+5*math.sin(theta),8+4.8*math.cos(theta)
            taper=.58+1.85*math.sin(math.pi*(i+1)/10)
            part(x,8.1+i*.075,z,2.85,.24 + .30 * math.sin(math.pi*(i+1)/10),taper,"light","y",math.degrees(theta))
            part(8+(x-8)*.79,7.73+i*.075,8+(z-8)*.79,2.6,.37,taper*.91,
                 "dark" if kind=="shadow_dash" else "metal","y",math.degrees(theta))
        return
    if kind in ("earthquake_mage","calamity_train"):
        points=[(3,2.8),(5.7,5.2),(5.1,7.5),(8.5,8.6),(11.2,10.3),(11,13.7)]
        for i,(a,b) in enumerate(zip(points,points[1:])):
            beam(a,b,i*.013,1.25,"dark")
            beam(a,b,.29+i*.017,.45,"light")
        for i,(x,z) in enumerate([(4,5.5),(7.3,6),(8.1,10.6),(10.8,8.5),(12.5,12.4)]):
            part(x,8.2+i*.21,z,1.2+i*.16,1.1+i*.39,1.55,"metal","z",(-1)**i*22.5)
        if kind=="earthquake_mage":
            for i,(first,last) in enumerate([((5.7,5.2),(2.4,7.7)),((8.5,8.6),(12.6,6.2)),((11.2,10.3),(14,12.7))]):
                beam(first,last,.02+i*.022,.77,"dark")
                beam(first,last,.31+i*.021,.23,"accent")
        if kind=="earthquake_mage":
            for x,y,z,angle in ((5.25,8.36,7.42,45),(7.18,9.18,8.75,-22.5),(10.25,9.29,10.38,45)):
                part(x,y,z,.87,2.66,1.19,"accent","z",angle)
        if kind=="calamity_train":
            part(8,9.45,8,4,2,6,"metal","z",0)
            part(8,10.85,9.15,3.65,1.7,2.2,"dark","z",0)
            part(8,11.24,6.35,.85,1.64,1,"accent","z",0)
            part(8,9.13,4.7,3.9,.82,1.23,"light","x",22.5)
            for x in (5.55,10.45):
                for z in (6.09,10.12):
                    part(x,8.51,z,1.32,1.94,1.72,"dark","x",45)
                    part(x+(.63 if x>8 else -.63),8.52,z,.34,1.31,1.38,"accent","x",45)
            part(8,8.07,3.78,3.8,.67,.81,"metal","x",22.5)
        return
    if kind in ("time_rewind","duel_contract","void_lord","life_echo","impact","fracture"):
        rim(5.25,7.35,"metal",.88)
        rim(4.52,7.56,"light",.39)
        if kind=="time_rewind":
            for i in range(4):
                a=i*math.pi/2
                part(8+4.88*math.sin(a),7.94,8+4.88*math.cos(a),.66,.33,1.18,"accent","y",i*90)
            part(8,7.95,6.39,.38,.33,4.5,"light","y",0)
            part(9.03,8.24,8,2.24,.33,.53,"accent","y",22.5)
            part(8,7.96,4.10,.65,.21,.83,"light","y",45)
            crystal(8,8.42,8,.7,"y")
        elif kind=="duel_contract":
            for x in (5.8,10.2):
                rim_x=x
                part(rim_x,8.03,8,2.6,.42,2.6,"metal","y",45)
                part(rim_x,8.30,8,1.37,.34,1.37,"light","y",45)
            part(8,8.10,8,4.45,.29,.48,"accent","x",0)
            part(5.8,8.59,8,.50,.64,1.04,"accent","z",0)
            part(10.2,8.60,8,1.07,.49,.5,"metal","z",45)
        elif kind in ("void_lord","life_echo"):
            rim(3.46,8.55,"accent",.49)
            for i in range(2 if kind == "void_lord" else 0):
                a=i*math.pi
                part(8+4.5*math.sin(a),8.64,8+4.5*math.cos(a),.54,1.58,.76,"metal","z",22.5)
            part(8,9.37,8,2.78,3.02,2.72,"dark","y",45)
            crystal(8,10.71,8,1.5,"z",45,"light")
            if kind == "void_lord":
                part(8,12.07,8.02,.64,1.17,.55,"metal","z",-22.5)
                part(7.38,10.38,6.7,.47,1.89,.51,"light","z",-22.5)
            if kind == "life_echo":
                for x,angle in ((6.81,-22.5),(9.19,22.5)):
                    part(x,10.13,6.31,2.23,2.41,.59,"light","z",angle)
                part(8,9.05,6.27,2.65,2.65,.67,"accent","z",45)
                part(8,9.63,5.86,.6,1.80,.18,"light","z",-45)
        elif kind=="impact":
            rim(3.73,8.06,"accent",.38)
            for x,z in [(8,5.3),(5.2,8.6),(10.9,8.1),(8.8,10.7)]:
                part(x,8.35,z,1.25,2.35,1.28,"accent","z",22.5)
            crystal(8,9.45,8,2.5,"y")
        else:
            part(8,8.7,6.4,4.18,4.4,.59,"metal","z",0)
            part(8,9.07,6.04,3.57,3.48,.32,"light","z",0)
            part(8,9.08,5.78,1.72,1.72,.19,"accent","z",45)
        return
    if kind=="bomb_alchemy":
        part(8,8,8,5.5,4.4,5.55,"dark","y",45)
        part(8,5.65,8,4.5,1,4.42,"metal","y",45)
        part(8,10.32,8,4.56,1.12,4.58,"metal","y",45)
        rim(4.1,7.72,"metal",.58)
        part(8,11.29,8,1.9,1.22,1.9,"accent","y",45)
        part(8.48,12.26,8,1.7,.63,.55,"light","z",45)
        for turn in range(4):
            a=turn*math.pi/2
            x,z=8+4.04*math.sin(a),8+4.04*math.cos(a)
            part(x,8.14,z,1.65,1.72,.31,"accent","y",turn*90)
        part(8.93,12.63,8,.62,.58,.59,"accent","z",45)
        return
    if kind in ("calamity_giant","steel_king"):
        across = 1.28 if kind == "steel_king" else 1
        for i in range(4):
            x = 8+(i-1.5)*1.78*across
            part(x,9.4+.3*(i%2),7.4,1.55,3.8,2.65,"metal","z",(i-1.5)*8)
            part(x+.08,10.8+.34*(i%2),6.12,1.2,.6,.37,"light","z",(i-1.5)*8)
        part(8,7.42,8.35,6.7,2.18,3.14,"dark","z",0)
        part(5.2,8.02,7.92,1.83,2.72,2.82,"accent","z",-45)
        part(7.9,6.20,9.12,4.02,1.41,2.83,"metal","x",22.5)
        if kind == "calamity_giant":
            part(8,5.38,9.14,4.12,2.13,3.27,"dark","x",-22.5)
            part(8,5.02,9.16,5.04,.69,3.79,"metal","x",-22.5)
            part(8.04,4.83,7.35,.97,.77,.77,"light","z",45)
            part(8,9.02,9.33,6.82,2.08,1.75,"dark","x",22.5)
            for i,x in enumerate((5.27,7.06,8.85,10.64)):
                part(x,9.24+.13*i,6.04,1.17,1.93,.47,"accent","x",22.5)
        if kind == "steel_king":
            for i,x in enumerate((5.1,8,10.9)):
                part(x,12.55+.48*(i%2),7.52,1.21,1.55,.65,"light","z",45)
            part(8,11.96,7.47,7.5,.48,.83,"accent")
            for x,angle in ((4.48,-22.5),(11.52,22.5)):
                part(x,6.96,7.1,2.16,2.08,1.3,"light","z",angle)
        for x in (4.5,7.2,10.1,12.75):
            part(x,4.42,8.7,2.49,.54,.63,"accent","y",(-1 if x<8 else 1)*22.5)
        crystal(8,7.8,6.5,1.06,"z")
        return
    # Separate the mechanical body from its emitted energy, using one display per form.
    if kind == "steel_heart":
        for x,angle in ((5.9,-22.5),(10.1,22.5)):
            part(x,9,8,3.65,3.95,3.3,"metal","z",angle)
            part(x,9.3,6.21,2.42,2.87,.32,"light","z",angle)
        part(8,7.27,8,4.35,4.35,3.18,"dark","z",45)
        part(8,7.27,6.15,2.88,2.88,.39,"accent","z",45)
        for x,angle in ((4.92,22.5),(11.08,-22.5)):
            part(x,7.01,7.12,.72,3.44,1.15,"light","z",angle)
            part(x,9.68,8.57,1.14,2.56,2.25,"dark","z",angle)
        return
    if kind in ("magic_missile","infinite_barrage"):
        lanes = (8,) if kind == "magic_missile" else (4.35,8,11.65)
        for i,x in enumerate(lanes):
            y=8.25+(.43 if x==8 else 0)
            part(x,y,8,1.3,1.3,7.2,"metal","z",45)
            part(x,y,4.6,1.43,1.43,1.52,"light","z",45)
            for j in (-1,1):
                part(x+j*.91,y,10.46,.46,1.7,2.15,"accent","z",j*22.5)
        if kind == "magic_missile":
            rim(3.5,7.08,"accent",.43)
            for x in (7.38,8.62):
                part(x,8.79,7.01,.20,.78,2.97,"light","x",0)
        else:
            part(8,7.2,9.76,9.12,.45,1.22,"dark")
            for x in lanes:
                part(x,7.29,8.69,.63,.56,1.08,"accent","x",22.5)
        return
    if kind == "berserk_engine":
        part(8,8.04,8,6.9,6.45,2.97,"dark","z",45)
        part(8,8,6.40,4.92,4.92,.69,"metal","z",45)
        for i in range(8):
            a=i*math.pi/4
            part(8+4.13*math.cos(a),8+4.13*math.sin(a),7.32,1.45,1.63,1.24,"accent","z",i*45)
        part(8,8,5.82,2.15,2.15,.51,"light","z",45)
        for turn in range(4):
            a=turn*math.pi/2
            part(8+2.3*math.sin(a),8+2.3*math.cos(a),6.04,.49,1.64,.48,"light","z",turn*90)
        return
    if kind in ("hell_lord","infernal_conductor"):
        if kind == "hell_lord":
            part(8,6.45,8,8.2,1.17,3.51,"dark")
            for x,y,angle in ((4.9,8.45,-22.5),(8,9.36,0),(11.1,8.52,22.5)):
                part(x,y,8,1.75,4.55,1.57,"metal","z",angle)
                part(x,y+.39,6.88,.83,3,.43,"light","z",angle)
            rim(4.41,5.51,"accent",.68)
        else:
            for x,z in ((5.7,6.1),(10.3,6.1),(8,10.6)):
                part(x,8.38,z,1.49,6.31,1.52,"metal","z",22.5 if x<8 else -22.5)
                crystal(x,11.73,z,1.36,"y")
            rim(4.48,5.7,"dark",.6)
            crystal(8,8.65,8,1.8,"y")
            for tip in ((5.7,6.1),(10.3,6.1),(8,10.6)):
                beam((8,8),tip,.48,.31,"light")
        return
    if kind == "tank_engine":
        part(8,6.48,8,6.59,2.27,8.58,"dark")
        part(8,8.25,8.15,4.37,2.14,4.27,"metal","y",45)
        part(8,8.41,4.85,1.08,1.17,4.65,"light")
        part(8,8.56,3.39,1.39,1.49,.57,"accent")
        part(8.01,7.08,3.49,5.97,.46,.71,"light")
        for x in (4.21,11.79):
            part(x,6.64,8,1.61,1.84,9.06,"metal")
            for i in range(4):
                part(x,5.58+i*.04,4.74+i*2.11,1.79,.41,.83,"accent")
        return
    if kind == "sword_wind":
        part(8,9.21,8,1.02,7.76,.43,"light","z",-22.5)
        part(6.41,5.71,8,3.22,.47,.81,"metal","z",-22.5)
        part(6.16,4.58,8,.68,2.27,.65,"dark","z",-22.5)
        for i in range(7):
            a=math.radians(-70+i*25)
            part(8+4.25*math.sin(a),7.24+.18*i,8+4.4*math.cos(a),2.43,.33,.69,"accent","y",math.degrees(a))
            if i in (1,2,3,4,5):
                part(8+4.94*math.sin(a),7.37+.18*i,8+5.1*math.cos(a),2.03,.17,.25,"light","y",math.degrees(a))
        return
    if kind == "thousand_swords":
        blades(5,2.35,kind)
        for x in (4.63,8.23,11.83):
            part(x,5.34,10.13,2.15,.46,.42,"accent","y",-22.5)
        return
    if kind == "stacking":
        for i,side in enumerate((9.5,7.5,5.5,3.5)):
            part(8,4.8+i*1.49,8,side,.78,side,"metal","y",22.5 if i%2 else 0)
        for i,side in enumerate((9.5,7.5,5.5,3.5)):
            part(8,5.29+i*1.49,8,side*.64,.23,side*.64,"light","y",22.5 if i%2 else 0)
        crystal(8,11.21,8,2,"z")
        return
    raise ValueError(kind)
