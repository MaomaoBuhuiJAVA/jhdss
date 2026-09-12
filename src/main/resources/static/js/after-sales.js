(function(){
    const rows=Array.from(document.querySelectorAll('#feedback-body tr'));
    const search=document.getElementById('feedback-search');
    const topic=document.getElementById('topic-filter');
    const severity=document.getElementById('severity-filter');
    const channel=document.getElementById('channel-filter');
    function filterRows(topicOverride){
        if(typeof topicOverride==='string') topic.value=topicOverride;
        const query=(search.value||'').trim().toLowerCase();
        rows.forEach(function(row){
            row.hidden=!!((query&&!row.textContent.toLowerCase().includes(query))||(topic.value&&row.dataset.topic!==topic.value)||(severity.value&&row.dataset.severity!==severity.value)||(channel.value&&row.dataset.channel!==channel.value));
        });
        document.querySelector('.feedback-panel').scrollIntoView({behavior:'smooth',block:'center'});
    }
    [search,topic,severity,channel].forEach(function(control){control.addEventListener(control===search?'input':'change',function(){filterRows();});});
    document.querySelectorAll('.problem-block').forEach(function(button){button.addEventListener('click',function(){filterRows(button.dataset.topic);});});
    const drawer=document.getElementById('trace-drawer');
    document.querySelectorAll('[data-trace]').forEach(function(button){button.addEventListener('click',function(){document.getElementById('trace-title').textContent='批次 '+button.dataset.trace+' 溯源';drawer.hidden=false;});});
    document.querySelector('.trace-close').addEventListener('click',function(){drawer.hidden=true;});
    const overlay=document.getElementById('logistics-overlay');
    function setLogistics(open){overlay.hidden=!open;document.body.style.overflow=open?'hidden':'';if(window.logisticsWorkspace)window.logisticsWorkspace.setExpanded(open);}
    document.getElementById('open-logistics').addEventListener('click',function(){setLogistics(true);});
    document.getElementById('close-logistics').addEventListener('click',function(){setLogistics(false);});
    overlay.addEventListener('click',function(event){if(event.target===overlay)setLogistics(false);});
    document.getElementById('logistics-to-sales').addEventListener('click',function(){setLogistics(false);document.querySelector('.feedback-panel').scrollIntoView({behavior:'smooth'});});
    document.addEventListener('keydown',function(event){if(document.querySelector('dialog[open]'))return;if(event.key==='3'&&!/INPUT|SELECT|TEXTAREA/.test(event.target.tagName)){event.preventDefault();setLogistics(true);}if(event.key==='Escape'){setLogistics(false);drawer.hidden=true;}});
    document.getElementById('export-feedback').addEventListener('click',function(){
        const visible=rows.filter(function(row){return !row.hidden;});
        const csv=['反馈时间,渠道与批次,客户原始反馈,自动主题,严重度,情绪,处理状态'].concat(visible.map(function(row){return Array.from(row.cells).slice(0,7).map(function(cell){return '"'+cell.innerText.replace(/\s+/g,' ').replace(/"/g,'""')+'"';}).join(',');})).join('\r\n');
        const link=document.createElement('a');link.href=URL.createObjectURL(new Blob(['\ufeff'+csv],{type:'text/csv'}));link.download='售后反馈明细.csv';link.click();URL.revokeObjectURL(link.href);
    });
    document.getElementById('feedback-upload').addEventListener('change',function(event){const file=event.target.files[0];if(file)window.alert('已读取 '+file.name+'，待人工确认后导入。');event.target.value='';});
})();
