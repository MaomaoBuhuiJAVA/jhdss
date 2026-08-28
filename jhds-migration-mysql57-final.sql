-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: jhds
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `jhds`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `jhds` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `jhds`;

--
-- Table structure for table `ai_knowledge_entry`
--

DROP TABLE IF EXISTS `ai_knowledge_entry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_knowledge_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `keywords` text NOT NULL COMMENT '关键词，逗号、换行或竖线分隔',
  `answer` text NOT NULL COMMENT '关键词对应回答',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用 0否1是',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '匹配顺序',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_knowledge_enabled` (`enabled`,`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='页面：AI农业助手；区域：关键词问答；数据：关键词、回答和启用状态。';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_knowledge_entry`
--

LOCK TABLES `ai_knowledge_entry` WRITE;
/*!40000 ALTER TABLE `ai_knowledge_entry` DISABLE KEYS */;
INSERT INTO `ai_knowledge_entry` VALUES (1,'褐斑病,褐斑病爆发,褐斑病发生条件','温湿度是褐斑病的核心诱因：叶片结露或湿润6小时以上、降雨超过2毫米时容易侵染；病菌最活跃温度为20-25℃。连续阴雨、高温高湿、通风透光差、排水不良或树势衰弱都会加重病害，应及时清理病叶、改善通风并按植保方案用药。',1,1,'2026-08-28 08:47:52',NULL),(2,'白粉病,白粉病爆发,白粉病发生条件','樱桃白粉病由专性寄生真菌引起，最适温度20-25℃，相对湿度超过70%有利于发展；暖干日加凉湿夜最易流行。幼嫩叶片和幼果更易感病，应改善通风透光，避免过密和不当喷灌，并在春季初侵染期及时防治。',1,2,'2026-08-28 08:47:52',NULL),(3,'c1,c1角落区,c1角落区环境,三号种植区,三号种植区环境','三号种植区当前环境总体良好，但C1角落区可能存在高湿环境。实时数据约为大气温度22.65℃、湿度95.95%、土壤温度19.31℃、土壤湿度26.42%、光照35007lux、二氧化碳707.67ppm；褐斑病喜低温高湿，请及时到现场检查。',1,3,'2026-08-28 08:47:52',NULL),(4,'调整,调整三号种植区c1角落区,调整c1角落区,硬件','建议降低三号种植区C1角落区的大气湿度：调高天窗角度、提速内循环机，改善该区域通风透气条件。',1,4,'2026-08-28 08:47:52',NULL),(5,'采收,采收标准','物联网全域监测显示，4号种植区约95.6%的樱桃达到采收标准，可集中采收。建议清晨或傍晚低温时段连同果柄轻采，现场剔除病果、虫果和残果并按大小分级。',1,5,'2026-08-28 08:47:52',NULL),(6,'保存,采后处理,采后,处理','樱桃采后应在2小时内预冷，将果温降至0-2℃，再进行分级、防震包装和0-4℃冷链运输；同步做好吸水保鲜纸、NFC温度标签和采后树体施肥、修剪管理。',1,6,'2026-08-28 08:47:52',NULL),(7,'二号种植区,二号种植区环境','警告：二号种植区疑似光合速率较低。当前二氧化碳约167.46ppm，可能影响光合速率；请结合温湿度、土壤和光照数据及时到现场检查并改善通风、补充二氧化碳。',1,7,'2026-08-28 08:47:52',NULL);
/*!40000 ALTER TABLE `ai_knowledge_entry` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_learn_card`
--

DROP TABLE IF EXISTS `ai_learn_card`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_learn_card` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `video_id` bigint NOT NULL COMMENT '关联视频ID',
  `image_url` varchar(500) NOT NULL COMMENT '知识卡片图片路径',
  `card_title` varchar(255) NOT NULL COMMENT '知识卡片标题',
  `description` text COMMENT '图片对应讲解',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用 0否1是',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '卡片排序',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_learn_card_video` (`video_id`,`enabled`,`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='页面：AI学习；区域：知识卡片；数据：图片、标题、讲解和展示状态。';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_learn_card`
--

LOCK TABLES `ai_learn_card` WRITE;
/*!40000 ALTER TABLE `ai_learn_card` DISABLE KEYS */;
INSERT INTO `ai_learn_card` VALUES (1,1,'/jhds/ai-learn-media/1/图片1.png','第1项学习要点','苗木筛选与质量把控\n对照标准测量苗高（≥0.8m），检查主干粗细均匀度、侧根数量与健壮程度，剔除偏细、弯曲、根系稀疏或发黑的苗木。',1,1,'2026-08-28 08:47:52',NULL),(2,1,'/jhds/ai-learn-media/1/图片2.png','第2项学习要点','接穗处理与嫁接操作\n选取健壮嫩梢削取带少量木质部的盾形芽片；砧木斜切形成嵌合切口，将芽片嵌入并对齐形成层，用嫁接膜密封固定；另有劈接方式：砧木劈开2–3cm，接穗削成楔形插入并对准形成层后固定。',1,2,'2026-08-28 08:47:52',NULL),(3,1,'/jhds/ai-learn-media/1/图片3.png','第3项学习要点','根系修剪与无土栽培准备\n剪除细弱须根和交叉缠绕根，保留健壮主根与侧根，将剪口修成45°斜面；依次用多菌灵浸泡30分钟杀菌、生根粉溶液促根；同时配制均匀无分层的混合基质。',1,3,'2026-08-28 08:47:52',NULL),(4,1,'/jhds/ai-learn-media/1/图片4.png','第4项学习要点','定干与促枝处理\n种植袋底部铺珍珠岩，苗木根系自然舒展后填充基质并压实，确保根颈露出基质±1cm；在主干70cm处定干，选芽刻伤并涂抹发枝素促枝；插入竹竿固定苗木，浇透定根水（分两次浇灌）。',1,4,'2026-08-28 08:47:52',NULL),(5,2,'/jhds/ai-learn-media/2/图片5.png','第1项学习要点','叶色诊断法\n学习要点：通过叶片颜色变化初步判断缺素类型\n案例应用：叶片褪绿、叶脉间呈现褪绿条纹 → 初步判断为缺镁',1,1,'2026-08-28 08:47:52',NULL),(6,2,'/jhds/ai-learn-media/2/图片6.png','第2项学习要点','症状记录与数据更新\n学习要点：发现异常症状时及时拍照记录，并上传至AI数据库，用于后续识别模型训练\n（这两段话放在一个框里出现）',1,2,'2026-08-28 08:47:52',NULL),(7,2,'/jhds/ai-learn-media/2/图片7.png','第3项学习要点','快捷光合作用速率仪\n学习要点：掌握仪器的使用方法，测定植株的光合速率\n数据意义：光合速率是判断植物生理状态的重要指标',1,3,'2026-08-28 08:47:52',NULL),(8,3,'/jhds/ai-learn-media/3/图片10.png','第1项学习要点','ai巡检全域拍摄采集\n基于温室内温度、湿度等环境参数，利用系统模型预测病虫害风险（如红色预警为高风险）；操作AI摄像头搭载高光谱摄像头规划巡检航线，采集图像并生成报告，提取病虫害种类、位置、严重程度等信息。',1,1,'2026-08-28 08:47:52',NULL),(9,3,'/jhds/ai-learn-media/3/图片11.png','第2项学习要点','人工采样与实验室诊断\n当AI无法精准识别时，启动人工采样：在病叶健康交界处刮取病灶（病原物最集中），通过载玻片制片（滴无菌水、加盖玻片排除气泡）后，使用光学显微镜观察病原形态（如卵形孢子判定为灰霉病），完成确诊。',1,2,'2026-08-28 08:47:52',NULL),(10,3,'/jhds/ai-learn-media/3/图片8.png','第3项学习要点','药剂配制与精准施药\n根据诊断结果选择对症药剂（如灰霉病用50%湿霉利可湿性粉剂1500倍液），利用植保无人机进行精准变量施药；同时掌握蜂卡悬挂技术（位置、高度、密度），辅助生物防治。',1,3,'2026-08-28 08:47:52',NULL),(11,3,'/jhds/ai-learn-media/3/图片9.png','第4项学习要点','标本制作与资源库建设\n采用针插法制作害虫标本，使用标准扎网框固定；采集病叶制作病害标本（处理、保存）。标本用于培训、科普、科研及AI模型训练，丰富教学与识别资源。',1,4,'2026-08-28 08:47:52',NULL);
/*!40000 ALTER TABLE `ai_learn_card` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_learn_video`
--

DROP TABLE IF EXISTS `ai_learn_video`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_learn_video` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `video_name` varchar(255) NOT NULL COMMENT '上传视频文件名',
  `folder_key` varchar(50) NOT NULL COMMENT '资料目录标识',
  `group_title` varchar(200) NOT NULL COMMENT '知识卡片分组标题',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用 0否1是',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '视频排序',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `video_name` (`video_name`(191)),
  KEY `idx_ai_learn_video_enabled` (`enabled`,`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='页面：AI学习；区域：上传视频匹配；数据：视频文件名、资料目录和分组。';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_learn_video`
--

LOCK TABLES `ai_learn_video` WRITE;
/*!40000 ALTER TABLE `ai_learn_video` DISABLE KEYS */;
INSERT INTO `ai_learn_video` VALUES (1,'视频1.MP4','1','樱桃苗木培育与定植',1,1,'2026-08-28 08:47:52',NULL),(2,'视频2.MP4','2','缺素诊断与仪器操作',1,2,'2026-08-28 08:47:52',NULL),(3,'视频3.MP4','3','病虫害预警与防治',1,3,'2026-08-28 08:47:52',NULL);
/*!40000 ALTER TABLE `ai_learn_video` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `alarm_record`
--

DROP TABLE IF EXISTS `alarm_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alarm_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '????',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '????',
  `level` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '?? urgent/important/normal',
  `source_module` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '???? patrol/weather/nutrient/insect',
  `location` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '??',
  `status` tinyint DEFAULT '0' COMMENT '0=??? 1=???',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `handled_at` datetime DEFAULT NULL COMMENT '????',
  `handling_memo` text COLLATE utf8mb4_general_ci COMMENT '处置说明',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='椤甸潰锛氭姤璀︿腑蹇冿紱鍖哄煙锛氭姤璀﹀垪琛ㄣ?鐘舵?涓庡?缃??鏄庛?鏉ユ簮鍒嗗竷锛涙暟鎹?細鏍囬?銆佹弿杩般?绾у埆銆佹潵婧愩?浣嶇疆銆佸?缃?姸鎬併?璇存槑涓庢椂闂淬?椤甸潰閫氳繃API璇诲彇鍜屼慨鏀规湰琛?紱姘旇薄闃堝?绛夊悗鍙版湇鍔′篃浼氬啓鍏ユ湰琛ㄣ?';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `alarm_record`
--

LOCK TABLES `alarm_record` WRITE;
/*!40000 ALTER TABLE `alarm_record` DISABLE KEYS */;
INSERT INTO `alarm_record` VALUES (1,'发现虫害几棵','AI图像识别检测到种植架1、2出现蚜虫聚集，建议立即进行植保处理','urgent','insect','种植架1、种植架2',0,'2026-03-30 05:30:00',NULL,''),(2,'植株叶面存在杂点','轨道巡检摄像头检测到A2区域植株叶面出现不明杂点，疑似病害早期','important','patrol','A2',0,'2026-03-30 04:45:00',NULL,NULL),(3,'土壤湿度偏低','土壤湿度传感器显示当前湿度45%，略低于设定阈值50%','normal','nutrient',NULL,0,'2026-03-30 03:20:00',NULL,NULL),(4,'风速超过3级','气象站监测到当前风速3.2m/s，建议检查大棚通风口','normal','weather',NULL,0,'2026-03-30 02:15:00',NULL,NULL),(5,'营养液EC值异常','土壤EC值达到2.1mS/cm，超出正常范围1.5-2.0，需调整配液比例','important','nutrient',NULL,0,'2026-03-30 01:30:00',NULL,NULL),(6,'轨道巡检设备离线','AI轨道巡检模块通信中断，已持续5分钟，请检查网络连接','urgent','patrol',NULL,0,'2026-03-30 00:45:00',NULL,NULL),(7,'大棚温度过高','大棚1温度达到38°C，超过预警阈值35°C，建议开启通风降温','important','iot','大棚1',0,'2026-03-30 06:10:00',NULL,NULL),(8,'二氧化碳浓度偏低','大棚2内CO₂浓度降至280ppm，低于光合作用适宜值，建议增施CO₂','normal','iot','大棚2',0,'2026-03-30 05:50:00',NULL,NULL),(9,'光照强度不足','连续阴天导致大棚内光照强度仅8000lux，建议开启补光灯','normal','iot',NULL,0,'2026-03-29 23:30:00',NULL,NULL),(10,'水泵异常停机','灌溉系统B水泵电流异常自动停机，需检查电机和电路','urgent','nutrient','灌溉系统B',0,'2026-03-29 22:15:00',NULL,NULL);
/*!40000 ALTER TABLE `alarm_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `control_log`
--

DROP TABLE IF EXISTS `control_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `control_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `device_alias` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '????',
  `device_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????',
  `value` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '??? open/close',
  `automatic` tinyint DEFAULT '0' COMMENT '0=?? 1=??',
  `send_command` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '???Modbus?',
  `return_command` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????',
  `success` tinyint DEFAULT '0' COMMENT '0=?? 1=??',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=52220 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='鍚庡彴杩愮淮瀹¤?鏃ュ織锛涚敤閫旓細MQTT璁惧?鎺у埗銆佹皵璞″拰鍦熷￥Modbus閲囬泦鐨勬寚浠?鍝嶅簲璁板綍锛涙暟鎹?細璁惧?銆佹帶鍒跺?銆佹寚浠?鍝嶅簲銆佹垚鍔熺姸鎬併?鎵嬭嚜鍔ㄦ潵婧愬拰鏃堕棿銆傚綋鍓嶆棤鍓嶇?椤甸潰鐩存帴灞曠ず鏈?〃銆';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `control_log`
--

LOCK TABLES `control_log` WRITE;
/*!40000 ALTER TABLE `control_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `control_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `dashboard_farm_operation`
--

DROP TABLE IF EXISTS `dashboard_farm_operation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dashboard_farm_operation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `operation_name` varchar(100) NOT NULL COMMENT '农事操作名称',
  `operation_date` date DEFAULT NULL COMMENT '操作日期',
  `icon_class` varchar(100) DEFAULT NULL COMMENT 'Remix图标类名',
  `color_theme` varchar(20) DEFAULT NULL COMMENT '图标颜色主题',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '展示顺序',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_dashboard_operation_sort` (`sort_order`,`operation_date`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='页面：数据大屏；区域：农事操作；数据：已完成农事名称、日期、图标和展示顺序。';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `dashboard_farm_operation`
--

LOCK TABLES `dashboard_farm_operation` WRITE;
/*!40000 ALTER TABLE `dashboard_farm_operation` DISABLE KEYS */;
INSERT INTO `dashboard_farm_operation` VALUES (1,'控温通气','2026-03-28','ri-temp-hot-line','blue',1,'2026-08-28 07:30:47',NULL),(2,'浇水','2026-03-27','ri-drop-line','cyan',2,'2026-08-28 07:30:47',NULL),(3,'施肥','2026-03-27','ri-seedling-line','green',3,'2026-08-28 07:30:47',NULL),(4,'灌根','2026-03-26','ri-bug-line','purple',4,'2026-08-28 07:30:47',NULL);
/*!40000 ALTER TABLE `dashboard_farm_operation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `dashboard_greenhouse`
--

DROP TABLE IF EXISTS `dashboard_greenhouse`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dashboard_greenhouse` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '大棚名称',
  `greenhouse_type` varchar(100) DEFAULT NULL COMMENT '大棚类型',
  `crop_name` varchar(100) DEFAULT NULL COMMENT '作物名称',
  `area` varchar(50) DEFAULT NULL COMMENT '种植面积',
  `plant_count` int DEFAULT NULL COMMENT '定植株数',
  `planting_date` date DEFAULT NULL COMMENT '定植日期',
  `is_primary` tinyint NOT NULL DEFAULT '0' COMMENT '是否当前大棚 0否1是',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '展示顺序',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_dashboard_greenhouse_primary` (`is_primary`,`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='页面：数据大屏；区域：大棚信息；数据：名称、类型、作物、面积、株数和定植日期。';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `dashboard_greenhouse`
--

LOCK TABLES `dashboard_greenhouse` WRITE;
/*!40000 ALTER TABLE `dashboard_greenhouse` DISABLE KEYS */;
INSERT INTO `dashboard_greenhouse` VALUES (1,'种植架1','玻璃体棚','樱桃','1000 m²',1200,'2026-03-28',1,1,'2026-08-28 07:30:47',NULL);
/*!40000 ALTER TABLE `dashboard_greenhouse` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `dashboard_market_feedback`
--

DROP TABLE IF EXISTS `dashboard_market_feedback`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dashboard_market_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title` varchar(200) NOT NULL COMMENT '反馈标题',
  `summary` varchar(255) DEFAULT NULL COMMENT '列表摘要',
  `modal_title` varchar(200) DEFAULT NULL COMMENT '弹窗标题',
  `content` text COMMENT '反馈详情',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否展示 0否1是',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '展示顺序',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_dashboard_market_enabled` (`enabled`,`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='页面：数据大屏；区域：市场反馈弹窗；数据：反馈标题、摘要、详情和是否展示。';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `dashboard_market_feedback`
--

LOCK TABLES `dashboard_market_feedback` WRITE;
/*!40000 ALTER TABLE `dashboard_market_feedback` DISABLE KEYS */;
INSERT INTO `dashboard_market_feedback` VALUES (1,'2025第四批次樱桃市场评价中等','消费者评价数据已更新','消费者反映风味欠佳','据NFC追溯得到的消费者评价数据，56%消费者反映该批次樱桃糖度较低；33%消费者反映消费者反映该批次樱桃酸度过高，9%消费者反映该批次樱桃硬度较低。',1,1,'2026-08-28 07:30:47',NULL);
/*!40000 ALTER TABLE `dashboard_market_feedback` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `dashboard_todo`
--

DROP TABLE IF EXISTS `dashboard_todo`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dashboard_todo` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `week_label` varchar(50) NOT NULL COMMENT '时间标签',
  `task_name` varchar(100) NOT NULL COMMENT '农事类别',
  `action_name` varchar(100) NOT NULL COMMENT '待办操作',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '展示顺序',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_dashboard_todo_sort` (`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='页面：数据大屏；区域：待办农事；数据：时间、农事类别、待办操作和展示顺序。';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `dashboard_todo`
--

LOCK TABLES `dashboard_todo` WRITE;
/*!40000 ALTER TABLE `dashboard_todo` DISABLE KEYS */;
INSERT INTO `dashboard_todo` VALUES (1,'第6周','农事','浇水',1,'2026-08-28 07:30:47',NULL),(2,'第1周','农事','浇水',2,'2026-08-28 07:30:47',NULL),(3,'第1周','施肥','植保',3,'2026-08-28 07:30:47',NULL);
/*!40000 ALTER TABLE `dashboard_todo` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `equipment`
--

DROP TABLE IF EXISTS `equipment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `equipment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '????',
  `alias` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '????',
  `type` int DEFAULT '0' COMMENT '??',
  `open_code` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '?????',
  `close_code` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '?????',
  `return_open_code` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '?????',
  `return_close_code` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '?????',
  `status` tinyint DEFAULT '0' COMMENT '???? 0=?? 1=??',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='椤甸潰锛氱墿鑱旇?澶囥?钀ュ吇娑查厤娑诧紱鍖哄煙锛氬ぇ妫氭帶鍒堕潰鏉垮拰鎵嬪姩妯″紡娉垫帶鍒跺崱鐗囷紱鏁版嵁锛氳?澶囧悕绉?鍒?悕銆佹帶鍒舵寚浠ゅ拰寮?叧鐘舵?銆傜墿鑱旇?澶囬〉浠呮樉绀哄埆鍚嶄互GH寮?ご鐨勮?澶囷紝閰嶆恫椤垫樉绀篜UMP璁惧?銆';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `equipment`
--

LOCK TABLES `equipment` WRITE;
/*!40000 ALTER TABLE `equipment` DISABLE KEYS */;
INSERT INTO `equipment` VALUES (24,'营养液A泵','PUMP_A',1,'','',NULL,NULL,0),(25,'营养液B泵','PUMP_B',1,'','',NULL,NULL,0),(26,'酸液泵','PUMP_ACID',1,'','',NULL,NULL,0),(27,'碱液泵','PUMP_BASE',1,'','',NULL,NULL,0),(28,'灌溉泵','PUMP_IRRIGATE',1,'','',NULL,NULL,0),(29,'搅拌泵','PUMP_MIX',1,'','',NULL,NULL,0),(30,'二氧化碳气肥','PUMP_CO2',1,'','',NULL,NULL,0),(31,'灌溉循环泵','PUMP_CIRCULATION',1,'','',NULL,NULL,0),(32,'氯化钙叶面肥','PUMP_CALCIUM',1,'','',NULL,NULL,0);
/*!40000 ALTER TABLE `equipment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `insect_record`
--

DROP TABLE IF EXISTS `insect_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `insect_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `image_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '??????',
  `species` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '????',
  `count` int DEFAULT '0' COMMENT '????',
  `record_date` date NOT NULL COMMENT '????',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `device_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `thumb_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ai_engine` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `record_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_record_date` (`record_date`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='椤甸潰锛氳櫕鎯呯伅锛涘尯鍩燂細鏈?湴璁板綍鍥惧簱銆佷粖鏃ョ粺璁″拰AI璇嗗埆宸℃?绉嶇被缁熻?锛涙暟鎹?細铏?綋鍥剧墖銆佺?绫汇?鏁伴噺銆佽?澶囥?AI寮曟搸鍜岄噰闆嗘椂闂淬?鍛婅?寮圭獥涓庡?閮ㄧ収鐗囨爣绛鹃〉涓嶈?鍙栨湰琛ㄣ?';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `insect_record`
--

LOCK TABLES `insect_record` WRITE;
/*!40000 ALTER TABLE `insect_record` DISABLE KEYS */;
INSERT INTO `insect_record` VALUES (2,'/jhds/images/alerts/fruit-fly-detection.png','果蝇',5,'2026-08-28','2026-08-28 07:30:48','AI_PATROL','/jhds/images/alerts/fruit-fly-detection.png','AI识别巡检','2026-08-28 15:30:48'),(3,'/jhds/images/alerts/red-spider-suspected.png','红蜘蛛（疑似）',0,'2026-08-28','2026-08-28 07:30:48','AI_PATROL','/jhds/images/alerts/red-spider-suspected.png','AI识别巡检','2026-08-28 15:30:48'),(4,'/jhds/images/alerts/longhorn-beetle-detection.png','桃红颈天牛',1,'2026-08-28','2026-08-28 07:30:48','AI_PATROL','/jhds/images/alerts/longhorn-beetle-detection.png','AI识别巡检','2026-08-28 15:30:48');
/*!40000 ALTER TABLE `insect_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `irrigation_record`
--

DROP TABLE IF EXISTS `irrigation_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `irrigation_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `mode` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '?? manual/auto',
  `pump_alias` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '???',
  `duration` int DEFAULT '0' COMMENT '???????',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='椤甸潰锛氳惀鍏绘恫閰嶆恫锛涘尯鍩燂細鐏屾簤缁熻?鍗′笌AI鍐崇瓥鏃ュ織锛涙暟鎹?細鎵嬪姩/鑷?姩/AI妯″紡銆佹车鍒?悕銆佽繍琛屾椂闀垮拰鎵ц?鏃堕棿銆傞〉闈㈣仛鍚堟樉绀烘湰鏃ユ?鏁般?杩愯?鏃堕暱銆佷及绠楃敤姘撮噺鍜屾渶杩戣?褰曘?';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `irrigation_record`
--

LOCK TABLES `irrigation_record` WRITE;
/*!40000 ALTER TABLE `irrigation_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `irrigation_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `irrigation_schedule`
--

DROP TABLE IF EXISTS `irrigation_schedule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `irrigation_schedule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `schedule_time` time NOT NULL COMMENT '????',
  `duration` int DEFAULT '10' COMMENT '????????',
  `frequency` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'daily' COMMENT '?? daily/alternate/custom',
  `enabled` tinyint DEFAULT '1' COMMENT '0=?? 1=??',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `updated_at` datetime DEFAULT NULL COMMENT '????',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='椤甸潰锛氳惀鍏绘恫閰嶆恫锛涘尯鍩燂細鑷?姩妯″紡鐨勮嚜鍔ㄧ亴婧夎?鍒掕〃鍗曪紱鏁版嵁锛氳?鍒掓椂闂淬?杩愯?鏃堕暱銆侀?鐜囧拰鍚?敤鐘舵?銆傞〉闈?繚瀛樸?鍥炴樉鍜屽悗鍙拌皟搴﹀潎璇诲彇鏈?〃銆';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `irrigation_schedule`
--

LOCK TABLES `irrigation_schedule` WRITE;
/*!40000 ALTER TABLE `irrigation_schedule` DISABLE KEYS */;
/*!40000 ALTER TABLE `irrigation_schedule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `page_alert_content`
--

DROP TABLE IF EXISTS `page_alert_content`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `page_alert_content` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `alert_key` varchar(80) NOT NULL COMMENT '页面告警键',
  `title` varchar(200) NOT NULL COMMENT '告警卡片标题',
  `summary` varchar(500) DEFAULT NULL COMMENT '卡片摘要',
  `modal_title` varchar(200) DEFAULT NULL COMMENT '详情弹窗标题',
  `description` text COMMENT '详情说明',
  `images_json` text COMMENT '详情图片URL JSON数组',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否展示 0否1是',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '展示顺序',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `alert_key` (`alert_key`),
  KEY `idx_page_alert_enabled` (`enabled`,`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='椤甸潰锛氭暟鎹?ぇ灞忋?铏?儏鐏??AI杞ㄩ亾宸℃?锛涘尯鍩燂細椤甸潰涓撶敤鍛婅?鍗＄墖鍜岃?鎯呭脊绐楋紱鏁版嵁锛氭爣棰樸?璇存槑銆侀潤鎬佸浘鐗嘦RL鍜屽睍绀哄紑鍏炽?';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `page_alert_content`
--

LOCK TABLES `page_alert_content` WRITE;
/*!40000 ALTER TABLE `page_alert_content` DISABLE KEYS */;
INSERT INTO `page_alert_content` VALUES (1,'dashboard-graft','嫁接苗异常告警','⚠️嫁接苗存在异常特征，请及时处理！','嫁接苗异常告警','⚠️嫁接苗存在异常特征，请及时处理！','[\"/jhds/images/alerts/graft-union-anomaly.png\",\"/jhds/images/alerts/graft-cut-anomaly.png\"]',1,1,'2026-08-28 07:53:09',NULL),(2,'insect-pest','发现害虫！','点击查看 AI 巡检详情','发现害虫！','发现5只果蝇、1只桃红颈天牛，同时部分害虫无法捕获，叶面有失绿斑，且有部分红色斑点，疑似红蜘蛛','[\"/jhds/images/alerts/fruit-fly-detection.png\",\"/jhds/images/alerts/red-spider-suspected.png\",\"/jhds/images/alerts/longhorn-beetle-detection.png\"]',1,2,'2026-08-28 07:53:09',NULL),(3,'patrol-flower','⚠️花朵数量严重超标！','点击查看 AI 巡检详情','花朵数量严重超标','','[\"/jhds/images/alerts/flower-overload-c2.png\",\"/jhds/images/alerts/flower-overload-c1.png\"]',1,3,'2026-08-28 07:53:09',NULL);
/*!40000 ALTER TABLE `page_alert_content` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `patrol_record`
--

DROP TABLE IF EXISTS `patrol_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `patrol_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `task_id` bigint DEFAULT NULL COMMENT '????ID',
  `image_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '??????',
  `track_position` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '????',
  `ai_result` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `ai_status` tinyint DEFAULT '0' COMMENT 'AI?? 0=??? 1=??? 2=?? 3=??',
  `shoot_time` datetime DEFAULT NULL COMMENT '????',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=COMPACT COMMENT='椤甸潰锛欰I杞ㄩ亾宸℃?锛涘尯鍩燂細瑙嗛?涓嬫柟宸℃?璁板綍涓嶢I璇嗗埆缁撴灉锛涙暟鎹?細杞ㄩ亾浣嶇疆銆佹姄鎷嶅浘鐗囥?鎷嶆憚鏃堕棿銆丄I鐘舵?鍜孉I缁撴灉銆';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `patrol_record`
--

LOCK TABLES `patrol_record` WRITE;
/*!40000 ALTER TABLE `patrol_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `patrol_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `patrol_task`
--

DROP TABLE IF EXISTS `patrol_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `patrol_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `task_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '????',
  `execute_time` time NOT NULL COMMENT '????',
  `patrol_range` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'all' COMMENT '????',
  `status` tinyint DEFAULT '0' COMMENT '?? 0=??? 1=??? 2=??? 3=???',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `updated_at` datetime DEFAULT NULL COMMENT '????',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='椤甸潰锛欰I杞ㄩ亾宸℃?锛涘尯鍩燂細宸℃?浠诲姟鍒楄〃涓庢柊寤轰换鍔¤〃鍗曪紱鏁版嵁锛氫换鍔″悕绉般?鎵ц?鏃堕棿銆佸贰妫?寖鍥村拰鎵ц?鐘舵?銆';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `patrol_task`
--

LOCK TABLES `patrol_task` WRITE;
/*!40000 ALTER TABLE `patrol_task` DISABLE KEYS */;
INSERT INTO `patrol_task` VALUES (2,'晨间全面巡检','08:00:00','all',0,'2026-08-28 08:47:52',NULL),(3,'午间生长监测','12:00:00','all',0,'2026-08-28 08:47:52',NULL),(4,'晚间状态巡查','18:00:00','all',0,'2026-08-28 08:47:52',NULL);
/*!40000 ALTER TABLE `patrol_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `plant_cultivation`
--

DROP TABLE IF EXISTS `plant_cultivation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `plant_cultivation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `plant_id` bigint NOT NULL COMMENT '??ID',
  `year` int NOT NULL COMMENT '??',
  `month` tinyint NOT NULL COMMENT '?? 1-12',
  `water_frequency` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????',
  `fertilize` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '???????',
  `pruning` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '??',
  `trellis` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '??/??',
  `weeding` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????',
  `repot` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '??/??',
  `other` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '??',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `updated_at` datetime DEFAULT NULL COMMENT '????',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='椤甸潰锛氬巻骞存。妗堬紱鍖哄煙锛氭牻鍩圭?鐞嗭紱鏁版嵁锛氭湀搴︾亴婧夈?鏂借偉銆佷慨鍓??鎼?灦銆侀櫎鑽夈?鎹㈢泦鍜屽?娉ㄣ?';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `plant_cultivation`
--

LOCK TABLES `plant_cultivation` WRITE;
/*!40000 ALTER TABLE `plant_cultivation` DISABLE KEYS */;
INSERT INTO `plant_cultivation` VALUES (47,5,2026,1,'3',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-28 06:02:48','2026-08-28 14:02:54');
/*!40000 ALTER TABLE `plant_cultivation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `plant_growth_record`
--

DROP TABLE IF EXISTS `plant_growth_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `plant_growth_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `plant_id` bigint NOT NULL COMMENT '??ID',
  `year` int NOT NULL COMMENT '??',
  `record_date` date DEFAULT NULL COMMENT '????',
  `height_cm` decimal(6,1) DEFAULT NULL COMMENT '??(cm)',
  `crown_width_cm` decimal(6,1) DEFAULT NULL COMMENT '??(cm)',
  `leaf_count` int DEFAULT NULL COMMENT '????',
  `flower_count` int DEFAULT NULL COMMENT '???',
  `fruit_count` int DEFAULT NULL COMMENT '???',
  `photo_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????',
  `photo_no` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `updated_at` datetime DEFAULT NULL COMMENT '????',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_plant_year` (`plant_id`,`year`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='椤甸潰锛氬巻骞存。妗堬紱鍖哄煙锛氱敓闀胯?娴嬶紱鏁版嵁锛氳?娴嬫棩鏈熴?鏍?珮銆佸啝骞呫?鍙惰姳鏋滄暟閲忋?鐓х墖鍜屽?娉ㄣ?';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `plant_growth_record`
--

LOCK TABLES `plant_growth_record` WRITE;
/*!40000 ALTER TABLE `plant_growth_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `plant_growth_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `plant_info`
--

DROP TABLE IF EXISTS `plant_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `plant_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `plant_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '???',
  `scientific_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '??',
  `family_genus` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '??',
  `variety` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '??',
  `source_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????: ??/??',
  `source_channel` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????',
  `plant_date` date DEFAULT NULL COMMENT '????',
  `plant_location` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????: ??/??/??',
  `soil_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????',
  `substrate_ratio` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????',
  `light_env` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????',
  `planting_spec` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????(???/???)',
  `main_photo` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '??',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `updated_at` datetime DEFAULT NULL COMMENT '????',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_plant_name` (`plant_name`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='椤甸潰锛氬巻骞存。妗堬紱鍖哄煙锛氭?鏍?。妗堜晶鏍忓拰鍩虹?妗ｆ?锛涙暟鎹?細妞嶇墿鍩烘湰璧勬枡銆佺?妞嶆潯浠躲?涓诲浘鍜屽?娉ㄣ?';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `plant_info`
--

LOCK TABLES `plant_info` WRITE;
/*!40000 ALTER TABLE `plant_info` DISABLE KEYS */;
INSERT INTO `plant_info` VALUES (5,'1',NULL,NULL,NULL,NULL,NULL,'2026-08-28',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-28 06:02:41','2026-08-28 14:02:41');
/*!40000 ALTER TABLE `plant_info` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `plant_pest_disease`
--

DROP TABLE IF EXISTS `plant_pest_disease`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `plant_pest_disease` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `plant_id` bigint NOT NULL COMMENT '??ID',
  `year` int NOT NULL COMMENT '??',
  `record_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '??: ??/??/????',
  `pest_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '??????????',
  `occur_date` date DEFAULT NULL COMMENT '????',
  `symptom` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????',
  `severity` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????: ?/?/?',
  `measure_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????: ??/??/??/??',
  `measure` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????',
  `effect` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '?????',
  `photo_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `updated_at` datetime DEFAULT NULL COMMENT '????',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_plant_year` (`plant_id`,`year`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='椤甸潰锛氬巻骞存。妗堬紱鍖哄煙锛氱梾铏??閫嗗?锛涙暟鎹?細鍙戠敓鍚嶇О/鏃堕棿銆佺棁鐘躲?涓ラ噸绋嬪害銆佸?鐞嗘帾鏂姐?鏁堟灉鍜屽浘鐗囥?';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `plant_pest_disease`
--

LOCK TABLES `plant_pest_disease` WRITE;
/*!40000 ALTER TABLE `plant_pest_disease` DISABLE KEYS */;
/*!40000 ALTER TABLE `plant_pest_disease` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `plant_phenology`
--

DROP TABLE IF EXISTS `plant_phenology`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `plant_phenology` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `plant_id` bigint NOT NULL COMMENT '??ID',
  `year` int NOT NULL COMMENT '??',
  `stage` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '???: ???/???/???/???/???/?????',
  `phase` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????: ??/??/??/??/????/?????',
  `event_date` date DEFAULT NULL COMMENT '????',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????(????/????/?????)',
  `photo_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `updated_at` datetime DEFAULT NULL COMMENT '????',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_plant_year` (`plant_id`,`year`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=70 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='椤甸潰锛氬巻骞存。妗堬紱鍖哄煙锛氱墿鍊欏勾鍘嗭紱鏁版嵁锛氱敓鑲查樁娈点?鐗╁?鏈熴?鍙戠敓鏃ユ湡銆佽?瀵熸弿杩板拰鍥剧墖鍦板潃銆';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `plant_phenology`
--

LOCK TABLES `plant_phenology` WRITE;
/*!40000 ALTER TABLE `plant_phenology` DISABLE KEYS */;
/*!40000 ALTER TABLE `plant_phenology` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `plant_year_record`
--

DROP TABLE IF EXISTS `plant_year_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `plant_year_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `plant_id` bigint NOT NULL COMMENT '??ID',
  `year` int NOT NULL COMMENT '??',
  `growth_grade` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????: ?/?/?/?',
  `annual_summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '????????',
  `problem_review` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '??????',
  `improvement_suggestion` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '?????????',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `updated_at` datetime DEFAULT NULL COMMENT '????',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_plant_year` (`plant_id`,`year`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='椤甸潰锛氬巻骞存。妗堬紱鍖哄煙锛氬勾搴﹂?椤瑰崱鍜屽勾缁堟?缁擄紱鏁版嵁锛氬勾搴︾敓闀胯瘎绾с?骞村害鎬荤粨銆侀棶棰樺?鐩樺拰鏀硅繘寤鸿?銆';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `plant_year_record`
--

LOCK TABLES `plant_year_record` WRITE;
/*!40000 ALTER TABLE `plant_year_record` DISABLE KEYS */;
INSERT INTO `plant_year_record` VALUES (11,5,2026,NULL,NULL,NULL,NULL,'2026-08-28 06:02:41','2026-08-28 14:02:41');
/*!40000 ALTER TABLE `plant_year_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `soil_sensor_data`
--

DROP TABLE IF EXISTS `soil_sensor_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `soil_sensor_data` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `soil_temp` decimal(5,1) DEFAULT NULL COMMENT '???? ?C',
  `soil_humidity` decimal(5,1) DEFAULT NULL COMMENT '???? %',
  `soil_ec` decimal(5,2) DEFAULT NULL COMMENT '??EC mS/cm',
  `soil_ph` decimal(4,1) DEFAULT NULL COMMENT '??pH',
  `record_time` datetime NOT NULL COMMENT '????',
  `soil_salt` decimal(10,2) DEFAULT NULL COMMENT '????',
  `soil_nitrogen` decimal(10,2) DEFAULT NULL COMMENT '??? mg/kg',
  `soil_phosphorus` decimal(10,2) DEFAULT NULL COMMENT '??? mg/kg',
  `soil_potassium` decimal(10,2) DEFAULT NULL COMMENT '??? mg/kg',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_record_time` (`record_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=467 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='椤甸潰锛氳惀鍏绘恫閰嶆恫锛涘尯鍩燂細鍦熷￥浼犳劅鍣ㄤ笌48灏忔椂瓒嬪娍鍥撅紱鏁版嵁锛氬湡澹ゆ俯婀垮害銆丒C銆乸H銆佺洂鍒嗐?姘?７閽惧拰閲囬泦鏃堕棿銆傞〉闈?粠鏈?〃璇诲彇鍏ㄩ儴鎸囨爣涓庡巻鍙茶秼鍔裤?';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `soil_sensor_data`
--

LOCK TABLES `soil_sensor_data` WRITE;
/*!40000 ALTER TABLE `soil_sensor_data` DISABLE KEYS */;
INSERT INTO `soil_sensor_data` VALUES (466,23.6,47.0,0.40,6.5,'2026-08-28 15:30:48',0.08,101.00,13.00,167.00);
/*!40000 ALTER TABLE `soil_sensor_data` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `system_setting`
--

DROP TABLE IF EXISTS `system_setting`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_setting` (
  `setting_key` varchar(100) NOT NULL COMMENT '设置键',
  `setting_value` text COMMENT '设置值',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应用持久化设置；页面：营养液配液；数据：手动、自动或AI模式。';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `system_setting`
--

LOCK TABLES `system_setting` WRITE;
/*!40000 ALTER TABLE `system_setting` DISABLE KEYS */;
INSERT INTO `system_setting` VALUES ('dashboard.alarm-seeded','done','2026-08-28 17:00:41'),('dashboard.seeded','true','2026-08-28 15:30:47'),('nutrient.mode','manual','2026-08-28 15:30:47'),('seed.ai.knowledge.v1','done','2026-08-28 16:47:52'),('seed.insect.v1','done','2026-08-28 15:30:48'),('seed.nutrient.v1','done','2026-08-28 15:30:48');
/*!40000 ALTER TABLE `system_setting` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `weather_sensor_data`
--

DROP TABLE IF EXISTS `weather_sensor_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `weather_sensor_data` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `temperature` decimal(5,1) DEFAULT NULL COMMENT '???? ?C',
  `humidity` decimal(5,1) DEFAULT NULL COMMENT '???? %',
  `wind_speed` decimal(5,1) DEFAULT NULL COMMENT '?? m/s',
  `rainfall` decimal(5,1) DEFAULT NULL COMMENT '??? mm',
  `wind_direction` decimal(5,1) DEFAULT NULL COMMENT '???? 0~360?',
  `record_time` datetime NOT NULL COMMENT '????',
  `light_intensity` decimal(8,1) DEFAULT NULL COMMENT '???? Lux',
  `uv_intensity` decimal(5,1) DEFAULT NULL COMMENT '????? uW/cm?',
  `uv_index` decimal(3,1) DEFAULT NULL COMMENT '?????',
  `battery_status` tinyint DEFAULT NULL COMMENT '???? 0?? 1???',
  `hourly_rainfall` decimal(5,1) DEFAULT NULL COMMENT '????? mm',
  `daily_rainfall` decimal(5,1) DEFAULT NULL COMMENT '???? mm',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_record_time` (`record_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=695 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='椤甸潰锛氭皵璞＄珯銆佹暟鎹?ぇ灞忥紱鍖哄煙锛氭皵璞℃寚鏍囧崱銆?8灏忔椂瓒嬪娍鍥惧拰澶у睆姘旇薄缂╃暐鍗★紱鏁版嵁锛氭俯婀垮害銆侀?閫熼?鍚戙?闆ㄩ噺銆佸厜鐓с?绱??绾裤?鐢甸噺鍜岄噰闆嗘椂闂淬?褰撳墠鐩存帴璇诲彇鏈?柊/鍘嗗彶璁板綍锛岀┖琛ㄦ椂椤甸潰淇濈暀鍒濆?鍊笺?';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `weather_sensor_data`
--

LOCK TABLES `weather_sensor_data` WRITE;
/*!40000 ALTER TABLE `weather_sensor_data` DISABLE KEYS */;
/*!40000 ALTER TABLE `weather_sensor_data` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `weather_station_protocol`
--

DROP TABLE IF EXISTS `weather_station_protocol`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `weather_station_protocol` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `sensor_key` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '?????? TEMPERATURE/HUMIDITY/...',
  `display_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '???? ??/??/...',
  `command_hex` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Modbus?? hex???',
  `unit` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '?? ?/%/m/s/...',
  `sort_order` int DEFAULT '0' COMMENT '????',
  `enabled` tinyint DEFAULT '1' COMMENT '0=?? 1=??',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL COMMENT '????',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_sensor_key` (`sensor_key`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='鍚庡彴姘旇薄閲囬泦閰嶇疆锛涚敤閫旓細WeatherService鎸夊惎鐢ㄩ」璇诲彇Modbus鎸囦护銆佸崟浣嶅拰鎺掑簭鍚庨噰闆嗗苟鍐欏叆weather_sensor_data锛涙暟鎹?細浼犳劅鍣ㄦ爣璇嗐?鏄剧ず鍚嶇О銆佹寚浠ゃ?鍗曚綅銆佹帓搴忓拰鍚?敤鐘舵?銆傚綋鍓嶆棤椤甸潰鐩存帴缂栬緫鎴栧睍绀烘湰琛ㄣ?';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `weather_station_protocol`
--

LOCK TABLES `weather_station_protocol` WRITE;
/*!40000 ALTER TABLE `weather_station_protocol` DISABLE KEYS */;
/*!40000 ALTER TABLE `weather_station_protocol` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `weather_threshold_config`
--

DROP TABLE IF EXISTS `weather_threshold_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `weather_threshold_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `temp_min` decimal(5,1) DEFAULT NULL COMMENT '温度下限 °C',
  `temp_max` decimal(5,1) DEFAULT NULL COMMENT '温度上限 °C',
  `humidity_min` decimal(5,1) DEFAULT NULL COMMENT '湿度下限 %',
  `humidity_max` decimal(5,1) DEFAULT NULL COMMENT '湿度上限 %',
  `wind_speed_max` decimal(5,1) DEFAULT NULL COMMENT '风速上限 m/s',
  `total_rainfall_max` decimal(5,1) DEFAULT NULL COMMENT '累计雨量上限 mm',
  `hourly_rainfall_max` decimal(5,1) DEFAULT NULL COMMENT '小时雨量上限 mm',
  `daily_rainfall_max` decimal(5,1) DEFAULT NULL COMMENT '日雨量上限 mm',
  `light_min` decimal(8,1) DEFAULT NULL COMMENT '光照下限 Lux',
  `light_max` decimal(8,1) DEFAULT NULL COMMENT '光照上限 Lux',
  `uv_intensity_max` decimal(5,1) DEFAULT NULL COMMENT '紫外线强度上限 uW/cm²',
  `uv_index_max` decimal(3,1) DEFAULT NULL COMMENT '紫外线指数上限',
  `battery_alarm` tinyint DEFAULT '1' COMMENT '低电量报警 0关1开',
  `enabled` tinyint DEFAULT '1' COMMENT '总开关',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='椤甸潰锛氭皵璞＄珯锛涘尯鍩燂細鎶ヨ?闃堝?璁剧疆闈㈡澘锛涙暟鎹?細娓╂箍搴︺?椋庨?銆侀洦閲忋?鍏夌収銆佺传澶栫嚎鍜屼綆鐢甸噺鎶ヨ?闃堝?鍙婂紑鍏炽?椤甸潰鐩存帴璇诲彇/淇濆瓨锛屽悗鍙版嵁姝ょ敓鎴恆larm_record銆';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `weather_threshold_config`
--

LOCK TABLES `weather_threshold_config` WRITE;
/*!40000 ALTER TABLE `weather_threshold_config` DISABLE KEYS */;
INSERT INTO `weather_threshold_config` VALUES (1,5.0,40.0,30.0,90.0,10.0,100.0,20.0,50.0,5000.0,100000.0,200.0,8.0,1,1,'2026-08-28 16:50:26');
/*!40000 ALTER TABLE `weather_threshold_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'jhds'
--

--
-- Dumping routines for database 'jhds'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-28 17:32:48

-- 修复旧版 MySQL 导入后的表注释（UTF-8）
USE `jhds`;
ALTER TABLE `ai_knowledge_entry` COMMENT = '页面：AI农业助手；区域：关键词问答；数据：关键词、回答和启用状态。';
ALTER TABLE `ai_learn_card` COMMENT = '页面：AI学习；区域：知识卡片；数据：图片、标题、讲解和展示状态。';
ALTER TABLE `ai_learn_video` COMMENT = '页面：AI学习；区域：上传视频匹配；数据：视频文件名、资料目录和分组。';
ALTER TABLE `alarm_record` COMMENT = '页面：报警中心；区域：报警列表、状态与处置说明、来源分布；数据：标题、描述、级别、来源、位置、处置状态、说明与时间。页面通过API读取和修改本表；气象阈值等后台服务也会写入本表。';
ALTER TABLE `control_log` COMMENT = '后台运维审计日志；用途：MQTT设备控制、气象和土壤Modbus采集的指令/响应记录；数据：设备、控制值、指令/响应、成功状态、手自动来源和时间。当前无前端页面直接展示本表。';
ALTER TABLE `dashboard_farm_operation` COMMENT = '页面：数据大屏；区域：农事操作；数据：已完成农事名称、日期、图标和展示顺序。';
ALTER TABLE `dashboard_greenhouse` COMMENT = '页面：数据大屏；区域：大棚信息；数据：名称、类型、作物、面积、株数和定植日期。';
ALTER TABLE `dashboard_market_feedback` COMMENT = '页面：数据大屏；区域：市场反馈弹窗；数据：反馈标题、摘要、详情和是否展示。';
ALTER TABLE `dashboard_todo` COMMENT = '页面：数据大屏；区域：待办农事；数据：时间、农事类别、待办操作和展示顺序。';
ALTER TABLE `equipment` COMMENT = '页面：物联设备、营养液配液；区域：大棚控制面板和手动模式泵控制卡片；数据：设备名称/别名、控制指令和开关状态。物联设备页仅显示别名以GH开头的设备，配液页显示PUMP设备。';
ALTER TABLE `insect_record` COMMENT = '页面：虫情灯；区域：本地记录图库、今日统计和AI识别巡检种类统计；数据：虫体图片、种类、数量、设备、AI引擎和采集时间。告警弹窗与外部照片标签页不读取本表。';
ALTER TABLE `irrigation_record` COMMENT = '页面：营养液配液；区域：灌溉统计卡与AI决策日志；数据：手动/自动/AI模式、泵别名、运行时长和执行时间。页面聚合显示本日次数、运行时长、估算用水量和最近记录。';
ALTER TABLE `irrigation_schedule` COMMENT = '页面：营养液配液；区域：自动模式的自动灌溉计划表单；数据：计划时间、运行时长、频率和启用状态。页面保存、回显和后台调度均读取本表。';
ALTER TABLE `page_alert_content` COMMENT = '页面：数据大屏、虫情灯、AI轨道巡检；区域：页面专用告警卡片和详情弹窗；数据：标题、说明、静态图片URL和展示开关。';
ALTER TABLE `patrol_record` COMMENT = '页面：AI轨道巡检；区域：视频下方巡检记录与AI识别结果；数据：轨道位置、抓拍图片、拍摄时间、AI状态和AI结果。';
ALTER TABLE `patrol_task` COMMENT = '页面：AI轨道巡检；区域：巡检任务列表与新建任务表单；数据：任务名称、执行时间、巡检范围和执行状态。';
ALTER TABLE `plant_cultivation` COMMENT = '页面：历年档案；区域：栽培管理；数据：月度灌溉、施肥、修剪、搭架、除草、换盆和备注。';
ALTER TABLE `plant_growth_record` COMMENT = '页面：历年档案；区域：生长观测；数据：观测日期、株高、冠幅、叶花果数量、照片和备注。';
ALTER TABLE `plant_info` COMMENT = '页面：历年档案；区域：植株档案侧栏和基础档案；数据：植物基本资料、种植条件、主图和备注。';
ALTER TABLE `plant_pest_disease` COMMENT = '页面：历年档案；区域：病虫害逆境；数据：发生名称/时间、症状、严重程度、处理措施、效果和图片。';
ALTER TABLE `plant_phenology` COMMENT = '页面：历年档案；区域：物候年历；数据：生育阶段、物候期、发生日期、观察描述和图片地址。';
ALTER TABLE `plant_year_record` COMMENT = '页面：历年档案；区域：年度选项卡和年终总结；数据：年度生长评级、年度总结、问题复盘和改进建议。';
ALTER TABLE `soil_sensor_data` COMMENT = '页面：营养液配液；区域：土壤传感器与48小时趋势图；数据：土壤温湿度、EC、pH、盐分、氮磷钾和采集时间。页面从本表读取全部指标与历史趋势。';
ALTER TABLE `system_setting` COMMENT = '应用持久化设置；页面：营养液配液；数据：手动、自动或AI模式。';
ALTER TABLE `weather_sensor_data` COMMENT = '页面：气象站、数据大屏；区域：气象指标卡、48小时趋势图和大屏气象缩略卡；数据：温湿度、风速风向、雨量、光照、紫外线、电量和采集时间。当前直接读取最新/历史记录，空表时页面保留初始值。';
ALTER TABLE `weather_station_protocol` COMMENT = '后台气象采集配置；用途：WeatherService按启用项读取Modbus指令、单位和排序后采集并写入weather_sensor_data；数据：传感器标识、显示名称、指令、单位、排序和启用状态。当前无页面直接编辑或展示本表。';
ALTER TABLE `weather_threshold_config` COMMENT = '页面：气象站；区域：报警阈值设置面板；数据：温湿度、风速、雨量、光照、紫外线和低电量报警阈值及开关。页面直接读取/保存，后台据此生成alarm_record。';