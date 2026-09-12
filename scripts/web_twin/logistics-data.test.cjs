const assert = require('node:assert/strict');
const {test} = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
function fixture() {
    const records = new Map();
    const context = {window:{}, structuredClone, localStorage:{
        getItem:key=>records.get(key), setItem:(key,value)=>records.set(key,value)
    }};
    vm.runInNewContext(fs.readFileSync(path.resolve(__dirname,
        '../../src/main/resources/static/js/logistics-data.js'), 'utf8'), context);
    return {...context.window.LogisticsData, records, storage:context.localStorage};
}
test('sample orders validate, and a load returns independent data', () => {
    const data=fixture();
    assert.equal(data.validate(data.defaults).length, 8);
    data.load().jobs[0].via.length=0;
    assert.equal(data.load().jobs[0].via.length, 3);
});
test('route order survives save and reload', () => {
    const data=fixture(), jobs=data.load().jobs;
    jobs[0].via=['nanjing','wuhan','chongqing'];
    data.save(jobs);
    assert.equal(JSON.stringify(data.load().jobs[0].via), JSON.stringify(jobs[0].via));
});
test('duplicate identifiers, invalid states, and HTML identifiers are rejected', () => {
    const data=fixture(), first=data.load().jobs[0];
    assert.throws(()=>data.validate([first,first]));
    for(const patch of [{id:'<img>'},{vehicle:''},{status:'__proto__'},{id:null}]) {
        assert.throws(()=>data.validate([{...first,...patch}]));
    }
    assert.throws(()=>data.validate([first,{...first,id:'DIFFERENT'}]));
});
test('numeric limits and integer parcel counts are enforced', () => {
    const data=fixture(), first=data.load().jobs[0];
    for(const patch of [{speed:121},{temp:Infinity},{parcels:1.5},{parcels:0},
        {progress:1.1},{speed:'50'},{progress:NaN}]) assert.throws(()=>data.validate([{...first,...patch}]));
});
test('malformed and repeated route stops are rejected', () => {
    const data=fixture(), first=data.load().jobs[0];
    for(const patch of [{from:'unknown'},{to:first.from},{via:'wuhan'},
        {via:[first.from]},{via:['wuhan','wuhan']}]) assert.throws(()=>data.validate([{...first,...patch}]));
    assert.throws(()=>data.validate(null));
    assert.throws(()=>data.validate([null]));
    assert.throws(()=>data.validate(Array(41).fill(first)));
});
test('empty orders and delivered state have stable semantics', () => {
    const data=fixture();data.save([]);assert.equal(data.load().jobs.length,0);
    const result=data.validate([{...data.defaults[0],status:'delivered',progress:0}]);
    assert.equal(result[0].progress,1);
});
test('corrupt storage falls back without overwriting the original', () => {
    const data=fixture();data.save(data.defaults);
    const key=[...data.records.keys()][0];data.records.set(key,'broken');
    assert.ok(data.load().error);assert.equal(data.load().jobs.length,8);
    assert.equal(data.records.get(key),'broken');
});
test('unavailable storage surfaces an error instead of claiming persistence', () => {
    const data=fixture();data.storage.setItem=()=>{throw new Error('quota');};
    assert.throws(()=>data.save(data.defaults),/quota/);
});
