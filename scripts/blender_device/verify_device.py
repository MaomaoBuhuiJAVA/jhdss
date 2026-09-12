"""Exercise model articulation, demo persistence and read-only telemetry in Blender."""
import bpy
import importlib.util
import json
import math
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location("device_twin_sync",HERE/"device_twin_sync.py")
sync=importlib.util.module_from_spec(spec)
sys.modules[spec.name]=sync
spec.loader.exec_module(sync)
sync.register()
scene=bpy.context.scene
s=scene.device_twin
passed=[]


def check(condition,name):
    if not condition:
        raise AssertionError(name)
    passed.append(name)


def pose(x,y):
    s.axis_x=x
    s.axis_y=y
    sync.apply_pose(scene)
    bpy.context.view_layer.update()
    return scene.objects["CAMERA_TILT"].matrix_world.translation.copy()


static=scene.objects["6060 slotted upright"].matrix_world.copy()
s.mode="MANUAL"
p0=pose(.1,.2)
p1=pose(.9,.8)
check(abs(p1.x-p0.x-1.648)<.00001,"Camera follows X bridge")
check(abs(p1.y-p0.y-.684)<.00001,"Camera follows Y carriage")
check(scene.objects["6060 slotted upright"].matrix_world==static,"Fixed frame stays fixed")
for x,y in [(0,0),(.5,.5),(1,1)]:
    pose(x,y)
    for name in ["CHAIN_X_FRONT","CHAIN_X_REAR","CHAIN_Y"]:
        chain=scene.objects[name]
        links=sorted([o for o in chain.children if "chain_t" in o],key=lambda o:o["chain_t"])
        pitch=chain["length"]/len(links)
        distances=[(a.location-b.location).length for a,b in zip(links,links[1:])]
        check(min(distances)>pitch*.96 and max(distances)<pitch*1.01,"Chain continuity at %.1f: %s"%(x,name))
        for cable in [o for o in chain.children if "chain_cable_offset" in o]:
            points=cable.data.splines[0].points
            length=sum((a.co.xyz-b.co.xyz).length for a,b in zip(points,points[1:]))
            axis=1 if chain["axis"]=="Y" else 0
            endpoint=scene.objects["CARRIAGE_Y"].location.y if axis==1 else scene.objects["GANTRY_X"].location.x
            check(abs(points[-1].co[axis]-endpoint)<1e-5,"Cable moving end stays attached: %.1f %s"%(x,cable.name))
            check(abs(length-chain["length"])<.003,"Cable maintains physical length: %.1f %s"%(x,cable.name))
s.mode="DEMO"
check(scene.animation_data and scene.animation_data.action is not None,"Demo action survives save and reload")
scene.frame_set(1)
sync.apply_pose(scene)
start=scene.objects["GANTRY_X"].location.x
scene.frame_set(60)
sync.apply_pose(scene)
check(abs(scene.objects["GANTRY_X"].location.x-start)>1.4,"Timeline moves the bridge")
s.mode="MANUAL"
check(scene.animation_data.action is None,"Live/manual mode detaches demo animation")
sync._rail_basis=None
a,_=sync.decode_position({"data":{"rail":{"positionMs":1000,"fullTravelMs":10000,"leftLimitMs":8000,"movingDirection":"left"}}},now=10)
b,_=sync.decode_position({"data":{"rail":{"positionMs":1000,"fullTravelMs":10000,"leftLimitMs":8000,"movingDirection":"left"}}},now=12)
check(abs(a["x"]-.9)<1e-6 and abs(b["x"]-.7)<1e-6,"Banked rail position interpolates while moving")
for payload in [{"x":"nan"},{"x":True},{"rail":{"positionMs":0,"fullTravelMs":0}},{"x":.5,"timestamp_ms":0},{"data":{"mqttConnected":False,"x":.5}}]:
    try:
        sync.decode_position(payload)
    except (ValueError,TypeError):
        check(True,"Reject invalid/stale/offline sample: "+str(payload))
    else:
        raise AssertionError("Invalid sample accepted: "+str(payload))

fixture={"value":{"x":.15,"y":.25,"pan_deg":20,"tilt_deg":-15},"gets":0}


class Feed(BaseHTTPRequestHandler):
    def do_GET(self):
        fixture["gets"]+=1
        encoded=json.dumps(fixture["value"]).encode()
        self.send_response(200)
        self.send_header("Content-Type","application/json")
        self.send_header("Content-Length",str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)
    def log_message(self,*args):
        pass


server=ThreadingHTTPServer(("127.0.0.1",0),Feed)
server.daemon_threads=True
thread=threading.Thread(target=server.serve_forever,daemon=True)
thread.start()
try:
    s.url="http://127.0.0.1:%d/position"%server.server_port
    s.poll_interval=.1
    s.stale_seconds=.5
    s.mode="LIVE"
    deadline=time.monotonic()+3
    while sync._packet is None and time.monotonic()<deadline:
        time.sleep(.03)
    sync.tick()
    check(abs(s.axis_x-.15)<1e-5 and abs(s.axis_y-.25)<1e-5,"HTTP feed updates both model axes")
    check(abs(s.pan_deg-20)<1e-4 and abs(s.tilt_deg+15)<1e-4,"HTTP feed updates PTZ")
    fixture["value"]={"x":.77,"y":.83}
    deadline=time.monotonic()+3
    while time.monotonic()<deadline:
        time.sleep(.04)
        sync.tick()
        if abs(s.axis_x-.77)<1e-4:
            break
    check(abs(s.axis_x-.77)<1e-5 and abs(s.axis_y-.83)<1e-5,"Second live sample moves the same rig")
    check(abs(s.pan_deg-20)<1e-4,"Missing feedback preserves independent axes")
    server.shutdown()
    server.server_close()
    time.sleep(.6)
    sync.tick()
    check(s.status.startswith("OFFLINE") and abs(s.axis_x-.77)<1e-5,"Stale feed freezes last known pose")
    check(fixture["gets"]>=2,"Live transport uses read-only GET requests")
finally:
    sync.stop_worker()
    server.server_close()
    thread.join(timeout=2)
report={"passed":len(passed),"checks":passed,"objects":len(scene.objects),"photo_references":sum(o.name.startswith("PHOTO_") for o in scene.objects)}
out=HERE.parents[1]/"artifacts"/"blender-device"/"verification.json"
out.write_text(json.dumps(report,indent=2),encoding="utf-8")
print(json.dumps(report,indent=2),flush=True)
