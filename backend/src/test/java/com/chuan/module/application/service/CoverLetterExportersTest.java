package com.chuan.module.application.service;

import com.chuan.common.pdf.PdfHtmlRenderer;
import com.chuan.common.progress.GenerationProgressStore;
import com.chuan.module.application.vo.CoverLetterVO;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 求职信导出冒烟测试：PDF 与 Word 均可生成。
 */
class CoverLetterExportersTest {

    @Test
    void pdfIsValid() {
        ApplicationService service = new ApplicationService(
                null, null, null, null, null, new PdfHtmlRenderer(), new GenerationProgressStore(), null);
        byte[] bytes = service.exportCoverLetterPdf(letter());
        assertTrue(bytes.length > 500);
        assertEquals("%PDF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void wordIsValid() {
        ApplicationService service = new ApplicationService(
                null, null, null, null, null, null, new GenerationProgressStore(), null);
        byte[] bytes = service.exportCoverLetterWord(letter());
        assertTrue(bytes.length > 500);
        assertEquals('P', bytes[0]);
        assertEquals('K', bytes[1]);
    }

    private CoverLetterVO letter() {
        CoverLetterVO letter = new CoverLetterVO();
        letter.setSubject("应聘 Java 后端开发工程师");
        letter.setGreeting("尊敬的招聘负责人：");
        letter.setBody("您好！\n我对贵公司的岗位非常感兴趣，期待进一步沟通。");
        letter.setApplicantName("张三");
        return letter;
    }
}
