import bpy
import math
from mathutils import Vector
scene=bpy.context.scene
mechanics=bpy.data.collections["02 | Linear motion and cable chains"]
black=bpy.data.materials["Black anodised metal"]
yellow=bpy.data.materials["Motor yellow vent cover"]
housing=scene.objects["White camera upper housing"]
housing.scale.z=.035/.075
housing.location.z=-.050


def move_collection(obj):
    for c in list(obj.users_collection):
        c.objects.unlink(obj)
    mechanics.objects.link(obj)


def cylinder(name,pos,radius,depth,material,parent,axis=(1,0,0)):
    bpy.ops.mesh.primitive_cylinder_add(vertices=32,radius=radius,depth=depth)
    o=bpy.context.object
    o.name=name
    o.parent=parent
    o.location=pos
    o.rotation_mode="QUATERNION"
    o.rotation_quaternion=Vector((0,0,1)).rotation_difference(Vector(axis).normalized())
    o.data.materials.append(material)
    for p in o.data.polygons:
        p.use_smooth=len(p.vertices)==4
    move_collection(o)
    return o


def box(name,pos,size,parent):
    bpy.ops.mesh.primitive_cube_add(size=1)
    o=bpy.context.object
    o.name=name
    o.parent=parent
    o.location=pos
    o.scale=size
    o.data.materials.append(black)
    move_collection(o)
    return o


for cover in [o for o in scene.objects if o.name.startswith("Yellow belt safety cover")]:
    cover.dimensions=(.006,.070,.160)
    cover.location.z=.049
    group=cover.parent
    slots=sorted([o for o in group.children if o.name.startswith("Diagonal ventilation opening")],key=lambda o:o.location.z)
    for i,o in enumerate(slots):
        o.location=(.1245,-.011,.033+i*.012)
        for v in o.data.vertices:
            v.co.y*=.044/.059
    cylinder("Motor cover fan aperture",(.125,-.011,-.006),.024,.001,black,group)
    cylinder("Motor cover fan hub",(.127,-.011,-.006),.007,.001,yellow,group)
    for i in range(8):
        a=i*math.tau/8
        start=Vector((.127,-.011+math.cos(a)*.006,-.006+math.sin(a)*.006))
        end=Vector((.127,-.011+math.cos(a+.18)*.024,-.006+math.sin(a+.18)*.024))
        cylinder("Motor fan grille spoke",(start+end)/2,.0014,(end-start).length,yellow,group,end-start)
for y in [-.785,.785]:
    box("X chain moving end anchor",(0,y,2.22+.265),(.052,.058,.014),scene.objects["GANTRY_X"])
    box("X chain anchor upright",(0,y,2.22+.215),(.012,.03,.10),scene.objects["GANTRY_X"])
box("Y chain moving end anchor",(-.103,0,2.22+.392),(.058,.052,.014),scene.objects["CARRIAGE_Y"])
box("Y chain anchor upright",(-.103,0,2.22+.344),(.03,.012,.09),scene.objects["CARRIAGE_Y"])
bpy.ops.object.select_all(action="DESELECT")
print("Camera casing, fan grilles and moving cable anchors refined:",len(scene.objects))
bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath)
