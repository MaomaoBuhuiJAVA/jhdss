# LabelImg 天牛数据标注操作文档

## 1. 已安装内容

- Python：3.11（用户范围安装，按官方项目兼容性选择）
- 虚拟环境：`D:\jhdss-tools\labelimg-venv311`
- LabelImg：1.8.6
- 界面语言：简体中文（启动脚本已固定选择中文资源）
- 本项目的一键启动脚本：`启动LabelImg.bat`

虚拟环境与项目的 Java、Nginx 服务相互独立，不会修改项目运行环境。

## 2. 启动 LabelImg

在项目根目录双击：

```text
启动LabelImg.bat
```

启动脚本会自动设置 `zh-CN` 语言，并调用 D 盘的 Python 3.11 虚拟环境；不需要手动激活虚拟环境。

启动脚本同时强制 Qt 使用软件渲染并关闭自动高 DPI 缩放。这样可以避开部分显卡驱动在切换图片或拖动标注框时触发的 Qt 崩溃；窗口文字可能略大，这是正常现象。

当前环境还修复了 LabelImg 1.8.6 与新版 PyQt5 的坐标类型兼容问题。原版在按 `W` 后绘制十字线和矩形时，会把小数坐标传给只接受整数的 Qt 绘图接口，表现为 `Qt5Core.dll` 直接闪退。

也可以把图片文件夹拖到这个批处理文件上，LabelImg 会直接打开该文件夹：

```text
启动LabelImg.bat D:\beetle-dataset\images
```

如果双击后没有窗口，打开 PowerShell 执行：

```powershell
D:\jhdss-tools\labelimg-venv311\Scripts\pythonw.exe -m labelImg.labelImg
```

启动脚本直接调用虚拟环境的 Python 模块，并使用已经修复坐标兼容问题的 Python 3.11 环境。不要使用旧的 `D:\jhdss-tools\labelimg-venv` 启动；旧环境没有这些兼容修复。

## 3. 标注前准备

建议将原始照片放在单独目录，例如：

```text
D:\beetle-dataset\raw-images
```

照片应包含真实叶片、枝干和田间背景，同时准备一些没有天牛的负样本图片。

如果只有一种天牛，类别名称统一使用：

```text
longhorn_beetle
```

类别名称必须始终保持完全一致，不要交替使用“天牛”“longhorn”“beetle”等名称。

## 4. 在 LabelImg 中画框

1. 点击菜单 `文件 → 打开目录`，选择图片目录。
2. 点击菜单 `文件 → 改变存放目录`，选择标签保存目录；如果不单独选择，标签会保存到图片目录。
3. 在工具栏选择 `YOLO` 保存格式，不要使用 `PascalVOC/XML` 格式。
4. 点击左侧工具栏的 `创建区块`，或按 `W` 键。
5. 用鼠标从虫体左上角拖到右下角。
6. 在弹出的标签框中输入类别名 `longhorn_beetle`，确认后按 `Ctrl+S` 保存。
7. 按 `D` 查看下一张图片，按 `A` 返回上一张图片。

画框规则：

- 框要尽量贴合虫体外轮廓。
- 包含虫子的身体、明显的足和触角，但不要框入大块叶片或树枝。
- 一张图片有多只天牛，就画多个框。
- 没有天牛的图片不画框，但保留图片作为负样本。
- 对遮挡、截断和远距离目标，所有图片使用一致的标注习惯。

## 5. YOLO 数据集目录

标注完成后，整理成以下结构：

```text
D:\beetle-dataset\
├── images\
│   ├── train\
│   ├── val\
│   └── test\
├── labels\
│   ├── train\
│   ├── val\
│   └── test\
└── data.yaml
```

每张图片必须有同名标签文件：

```text
images\train\beetle001.jpg
labels\train\beetle001.txt
```

标签文件每行一个目标，格式为：

```text
类别编号 中心点X 中心点Y 框宽 框高
```

示例：

```text
0 0.512 0.438 0.220 0.310
```

这些坐标由 LabelImg 自动生成，不要手动填写像素坐标。

建议划分比例：训练集 70%，验证集 20%，测试集 10%。不要把同一段视频连续截取的相似画面随机分到不同集合中。

## 6. data.yaml 示例

```yaml
path: D:/beetle-dataset
train: images/train
val: images/val
test: images/test

names:
  0: longhorn_beetle
```

Windows 路径建议在 YAML 中使用正斜杠 `/`。

## 7. 标注质量检查

训练前逐张检查：

- 框是否漏掉天牛。
- 是否把叶片、树枝框进去了。
- 类别名称是否始终相同。
- 图片和 `.txt` 文件是否同名。
- 没有目标的负样本是否没有错误标签。
- 是否存在空标签文件或损坏图片。

如果框明显偏大或偏小，先修正标签，再进行模型训练。

## 8. 常用快捷键（官方 Hotkeys）

| 快捷键 | 中文功能 |
|---|---|
| `Ctrl+U` | 打开目录 |
| `Ctrl+R` | 改变存放目录 |
| `Ctrl+S` | 保存标签 |
| `Ctrl+Shift+S` | 另存为 |
| `W` | 创建区块（矩形框） |
| `Ctrl+J` | 编辑区块 |
| `Ctrl+E` | 编辑标签名称 |
| `Delete` | 删除选中的区块 |
| `Ctrl+D` | 复制选中的区块 |
| `Ctrl+V` | 复制上一张图片的区块 |
| `A` | 上一张图片 |
| `D` | 下一张图片 |
| `Ctrl+Shift+D` | 删除当前图片 |
| `Space` | 标记/验证当前图片 |
| `Ctrl++` | 放大 |
| `Ctrl+-` | 缩小 |
| `Ctrl+=` | 恢复原始大小 |
| `Ctrl+F` | 适应窗口 |
| `Ctrl+Shift+F` | 适应宽度 |
| `Ctrl+Q` | 退出程序 |

## 9. 减少闪退的操作习惯

- 使用本目录的 `启动LabelImg.bat`，不要直接运行旧 Python 3.12 环境或 `labelImg.exe` 启动器。
- 先选择 `YOLO` 格式，再开始画框。
- 每画完一个目标立即按 `Ctrl+S` 保存，不要积累大量未保存标注。
- 图片建议使用普通 JPG/PNG；过大的单张图片先缩小，建议最长边不超过 6000 像素。
- 图片和标签放在本地磁盘目录，不要直接从微信临时目录、U 盘或网络共享目录标注。
- 如果某一张图片一打开就闪退，先把它转换为 JPG 或重新导出，再继续标注。

## 10. 推荐模型与后续训练

检测天牛位置建议从 YOLO11 开始：

- `yolo11n.pt`：速度快，适合性能较弱的电脑。
- `yolo11s.pt`：精度和速度较均衡，建议优先使用。

安装训练工具：

```powershell
D:\jhdss-tools\labelimg-venv311\Scripts\python.exe -m pip install ultralytics
```

开始训练：

```powershell
D:\jhdss-tools\labelimg-venv311\Scripts\yolo.exe detect train data=D:/beetle-dataset/data.yaml model=yolo11s.pt imgsz=640 epochs=100 batch=8
```

训练结果中的最佳模型通常位于：

```text
runs\detect\train\weights\best.pt
```

## 11. 常见问题

### LabelImg 无法启动

确认 Python 环境存在：

```powershell
Test-Path D:\jhdss-tools\labelimg-venv311\Scripts\pythonw.exe
```

结果应为 `True`。优先双击项目根目录的 `启动LabelImg.bat`。

如果标注时仍然闪退，请先完全退出 LabelImg，再重新双击 `启动LabelImg.bat`。不要从旧的 Python 3.12 环境启动，也不要直接双击 `labelImg.exe`，否则可能使用未修复的环境或缺少软件渲染参数。

若只有某一张图片触发闪退，把该图片复制到本地临时目录并转换为普通 JPG（最长边建议不超过 6000 像素），然后重新标注。若所有图片都闪退，请在 PowerShell 执行以下命令，把输出保存下来以便继续定位：

```powershell
& 'D:\jhdss-tools\labelimg-venv311\Scripts\python.exe' -c "import os; print('QT_OPENGL=', os.environ.get('QT_OPENGL')); import PyQt5.QtCore as q; print(q.QT_VERSION_STR, q.PYQT_VERSION_STR)"
```

### 标签保存成 XML

在 LabelImg 顶部工具栏切换为 `YOLO` 格式，然后重新保存。YOLO 标签扩展名应为 `.txt`。

### 框选后类别列表不统一

删除错误类别，统一使用 `longhorn_beetle`，否则训练时会被识别成多个类别。

### 模型误识别其他昆虫

增加其他昆虫、枯叶、树皮和空场景图片作为负样本，再重新训练。
