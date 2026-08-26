package com.jhds.controller;

import com.jhds.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 学习模块 - 视频分析结果接口
 * 仅在扫描完成后由前端拉取，返回 mock 知识点卡片数据。
 */
@RestController
@RequestMapping("/api/ai-learn")
public class AiLearnController {

    @GetMapping("/analyze")
    public Result<List<Map<String, Object>>> analyze() {
        List<Map<String, Object>> groups = new ArrayList<>();

        // ============ 第 1 组：樱桃苗木培育与定植 ============
        groups.add(buildGroup("樱桃苗木培育与定植", Arrays.asList(
                buildItem("1-1.png", "1-1 苗木筛选与质量把控",
                        "对照标准测量苗高（≥0.8m），检查主干粗细均匀度、侧根数量与健壮程度，剔除偏细、弯曲、根系稀疏或发黑的苗木。"),
                buildItem("1-2.png", "1-2 接穗处理与嫁接操作",
                        "选取健壮嫩梢削取带少量木质部的盾形芽片；砧木斜切形成嵌合切口，将芽片嵌入并对齐形成层，用嫁接膜密封固定；另有劈接方式：砧木劈开2–3cm，接穗削成楔形插入并对准形成层后固定。"),
                buildItem("1-3.png", "1-3 根系修剪与无土栽培准备",
                        "剪除细弱须根和交叉缠绕根，保留健壮主根与侧根，将剪口修成45°斜面；依次用多菌灵浸泡30分钟杀菌、生根粉溶液促根；同时配制均匀无分层的混合基质。"),
                buildItem("1-4.png", "1-4 定植、定干与促枝处理",
                        "种植袋底部铺珍珠岩，苗木根系自然舒展后填充基质并压实，确保根颈露出基质±1cm；在主干70cm处定干，选芽刻伤并涂抹发枝素促枝；插入竹竿固定苗木，浇透定根水（分两次浇灌）。")
        )));

        // ============ 第 2 组：缺素诊断与仪器操作 ============
        groups.add(buildGroup("缺素诊断与仪器操作", Arrays.asList(
                buildItem("2-1.png", "2-1 叶色诊断法",
                        "学习要点：通过叶片颜色变化初步判断缺素类型\n案例应用：叶片褪绿、叶脉间呈现褪绿条纹 → 初步判断为缺镁"),
                buildItem("2-2.png", "2-2 症状记录与数据更新",
                        "学习要点：发现异常症状时及时拍照记录，并上传至AI数据库，用于后续识别模型训练"),
                buildItem("2-3.png", "2-3 快捷光合作用速率仪",
                        "学习要点：掌握仪器的使用方法，测定植株的光合速率\n数据意义：光合速率是判断植物生理状态的重要指标\n\n土壤测定仪：掌握土壤速效氮、磷、钾及中微量元素（Ca、Mg、Fe等）的测定方法；Mg含量低于0.49%即为缺镁；准确记录各项测定数值，作为诊断依据\n\n环境参数异常判断：掌握樱桃适宜生长环境参数范围；CO₂浓度180ppm过低 → 通风处理\n\n缺素矫正方案制定：根据诊断结果制定针对性施肥方案；缺镁 → 配制0.3%镁肥进行叶面喷施")
        )));

        // ============ 第 3 组：病虫害预警与防治 ============
        groups.add(buildGroup("病虫害预警与防治", Arrays.asList(
                buildItem("3-1.png", "3-1 环境预警与无人机巡检",
                        "基于温室内温度、湿度等环境参数，利用系统模型预测病虫害风险（如红色预警为高风险）；操作无人机搭载高光谱摄像头规划巡检航线，采集图像并生成报告，提取病虫害种类、位置、严重程度等信息。"),
                buildItem("3-2.png", "3-2 人工采样与实验室诊断",
                        "当AI无法精准识别时，启动人工采样：在病叶健康交界处刮取病灶（病原物最集中），通过载玻片制片（滴无菌水、加盖玻片排除气泡）后，使用光学显微镜观察病原形态（如卵形孢子判定为灰霉病），完成确诊。"),
                buildItem("3-3.png", "3-3 药剂配制与精准施药",
                        "根据诊断结果选择对症药剂（如灰霉病用50%湿霉利可湿性粉剂1500倍液），利用植保无人机进行精准变量施药；同时掌握蜂卡悬挂技术（位置、高度、密度），辅助生物防治。"),
                buildItem("3-4.png", "3-4 标本制作与资源库建设",
                        "采用针插法制作害虫标本，使用标准扎网框固定；采集病叶制作病害标本（处理、保存）。标本用于培训、科普、科研及AI模型训练，丰富教学与识别资源。")
        )));

        return Result.ok(groups);
    }

    private Map<String, Object> buildGroup(String title, List<Map<String, Object>> items) {
        Map<String, Object> g = new HashMap<>();
        g.put("group", title);
        g.put("items", items);
        return g;
    }

    private Map<String, Object> buildItem(String img, String title, String desc) {
        Map<String, Object> m = new HashMap<>();
        m.put("image", "/images/" + img);
        m.put("title", title);
        m.put("desc", desc);
        return m;
    }
}
