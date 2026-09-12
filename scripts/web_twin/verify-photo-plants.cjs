const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const dir = path.resolve('artifacts/blender-device/revisions/new-plants');
function glb(file) {
    const bytes = fs.readFileSync(file);
    assert.equal(bytes.readUInt32LE(0), 0x46546c67);
    assert.equal(bytes.readUInt32LE(4), 2);
    assert.equal(bytes.readUInt32LE(8), bytes.length);
    const jsonLength = bytes.readUInt32LE(12);
    const data = JSON.parse(bytes.subarray(20, 20 + jsonLength));
    const binary = bytes.subarray(28 + jsonLength);
    return {data, binary, size: bytes.length};
}
function geometry(model, primitive) {
    const viewIndex = primitive.extensions.KHR_draco_mesh_compression.bufferView;
    const view = model.data.bufferViews[viewIndex];
    const bytes = model.binary.subarray(view.byteOffset || 0, (view.byteOffset || 0) + view.byteLength);
    return crypto.createHash('sha256').update(bytes).digest('hex');
}
const before = glb(path.join(dir, 'Before_New_Plants_Web.glb'));
const after = glb(path.join(dir, 'web/inspection-gantry.glb'));
const botanical = /Crumpled foliage|Woody plant stems|Fruit ochre|Fruit red|Ivory flower petals|Yellow-green flower centres/;
const unchanged = [], changed = [], removed = [];
for (const oldNode of before.data.nodes) {
    const newNode = after.data.nodes.find(n => n.name === oldNode.name);
    if (oldNode.mesh === undefined) {
        assert.ok(newNode, `Missing rig or anchor: ${oldNode.name}`);
        for (const prop of ['translation', 'rotation', 'scale', 'matrix']) {
            assert.deepEqual(newNode[prop], oldNode[prop], `Changed ${oldNode.name}.${prop}`);
        }
        continue;
    }
    const oldPrimitives = before.data.meshes[oldNode.mesh].primitives;
    if (oldPrimitives.some(p => botanical.test(before.data.materials[p.material].name))) continue;
    if (!newNode) { removed.push(oldNode.name); continue; }
    const newPrimitives = after.data.meshes[newNode.mesh].primitives;
    const match = oldPrimitives.length === newPrimitives.length && oldPrimitives.every((p, i) =>
        geometry(before, p) === geometry(after, newPrimitives[i]) &&
        JSON.stringify(before.data.materials[p.material]) === JSON.stringify(after.data.materials[newPrimitives[i].material]));
    (match ? unchanged : changed).push(oldNode.name);
}
const plants = after.data.nodes.filter(n => n.name.includes('NewPlants'));
assert.equal(plants.length, 5);
let plantTriangles = 0;
for (const node of plants) {
    for (const primitive of after.data.meshes[node.mesh].primitives) {
        assert.notEqual(primitive.attributes.COLOR_0, undefined, `${node.name} lost botanical vertex colors`);
        plantTriangles += after.data.accessors[primitive.indices].count / 3;
    }
}
assert.deepEqual(removed, [], 'Non-plant web geometry was removed');
assert.deepEqual(changed, [], 'Non-plant web geometry/materials changed');
assert.ok(plantTriangles < 450000);
const oldMeta = JSON.parse(fs.readFileSync(path.join(dir, 'Before_New_Plants_Web.json')));
const newMeta = JSON.parse(fs.readFileSync(path.join(dir, 'web/inspection-gantry.json')));
for (const prop of ['axis', 'xRange', 'yRange', 'chains']) assert.deepEqual(newMeta[prop], oldMeta[prop]);
const report = {bytes: after.size, plantTriangles, unchangedMeshGroups: unchanged.length,
    changed, removed, plantDrawGroups: plants.length, botanicalVertexColors: true, rigAndEffectsUnchanged: true};
fs.writeFileSync(path.join(dir, 'web_asset_verification.json'), JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
