package com.chuan.module.jobmatch.service;

import com.chuan.module.jobmatch.vo.ResumeTemplateVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置简历模板目录：只保留 4 套视觉差异明确的版式，接口直接返回，打开弹窗即可显示。
 *
 * <p>此前实时抓取外部模板会带来数秒等待，且抓取结果与内置模板版式重复；
 * 这里改为稳定的内置目录，不再依赖外部网络。</p>
 */
@Component
public class ResumeTemplateCrawler {

    private static final List<ResumeTemplateVO> TEMPLATES = buildTemplates();

    public List<ResumeTemplateVO> listTemplates() {
        return TEMPLATES;
    }

    public ResumeTemplateVO getTemplate(String id) {
        for (ResumeTemplateVO template : TEMPLATES) {
            if (template.getId() != null && template.getId().equals(id)) {
                return template;
            }
        }
        return TEMPLATES.get(0);
    }

    private static List<ResumeTemplateVO> buildTemplates() {
        List<ResumeTemplateVO> list = new ArrayList<>();
        // ① 极简单栏：黑白灰低饱和、信息密度高，最适配大厂 ATS
        list.add(builtin("builtin-1", "极简单栏", "single",
                "#1f2933", "#9aa6b2", "#ffffff", "#24292f",
                "Helvetica Neue, Arial, 'PingFang SC', 'Microsoft YaHei', sans-serif"));
        // ② 侧边栏双栏：左侧信息栏 + 右侧主体区，藏青色块分区
        list.add(builtin("builtin-2", "侧边栏双栏", "sidebar",
                "#143252", "#2c5f8a", "#ffffff", "#2b2b2b",
                "Helvetica Neue, Arial, 'PingFang SC', 'Microsoft YaHei', sans-serif"));
        // ③ 宽页眉：顶部通栏深蓝色彩带，姓名与求职意向醒目
        list.add(builtin("builtin-3", "宽页眉", "banner",
                "#1e3a5f", "#3a5b80", "#ffffff", "#2b2b2b",
                "Helvetica Neue, Arial, 'PingFang SC', 'Microsoft YaHei', sans-serif"));
        // ④ 轻商务：细线分隔 + 墨绿米白，稳重克制
        list.add(builtin("builtin-4", "轻商务", "serif",
                "#2f4f3e", "#6b8a78", "#fbfaf7", "#2b2b29",
                "Georgia, 'Times New Roman', 'Songti SC', 'SimSun', serif"));
        return list;
    }

    private static ResumeTemplateVO builtin(String id, String name, String layout,
                                            String primary, String secondary,
                                            String background, String text, String font) {
        ResumeTemplateVO vo = new ResumeTemplateVO();
        vo.setId(id);
        vo.setName(name);
        vo.setCrawled(false);
        vo.setPrimary(primary);
        vo.setSecondary(secondary);
        vo.setBackground(background);
        vo.setText(text);
        vo.setHeadingFont(font);
        vo.setBodyFont(font);
        vo.setLayout(layout);
        return vo;
    }
}