"""Check reference packaging, genuine holes, rig parenting and refined surfaces."""
import bpy
import json
import math
from pathlib import Path
from mathutils import Vector

scene=bpy.context.scene
passed=[]
def check(condition,name):
    if not condition:
        raise AssertionError(name)
    passed.append(name)

bracket=scene.objects["Camera bracket vertical back"]
check(bracket.get("through_slots")==4,"Camera bracket has four applied capsule cuts")
evaluated=bracket.evaluated_get(bpy.context.evaluated_depsgraph_get())
for z in [2.319,2.366,2.413,2.460]:
    hit,*_=evaluated.ray_cast(Vector((-.10,0,z-bracket.location.z)),Vector((1,0,0)))
    check(not hit,"Ray passes through bracket slot at %.3f"%z)
hit,*_=evaluated.ray_cast(Vector((-.10,0,2.340-bracket.location.z)),Vector((1,0,0)))
check(hit,"Bracket remains solid between the slots")
check(scene.objects["Nozzle slotted steel strap"].get("through_slots")==4,"Nozzle strap has four genuine through holes")
cups=[o for o in scene.objects if o.get("hollow_shell")]
check(len(cups)==3,"Three separately modelled hollow anemometer cups")
check("Rain gauge black funnel rim" in scene.objects,"Rain gauge has an open funnel and cylinder")
check(scene.objects["Integrated weather station body"].matrix_world.translation.x>1.30,"Weather station is mounted outside the post")
check(len([o for o in scene.objects if "chain_cable_offset" in o])==6,"All three chains contain two animated cable paths")
blue=bpy.data.materials["Non-emissive blue pneumatic collet"]
check(blue.node_tree.nodes.get("Principled BSDF").inputs["Emission Strength"].default_value==0,"Blue nozzle fittings are non-emissive")
check(scene.objects["Black camera lens face"].parent.name=="CAMERA_TILT","Curved optical face follows the tilt joint")
check(scene.objects["INSPECTION_CAMERA"].parent.name=="CAMERA_TILT","Actual Blender camera follows the same optical joint")
check(scene.objects["Orange carriage service loop"].parent.name=="CARRIAGE_Y","Spray hose follows the carriage independently of PTZ")
check(scene.objects["Nozzle slotted steel strap"].parent.name=="CARRIAGE_Y","Spray bracket is attached to the moving carriage")
check(abs(scene.objects["White gimbal ball"].scale.x-scene.objects["White gimbal ball"].scale.z)<1e-6,"Camera globe preserves a spherical body")
check(len(scene.objects["Black camera lens face"].data.vertices)>2000,"Optical window is a smooth curved surface")
check(scene.objects["White gimbal ball"].get("recessed_lens_aperture"),"Lens aperture is cut into the globe rather than sitting on its surface")
check(scene.objects["Blue coated optical lens"].location.y>-.079,"Optical glass is recessed behind the spherical front")
shield=scene.objects["Weather shield core"]
check(shield.location.x<-.07 and shield.location.y>.05,"Radiation shield is offset clear of the folded support and post")
check(abs(scene.objects["CABINET_DOOR_HINGE"].rotation_euler.z)>math.radians(5),"Cabinet door is slightly ajar")
check(len([o for o in scene.objects if "straight-sided weathered pot" in o.name])==12,"Twelve near-straight pots preserve the photographed grid")
check("Scattered compost on white deck" in scene.objects,"Deck has sparse compost rather than a pristine surface")
refs=[o for o in scene.objects if o.name.startswith("PHOTO_")]
check(len(refs)==11 and all(o.data.packed_file for o in refs),"All eleven source photographs are packed into the blend")
check(not any(o.name.startswith("TEMP") for o in scene.objects),"No temporary boolean cutters remain in the scene")
check(all(all(math.isfinite(v) for row in o.matrix_world for v in row) for o in scene.objects),"All object transforms are finite")
report={"passed":len(passed),"checks":passed,"model_objects":len(scene.objects),"source_photos":len(refs)}
out=Path(__file__).resolve().parents[2]/"artifacts"/"blender-device"/"supplement_verification.json"
out.write_text(json.dumps(report,indent=2),encoding="utf-8")
print(json.dumps(report,indent=2),flush=True)
