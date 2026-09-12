(function(){
    'use strict';
    const board=document.getElementById('logistics-workbench'),host=document.getElementById('logistics-map-3d');
    if(!board||!host)return;
    const byId=id=>document.getElementById('logistics-'+id);
    const {cities,states,load,save,validate}=window.LogisticsData;
    const loaded=load();let jobs=loaded.jobs,selected=jobs[0]?.id,editing=null,editingVia=[],viewer,paused=false,ready=false;
    const dialog=byId('order-editor'),form=byId('order-form'),message=byId('message');
    const mainDock=document.getElementById('logistics-main-dock'),modalDock=host.parentElement;
    const heading=modalDock.querySelector('h3');if(heading)heading.remove();
    byId('map-dock').append(host);
    const overview=document.createElement('aside');overview.className='logistics-overview';overview.setAttribute('aria-label','销售与冷链概览');
    const overviewTitle=document.createElement('h3');overviewTitle.className='logistics-rail-title';overviewTitle.textContent='销售概览';overview.append(overviewTitle);
    const salesMetrics=document.querySelector('.sales-metrics');if(salesMetrics)overview.append(salesMetrics);
    const coldCard=document.createElement('article');coldCard.className='logistics-info-card';
    coldCard.innerHTML='<h3><i class="ri-temp-cold-line"></i>冷链状态</h3><div id="logistics-cold-summary"></div><div id="logistics-cold-risks"></div>';
    const destinations=document.createElement('article');destinations.className='logistics-info-card';
    destinations.innerHTML='<h3><i class="ri-map-pin-line"></i>目的地货量</h3><div id="logistics-destination-ranks"></div>';
    overview.append(coldCard,destinations);board.querySelector('.logistics-board-body').prepend(overview);
    const dispatchTitle=document.createElement('h3');dispatchTitle.className='logistics-rail-title';dispatchTitle.textContent='运单动态';board.querySelector('.logistics-dispatch').prepend(dispatchTitle);
    host.querySelector('.logistics-map-legend').replaceChildren();
    for(const key of ['transit','delivered','alert','delayed']){
        const span=document.createElement('span'),dot=document.createElement('i');dot.style.background='#'+states[key].color.toString(16);span.append(dot,states[key].name);host.querySelector('.logistics-map-legend').append(span);
    }
    const topButton=document.createElement('button');topButton.type='button';topButton.title='俯视地图';topButton.setAttribute('aria-label','俯视地图');topButton.innerHTML='<i class="ri-layout-grid-line"></i>';
    host.querySelector('.logistics-map-toolbar').append(topButton);
    function notice(text){message.textContent=text;message.hidden=!text;}
    function cityName(id){return cities.find(c=>c.id===id)?.name||id;}
    function drawOverview(){
        const cold=byId('cold-summary');cold.replaceChildren();
        const mean=jobs.length?jobs.reduce((n,j)=>n+j.temp,0)/jobs.length:null;
        cold.append(detailRow('平均箱温',mean===null?'--':mean.toFixed(1)+'°C'),detailRow('在途货件',jobs.filter(j=>['transit','alert','delayed'].includes(j.status)).reduce((n,j)=>n+j.parcels,0)+' 件'));
        const risks=byId('cold-risks');risks.replaceChildren();
        const abnormal=jobs.filter(j=>['alert','delayed'].includes(j.status));
        abnormal.forEach(j=>{const button=document.createElement('button');button.type='button';button.className='logistics-risk-link';
            const vehicle=document.createElement('span'),state=document.createElement('b');vehicle.textContent=j.vehicle;state.textContent=states[j.status].name;state.style.color='#'+states[j.status].color.toString(16);
            button.append(vehicle,state);button.addEventListener('click',()=>select(j.id));risks.append(button);
        });
        if(!abnormal.length){const empty=document.createElement('p');empty.className='logistics-no-risk';empty.textContent='暂无异常运单';risks.append(empty);}
        const counts=new Map();jobs.forEach(j=>counts.set(j.to,(counts.get(j.to)||0)+j.parcels));
        const ranked=[...counts].sort((a,b)=>b[1]-a[1]).slice(0,4),ranks=byId('destination-ranks');ranks.replaceChildren();
        ranked.forEach(([id,count])=>{const button=document.createElement('button');button.type='button';button.className='logistics-destination';
            const name=document.createElement('span'),value=document.createElement('b'),bar=document.createElement('meter');name.textContent=cityName(id);value.textContent=count+' 件';bar.min=0;bar.max=ranked[0][1];bar.value=count;bar.setAttribute('aria-label',cityName(id)+'货件数量');
            button.append(name,value,bar);button.addEventListener('click',()=>selectCity(id,true));ranks.append(button);
        });
        if(!ranked.length){const empty=document.createElement('p');empty.className='logistics-no-risk';empty.textContent='暂无货件';ranks.append(empty);}
    }
    function matches(j){const q=byId('search').value.trim().toLowerCase(),state=byId('state-filter').value;return (!state||j.status===state)&&(!q||[j.id,j.vehicle,...[j.from,...j.via,j.to].map(cityName)].join(' ').toLowerCase().includes(q));}
    function drawList(){
        const list=byId('order-list');list.replaceChildren();
        jobs.filter(matches).forEach(j=>{
            const button=document.createElement('button');button.type='button';button.className='logistics-order';button.dataset.order=j.id;button.setAttribute('aria-pressed',String(j.id===selected));
            const row=document.createElement('span'),id=document.createElement('strong'),status=document.createElement('em');id.textContent=j.id;status.textContent=states[j.status].name;status.style.color='#'+states[j.status].color.toString(16);row.append(id,status);
            const path=document.createElement('span');path.textContent=cityName(j.from)+' → '+cityName(j.to);
            const meta=document.createElement('small');meta.textContent=j.vehicle+' · '+j.parcels+' 件 · '+j.temp.toFixed(1)+'°C';
            button.append(row,path,meta);button.addEventListener('click',()=>select(j.id));list.append(button);
        });
        if(!list.children.length){const p=document.createElement('p');p.className='logistics-empty';p.textContent='没有符合条件的运单';list.append(p);}
        byId('total').textContent=jobs.length;byId('active').textContent=jobs.filter(j=>['transit','delayed','alert'].includes(j.status)).length;byId('risk').textContent=jobs.filter(j=>['alert','delayed'].includes(j.status)).length;
        drawOverview();
        viewer?.setFilter(byId('search').value.trim(),byId('state-filter').value);
    }
    function detailRow(label,value){const row=document.createElement('div'),key=document.createElement('span'),val=document.createElement('b');key.textContent=label;val.textContent=value;row.append(key,val);return row;}
    function drawDetail(){
        const detail=byId('detail');detail.replaceChildren();const job=jobs.find(j=>j.id===selected);
        byId('focus').disabled=byId('edit').disabled=!job||!ready;
        if(!job){byId('selected-title').textContent='节点详情';return;}
        byId('selected-title').textContent=job.vehicle+' · '+states[job.status].name;
        const path=document.createElement('p');path.className='logistics-detail-route';
        [job.from,...job.via,job.to].forEach((id,index)=>{const stop=document.createElement('span');stop.textContent=(index?' → ':'')+cityName(id);path.append(stop);});detail.append(path);
        const grid=document.createElement('div');grid.className='logistics-detail-grid';
        grid.append(detailRow('车厢温度',job.temp.toFixed(1)+'°C'),detailRow('设定车速',job.speed+' km/h'),detailRow('货件数量',job.parcels+' 件'),detailRow('运单编号',job.id));detail.append(grid);
        const progress=document.createElement('progress');progress.id='logistics-progress';progress.max=1;progress.value=viewer?.state(job.id)?.progress||job.progress;detail.append(progress);
        const label=document.createElement('span');label.id='logistics-progress-value';label.className='logistics-progress-value';detail.append(label);
    }
    function select(id){selected=id;viewer?.select(id);drawList();drawDetail();}
    function selectCity(id,heat){
        selected=null;viewer?.select(null);drawList();drawDetail();byId('selected-title').textContent=cityName(id)+(heat?' · 货量热点':' · 分拨节点');
        const linked=jobs.filter(j=>[j.from,...j.via,j.to].includes(id));byId('detail').append(detailRow('关联运单',linked.length+' 单'),detailRow('关联货件',linked.reduce((n,j)=>n+j.parcels,0)+' 件'));
        linked.forEach(j=>{const button=document.createElement('button');button.className='logistics-linked';button.textContent=j.id;button.addEventListener('click',()=>select(j.id));byId('detail').append(button);});
    }
    function tick(clock=0){
        byId('runtime').textContent=String(Math.floor(clock/60)).padStart(2,'0')+':'+String(Math.floor(clock%60)).padStart(2,'0');
        const state=viewer?.state(selected);if(state&&byId('progress')){byId('progress').value=state.progress;byId('progress-value').textContent=(state.progress>=1?'已到达 · ':'行驶进度 ')+(state.progress*100).toFixed(1)+'%';}
    }
    function persist(next){
        try{const saved=save(next);jobs=saved;viewer.setJobs(jobs);drawList();drawDetail();notice('虚拟运单已保存到本机');return true;}
        catch(error){notice(error.message||'无法保存本机运单');return false;}
    }
    for(const name of['from','to'])cities.forEach(city=>{const option=document.createElement('option');option.value=city.id;option.textContent=city.name;form.elements[name].append(option);});
    const viaField=form.elements.via.parentElement;
    const viaRows=document.createElement('div');viaRows.className='logistics-via-rows';
    const addVia=document.createElement('button');addVia.type='button';addVia.className='logistics-via-add';addVia.innerHTML='<i class="ri-add-line"></i>添加途经站';
    viaField.replaceChildren(document.createTextNode('途经节点'),viaRows,addVia);
    function viaButton(icon,title,action){const button=document.createElement('button');button.type='button';button.title=title;button.setAttribute('aria-label',title);button.innerHTML='<i class="ri-'+icon+'-line"></i>';button.addEventListener('click',action);return button;}
    function drawVia(){
        viaRows.replaceChildren();
        editingVia.forEach((id,index)=>{
            const row=document.createElement('div');row.className='logistics-via-row';
            const select=document.createElement('select');select.setAttribute('aria-label','途经站 '+(index+1));
            cities.forEach(city=>{const option=document.createElement('option');option.value=city.id;option.textContent=city.name;select.append(option);});select.value=id;
            select.addEventListener('change',()=>{editingVia[index]=select.value;});
            const up=viaButton('arrow-up','前移途经站',()=>{[editingVia[index-1],editingVia[index]]=[editingVia[index],editingVia[index-1]];drawVia();});up.disabled=index===0;
            const down=viaButton('arrow-down','后移途经站',()=>{[editingVia[index+1],editingVia[index]]=[editingVia[index],editingVia[index+1]];drawVia();});down.disabled=index===editingVia.length-1;
            row.append(select,up,down,viaButton('close','移除途经站',()=>{editingVia.splice(index,1);drawVia();}));viaRows.append(row);
        });addVia.disabled=editingVia.length>=8;
    }
    addVia.addEventListener('click',()=>{const available=cities.find(c=>![form.elements.from.value,form.elements.to.value,...editingVia].includes(c.id));if(available&&editingVia.length<8){editingVia.push(available.id);drawVia();}});
    function openEditor(id){
        editing=id||null;const j=jobs.find(j=>j.id===id)||{id:'WL-'+Date.now().toString().slice(-8),vehicle:'CL-'+Date.now().toString().slice(-5),from:'yantai',to:'shanghai',via:[],status:'transit',speed:65,temp:3.0,parcels:100,progress:0};
        for(const key of['id','vehicle','from','to','status','speed','temp','parcels'])form.elements[key].value=j[key];
        form.elements.id.readOnly=!!id;form.elements.progress.value=Math.round(j.progress*100);byId('form-progress').value=form.elements.progress.value+'%';
        editingVia=[...j.via];drawVia();
        byId('editor-title').textContent=id?'编辑虚拟运单':'新建虚拟运单';byId('delete').hidden=!id;byId('form-error').textContent='';dialog.showModal();
    }
    byId('add').addEventListener('click',()=>{if(ready)openEditor();});byId('edit').addEventListener('click',()=>openEditor(selected));
    byId('editor-close').addEventListener('click',()=>dialog.close());
    form.elements.progress.addEventListener('input',()=>byId('form-progress').value=form.elements.progress.value+'%');
    form.addEventListener('submit',event=>{
        event.preventDefault();const raw={};for(const key of['id','vehicle','from','to','status'])raw[key]=form.elements[key].value.trim();
        for(const key of['speed','temp','parcels'])raw[key]=Number(form.elements[key].value);
        raw.progress=Number(form.elements.progress.value)/100;raw.via=[...editingVia];
        try{const next=validate([...jobs.filter(j=>j.id!==editing),raw]);if(persist(next)){select(raw.id);dialog.close();}}
        catch(error){byId('form-error').textContent=error.message;}
    });
    byId('delete').addEventListener('click',()=>{const remaining=jobs.filter(j=>j.id!==editing);if(persist(remaining)){select(remaining[0]?.id);dialog.close();}});
    byId('download').addEventListener('click',()=>{const url=URL.createObjectURL(new Blob([JSON.stringify(jobs,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='virtual-logistics.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);});
    byId('upload').addEventListener('click',()=>byId('json-file').click());
    byId('json-file').addEventListener('change',async event=>{const file=event.target.files[0];try{if(!file)return;if(file.size>200000)throw new Error('运单文件不能超过 200 KB');const next=validate(JSON.parse(await file.text()));if(persist(next))select(next[0]?.id);}catch(error){notice(error.message||'运单文件读取失败');}finally{event.target.value='';}});
    byId('search').addEventListener('input',drawList);byId('state-filter').addEventListener('change',drawList);
    board.querySelectorAll('[data-layer]').forEach(input=>input.addEventListener('change',()=>viewer?.setLayer(input.dataset.layer,input.checked)));
    function setPaused(){paused=!paused;viewer?.setPaused(paused);for(const id of['toggle-play','map-pause']){const button=byId(id);button.title=paused?'播放':'暂停';button.setAttribute('aria-label',button.title);button.innerHTML='<i class="ri-'+(paused?'play':'pause')+'-line"></i>';}}
    byId('toggle-play').addEventListener('click',setPaused);byId('map-pause').addEventListener('click',setPaused);
    byId('replay').addEventListener('click',()=>{viewer?.restart();tick(0);});byId('rate').addEventListener('change',event=>viewer?.setRate(Number(event.target.value)));
    byId('focus').addEventListener('click',()=>viewer?.focus(selected));byId('map-reset').addEventListener('click',()=>viewer?.fit());topButton.addEventListener('click',()=>viewer?.fit(true));
    byId('add').disabled=true;
    try{
        viewer=window.createLogisticsMap({jobs,onSelect:select,onCity:selectCity,onTick:tick,onReady(){ready=true;byId('add').disabled=false;requestAnimationFrame(()=>select(selected));}});
        window.logisticsMap3D=viewer;
    }catch(error){host.querySelector('.logistics-map-loading').textContent='浏览器无法启用 3D 地图';host.dataset.state='error';console.error(error);}
    window.logisticsWorkspace={setExpanded(open){(open?modalDock:mainDock).append(board);requestAnimationFrame(()=>{viewer?.resize();viewer?.fit();});}};
    if(new URLSearchParams(location.search).get('view')==='logistics')board.scrollIntoView();
    drawList();drawDetail();if(loaded.error)notice(loaded.error);
    window.addEventListener('pagehide',event=>{if(!event.persisted)viewer?.destroy();});
})();
