"""Assert side-platform footprint, spacing, outward door and stable articulated rig."""
import bpy
import json
import math
from pathlib import Path
from mathutils import Vector

scene=bpy.context.scene
bpy.context.view_layer.update()
checks=[]
def check(value,name):
    if not value:
        raise AssertionError(name)
    checks.append(name)

def bounds(objects):
    points=[o.matrix_world@Vector(v) for o in objects if o.type in {"MESH","CURVE","FONT"} for v in o.bound_box]
    return [[min(p[i] for p in points),max(p[i] for p in points)] for i in range(3)]

assemblies={}
for name in ["RESERVOIR","CONTROL_CABINET","OPEN_UTILITY_BASKET"]:
    group=scene.objects[name]
    box=bounds(group.children_recursive)
    assemblies[name]=box
    check(box[0][0]>1.23 and box[0][1]<1.80,name+" is entirely on the outside short-end platform")
    check(box[1][0]>-.72 and box[1][1]<.72,name+" fits between the side-platform rails")
    check(box[2][0]>.212,name+" does not intersect the shelf")
for a,b in [("RESERVOIR","OPEN_UTILITY_BASKET"),("OPEN_UTILITY_BASKET","CONTROL_CABINET")]:
    check(assemblies[a][1][1]<assemblies[b][1][0],a+" and "+b+" have a clear gap")
cab=scene.objects["CONTROL_CABINET"]
normal=cab.matrix_world.to_quaternion()@Vector((0,-1,0))
check(normal.x>.99,"Cabinet door faces outward along positive X")
shelf=bounds([scene.objects["White service shelf"]])
check(shelf[0][0]>1.20 and shelf[1][1]<.72,"No service shelf remains along the long front or rear edge")
pots=[o for o in scene.objects if o.type=="EMPTY" and o.name.startswith("P-")]
check(len(pots)==12 and all(abs(o.location.x)<1 and abs(o.location.y)<.5 for o in pots),"All twelve pots retain their growing-deck positions")
check(scene.objects["DIAPHRAGM_PUMP"].location.x>1.2 and scene.objects["DIAPHRAGM_PUMP"].location.y<-.7,"Pump remains at the reservoir-side corner")
for name in ["Camera bracket vertical back","Orange carriage service loop","Nozzle slotted steel strap"]:
    check(scene.objects[name].parent.name=="CARRIAGE_Y",name+" retains its moving-carriage parent")
report={"passed":len(checks),"checks":checks,"assembly_world_bounds":assemblies,"shelf_bounds":shelf,"layout":scene.get("side_service_layout")}
out=Path(__file__).resolve().parents[2]/"artifacts"/"blender-device"/"side_service_verification.json"
out.write_text(json.dumps(report,indent=2),encoding="utf-8")
print(json.dumps(report,indent=2),flush=True)
