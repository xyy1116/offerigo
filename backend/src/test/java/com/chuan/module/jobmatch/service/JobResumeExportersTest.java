package com.chuan.module.jobmatch.service;

import com.chuan.common.pdf.PdfHtmlRenderer;
import com.chuan.module.jobmatch.vo.JobResumeVO;
import com.chuan.module.jobmatch.vo.ResumeSkillVO;
import com.chuan.module.jobmatch.vo.ResumeTemplateVO;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简历导出冒烟测试：四种版式都能产出合法的 PDF / DOCX。
 */
class JobResumeExportersTest {

    /** 1x1 透明 PNG，仅用于验证证件照内嵌不报错。 */
    private static final String PHOTO =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

    @Test
    void pdfRendersEveryLayout() {
        JobResumePdfExporter exporter = new JobResumePdfExporter(new PdfHtmlRenderer());
        for (String layout : List.of("single", "sidebar", "banner", "serif")) {
            byte[] bytes = exporter.export(resume(), template(layout));
            assertTrue(bytes.length > 500, layout + " PDF 过小");
            assertEquals("%PDF", new String(bytes, 0, 4, StandardCharsets.US_ASCII), layout);
        }
    }

    @Test
    void wordRendersEveryLayout() {
        JobResumeWordExporter exporter = new JobResumeWordExporter();
        for (String layout : List.of("single", "sidebar", "banner", "serif")) {
            byte[] bytes = exporter.export(resume(), template(layout));
            assertTrue(bytes.length > 500, layout + " DOCX 过小");
            assertEquals('P', bytes[0], layout);
            assertEquals('K', bytes[1], layout);
        }
    }

    private JobResumeVO resume() {
        JobResumeVO vo = new JobResumeVO();
        vo.setJobName("Java 后端开发工程师");
        vo.setName("张三");
        vo.setPhoto(PHOTO);
        vo.setBasicInfo("13800000000 | zhangsan@example.com | 北京");
        vo.setSelfEvaluation("三年后端开发经验，熟悉高并发与分布式系统。");
        ResumeSkillVO skill = new ResumeSkillVO();
        skill.setName("Java");
        skill.setMatched(true);
        ResumeSkillVO skill2 = new ResumeSkillVO();
        skill2.setName("Spring Boot");
        vo.setSkills(List.of(skill, skill2));
        vo.setExperiences(List.of("负责订单系统重构，QPS 提升 3 倍。"));
        vo.setProjectHighlights(List.of("主导支付网关迁移，故障率下降 60%。"));
        vo.setEducation(List.of("某某大学 · 计算机科学与技术 · 本科"));
        vo.setCertificates(List.of("软件设计师"));
        return vo;
    }

    private ResumeTemplateVO template(String layout) {
        ResumeTemplateVO t = new ResumeTemplateVO();
        t.setId("test-" + layout);
        t.setName("测试模板");
        t.setLayout(layout);
        t.setPrimary("#143252");
        t.setSecondary("#2c5f8a");
        t.setBackground("#ffffff");
        t.setText("#2b2b2b");
        return t;
    }
}
