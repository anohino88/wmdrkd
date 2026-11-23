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
            part(x,8.1+i*.075,z,2.85,.44,taper,"light","y",math.degrees(theta))
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
        if kind=="calamity_train":
            part(8,9.45,8,4,2,6,"metal","z",0)
            part(8,10.85,9.15,3.65,1.7,2.2,"dark","z",0)
            part(8,11.24,6.35,.85,1.64,1,"accent","z",0)
            part(8,9.13,4.7,3.9,.82,1.23,"light","x",22.5)
        return
    if kind in ("time_rewind","duel_contract","void_lord","life_echo","impact","fracture"):
        rim(5.25,7.35,"metal",.88)
        rim(4.52,7.56,"light",.39)
        if kind=="time_rewind":
            for i in range(4):
                a=i*math.pi/2
                part(8+4.88*math.sin(a),7.94,8+4.88*math.cos(a),.66,.33,1.18,"accent","y",i*90)
            part(8,7.95,6.39,.40,.33,3.55,"light","y",0)
            part(9.18,8.18,8,2.58,.33,.47,"accent","y",22.5)
            crystal(8,8.42,8,.7,"y")
        elif kind=="duel_contract":
            for x in (5.8,10.2):
                rim_x=x
                part(rim_x,8.03,8,2.6,.42,2.6,"metal","y",45)
                part(rim_x,8.30,8,1.37,.34,1.37,"light","y",45)
            part(8,8.10,8,4.45,.29,.48,"accent","x",0)
        elif kind in ("void_lord","life_echo"):
            rim(3.46,8.55,"accent",.49)
            for i in range(4):
                a=i*math.pi/2
                part(8+4.5*math.sin(a),8.64,8+4.5*math.cos(a),.54,1.58,.76,"metal","z",22.5)
            part(8,9.37,8,2.78,3.02,2.72,"dark","y",45)
            crystal(8,10.71,8,1.5,"z",45,"light")
        elif kind=="impact":
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
        part(8,8.37,4.22,1.65,1.72,.25,"light","z",45)
        return
    if kind in ("hell_lord","infernal_conductor"):
        hoop(5,5.4,8,"dark",0,.63)
        lift = kind in ("hell_lord","infernal_conductor")
        crystal(8,9.15,8,3.0 if not lift else 2.4,"y")
        for i in range(6):
            theta = i * math.tau / 6
            x,z = 8+4.1*math.cos(theta),8+4.1*math.sin(theta)
            part(x,7.2+i*.61,z,.65,2.8 if lift else 1.9,.69,"accent","z",(-1)**i*(22.5+i*3))
        if kind == "life_echo":
            hoop(4.2,10.7,8,"accent",0,.39)
        if lift:
            part(8,12.15,8,.95,2.2,.95,"light","z",33)
    elif kind in ("bomb_alchemy","tank_engine","berserk_engine","steel_heart"):
        part(8,8,8,4.4,3.6,4.1,"dark","y",22.5)
        crystal(8,8.5,8,2.8,"y")
        for i in range(8):
            angle = i*math.pi/4
            part(8+4.3*math.sin(angle),8+.23*(i%2),8+4.3*math.cos(angle),.65,4,.61,
                 "metal","y",-i*45 if i<=4 else 360-i*45)
        hoop(4.3,6.1,8,"accent",0,.38)
        if kind == "bomb_alchemy":
            part(8.1,11.5,8.1,1.27,1.4,1.1,"metal","z",22.5)
            crystal(8.6,12.8,8.05,.9,"z",-30)
        if kind == "tank_engine":
            part(8,7.8,4.85,5,.66,1.3,"light","z",0)
    elif kind in ("calamity_giant","steel_king"):
        across = 1.28 if kind == "steel_king" else 1
        for i in range(4):
            x = 8+(i-1.5)*1.78*across
            part(x,9.4+.3*(i%2),7.4,1.55,3.8,2.65,"metal","z",(i-1.5)*8)
            part(x+.08,10.8+.34*(i%2),6.12,1.2,.6,.37,"light","z",(i-1.5)*8)
        part(8,7.42,8.35,6.7,2.18,3.14,"dark","z",0)
        part(5.2,8.02,7.92,1.83,2.72,2.82,"accent","z",-45)
        part(7.9,6.20,9.12,4.02,1.41,2.83,"metal","x",22.5)
        if kind == "steel_king":
            for i,x in enumerate((5.1,8,10.9)):
                part(x,12.55+.48*(i%2),7.52,1.21,1.55,.65,"light","z",45)
            part(8,11.96,7.47,7.5,.48,.83,"accent")
        for x in (4.5,7.2,10.1,12.75):
            part(x,4.42,8.7,2.49,.54,.63,"accent","y",(-1 if x<8 else 1)*22.5)
        crystal(8,7.8,6.5,1.06,"z")
    elif kind in ("thousand_swords","sword_wind","infinite_barrage","magic_missile"):
        blades(5 if kind == "thousand_swords" else 3,2.3,kind)
        if kind in ("infinite_barrage","magic_missile"):
            for x,y,z in [(8,12.4,10),(6,10.3,11),(10,9.2,12.3)]:
                crystal(x,y,z,1.05,"x")
    elif kind == "stacking":
        for i in range(5):
            crystal(8,4.9+i*1.59,8,2.55-.29*i,"y",(-1)**i*45)
        hoop(4.3,5.1,8,"accent",0,.38)
    else:
        raise ValueError(kind)
