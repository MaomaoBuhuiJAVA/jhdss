"""Blender device twin: model-only controls and read-only HTTP telemetry."""
bl_info = {
    "name": "JHDS Device Twin",
    "author": "JHDS",
    "version": (1, 1, 0),
    "blender": (4, 2, 0),
    "location": "3D View > Sidebar > Device Twin",
    "category": "3D View",
    "description": "Articulated camera gantry with read-only position synchronisation",
}

import bpy
import json
import math
import threading
import time
import urllib.request
from bpy.app.handlers import persistent
from mathutils import Quaternion

_worker = None
_stop = None
_packet = None
_generation = 0
_received = 0.0
_network_error = "Waiting for position data"
_rail_basis = None
_chain_cache = {}
_last_pose = {}
_updating = False


def finite(value, name):
    if isinstance(value, bool):
        raise ValueError(name + " must be numeric")
    number = float(value)
    if not math.isfinite(number):
        raise ValueError(name + " must be finite")
    return number


def clamp(value, low=0.0, high=1.0):
    return max(low, min(high, value))


def decode_position(payload, now=None):
    """Parse the existing JHDS estimate or an explicit normalised position feed."""
    global _rail_basis
    now = time.monotonic() if now is None else now
    if not isinstance(payload, dict):
        raise ValueError("Position response must be an object")
    if payload.get("code", 200) != 200:
        raise ValueError("Position API returned an error")
    data = payload.get("data", payload)
    if not isinstance(data, dict):
        raise ValueError("Missing position data")
    if data.get("mqttConnected") is False:
        raise ValueError("Device MQTT connection is offline")
    stamp = data.get("timestamp_ms")
    if stamp is not None:
        age = time.time() - finite(stamp, "timestamp_ms") / 1000
        if age > 5 or age < -5:
            raise ValueError("Position sample timestamp is stale or in the future")
    rail = data.get("rail")
    if isinstance(rail, dict):
        full = finite(rail.get("fullTravelMs"), "fullTravelMs")
        bank = finite(rail.get("positionMs"), "positionMs")
        if full <= 0 or bank < 0:
            raise ValueError("Invalid rail calibration")
        direction = rail.get("movingDirection")
        basis = (bank, direction, full)
        if _rail_basis is None or _rail_basis[:3] != basis:
            _rail_basis = (*basis, now)
        position = bank
        if direction in ("left", "right"):
            # Existing backend banks travel only on command changes / stop.
            position += (now-_rail_basis[3])*1000*(1 if direction == "left" else -1)
        limit = finite(rail.get("leftLimitMs", full), "leftLimitMs")
        out = {"x": 1-clamp(position, 0, limit)/full}
        source = "LIVE / X estimated from motor time"
    else:
        _rail_basis = None
        out = {}
        for key in ("x", "y", "pan_deg", "tilt_deg"):
            if key in data:
                out[key] = finite(data[key], key)
        if not out:
            raise ValueError("No x/y or rail position in API response")
        source = "LIVE / position feed"
    # The second axis and PTZ are independent: absent fields hold their pose.
    for key in ("y", "pan_deg", "tilt_deg"):
        if key in data:
            out[key] = finite(data[key], key)
    return out, source


def update_chain(container, endpoint):
    cached = _chain_cache.get(container.name)
    if cached is None or cached[0] != len(container.children):
        links = [(o, float(o["chain_t"])) for o in container.children if "chain_t" in o]
        cables = [o for o in container.children if "chain_cable_offset" in o and o.type == "CURVE"]
        cached = (len(container.children), links, cables)
        _chain_cache[container.name] = cached
    _, links, cables = cached
    fixed = float(container["fixed"])
    radius = float(container["radius"])
    length = float(container["length"])
    bend = (length+fixed+endpoint-math.pi*radius)/2
    lower = bend-fixed
    z0, side = float(container["base_z"]), float(container["side"])
    along_y = container["axis"] == "Y"
    turn = Quaternion((0,0,1),math.pi/2) if along_y else Quaternion()
    def sample(distance):
        if distance < lower:
            along, z, angle = fixed+distance, z0, 0.0
        elif distance < lower+math.pi*radius:
            phase = (distance-lower)/radius-math.pi/2
            along = bend+radius*math.cos(phase)
            z = z0+radius+radius*math.sin(phase)
            angle = phase+math.pi/2
        else:
            along = bend-(distance-lower-math.pi*radius)
            z, angle = z0+2*radius, math.pi
        return along, z, angle

    for obj, fraction in links:
        along, z, angle = sample(fraction*length)
        obj.location = (side,along,z) if along_y else (along,side,z)
        obj.rotation_mode = "QUATERNION"
        obj.rotation_quaternion = turn @ Quaternion((0,1,0),-angle)
    for cable in cables:
        points = cable.data.splines[0].points
        offset = side+float(cable["chain_cable_offset"])
        for i, point in enumerate(points):
            along, z, _ = sample(i*length/(len(points)-1))
            point.co = (offset,along,z,1) if along_y else (along,offset,z,1)


def apply_pose(scene):
    root = scene.objects.get("DEVICE_ROOT")
    if root is None or not hasattr(scene,"device_twin"):
        return
    s = scene.device_twin
    x = root["x_min"]+clamp(s.axis_x)*(root["x_max"]-root["x_min"])
    y = root["y_min"]+clamp(s.axis_y)*(root["y_max"]-root["y_min"])
    pose = (x,y,s.pan_deg,s.tilt_deg)
    if _last_pose.get(scene.name) == pose:
        return
    for name, index, value in [("GANTRY_X",0,x),("CARRIAGE_Y",1,y)]:
        obj = scene.objects.get(name)
        if obj:
            obj.location[index] = value
    pan, tilt = scene.objects.get("CAMERA_PAN"), scene.objects.get("CAMERA_TILT")
    if pan:
        pan.rotation_euler.z = math.radians(s.pan_deg)
    if tilt:
        tilt.rotation_euler.x = math.radians(s.tilt_deg)
    previous = _last_pose.get(scene.name)
    if previous is None or previous[0] != x:
        for name in ("CHAIN_X_FRONT", "CHAIN_X_REAR"):
            if scene.objects.get(name):
                update_chain(scene.objects[name],x)
    if previous is None or previous[1] != y:
        if scene.objects.get("CHAIN_Y"):
            update_chain(scene.objects["CHAIN_Y"],y)
    _last_pose[scene.name] = pose


def on_pose(self, context):
    if not _updating and context.scene:
        apply_pose(context.scene)


def stop_worker():
    global _stop, _worker, _packet, _received, _rail_basis, _generation
    _generation += 1
    if _stop:
        _stop.set()
    _stop, _worker, _packet, _rail_basis = None, None, None, None
    _received = 0


def start_worker(url, interval):
    global _stop, _worker, _network_error
    stop_worker()
    event = threading.Event()
    _stop = event
    generation = _generation
    _network_error = "Connecting to position API"

    def poll():
        global _packet, _received, _network_error
        while not event.is_set():
            try:
                request = urllib.request.Request(url, headers={"Accept":"application/json"})
                # Network thread never touches Blender datablocks.
                with urllib.request.urlopen(request, timeout=2) as response:
                    raw = response.read(1024*1024+1)
                if len(raw) > 1024*1024:
                    raise ValueError("Position response too large")
                data = json.loads(raw)
                if generation != _generation or event.is_set():
                    return
                _packet = (data,time.monotonic())
                _received = _packet[1]
                _network_error = ""
            except Exception as exc:
                if generation != _generation:
                    return
                _network_error = str(exc)[:160]
            event.wait(interval)

    _worker = threading.Thread(target=poll, name="JHDS-read-only-position", daemon=True)
    _worker.start()


def on_mode(self, context):
    scene = context.scene
    if scene is None:
        return
    stop_worker()
    if scene.animation_data and scene.animation_data.action:
        self.demo_action = scene.animation_data.action.name
        scene.animation_data.action = None
    if self.mode == "DEMO":
        action = bpy.data.actions.get(self.demo_action)
        if action:
            scene.animation_data_create().action = action
        self.status = "DEMO / timeline animation"
    elif self.mode == "LIVE":
        start_worker(self.url, self.poll_interval)
        self.status = "Connecting to position API"
    else:
        self.status = "MANUAL / model position"


@persistent
def on_frame(scene, *args):
    if hasattr(scene,"device_twin") and scene.device_twin.mode == "DEMO":
        apply_pose(scene)


def tick():
    global _updating
    for scene in bpy.data.scenes:
        if "DEVICE_ROOT" not in scene.objects:
            continue
        s = scene.device_twin
        if s.mode != "LIVE":
            continue
        packet = _packet
        if packet is None or time.monotonic()-packet[1] > s.stale_seconds:
            s.status = "OFFLINE / holding last pose"
            s.detail = _network_error or "Position data timed out"
            continue
        try:
            position, source = decode_position(packet[0])
            _updating = True
            if "x" in position:
                s.axis_x = clamp(position["x"])
            if "y" in position:
                s.axis_y = clamp(position["y"])
            if "pan_deg" in position:
                s.pan_deg = clamp(position["pan_deg"],-180,180)
            if "tilt_deg" in position:
                s.tilt_deg = clamp(position["tilt_deg"],-85,85)
            _updating = False
            s.status = source
            s.detail = "X + Y" if "y" in position else "Y / PTZ hold: no position feedback"
            apply_pose(scene)
        except (ValueError,TypeError,KeyError) as exc:
            _updating = False
            s.status = "INVALID DATA / holding last pose"
            s.detail = str(exc)[:150]
    return .05


@persistent
def on_load_pre(*args):
    stop_worker()
    _last_pose.clear()
    _chain_cache.clear()


@persistent
def on_load_post(*args):
    _last_pose.clear()
    _chain_cache.clear()
    if not bpy.app.timers.is_registered(tick):
        bpy.app.timers.register(tick,first_interval=.25,persistent=True)
    for scene in bpy.data.scenes:
        if "DEVICE_ROOT" in scene.objects:
            apply_pose(scene)
            if scene.device_twin.mode == "LIVE":
                start_worker(scene.device_twin.url,scene.device_twin.poll_interval)


class DeviceTwinSettings(bpy.types.PropertyGroup):
    mode: bpy.props.EnumProperty(name="Mode",items=[("MANUAL","Manual","Model pose"),("DEMO","Demo","Timeline scan"),("LIVE","Live","Read position API")],default="MANUAL",update=on_mode)
    axis_x: bpy.props.FloatProperty(name="Bridge X",default=.43,min=0,max=1,precision=3,update=on_pose)
    axis_y: bpy.props.FloatProperty(name="Camera Y",default=.38,min=0,max=1,precision=3,update=on_pose)
    pan_deg: bpy.props.FloatProperty(name="Pan (deg)",default=0,min=-180,max=180,update=on_pose)
    tilt_deg: bpy.props.FloatProperty(name="Tilt (deg)",default=0,min=-85,max=85,update=on_pose)
    url: bpy.props.StringProperty(name="Position URL",default="http://127.0.0.1:9117/jhds/api/patrol/auto/status")
    poll_interval: bpy.props.FloatProperty(name="Poll interval (s)",default=.2,min=.1,max=5)
    stale_seconds: bpy.props.FloatProperty(name="Stale after (s)",default=2,min=.5,max=10)
    status: bpy.props.StringProperty(default="MANUAL / model position")
    detail: bpy.props.StringProperty(default="")
    demo_action: bpy.props.StringProperty(default="")


class DEVICE_OT_reconnect(bpy.types.Operator):
    bl_idname="device_twin.reconnect"
    bl_label="Reconnect"
    bl_description="Restart the read-only position feed"
    def execute(self,context):
        s=context.scene.device_twin
        s.mode="LIVE"
        return {"FINISHED"}


class DEVICE_OT_play(bpy.types.Operator):
    bl_idname="device_twin.play_demo"
    bl_label="Play / Pause"
    bl_description="Play or pause the model scan animation"
    def execute(self,context):
        context.scene.device_twin.mode="DEMO"
        bpy.ops.screen.animation_play()
        return {"FINISHED"}


class DEVICE_OT_view(bpy.types.Operator):
    bl_idname="device_twin.view"
    bl_label="View"
    camera: bpy.props.StringProperty()
    def execute(self,context):
        camera=context.scene.objects.get(self.camera)
        if camera:
            context.scene.camera=camera
            for area in context.screen.areas:
                if area.type=="VIEW_3D":
                    area.spaces.active.region_3d.view_perspective="CAMERA"
        return {"FINISHED"}


class DEVICE_PT_twin(bpy.types.Panel):
    bl_label="JHDS Inspection Gantry"
    bl_idname="DEVICE_PT_twin"
    bl_space_type="VIEW_3D"
    bl_region_type="UI"
    bl_category="Device Twin"
    @classmethod
    def poll(cls,context):
        return "DEVICE_ROOT" in context.scene.objects
    def draw(self,context):
        layout=self.layout
        s=context.scene.device_twin
        layout.prop(s,"mode",expand=True)
        layout.separator()
        column=layout.column()
        column.enabled=s.mode=="MANUAL"
        column.prop(s,"axis_x",slider=True)
        column.prop(s,"axis_y",slider=True)
        column.prop(s,"pan_deg",slider=True)
        column.prop(s,"tilt_deg",slider=True)
        if s.mode=="DEMO":
            layout.operator("device_twin.play_demo",icon="PLAY")
        if s.mode=="LIVE":
            layout.prop(s,"url")
            row=layout.row(align=True)
            row.prop(s,"poll_interval")
            row.operator("device_twin.reconnect",text="",icon="FILE_REFRESH")
            layout.prop(s,"stale_seconds")
        layout.label(text=s.status,icon="LINKED" if s.status.startswith("LIVE") else "INFO")
        if s.mode=="LIVE" and s.detail:
            layout.label(text=s.detail)
        layout.separator()
        row=layout.row(align=True)
        for title,name in [("Overview","VIEW_01 | Equipment overview"),("Top","VIEW_03 | XY camera and chain")]:
            row.operator("device_twin.view",text=title,icon="CAMERA_DATA").camera=name
        row=layout.row(align=True)
        row.operator("device_twin.view",text="Rear",icon="CAMERA_DATA").camera="VIEW_02 | Rear services"
        row.operator("device_twin.view",text="Lens",icon="VIEW_CAMERA").camera="INSPECTION_CAMERA"
        if context.scene.objects.get("VIEW_05 | Camera housing and nozzle"):
            row=layout.row(align=True)
            row.operator("device_twin.view",text="Camera",icon="CAMERA_DATA").camera="VIEW_05 | Camera housing and nozzle"
            row.operator("device_twin.view",text="Weather",icon="CAMERA_DATA").camera="VIEW_06 | Integrated weather station"
            layout.operator("device_twin.view",text="Bracket slots",icon="CAMERA_DATA").camera="VIEW_07 | Camera rear through slots"


CLASSES=(DeviceTwinSettings,DEVICE_OT_reconnect,DEVICE_OT_play,DEVICE_OT_view,DEVICE_PT_twin)


def register():
    for cls in CLASSES:
        bpy.utils.register_class(cls)
    bpy.types.Scene.device_twin=bpy.props.PointerProperty(type=DeviceTwinSettings)
    for handlers,callback in [(bpy.app.handlers.frame_change_post,on_frame),(bpy.app.handlers.load_pre,on_load_pre),(bpy.app.handlers.load_post,on_load_post)]:
        if callback not in handlers:
            handlers.append(callback)
    if not bpy.app.timers.is_registered(tick):
        bpy.app.timers.register(tick,first_interval=.3,persistent=True)


def unregister():
    stop_worker()
    if bpy.app.timers.is_registered(tick):
        bpy.app.timers.unregister(tick)
    for handlers,callback in [(bpy.app.handlers.frame_change_post,on_frame),(bpy.app.handlers.load_pre,on_load_pre),(bpy.app.handlers.load_post,on_load_post)]:
        if callback in handlers:
            handlers.remove(callback)
    if hasattr(bpy.types.Scene,"device_twin"):
        del bpy.types.Scene.device_twin
    for cls in reversed(CLASSES):
        bpy.utils.unregister_class(cls)


if __name__=="__main__":
    register()
