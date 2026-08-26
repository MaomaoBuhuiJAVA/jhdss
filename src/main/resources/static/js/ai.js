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
