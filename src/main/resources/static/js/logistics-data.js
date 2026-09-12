(function () {
    'use strict';
    // Display anchors calibrated against the supplied non-georeferenced model.
    // The model has no CRS; these are model coordinates, not GPS positions.
    const cities = [
        ['beijing', '北京', 9.39, 3.97], ['tianjin', '天津', 10.14, 3.25],
        ['shanghai', '上海', 13.4, -4.42], ['nanjing', '南京', 11.44, -3.86],
        ['hangzhou', '杭州', 11.94, -5.78], ['jinan', '济南', 10.01, .19],
        ['yantai', '烟台', 13.28, 1.67], ['zhengzhou', '郑州', 7.31, -1.19],
        ['wuhan', '武汉', 7.83, -5.39], ['changsha', '长沙', 6.78, -7.78],
        ['nanchang', '南昌', 9.44, -7.21], ['fuzhou', '福州', 11.5, -9.2],
        ['guangzhou', '广州', 6.86, -12.94], ['shenzhen', '深圳', 7.89, -12.75],
        ['chengdu', '成都', -.07, -5.31], ['chongqing', '重庆', 1.93, -6.31],
        ['xian', '西安', 3.95, -1.70], ['lanzhou', '兰州', .64, -.75],
        ['urumqi', '乌鲁木齐', -13.14, 8.33], ['kunming', '昆明', -1.55, -10.78],
        ['harbin', '哈尔滨', 17.66, 9.60], ['shenyang', '沈阳', 14.94, 5.75]
    ].map(([id, name, x, y]) => ({id, name, x, y}));
    const states = {transit: {name:'运输中',color:0x42cfff}, alert:{name:'温度异常',color:0xff6978},
        delayed:{name:'延误',color:0xffbe58}, delivered:{name:'已签收',color:0x73dfad}, stopped:{name:'停靠',color:0xacb5bd}};
    const defaults = [
        ['WL-0912-031','CL-03','shanghai','chengdu',['nanjing','wuhan','chongqing'],'transit',68,2.8,126,.39],
        ['WL-0912-067','CL-08','nanjing','shanghai',[],'alert',42,7.2,84,.62],
        ['WL-0911-204','CL-12','yantai','beijing',['jinan','tianjin'],'delayed',26,3.5,96,.54],
        ['WL-0912-108','CL-06','beijing','harbin',['shenyang'],'transit',72,3.1,180,.43],
        ['WL-0912-144','CL-09','kunming','shenzhen',['guangzhou'],'delivered',0,2.1,72,1],
        ['WL-0912-155','CL-15','urumqi','xian',['lanzhou'],'transit',64,3.4,215,.31],
        ['WL-0912-186','CL-21','guangzhou','wuhan',['changsha'],'transit',61,2.9,118,.58],
        ['WL-0912-193','CL-24','shanghai','fuzhou',['hangzhou'],'stopped',0,3.2,64,.33]
    ].map(([id,vehicle,from,to,via,status,speed,temp,parcels,progress])=>({id,vehicle,from,to,via,status,speed,temp,parcels,progress}));
    const storageKey = 'jhds.logistics.virtual.v2';
    function validate(raw) {
        if (!Array.isArray(raw) || raw.length > 40) throw new Error('最多保存 40 条虚拟运单');
        const ids = new Set(), vehicles = new Set();
        return raw.map(j => {
            const number = (key,min,max) => {
                if (typeof j[key] !== 'number' || !Number.isFinite(j[key]) || j[key]<min || j[key]>max) throw new Error('运单数值无效');
                return j[key];
            };
            const integer = (key,min,max) => {
                const value=number(key,min,max);
                if (!Number.isInteger(value)) throw new Error('运单件数必须为整数');
                return value;
            };
            if (!j || typeof j.id!=='string' || !/^[A-Za-z0-9_-]{1,32}$/.test(j.id) || ids.has(j.id)) throw new Error('运单编号无效或重复');
            if (typeof j.vehicle!=='string' || !/^[A-Za-z0-9_-]{1,24}$/.test(j.vehicle) || vehicles.has(j.vehicle)) throw new Error('车牌代号无效或重复');
            if (!Object.hasOwn(states,j.status)) throw new Error('运输状态无效');
            if (j.via !== undefined && !Array.isArray(j.via)) throw new Error('途经节点必须为有序数组');
            const via = j.via || [];
            const stops=[j.from,...via,j.to];
            if (via.length>8 || stops.some(id=>!cities.some(c=>c.id===id)) || new Set(stops).size!==stops.length) throw new Error('起终点或途经节点无效');
            ids.add(j.id); vehicles.add(j.vehicle);
            return {id:j.id,vehicle:j.vehicle,from:j.from,to:j.to,via:[...via],status:j.status,
                speed:number('speed',0,120),temp:number('temp',-30,50),parcels:integer('parcels',1,99999),
                progress:j.status==='delivered'?1:number('progress',0,1)};
        });
    }
    function load() {
        try { const raw=localStorage.getItem(storageKey); return {jobs:raw?validate(JSON.parse(raw)):structuredClone(defaults)}; }
        catch (error) { return {jobs:structuredClone(defaults),error:'本机运单记录无法读取，已加载示例'}; }
    }
    function save(jobs) { const valid=validate(jobs); localStorage.setItem(storageKey,JSON.stringify(valid)); return valid; }
    window.LogisticsData={cities,states,defaults,validate,load,save};
})();
