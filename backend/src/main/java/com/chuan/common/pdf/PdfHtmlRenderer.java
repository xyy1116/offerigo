package com.chuan.common.pdf;

import com.chuan.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * 通用 HTML → PDF 渲染器（openhtmltopdf + 中文字体 simhei）。
 * 供求职信、定制简历等需要将 HTML 模板导出为 PDF 的模块复用，避免重复加载字体与构建逻辑。
 */
@Slf4j
@Component
public class PdfHtmlRenderer {

    /** 将 HTML 文档渲染为 PDF 字节流。 */
    public byte[] render(String html) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            File font = loadFontFile();
            if (font != null) {
                builder.useFont(font, "SimHei");
            }
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            log.error("生成 PDF 失败", e);
            throw new BusinessException("PDF 导出失败，请稍后重试");
        }
    }

    private File loadFontFile() {
        try {
            ClassPathResource resource = new ClassPathResource("fonts/simhei.ttf");
            InputStream in = resource.getInputStream();
            File tmp = File.createTempFile("simhei", ".ttf");
            tmp.deleteOnExit();
            Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return tmp;
        } catch (Exception e) {
            log.warn("加载中文字体失败，PDF 将使用默认字体", e);
            return null;
        }
    }
}