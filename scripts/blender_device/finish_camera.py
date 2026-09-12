import bpy
import math
from mathutils import Vector
obj=bpy.context.scene.objects["Black camera lens face"]
n=64
verts=[(.05*math.cos(i*math.tau/n),.05*math.sin(i*math.tau/n),z) for z in [-.02,.02] for i in range(n)]
faces=[tuple(reversed(range(n))),tuple(range(n,2*n))]+[(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)]
data=bpy.data.meshes.new("Smooth camera lens bezel")
data.from_pydata(verts,[],faces)
data.materials.append(bpy.data.materials["Black anodised metal"])
for p in data.polygons[2:]:
    p.use_smooth=True
obj.data=data
obj.location=(0,-.058,-.025)
obj.rotation_mode="QUATERNION"
obj.rotation_quaternion=Vector((0,0,1)).rotation_difference(Vector((0,-1,0)))
obj.scale=(1,.9,1)
mod=obj.modifiers.new("Lens bezel edge","BEVEL")
mod.width=.002
mod.segments=3
obj.modifiers.new("Bezel normals","WEIGHTED_NORMAL")
for o in obj.parent.children:
    if o.name.startswith("Infrared illuminator"):
        o.location.y=-.080
    if o.name=="Camera indicator":
        o.location.y=-.079
bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath)
print("Lens bezel finished; live synchronisation remains active")
