"""Replace only nursery vegetation, with reproducible photo-review passes.

Run from the preserved source blend with -- --pass 0, 1, or 2.
All non-vegetation objects and their geometry are fingerprinted before/after.
"""
import argparse
from array import array
import hashlib
import json
import math
from pathlib import Path
import random
import re
import sys

import bpy
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "artifacts/blender-device"
REVIEW = OUT / "revisions/new-plants"
REVIEW.mkdir(parents=True, exist_ok=True)
parser = argparse.ArgumentParser()
parser.add_argument("--pass", type=int, default=0, choices=(0, 1, 2), dest="detail")
args = parser.parse_args(sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else [])
PASS = args.detail
scene = bpy.context.scene
SOIL = .227
OLD_PREFIXES = (
    "Main woody stem", "Irregular curled and torn foliage", "Drooping leaf midrib",
    "Irregular drooping branch", "Flower stamen", "Flower green yellow heart",
    "Layered irregular white blossoms", "Upright flowering spray", "Blossom pedicel",
    "Fruit stalk", "Small ripening fruit",
)
pots = sorted((o for o in scene.objects if re.fullmatch(r"P-\d\d", o.name)), key=lambda o: o.name)
assert len(pots) == 12
old = {o for p in pots for o in p.children_recursive if o.name.startswith(OLD_PREFIXES)}
assert len(old) > 1000, "Expected the unmodified source vegetation; refusing ambiguous input"


def digest_object(obj):
    h = hashlib.sha256()
    h.update(str((obj.type, obj.parent.name if obj.parent else None,
                  [tuple(row) for row in obj.matrix_world],
                  [tuple(row) for row in obj.matrix_local],
                  obj.hide_render, obj.hide_viewport,
                  sorted(c.name for c in obj.users_collection),
                  obj.data.name if obj.data else None,
                  str(obj.items()))).encode())
    if obj.type == "MESH":
        for items, prop, width, dtype in ((obj.data.vertices, "co", 3, "f"),
                                        (obj.data.loops, "vertex_index", 1, "i")):
            buffer = array(dtype, [0]) * (len(items) * width)
            items.foreach_get(prop, buffer)
            h.update(buffer.tobytes())
        h.update(str([(p.loop_start, p.loop_total, p.material_index) for p in obj.data.polygons]).encode())
    elif obj.type == "CURVE":
        for spline in obj.data.splines:
            h.update(str((spline.type, spline.use_cyclic_u,
                          [(tuple(p.co), p.radius) for p in spline.points],
                          [(tuple(p.co), tuple(p.handle_left), tuple(p.handle_right), p.radius)
                           for p in spline.bezier_points])).encode())
    if obj.data and hasattr(obj.data, "materials"):
        h.update(str([m.name if m else None for m in obj.data.materials]).encode())
    h.update(str([(m.name, m.type) for m in obj.modifiers]).encode())
    return h.hexdigest()


bpy.context.view_layer.update()
preserved = {o.name: digest_object(o) for o in scene.objects if o not in old}
old_names = sorted(o.name for o in old)
for obj in old:
    bpy.data.objects.remove(obj, do_unlink=True)
collection = bpy.data.collections.new("04A | Photo-derived nursery plants 20260912")
scene.collection.children.link(collection)


def material(name, color, roughness, kind):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    mat.diffuse_color = (*color, 1)
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    bsdf = nodes.get("Principled BSDF")
    bsdf.inputs["Base Color"].default_value = (*color, 1)
    bsdf.inputs["Roughness"].default_value = roughness
    attr = nodes.new("ShaderNodeVertexColor")
    attr.layer_name = "PlantColor"
    links.new(attr.outputs["Color"], bsdf.inputs["Base Color"])
    if kind == "leaf":
        bsdf.inputs["Subsurface Weight"].default_value = .055
        bsdf.inputs["Subsurface Radius"].default_value = (.06, .13, .025)
        bsdf.inputs["Coat Weight"].default_value = .035 if PASS else .09
        bsdf.inputs["Coat Roughness"].default_value = .44
        bsdf.inputs["Roughness"].default_value = .64 if PASS else roughness
    if kind == "fruit":
        bsdf.inputs["Coat Weight"].default_value = .24
        bsdf.inputs["Coat Roughness"].default_value = .22
    if PASS > 0:
        noise = nodes.new("ShaderNodeTexNoise")
        noise.inputs["Scale"].default_value = 220 if kind == "bark" else 145
        noise.inputs["Detail"].default_value = 3
        bump = nodes.new("ShaderNodeBump")
        bump.inputs["Strength"].default_value = .28 if kind == "bark" else .12
        bump.inputs["Distance"].default_value = .0012 if kind == "bark" else .00032
        links.new(noise.outputs["Fac"], bump.inputs["Height"])
        links.new(bump.outputs["Normal"], bsdf.inputs["Normal"])
        if PASS == 2 and kind == "leaf":
            coords = nodes.new("ShaderNodeTexCoord")
            links.new(coords.outputs["UV"], noise.inputs["Vector"])
            noise.inputs["Scale"].default_value = 78
            cells = nodes.new("ShaderNodeTexVoronoi")
            cells.feature = "DISTANCE_TO_EDGE"
            cells.inputs["Scale"].default_value = 34
            links.new(coords.outputs["UV"], cells.inputs["Vector"])
            micro = nodes.new("ShaderNodeBump")
            micro.inputs["Strength"].default_value = .22
            micro.inputs["Distance"].default_value = .00028
            links.new(cells.outputs["Distance"], micro.inputs["Height"])
            links.new(bump.outputs["Normal"], micro.inputs["Normal"])
            links.new(micro.outputs["Normal"], bsdf.inputs["Normal"])
    return mat


MATS = {
    "bark": material("NewPlants | Grey brown fissured bark", (.20, .17, .11), .88, "bark"),
    "leaf": material("NewPlants | Corrugated broad leaves", (.041, .10, .017), .48, "leaf"),
    "vein": material("NewPlants | Veins petioles and new shoots", (.085, .15, .025), .65, "leaf"),
    "fruit": material("NewPlants | Red amber ripening fruit", (.46, .055, .008), .28, "fruit"),
    "detail": material("NewPlants | Buds and bark lenticels", (.22, .23, .10), .75, "bark"),
}


class Batch:
    def __init__(self, name, mat, parent):
        self.name, self.mat, self.parent = name, mat, parent
        self.verts, self.faces, self.colors = [], [], []
        self.uvs = []

    def vertex(self, point, color, uv=(0, 0)):
        self.verts.append(tuple(point))
        self.colors.append((*color[:3], 1))
        self.uvs.append(uv)
        return len(self.verts) - 1

    def tube(self, points, radii, color, sides=7):
        points = [Vector(p) for p in points]
        offset = len(self.verts)
        for j, point in enumerate(points):
            tangent = (points[min(j+1, len(points)-1)] - points[max(0, j-1)]).normalized()
            cross = tangent.cross(Vector((0, 1, 0))).normalized()
            if cross.length < .1:
                cross = tangent.cross(Vector((1, 0, 0))).normalized()
            up = tangent.cross(cross).normalized()
            for k in range(sides):
                theta = k * math.tau / sides
                variation = 1 + .05 * math.sin(k * 2.7 + j * .71)
                radius = radii[j] * variation
                shade = 1 + .12 * math.sin(k * 4.1 + j * 1.7)
                self.vertex(point + radius * (cross*math.cos(theta)+up*math.sin(theta)),
                            [min(1, v*shade) for v in color])
        self.faces.append(tuple(offset+k for k in reversed(range(sides))))
        for j in range(len(points)-1):
            for k in range(sides):
                a = offset + j*sides + k
                b = offset + j*sides + (k+1) % sides
                self.faces.append((a, b, b+sides, a+sides))
        self.faces.append(tuple(offset+(len(points)-1)*sides+k for k in range(sides)))

    def finish(self):
        if not self.verts:
            return None
        mesh = bpy.data.meshes.new(self.name)
        mesh.from_pydata(self.verts, [], self.faces)
        mesh.materials.append(self.mat)
        mesh.update()
        colors = mesh.color_attributes.new(name="PlantColor", type="BYTE_COLOR", domain="POINT")
        colors.data.foreach_set("color", [v for c in self.colors for v in c])
        if self.mat == MATS["leaf"]:
            uv = mesh.uv_layers.new(name="LeafUV")
            uv.data.foreach_set("uv", [v for loop in mesh.loops for v in self.uvs[loop.vertex_index]])
        for face in mesh.polygons:
            face.use_smooth = True
        obj = bpy.data.objects.new(self.name, mesh)
        collection.objects.link(obj)
        obj.parent = self.parent
        obj["photo_vegetation"] = True
        obj["detail_pass"] = PASS
        return obj


def curve(a, b, c, d, steps=12):
    a, b, c, d = map(Vector, (a, b, c, d))
    return [a*(1-t)**3 + b*3*(1-t)**2*t + c*3*(1-t)*t*t + d*t**3
            for t in (j/steps for j in range(steps+1))]


def sample(points, t):
    f = min(len(points)-1.000001, max(0, t*(len(points)-1)))
    index = int(f)
    return points[index].lerp(points[index+1], f-index)


def leaf(batches, origin, heading, length, halfwidth, tilt, roll, seed, young=False):
    rng = random.Random(seed)
    origin = Vector(origin)
    along = Vector((math.cos(heading)*math.cos(tilt), math.sin(heading)*math.cos(tilt), math.sin(tilt)))
    side = Vector((-math.sin(heading), math.cos(heading), 0))
    normal = along.cross(side)
    side, normal = side*math.cos(roll)+normal*math.sin(roll), normal*math.cos(roll)-side*math.sin(roll)
    phase = rng.uniform(0, math.tau)
    droop = rng.uniform(.17, .38) if PASS else .13
    skew = rng.uniform(-.09, .09)
    rows, cols = ((14, 5) if PASS == 0 else (24, 7) if PASS == 1 else (30, 9))
    green = ((.12, .235, .027) if young else rng.choice([
        (.014, .037, .005), (.025, .053, .009), (.036, .078, .013),
        (.021, .048, .006), (.048, .082, .014)]))

    def point(t, s):
        profile = math.sin(math.pi*t)**.73 * (1.13-.43*t)
        serration = 1 + ((.021 if PASS == 2 else .038)*math.cos(t*math.pi*rows) if PASS else 0)
        width = halfwidth*profile*serration*(1+skew*s)
        ridge = halfwidth*profile*(-.15*abs(s)+.10*s*s)
        wrinkle = (halfwidth*(.042 if PASS == 2 else .07)*math.sin(2*math.pi*(t*7.5-abs(s)*.38)+phase)*abs(s)**.7
                   if PASS else 0)
        if PASS == 2:
            wrinkle += halfwidth*.027*math.sin(t*37+s*12+phase)*math.sin(t*14-s*9)*abs(s)
        curl = (halfwidth*.15*math.sin(t*5+phase)*abs(s)**3 if PASS == 2 else 0)
        return origin + along*(t*length) + side*(s*width+skew*length*t*t) + normal*(
            length*(.13*math.sin(math.pi*t)-droop*t*t)+ridge+wrinkle+curl)

    batch = batches["leaf"]
    offset = len(batch.verts)
    for j in range(rows+1):
        t = j/rows
        for k in range(cols):
            s = 2*k/(cols-1)-1
            shade = (.85+.19*math.sin(t*11+phase)+.10*(1-abs(s)))
            shade += .08*math.cos((t*7.5-abs(s)*.38)*math.tau) if PASS else 0
            color = [v*shade for v in green]
            if abs(s) < .01:
                color = [color[0]*1.19, color[1]*1.15, color[2]*1.15]
            batch.vertex(point(t, s), color, (t, (s+1)*.5))
    for j in range(rows):
        for k in range(cols-1):
            # Sparse, small edge damage, not the uniformly shredded old leaves.
            if PASS == 2 and seed % 17 == 0 and j in (rows//2, rows//2+1) and k == 0:
                continue
            n = offset+j*cols+k
            batch.faces.append((n, n+cols, n+cols+1, n+1))
    if PASS:
        veins = batches["vein"]
        color = (green[0]*1.27, green[1]*1.20, green[2]*1.25)
        mid = [point(t, 0)+normal*.00035 for t in [0, .18, .38, .58, .78, .96]]
        veins.tube(mid, [.00065, .00058, .00048, .00036, .00023, .00008], color, 5)
        if PASS == 2:
            for t in [.17, .29, .41, .53, .65, .77]:
                for sign in [-1, 1]:
                    line = [point(t+q*.105, sign*q*.88)+normal*.00024 for q in [0, .36, .7, 1]]
                    veins.tube(line, [.00025, .00022, .00015, .000045], color, 3)


def fruit(batches, center, radius, seed, ripe):
    rng = random.Random(seed)
    center = Vector(center)
    batch = batches["fruit"]
    offset = len(batch.verts)
    segments, rings = (16, 10) if PASS == 0 else (24, 16)
    angle = rng.uniform(0, math.tau)
    red = rng.choice([(.43, .007, .003), (.58, .018, .005), (.34, .006, .008)])
    amber = (.90, .32, .008)
    for j in range(rings+1):
        theta = j/rings*math.pi
        st, ct = math.sin(theta), math.cos(theta)
        for k in range(segments):
            phi = k/segments*math.tau
            lobe = 1 + (.042*math.cos(phi*5+angle)*st**.4 if PASS else 0)
            z = radius*(.86*ct-.12*math.exp(-(theta/.31)**2)+.08*math.exp(-((theta-math.pi)/.34)**2))
            p = center+Vector((radius*st*math.cos(phi)*lobe, radius*st*math.sin(phi)*lobe, z))
            yellow = max(0, min(1, (ct-(.62-ripe*.9)+.25*math.sin(phi*3+angle))/.46))
            color = tuple(red[i]*(1-yellow)+amber[i]*yellow for i in range(3))
            if ripe > 1.1:
                color = (.13+.06*ct, .24+.05*ct, .021)
            batch.vertex(p, color)
    for j in range(rings):
        for k in range(segments):
            n = offset+j*segments+k
            other = offset+j*segments+(k+1)%segments
            batch.faces.append((n, n+segments, other+segments, other))
    top = center+Vector((0, 0, radius*.74))
    if PASS:
        for k in range(5):
            a = angle+k*math.tau/5
            tip = top+Vector((.0045*math.cos(a), .0045*math.sin(a), .002))
            batches["vein"].tube([top, tip], [.0013, .00008], (.055, .095, .012), 5)
        bottom = center-Vector((0, 0, radius*.78))
        batches["detail"].tube([bottom, bottom-Vector((0, 0, .0007))], [.0012, .0007], (.065, .038, .014), 6)
    return top


# Photo-derived relative silhouettes; no physical measurements or cultivar claim.
HEIGHTS = ([.87, 1.12, 1.35, 1.57, 1.08, 1.31, 1.46, 1.53, .94, 1.17, 1.39, 1.51]
           if PASS == 0 else [.88, 1.30, 1.13, 1.57, 1.04, 1.24, 1.43, 1.38, .87, 1.46, 1.22, 1.53])
FRUITING = {3: 2, 4: 8, 7: 4, 8: 7, 11: 1, 12: 9}
records = []
for index, (pot, height) in enumerate(zip(pots, HEIGHTS), 1):
    rng = random.Random(91200+index)
    root = bpy.data.objects.new("NEW_PLANT_"+pot.name, None)
    collection.objects.link(root)
    root.parent = pot
    root.location = (0, 0, SOIL)
    root.empty_display_size = .03
    root["plant_id"] = pot.name
    root["source"] = "2026-09-12 supplied photographs; dimensions estimated"
    root["detail_pass"] = PASS
    batches = {kind: Batch(pot.name+" | New "+kind, mat, root) for kind, mat in MATS.items()}
    lean = Vector((rng.uniform(-.10, .10), rng.uniform(-.07, .07), 0))
    phase = rng.uniform(0, math.tau)
    trunk = [Vector((lean.x*t+.012*math.sin(t*6+phase)*t,
                     lean.y*t+.010*math.sin(t*8)*t, height*t)) for t in (j/22 for j in range(23))]
    base_radius = rng.uniform(.0095, .014)
    bark_color = rng.choice([(.21, .172, .121), (.18, .155, .116), (.26, .226, .17)])
    batches["bark"].tube(trunk, [base_radius*(1-j/24)**1.32+.0006 for j in range(23)], bark_color, 11)
    if PASS == 2:
        # Shallow root flare and sparse bark lenticels remain vegetation-only.
        for k in range(5):
            a = phase+k*math.tau/5
            tip = Vector((.027*math.cos(a), .027*math.sin(a), -.003))
            batches["bark"].tube([tip, trunk[0]+Vector((0, 0, .018)), trunk[1]],
                                 [.0013, .0055, .003], bark_color, 7)
        for k in range(24):
            t = rng.uniform(.04, .49)
            a = rng.uniform(0, math.tau)
            radius = base_radius*(1-t*22/24)**1.32+.0007
            center = sample(trunk, t)+Vector((radius*math.cos(a), radius*math.sin(a), 0))
            tangent = Vector((-math.sin(a), math.cos(a), 0))
            batches["detail"].tube([center-tangent*.0017, center+tangent*.0017], [.00055, .0004], (.29, .26, .19), 5)
    bare_fraction = .30 if index in (4, 8, 12) else rng.uniform(.28, .39)
    branches = []
    branch_count = 10 if height > 1.25 else 8
    for j in range(branch_count):
        t = bare_fraction+(1-bare_fraction)*(j+.13)/branch_count
        if PASS:
            t += rng.uniform(-.019, .019)
        start = sample(trunk, t)
        heading = phase+j*2.399+rng.uniform(-.35, .35)
        span = rng.uniform(.14, .25)*(1-.55*(j/branch_count)**2)
        if index in (4, 8, 12):
            span *= .75
        rise = rng.uniform(.05, .15)*(1-j/branch_count*.45)
        direction = Vector((math.cos(heading), math.sin(heading), 0))
        end = start+direction*span+Vector((0, 0, rise))
        path = curve(start, start+direction*span*.28+Vector((0, 0, .025)),
                     end-direction*span*.22+Vector((0, 0, .025)), end)
        branches.append((path, heading, j))
        radius = (.0032 if j < 4 else .0022)*(height/1.4)**.5
        batches["bark"].tube(path, [radius*(1-k/13)**1.1+.0003 for k in range(13)], bark_color, 7)
        if PASS == 2 and j % 3 == 0:
            split_start = sample(path, .57)
            split_heading = heading+.77
            side = Vector((math.cos(split_heading), math.sin(split_heading), 0))
            end = split_start+side*.092+Vector((0, 0, .057))
            shoot = curve(split_start, split_start+side*.032, end-Vector((0, 0, .027)), end, 7)
            batches["vein"].tube(shoot, [.00135*(1-k/9)+.0001 for k in range(8)], (.081, .112, .027), 6)
            branches.append((shoot, split_heading, j+30))
    leaf_count = 0
    for path, heading, branch_id in branches:
        count = 4 if branch_id >= 30 else rng.choice([4, 5, 6])
        for k in range(count):
            t = .22+.77*(k+.1)/count
            node = sample(path, t)
            leaf_heading = heading+(-1 if k % 2 else 1)*rng.uniform(.60, 1.25)
            petiole = rng.uniform(.016, .026)
            leaf_origin = node+Vector((math.cos(leaf_heading)*petiole, math.sin(leaf_heading)*petiole, .008))
            batches["vein"].tube([node, node.lerp(leaf_origin, .55)+Vector((0, 0, .005)), leaf_origin],
                                 [.00095, .00075, .00055], (.065, .12, .024), 5)
            young = k == count-1 and (branch_id > 6 or branch_id >= 30)
            length = rng.uniform(.145, .225)*( .56 if young else 1)
            halfwidth = length*rng.uniform(.28, .355)
            if PASS == 0:
                length *= .83
            leaf(batches, leaf_origin, leaf_heading, length, halfwidth,
                 rng.uniform(-.49, .28) if not young else rng.uniform(.15, .55),
                 rng.uniform(-.52, .52), index*10000+leaf_count, young)
            leaf_count += 1
    # A small terminal flush gives the tall stems a growing tip, not a cut end.
    for k in range(4):
        origin = trunk[-1]-Vector((0, 0, k*.014))
        leaf(batches, origin, phase+k*2.4, .060+k*.012, .016+k*.003,
             .38 if k else .80, k*.1, index*10000+900+k, True)
        leaf_count += 1
    fruit_count = 0
    wanted_clusters = FRUITING.get(index, 0)
    main_branches = [b for b in branches if b[2] < 30]
    for cluster in range(wanted_clusters):
        path, heading, branch_id = main_branches[(cluster+1) % len(main_branches)]
        attachment = sample(path, .19 if cluster % 2 else .48)
        junction = attachment+Vector((math.cos(heading)*.018, math.sin(heading)*.018, -.017))
        batches["vein"].tube([attachment, junction], [.0013, .001], (.067, .092, .013), 6)
        for k in range(rng.choice([2, 2, 3])):
            theta = heading+k*2.37+cluster*.44
            radius = rng.uniform(.016, .023)
            spread = .027+k*.007
            center = junction+Vector((math.cos(theta)*spread, math.sin(theta)*spread, -.040-k*.008))
            ripe = rng.choice([.07, .2, .48, .64, .85, 1.2])
            top = fruit(batches, center, radius, index*1000+fruit_count, ripe)
            batches["vein"].tube([junction, junction.lerp(top, .53)+Vector((0, 0, .006)), top],
                                 [.0009, .0007, .00055], (.060, .098, .013), 6)
            fruit_count += 1
    root["height_m"] = height
    root["leaf_count"] = leaf_count
    root["fruit_count"] = fruit_count
    for batch in batches.values():
        batch.finish()
    records.append(dict(id=pot.name, height_m=height, leaves=leaf_count, fruit=fruit_count,
                        base_local=list(root.location)))
    print("PLANT_DONE", pot.name, leaf_count, fruit_count, flush=True)

bpy.context.view_layer.update()
changed = [name for name, before in preserved.items()
           if name not in bpy.data.objects or digest_object(bpy.data.objects[name]) != before]
assert not changed, "Non-plant objects changed: "+str(changed)
for record in records:
    obj = bpy.data.objects["NEW_PLANT_"+record["id"]]
    assert obj.parent == bpy.data.objects[record["id"]]
    assert abs(obj.location.z-SOIL) < 1e-6 and obj.location.xy.length < 1e-6
plant_meshes = [o for o in collection.objects if o.type == "MESH"]
triangles = sum(sum(len(p.vertices)-2 for p in o.data.polygons) for o in plant_meshes)
bounds = [o.matrix_world @ Vector(corner) for o in plant_meshes for corner in o.bound_box]
report = dict(detail_pass=PASS, preserved_object_count=len(preserved),
              changed_non_plant_objects=changed, removed_vegetation_objects=len(old_names),
              plants=records, plant_mesh_objects=len(plant_meshes), plant_triangles=triangles,
              plant_bounds=[[min(p[i] for p in bounds) for i in range(3)],
                            [max(p[i] for p in bounds) for i in range(3)]],
              drip_loops=sum(o.name.startswith("Clear micro irrigation loop") for o in scene.objects),
              original_object_fingerprints=preserved)
assert report["drip_loops"] == 12
assert report["plant_bounds"][1][2] < 2.17, "Foliage exceeds safe clearance below the overhead frame"
assert triangles < 800000
(REVIEW/f"pass{PASS}_verification.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
output = REVIEW/f"NewPlants_Pass{PASS}.blend" if PASS < 2 else OUT/"JHDS_Inspection_Gantry_NewPlants_Final.blend"
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print("PLANT_REBUILD_COMPLETE", json.dumps({k:v for k,v in report.items() if k != "original_object_fingerprints"}), flush=True)
