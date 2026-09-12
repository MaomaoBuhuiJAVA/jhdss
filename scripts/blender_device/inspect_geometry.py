import bpy
import json
from mathutils import Vector
records=[]
for o in bpy.context.scene.objects:
    if o.type != "MESH" or "Ground" in o.name:
        continue
    corners=[o.matrix_world @ Vector(p) for p in o.bound_box]
    top=max(p.z for p in corners)
    if top>2.9:
        records.append({"name":o.name,"z_max":round(top,3),"dimensions":list(o.dimensions),"parent":o.parent.name if o.parent else None,"location":list(o.location),"quaternion":list(o.rotation_quaternion)})
print(json.dumps(records,indent=2))
print("ACTIONS",[(a.name,a.users,a.use_fake_user) for a in bpy.data.actions])
