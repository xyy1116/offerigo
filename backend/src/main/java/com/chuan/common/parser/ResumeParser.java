package com.chuan.common.parser;

import com.chuan.common.exception.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 简历文档解析器：支持 .pdf / .docx / .doc，提取纯文本内容。
 */
public final class ResumeParser {

    private ResumeParser() {
    }

    public static String extractText(byte[] bytes, String filename) {
        if (filename == null) {
            throw new BusinessException("文件名不能为空");
        }
        String lower = filename.toLowerCase();
        try {
            if (lower.endsWith(".pdf")) {
                return extractPdf(bytes);
            }
            if (lower.endsWith(".docx")) {
                return extractDocx(bytes);
            }
            if (lower.endsWith(".doc")) {
                return extractDoc(bytes);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("简历文档解析失败，请确认文件未损坏");
        }
        throw new BusinessException("仅支持 .pdf / .doc / .docx 格式的简历文件");
    }

    private static String extractPdf(byte[] bytes) throws Exception {
        try (PDDocument document = PDDocument.load(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private static String extractDocx(byte[] bytes) throws Exception {
        try (InputStream in = new ByteArrayInputStream(bytes);
             XWPFDocument document = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private static String extractDoc(byte[] bytes) throws Exception {
        try (InputStream in = new ByteArrayInputStream(bytes);
             HWPFDocument document = new HWPFDocument(in);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }
}