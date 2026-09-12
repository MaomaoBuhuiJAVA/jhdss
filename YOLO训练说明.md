# 黑天牛 YOLO11s 训练说明

## 已准备好的内容

- 虚拟环境：`D:\jhdss-tools\yolo-venv311`
- 预训练权重：`D:\jhdss-tools\yolo-models\yolo11s.pt`
- 数据集：`E:\LabelImg资料图片\yolo_dataset`
- 数据配置：`E:\LabelImg资料图片\yolo_dataset\dataset.yaml`
- 训练集：150 张；验证集：37 张
- 摄像头测试图：`E:\LabelImg资料图片\yolo_dataset\camera_test`（45 张，无标注）

## 开始训练

在 PowerShell 中进入本项目目录，执行：

```powershell
.\train_yolo11s.ps1
```

训练结果会保存到：
`E:\LabelImg资料图片\yolo_runs\black_longhorn_yolo11s`

主要参数已经写入脚本：100 个 epoch、早停 patience 30、输入尺寸 1024、batch 2、`workers=0`、不使用磁盘缓存、RTX 3060（`device=0`）。显存稳定后可把 batch 调到 4。

启动脚本会自动从 E 盘查找 `yolo_dataset\dataset.yaml`，不依赖中文路径编码。

如果训练中断，脚本会自动读取已有的 `last.pt` 并从上次 epoch 继续，不会重复从头训练。

## 测试摄像头照片

训练完成后执行：

```powershell
.\predict_camera.ps1
```

预测结果会保存到：
`E:\LabelImg资料图片\yolo_runs\camera_test`

摄像头照片没有真实标注，因此只能用于查看检测效果，不能计算正式验证指标。需要计算指标时，先用 LabelImg 为这些照片补框，再加入数据集重新划分。
