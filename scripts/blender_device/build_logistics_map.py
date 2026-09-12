"""Build a performant, data-driven China logistics map in Blender.

The scene is deliberately low-poly and organized by data layer. Every route,
vehicle, hotspot and label has custom properties so a later importer can replace
the sample data without rebuilding the scene by hand.
"""
import bpy
import math
from mathutils import Vector
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / 'artifacts' / 'blender-device' / 'China_Logistics_3D_Map.blend'
PREVIEW = OUT.with_suffix('.png')

# Approximate lon/lat outline, intentionally simplified for interactive use.
CHINA = [(73,39),(78,48),(88,49),(97,47),(106,50),(119,53),(126,49),(132,48),
         (135,44),(134,39),(130,35),(127,31),(124,27),(122,23),(116,21),(110,20),
         (105,21),(101,24),(97,22),(92,27),(88,28),(85,31),(80,31),(76,35)]
CITIES = {
    'Beijing': (116.40,39.90, 'North China hub'),
    'Shanghai': (121.47,31.23, 'East China gateway'),
    'Guangzhou': (113.26,23.13, 'South China hub'),
    'Shenzhen': (114.06,22.54, 'Cross-border gateway'),
    'Chengdu': (104.07,30.67, 'West China hub'),
    'Wuhan': (114.30,30.59, 'Central China hub'),
    'XiAn': (108.94,34.34, 'Northwest hub'),
    'Urumqi': (87.62,43.82, 'Silk Road gateway'),
    'Harbin': (126.64,45.75, 'Northeast hub'),
    'Kunming': (102.83,24.88, 'Southwest hub'),
}
ROUTES = [
    ('ORD-1001', 'Beijing', 'Shanghai', 'In transit', 1280, 0x31d7ff),
    ('ORD-1002', 'Shanghai', 'Guangzhou', 'Sorting', 860, 0xffb454),
    ('ORD-1003', 'Guangzhou', 'Chengdu', 'Delivered', 720, 0x6ee7a8),
    ('ORD-1004', 'Urumqi', 'XiAn', 'Delayed', 430, 0xff6b77),
    ('ORD-1005', 'Shenzhen', 'Wuhan', 'In transit', 520, 0x31d7ff),
    ('ORD-1006', 'Harbin', 'Beijing', 'Sorting', 970, 0xffb454),
    ('ORD-1007', 'Kunming', 'Shenzhen', 'Delivered', 690, 0x6ee7a8),
]
HOTSPOTS = [('Shanghai', 1.0, 'High parcel volume'), ('Guangzhou', .82, 'Peak dispatch'),
            ('Beijing', .74, 'Weather watch'), ('Shenzhen', .64, 'Cross-border surge'),
            ('Wuhan', .48, 'Transfer congestion')]

def mat(name, color, emission=0.0, metallic=0.0, rough=.5):
    m = bpy.data.materials.new(name); m.diffuse_color = (*color, 1)
    m.use_nodes = True; bsdf = m.node_tree.nodes.get('Principled BSDF')
    bsdf.inputs['Base Color'].default_value = (*color, 1)
    bsdf.inputs['Roughness'].default_value = rough; bsdf.inputs['Metallic'].default_value = metallic
    if emission:
        bsdf.inputs['Emission Color'].default_value = (*color, 1); bsdf.inputs['Emission Strength'].default_value = emission
    return m

MAT = {
    'ocean': mat('Ocean', (0.015, .07, .12), 0.15),
    'land': mat('China land', (.06, .19, .22), 0.05, .1, .8),
    'border': mat('Border glow', (.17, .75, .78), 2.0),
    'grid': mat('Coordinate grid', (.08, .30, .34), .4),
    'hub': mat('City hub', (1.0, .58, .16), 3.0),
    'route': mat('Route cyan', (.08, .67, 1.0), 3.5),
    'delay': mat('Delay route', (1.0, .16, .28), 3.5),
    'delivered': mat('Delivered route', (.18, .9, .55), 2.5),
    'vehicle': mat('Vehicle body', (.92, .95, .96), .1, .6, .28),
    'vehicle_led': mat('Vehicle beacon', (.2, .85, 1.0), 5.0),
    'hotspot': mat('Hotspot heat', (1.0, .20, .08), 4.0),
    'label': mat('Labels', (.85, .96, .98), 2.0),
    'panel': mat('Panel', (.015, .035, .05), .1),
}
def link(obj, col):
    for c in list(obj.users_collection): c.objects.unlink(obj)
    col.objects.link(obj); return obj

def xy(lon, lat):
    return ((lon - 104.0) * .105, (lat - 35.5) * .125)

def curve_obj(name, points, material, bevel=.025, col=None, cyclic=False):
    cu = bpy.data.curves.new(name, 'CURVE'); cu.dimensions = '3D'; cu.resolution_u = 1
    cu.bevel_depth = bevel; cu.bevel_resolution = 1
    sp = cu.splines.new('POLY'); sp.points.add(len(points)-1)
    for p, co in zip(sp.points, points): p.co = (*co, 1)
    sp.use_cyclic_u = cyclic; obj = bpy.data.objects.new(name, cu); (col or COL['03 | Routes']).objects.link(obj)
    obj.data.materials.append(material); return obj

def text_obj(name, text, location, size=.18, col=None, parent=None):
    cu = bpy.data.curves.new(name, 'FONT'); cu.body = text; cu.align_x = 'CENTER'; cu.align_y = 'CENTER'; cu.size = size; cu.extrude = .004
    obj = bpy.data.objects.new(name, cu); (col or COL['06 | Labels']).objects.link(obj); obj.location = location
    obj.data.materials.append(MAT['label']); obj['label_type'] = 'data-label'; obj['data_value'] = text
    if parent: obj.parent = parent
    return obj

def sphere(name, loc, radius, material, col):
    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=2, radius=radius, location=loc)
    o = bpy.context.object; o.name = name; o.data.materials.append(material); link(o, col); return o

def box(name, loc, scale, material, col):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc); o = bpy.context.object; o.name = name; o.scale = scale; o.data.materials.append(material); link(o, col); return o

# Clean scene, preserving no previous assets in this dedicated output.
bpy.ops.object.select_all(action='SELECT'); bpy.ops.object.delete(use_global=False)
for c in list(bpy.data.collections):
    if c.name != 'Collection': bpy.data.collections.remove(c)
COL = {}
for name in ['00 | Base', '01 | China map', '02 | Cities', '03 | Routes', '04 | Vehicles', '05 | Hotspots', '06 | Labels', '07 | UI panels']:
    COL[name] = bpy.data.collections.new(name); bpy.context.scene.collection.children.link(COL[name])

scene = bpy.context.scene; scene.name = 'China Logistics Digital Twin'; scene.unit_settings.system = 'METRIC'; scene.unit_settings.length_unit = 'KILOMETERS'
scene['scene_type'] = 'china-logistics-map'; scene['data_version'] = 'sample-2026-09'; scene['performance'] = 'low-poly curves + instanced vehicles';
scene['interaction'] = 'select routes, vehicles, hotspots and labels; edit custom properties';
for k, v in [('active_time_min', 0), ('time_window_min', 60), ('selected_layer', 'all')]: scene[k] = v

# Ocean slab and coordinate grid.
box('Ocean base', (0, 0, -.22), (4.7, 3.6, .18), MAT['ocean'], COL['00 | Base'])
for lon in range(75, 136, 5):
    x = xy(lon, 35.5)[0]; curve_obj('lon_%s' % lon, [(x,-2.9,0),(x,2.9,0)], MAT['grid'], .006, COL['00 | Base'])
for lat in range(20, 51, 5):
    y = xy(104, lat)[1]; curve_obj('lat_%s' % lat, [(-3.4,y,0),(3.4,y,0)], MAT['grid'], .006, COL['00 | Base'])

# Filled low-poly land using a fan mesh and glowing outline.
verts = [(xy(*p)[0], xy(*p)[1], 0) for p in CHINA]; center = (sum(v[0] for v in verts)/len(verts), sum(v[1] for v in verts)/len(verts), 0)
mesh = bpy.data.meshes.new('China low poly'); mesh.from_pydata([center]+verts, [], [(0,i+1,(i+1)%len(verts)+1) for i in range(len(verts))]); mesh.materials.append(MAT['land'])
land = bpy.data.objects.new('China mainland', mesh); COL['01 | China map'].objects.link(land); land['geometry'] = 'simplified outline'; land['source'] = 'editable approximate footprint'
curve_obj('China border', [(x,y,.035) for x,y,_ in verts], MAT['border'], .035, COL['01 | China map'], True)

# City hubs and metadata labels.
city_objects = {}
for name, (lon, lat, description) in CITIES.items():
    x,y = xy(lon,lat); hub = sphere('HUB_' + name, (x,y,.11), .105, MAT['hub'], COL['02 | Cities']); hub['city'] = name; hub['longitude'] = lon; hub['latitude'] = lat; hub['description'] = description; city_objects[name] = hub
    # Small radial offsets keep dense east-coast labels readable at overview zoom.
    ox = .16 if x < 0 else -.16; oy = .13 if y < 0 else -.13
    text_obj('LABEL_' + name, name, (x+ox,y+oy,.28), .115)

# Route curves and moving vehicles.
for order, src, dst, status, parcels, color in ROUTES:
    a = CITIES[src]; b = CITIES[dst]; p1 = Vector((*xy(a[0],a[1]),.19)); p2 = Vector((*xy(b[0],b[1]),.19)); mid = (p1+p2)/2; mid.z += .35 + .10*abs(p1.x-p2.x)
    material = MAT['delay'] if status == 'Delayed' else MAT['delivered'] if status == 'Delivered' else MAT['route'] if status == 'In transit' else MAT['hub']
    route = curve_obj('ROUTE_' + order, [p1, mid, p2], material, .035, COL['03 | Routes']); route['order_id']=order; route['origin']=src; route['destination']=dst; route['status']=status; route['parcel_count']=parcels
    label = text_obj('ORDER_' + order, order + ' | ' + status, tuple(mid + Vector((0,0,.13 + .045*(len(city_objects)%3)))), .078); label['order_id']=order; label['status']=status
    # Low-poly courier vehicle, keyed along the path for a 60 minute preview.
    vehicle = box('VEHICLE_' + order, tuple(p1 + Vector((0,0,.14))), (.10,.055,.045), MAT['vehicle'], COL['04 | Vehicles']); vehicle['vehicle_id']='TRK-' + order[-4:]; vehicle['order_id']=order; vehicle['route']=order; vehicle['speed_kmh']=58 if status != 'Delayed' else 18; vehicle['state']=status
    beacon = sphere('BEACON_' + order, tuple(p1 + Vector((0,0,.205))), .022, MAT['vehicle_led'], COL['04 | Vehicles']); beacon.parent = vehicle; beacon.location = (0,0,.07)
    vehicle.keyframe_insert('location', frame=1); vehicle.location = tuple(mid + Vector((0,0,.14))); vehicle.keyframe_insert('location', frame=45); vehicle.location = tuple(p2 + Vector((0,0,.14))); vehicle.keyframe_insert('location', frame=90); vehicle.animation_data.action.frame_range = (1,90)
    # Blender 5.2 stores keyframes in layered actions; the 1..90 preview range
    # is enough for the interactive timeline and avoids version-specific API.

# Hotspot rings and labels, each with a numeric intensity property.
for city, intensity, desc in HOTSPOTS:
    x,y = xy(CITIES[city][0], CITIES[city][1]); ring = curve_obj('HOTSPOT_' + city, [(x + math.cos(i*math.tau/20)*(.18+intensity*.12), y + math.sin(i*math.tau/20)*(.18+intensity*.12), .08) for i in range(20)], MAT['hotspot'], .022, COL['05 | Hotspots'], True); ring['city']=city; ring['intensity']=intensity; ring['description']=desc
    text_obj('HOT_' + city, 'HOTSPOT %.0f%%' % (intensity*100), (x,y,.25), .09)

# Data legend and KPI panel, selectable as an object group.
panel = box('DATA_LEGEND_PANEL', (3.45, 1.75, .25), (1.0, .7, .05), MAT['panel'], COL['07 | UI panels']); panel['panel_type']='legend'; panel['orders']=len(ROUTES); panel['vehicles']=len(ROUTES); panel['hotspots']=len(HOTSPOTS)
for i, (txt, m) in enumerate([('LIVE ROUTES', MAT['route']),('DELIVERED', MAT['delivered']),('DELAY ALERT', MAT['delay']),('HOTSPOT', MAT['hotspot'])]):
    sphere('LEGEND_DOT_' + str(i), (2.78, 2.18-i*.28, .34), .045, m, COL['07 | UI panels']); text_obj('LEGEND_' + str(i), txt, (3.35,2.18-i*.28,.34), .12, COL['07 | UI panels'])
text_obj('TITLE', 'CHINA LOGISTICS CONTROL MAP', (0, 3.03, .35), .30, COL['07 | UI panels'])
text_obj('SUBTITLE', 'Orders 07   |   Vehicles 07   |   Hotspots 05   |   Demo telemetry', (0, 2.72, .35), .13, COL['07 | UI panels'])

# Camera and lighting tuned for the complete map; Eevee is enough for preview.
scene.frame_start=1; scene.frame_end=90; scene.render.engine='BLENDER_EEVEE'; scene.render.resolution_x=1200; scene.render.resolution_y=850; scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG'; scene.render.filepath=str(PREVIEW); scene.world.color=(.003,.008,.012)
bpy.ops.object.camera_add(location=(0,-6.7,7.6)); cam=bpy.context.object; cam.name='MAP_CAMERA'; scene.camera=cam; cam.data.lens=52
direction = Vector((0,0,.15)) - cam.location; cam.rotation_euler = direction.to_track_quat('-Z','Y').to_euler()
for loc, energy, size, color in [((-2,-2,7),1800,5,(.45,.75,1)),((4,1,6),1300,4,(1,.48,.2)),((0,3,5),700,3,(.2,1,.8))]:
    bpy.ops.object.light_add(type='AREA', location=loc); light=bpy.context.object; light.data.energy=energy; light.data.shape='DISK'; light.data.size=size; light.data.color=color; light.rotation_euler=(Vector((0,0,.15))-light.location).to_track_quat('-Z','Y').to_euler()

# Make route/city layers easy to toggle from Blender's Outliner.
for col in COL.values(): col['layer_visibility']='toggle in Outliner'; col['selectable_data']='true'
bpy.context.scene.frame_set(30); bpy.ops.wm.save_as_mainfile(filepath=str(OUT)); bpy.ops.render.render(write_still=True)
# A lightweight interchange copy is useful for a later website view. Curves,
# labels, custom properties and keyframes are retained by the glTF exporter.
GLB = OUT.with_suffix('.glb')
bpy.ops.export_scene.gltf(filepath=str(GLB), export_format='GLB', export_animations=True,
                          export_cameras=True, export_lights=False, export_yup=False,
                          export_apply=True, export_extras=True)
print('LOGISTICS_MAP_READY', OUT, PREVIEW)
