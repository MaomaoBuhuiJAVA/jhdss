"""Export the corrected source blend without saving or altering the source file."""
import json
import math
import argparse
import sys
from collections import defaultdict
from pathlib import Path

import bpy
import bmesh
from mathutils import Matrix

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--dest', type=Path, default=ROOT / 'src/main/resources/static/models')
options = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
DEST = options.dest
DEST.mkdir(parents=True, exist_ok=True)
source = bpy.context.scene
rig_names = ['DEVICE_ROOT', 'GANTRY_X', 'CARRIAGE_Y', 'CAMERA_PAN', 'CAMERA_TILT']
rigs = {name: bpy.data.objects[name] for name in rig_names}
rigs['GANTRY_X'].location.x = 0
rigs['CARRIAGE_Y'].location.y = 0
rigs['CAMERA_PAN'].rotation_euler.z = 0
rigs['CAMERA_TILT'].rotation_euler.x = 0
bpy.context.view_layer.update()
export_scene = bpy.data.scenes.new('Web export')
export_rigs = {}
for name, original in rigs.items():
    obj = bpy.data.objects.new('WEB_' + name, None)
    export_scene.collection.objects.link(obj)
    if original.parent and original.parent.name in export_rigs:
        obj.parent = export_rigs[original.parent.name]
    obj.matrix_local = original.matrix_local.copy()
    export_rigs[name] = obj

def anchor(name, parent, location):
    obj = bpy.data.objects.new(name, None)
    export_scene.collection.objects.link(obj)
    obj.parent = export_rigs[parent]
    obj.location = location
    return obj

anchor('FX_FOLIAR', 'CARRIAGE_Y', (.230, .04, 1.9208))
for i in range(1, 13):
    pot = bpy.data.objects['P-%02d' % i]
    p = pot.matrix_world.translation
    anchor('FX_POT_%02d' % i, 'DEVICE_ROOT', (p.x, p.y, p.z + .23))

# Keep one chain link mesh per axis; the browser instances and bends the links.
chains = []
chain_objects = set()
for name in ['CHAIN_X_FRONT', 'CHAIN_X_REAR', 'CHAIN_Y']:
    container = bpy.data.objects[name]
    chain_objects.update(o.name for o in container.children_recursive)
    links = [o for o in container.children if 'chain_t' in o]
    chains.append(dict(name=name, parent=container.parent.name,
                       **{k: container[k] for k in ['axis', 'fixed', 'length', 'radius', 'base_z', 'side']},
                       fractions=[o['chain_t'] for o in links]))
    link = links[0].copy()
    link.data = links[0].data.copy()
    link.name = 'WEB_TEMPLATE_' + name
    link.parent = None
    link.matrix_world = Matrix.Identity(4)
    export_scene.collection.objects.link(link)

groups = defaultdict(lambda: [[], [], []])
# Sub-centimetre flower hearts used 4.2 million polygons in the render model.
# Their silhouette needs only an icosphere at web viewing distances.
heart_mesh = bpy.data.meshes.new('Web flower heart')
bm = bmesh.new()
bmesh.ops.create_icosphere(bm, subdivisions=1, radius=1)
bm.to_mesh(heart_mesh)
bm.free()
heart_mesh.materials.append(bpy.data.materials['Yellow-green flower centres'])
for original in list(source.objects):
    if original.type == 'MESH' and original.active_material and original.active_material.name == 'Yellow-green flower centres':
        original.data = heart_mesh
    if original.type == 'CURVE':
        original.data.resolution_u = min(original.data.resolution_u, 8)
        original.data.bevel_resolution = min(original.data.bevel_resolution, 2)
bpy.context.view_layer.update()
depsgraph = bpy.context.evaluated_depsgraph_get()
objects = [o for c in bpy.data.collections if c.name[:2] in ['01', '02', '03', '04', '05'] for o in c.objects]
for original in objects:
    if original.type not in {'MESH', 'CURVE', 'FONT', 'SURFACE'} or original.name in chain_objects:
        continue
    owner = original.parent
    while owner and owner.name not in rigs:
        owner = owner.parent
    owner_name = owner.name if owner else 'DEVICE_ROOT'
    transform = rigs[owner_name].matrix_world.inverted() @ original.matrix_world
    evaluated = original.evaluated_get(depsgraph)
    mesh = evaluated.to_mesh()
    if not mesh:
        continue
    by_material = defaultdict(list)
    for polygon in mesh.polygons:
        index = min(polygon.material_index, max(0, len(mesh.materials)-1))
        material = mesh.materials[index] if mesh.materials else bpy.data.materials[0]
        by_material[material.name].append(polygon)
    for material, polygons in by_material.items():
        vertices, faces, colors = groups[(owner_name, material)]
        plant_colors = mesh.color_attributes.get('PlantColor') if material.startswith('NewPlants |') else None
        remap = {}
        for polygon in polygons:
            face = []
            for vi in polygon.vertices:
                if vi not in remap:
                    remap[vi] = len(vertices)
                    vertices.append(tuple(transform @ mesh.vertices[vi].co))
                    if plant_colors:
                        colors.append(tuple(plant_colors.data[vi].color))
                face.append(remap[vi])
            faces.append(face)
    evaluated.to_mesh_clear()

for (owner, material), (vertices, faces, colors) in groups.items():
    mesh = bpy.data.meshes.new('Web_' + material)
    mesh.from_pydata(vertices, [], faces)
    mesh.materials.append(bpy.data.materials[material])
    mesh.update()
    if colors:
        assert len(colors) == len(vertices)
        attr = mesh.color_attributes.new(name='PlantColor', type='BYTE_COLOR', domain='POINT')
        attr.data.foreach_set('color', [value for color in colors for value in color])
    obj = bpy.data.objects.new(owner + '_' + material, mesh)
    export_scene.collection.objects.link(obj)
    obj.parent = export_rigs[owner]
    if material.startswith('NewPlants |'):
        for poly in mesh.polygons:
            poly.use_smooth = True
        if 'leaves' in material or 'Veins' in material:
            modifier = obj.modifiers.new('Web nursery detail reduction', 'DECIMATE')
            modifier.ratio = .55 if 'leaves' in material else .48
    elif 'foliage' in material.lower():
        for poly in mesh.polygons:
            poly.use_smooth = True
        modifier = obj.modifiers.new('Web foliage reduction', 'DECIMATE')
        modifier.ratio = .55
    elif material in ['Ivory flower petals', 'Woody plant stems', 'Fruit ochre', 'Fruit red']:
        for poly in mesh.polygons:
            poly.use_smooth = True
        modifier = obj.modifiers.new('Web botanical reduction', 'DECIMATE')
        modifier.ratio = .22

# Procedural Blender nodes are not supported by glTF. Use the authored base
# colors and physical metal/roughness settings, leaving the blend untouched.
for mat in bpy.data.materials:
    if not mat.use_nodes:
        continue
    bsdf = next((n for n in mat.node_tree.nodes if n.type == 'BSDF_PRINCIPLED'), None)
    if not bsdf:
        continue
    for socket in ['Base Color', 'Roughness', 'Metallic', 'Normal']:
        if socket == 'Base Color' and mat.name.startswith('NewPlants |'):
            continue
        for link in list(bsdf.inputs[socket].links):
            mat.node_tree.links.remove(link)
    # COLOR_0 carries botanical color variation, including the red/amber fruit.
    bsdf.inputs['Base Color'].default_value = (1, 1, 1, 1) if mat.name.startswith('NewPlants |') else mat.diffuse_color
    bsdf.inputs['Transmission Weight'].default_value = 0

bpy.context.window.scene = export_scene
bpy.ops.export_scene.gltf(filepath=str(DEST / 'inspection-gantry.glb'),
    export_format='GLB', use_active_scene=True, export_apply=True,
    export_animations=False, export_cameras=False, export_lights=False,
    export_yup=False, export_extras=False, export_texcoords=False,
    export_draco_mesh_compression_enable=True, export_draco_mesh_compression_level=6)
report = dict(source=Path(bpy.data.filepath).name, axis='Z_UP',
              xRange=[-1.03, 1.03], yRange=[-.57, .57], chains=chains,
              meshGroups=len(groups), bytes=(DEST / 'inspection-gantry.glb').stat().st_size)
(DEST / 'inspection-gantry.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
print('WEB_EXPORT', json.dumps(report))
