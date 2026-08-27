(function() {
    'use strict';

    var messages = document.getElementById('messages');
    var inputBox = document.getElementById('inputBox');
    var sendBtn = document.getElementById('sendBtn');
    var uploadBtn = document.getElementById('uploadBtn');
    var imageInput = document.getElementById('imageInput');
    var imagePreview = document.getElementById('imagePreview');
    var previewImg = document.getElementById('previewImg');
    var removeImageBtn = document.getElementById('removeImageBtn');
    var newChatBtn = document.getElementById('newChatBtn');
    var memoryToggle = document.getElementById('memoryToggle');

    var isStreaming = false;
    var eventSource = null;
    var pendingImage = null;
    var memoryEnabled = false;

    var API_BASE = window.location.origin + '/jhds/api/ai';

    var LOCAL_KNOWLEDGE = [
        {
            keywords: ['调整三号种植区c1角落区', '调整c1角落区', '调整', '硬件'],
            answer: '3号种植区C1角落区目前需要降低大气湿度，建议在硬件方面进行调整，提高3号种植区C1角落区的通风透气条件，调高天窗角度，提速内循环机，为该区域提供较优通风条件。'
        },
        {
            keywords: ['褐斑病发生条件', '褐斑病爆发', '褐斑病'],
            answer: '温湿度：核心诱因\n\n' +
                '• 高湿度是必要条件：褐斑病的流行与降雨和园内湿度大直接相关。病菌孢子需要在水滴中才能萌发和侵染。只要叶片结露或湿润6小时以上，或者降雨量超过2毫米，病菌就可能完成侵染。\n' +
                '• 温度决定发病速度：褐斑病菌在10-35℃的广泛温度范围内都能生长，但最活跃的温度区间是20-25℃。温度越高，病菌从侵染到发病的“潜育期”越短，病害蔓延越快。\n\n' +
                '降雨与传播：病害流行推手\n\n' +
                '• 降雨是传播媒介：风雨是褐斑病菌传播的主要途径。雨水将病叶上的孢子冲溅到健康叶片上，造成初次感染和后续的再侵染。\n' +
                '• 多雨年份易暴发：连续的阴雨天气或高湿环境会迅速导致病害流行。特别是在高温之后的降雨，或“高温高湿”的天气，极容易导致褐斑病暴发。\n\n' +
                '寄主与栽培管理：不可忽视的因素\n\n' +
                '• 病原菌基数与越冬：上一年病叶大量残留的果园，是褐斑病发生的重要菌源。病菌通常在病叶上越冬，等到次年温湿度适宜时开始新一轮侵染。\n' +
                '• 树体抗性与长势：树势衰弱的植株更易感病。\n' +
                '• 果园环境与管理：\n' +
                '  - 通风透光差：种植密度过高、枝条郁闭、通风透光不良的果园，会为病害创造有利的小气候。\n' +
                '  - 排水不良：地势低洼、排水不畅的果园，土壤和空气湿度大，发病会更严重。\n' +
                '  - 管理不当：用药不及时、方法不对路也是导致褐斑病严重发生的重要原因。'
        },
        {
            keywords: ['白粉病发生条件', '白粉病爆发', '白粉病'],
            answer: '病原菌：专性寄生，善于潜伏\n\n' +
                '樱桃白粉病由专性寄生真菌Podosphaera cerasi（或异名P. clandestina）引起。其生活史分为两个阶段：\n' +
                '• 越冬与初侵染：病菌在落叶或树皮裂缝中以闭囊壳越冬。春季温湿度合适时释放子囊孢子，成为初侵染源。\n' +
                '• 循环与再侵染：初侵染后产生大量分生孢子，通过风力传播，在生长季内反复侵染。\n\n' +
                '温湿度：微妙的“平衡”\n\n' +
                '白粉病的发生需要特定的温湿度组合，与许多喜湿病害不同，它的条件更微妙。\n' +
                '• 温度：分生孢子萌发和菌丝生长的最适温度为20-25℃。孢子萌发温度范围很宽（4-35℃），但温度高于30℃时生长会受到抑制。\n' +
                '• 湿度：高湿度（>70%）非常有利于病害发展。值得注意的是，与多数真菌不同，白粉病孢子不需要叶面水膜即可萌发。相反，长时间的雨水或灌溉水反而会冲刷掉孢子，抑制病害。\n' +
                '• 最佳组合：“暖干日+凉湿夜”是病害流行的理想天气模式。\n\n' +
                '易感组织：幼嫩组织是主要目标\n\n' +
                '病菌主要侵染幼嫩、正在展开的叶片。随着叶片成熟，表皮形成蜡质层和角质层，会逐渐产生对白粉病的抗性。果实则在幼果期较为感病，随着糖分积累，抗性会逐渐增强。\n\n' +
                '栽培管理：人为创造不利条件\n\n' +
                '• 郁闭果园：种植过密、树冠郁闭、通风透光不良的果园，湿度高，利于发病。\n' +
                '• 灌溉方式：滴灌或喷灌可能为病害的初侵染提供所需湿度。\n\n' +
                '发生规律：关键时期\n\n' +
                '• 初侵染期：春季萌芽后，遇0.1英寸（约2.5mm）的降雨或灌溉，且温度达到10℃以上时，可能诱发初侵染。\n' +
                '• 高发期：晚春至初夏，若天气温暖湿润，病害会迅速蔓延。'
        },
        {
            keywords: ['c1角落区环境', 'c1角落区', '三号种植区环境', '三号种植区', 'c1'],
            answer: '三号种植区内环境情况良好，根据目前实时数据所示，3号种植区内大气温度22.65°C，大气湿度95.95%，土壤温度19.31°C，土壤湿度26.42%，光照强度35007.00 lux，二氧化碳浓度707.67 ppm。但请注意，3号种植区的C1角落区可能存在高湿环境，褐斑病喜低温高湿，角落区发生褐斑病概率较大，请及时前往现场检查。'
        },
        {
            keywords: ['采收标准', '采收'],
            answer: '根据物联网系统的全域监测，目前检测到：4号种植区95.6%樱桃达到采收标准，果实饱满，色泽鲜艳，结果较多，可集中采收！\n\n' +
                '一、采收时间\n' +
                '宜在清晨或傍晚低温时段采摘，避免高温时段采收。应避开烈日和雨后采摘。建议在早晨8:00之前或下午5:00之后进行采摘。\n\n' +
                '二、采收方法\n' +
                '用手握住果实，连同果柄一起摘下，尽量保持果柄完整。具体操作为：用手握住果柄，用食指顶住果柄基部，轻轻掀起采下。轻采轻放，严禁挤压，从源头减少机械损伤。\n\n' +
                '三、田间初选\n' +
                '采摘后在树下或田间即时进行初选分级：先将病果、虫果、残果剔除，再将果实按大小分级放入果箱。合格果与次果应分开放置，次果统一无害化处理。'
        },
        {
            keywords: ['采后处理', '采后', '保存', '处理'],
            answer: '1. 预冷——争分夺秒\n' +
                '采后2小时是果实保鲜的“黄金窗口”。樱桃采收时正值高温天气，果温常接近30℃，两三天内就会变软变色。采后应尽快预冷，将果温降至0～2℃。\n' +
                '• 水预冷：通过水循环快速带走果实热量，能在短时间内将樱桃果温从20～30℃降至5～8℃。\n' +
                '• 压差预冷：适合规模化处理，可有效提升冷链水平。\n' +
                '• 冷库预冷：适用于部分品种，但需注意不同品种适用不同预冷方式。\n\n' +
                '2. 分级\n' +
                '分级应严格按照果实大小、色泽、成熟度等进行。可采用自动化分级设备：\n' +
                '• 水包膜分级：让果实全程“漂”在水里完成分选，几乎没有碰伤，分级准确率超过85%。\n' +
                '• 微型电脑选果机：可对单果实现重量分级，分选能力约每小时7200颗果实（100公斤左右），体积小、便于移动，适合大棚或果农散户使用。\n' +
                '• 分级效率比人工分级提高8～10倍。\n\n' +
                '3. 包装\n' +
                '• 将樱桃装入带分格的防震泡沫托中，每颗单独固定，避免运输中相互挤压磕碰。\n' +
                '• 放置吸水保鲜纸，维持低温环境。\n' +
                '• 采用功能性保鲜包装（如MAP气调包装），结合保鲜剂和杀菌剂，0℃下贮藏30天，樱桃腐烂率可控制在3%以下。\n' +
                '• 贴上NFC测温记录标签和溯源二维码，全程记录运输温度变化，确保全程处于0～4℃安全区间。\n\n' +
                '4. 冷链运输\n' +
                '甜樱桃采后应及时入冷库预冷，外运果实宜当天采摘、当天分级包装、当天装冷藏库或冷藏气调车发运。用普通汽车运输的，应将果实预冷至2℃左右时再装车运输。\n\n' +
                '5. 全流程减损技术体系\n' +
                '目前行业内已集成“酶降解海藻寡糖采前保鲜—智慧压差预冷—自动化分级分选—功能性保鲜包装—高效相变蓄冷配送”等成套设备与技术，可搭建完整的樱桃全流程减损技术体系。技术落地后累计新增收益显著，有效补齐了樱桃产业链储运短板。\n\n' +
                '四、采收后树体管理\n' +
                '采收后应及时进行以下工作：\n' +
                '• 施“月子肥”：采果后7～10天，施用速效肥补充树体营养。\n' +
                '• 适度修剪：疏除影响树冠内光照的枝，提高花芽质量。\n' +
                '• 撤膜管理：采收后当外界气候适宜时，进行通风锻炼20天以上再撤膜，进入露地管理。\n\n' +
                '总结：大棚樱桃采果是一个系统工程，核心可概括为“采前控水控氮、适时标准采收、快速预冷分级、全程冷链保鲜”四个关键环节，环环相扣，缺一不可。'
        },
        {
            keywords: ['二号种植区环境', '二号种植区'],
            answer: '警告！二号种植区疑似光合速率较低！传感器数据显示：二号种植区目前大气温度：23.70℃，大气湿度69.82%，土壤温度18.49℃，土壤湿度26.42%，光照强度31332.70 lux，二氧化碳浓度167.46 ppm，二氧化碳浓度过低，可能影响植株光合速率，请及时前往检查！'
        }
    ];

    function findLocalAnswer(text) {
        var normalized = text.toLowerCase().replace(/\s+/g, '');
        for (var i = 0; i < LOCAL_KNOWLEDGE.length; i++) {
            var entry = LOCAL_KNOWLEDGE[i];
            for (var j = 0; j < entry.keywords.length; j++) {
                if (normalized.indexOf(entry.keywords[j].toLowerCase()) !== -1) {
                    return entry.answer;
                }
            }
        }
        return null;
    }

    if (memoryToggle) {
        memoryToggle.addEventListener('change', function() {
            memoryEnabled = this.checked;
        });
    }

    function addMessage(text, isUser, imageBase64) {
        var div = document.createElement('div');
        div.className = 'message' + (isUser ? ' user' : ' ai');

        var avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.textContent = isUser ? '\uD83D\uDC64' : '\uD83E\uDD16';

        var content = document.createElement('div');
        content.className = 'message-content';

        var sender = document.createElement('div');
        sender.className = 'message-sender';
        sender.textContent = isUser ? '我' : 'AI 农业助手';

        var textDiv = document.createElement('div');
        textDiv.className = 'message-text';

        if (isUser && imageBase64) {
            var img = document.createElement('img');
            img.className = 'message-image';
            img.src = 'data:image/jpeg;base64,' + imageBase64;
            textDiv.appendChild(img);
        }

        var textP = document.createElement('p');
        textP.textContent = text;
        textDiv.appendChild(textP);

        content.appendChild(sender);
        content.appendChild(textDiv);
        div.appendChild(avatar);
        div.appendChild(content);
        messages.appendChild(div);
        scrollToBottom();

        return textDiv;
    }

    function addStreamingMessage() {
        var div = document.createElement('div');
        div.className = 'message ai';
        div.id = 'streamingMsg';

        var avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.textContent = '\uD83E\uDD16';

        var content = document.createElement('div');
        content.className = 'message-content';

        var sender = document.createElement('div');
        sender.className = 'message-sender';
        sender.textContent = 'AI 农业助手';

        var textDiv = document.createElement('div');
        textDiv.className = 'message-text';
        textDiv.innerHTML = '<div class="thinking-indicator">'
            + '<span class="thinking-emoji">\uD83E\uDD14</span>'
            + '<span class="thinking-text">大模型正在思考中</span>'
            + '<span class="thinking-dots"><span>.</span><span>.</span><span>.</span></span>'
            + '</div>';

        content.appendChild(sender);
        content.appendChild(textDiv);
        div.appendChild(avatar);
        div.appendChild(content);
        messages.appendChild(div);
        scrollToBottom();

        return { container: textDiv };
    }

    function addNoticeMessage(text) {
        var div = document.createElement('div');
        div.style.cssText = 'text-align:center;padding:10px 16px;margin:4px 0;font-size:12px;color:var(--accent-caution,#f0a040);background:rgba(240,160,64,0.08);border-radius:8px;border:1px solid rgba(240,160,64,0.2);';
        div.textContent = '\uD83D\uDD04 ' + text;
        messages.appendChild(div);
        scrollToBottom();
    }

    function scrollToBottom() {
        requestAnimationFrame(function() {
            messages.scrollTop = messages.scrollHeight;
        });
    }

    function setStreaming(streaming) {
        isStreaming = streaming;
        sendBtn.disabled = streaming;
        inputBox.disabled = streaming;
        if (!streaming) {
            inputBox.focus();
        }
    }

    function autoResizeTextarea() {
        inputBox.style.height = 'auto';
        var newHeight = Math.min(inputBox.scrollHeight, 120);
        inputBox.style.height = newHeight + 'px';
    }

    inputBox.addEventListener('input', autoResizeTextarea);

    inputBox.addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    sendBtn.addEventListener('click', sendMessage);

    if (newChatBtn) {
        newChatBtn.addEventListener('click', function() {
            if (memoryEnabled) {
                fetch(API_BASE + '/clear', { method: 'POST' });
            }
            clearMessages();
        });
    }

    function clearMessages() {
        messages.innerHTML = '';
        addWelcomeMessage();
    }

    function addWelcomeMessage() {
        var div = document.createElement('div');
        div.className = 'message ai';

        var avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.textContent = '\uD83E\uDD16';

        var content = document.createElement('div');
        content.className = 'message-content';

        var sender = document.createElement('div');
        sender.className = 'message-sender';
        sender.textContent = 'AI 农业助手';

        var textDiv = document.createElement('div');
        textDiv.className = 'message-text';
        textDiv.innerHTML = '<p>您好！我是jhds智慧农业AI助手，您可以问我任何农业相关问题，我会为您智能解答。</p>'
            + '<div class="welcome-suggestions">'
            + '<span class="welcome-suggestion" onclick="document.getElementById(\'inputBox\').value=\'樱桃种植需要注意什么\'; autoResizeTextarea();">樱桃种植注意事项</span>'
            + '<span class="welcome-suggestion" onclick="document.getElementById(\'inputBox\').value=\'常见病虫害防治方法\'; autoResizeTextarea();">病虫害防治</span>'
            + '<span class="welcome-suggestion" onclick="document.getElementById(\'inputBox\').value=\'温室大棚温湿度管理\'; autoResizeTextarea();">温室管理</span>'
            + '<span class="welcome-suggestion" onclick="document.getElementById(\'inputBox\').value=\'智慧农业有哪些应用\'; autoResizeTextarea();">智慧农业应用</span>'
            + '</div>';

        content.appendChild(sender);
        content.appendChild(textDiv);
        div.appendChild(avatar);
        div.appendChild(content);
        messages.appendChild(div);
        scrollToBottom();
    }

    /* ========== Image Upload ========== */

    uploadBtn.addEventListener('click', function() {
        imageInput.click();
    });

    imageInput.addEventListener('change', function(e) {
        var file = e.target.files[0];
        if (!file) return;

        if (!file.type.startsWith('image/')) {
            alert('请选择图片文件');
            return;
        }

        if (file.size > 10 * 1024 * 1024) {
            alert('图片不能超过 10MB');
            return;
        }

        var reader = new FileReader();
        reader.onload = function(ev) {
            var rawBase64 = ev.target.result.split(',')[1];
            try {
                pendingImage = compressImage(rawBase64, file.size);
            } catch (e) {
                pendingImage = Promise.resolve(rawBase64);
            }
            pendingImage.then(function(compressed) {
                pendingImage = compressed;
                previewImg.src = 'data:image/jpeg;base64,' + compressed;
                imagePreview.style.display = 'inline-block';
            });
        };
        reader.readAsDataURL(file);

        imageInput.value = '';
    });

    removeImageBtn.addEventListener('click', function() {
        pendingImage = null;
        imagePreview.style.display = 'none';
        previewImg.src = '';
    });

    function compressImage(base64, fileSize) {
        if (fileSize <= 100 * 1024) {
            return Promise.resolve(base64);
        }
        return new Promise(function(resolve, reject) {
            var img = new Image();
            img.onload = function() {
                var MAX_DIM = 1024;
                var w = img.width, h = img.height;
                if (w > MAX_DIM || h > MAX_DIM) {
                    var ratio = Math.min(MAX_DIM / w, MAX_DIM / h);
                    w = Math.round(w * ratio);
                    h = Math.round(h * ratio);
                }
                var canvas = document.createElement('canvas');
                canvas.width = w;
                canvas.height = h;
                var ctx = canvas.getContext('2d');
                ctx.drawImage(img, 0, 0, w, h);
                canvas.toBlob(function(blob) {
                    var reader = new FileReader();
                    reader.onload = function(e) {
                        resolve(e.target.result.split(',')[1]);
                    };
                    reader.onerror = reject;
                    reader.readAsDataURL(blob);
                }, 'image/jpeg', 0.6);
            };
            img.onerror = reject;
            img.src = 'data:image/jpeg;base64,' + base64;
        });
    }

    /* ========== Send Message ========== */

    function sendMessage() {
        var text = inputBox.value.trim();
        var hasImage = pendingImage != null;

        if (!text && !hasImage) return;
        if (isStreaming) return;

        var sendImage = pendingImage;
        pendingImage = null;
        imagePreview.style.display = 'none';
        previewImg.src = '';

        inputBox.value = '';
        autoResizeTextarea();

        addMessage(text || '识别图片', true, sendImage);

        var streamMsg = addStreamingMessage();
        var fullText = '';

        setStreaming(true);

        var localAnswer = sendImage ? null : findLocalAnswer(text);
        if (localAnswer) {
            window.setTimeout(function() {
                streamMsg.container.textContent = localAnswer;
                scrollToBottom();
                setStreaming(false);
            }, 260);
            return;
        }

        var url = API_BASE + '/stream';

        var body = { msg: text || '请描述这张图片' };
        if (sendImage) {
            body.image = sendImage;
        }
        body.memory = memoryEnabled;

        fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        })
        .then(function(response) {
            if (!response.ok) {
                throw new Error('HTTP error: ' + response.status);
            }

            var reader = response.body.getReader();
            var decoder = new TextDecoder();
            var buffer = '';

            var currentEvent = '';
            var noticed = false;
            var firstChunk = true;

            function readNext() {
                reader.read().then(function(result) {
                    if (result.done) {
                        if (!noticed) {
                            streamMsg.container.textContent = fullText;
                            scrollToBottom();
                        }
                        setStreaming(false);
                        return;
                    }

                    buffer += decoder.decode(result.value, { stream: true });
                    var lines = buffer.split('\n');
                    buffer = lines.pop() || '';

                    for (var i = 0; i < lines.length; i++) {
                        var line = lines[i].trim();
                        if (line.startsWith('event:')) {
                            currentEvent = line.substring(6).trim();
                            continue;
                        }
                        if (line.startsWith('data:')) {
                            var data = line.substring(5).trim();
                            if (data === '[DONE]') {
                                continue;
                            }
                            if (currentEvent === 'notice') {
                                if (!noticed) {
                                    noticed = true;
                                    streamMsg.container.innerHTML = '';
                                    addNoticeMessage(data);
                                }
                            } else {
                                if (firstChunk) {
                                    firstChunk = false;
                                    streamMsg.container.innerHTML = '';
                                }
                                fullText += data;
                            }
                        }
                    }

                    if (!noticed && streamMsg) {
                        streamMsg.container.textContent = fullText;
                    }
                    scrollToBottom();
                    readNext();
                }).catch(function(err) {
                    console.error('Stream read error:', err);
                    if (!noticed && streamMsg) {
                        streamMsg.container.textContent = fullText || '接收数据失败';
                    }
                    scrollToBottom();
                    setStreaming(false);
                });
            }

            readNext();
        })
        .catch(function(err) {
            streamMsg.container.textContent = '请求失败: ' + err.message;
            scrollToBottom();
            setStreaming(false);
        });
    }

    autoResizeTextarea();
    addWelcomeMessage();
})();
