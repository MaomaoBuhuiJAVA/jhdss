import bpy
import json
import device_twin_sync as sync
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view
s=bpy.context.scene
result={"file":bpy.data.filepath,"objects":len(s.objects),"addon_version":sync.bl_info["version"],"cameras":[]}
for name in ["VIEW_05 | Camera housing and nozzle","VIEW_07 | Camera rear through slots"]:
    o=s.objects[name]
    points=[world_to_camera_view(s,o,item.matrix_world@Vector(c)) for item in bpy.data.collections["03 | Camera and nozzle"].objects if item.type in {"MESH","CURVE"} for c in item.bound_box]
    result["cameras"].append({"name":name,"lens":o.data.lens,"parent":o.parent.name if o.parent else None,"location":list(o.location),"range_x":[min(p.x for p in points),max(p.x for p in points)],"range_y":[min(p.y for p in points),max(p.y for p in points)],"min_z":min(p.z for p in points)})
print(json.dumps(result))
