package com.chuan.module.jobmatch.service;

import com.chuan.module.jobmatch.vo.JobResumeVO;
import com.chuan.module.jobmatch.vo.ResumeSkillVO;
import com.chuan.module.jobmatch.vo.ResumeTemplateVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 岗位定制简历 Word(.docx) 导出器：按所选模板的版式与配色生成可编辑文档，
 * 内嵌证件照。支持单栏 / 侧边栏 / 宽页眉 / 衬线四种版式。
 */
@Slf4j
@Component
public class JobResumeWordExporter {

    private static final Pattern HEX = Pattern.compile("^#?[0-9a-fA-F]{6}$");
    private static final String FONT = "SimHei";

    public byte[] export(JobResumeVO r, ResumeTemplateVO t) {
        String primary = hex(t == null ? null : t.getPrimary(), "1A5FB4");
        String secondary = hex(t == null ? null : t.getSecondary(), "666666");
        String body = hex(t == null ? null : t.getText(), "2B2B2B");
        String layout = t == null || !StringUtils.hasText(t.getLayout()) ? "single" : t.getLayout();
        String contrast = isLight(primary) ? "111827" : "FFFFFF";

        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            if ("sidebar".equals(layout) || "double".equals(layout)) {
                buildSidebar(doc, r, primary, contrast);
            } else if ("banner".equals(layout)) {
                buildBanner(doc, r, primary, contrast);
                addMainSections(doc, null, r, primary, body);
            } else {
                buildSingleHeader(doc, r, primary, secondary);
                addMainSections(doc, null, r, primary, body);
            }
            doc.write(os);
            return os.toByteArray();
        } catch (Exception e) {
            log.error("生成岗位定制简历 Word 失败", e);
            throw new com.chuan.common.exception.BusinessException("Word 导出失败，请稍后重试");
        }
    }

    // ==================== 版式 ====================

    /** 极简单栏 / 轻商务衬线：姓名与证件照左右分栏，正文在下方。 */
    private void buildSingleHeader(XWPFDocument doc, JobResumeVO r, String primary, String secondary) {
        boolean hasPhoto = safePhoto(r.getPhoto());
        XWPFTable table = doc.createTable(1, hasPhoto ? 2 : 1);
        table.setWidth("100%");
        table.removeBorders();
        XWPFTableRow row = table.getRow(0);
        XWPFTableCell left = clearCell(row.getCell(0));
        left.setWidth(hasPhoto ? "82%" : "100%");
        addIdentity(doc, left, r, 20, primary, secondary);

        if (hasPhoto) {
            XWPFTableCell right = clearCell(row.getCell(1));
            right.setWidth("18%");
            addPhoto(right.addParagraph(), r.getPhoto(), true);
        }
        doc.createParagraph();
    }

    /** 宽页眉：整条主色带，姓名/信息白字，照片在右。 */
    private void buildBanner(XWPFDocument doc, JobResumeVO r, String primary, String contrast) {
        boolean hasPhoto = safePhoto(r.getPhoto());
        XWPFTable table = doc.createTable(1, hasPhoto ? 2 : 1);
        table.setWidth("100%");
        table.removeBorders();
        table.setCellMargins(120, 140, 120, 140);
        XWPFTableRow row = table.getRow(0);
        XWPFTableCell left = clearCell(row.getCell(0));
        left.setColor(primary);
        left.setWidth(hasPhoto ? "80%" : "100%");
        addIdentity(doc, left, r, 20, contrast, contrast);

        if (hasPhoto) {
            XWPFTableCell right = clearCell(row.getCell(1));
            right.setColor(primary);
            right.setWidth("20%");
            addPhoto(right.addParagraph(), r.getPhoto(), true);
        }
        doc.createParagraph();
    }

    /** 侧边栏双栏：左侧主色信息栏，右侧主体经历。 */
    private void buildSidebar(XWPFDocument doc, JobResumeVO r, String primary, String contrast) {
        XWPFTable table = doc.createTable(1, 2);
        table.setWidth("100%");
        table.removeBorders();
        XWPFTableRow row = table.getRow(0);
        XWPFTableCell left = clearCell(row.getCell(0));
        left.setColor(primary);
        left.setWidth("33%");
        if (safePhoto(r.getPhoto())) {
            addPhoto(left.addParagraph(), r.getPhoto(), true);
        }
        addIdentity(doc, left, r, 18, contrast, contrast);
        addSkills(doc, left, r, 12, 10, contrast, contrast);
        addList(doc, left, "证书奖项", r.getCertificates(), 12, 10, contrast, contrast);

        XWPFTableCell right = clearCell(row.getCell(1));
        right.setWidth("67%");
        addSelf(doc, right, r, 12, 10, primary, "2B2B2B");
        addList(doc, right, "实习/项目经历", r.getExperiences(), 12, 10, primary, "2B2B2B");
        addList(doc, right, "项目亮点", r.getProjectHighlights(), 12, 10, primary, "2B2B2B");
        addList(doc, right, "教育背景", r.getEducation(), 12, 10, primary, "2B2B2B");
    }

    // ==================== 内容 ====================

    private void addMainSections(XWPFDocument doc, XWPFTableCell cell, JobResumeVO r,
                                 String heading, String body) {
        addSelf(doc, cell, r, 12, 10, heading, body);
        addSkills(doc, cell, r, 12, 10, heading, body);
        addList(doc, cell, "实习/项目经历", r.getExperiences(), 12, 10, heading, body);
        addList(doc, cell, "项目亮点", r.getProjectHighlights(), 12, 10, heading, body);
        addList(doc, cell, "教育背景", r.getEducation(), 12, 10, heading, body);
        addList(doc, cell, "证书奖项", r.getCertificates(), 12, 10, heading, body);
    }

    private void addIdentity(XWPFDocument doc, XWPFTableCell cell, JobResumeVO r,
                             int nameSize, String nameColor, String basicColor) {
        XWPFParagraph p = paragraph(doc, cell);
        XWPFRun name = run(p, textOr(r.getName(), "求职者"), nameSize, true, nameColor);
        name.setCharacterSpacing(20);
        if (StringUtils.hasText(r.getBasicInfo())) {
            XWPFParagraph bp = paragraph(doc, cell);
            bp.setSpacingAfter(80);
            addMultiline(bp, r.getBasicInfo(), 10, basicColor);
        }
    }

    private void addSelf(XWPFDocument doc, XWPFTableCell cell, JobResumeVO r,
                         int headingSize, int bodySize, String headingColor, String bodyColor) {
        if (!StringUtils.hasText(r.getSelfEvaluation())) {
            return;
        }
        addHeading(doc, cell, "自我评价", headingSize, headingColor);
        XWPFParagraph p = paragraph(doc, cell);
        p.setSpacingAfter(80);
        p.setSpacingBetween(1.3);
        addMultiline(p, r.getSelfEvaluation(), bodySize, bodyColor);
    }

    private void addSkills(XWPFDocument doc, XWPFTableCell cell, JobResumeVO r,
                           int headingSize, int bodySize, String headingColor, String bodyColor) {
        List<ResumeSkillVO> skills = r.getSkills();
        if (skills == null || skills.isEmpty()) {
            return;
        }
        StringBuilder line = new StringBuilder();
        for (ResumeSkillVO s : skills) {
            if (s == null || !StringUtils.hasText(s.getName())) {
                continue;
            }
            if (line.length() > 0) {
                line.append(" · ");
            }
            line.append(s.getName().trim());
        }
        if (line.length() == 0) {
            return;
        }
        addHeading(doc, cell, "核心技能", headingSize, headingColor);
        XWPFParagraph p = paragraph(doc, cell);
        p.setSpacingAfter(80);
        run(p, line.toString(), bodySize, false, bodyColor);
    }

    private void addList(XWPFDocument doc, XWPFTableCell cell, String title, List<String> lines,
                         int headingSize, int bodySize, String headingColor, String bodyColor) {
        if (lines == null || lines.isEmpty() || lines.stream().noneMatch(StringUtils::hasText)) {
            return;
        }
        addHeading(doc, cell, title, headingSize, headingColor);
        for (String line : lines) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            XWPFParagraph p = paragraph(doc, cell);
            p.setIndentationLeft(200);
            p.setSpacingAfter(40);
            p.setSpacingBetween(1.25);
            run(p, "• " + line.trim(), bodySize, false, bodyColor);
        }
    }

    // ==================== 低层工具 ====================

    private XWPFParagraph paragraph(XWPFDocument doc, XWPFTableCell cell) {
        return cell == null ? doc.createParagraph() : cell.addParagraph();
    }

    private void addHeading(XWPFDocument doc, XWPFTableCell cell, String title, int size, String color) {
        XWPFParagraph p = paragraph(doc, cell);
        p.setSpacingBefore(200);
        p.setSpacingAfter(80);
        p.setBorderBottom(Borders.SINGLE);
        run(p, title, size, true, color);
    }

    private XWPFRun run(XWPFParagraph p, String text, int size, boolean bold, String color) {
        XWPFRun r = p.createRun();
        r.setText(text == null ? "" : text);
        r.setFontSize(size);
        r.setBold(bold);
        r.setColor(color);
        r.setFontFamily(FONT);
        r.setFontFamily(FONT, XWPFRun.FontCharRange.eastAsia);
        r.setFontFamily(FONT, XWPFRun.FontCharRange.hAnsi);
        return r;
    }

    private void addMultiline(XWPFParagraph p, String text, int size, String color) {
        String[] lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            XWPFRun r = p.createRun();
            if (i > 0) {
                r.addBreak();
            }
            r.setText(lines[i]);
            r.setFontSize(size);
            r.setColor(color);
            r.setFontFamily(FONT);
            r.setFontFamily(FONT, XWPFRun.FontCharRange.eastAsia);
            r.setFontFamily(FONT, XWPFRun.FontCharRange.hAnsi);
        }
    }

    private void addPhoto(XWPFParagraph p, String dataUri, boolean center) {
        byte[] photo = decodePhoto(dataUri);
        if (photo == null) {
            return;
        }
        try {
            p.setAlignment(center ? ParagraphAlignment.CENTER : ParagraphAlignment.RIGHT);
            XWPFRun r = p.createRun();
            int type = dataUri.startsWith("data:image/png")
                    ? XWPFDocument.PICTURE_TYPE_PNG : XWPFDocument.PICTURE_TYPE_JPEG;
            r.addPicture(new ByteArrayInputStream(photo), type, "photo.jpg",
                    Units.toEMU(86), Units.toEMU(115));
        } catch (Exception e) {
            log.warn("简历证件照写入失败，已跳过：{}", e.getMessage());
        }
    }

    private XWPFTableCell clearCell(XWPFTableCell cell) {
        for (int i = cell.getParagraphs().size() - 1; i >= 0; i--) {
            cell.removeParagraph(i);
        }
        return cell;
    }

    private String hex(String value, String fallback) {
        if (!StringUtils.hasText(value) || !HEX.matcher(value.trim()).matches()) {
            return fallback;
        }
        return value.trim().replace("#", "").toUpperCase();
    }

    private boolean isLight(String hex) {
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return (r * 299 + g * 587 + b * 114) / 1000 > 170;
    }

    private boolean safePhoto(String photo) {
        return StringUtils.hasText(photo)
                && (photo.startsWith("data:image/jpeg") || photo.startsWith("data:image/jpg")
                || photo.startsWith("data:image/png"))
                && photo.indexOf(',') > 0;
    }

    private byte[] decodePhoto(String dataUri) {
        try {
            return Base64.getDecoder().decode(dataUri.substring(dataUri.indexOf(',') + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private String textOr(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
