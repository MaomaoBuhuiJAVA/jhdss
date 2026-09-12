"""Move the service assembly from the long edge to the photographed short end."""
import ast
import bpy
import json
import math
from datetime import datetime
from pathlib import Path
from mathutils import Vector
import device_twin_sync as sync

HERE=Path(r"C:/Users/Administrator/Documents/Codex/2026-08-26/https-github-com-maomaobuhuijava-jhdss-git/jhdss/scripts/blender_device")
OUT=HERE.parents[1]/"artifacts"/"blender-device"
scene=bpy.context.scene
assert scene.get("supplement_review_complete"),"Open the refined equipment model first"
assert not scene.get("side_service_layout"),"Side correction has already been applied"
backup=OUT/"revisions"/("Before_Side_Service_Correction_"+datetime.now().strftime("%Y%m%d_%H%M%S")+".blend")
bpy.ops.wm.save_as_mainfile(filepath=str(backup),copy=True)
root=scene.objects["DEVICE_ROOT"]
current=bpy.data.collections["01 | Aluminium frame and fasteners"]
for key,name in {"AL":"Satin extruded aluminium","STEEL":"Zinc plated fasteners","WHITE":"Off-white powder coating","BLACK":"Black anodised metal","RUBBER":"Cable chain PA and rubber","ORANGE":"Orange pneumatic hose"}.items():
    globals()[key]=bpy.data.materials[name]
source=ast.parse((HERE/"build_device.py").read_text(encoding="utf-8"))
names={"mesh","bevel","box","cylinder","rod","tube","bolt","extrusion","empty"}
exec(compile(ast.Module(body=[n for n in source.body if isinstance(n,ast.FunctionDef) and n.name in names],type_ignores=[]),str(HERE/"build_device.py"),"exec"),globals())

protected=[o for o in scene.objects if o.name.startswith("P-") and o.type=="EMPTY"]
protected += [scene.objects[n] for n in ["DEVICE_ROOT","GANTRY_X","CARRIAGE_Y","CAMERA_PAN","CAMERA_TILT","WEATHER_SENSOR"]]
fixed={o.name:o.matrix_basis.copy() for o in protected}
for o in list(scene.objects):
    if o.name.startswith(("Service deck extension","Service deck end","White service shelf")):
        bpy.data.objects.remove(o,do_unlink=True)

# The shelf now extends along X, spanning only the 1.44 m short edge in Y.
for y in [-.72,.72]:
    extrusion("Service deck extension",(1.20,y,.19),(1.80,y,.19),.06,.06)
extrusion("Service deck end",(1.80,-.72,.19),(1.80,.72,.19),.06,.06)
extrusion("Service shelf central support",(1.20,0,.168),(1.80,0,.168),.04,.04)
box("White service shelf",(1.50,0,.215),(.54,1.38,.012),WHITE)
for y in [-.72,.72]:
    box("Side platform connecting plate",(1.20,y,.225),(.15,.064,.006),AL)
    for x in [1.155,1.245]:
        bolt((x,y,.230),size=.003)

tank=scene.objects["RESERVOIR"]
tank.location=(1.50,-.432,.224)
tank.rotation_euler.z=math.pi/2
tank.scale.x=.88
cab=scene.objects["CONTROL_CABINET"]
cab.location=(1.52,.439,.224)
cab.rotation_euler.z=math.pi/2
cab.scale.x=.90
tray=scene.objects["OPEN_UTILITY_BASKET"]
tray.location=(1.535,.010,.231)
tray.rotation_euler.z=math.pi/2
tray.scale.x=.82

# Pump and suction move together to the tank-side corner; fixed feed reaches the drag chain.
current=bpy.data.collections["05 | Cabinet reservoir and instruments"]
pump=scene.objects["DIAPHRAGM_PUMP"]
pump.location=(1.24,-.72,.74)
pump.rotation_euler.z=math.pi
for o in scene.objects:
    if o.name.startswith("Riser cable tie"):
        o.rotation_euler.z+=math.pi

def replace_curve(name,points,radius,material):
    old=scene.objects[name]
    bpy.data.objects.remove(old,do_unlink=True)
    return tube(name,points,radius,material,root)

replace_curve("Orange tank suction tube",[(1.53,-.44,.48),(1.56,-.54,.81),(1.40,-.71,.88),(1.27,-.73,.833)],.006,ORANGE)
replace_curve("Orange pressurised riser",[(1.27,-.70,.86),(1.30,-.72,1.03),(1.265,-.72,1.65),(1.265,-.72,2.25),(1.17,-.785,2.323),(-.97,-.785,2.323),(-1.12,-.795,2.357)],.006,ORANGE)
replace_curve("Black gantry supply harness",[(-1.19,-.69,2.26),(0,-.69,2.26),(1.18,-.69,2.26),(1.18,.73,2.26),(1.24,.77,1.55),(1.24,.77,.40),(1.40,.56,.26),(1.52,.44,.24)],.006,RUBBER)
glands=sorted([o for o in scene.objects if o.name.startswith("Cabinet cable gland")],key=lambda o:o.name)
cables=sorted([o for o in scene.objects if o.name.startswith("Service cable")],key=lambda o:o.name)
for i,o in enumerate(glands):
    o.location=(1.49,.285+i*.095,.222)
for i,o in enumerate(cables):
    name=o.name
    y=.285+i*.095
    replace_curve(name,[(1.49,y,.218),(1.53,y+.02,.164),(1.69,.61+i*.01,.15),(1.35,.68+i*.01,.157),(1.225,.705,.22)],.004,RUBBER)

# Frame the corrected end while preserving view names used by the installed add-on.
def view(name,pos,target,lens):
    obj=scene.objects[name]
    obj.location=pos
    obj.rotation_euler=(Vector(target)-obj.location).to_track_quat("-Z","Y").to_euler()
    obj.data.lens=lens

view("VIEW_01 | Equipment overview",(4.7,-6.0,3.8),(.27,0,1.22),51)
view("VIEW_02 | Rear services",(5.8,.05,2.75),(1.05,0,1.12),54)
view("VIEW_04 | Front elevation",(.27,-7,2.3),(.27,0,1.24),55)
for name,matrix in fixed.items():
    assert scene.objects[name].matrix_basis==matrix,"Unexpected rig or plant movement: "+name
scene["side_service_layout"]="X positive short end; tank at negative Y, cabinet at positive Y, door outward"
scene["side_service_backup"]=str(backup)
scene["dimension_note"]="Estimated frame 2.40 x 1.44 m; short-end service platform extends 0.60 m along +X."
scene.camera=scene.objects["VIEW_01 | Equipment overview"]
sync.apply_pose(scene)
bpy.context.view_layer.update()
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/"JHDS_Inspection_Gantry.blend"))
print(json.dumps({"corrected":"short-end side platform","tank":list(tank.location),"cabinet":list(cab.location),"tray":list(tray.location),"backup":str(backup),"mode":scene.device_twin.mode,"objects":len(scene.objects)}))
