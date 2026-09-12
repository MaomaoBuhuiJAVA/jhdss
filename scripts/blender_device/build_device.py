"""Photo-derived, articulated inspection gantry. Run with Blender --background."""
import bpy
import math
import random
import sys
from pathlib import Path
from mathutils import Vector

HERE = Path(__file__).resolve().parent
OUT = HERE.parents[1] / "artifacts" / "blender-device"
OUT.mkdir(parents=True, exist_ok=True)
random.seed(408)
W, D, BASE, TOP = 2.40, 1.44, 0.19, 2.22
XMIN, XMAX, YMIN, YMAX = -1.03, 1.03, -0.57, 0.57
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.name = "JHDS | Photo Reconstruction"
scene.unit_settings.system = "METRIC"
scene.unit_settings.length_unit = "METERS"


def collection(name):
    value = bpy.data.collections.new(name)
    scene.collection.children.link(value)
    return value


FRAME = collection("01 | Aluminium frame and fasteners")
MECH = collection("02 | Linear motion and cable chains")
CAM = collection("03 | Camera and nozzle")
PLANTS = collection("04 | Plant trays and irrigation")
SERVICE = collection("05 | Cabinet reservoir and instruments")
STUDIO = collection("06 | Lighting and inspection views")
REFS = collection("07 | Photo references")
current = FRAME


def mat(name, color, metal=0, rough=0.45):
    m = bpy.data.materials.new(name)
    m.diffuse_color = (*color, 1)
    m.use_nodes = True
    p = m.node_tree.nodes.get("Principled BSDF")
    p.inputs["Base Color"].default_value = (*color, 1)
    p.inputs["Metallic"].default_value = metal
    p.inputs["Roughness"].default_value = rough
    return m


AL = mat("Satin extruded aluminium", (.59, .63, .66), .83, .28)
STEEL = mat("Zinc plated fasteners", (.35, .39, .43), .88, .21)
RAIL = mat("Anodised linear module", (.43, .42, .32), .75, .31)
BLACK = mat("Black anodised metal", (.023, .027, .030), .58, .3)
RUBBER = mat("Cable chain PA and rubber", (.014, .017, .019), .08, .52)
YELLOW = mat("Motor yellow vent cover", (.98, .57, .008), .25, .3)
WHITE = mat("Off-white powder coating", (.86, .88, .87), .13, .36)
GRAY = mat("Weatherproof control cabinet", (.43, .48, .53), .18, .36)
ORANGE = mat("Orange pneumatic hose", (1.0, .105, .006), .02, .34)
POT = mat("Charcoal square nursery pots", (.014, .019, .016), .08, .82)
DIRT = mat("Potting compost", (.020, .011, .005), 0, 1)
WOOD = mat("Bamboo plant supports", (.32, .23, .095), 0, .84)
STEM = mat("Woody plant stems", (.17, .15, .048), 0, .9)
LEAVES = [mat("Foliage %02d" % i, col, 0, .85) for i, col in enumerate([
    (.018, .031, .004), (.033, .060, .008), (.068, .090, .013), (.015, .025, .004), (.11, .10, .022)])]
PETAL = mat("Ivory flower petals", (.93, .96, .88), 0, .68)
POLLEN = mat("Yellow-green flower centres", (.57, .64, .12), 0, .8)
FRUITS = [mat("Fruit red", (.48, .018, .008), 0, .32), mat("Fruit ochre", (.89, .31, .009), 0, .36)]
GLASS = mat("Lens optical glass", (.012, .055, .075), .5, .1)
TUBE = mat("Translucent irrigation tube", (.49, .57, .53), 0, .27)
TUBE.node_tree.nodes.get("Principled BSDF").inputs["Transmission Weight"].default_value = .25
LED = mat("Status LED", (.07, .48, .9), .2, .23)
LED.node_tree.nodes.get("Principled BSDF").inputs["Emission Color"].default_value = (.03, .3, 1, 1)
LED.node_tree.nodes.get("Principled BSDF").inputs["Emission Strength"].default_value = 2
for m, scale, strength in [(AL, 650, .025), (POT, 95, .09), (DIRT, 65, .5)]:
    nodes, links = m.node_tree.nodes, m.node_tree.links
    noise = nodes.new("ShaderNodeTexNoise")
    noise.inputs["Scale"].default_value = scale
    noise.inputs["Detail"].default_value = 3
    bump = nodes.new("ShaderNodeBump")
    bump.inputs["Strength"].default_value = strength
    bump.inputs["Distance"].default_value = .007 if m == DIRT else .001
    links.new(noise.outputs["Fac"], bump.inputs["Height"])
    links.new(bump.outputs["Normal"], nodes.get("Principled BSDF").inputs["Normal"])


def mesh(name, verts, faces, material, parent=None, col=None):
    data = bpy.data.meshes.new(name)
    data.from_pydata(verts, [], faces)
    data.update()
    obj = bpy.data.objects.new(name, data)
    (col or current).objects.link(obj)
    if material:
        data.materials.append(material)
    obj.parent = parent
    return obj


def empty(name, pos=(0, 0, 0), parent=None, col=None):
    obj = bpy.data.objects.new(name, None)
    (col or current).objects.link(obj)
    obj.empty_display_type = "PLAIN_AXES"
    obj.empty_display_size = .12
    obj.location = pos
    obj.parent = parent
    return obj


root = empty("DEVICE_ROOT")
root["dimensions_estimated"] = True
root["photo_frame_width_m"] = W
root["photo_frame_depth_m"] = D
root["photo_frame_top_m"] = TOP
root["x_min"] = XMIN
root["x_max"] = XMAX
root["y_min"] = YMIN
root["y_max"] = YMAX
root["description"] = "Photo-derived dimensions; calibrate against physical measurements."


def bevel(obj, width=.002, segments=3):
    mod = obj.modifiers.new("Machined edge radii", "BEVEL")
    mod.width = width
    mod.segments = segments
    mod = obj.modifiers.new("Weighted face normals", "WEIGHTED_NORMAL")
    mod.keep_sharp = True
    return obj


def box(name, pos, size, material, parent=root, radius=.0015):
    x, y, z = [v / 2 for v in size]
    verts = [(a*x, b*y, c*z) for a,b,c in [(-1,-1,-1),(1,-1,-1),(1,1,-1),(-1,1,-1),(-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)]]
    faces = [(3,2,1,0),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7),(4,5,6,7)]
    obj = mesh(name, verts, faces, material, parent)
    obj.location = pos
    if radius:
        bevel(obj, radius, 2)
    return obj


def cylinder(name, pos, radius, depth, material, parent=root, axis=(0,0,1), sides=24):
    verts = [(radius*math.cos(i*math.tau/sides), radius*math.sin(i*math.tau/sides), z) for z in [-depth/2,depth/2] for i in range(sides)]
    faces = [tuple(reversed(range(sides))), tuple(range(sides,2*sides))]
    faces += [(i,(i+1)%sides,(i+1)%sides+sides,i+sides) for i in range(sides)]
    obj = mesh(name, verts, faces, material, parent)
    obj.location = pos
    obj.rotation_mode = "QUATERNION"
    obj.rotation_quaternion = Vector((0,0,1)).rotation_difference(Vector(axis).normalized())
    for p in obj.data.polygons[2:]:
        p.use_smooth = sides > 8
    return obj


def rod(name, a, b, radius, material, parent=root, sides=12):
    va,vb = Vector(a),Vector(b)
    return cylinder(name,(va+vb)/2,radius,(vb-va).length,material,parent,vb-va,sides)


def sphere(name,pos,scale,material,parent=root):
    n, rings = 24, 12
    verts = [(math.sin(j*math.pi/rings)*math.cos(i*math.tau/n)*scale[0],math.sin(j*math.pi/rings)*math.sin(i*math.tau/n)*scale[1],math.cos(j*math.pi/rings)*scale[2]) for j in range(rings+1) for i in range(n)]
    faces = [(j*n+i,j*n+(i+1)%n,(j+1)*n+(i+1)%n,(j+1)*n+i) for j in range(rings) for i in range(n)]
    obj = mesh(name,verts,faces,material,parent)
    obj.location = pos
    for p in obj.data.polygons:
        p.use_smooth = True
    return obj


def tube(name,points,radius,material,parent=root,smooth=True):
    data = bpy.data.curves.new(name,"CURVE")
    data.dimensions = "3D"
    data.resolution_u = 12
    data.bevel_depth = radius
    data.bevel_resolution = 2
    if smooth:
        spline=data.splines.new("BEZIER")
        spline.bezier_points.add(len(points)-1)
        for p,co in zip(spline.bezier_points,points):
            p.co=co
            p.handle_left_type=p.handle_right_type="AUTO"
    else:
        spline=data.splines.new("POLY")
        spline.points.add(len(points)-1)
        for p,co in zip(spline.points,points):
            p.co=(*co,1)
    data.materials.append(material)
    obj=bpy.data.objects.new(name,data)
    current.objects.link(obj)
    obj.parent=parent
    return obj


def bolt(pos,parent=root,axis=(0,0,1),size=.004,name="M6 socket fastener"):
    cylinder(name+" washer",pos,size*1.6,.0012,STEEL,parent,axis,16)
    p=Vector(pos)+Vector(axis)*.002
    cylinder(name,p,size,.003,STEEL,parent,axis,12)
    cylinder(name+" hex recess",p+Vector(axis)*.0017,size*.52,.0005,BLACK,parent,axis,6)


def label(name,text,pos,size,material=BLACK,parent=root,rot=(math.pi/2,0,0)):
    data=bpy.data.curves.new(name,"FONT")
    data.body=text
    data.size=size
    data.extrude=.00005
    data.align_x="CENTER"
    obj=bpy.data.objects.new(name,data)
    current.objects.link(obj)
    data.materials.append(material)
    obj.location=pos
    obj.rotation_euler=rot
    obj.parent=parent
    return obj


def extrusion(name,a,b,u=.06,v=.06,parent=root,material=AL):
    # Each side has a narrow opening and a wider T-shaped cavity.
    outline=[]
    corners=[(-u/2,-v/2),(u/2,-v/2),(u/2,v/2),(-u/2,v/2)]
    for k in range(4):
        start,end=Vector(corners[k]),Vector(corners[(k+1)%4])
        length=(end-start).length
        tangent=(end-start).normalized()
        inward=Vector((-tangent.y,tangent.x))
        outline.append(tuple(start))
        count=2 if length>.079 else 1
        for slot in range(count):
            center=length*(slot+.5)/count
            for along,deep in [(-.0045,0),(-.0045,.003),(-.009,.003),(-.009,.008),(.009,.008),(.009,.003),(.0045,.003),(.0045,0)]:
                outline.append(tuple(start+tangent*(center+along)+inward*deep))
    n=len(outline)
    length=(Vector(b)-Vector(a)).length
    verts=[(x,y,z) for z in [-length/2,length/2] for x,y in outline]
    faces=[tuple(reversed(range(n))),tuple(range(n,n*2))]+[(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)]
    obj=mesh(name,verts,faces,material,parent)
    obj.location=(Vector(a)+Vector(b))/2
    obj.rotation_mode="QUATERNION"
    obj.rotation_quaternion=Vector((0,0,1)).rotation_difference(Vector(b)-Vector(a))
    bevel(obj,.00045,2)
    return obj


# Main open-sided frame with rear service extension, square grooves and caster feet.
for x in [-W/2,W/2]:
    for y in [-D/2,D/2]:
        extrusion("6060 slotted upright",(x,y,BASE),(x,y,TOP))
        box("Black upright end cap",(x,y,BASE),(.064,.064,.008),RUBBER)
        box("Caster mounting plate",(x,y,.14),(.085,.085,.009),STEEL)
        cylinder("Caster swivel race",(x,y,.12),.028,.025,STEEL)
        for xx in [x-.026,x+.026]:
            box("Fork cheek",(xx,y+.006,.084),(.008,.045,.064),STEEL)
        cylinder("Rubber caster tyre",(x,y+.014,.06),.057,.038,RUBBER,axis=(1,0,0),sides=32)
        cylinder("Caster wheel hub",(x,y+.014,.06),.022,.042,WHITE,axis=(1,0,0))
        bolt((x+.030,y+.014,.06),axis=(1,0,0))
        box("Caster brake pedal",(x,y-.033,.08),(.038,.035,.008),STEEL)
for z in [BASE,TOP]:
    for y in [-D/2,D/2]:
        extrusion("Horizontal long frame",(-W/2,y,z),(W/2,y,z),.06,.09)
    for x in [-W/2,W/2]:
        extrusion("Horizontal short frame",(x,-D/2,z),(x,D/2,z),.06,.09)
for x in [-.74,0,.74]:
    extrusion("Underfloor cross support",(x,-D/2,BASE-.027),(x,D/2,BASE-.027),.04,.04)
box("White growing deck",(0,0,BASE+.025),(W-.06,D-.06,.012),WHITE)
for x in [-W/2,W/2]:
    extrusion("Service deck extension",(x,D/2,BASE),(x,D/2+.50,BASE))
extrusion("Service deck end",(-W/2,D/2+.5,BASE),(W/2,D/2+.5,BASE),.06,.06)
box("White service shelf",(0,D/2+.24,BASE+.025),(W-.055,.48,.012),WHITE)
for x in [-W/2,W/2]:
    for y in [-D/2,D/2]:
        for z in [BASE+.105,TOP-.105]:
            box("Internal corner bracket",(x-math.copysign(.035,x),y,z),(.010,.10,.10),AL)
            for dz in [-.027,.027]:
                bolt((x-math.copysign(.042,x),y-.027,z+dz),axis=(-math.copysign(1,x),0,0))
for y in [-D/2,D/2]:
    for x in [-.98,-.5,0,.5,.98]:
        bolt((x,y,BASE+.059),size=.003)


current=MECH
gantry=empty("GANTRY_X",(0,0,0),root)
carriage=empty("CARRIAGE_Y",(0,0,0),gantry)
gantry["role"]="Long-axis moving bridge"
carriage["role"]="Camera carriage along transverse bridge"


def motor(name,pos,parent=root,along="X"):
    group=empty(name,pos,parent)
    if along=="Y":
        group.rotation_euler.z=math.pi/2
    box("Servo motor body",(.004,0,.055),(.16,.082,.085),BLACK,group,.006)
    for x in [-.064,-.058,-.052,-.046,-.04,-.034,-.028,-.022,-.016]:
        box("Motor cooling rib",(x,0,.055),(.0028,.089,.091),BLACK,group,.0007)
    box("Gearbox",(.098,0,.052),(.035,.091,.10),BLACK,group)
    box("Yellow belt safety cover",(.121,-.011,.049),(.006,.070,.160),YELLOW,group,.006)
    for i in range(7):
        o=box("Diagonal ventilation opening",(.1245,-.011,.033+i*.012),(.001,.044,.0035),BLACK,group,.001)
        o.rotation_euler.x=.30
    cylinder("Motor cover fan aperture",(.125,-.011,-.006),.024,.001,BLACK,group,(1,0,0),32)
    cylinder("Motor cover fan hub",(.127,-.011,-.006),.007,.001,YELLOW,group,(1,0,0),24)
    for i in range(8):
        a=i*math.tau/8
        rod("Motor fan grille spoke",(.127,-.011+math.cos(a)*.006,-.006+math.sin(a)*.006),(.127,-.011+math.cos(a+.18)*.024,-.006+math.sin(a+.18)*.024),.0014,YELLOW,group)
    for yy in [-.028,.028]:
        for zz in [.005,.09]:
            bolt((.127,yy,zz),group,(1,0,0),.0025)
    box("Motor identification label",(.005,-.0425,.063),(.068,.001,.029),WHITE,group,.0001)
    label("Motor plate text","SERVO\n24V  /  AXIS",(.005,-.044,.065),.008,BLACK,group)
    cylinder("Encoder connector",(-.056,0,.109),.014,.024,BLACK,group)
    tube("Motor power loop",[(-.056,0,.117),(-.07,0,.161),(.025,.025,.14),(.07,.021,.08)],.005,RUBBER,group)
    return group


for y in [-D/2,D/2]:
    z=TOP+.085
    extrusion("Long linear actuator housing",(-1.24,y,z),(1.24,y,z),.06,.065,material=RAIL)
    box("Long toothed drive belt",(0,y,z+.034),(2.4,.024,.007),BLACK)
    for dy in [-.024,.024]:
        rod("Ground steel guide",(-1.19,y+dy,z+.037),(1.19,y+dy,z+.037),.004,STEEL)
    for x in [-1.245,1.245]:
        box("Actuator end bearing block",(x,y,z),(.035,.078,.088),BLACK)
        for yy in [y-.023,y+.023]:
            bolt((x,yy,z+.046),size=.003)
    for x in [-.92,-.32,.32,.92]:
        box("Rail mounting foot",(x,y,TOP+.038),(.14,.10,.012),AL)
        for xx in [x-.045,x+.045]:
            bolt((xx,y+.035,TOP+.046),size=.003)
    for x in [-.62,.62]:
        box("Inductive limit sensor",(x,y-.040,z),(.045,.016,.016),BLACK)
        sphere("Sensor LED",(x+.012,y-.05,z+.004),(.002,.001,.002),LED)
    motor("Long axis end servo",(-1.25,y-.03,TOP+.16))
    box("Gantry bearing saddle",(0,y,TOP+.137),(.16,.112,.045),BLACK,gantry)
    box("Bridge mounting plate",(0,y,TOP+.166),(.205,.135,.013),AL,gantry)
    for x in [-.075,.075]:
        for yy in [y-.04,y+.04]:
            bolt((x,yy,TOP+.177),gantry)

extrusion("Moving transverse linear actuator",(0,-.84,TOP+.203),(0,.84,TOP+.203),.066,.076,gantry,RAIL)
box("Transverse black drive belt",(0,0,TOP+.244),(.030,1.60,.007),BLACK,gantry)
for x in [-.026,.026]:
    rod("Transverse precision guide",(x,-.76,TOP+.245),(x,.76,TOP+.245),.004,STEEL,gantry)
for y in [-.845,.845]:
    box("Transverse end cap",(0,y,TOP+.203),(.089,.024,.083),BLACK,gantry)
    label("Module caution label","LINEAR",(0,y-.014,TOP+.222),.009,YELLOW,gantry)
motor("Transverse drive servo",(.075,.81,TOP+.248),gantry,"Y")
box("Camera sliding saddle",(0,0,TOP+.269),(.121,.13,.036),BLACK,carriage)
box("White camera mounting plate",(.055,0,TOP+.295),(.25,.165,.007),WHITE,carriage)
for x in [-.035,.06]:
    for y in [-.049,.049]:
        bolt((x,y,TOP+.303),carriage)


# Reusable rigid links positioned along a constant-length U-shaped cable path.
link_parts=[]
for yy in [-.023,.023]:
    link_parts.append(box("Temporary chain cheek",(0,yy,0),(.026,.009,.028),RUBBER,None,0))
for xx in [-.010,.010]:
    link_parts.append(box("Temporary chain rung",(xx,0,.010),(.005,.052,.006),RUBBER,None,0))
for yy in [-.028,.028]:
    link_parts.append(cylinder("Temporary hinge",(0,yy,0),.006,.003,RUBBER,None,(0,1,0),10))
bpy.ops.object.select_all(action="DESELECT")
for o in link_parts:
    o.select_set(True)
bpy.context.view_layer.objects.active=link_parts[0]
bpy.ops.object.join()
template=bpy.context.object
# Bake origin so the shared link mesh is centred at its local hinge.
bpy.context.scene.cursor.location=(0,0,0)
bpy.ops.object.origin_set(type="ORIGIN_CURSOR")
link_data=template.data
link_data.name="Reusable articulated chain link"
bpy.data.objects.remove(template,do_unlink=True)


def chain(name,axis,fixed,length,z,side,parent):
    container=empty(name,parent=parent)
    container["axis"]=axis
    container["fixed"]=fixed
    container["length"]=length
    container["radius"]=.067
    container["base_z"]=z
    container["side"]=side
    count=math.ceil(length/.028)
    for i in range(count):
        obj=bpy.data.objects.new("%s.link.%03d"%(name,i),link_data)
        current.objects.link(obj)
        obj.parent=container
        obj["chain_t"]=(i+.5)/count
    return container


chain("CHAIN_X_FRONT","X",-1.12,2.40,TOP+.137,-.785,root)
chain("CHAIN_X_REAR","X",-1.12,2.40,TOP+.137,.785,root)
chain("CHAIN_Y","Y",-.72,1.70,TOP+.264,-.103,gantry)
for y in [-.785,.785]:
    box("X chain moving end anchor",(0,y,TOP+.265),(.052,.058,.014),BLACK,gantry)
    box("X chain anchor upright",(0,y,TOP+.215),(.012,.03,.10),BLACK,gantry)
box("Y chain moving end anchor",(-.103,0,TOP+.392),(.058,.052,.014),BLACK,carriage)
box("Y chain anchor upright",(-.103,0,TOP+.344),(.03,.012,.09),BLACK,carriage)
for y in [-.785,.785]:
    for x in [-.94,-.44,.06,.56,1.04]:
        box("White chain support clip",(x,y,TOP+.097),(.05,.082,.009),WHITE)
        bolt((x,y-.025,TOP+.104),size=.0025)


current=CAM
box("Camera bracket vertical back",(.151,0,TOP+.13),(.007,.16,.32),WHITE,carriage)
box("Camera bracket lower ledge",(.101,0,TOP-.03),(.107,.16,.007),WHITE,carriage)
for z in [TOP+.035,TOP+.09,TOP+.145,TOP+.20]:
    box("Bracket height adjustment slot",(.155,0,z),(.001,.04,.005),BLACK,carriage,.001)
pan=empty("CAMERA_PAN",(.076,0,TOP-.036),carriage)
cylinder("Camera mounting collar",(0,0,-.024),.072,.045,WHITE,pan,sides=48)
sphere("White camera upper housing",(0,0,-.050),(.084,.082,.035),WHITE,pan)
tilt=empty("CAMERA_TILT",(0,0,-.10),pan)
sphere("White gimbal ball",(0,0,-.015),(.071,.069,.064),WHITE,tilt)
face=cylinder("Black camera lens face",(0,-.058,-.025),.05,.04,BLACK,tilt,(0,-1,0),64)
face.scale.y=.90
bevel(face,.002,3)
cylinder("Camera lens retaining ring",(0,-.076,-.022),.023,.009,BLACK,tilt,(0,-1,0),40)
cylinder("Blue coated optical lens",(0,-.082,-.022),.017,.003,GLASS,tilt,(0,-1,0),40)
for x in [-.032,.032]:
    cylinder("Infrared illuminator",(x,-.080,-.018),.008,.002,GLASS,tilt,(0,-1,0))
sphere("Camera indicator",(.023,-.079,-.046),(.002,.001,.002),LED,tilt)
label("Camera body marking","IP CAMERA",(0,-.077,.022),.008,GRAY,pan)
camera_data=bpy.data.cameras.new("Actual inspection camera optics")
sensor=bpy.data.objects.new("INSPECTION_CAMERA",camera_data)
CAM.objects.link(sensor)
sensor.parent=tilt
sensor.location=(0,-.085,-.022)
sensor.rotation_euler=(math.pi/2,0,0)
camera_data.lens=3.6
camera_data.sensor_width=5.6
camera_data.clip_start=.025
camera_data.clip_end=8
camera_data.display_size=.10
tube("Orange carriage service loop",[(-.11,-.08,TOP+.34),(.09,-.11,TOP+.38),(.23,-.08,TOP+.28),(.215,-.03,TOP-.20)],.006,ORANGE,carriage)
cylinder("Nozzle push-fit coupler",(.215,-.03,TOP-.203),.012,.035,BLACK,carriage)
cylinder("Nozzle blue collar",(.215,-.03,TOP-.189),.013,.009,LED,carriage)
cylinder("Brass spray tip",(.215,-.03,TOP-.231),.006,.02,RAIL,carriage)
tube("Camera signal lead",[(.06,.025,TOP-.055),(.145,.07,TOP+.04),(.13,.065,TOP+.29),(-.07,.08,TOP+.28)],.0035,RUBBER,carriage)


current=PLANTS


def pot(name,x,y):
    group=empty(name,(x,y,BASE+.035),root)
    verts=[]
    for z,half in [(0,.122),(.252,.151),(.252,.138),(.224,.136)]:
        verts.extend([(-half,-half,z),(half,-half,z),(half,half,z),(-half,half,z)])
    faces=[(3,2,1,0)]
    for ring in range(3):
        for j in range(4):
            faces.append((ring*4+j,ring*4+(j+1)%4,(ring+1)*4+(j+1)%4,(ring+1)*4+j))
    obj=mesh(name+" tapered open pot",verts,faces,POT,group)
    bevel(obj,.0017,2)
    box("Soil surface",(0,0,.227),(.272,.272,.012),DIRT,group,.0005)
    for xx in [-.133,.133]:
        for zz in [.045,.205]:
            cylinder("Pot rivet",(xx,-(.122+zz*.116)-.001,zz),.0025,.0015,STEEL,group,(0,-1,0),10)
    # Low-poly granules are merged into one mesh per tray.
    verts,faces=[],[]
    for i in range(85):
        xx,yy=random.uniform(-.127,.127),random.uniform(-.127,.127)
        r=random.uniform(.0015,.005)
        k=len(verts)
        verts.extend([(xx-r,yy-r,.235),(xx+r,yy-r,.235),(xx+r,yy+r,.235),(xx-r,yy+r,.235),(xx,yy,.235+r)])
        faces.extend([(k,k+1,k+4),(k+1,k+2,k+4),(k+2,k+3,k+4),(k+3,k,k+4)])
    mesh("Soil aggregate",verts,faces,WOOD,group)
    rod("Plant label stake",(.061,.062,.20),(.061,.062,.40),.004,WHITE,group)
    box("White plant identification tag",(.061,.060,.39),(.095,.003,.05),WHITE,group,.006)
    label("Plant identifier",name,(.061,.057,.393),.013,BLACK,group)
    label("Plant accession","TRIAL  /  C%d" % (int(name[-2:])%3+1),(.061,.057,.377),.006,BLACK,group)
    for xx in [-.06,.06]:
        rod("Irrigation emitter stake",(xx,-.065,.204),(xx,-.065,.286),.003,WHITE,group)
        cylinder("White drip tee",(xx,-.065,.27),.007,.021,WHITE,group,(1,0,0),12)
    tube("Clear micro irrigation loop",[(-.06,-.065,.274),(-.17,-.10,.30),(-.17,-.24,.30),(.12,-.25,.28),(.06,-.065,.274)],.0027,TUBE,group)
    return group


def leaf_mesh(name,leaves,parent):
    verts,faces=[],[]
    for origin,direction,length,width in leaves:
        v=Vector(direction).normalized()
        side=v.cross(Vector((0,0,1)))
        if side.length<.01:
            side=Vector((1,0,0))
        side.normalize()
        off=len(verts)
        for j in range(8):
            t=j/7
            middle=Vector(origin)+v*(t*length)+Vector((0,0,-length*.55*t*t))
            w=math.sin(math.pi*t)**.7*width*(1 if j%2 else .78)
            for edge in [-1,0,1]:
                point=middle+side*(edge*w)+Vector((0,0,-abs(edge)*w*.25+math.sin(t*9)*w*.15))
                verts.append(tuple(point))
        for j in range(7):
            for k in range(2):
                n=off+j*3+k
                faces.append((n,n+1,n+4,n+3))
    obj=mesh(name,verts,faces,None,parent)
    for m in LEAVES:
        obj.data.materials.append(m)
    for p in obj.data.polygons:
        p.material_index=(p.index//14)%len(LEAVES)
        p.use_smooth=True
    return obj


def blooms(name,centres,parent):
    verts,faces=[],[]
    for center,radius in centres:
        center=Vector(center)
        for petal in range(5):
            a=petal*math.tau/5+random.random()*.2
            forward=Vector((math.cos(a),math.sin(a),.18))
            side=Vector((-math.sin(a),math.cos(a),0))
            off=len(verts)
            for j in range(5):
                t=j/4
                for k in range(5):
                    s=(k-2)/2
                    p=center+forward*(t*radius)+side*(s*radius*.55*math.sin(math.pi*t))
                    p.z+=radius*(.28*t*t+.14*s*s)
                    verts.append(tuple(p))
            for j in range(4):
                for k in range(4):
                    n=off+j*5+k
                    faces.append((n,n+1,n+6,n+5))
        sphere("Flower yellow centre",center,(radius*.21,radius*.21,.003),POLLEN,parent)
    obj=mesh(name,verts,faces,PETAL,parent)
    for p in obj.data.polygons:
        p.use_smooth=True


for row,y in enumerate([-.46,0,.46]):
    for col,x in enumerate([-.87,-.29,.29,.87]):
        index=row*4+col+1
        group=pot("P-%02d"%index,x,y)
        height=([.60,.28,.72,.32][col] if row==0 else [1.44,1.62,1.52,1.73][col]-(.12 if row==1 else 0))
        lean=random.uniform(-.08,.08)
        tube("Main woody stem",[(0,0,.235),(.015,0,.55),(lean,.01,height+.20)],.004,STEM,group)
        rod("Bamboo support",(-.035,.022,.23),(-.032,.017,height+.27),.005,WOOD,group)
        for z in [.47,.8,1.2]:
            if z<height+.2:
                tube("Stem support tie",[(-.041,.022,z),(-.03,.004,z),(.02,-.008,z),(.028,.02,z),(-.041,.022,z)],.0014,TUBE,group)
        leaves=[]
        flower_centres=[]
        if row>0 or col==0:
            for j in range(14 if row>0 else 5):
                z=.47+(height-.25)*j/(14 if row>0 else 5)
                a=j*2.399+index
                length=random.uniform(.13,.26)
                end=(math.cos(a)*length,math.sin(a)*length,z+random.uniform(.02,.13))
                tube("Drooping branch",[(lean*j/18,0,z),end],.0022,STEM,group)
                for k in range(7):
                    f=(k+.5)/7
                    origin=(end[0]*f,end[1]*f,z+(end[2]-z)*f)
                    la=a+(-.7 if k%2 else .7)
                    leaves.append((origin,(math.cos(la),math.sin(la),random.uniform(-.6,.05)),random.uniform(.10,.20),random.uniform(.021,.039)))
                if col in [0,1,2] and row>0 and j>4:
                    for k in range(5):
                        flower_centres.append(((end[0]+random.uniform(-.032,.032),end[1]+random.uniform(-.032,.032),end[2]+k*.026),random.uniform(.024,.035)))
                if col==3 and row>0 and j in [7,8,10]:
                    for k in range(2):
                        sphere("Small ripening fruit",(end[0]+k*.034,end[1],end[2]-.028),(.021,.022,.026),FRUITS[k%2],group)
            leaf_mesh("Folded hanging foliage",leaves,group)
            if flower_centres:
                blooms("Five-petal white blossoms",flower_centres,group)

for y in [-.46,0,.46]:
    tube("Row irrigation supply",[(-1.08,y,.245),(-.70,y-.21,.247),(0,y-.21,.248),(.75,y-.21,.245),(1.1,y,.25)],.0033,TUBE)
box("Soil sensor transmitter",(-.96,-.49,.43),(.045,.021,.083),BLACK)
box("Soil sensor blue panel",(-.96,-.502,.43),(.035,.002,.064),LED)
tube("Soil probe cable",[(-.96,-.49,.40),(-1.01,-.50,.28),(-1.13,.20,.23),(-1.10,.81,.29)],.002,RUBBER)


current=SERVICE
# Rear white open reservoir and ribbed electrical cabinet.
tank=empty("RESERVOIR",(-.70,.99,BASE+.034),root)
box("Reservoir bottom",(0,0,.014),(.56,.40,.028),WHITE,tank,.01)
for x in [-.275,.275]:
    box("Reservoir side wall",(x,0,.255),(.016,.40,.51),WHITE,tank,.006)
for y in [-.196,.196]:
    box("Reservoir end wall",(0,y,.255),(.56,.016,.51),WHITE,tank,.006)
for x in [-.28,.28]:
    box("Reservoir rounded rim",(x,0,.514),(.026,.421,.022),WHITE,tank,.007)
for y in [-.204,.204]:
    box("Reservoir rounded rim",(0,y,.514),(.56,.026,.022),WHITE,tank,.007)
cab=empty("CONTROL_CABINET",(.69,1.00,BASE+.034),root)
box("IP65 cabinet enclosure",(0,0,.36),(.52,.28,.72),GRAY,cab,.022)
box("Cabinet door gasket",(0,-.148,.36),(.488,.01,.69),RUBBER,cab,.016)
box("Cabinet recessed door",(0,-.159,.36),(.474,.017,.674),GRAY,cab,.018)
for z in [.13,.59]:
    box("Cabinet hinge",(-.255,-.14,z),(.020,.028,.075),WHITE,cab,.005)
    bolt((.215,-.171,z),cab,(0,-1,0),.004)
for i in range(11):
    box("Cabinet side cooling rib",(-.264,.017,.31+i*.013),(.004,.20,.004),GRAY,cab,.001)
box("Cabinet small equipment plate",(.12,-.173,.64),(.10,.001,.027),WHITE,cab,.001)
label("Cabinet serial","CONTROL 01",(.12,-.175,.642),.007,BLACK,cab)
router=empty("WiFi router",(0,0,.75),cab)
box("White router body",(0,0,0),(.27,.17,.032),WHITE,router,.011)
for x in [-.115,.115]:
    for y in [-.055,.055]:
        rod("Router antenna",(x,y,.01),(x+math.copysign(.03,x),y,.25),.004,WHITE,router)
sphere("Router power indicator",(0,-.087,0),(.004,.002,.002),LED,router)
for x in [.40,.52,.76,.86]:
    cylinder("Cabinet cable gland",(x,1.01,BASE+.03),.013,.04,BLACK)
    tube("Service cable",[(x,1.01,BASE+.02),(x+.05,1.14,.12),(1.12,1.12,.13),(1.16,.72,.20)],.004,RUBBER)
box("Utility tray",(0,1.02,.25),(.40,.35,.08),mat("Dark green service tray",(.022,.07,.046)),radius=.008)
cylinder("Bottle",(-.07,1.02,.35),.032,.17,WHITE)
cylinder("Bottle cap",(-.07,1.02,.445),.021,.016,WHITE)
pump=empty("DIAPHRAGM_PUMP",(-W/2-.04,.72,.74),root)
cylinder("Pump motor",(0,0,0),.036,.12,BLACK,pump)
box("Pump mounting bracket",(.025,0,.03),(.02,.09,.18),BLACK,pump)
box("Pump valve head",(0,0,.093),(.08,.067,.06),BLACK,pump,.004)
box("Pump rating label",(-.036,-.005,-.008),(.001,.045,.077),WHITE,pump,.001)
for z in [-.063,.13]:
    bolt((-.048,-.03,z),pump,(-1,0,0),.004)
tube("Orange tank suction tube",[(-.77,1.02,.42),(-1.02,1.06,.74),(-1.27,.73,.83)],.006,ORANGE)
tube("Orange pressurised riser",[(-1.27,.70,.86),(-1.30,.72,1.03),(-1.265,.72,1.65),(-1.265,.72,2.28),(-1.13,.73,2.35)],.006,ORANGE)
for z in [.51,1.02,1.5,2.02]:
    tube("Riser cable tie",[(-1.235,.676,z),(-1.27,.725,z),(-1.235,.762,z),(-1.165,.762,z),(-1.165,.676,z),(-1.235,.676,z)],.0017,RUBBER,smooth=False)
tube("Black gantry supply harness",[(-1.19,-.69,2.26),(0,-.69,2.26),(1.18,-.69,2.26),(1.18,.73,2.26),(1.24,.77,1.55),(1.24,.77,.40),(.92,1.07,.27)],.006,RUBBER)
# Weather sensor package mounted outside the upper rear post.
weather=empty("WEATHER_SENSOR",(1.25,.69,1.95),root)
box("Weather sensor bracket",(.09,0,.00),(.20,.10,.011),WHITE,weather)
box("Weather bracket vertical",(.005,0,-.04),(.009,.09,.13),WHITE,weather)
cylinder("Radiation shield core",(.15,0,.04),.034,.10,GRAY,weather)
for z in [.003,.02,.037,.054,.071,.088]:
    cylinder("Radiation shield louvres",(.15,0,z),.051,.010,WHITE,weather,sides=32)
rod("Wind instrument mast",(.15,0,.10),(.15,0,.27),.012,WHITE,weather)
box("Weather cross arm",(.15,0,.27),(.44,.025,.022),BLACK,weather)
for x in [-.035,.35]:
    cylinder("Wind sensor spindle",(x,0,.30),.009,.07,BLACK,weather)
for i in range(3):
    a=i*math.tau/3
    p=(-.035+math.cos(a)*.065,math.sin(a)*.065,.338)
    rod("Anemometer arm",(-.035,0,.338),p,.0025,BLACK,weather)
    sphere("Anemometer cup",p,(.017,.018,.012),BLACK,weather)
rod("Wind vane beam",(.28,0,.338),(.43,0,.338),.0025,BLACK,weather)
mesh("Wind vane fin",[(.36,0,.34),(.43,0,.34),(.43,0,.405),(.38,0,.39)],[(0,1,2,3)],BLACK,weather)


current=STUDIO
FLOOR=mat("Studio floor",(.25,.29,.31),.05,.7)
box("Ground plane",(0,0,-.018),(200,200,.028),FLOOR,None,.0)


def view_camera(name,pos,target,lens):
    data=bpy.data.cameras.new(name)
    obj=bpy.data.objects.new(name,data)
    current.objects.link(obj)
    obj.location=pos
    obj.rotation_euler=(Vector(target)-obj.location).to_track_quat("-Z","Y").to_euler()
    data.lens=lens
    data.clip_end=300
    return obj


hero=view_camera("VIEW_01 | Equipment overview",(3.6,-5.8,3.8),(0,.17,1.24),51)
view_camera("VIEW_02 | Rear services",(-4.3,5.0,3.10),(0,.23,1.23),50)
view_camera("VIEW_03 | XY camera and chain",(2.15,-3.3,3.95),(-.13,0,2.24),62)
view_camera("VIEW_04 | Front elevation",(0,-7,2.3),(0,0,1.24),58)
scene.camera=hero
for name,pos,power,size,color in [
    ("Large softbox left",(-3,-4,6),1600,4,(.91,.96,1)),
    ("Large softbox right",(4,-1,4.6),1250,3,(1,.96,.89)),
    ("Back edge light",(0,4,5.2),2100,3,(1,1,1)),
    ("Ceiling fill",(0,0,6),800,3,(1,1,1))]:
    data=bpy.data.lights.new(name,"AREA")
    data.energy=power*.55
    data.shape="DISK"
    data.size=size
    data.color=color
    obj=bpy.data.objects.new(name,data)
    STUDIO.objects.link(obj)
    obj.location=pos
    obj.rotation_euler=(Vector((0,0,1.3))-obj.location).to_track_quat("-Z","Y").to_euler()
scene.world=bpy.data.worlds.new("Neutral photographic studio")
scene.world.use_nodes=True
scene.world.node_tree.nodes.get("Background").inputs["Color"].default_value=(.35,.40,.45,1)
scene.world.node_tree.nodes.get("Background").inputs["Strength"].default_value=.25
scene.render.engine="CYCLES"
scene.cycles.samples=40
scene.cycles.use_denoising=True
scene.render.resolution_x=1500
scene.render.resolution_y=1400
scene.render.resolution_percentage=100
scene.render.image_settings.file_format="PNG"
scene.view_settings.view_transform="AgX"
scene.view_settings.look="AgX - Medium High Contrast"
scene.render.fps=24
scene.frame_start=1
scene.frame_end=240
scene["model_source"]="Five user-supplied photographs, September 2026"
scene["dimension_note"]="Frame 2.40 x 1.44 m, top 2.22 m: estimated, not surveyed."

# Keep original references packed in the .blend for future refinement.
reference_dir=Path("E:/WechatFile/xwechat_files/wxid_xyw8zuia0xqs22_a2b3/temp/RWTemp/2026-09/6e05eff014efceabe1f5ad76fb1795ae")
for i,name in enumerate(["b8a5d3052610bf3d9a57773534c45378.jpg","9c9ad7e3a20236f413eddafc44e12493.jpg","696508e01a758c5f8ffcaca3be2b0042.jpg","5132f62ee88fc4d164eca4691868ebac.jpg","ad9390a63f754902fd9b8a6df35c092a.jpg"]):
    path=reference_dir/name
    if path.exists():
        image=bpy.data.images.load(str(path),check_existing=True)
        image.pack()
        obj=empty("PHOTO_%02d"%(i+1),(-5,0,i*2),col=REFS)
        obj.empty_display_type="IMAGE"
        obj.data=image
        obj.empty_display_size=2
        obj.hide_render=True
REFS.hide_viewport=True
addon_text=bpy.data.texts.load(str(HERE/"device_twin_sync.py"))
addon_text.name="device_twin_sync.py"
addon_text.use_module=False
import importlib.util
spec=importlib.util.spec_from_file_location("device_twin_sync",HERE/"device_twin_sync.py")
sync=importlib.util.module_from_spec(spec)
sys.modules[spec.name]=sync
spec.loader.exec_module(sync)
sync.register()
settings=scene.device_twin
settings.axis_x=.43
settings.axis_y=.38
settings.pan_deg=-10
settings.tilt_deg=-12
sync.apply_pose(scene)
for frame,x,y in [(1,.08,.15),(60,.88,.15),(90,.88,.8),(150,.08,.8),(180,.08,.15),(240,.88,.8)]:
    settings.axis_x=x
    settings.axis_y=y
    settings.keyframe_insert(data_path="axis_x",frame=frame)
    settings.keyframe_insert(data_path="axis_y",frame=frame)
scene.frame_set(106)
sync.apply_pose(scene)
# The demo action is stored but muted until the explicit demo mode is selected.
if scene.animation_data:
    settings.demo_action=scene.animation_data.action.name
    scene.animation_data.action.use_fake_user=True
    scene.animation_data.action=None
settings.mode="MANUAL"
settings.axis_x=.43
settings.axis_y=.38
sync.apply_pose(scene)
bpy.ops.object.select_all(action="DESELECT")
carriage.select_set(True)
bpy.context.view_layer.objects.active=carriage
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type=="VIEW_3D":
            area.spaces.active.region_3d.view_distance=5.5
            area.spaces.active.region_3d.view_location=(0,.15,1.24)
            area.spaces.active.region_3d.view_rotation=hero.rotation_euler.to_quaternion()
            area.spaces.active.shading.type="MATERIAL"
            area.spaces.active.clip_end=300
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/"JHDS_Inspection_Gantry.blend"))
print("DEVICE_BUILD_OK",len(scene.objects),"objects",flush=True)
if "--render" in sys.argv:
    scene.render.filepath=str(OUT/"01_overview.png")
    bpy.ops.render.render(write_still=True)
    print("DEVICE_RENDER_OK",flush=True)
