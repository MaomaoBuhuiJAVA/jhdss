/* Plant archive page. The API is intentionally kept behind the existing
 * common.js helpers so this page also works when the archive tables are empty. */
(function () {
    'use strict';

    var state = {
        plants: [],
        selectedPlant: null,
        currentYear: null,
        archive: null,
        activeBlock: 'base',
        modal: null,
        requestId: 0
    };

    var recordConfig = {
        pheno: {
            title: '物候记录', endpoint: '/phenology', listKey: 'phenology', deleteEndpoint: '/phenology',
            fields: [
                ['stage', '生育阶段', 'text'], ['phase', '物候期', 'text'], ['eventDate', '发生日期', 'date'],
                ['description', '观察描述', 'textarea'], ['photoUrl', '图片', 'image']
            ]
        },
        cult: {
            title: '栽培管理', endpoint: '/cultivation', listKey: 'cultivation', deleteEndpoint: '/cultivation',
            fields: [
                ['month', '月份', 'number'], ['waterFrequency', '灌溉频次', 'text'], ['fertilize', '施肥', 'text'],
                ['pruning', '修剪', 'text'], ['trellis', '整枝/搭架', 'text'], ['weeding', '除草', 'text'],
                ['repot', '换盆/移栽', 'text'], ['other', '其他农事', 'text'], ['remark', '备注', 'textarea']
            ]
        },
        pest: {
            title: '病虫害与逆境', endpoint: '/pest', listKey: 'pestDisease', deleteEndpoint: '/pest',
            fields: [
                ['recordType', '记录类型', 'text'], ['pestName', '病虫害名称', 'text'], ['occurDate', '发生日期', 'date'],
                ['symptom', '症状/表现', 'textarea'], ['severity', '严重程度', 'text'], ['measureType', '措施类型', 'text'],
                ['measure', '处理措施', 'textarea'], ['effect', '处理效果', 'textarea'], ['photoUrl', '图片', 'image']
            ]
        },
        growth: {
            title: '生长观测', endpoint: '/growth', listKey: 'growthRecords', deleteEndpoint: '/growth',
            fields: [
                ['recordDate', '观测日期', 'date'], ['heightCm', '株高（cm）', 'number'], ['crownWidthCm', '冠幅（cm）', 'number'],
                ['leafCount', '叶片数', 'number'], ['flowerCount', '花朵数', 'number'], ['fruitCount', '果实数', 'number'],
                ['photoUrl', '图片', 'image'], ['photoNo', '照片编号', 'text'], ['remark', '备注', 'textarea']
            ]
        }
    };

    function esc(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function text(value, fallback) {
        return value == null || value === '' ? (fallback === undefined ? '--' : fallback) : String(value);
    }

    function dateValue(value) {
        if (!value) return '';
        if (typeof value === 'string') return value.slice(0, 10);
        var d = new Date(value);
        if (isNaN(d.getTime())) return '';
        return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
    }

    function notify(message, isError) {
        var toast = document.getElementById('toast');
        if (!toast) return;
        toast.textContent = message || '';
        toast.className = isError ? 'error' : 'show';
        clearTimeout(toast._timer);
        toast._timer = setTimeout(function () { toast.className = ''; }, 2600);
    }

    function isOk(res) {
        return !!res && (res.code === 200 || res.code === 0);
    }

    function resultData(res, fallback) {
        return isOk(res) && res.data != null ? res.data : fallback;
    }

    function setLoading(target, message) {
        if (!target) return;
        target.innerHTML = '<div class="empty-state"><i class="ri-loader-4-line spinning"></i><span>' + esc(message || '加载中…') + '</span></div>';
    }

    async function loadPlants() {
        var list = document.getElementById('plant-list');
        setLoading(list, '正在加载档案…');
        var input = document.getElementById('search-input');
        var clear = document.getElementById('search-clear');
        var keyword = input ? input.value.trim() : '';
        if (clear) clear.hidden = !keyword;
        var request = ++state.requestId;
        var query = keyword ? '?keyword=' + encodeURIComponent(keyword) : '';
        var res = await apiGet('/plant-archive/plants' + query);
        if (request !== state.requestId) return;
        if (!isOk(res)) {
            state.plants = [];
            renderPlantList('档案接口暂不可用');
            return;
        }
        state.plants = Array.isArray(res.data) ? res.data : [];
        renderPlantList();
        if (!state.plants.length) {
            clearArchiveView();
            return;
        }
        var selectedId = state.selectedPlant && state.selectedPlant.id;
        var item = state.plants.find(function (entry) { return entry.plant && entry.plant.id === selectedId; }) || state.plants[0];
        if (item && item.plant) await selectPlant(item.plant.id, item);
    }

    function renderPlantList(emptyMessage) {
        var list = document.getElementById('plant-list');
        if (!list) return;
        list.innerHTML = '';
        var count = document.getElementById('plant-count');
        if (count) count.textContent = state.plants.length ? state.plants.length + ' 份档案' : '';
        if (!state.plants.length) {
            list.innerHTML = '<div class="empty-state"><i class="ri-inbox-line"></i><span>' + esc(emptyMessage || '暂无植株档案') + '</span><button type="button" class="empty-action" onclick="openCreatePlant()">新建档案</button></div>';
            return;
        }
        state.plants.forEach(function (entry) {
            var plant = entry.plant || entry;
            if (!plant || plant.id == null) return;
            var years = Array.isArray(entry.years) ? entry.years : [];
            var item = document.createElement('button');
            item.type = 'button';
            item.className = 'plant-item' + (state.selectedPlant && state.selectedPlant.id === plant.id ? ' active' : '');
            item.onclick = function () { selectPlant(plant.id, entry); };
            item.innerHTML = '<span class="plant-avatar"><i class="ri-seedling-line"></i></span>' +
                '<span class="plant-copy"><strong>' + esc(text(plant.plantName, '未命名植株')) + '</strong>' +
                '<small>' + esc(text(plant.variety || plant.scientificName, '品种未填写')) + '</small></span>' +
                '<span class="plant-meta"><b>' + esc(years.length ? years.length + ' 年' : '待建档') + '</b>' +
                (entry.grade ? '<em>' + esc(entry.grade) + '</em>' : '') + '</span>';
            list.appendChild(item);
        });
    }

    async function selectPlant(id, entry) {
        var plant = entry && entry.plant ? entry.plant : null;
        var res = await apiGet('/plant-archive/plants/' + encodeURIComponent(id));
        if (isOk(res) && res.data) plant = res.data;
        if (!plant) return;
        state.selectedPlant = plant;
        renderPlantList();
        var years = entry && Array.isArray(entry.years) ? entry.years.slice() : [];
        if (!years.length) {
            years = resultData(await apiGet('/plant-archive/plants/' + id + '/years'), []) || [];
        }
        years = years.map(Number).filter(function (year) { return !isNaN(year); }).sort(function (a, b) { return b - a; });
        state.selectedPlant.years = years;
        state.currentYear = years[0] || new Date().getFullYear();
        renderPlantTitle();
        renderYearTabs();
        await loadYearArchive();
    }

    function renderPlantTitle() {
        var title = document.getElementById('top-title');
        if (!title) return;
        var p = state.selectedPlant;
        if (!p) {
            title.innerHTML = '<div class="placeholder-title"><i class="ri-file-search-line"></i><span>请选择或新建植株档案</span></div>';
            return;
        }
        title.innerHTML = '<div class="title-avatar"><i class="ri-seedling-line"></i></div><div class="title-copy"><h1>' +
            esc(text(p.plantName, '未命名植株')) + '</h1><p>' + esc(text(p.variety || p.scientificName, '品种未填写')) +
            (p.plantLocation ? ' · ' + esc(p.plantLocation) : '') + '</p></div>' +
            '<div class="title-actions"><button type="button" class="icon-btn" onclick="openEditPlant()" title="编辑基础档案"><i class="ri-edit-line"></i></button>' +
            '<button type="button" class="icon-btn danger" onclick="deleteCurrentPlant()" title="删除植株档案"><i class="ri-delete-bin-line"></i></button></div>';
    }

    function renderYearTabs() {
        var tabs = document.getElementById('year-tabs');
        if (!tabs) return;
        tabs.innerHTML = '';
        if (!state.selectedPlant) return;
        var years = state.selectedPlant.years || [];
        years.forEach(function (year) {
            var button = document.createElement('button');
            button.type = 'button';
            button.className = 'year-tab' + (Number(year) === Number(state.currentYear) ? ' active' : '');
            button.textContent = year + ' 年';
            button.onclick = function () { selectYear(year); };
            tabs.appendChild(button);
        });
        var add = document.createElement('button');
        add.type = 'button'; add.className = 'year-add'; add.title = '新建年度档案';
        add.innerHTML = '<i class="ri-add-line"></i><span>年度</span>'; add.onclick = openCreateYear;
        tabs.appendChild(add);
    }

    async function selectYear(year) {
        state.currentYear = Number(year);
        renderYearTabs();
        await loadYearArchive();
    }

    async function loadYearArchive() {
        if (!state.selectedPlant || !state.currentYear) return;
        var blocks = document.querySelectorAll('.block');
        blocks.forEach(function (block) { setLoading(block, '正在加载年度档案…'); });
        var res = await apiGet('/plant-archive/plants/' + state.selectedPlant.id + '/years/' + state.currentYear);
        state.archive = isOk(res) && res.data ? res.data : {
            plant: state.selectedPlant, yearRecord: null, phenology: [], cultivation: [], pestDisease: [], growthRecords: []
        };
        if (!state.archive.plant) state.archive.plant = state.selectedPlant;
        renderAllBlocks();
    }

    function clearArchiveView() {
        state.selectedPlant = null; state.currentYear = null; state.archive = null;
        renderPlantTitle(); renderYearTabs();
        document.querySelectorAll('.block').forEach(function (block) {
            block.innerHTML = '<div class="empty-state"><i class="ri-file-search-line"></i><span>选择植株后查看年度档案</span></div>';
        });
    }

    function renderAllBlocks() {
        renderBase(); renderRecordBlock('pheno'); renderRecordBlock('cult'); renderRecordBlock('pest'); renderRecordBlock('growth'); renderSummary();
        switchBlock(state.activeBlock || 'base');
    }

    function blockHeader(icon, title, action, actionLabel) {
        return '<div class="block-head"><div><i class="' + icon + '"></i><strong>' + esc(title) + '</strong></div>' +
            (action ? '<button type="button" class="btn-outline" onclick="' + action + '"><i class="ri-add-line"></i>' + esc(actionLabel || '新增') + '</button>' : '') + '</div>';
    }

    function infoPair(label, value) {
        return '<div class="info-pair"><span>' + esc(label) + '</span><b>' + esc(text(value)) + '</b></div>';
    }

    function renderBase() {
        var box = document.getElementById('block-base');
        if (!box) return;
        var p = state.selectedPlant || (state.archive && state.archive.plant);
        if (!p) { box.innerHTML = '<div class="empty-state"><i class="ri-file-search-line"></i><span>选择植株后查看档案</span></div>'; return; }
        var photo = p.mainPhoto ? '<img class="plant-photo" src="' + esc(p.mainPhoto) + '" alt="植株照片" onerror="this.hidden=true">' : '<div class="plant-photo placeholder"><i class="ri-seedling-line"></i></div>';
        box.innerHTML = blockHeader('ri-file-user-line', '基础档案') +
            '<div class="base-grid"><div class="profile-card">' + photo + '<div class="profile-name"><strong>' + esc(text(p.plantName, '未命名植株')) + '</strong><span>' + esc(text(p.scientificName, '学名未填写')) + '</span></div></div>' +
            '<div class="info-grid">' + infoPair('植物名称', p.plantName) + infoPair('科属', p.familyGenus) + infoPair('品种', p.variety) + infoPair('来源类型', p.sourceType) +
            infoPair('来源渠道', p.sourceChannel) + infoPair('定植日期', dateValue(p.plantDate)) + infoPair('种植位置', p.plantLocation) + infoPair('土壤类型', p.soilType) +
            infoPair('基质比例', p.substrateRatio) + infoPair('光照环境', p.lightEnv) + infoPair('种植规格', p.plantingSpec) + infoPair('备注', p.remark) + '</div></div>';
    }

    function recordRows(key) {
        if (!state.archive) return [];
        var rows = state.archive[key];
        return Array.isArray(rows) ? rows : [];
    }

    function rowSummary(block, row) {
        if (block === 'pheno') return '<strong>' + esc(text(row.stage || row.phase, '未命名阶段')) + '</strong><span>' + esc(text(row.eventDate, '日期未填写')) + '</span><p>' + esc(text(row.description, '暂无描述')) + '</p>';
        if (block === 'cult') return '<strong>' + esc((row.month ? row.month + ' 月' : '未标注月份')) + '</strong><span>' + esc(text(row.waterFrequency, '灌溉未记录')) + '</span><p>' + esc([row.fertilize, row.pruning, row.trellis, row.weeding, row.repot, row.other].filter(Boolean).join(' · '), '暂无农事记录') + '</p>';
        if (block === 'pest') return '<strong>' + esc(text(row.pestName || row.recordType, '未命名记录')) + '</strong><span>' + esc(text(row.occurDate, '日期未填写')) + '</span><p>' + esc(text(row.symptom || row.measure, '暂无症状或措施')) + '</p>';
        return '<strong>' + esc(text(row.recordDate, '日期未填写')) + '</strong><span>株高 ' + esc(text(row.heightCm, '--')) + ' cm · 冠幅 ' + esc(text(row.crownWidthCm, '--')) + ' cm</span><p>叶 ' + esc(text(row.leafCount, '--')) + ' · 花 ' + esc(text(row.flowerCount, '--')) + ' · 果 ' + esc(text(row.fruitCount, '--')) + '</p>';
    }

    function renderRecordBlock(block) {
        var box = document.getElementById('block-' + block);
        var cfg = recordConfig[block];
        if (!box || !cfg) return;
        var rows = recordRows(cfg.listKey);
        var content = blockHeader(block === 'pheno' ? 'ri-calendar-check-line' : block === 'cult' ? 'ri-tools-line' : block === 'pest' ? 'ri-bug-2-line' : 'ri-line-chart-line', cfg.title, "openRecordModal('" + block + "')", '新增记录');
        if (!rows.length) content += '<div class="empty-state compact"><i class="ri-inbox-line"></i><span>本年度暂无记录</span><button type="button" class="empty-action" onclick="openRecordModal(\'' + block + '\')">添加第一条</button></div>';
        else {
            content += '<div class="record-list">';
            rows.forEach(function (row) {
                content += '<article class="record-row"><div class="record-copy">' + rowSummary(block, row) + '</div><div class="record-actions"><button type="button" class="icon-btn" onclick="openRecordModal(\'' + block + '\',' + row.id + ')" title="编辑"><i class="ri-edit-line"></i></button><button type="button" class="icon-btn danger" onclick="deleteRecord(\'' + block + '\',' + row.id + ')" title="删除"><i class="ri-delete-bin-line"></i></button></div></article>';
            });
            content += '</div>';
        }
        box.innerHTML = content;
    }

    function renderSummary() {
        var box = document.getElementById('block-sum');
        if (!box) return;
        var r = state.archive && state.archive.yearRecord ? state.archive.yearRecord : {};
        box.innerHTML = blockHeader('ri-medal-line', '年终总结') + '<div class="summary-grid">' +
            '<div class="summary-score"><span>生长评级</span><b>' + esc(text(r.growthGrade, '待评估')) + '</b><small>' + esc(state.currentYear || '') + ' 年档案</small></div>' +
            '<div class="summary-copy"><div><span>年度总结</span><p>' + esc(text(r.annualSummary, '尚未填写年度总结')) + '</p></div><div><span>问题复盘</span><p>' + esc(text(r.problemReview, '尚未填写问题复盘')) + '</p></div><div><span>改进建议</span><p>' + esc(text(r.improvementSuggestion, '尚未填写改进建议')) + '</p></div></div>' +
            '</div><div class="summary-actions"><button type="button" class="btn-outline" onclick="openSummaryModal()"><i class="ri-edit-line"></i>编辑年终总结</button><button type="button" class="btn-danger" onclick="deleteCurrentYear()"><i class="ri-delete-bin-line"></i>删除本年度</button></div>';
    }

    function switchBlock(block) {
        state.activeBlock = block || 'base';
        document.querySelectorAll('.btab').forEach(function (tab) {
            var active = tab.getAttribute('data-b') === state.activeBlock;
            tab.classList.toggle('active', active); tab.setAttribute('aria-selected', active ? 'true' : 'false');
        });
        document.querySelectorAll('.block').forEach(function (panel) {
            var active = panel.id === 'block-' + state.activeBlock;
            panel.classList.toggle('show', active); panel.hidden = !active;
        });
    }

    function fieldHtml(field, value) {
        var name = field[0], label = field[1], type = field[2], val = type === 'date' ? dateValue(value) : text(value, '');
        if (type === 'image') return imageUploadFieldHtml(name, label, value);
        if (type === 'textarea') return '<label class="form-group full"><span class="form-label">' + esc(label) + '</span><textarea class="form-input" name="' + esc(name) + '" rows="3">' + esc(val) + '</textarea></label>';
        return '<label class="form-group"><span class="form-label">' + esc(label) + '</span><input class="form-input" name="' + esc(name) + '" type="' + esc(type) + '" value="' + esc(val) + '"' + (type === 'number' ? ' step="any"' : '') + '></label>';
    }

    function imageUploadFieldHtml(name, label, value) {
        var photoUrl = text(value, '');
        var hasPhoto = !!photoUrl;
        return '<section class="form-group full archive-image-field" data-image-picker data-image-field="' + esc(name) + '">' +
            '<span class="form-label">' + esc(label) + '</span>' +
            '<input type="hidden" name="' + esc(name) + '" value="' + esc(photoUrl) + '" data-image-value>' +
            '<input type="file" class="archive-image-file" accept="image/jpeg,image/png,image/gif" data-image-file>' +
            '<div class="archive-image-preview' + (hasPhoto ? ' has-image' : '') + '" data-image-preview>' +
                '<img' + (hasPhoto ? ' src="' + esc(photoUrl) + '"' : '') + ' alt="' + esc(label) + '预览" data-image-preview-image>' +
                '<span class="archive-image-empty" data-image-empty' + (hasPhoto ? ' hidden' : '') + '><i class="ri-image-add-line"></i>未选择图片</span>' +
                '<span class="archive-image-status" data-image-status></span>' +
            '</div>' +
            '<div class="archive-image-actions">' +
                '<button class="btn-outline" type="button" data-image-select><i class="ri-upload-2-line"></i>选择图片</button>' +
                '<button class="btn-ghost" type="button" data-image-clear' + (hasPhoto ? '' : ' disabled') + '><i class="ri-delete-bin-line"></i>移除</button>' +
            '</div>' +
            '<p class="archive-image-tip">支持 JPG、PNG、GIF，文件不超过 10 MB。</p>' +
        '</section>';
    }

    function plantFormHtml(plant) {
        var fields = [
            ['plantName', '植物名称', 'text'], ['scientificName', '学名', 'text'], ['familyGenus', '科属', 'text'], ['variety', '品种', 'text'],
            ['sourceType', '来源类型', 'text'], ['sourceChannel', '来源渠道', 'text'], ['plantDate', '定植日期', 'date'], ['plantLocation', '种植位置', 'text'],
            ['soilType', '土壤类型', 'text'], ['substrateRatio', '基质比例', 'text'], ['lightEnv', '光照环境', 'text'], ['plantingSpec', '种植规格', 'text']
        ];
        return '<div class="form-grid">' + fields.map(function (field) { return fieldHtml(field, plant && plant[field[0]]); }).join('') +
            imageUploadFieldHtml('mainPhoto', '主图', plant && plant.mainPhoto) + fieldHtml(['remark', '备注', 'textarea'], plant && plant.remark) + '</div>';
    }

    function imageElements(picker) {
        return {
            input: picker.querySelector('[data-image-file]'),
            value: picker.querySelector('[data-image-value]'),
            preview: picker.querySelector('[data-image-preview]'),
            image: picker.querySelector('[data-image-preview-image]'),
            empty: picker.querySelector('[data-image-empty]'),
            status: picker.querySelector('[data-image-status]'),
            select: picker.querySelector('[data-image-select]'),
            clear: picker.querySelector('[data-image-clear]')
        };
    }

    function setImagePreview(picker, photoUrl) {
        var elements = imageElements(picker);
        var hasPhoto = !!photoUrl;
        if (elements.value) elements.value.value = photoUrl || '';
        if (elements.image) {
            if (hasPhoto) elements.image.src = photoUrl;
            else elements.image.removeAttribute('src');
        }
        if (elements.preview) elements.preview.classList.toggle('has-image', hasPhoto);
        if (elements.empty) elements.empty.hidden = hasPhoto;
        if (elements.clear) elements.clear.disabled = !hasPhoto;
    }

    function setImageUploadState(picker, uploading, message) {
        var elements = imageElements(picker);
        if (elements.select) elements.select.disabled = uploading;
        if (elements.clear) elements.clear.disabled = uploading || !(elements.value && elements.value.value);
        if (elements.status) {
            elements.status.textContent = message || '';
            elements.status.classList.toggle('show', !!message);
        }
    }

    async function uploadArchiveImage(file, picker) {
        if (!file) return;
        var acceptedName = /\.(jpe?g|png|gif)$/i.test(file.name || '');
        if (!/^image\/(jpeg|png|gif)$/i.test(file.type || '') && !acceptedName) {
            notify('请选择 JPG、PNG 或 GIF 图片', true);
            return;
        }
        if (file.size > 10 * 1024 * 1024) {
            notify('图片不能超过 10 MB', true);
            return;
        }
        var uploadModal = state.modal;
        if (uploadModal) uploadModal.imageUploadCount = (uploadModal.imageUploadCount || 0) + 1;
        setImageUploadState(picker, true, '图片上传中…');
        try {
            var formData = new FormData();
            formData.append('file', file);
            var response = await fetch(API_BASE + '/plant-archive/uploads/image', { method: 'POST', body: formData });
            var result = await response.json();
            if (state.modal !== uploadModal) return;
            if (!isOk(result) || !result.data) {
                notify((result && result.msg) || '图片上传失败', true);
                return;
            }
            setImagePreview(picker, result.data);
            notify('图片已上传');
        } catch (error) {
            console.warn('Plant archive image upload failed:', error);
            if (state.modal === uploadModal) notify('图片上传失败，请检查服务后重试', true);
        } finally {
            if (state.modal === uploadModal) {
                uploadModal.imageUploadCount = Math.max(0, (uploadModal.imageUploadCount || 1) - 1);
                if (document.body.contains(picker)) setImageUploadState(picker, false, '');
            }
        }
    }

    function bindImagePickers() {
        var body = document.getElementById('modal-body');
        if (!body) return;
        body.querySelectorAll('[data-image-picker]').forEach(function (picker) {
            var elements = imageElements(picker);
            if (elements.select && elements.input) elements.select.addEventListener('click', function () { elements.input.click(); });
            if (elements.input) elements.input.addEventListener('change', function () {
                var file = elements.input.files && elements.input.files[0];
                uploadArchiveImage(file, picker);
            });
            if (elements.clear) elements.clear.addEventListener('click', function () {
                if (state.modal && state.modal.imageUploadCount) return;
                if (elements.input) elements.input.value = '';
                setImagePreview(picker, '');
            });
        });
    }

    function openModal(title, html, modal) {
        var overlay = document.getElementById('modal-overlay');
        if (!overlay) return;
        state.modal = modal;
        document.getElementById('modal-title').textContent = title;
        document.getElementById('modal-body').innerHTML = html;
        bindImagePickers();
        overlay.hidden = false;
        document.body.style.overflow = 'hidden';
        var first = overlay.querySelector('input,textarea,select'); if (first) setTimeout(function () { first.focus(); }, 20);
    }

    function closeModal() {
        var overlay = document.getElementById('modal-overlay');
        if (!overlay) return;
        overlay.hidden = true; state.modal = null; document.body.style.overflow = '';
    }

    function openCreatePlant() {
        openModal('新建植株档案', plantFormHtml({}), { type: 'plant-create' });
    }

    function openEditPlant() {
        if (!state.selectedPlant) return notify('请先选择植株档案', true);
        var p = state.selectedPlant;
        openModal('编辑基础档案', plantFormHtml(p), { type: 'plant-edit', id: p.id });
    }

    function openCreateYear() {
        if (!state.selectedPlant) return notify('请先选择植株档案', true);
        openModal('新建年度档案', '<div class="form-grid one"><label class="form-group"><span class="form-label">档案年份</span><input class="form-input" name="year" type="number" min="2000" max="2100" value="' + new Date().getFullYear() + '"></label></div>', { type: 'year-create' });
    }

    function openSummaryModal() {
        if (!state.selectedPlant) return;
        var r = state.archive && state.archive.yearRecord ? state.archive.yearRecord : {};
        var fields = [['growthGrade', '生长评级', 'text'], ['annualSummary', '年度总结', 'textarea'], ['problemReview', '问题复盘', 'textarea'], ['improvementSuggestion', '改进建议', 'textarea']];
        openModal((state.currentYear || '') + ' 年终总结', '<div class="form-grid one">' + fields.map(function (f) { return fieldHtml(f, r[f[0]]); }).join('') + '</div>', { type: 'summary', id: r.id });
    }

    function openRecordModal(block, id) {
        if (!state.selectedPlant || !recordConfig[block]) return;
        var cfg = recordConfig[block];
        var row = id && state.archive && recordRows(cfg.listKey).find(function (item) { return Number(item.id) === Number(id); });
        var form = '<div class="form-grid">' + cfg.fields.map(function (f) { return fieldHtml(f, row && row[f[0]]); }).join('') + '</div>';
        openModal((id ? '编辑' : '新增') + cfg.title, form, { type: 'record', block: block, id: id || null });
    }

    function collectForm() {
        var body = document.getElementById('modal-body');
        var data = {};
        if (!body) return data;
        body.querySelectorAll('[name]').forEach(function (el) {
            var value = el.value.trim();
            if (el.type === 'number') data[el.name] = value === '' ? null : Number(value);
            else if (el.hasAttribute('data-image-value')) data[el.name] = value;
            else data[el.name] = value === '' ? null : value;
        });
        return data;
    }

    async function modalOnOk() {
        if (!state.modal) return;
        if (state.modal.imageUploadCount) return notify('图片正在上传，请稍候', true);
        var okButton = document.getElementById('modal-ok');
        if (okButton) okButton.disabled = true;
        var m = state.modal, data = collectForm(), res;
        try {
            if (m.type === 'plant-create') res = await apiPost('/plant-archive/plants', data);
            else if (m.type === 'plant-edit') res = await apiPut('/plant-archive/plants/' + m.id, data);
            else if (m.type === 'year-create') {
                var year = Number(data.year);
                if (!year || year < 2000 || year > 2100) { notify('请输入正确年份', true); return; }
                res = await apiPost('/plant-archive/plants/' + state.selectedPlant.id + '/years?year=' + year, {});
            } else if (m.type === 'summary') {
                data.id = m.id || null; data.plantId = state.selectedPlant.id; data.year = state.currentYear;
                res = await apiPost('/plant-archive/year-record', data);
            } else if (m.type === 'record') {
                data.id = m.id || null; data.plantId = state.selectedPlant.id; data.year = state.currentYear;
                res = await apiPost('/plant-archive' + recordConfig[m.block].endpoint, data);
            }
            if (!isOk(res)) { notify((res && res.msg) || '保存失败', true); return; }
            closeModal(); notify('保存成功');
            var selectedId = state.selectedPlant && state.selectedPlant.id;
            await loadPlants();
            if (selectedId && state.selectedPlant && state.selectedPlant.id === selectedId) await loadYearArchive();
        } finally {
            if (okButton) okButton.disabled = false;
        }
    }

    async function deleteRecord(block, id) {
        var cfg = recordConfig[block];
        if (!cfg || !id || !window.confirm('确定删除这条记录吗？')) return;
        var res = await apiDelete('/plant-archive' + cfg.deleteEndpoint + '?id=' + encodeURIComponent(id));
        if (!isOk(res)) return notify((res && res.msg) || '删除失败', true);
        notify('记录已删除'); await loadYearArchive();
    }

    async function deleteCurrentPlant() {
        if (!state.selectedPlant || !window.confirm('删除后将同时移除全部年度档案，确定继续吗？')) return;
        var id = state.selectedPlant.id;
        var res = await apiDelete('/plant-archive/plants/' + id);
        if (!isOk(res)) return notify((res && res.msg) || '删除失败', true);
        state.selectedPlant = null; notify('档案已删除'); await loadPlants();
    }

    async function deleteCurrentYear() {
        if (!state.selectedPlant || !state.currentYear || !window.confirm('确定删除 ' + state.currentYear + ' 年档案及其明细吗？')) return;
        var res = await apiDelete('/plant-archive/plants/' + state.selectedPlant.id + '/years/' + state.currentYear);
        if (!isOk(res)) return notify((res && res.msg) || '删除失败', true);
        notify('年度档案已删除'); await loadPlants();
    }

    function clearPlantSearch() {
        var input = document.getElementById('search-input'); if (input) input.value = '';
        loadPlants();
    }

    document.addEventListener('DOMContentLoaded', function () {
        loadPlants();
        window.setInterval(function () {
            var focused = document.activeElement;
            var searching = focused && focused.id === 'search-input';
            if (!state.modal && !searching) loadPlants();
        }, 30000);
        var input = document.getElementById('search-input');
        if (input) input.addEventListener('input', function () {
            var clear = document.getElementById('search-clear'); if (clear) clear.hidden = !input.value.trim();
        });
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') closeModal();
        });
    });

    window.loadPlants = loadPlants;
    window.openCreatePlant = openCreatePlant;
    window.openEditPlant = openEditPlant;
    window.openCreateYear = openCreateYear;
    window.openSummaryModal = openSummaryModal;
    window.openRecordModal = openRecordModal;
    window.switchBlock = switchBlock;
    window.closeModal = closeModal;
    window.modalOnOk = modalOnOk;
    window.deleteRecord = deleteRecord;
    window.deleteCurrentPlant = deleteCurrentPlant;
    window.deleteCurrentYear = deleteCurrentYear;
    window.clearPlantSearch = clearPlantSearch;
})();
