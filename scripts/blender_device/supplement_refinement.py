"""Refine the saved device non-destructively in a background Blender process."""
import ast
import bpy
import math
import random
import sys
from pathlib import Path
from mathutils import Vector

HERE = Path(__file__).resolve().parent
OUT = HERE.parents[1] / "artifacts" / "blender-device"
scene = bpy.context.scene
root = scene.objects["DEVICE_ROOT"]
if scene.get("supplement_refinement"):
    raise RuntimeError("Start from revisions/Before_Supplement_Photos.blend, not the refined file")
random.seed(91226)
FRAME, MECH, CAM, PLANTS, SERVICE, STUDIO, REFS = [
    bpy.data.collections[n] for n in [
        "01 | Aluminium frame and fasteners", "02 | Linear motion and cable chains",
        "03 | Camera and nozzle", "04 | Plant trays and irrigation",
        "05 | Cabinet reservoir and instruments", "06 | Lighting and inspection views",
        "07 | Photo references"]]
current = CAM
BASE, TOP = .19, 2.22
for key, name in {
    "AL": "Satin extruded aluminium", "STEEL": "Zinc plated fasteners",
    "BLACK": "Black anodised metal", "RUBBER": "Cable chain PA and rubber",
    "WHITE": "Off-white powder coating", "GRAY": "Weatherproof control cabinet",
    "ORANGE": "Orange pneumatic hose", "POT": "Charcoal square nursery pots",
    "DIRT": "Potting compost", "WOOD": "Bamboo plant supports",
    "STEM": "Woody plant stems", "PETAL": "Ivory flower petals",
    "POLLEN": "Yellow-green flower centres", "GLASS": "Lens optical glass",
    "TUBE": "Translucent irrigation tube", "LED": "Status LED"
}.items():
    globals()[key] = bpy.data.materials[name]

# Reuse only pure geometry definitions. Executing the generator's top level would erase the scene.
source = ast.parse((HERE / "build_device.py").read_text(encoding="utf-8"))
helpers = {"mat", "mesh", "empty", "bevel", "box", "cylinder", "rod", "tube", "bolt", "label"}
module = ast.Module(body=[n for n in source.body if isinstance(n, ast.FunctionDef) and n.name in helpers], type_ignores=[])
exec(compile(module, str(HERE / "build_device.py"), "exec"), globals())


def remove_objects(objects):
    for obj in list(objects):
        bpy.data.objects.remove(obj, do_unlink=True)


def remove_prefixes(prefixes, objects=None):
    remove_objects(o for o in list(objects or scene.objects) if o.name.startswith(tuple(prefixes)))


def lathe(name, profile, material, parent, pos=(0, 0, 0), sides=80):
    verts = [(r * math.cos(i * math.tau / sides), r * math.sin(i * math.tau / sides), z)
             for r, z in profile for i in range(sides)]
    faces = [(j*sides+i, j*sides+(i+1)%sides, (j+1)*sides+(i+1)%sides, (j+1)*sides+i)
             for j in range(len(profile)-1) for i in range(sides)]
    obj = mesh(name, verts, faces, material, parent)
    obj.location = pos
    for p in obj.data.polygons:
        p.use_smooth = True
    return obj


def sphere(name, pos, scale, material, parent=root):
    obj = lathe(name, [(math.sin(j*math.pi/48), math.cos(j*math.pi/48)) for j in range(49)], material, parent, pos, 72)
    obj.scale = scale
    return obj


def plate(name, outline, thickness, material, parent):
    n = len(outline)
    verts = [(x, y, z) for z in [-thickness/2, thickness/2] for x, y in outline]
    faces = [tuple(reversed(range(n))), tuple(range(n, n*2))]
    faces += [(i, (i+1)%n, (i+1)%n+n, i+n) for i in range(n)]
    return bevel(mesh(name, verts, faces, material, parent), .0012, 3)


def slot_cut(obj, center, width, height, thickness, axis="X"):
    r = height/2
    half = (width-height)/2
    outline = [(half+r*math.cos(a), r*math.sin(a)) for a in [(-.5+i/16)*math.pi for i in range(17)]]
    outline += [(-half+r*math.cos(a), r*math.sin(a)) for a in [(.5+i/16)*math.pi for i in range(17)]]
    vertices = []
    for depth in [-thickness/2, thickness/2]:
        for u, v in outline:
            xyz = (depth, u, v) if axis == "X" else (u, depth, v)
            vertices.append(tuple(Vector(center)+Vector(xyz)))
    n = len(outline)
    faces = [tuple(reversed(range(n))), tuple(range(n, n*2))]
    faces += [(i, (i+1)%n, (i+1)%n+n, i+n) for i in range(n)]
    cutter = mesh("TEMP capsule cutter", vertices, faces, None, obj.parent)
    bpy.context.view_layer.update()
    mod = obj.modifiers.new("True capsule through hole", "BOOLEAN")
    mod.operation = "DIFFERENCE"
    mod.solver = "EXACT"
    mod.object = cutter
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.modifier_apply(modifier=mod.name)
    bpy.data.objects.remove(cutter, do_unlink=True)
    obj["through_slots"] = obj.get("through_slots", 0)+1


def noisy_material(material, colors, scale, bump_strength=.15):
    nodes, links = material.node_tree.nodes, material.node_tree.links
    noise = nodes.new("ShaderNodeTexNoise")
    noise.inputs["Scale"].default_value = scale
    noise.inputs["Detail"].default_value = 5
    ramp = nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].position = .25
    ramp.color_ramp.elements[0].color = (*colors[0], 1)
    ramp.color_ramp.elements[1].position = .78
    ramp.color_ramp.elements[1].color = (*colors[1], 1)
    links.new(noise.outputs["Fac"], ramp.inputs["Fac"])
    shader = nodes.get("Principled BSDF")
    links.new(ramp.outputs["Color"], shader.inputs["Base Color"])
    bump = nodes.new("ShaderNodeBump")
    bump.inputs["Strength"].default_value = bump_strength
    bump.inputs["Distance"].default_value = .001
    links.new(noise.outputs["Fac"], bump.inputs["Height"])
    links.new(bump.outputs["Normal"], shader.inputs["Normal"])
    return material


BLUE = mat("Non-emissive blue pneumatic collet", (.008, .035, .46), .05, .3)
BRASS = mat("Machined brass nozzle", (.45, .29, .065), .78, .24)
GREEN = mat("Green nylon cable ties", (.014, .19, .055), 0, .45)
GLOSS = mat("Glossy black instrument plastic", (.006, .008, .009), .05, .22)
BEZEL = mat("Smoked camera optical window", (.004, .006, .007), .18, .105)
SOILGRAIN = mat("Mixed dark compost grains", (.045, .032, .014), 0, .95)
noisy_material(SOILGRAIN, [(.013, .008, .003), (.15, .11, .055)], 20)
noisy_material(POT, [(.008, .011, .010), (.054, .059, .045)], 11, .28)
noisy_material(STEM, [(.031, .023, .009), (.16, .13, .048)], 16, .4)
GLASS.node_tree.nodes.get("Principled BSDF").inputs["Base Color"].default_value = (.003, .013, .018, 1)
ORANGE.node_tree.nodes.get("Principled BSDF").inputs["Base Color"].default_value = (1, .061, .001, 1)

# Short folded bracket and a continuous globe, with optics conforming to its surface.
carriage, pan, tilt, sensor = [scene.objects[n] for n in ["CARRIAGE_Y", "CAMERA_PAN", "CAMERA_TILT", "INSPECTION_CAMERA"]]
remove_objects(o for o in CAM.objects if o not in [pan, tilt, sensor])
plate_obj = box("Camera bracket vertical back", (.151, 0, 2.395), (.0035, .130, .235), WHITE, carriage, 0)
for z in [2.319, 2.366, 2.413, 2.460]:
    slot_cut(plate_obj, (.151, 0, z), .045, .010, .035)
bevel(plate_obj, .0011, 3)
box("Camera bracket lower ledge", (.106, 0, 2.279), (.094, .130, .0035), WHITE, carriage, .001)
box("White camera mounting plate", (.039, 0, 2.512), (.225, .135, .0035), WHITE, carriage, .001)
for x in [-.029, .049]:
    for y in [-.045, .045]:
        bolt((x, y, 2.515), carriage, size=.003)
pan.location = (.076, 0, 2.273)
tilt.location = (0, 0, -.089)
lathe("Camera mounting collar", [(0, 0), (.042, 0), (.044, -.005), (.043, -.016), (0, -.016)], WHITE, pan)
lathe("White camera upper housing", [(.081*math.sin(a), -.089+.081*math.cos(a))
      for a in [i*1.075/32 for i in range(33)]], WHITE, pan)
sphere("White gimbal ball", (0, 0, 0), (.0798, .0798, .0798), WHITE, tilt)
lathe("Camera upper casing seam", [(.0710, -.0503), (.0716, -.050), (.0718, -.0493)], RUBBER, pan)
# A spherical cap is only 0.4 mm above the body, never a projecting lens cylinder.
face = lathe("Black camera lens face", [(.0802*math.sin(a), .0802*math.cos(a))
             for a in [i*.73/32 for i in range(33)]], BEZEL, tilt)
face.rotation_euler.x = math.pi/2
face.scale.y = 1.02
cylinder("Camera lens retaining ring", (0, -.0805, .008), .019, .0018, GLOSS, tilt, (0,-1,0), 64)
lathe("Concentric optical barrel", [(.0175, -.001), (.0175, .001), (.014, .001), (.013, -.002)], GLOSS, tilt, (0,-.082,.008)).rotation_euler.x = math.pi/2
cylinder("Blue coated optical lens", (0, -.082, .008), .0118, .0006, GLASS, tilt, (0,-1,0), 64)
for x in [-.025, .025]:
    cylinder("Infrared illuminator", (x, -.0754, -.012), .0055, .001, GLASS, tilt, (0,-1,0), 32)
for x in [-.012, -.004, .004, .012]:
    box("Recessed illuminator strip", (x, -.0745, -.029), (.005, .001, .0038), WHITE, tilt, .0004)
sensor.location = (0, -.084, .008)
sensor.data.display_size = .032
lathe("Camera rear manufacturing seam", [(.0798, 0), (.0800, .0004), (.0798, .0008)], WHITE, tilt).rotation_euler.y = math.pi/2
label("Camera body marking", "IP", (0, -.064, -.036), .007, GRAY, pan)
support = box("Nozzle slotted steel strap", (.226, .04, 2.18), (.004, .019, .31), WHITE, carriage, 0)
for z in [2.085, 2.14, 2.20, 2.255]:
    slot_cut(support, (.226, .04, z), .011, .006, .025)
bevel(support, .0007, 3)
box("Nozzle folded support arm", (.182, .04, 2.333), (.09, .021, .004), WHITE, carriage)
tube("Orange carriage service loop", [(-.103,0,2.612),(.044,.043,2.593),(.218,.049,2.563),(.242,.04,2.36),(.245,.04,2.16),(.230,.04,2.00)], .006, ORANGE, carriage)
for z in [2.20, 2.32]:
    tube("Green hose restraint", [(.219,.026,z),(.249,.026,z),(.254,.04,z),(.249,.053,z),(.219,.053,z),(.219,.026,z)], .0014, GREEN, carriage)
    box("Green tie locking head", (.251,.04,z), (.006,.007,.004), GREEN, carriage)
lathe("Nozzle push-fit coupler", [(0,0),(.012,0),(.013,-.004),(.012,-.024),(.009,-.029),(.008,-.035),(.010,-.039),(.010,-.054),(0,-.057)], GLOSS, carriage, (.230,.04,2.00))
for z,r in [(2.003,.0129),(1.943,.0085)]:
    lathe("Blue push fit collet", [(r*.64,-.002),(r,-.002),(r,.002),(r*.64,.002)], BLUE, carriage, (.230,.04,z))
for z in [1.957,1.962,1.967]:
    lathe("Nozzle grip rib", [(.010,-.001),(.0108,0),(.010,.001)], RUBBER, carriage, (.230,.04,z), 48)
cylinder("Nozzle hex brass body", (.230,.04,1.935), .0062, .012, BRASS, carriage, sides=6)
cylinder("Brass spray tip", (.230,.04,1.925), .0047, .008, BRASS, carriage, sides=48)
cylinder("Spray tip orifice", (.230,.04,1.9208), .0009, .0004, BLACK, carriage, sides=24)
tube("Camera signal lead", [(.080,.022,2.255),(.135,.058,2.265),(.159,.077,2.397),(.076,.079,2.495),(-.076,.03,2.604)], .0026, RUBBER, carriage)
print("REFINED camera and nozzle", flush=True)

# Integrated weather station: V arms, rain funnel, hollow cups and moulded vane.
current = SERVICE
weather = scene.objects["WEATHER_SENSOR"]
remove_objects(weather.children_recursive)
weather.location = (1.25, .69, 1.975)
weather.rotation_euler.z = -math.pi/2
station = empty("Integrated weather station body", (0,-.125,.025), weather)
outline = [(-.285,.045),(-.285,-.045),(0,-.11),(.285,-.045),(.285,.045),(0,-.025)]
plate("Weather black lower V shell", outline, .022, GLOSS, station)
top = plate("Weather white V arms", outline, .024, WHITE, station)
top.location.z = .013
for x in [-.276,.276]:
    lathe("Wind mast moulded base", [(0,-.004),(.040,-.004),(.042,.008),(.039,.032),(.027,.042),(.014,.16),(.013,.173),(0,.173)], GLOSS, station, (x,0,.007))
lathe("Rain gauge cylinder", [(0,0),(.066,0),(.067,.008),(.067,.139),(.062,.14),(.062,.012),(0,.012)], WHITE, station, (0,-.041,.025))
lathe("Rain gauge black funnel rim", [(.066,0),(.073,.003),(.074,.018),(.070,.021),(.066,.016),(.060,-.005),(.013,-.075),(.009,-.080)], GLOSS, station, (0,-.041,.164))
lathe("Short calibration post", [(0,0),(.014,0),(.014,.063),(.011,.067),(.010,.004)], WHITE, station, (0,-.105,.035), 48)
for x in [-.276,.276]:
    cylinder("Wind rotor pivot", (x,0,.19), .020, .028, GLOSS, station, sides=48)
for i in range(3):
    a=i*math.tau/3+.30
    end=Vector((-.276+math.cos(a)*.077, math.sin(a)*.077, .200))
    rod("Anemometer stainless radial arm", (-.276,0,.2), end, .0027, STEEL, station)
    profile=[(.032*math.sin(t), -.029*math.cos(t)) for t in [i*math.pi/2/24 for i in range(25)]]
    profile += [(.0305,0)] + [(.0305*math.sin(t), -.0275*math.cos(t)) for t in [(24-i)*math.pi/2/24 for i in range(25)]]
    cup=lathe("Hollow anemometer cup", profile, GLOSS, station, end, 64)
    cup.rotation_mode="QUATERNION"
    cup.rotation_quaternion=Vector((0,0,1)).rotation_difference(Vector((-math.sin(a),math.cos(a),0)))
    cup["hollow_shell"]=True
rod("Wind vane beam", (.127,0,.204),(.424,0,.204), .0033, GLOSS, station)
fin=plate("Broad wind vane tail", [(-.14,-.063),(-.046,-.025),(-.022,0),(-.046,.025),(-.14,.074),(-.152,.07),(-.132,0),(-.15,-.056)], .003, GLOSS, station)
fin.rotation_euler.x=math.pi/2
fin.location=(.276,0,.205)
arrow=plate("Wind vane arrow", [(0,-.011),(.031,0),(0,.011),(.003,0)], .004, GLOSS, station)
arrow.location=(.410,0,.204)
for x in [-.024,.024]:
    bolt((x,-.07,-.014), station, (0,0,-1), .0025)
bracket=mesh("Weather folded mounting bracket", [(-.046,0,-.20),(.046,0,-.20),(-.046,0,-.14),(.046,0,-.14),(-.046,-.075,-.065),(.046,-.075,-.065),(-.046,-.075,.018),(.046,-.075,.018)], [(0,1,3,2),(2,3,5,4),(4,5,7,6)], WHITE, weather)
solid=bracket.modifiers.new("Folded sheet thickness", "SOLIDIFY")
solid.thickness=.004
bpy.context.view_layer.objects.active=bracket
bpy.ops.object.modifier_apply(modifier=solid.name)
for x in [-.026,.026]:
    slot_cut(bracket, (x,0,-.17), .011, .009, .035, "Y")
    bolt((x,-.004,-.168), weather, (0,-1,0), .004)
bevel(bracket,.001,3)
cylinder("Weather shield core", (0,.025,-.075), .024, .17, GRAY, weather)
for z in [-.020,-.042,-.064,-.086,-.108,-.130]:
    lathe("Weather radiation shield louvre", [(0,.004),(.025,.004),(.046,-.003),(.048,-.009),(.037,-.010),(.022,-.001)], WHITE, weather, (0,.025,z), 64)
tube("Weather sensor signal cable", [(0,.025,-.15),(.014,.05,-.22),(.015,.048,-.05),(.020,.04,.19)], .003, RUBBER, weather)
print("REFINED integrated weather station", flush=True)

# Pots remain on their established grid. Supplementary photos show five populated pots.
current = PLANTS
LEAVES=[]
for i,(a,b) in enumerate([
    ((.008,.013,.002),(.067,.086,.015)), ((.014,.019,.003),(.10,.115,.027)),
    ((.013,.019,.003),(.039,.066,.008)), ((.023,.020,.005),(.11,.092,.025)),
    ((.003,.012,.001),(.025,.080,.004))]):
    LEAVES.append(noisy_material(mat("Crumpled foliage %02d"%i,a,0,.82),[a,b],9,.35))
PETAL.node_tree.nodes.get("Principled BSDF").inputs["Subsurface Weight"].default_value=.045


def foliage(parent, leaves):
    verts, faces, indices=[],[],[]
    veins=[]
    for k,(origin,heading,length,width,droop) in enumerate(leaves):
        origin=Vector(origin)
        along=Vector((math.cos(heading),math.sin(heading),0))
        side=Vector((-math.sin(heading),math.cos(heading),0))
        phase=random.random()*math.tau
        off=len(verts)
        centerline=[]
        for j in range(19):
            t=j/18
            middle=origin+along*(length*(.78*t-.32*t*t))+side*(.018*math.sin(t*5+phase)*t)
            middle.z+=length*(.15*t-droop*t*t)
            centerline.append(tuple(middle))
            w=math.sin(math.pi*t)**.70*width*(.81 if j%2 else 1.05)
            for q in range(7):
                s=(q-3)/3
                p=middle+side*(s*w*(1-.34*t))
                p.z+=w*(.72*abs(s)+.27*math.sin(t*25+s*3+phase)*abs(s))
                p+=along*(w*.25*math.sin(s*3+t*16))
                verts.append(tuple(p))
        for j in range(18):
            for q in range(6):
                if q in (0,5) and j in (8,12) and (k+j)%9==0:
                    continue
                n=off+j*7+q
                faces.append((n,n+1,n+8,n+7))
                indices.append(k%len(LEAVES))
        veins.append([centerline[i] for i in [0,3,6,9,12,15,18]])
    obj=mesh("Irregular curled and torn foliage",verts,faces,None,parent)
    for m in LEAVES:
        obj.data.materials.append(m)
    for p,index in zip(obj.data.polygons,indices):
        p.material_index=index
        p.use_smooth=True
    solid=obj.modifiers.new("Leaf membrane thickness","SOLIDIFY")
    solid.thickness=.0003
    for i,line in enumerate(veins):
        if i%2==0:
            tube("Drooping leaf midrib",line,.00042,STEM,parent)


def flowers(parent, centers):
    verts,faces=[],[]
    for center,radius in centers:
        center=Vector(center)
        normal=Vector((random.uniform(-1,1),random.uniform(-1,1),random.uniform(-.1,1))).normalized()
        rotation=Vector((0,0,1)).rotation_difference(normal)
        for layer,count in [(0,7),(1,6),(2,5)]:
            for k in range(count):
                a=k*math.tau/count+layer*.43+random.uniform(-.14,.14)
                r=radius*(1-layer*.20)*random.uniform(.9,1.1)
                off=len(verts)
                for j in range(8):
                    t=j/7
                    for q in range(7):
                        s=(q-3)/3
                        u=t*r
                        v=s*r*.60*(math.sin(t*math.pi*.90)**.65)
                        z=r*(.18*t+.26*t*t+.15*s*s+.07*math.sin(t*10+s*4))+layer*radius*.06
                        point=Vector((math.cos(a)*u-math.sin(a)*v,math.sin(a)*u+math.cos(a)*v,z))
                        verts.append(tuple(center+rotation@point))
                for j in range(7):
                    for q in range(6):
                        n=off+j*7+q
                        faces.append((n,n+1,n+8,n+7))
        heart=sphere("Flower green yellow heart",center,(radius*.18,radius*.18,radius*.10),POLLEN,parent)
        heart.rotation_mode="QUATERNION"
        heart.rotation_quaternion=rotation
        for k in range(7):
            a=k*math.tau/7
            p=center+rotation@Vector((math.cos(a)*radius*.13,math.sin(a)*radius*.13,radius*.18))
            sphere("Flower stamen",p,(.001,.001,.0017),POLLEN,parent)
    obj=mesh("Layered irregular white blossoms",verts,faces,PETAL,parent)
    for p in obj.data.polygons:
        p.use_smooth=True


populated={7:1.30,8:1.67,10:1.25,11:1.53,12:1.76}
for group in sorted([o for o in PLANTS.objects if o.type=="EMPTY" and o.name.startswith("P-")],key=lambda o:o.name):
    idx=int(group.name[-2:])
    remove_prefixes(["Main woody stem","Bamboo support","Stem support tie","Drooping branch","Folded hanging foliage","Flower yellow centre","White blossom","Small ripening fruit"],group.children_recursive)
    # The original flower surface has a separate batch name.
    remove_objects(o for o in list(group.children) if o.type=="MESH" and any(m==PETAL for m in o.data.materials))
    pot=next(o for o in group.children if "tapered open pot" in o.name)
    for v in pot.data.vertices:
        if abs(v.co.z)<.001:
            v.co.x*=.146/.122
            v.co.y*=.146/.122
    pot.name=group.name+" straight-sided weathered pot"
    for o in group.children:
        if o.name.startswith("Pot rivet"):
            o.location.y=-.1485
        if o.name.startswith("Soil aggregate"):
            o.data.materials.clear()
            o.data.materials.append(SOILGRAIN)
    for x in [-.15,.15]:
        box("Worn nursery pot upper rim",(x,0,.252),(.004,.302,.004),SOILGRAIN,group,.0007)
    for y in [-.15,.15]:
        box("Worn nursery pot upper rim",(0,y,.252),(.302,.004,.004),SOILGRAIN,group,.0007)
    height=populated.get(idx,{1:.35,2:.96,3:.27,4:.39,5:.41,6:1.12,9:.34}.get(idx,.4))
    lean=random.uniform(-.045,.045)
    rod("Bamboo support",(-.038,.03,.232),(-.026,.014,height+.27),.004,WOOD,group)
    tube("Main woody stem",[(0,0,.232),(.012,-.01,.57),(lean,.013,height+.23)],.0038,STEM,group)
    for z in [.58,1.03,1.42]:
        if z<height+.23:
            tube("Green stem support tie",[(-.04,.033,z),(-.042,-.015,z),(.025,-.018,z),(.025,.029,z),(-.04,.033,z)],.001,GREEN,group)
    leaves=[]
    centers=[]
    if idx in populated:
        for j in range(21):
            z=.52+(height-.32)*j/21
            a=j*2.399+idx*.85
            length=random.uniform(.085,.22)
            end=Vector((math.cos(a)*length,math.sin(a)*length,z+random.uniform(-.08,.06)))
            tube("Irregular drooping branch",[(lean*j/24,.008,z),end],.0017,STEM,group)
            for k in range(random.randint(3,6)):
                f=(k+.4)/6
                origin=(end.x*f,end.y*f,z+(end.z-z)*f)
                leaves.append((origin,a+(-.7 if k%2 else .7),random.uniform(.10,.235),random.uniform(.025,.054),random.uniform(.95,1.65)))
            if idx in [7,10,11] and j>9 and j%2==0:
                for k in range(random.randint(2,4)):
                    c=end+Vector((random.uniform(-.035,.035),random.uniform(-.035,.035),k*.028))
                    centers.append((c,random.uniform(.021,.032)))
                    tube("Blossom pedicel",[end,c],.0008,STEM,group)
            if idx in [8,12] and j in [8,10,13]:
                for k in range(2):
                    c=end+Vector((k*.028,0,-.035-k*.005))
                    tube("Fruit stalk",[end,c],.001,STEM,group)
                    sphere("Small ripening fruit",c,(.021,.022,.025),bpy.data.materials["Fruit red" if k%2 else "Fruit ochre"],group)
        foliage(group,leaves)
        if centers:
            flowers(group,centers)
    else:
        for j in range(2):
            foliage(group,[((random.uniform(-.06,.06),random.uniform(-.06,.06),.247),random.random()*math.tau,.07,.024,.12)])
    print("REFINED pot",idx,flush=True)

# Sparse compost debris on the growing deck, not bright regularly spaced granules.
verts,faces=[],[]
for i in range(650):
    x,y=random.uniform(-1.12,1.12),random.uniform(-.64,.65)
    if any(abs(x-o.location.x)<.158 and abs(y-o.location.y)<.158 for o in PLANTS.objects if o.type=="EMPTY" and o.name.startswith("P-")):
        continue
    r=random.uniform(.0008,.0037)
    z=.222
    n=len(verts)
    verts += [(x-r,y-r,z),(x+r,y-r,z),(x,y+r,z),(x,y,z+r)]
    faces += [(n,n+1,n+3),(n+1,n+2,n+3),(n+2,n,n+3)]
mesh("Scattered compost on white deck",verts,faces,SOILGRAIN,root)

# Open electrical enclosure and the photographed service shelf.
current=SERVICE
cab=scene.objects["CONTROL_CABINET"]
router=scene.objects["WiFi router"]
remove_objects(o for o in list(cab.children_recursive) if o!=router)
cab.rotation_euler.z=math.pi
box("Cabinet rear wall",(0,.137,.36),(.52,.012,.72),GRAY,cab,.009)
for x in [-.255,.255]:
    box("Cabinet side wall",(x,0,.36),(.012,.28,.72),GRAY,cab,.006)
for z in [.006,.714]:
    box("Cabinet top bottom",(0,0,z),(.52,.28,.012),GRAY,cab,.005)
for x in [-.238,.238]:
    box("Cabinet vertical gasket",(x,-.141,.36),(.008,.008,.692),RUBBER,cab,.002)
for z in [.022,.698]:
    box("Cabinet horizontal gasket",(0,-.141,z),(.48,.008,.008),RUBBER,cab,.002)
door=empty("CABINET_DOOR_HINGE",(-.259,-.142,.36),cab)
door.rotation_euler.z=math.radians(-9)
box("Cabinet recessed door",(.259,-.009,0),(.518,.018,.718),GRAY,door,.012)
for z in [-.235,.235]:
    cylinder("Cabinet hinge pin",(0,0,z),.010,.074,WHITE,door,sides=32)
    for k in range(5):
        cylinder("Cabinet hinge knuckle",(0,0,z-.03+k*.014),.012,.010,GRAY,door,sides=24)
    bolt((.479,-.021,z),door,(0,-1,0),.004)
for x in [-.268,.268]:
    for i in range(16):
        box("Cabinet side cooling rib",(x,0,.20+i*.015),(.005,.24,.004),GRAY,cab,.001)
box("Cabinet internal mounting plate",(0,.109,.36),(.46,.006,.65),STEEL,cab,.001)
for z in [.23,.49]:
    box("DIN rail",(0,.08,z),(.42,.018,.02),STEEL,cab)
    for x in [-.14,-.065,.065,.14]:
        box("Concealed generic DIN module",(x,.045,z+.025),(.053,.07,.085),WHITE,cab)
router.location=(.005,.005,.752)
box("White router body",(0,0,0),(.27,.17,.027),WHITE,router,.008)
for i,(x,y) in enumerate([(-.11,.06),(.11,.06),(-.09,-.035),(.09,-.035)]):
    end=(x+(-.07 if x<0 else .06),y+.045,.19+(i%2)*.05)
    rod("Router antenna",(x,y,.008),end,.0037,WHITE,router)
    cylinder("Router antenna pivot",(x,y,.011),.007,.014,WHITE,router,(1,0,0))
for x in [-.055,-.026,0,.026]:
    box("Router indicator window",(x,-.086,0),(.003,.001,.0017),GREEN,router,.0002)
lathe("External antenna magnetic base", [(0,0),(.029,0),(.031,.007),(.024,.014),(.01,.018),(0,.018)], GLOSS,cab,(.209,.065,.729))
rod("Black external cellular antenna",(.209,.065,.742),(.209,.065,1.009),.005,GLOSS,cab)
tube("External aerial lead",[(.209,.065,.742),(.23,.15,.735),(.26,.17,.45),(.27,.1,.08)],.002,RUBBER,cab)
remove_prefixes(["Utility tray","Bottle","Bottle cap"])
tray=empty("OPEN_UTILITY_BASKET",(0,1.01,.231),root)
traymat=bpy.data.materials["Dark green service tray"]
for x in [-.175,.175]:
    box("Utility basket side",(x,0,.045),(.007,.36,.09),traymat,tray,.004)
for y in [-.176,.176]:
    box("Utility basket end",(0,y,.045),(.35,.007,.09),traymat,tray,.004)
for i in range(20):
    box("Basket perforated base X",(-.166+i*.0175,0,.009),(.005,.35,.006),traymat,tray,.001)
for i in range(21):
    box("Basket perforated base Y",(0,-.167+i*.0167,.009),(.35,.005,.006),traymat,tray,.001)
for x in [-.178,.178]:
    box("Basket upper rim",(x,0,.09),(.012,.37,.012),traymat,tray,.003)
for y in [-.182,.182]:
    box("Basket upper rim",(0,y,.09),(.36,.012,.012),traymat,tray,.003)
lathe("White service bottle",[(0,0),(.025,0),(.027,.008),(.027,.085),(.020,.098),(.017,.099),(.017,.118),(0,.118)],WHITE,tray,(-.095,.09,.013),48)
cylinder("Ribbed service bottle cap",(-.095,.09,.139),.020,.022,WHITE,tray,sides=48)
for i in range(30):
    a=i*math.tau/30
    rod("Bottle cap knurl",(-.095+.020*math.cos(a),.09+.020*math.sin(a),.130),(-.095+.020*math.cos(a),.09+.020*math.sin(a),.147),.0007,WHITE,tray,sides=6)
label("Bottle printed label","500 ml",(-.095,.062,.079),.007,GRAY,tray)
for i in range(22):
    x=.10+random.uniform(-.018,.018)
    rod("Bundle of green plant ties",(x,-.13,.025+random.random()*.015),(x,.12,.025+random.random()*.015),.001,GREEN,tray,sides=6)
tool=empty("Red pruning scissors",(-.055,-.064,.04),tray)
red=mat("Red tool grips",(.58,.015,.012),0,.45)
for x in [-.022,.022]:
    tube("Pruner red handle",[(x,0,0),(x*1.5,-.06,0),(x*1.3,-.076,0),(x*.65,-.067,0),(x*.6,-.015,0)],.005,red,tool)
    rod("Pruner steel jaw",(x,0,0),(-x*.2,.055,.004),.005,STEEL,tool)
cylinder("Pruner pivot bolt",(0,.008,.007),.008,.005,STEEL,tool)
flowers(tray,[((-.1,-.10,.032),.025),((-.12,-.067,.029),.023)])
tank=scene.objects["RESERVOIR"]
for x in [-.12,.12]:
    for y in [-.207,.207]:
        box("Reservoir moulded wall rib",(x,y,.267),(.009,.006,.46),WHITE,tank,.003)
for x in [-.286,.286]:
    box("Reservoir moulded end rib",(x,0,.267),(.007,.010,.46),WHITE,tank,.003)
print("REFINED plants and service equipment",flush=True)

reference_dir=Path("E:/WechatFile/xwechat_files/wxid_xyw8zuia0xqs22_a2b3/temp/RWTemp/2026-09/6e05eff014efceabe1f5ad76fb1795ae")
for i,name in enumerate(["f8a944d9b3f847e66bb33d0278f75da5.jpg","d11171b5b3599f49488fd5760eddf1be.jpg","446537164fc64d1a4a71ae363449297d.jpg","5d79f36c1b6333700259ab2b9a3648de.jpg","bec167b8bdd3de1b203364f64bace1d8.jpg","9f39c33dea2f957193c321342f3f03c4.jpg"],6):
    image=bpy.data.images.load(str(reference_dir/name),check_existing=True)
    image.pack()
    obj=empty("PHOTO_%02d_SUPPLEMENT"%i,(-5,0,i*2),col=REFS)
    obj.empty_display_type="IMAGE"
    obj.data=image
    obj.empty_display_size=2
    obj.hide_render=True
scene["supplement_refinement"]="2026-09-12 / first photo-detail pass"
scene["model_source"]="Eleven user photographs including six supplementary detail views"
sys.path.insert(0,str(HERE))
import device_twin_sync as sync
sync.register()
s=scene.device_twin
s.mode="MANUAL"
s.axis_x=.43
s.axis_y=.38
s.pan_deg=0
s.tilt_deg=-12
sync.apply_pose(scene)
bpy.context.view_layer.update()
bpy.ops.object.select_all(action="DESELECT")
scene.camera=scene.objects["VIEW_01 | Equipment overview"]
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/"revisions"/"Supplement_Refined_Pass1.blend"))
print("SUPPLEMENT_REFINEMENT_OK",len(scene.objects),flush=True)
