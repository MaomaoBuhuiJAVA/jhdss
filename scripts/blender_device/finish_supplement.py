"""Second pass: corrections identified in the first seven rendered review views."""
import ast
import bpy
import bmesh
import json
import math
import random
import sys
from pathlib import Path
from mathutils import Vector

HERE=Path(__file__).resolve().parent
OUT=HERE.parents[1]/"artifacts"/"blender-device"
scene=bpy.context.scene
if scene.get("supplement_review_complete"):
    raise RuntimeError("Run this pass only on Supplement_Refined_Pass1.blend")
root=scene.objects["DEVICE_ROOT"]
current=bpy.data.collections["04 | Plant trays and irrigation"]
for key,name in {
    "WHITE":"Off-white powder coating", "GRAY":"Weatherproof control cabinet",
    "STEEL":"Zinc plated fasteners", "BLACK":"Black anodised metal",
    "RUBBER":"Cable chain PA and rubber", "STEM":"Woody plant stems",
    "PETAL":"Ivory flower petals", "POLLEN":"Yellow-green flower centres",
    "ORANGE":"Orange pneumatic hose", "GREEN":"Green nylon cable ties",
    "GLOSS":"Glossy black instrument plastic"
}.items():
    globals()[key]=bpy.data.materials[name]
for filename in ["build_device.py","supplement_refinement.py"]:
    source=ast.parse((HERE/filename).read_text(encoding="utf-8"))
    names={"mesh","empty","bevel","box","cylinder","rod","tube","bolt","label","lathe","sphere","flowers"}
    module=ast.Module(body=[n for n in source.body if isinstance(n,ast.FunctionDef) and n.name in names],type_ignores=[])
    exec(compile(module,str(HERE/filename),"exec"),globals())
random.seed(91326)

# The first rendering revealed the V body facing into the frame instead of outward.
current=bpy.data.collections["05 | Cabinet reservoir and instruments"]
weather=scene.objects["WEATHER_SENSOR"]
weather.rotation_euler.z=math.pi/2
weather.location.x=1.24
weather.location.z=1.94
shield=scene.objects["Weather shield core"]
shield.location.x=-.085
shield.location.y=.075
for o in weather.children:
    if o.name.startswith("Weather radiation shield louvre"):
        o.location.x=-.085
        o.location.y=.075
lead=scene.objects["Weather sensor signal cable"]
for p,co in zip(lead.data.splines[0].bezier_points,[(-.085,.075,-.15),(-.085,.09,-.20),(-.055,.085,-.05),(-.055,.04,.19)]):
    p.co=co
box("Weather shield offset support",(-.085,-.06,.011),(.045,.30,.019),WHITE,weather,.003)
scene.objects["Wind vane arrow"].rotation_euler.x=math.pi/2

# Cut the optical opening before setting the lens behind the curved smoked face.
current=bpy.data.collections["03 | Camera and nozzle"]
tilt=scene.objects["CAMERA_TILT"]
ball=scene.objects["White gimbal ball"]
bm=bmesh.new()
bm.from_mesh(ball.data)
bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces))
bm.to_mesh(ball.data)
bm.free()
cutter=cylinder("TEMP recessed optical aperture",(0,-.075,.008),.0137,.032,None,tilt,(0,-1,0),64)
bpy.context.view_layer.update()
for name in ["White gimbal ball","Black camera lens face"]:
    obj=scene.objects[name]
    mod=obj.modifiers.new("Recessed optical aperture","BOOLEAN")
    mod.operation="DIFFERENCE"
    mod.solver="EXACT"
    mod.object=cutter
    bpy.context.view_layer.objects.active=obj
    bpy.ops.object.modifier_apply(modifier=mod.name)
bpy.data.objects.remove(cutter,do_unlink=True)
ring=scene.objects["Camera lens retaining ring"]
bpy.data.objects.remove(ring,do_unlink=True)
ring=lathe("Camera lens retaining ring",[(.019,-.0003),(.019,.0003),(.0136,.0003),(.0136,-.0003),(.019,-.0003)],GLOSS,tilt,(0,-.0788,.008),64)
ring.rotation_euler.x=math.pi/2
barrel=scene.objects["Concentric optical barrel"]
barrel.location.y=-.076
scene.objects["Blue coated optical lens"].location.y=-.077
ball["recessed_lens_aperture"]=True
scene.objects["INSPECTION_CAMERA"].location.y=-.078
orange_shader=ORANGE.node_tree.nodes.get("Principled BSDF")
orange_shader.inputs["Base Color"].default_value=(.7,.032,.001,1)
orange_shader.inputs["Specular IOR Level"].default_value=.25
orange_shader.inputs["Roughness"].default_value=.40

# Reduce the camouflage-like pot noise to subtle charcoal wear and vertical streaking.
current=bpy.data.collections["04 | Plant trays and irrigation"]
pot=bpy.data.materials["Charcoal square nursery pots"]
nodes,links=pot.node_tree.nodes,pot.node_tree.links
for n in nodes:
    if n.type=="VALTORGB":
        n.color_ramp.elements[0].color=(.006,.009,.008,1)
        n.color_ramp.elements[1].color=(.016,.021,.018,1)
    if n.type=="TEX_NOISE" and n.inputs["Scale"].default_value==11:
        tex=nodes.new("ShaderNodeTexCoord")
        mapping=nodes.new("ShaderNodeVectorMath")
        mapping.operation="MULTIPLY"
        mapping.inputs[1].default_value=(2.6,2.6,.18)
        links.new(tex.outputs["Generated"],mapping.inputs[0])
        links.new(mapping.outputs["Vector"],n.inputs["Vector"])
for o in scene.objects:
    if o.name.startswith("Worn nursery pot upper rim"):
        o.data.materials.clear()
        o.data.materials.append(pot)
for material in bpy.data.materials:
    if material.name.startswith("Crumpled foliage"):
        for n in material.node_tree.nodes:
            if n.type=="VALTORGB":
                for e in n.color_ramp.elements:
                    e.color=(*[v*.65 for v in e.color[:3]],1)
traymat=bpy.data.materials["Dark green service tray"]
traymat.diffuse_color=(.009,.032,.033,1)
traymat.node_tree.nodes.get("Principled BSDF").inputs["Base Color"].default_value=traymat.diffuse_color
GRAY.node_tree.nodes.get("Principled BSDF").inputs["Base Color"].default_value=(.29,.34,.38,1)

# Extra upright floral sprays match the photo's fuller, irregular white inflorescences.
for name,height in [("P-07",1.30),("P-10",1.25),("P-11",1.53)]:
    group=scene.objects[name]
    centers=[]
    for i in range(4):
        a=i*2.18+int(name[-2:])
        base=Vector((math.cos(a)*.07,math.sin(a)*.07,.86+i*.055))
        end=Vector((math.cos(a)*.20,math.sin(a)*.20,height+.16-random.random()*.14))
        tube("Upright flowering spray",[base,(base+end)/2+Vector((.02,0,.02)),end],.0015,STEM,group)
        for j in range(8):
            f=(j+.8)/9
            c=base.lerp(end,f)+Vector((random.uniform(-.023,.023),random.uniform(-.023,.023),0))
            centers.append((c,random.uniform(.021,.029)))
    flowers(group,centers)

# Retain the real orange/black routing inside all three deforming drag chains.
current=bpy.data.collections["02 | Linear motion and cable chains"]
for name in ["CHAIN_X_FRONT","CHAIN_X_REAR","CHAIN_Y"]:
    chain=scene.objects[name]
    for index,offset in enumerate([-.010,.010]):
        material=ORANGE if index==0 and name!="CHAIN_X_FRONT" else RUBBER
        data=bpy.data.curves.new(name+" internal routed cable","CURVE")
        data.dimensions="3D"
        data.bevel_depth=.0043 if material==ORANGE else .0037
        data.bevel_resolution=3
        spline=data.splines.new("POLY")
        spline.points.add(240)
        obj=bpy.data.objects.new(name+".cable.%d"%index,data)
        current.objects.link(obj)
        obj.parent=chain
        obj["chain_cable_offset"]=offset
        data.materials.append(material)

# Two mechanical feet and actual clamp bolts connect the folded weather bracket to the post.
current=bpy.data.collections["05 | Cabinet reservoir and instruments"]
for z in [-.17,-.115]:
    box("Weather post mounting spacer",(0,.002,z),(.085,.018,.026),WHITE,weather,.001)
    for x in [-.026,.026]:
        bolt((x,-.010,z),weather,(0,-1,0),.0035)

# Inspection views are saved in the deliverable, not just transient render-camera changes.
current=bpy.data.collections["06 | Lighting and inspection views"]
def view(name,pos,target,lens):
    camera=scene.objects.get(name)
    if camera is None:
        camera=bpy.data.objects.new(name,bpy.data.cameras.new(name))
        current.objects.link(camera)
    camera.location=pos
    camera.rotation_euler=(Vector(target)-camera.location).to_track_quat("-Z","Y").to_euler()
    camera.data.lens=lens
    return camera

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
p=scene.objects["CAMERA_TILT"].matrix_world.translation.copy()
view("VIEW_05 | Camera housing and nozzle",p+Vector((.50,-.52,-.18)),p+Vector((.055,0,.08)),32)
view("VIEW_06 | Integrated weather station",(2.45,-.05,2.30),(1.39,.68,2.02),52)
view("VIEW_07 | Camera rear through slots",p+Vector((.80,.43,.13)),p+Vector((.055,0,.08)),37)
scene.camera=scene.objects["VIEW_01 | Equipment overview"]
text=bpy.data.texts.get("device_twin_sync.py")
if text:
    text.clear()
    text.write((HERE/"device_twin_sync.py").read_text(encoding="utf-8"))
scene["supplement_review_complete"]=True
scene["supplement_refinement"]="2026-09-12 / two photo-comparison refinement passes"
scene["unmeasured_parts"]="Dimensions, unseen internal electrical components and plant microstructure are estimated."
for o in scene.objects:
    if o.name.startswith("Concealed generic DIN module"):
        o["reconstruction_note"]="Illustrative internal component; no readable internal wiring reference supplied."
bpy.ops.object.select_all(action="DESELECT")
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/"revisions"/"Supplement_Refined_Final.blend"))
print("SECOND_REVIEW_PASS_COMPLETE",len(scene.objects),flush=True)
