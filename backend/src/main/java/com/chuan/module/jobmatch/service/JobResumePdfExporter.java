package com.chuan.module.jobmatch.service;

import com.chuan.common.exception.BusinessException;
import com.chuan.common.pdf.PdfHtmlRenderer;
import com.chuan.module.jobmatch.vo.JobResumeVO;
import com.chuan.module.jobmatch.vo.ResumeSkillVO;
import com.chuan.module.jobmatch.vo.ResumeTemplateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 岗位定制简历 PDF 导出器：按所选模板的版式（单栏 / 侧边栏 / 宽页眉 / 衬线）
 * 与配色生成 A4 PDF，内嵌证件照。
 */
@Component
@RequiredArgsConstructor
public class JobResumePdfExporter {

    private static final Pattern HEX = Pattern.compile("^#?[0-9a-fA-F]{6}$");

    private final PdfHtmlRenderer pdfHtmlRenderer;

    public byte[] export(JobResumeVO resume, ResumeTemplateVO template) {
        if (resume == null) {
            throw new BusinessException("简历内容为空，无法导出");
        }
        return pdfHtmlRenderer.render(buildHtml(resume, template));
    }

    private String buildHtml(JobResumeVO r, ResumeTemplateVO t) {
        String primary = color(t == null ? null : t.getPrimary(), "#1f2933");
        String secondary = color(t == null ? null : t.getSecondary(), "#6b7280");
        String background = color(t == null ? null : t.getBackground(), "#ffffff");
        String text = color(t == null ? null : t.getText(), "#1f2933");
        String layout = t == null || !StringUtils.hasText(t.getLayout()) ? "single" : t.getLayout();
        String contrast = isLight(primary) ? "#111827" : "#ffffff";
        String photo = safePhoto(r.getPhoto()) ? r.getPhoto().trim() : null;

        StringBuilder sb = new StringBuilder(4096);
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'/><style>")
          .append("@page{size:A4;margin:16mm 15mm;}")
          .append("*{box-sizing:border-box;}")
          .append("body{margin:0;font-family:'SimHei','Microsoft YaHei',sans-serif;color:").append(text)
          .append(";background:").append(background).append(";font-size:10.5pt;line-height:1.55;}")
          .append(".name{font-size:20pt;font-weight:bold;color:").append(primary).append(";margin:0;}")
          .append(".basic{font-size:9.5pt;color:").append(secondary).append(";margin-top:5pt;}")
          .append(".photo{width:26mm;height:35mm;border:1px solid #d9d9d9;}")
          .append(".head{width:100%;border-collapse:collapse;margin-bottom:3pt;}")
          .append(".head td{vertical-align:middle;padding:0;}")
          .append(".head-photo{width:30mm;text-align:right;}")
          .append("h3{font-size:11.5pt;color:").append(primary)
          .append(";margin:13pt 0 5pt;padding-bottom:2pt;border-bottom:1px solid #d9d9d9;page-break-after:avoid;}")
          .append(".section{page-break-inside:avoid;}")
          .append("p{margin:0 0 4pt;}")
          .append("ul{margin:0;padding-left:15pt;}")
          .append("li{margin:0 0 2pt;}")
          .append(".banner{background:").append(primary).append(";color:").append(contrast)
          .append(";padding:12pt 14pt;margin:0 0 8pt;}")
          .append(".banner .name,.banner .basic{color:").append(contrast).append(";}")
          .append(".banner .photo{border-color:").append(contrast).append(";}")
          .append(".sidebar{width:100%;border-collapse:collapse;}")
          .append(".sidebar td{vertical-align:top;}")
          .append(".side{width:33%;background:").append(primary).append(";color:").append(contrast)
          .append(";padding:12pt 10pt;}")
          .append(".side .name,.side .basic,.side h3{color:").append(contrast).append(";}")
          .append(".side h3{border-bottom-color:").append(contrast).append(";}")
          .append(".side .photo{border-color:").append(contrast).append(";}")
          .append(".main{padding-left:12pt;}")
          .append("body.serif .name{letter-spacing:2px;}")
          .append("body.serif h3{border-bottom:2px solid ").append(primary).append(";}")
          .append("</style></head><body class='").append(esc(layout)).append("'>");

        if ("sidebar".equals(layout) || "double".equals(layout)) {
            appendSidebar(sb, r, photo);
        } else if ("banner".equals(layout)) {
            appendBanner(sb, r, photo);
            appendSections(sb, r);
        } else {
            appendHead(sb, r, photo);
            appendSections(sb, r);
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private void appendHead(StringBuilder sb, JobResumeVO r, String photo) {
        sb.append("<table class='head'><tr><td>");
        appendIdentity(sb, r);
        sb.append("</td>");
        if (photo != null) {
            sb.append("<td class='head-photo'><img class='photo' src='").append(esc(photo)).append("'/></td>");
        }
        sb.append("</tr></table>");
    }

    private void appendBanner(StringBuilder sb, JobResumeVO r, String photo) {
        sb.append("<div class='banner'><table class='head'><tr><td>");
        appendIdentity(sb, r);
        sb.append("</td>");
        if (photo != null) {
            sb.append("<td class='head-photo'><img class='photo' src='").append(esc(photo)).append("'/></td>");
        }
        sb.append("</tr></table></div>");
    }

    private void appendSidebar(StringBuilder sb, JobResumeVO r, String photo) {
        sb.append("<table class='sidebar'><tr><td class='side'>");
        if (photo != null) {
            sb.append("<p><img class='photo' src='").append(esc(photo)).append("'/></p>");
        }
        appendIdentity(sb, r);
        appendSkills(sb, r);
        appendListSection(sb, "证书奖项", r.getCertificates());
        sb.append("</td><td class='main'>");
        appendTextSection(sb, "自我评价", r.getSelfEvaluation());
        appendListSection(sb, "实习/项目经历", r.getExperiences());
        appendListSection(sb, "项目亮点", r.getProjectHighlights());
        appendListSection(sb, "教育背景", r.getEducation());
        sb.append("</td></tr></table>");
    }

    private void appendIdentity(StringBuilder sb, JobResumeVO r) {
        sb.append("<p class='name'>").append(esc(textOr(r.getName(), "求职者"))).append("</p>");
        if (StringUtils.hasText(r.getBasicInfo())) {
            sb.append("<div class='basic'>").append(nl2br(r.getBasicInfo())).append("</div>");
        }
    }

    /** 单栏/宽页眉：自我评价、技能、经历、亮点、教育、证书。 */
    private void appendSections(StringBuilder sb, JobResumeVO r) {
        appendTextSection(sb, "自我评价", r.getSelfEvaluation());
        appendSkills(sb, r);
        appendListSection(sb, "实习/项目经历", r.getExperiences());
        appendListSection(sb, "项目亮点", r.getProjectHighlights());
        appendListSection(sb, "教育背景", r.getEducation());
        appendListSection(sb, "证书奖项", r.getCertificates());
    }

    private void appendTextSection(StringBuilder sb, String title, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        sb.append("<div class='section'><h3>").append(esc(title)).append("</h3><p>")
          .append(nl2br(value)).append("</p></div>");
    }

    private void appendSkills(StringBuilder sb, JobResumeVO r) {
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
            line.append(esc(s.getName().trim()));
        }
        if (line.length() == 0) {
            return;
        }
        sb.append("<div class='section'><h3>核心技能</h3><p>").append(line).append("</p></div>");
    }

    private void appendListSection(StringBuilder sb, String title, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        StringBuilder list = new StringBuilder();
        for (String line : lines) {
            if (StringUtils.hasText(line)) {
                list.append("<li>").append(nl2br(line.trim())).append("</li>");
            }
        }
        if (list.length() == 0) {
            return;
        }
        sb.append("<div class='section'><h3>").append(esc(title)).append("</h3><ul>")
          .append(list).append("</ul></div>");
    }

    private String color(String value, String fallback) {
        if (StringUtils.hasText(value) && HEX.matcher(value.trim()).matches()) {
            String hex = value.trim();
            return hex.startsWith("#") ? hex : "#" + hex;
        }
        return fallback;
    }

    private boolean isLight(String hex) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        return (r * 299 + g * 587 + b * 114) / 1000 > 170;
    }

    private boolean safePhoto(String photo) {
        if (!StringUtils.hasText(photo)) {
            return false;
        }
        String value = photo.trim();
        int comma = value.indexOf(',');
        // 只接受内联 base64 图片，避免渲染器按 URL 去拉取外部或本机资源
        if (comma <= 0 || !value.substring(0, comma).endsWith(";base64")) {
            return false;
        }
        String head = value.substring(0, comma);
        if (!head.startsWith("data:image/jpeg") && !head.startsWith("data:image/jpg")
                && !head.startsWith("data:image/png")) {
            return false;
        }
        String payload = value.substring(comma + 1);
        return payload.length() <= 3_000_000 && payload.matches("[A-Za-z0-9+/=\\s]+");
    }

    private String textOr(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String nl2br(String s) {
        return esc(s).replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br/>");
    }
}
