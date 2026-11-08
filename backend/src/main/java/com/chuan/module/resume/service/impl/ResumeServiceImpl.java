package com.chuan.module.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuan.common.exception.BusinessException;
import com.chuan.common.llm.DeepSeekClient;
import com.chuan.module.resume.dto.CareerAnalysisVO;
import com.chuan.module.resume.dto.PolishRequest;
import com.chuan.module.resume.dto.PolishVO;
import com.chuan.module.resume.dto.ResumeContent;
import com.chuan.module.resume.dto.ResumeContent.BasicInfo;
import com.chuan.module.resume.dto.ResumeContent.CampusActivity;
import com.chuan.module.resume.dto.ResumeContent.CareerGoal;
import com.chuan.module.resume.dto.ResumeContent.Certificate;
import com.chuan.module.resume.dto.ResumeContent.Education;
import com.chuan.module.resume.dto.ResumeContent.Internship;
import com.chuan.module.resume.entity.Resume;
import com.chuan.module.resume.mapper.ResumeMapper;
import com.chuan.module.resume.service.ResumeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 在线简历服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeServiceImpl extends ServiceImpl<ResumeMapper, Resume> implements ResumeService {

    /** 证件照 data URI：仅允许 JPEG/PNG，避免 SVG 等可执行内容。 */
    private static final Pattern PHOTO_URI =
            Pattern.compile("^data:image/(?:jpeg|jpg|png);base64,[A-Za-z0-9+/=]+$");
    /** 压缩后的一寸照约数十 KB，2MB 足够且能挡住异常超大请求。 */
    private static final int MAX_PHOTO_CHARS = 2_000_000;

    private final ObjectMapper objectMapper;
    private final DeepSeekClient deepSeekClient;

    @Override
    public ResumeContent get(Long userId) {
        Resume resume = getOne(new LambdaQueryWrapper<Resume>().eq(Resume::getUserId, userId), false);
        if (resume == null || !StringUtils.hasText(resume.getContentJson())) {
            return new ResumeContent();
        }
        try {
            return objectMapper.readValue(resume.getContentJson(), ResumeContent.class);
        } catch (Exception e) {
            log.error("简历数据解析失败, userId={}", userId, e);
            throw new BusinessException("简历数据解析失败");
        }
    }

    @Override
    public void save(Long userId, ResumeContent content) {
        try {
            String json = objectMapper.writeValueAsString(content);
            Resume existing = getOne(new LambdaQueryWrapper<Resume>().eq(Resume::getUserId, userId), false);
            LocalDateTime now = LocalDateTime.now();
            if (existing == null) {
                Resume resume = new Resume();
                resume.setUserId(userId);
                resume.setContentJson(json);
                resume.setCreateTime(now);
                resume.setUpdateTime(now);
                save(resume);
            } else {
                existing.setContentJson(json);
                existing.setUpdateTime(now);
                updateById(existing);
            }
        } catch (Exception e) {
            log.error("保存简历失败, userId={}", userId, e);
            throw new BusinessException("保存失败");
        }
    }

    @Override
    public String getPhoto(Long userId) {
        return get(userId).getPhoto();
    }

    @Override
    public void savePhoto(Long userId, String photo) {
        ResumeContent content = get(userId);
        if (!StringUtils.hasText(photo)) {
            content.setPhoto(null);
        } else {
            String trimmed = photo.trim();
            if (trimmed.length() > MAX_PHOTO_CHARS || !PHOTO_URI.matcher(trimmed).matches()) {
                throw new BusinessException("证件照格式不支持或文件过大");
            }
            content.setPhoto(trimmed);
        }
        // 只更新照片字段，保留简历其余内容
        save(userId, content);
    }

    @Override
    public PolishVO polish(PolishRequest request) {
        if (!StringUtils.hasText(request.getText())) {
            throw new BusinessException("请先填写需要润色的内容");
        }
        PolishVO vo = new PolishVO();
        vo.setOriginal(request.getText());
        vo.setPolished(deepSeekClient.polishText(request.getText(), request.getSection()));
        return vo;
    }

    @Override
    public CareerAnalysisVO analyzeCareer(ResumeContent content) {
        CareerGoal goal = content.getCareerGoal() == null ? new CareerGoal() : content.getCareerGoal();
        if (!StringUtils.hasText(goal.getTargetPosition())) {
            throw new BusinessException("请先填写目标岗位");
        }
        return deepSeekClient.analyzeCareer(toTextSummary(content), goal.getTargetPosition());
    }

    @Override
    public byte[] exportPdf(ResumeContent content) {
        return htmlToPdf(buildHtml(content));
    }

    @Override
    public byte[] exportWord(ResumeContent content) {
        return buildWord(content);
    }

    // ==================== PDF ====================

    private String buildHtml(ResumeContent c) {
        BasicInfo b = c.getBasic();
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'/><style>")
          .append("body{font-family:'SimHei','Microsoft YaHei',sans-serif;color:#2b2b2b;margin:0;padding:40px;}")
          .append(".header{overflow:hidden;border-bottom:3px solid #1a5fb4;padding-bottom:16px;margin-bottom:8px;}")
          .append(".photo{float:right;width:90px;height:120px;object-fit:cover;border:1px solid #d9d9d9;margin-left:16px;}")
          .append("h1{font-size:26px;margin:0 0 8px;color:#111;}")
          .append(".contact{font-size:13px;color:#555;margin-bottom:4px;}")
          .append(".meta{font-size:13px;color:#555;margin-bottom:4px;line-height:1.7;}")
          .append("h2{font-size:16px;color:#1a5fb4;border-bottom:1px solid #c9d6e8;padding-bottom:4px;margin:20px 0 10px;}")
          .append(".item{margin-bottom:12px;}")
          .append(".item-head{display:flex;justify-content:space-between;font-weight:bold;font-size:14px;}")
          .append(".item-sub{font-size:13px;color:#666;margin:3px 0;}")
          .append(".desc{font-size:13px;line-height:1.7;color:#333;white-space:pre-wrap;}")
          .append("</style></head><body>");

        sb.append("<div class='header'>");
        if (safePhoto(c.getPhoto())) {
            sb.append("<img class='photo' src=\"").append(c.getPhoto().trim()).append("\"/>");
        }
        sb.append("<h1>").append(e(b.getName())).append("</h1>");
        sb.append("<div class='contact'>");
        sb.append(join(" &#160;|&#160; ", b.getPhone(), b.getEmail()));
        sb.append("</div>");
        // 基本信息明细行
        List<String> metas = new ArrayList<>();
        if (hasText(b.getEthnicity())) {
            metas.add(e(b.getEthnicity()));
        }
        if (hasText(b.getBirthDate())) {
            metas.add(e(b.getBirthDate()));
        }
        if (b.getHeight() != null) {
            metas.add(b.getHeight() + " cm");
        }
        if (hasText(b.getPoliticalStatus())) {
            metas.add(e(b.getPoliticalStatus()));
        }
        String region = joinRegion(b.getRegion(), b.getAddress());
        if (hasText(region)) {
            metas.add(e(region));
        }
        if (!metas.isEmpty()) {
            sb.append("<div class='meta'>").append(String.join(" &#160;|&#160; ", metas)).append("</div>");
        }
        CareerGoal goal = c.getCareerGoal() == null ? new CareerGoal() : c.getCareerGoal();
        List<String> goals = new ArrayList<>();
        if (hasText(goal.getTargetPosition())) {
            goals.add("目标岗位：" + e(goal.getTargetPosition()));
        }
        if (hasText(goal.getExpectedSalary())) {
            goals.add("期望薪资：" + e(goal.getExpectedSalary()));
        }
        if (hasText(goal.getWorkCity())) {
            goals.add("工作城市：" + e(goal.getWorkCity()));
        }
        if (!goals.isEmpty()) {
            sb.append("<div class='meta'>求职意向：").append(String.join(" &#160;|&#160; ", goals)).append("</div>");
        }
        sb.append("</div>");

        if (!c.getEducations().isEmpty()) {
            sb.append("<h2>教育背景</h2>");
            for (Education ed : c.getEducations()) {
                sb.append("<div class='item'><div class='item-head'><span>").append(e(ed.getSchool()))
                  .append("</span><span>").append(e(ed.getDegree())).append("</span></div>");
                if (hasText(ed.getGraduateDate())) {
                    sb.append("<div class='item-sub'>毕业时间：").append(e(ed.getGraduateDate())).append("</div>");
                }
                sb.append("</div>");
            }
        }

        if (!c.getInternships().isEmpty()) {
            sb.append("<h2>实习经历</h2>");
            for (Internship it : c.getInternships()) {
                sb.append("<div class='item'><div class='item-head'><span>").append(e(it.getCompany()))
                  .append("</span><span>").append(e(range(it.getStartDate(), it.getEndDate()))).append("</span></div>");
                if (hasText(it.getPosition())) {
                    sb.append("<div class='item-sub'>").append(e(it.getPosition())).append("</div>");
                }
                if (hasText(it.getDescription())) {
                    sb.append("<div class='desc'>").append(nl2br(e(it.getDescription()))).append("</div>");
                }
                sb.append("</div>");
            }
        }

        if (!c.getCampusActivities().isEmpty()) {
            sb.append("<h2>校园经历</h2>");
            for (CampusActivity ca : c.getCampusActivities()) {
                sb.append("<div class='item'><div class='item-head'><span>").append(e(ca.getOrganization()))
                  .append("</span><span>").append(e(range(ca.getStartDate(), ca.getEndDate()))).append("</span></div>");
                if (hasText(ca.getPosition())) {
                    sb.append("<div class='item-sub'>").append(e(ca.getPosition())).append("</div>");
                }
                if (hasText(ca.getDuty())) {
                    sb.append("<div class='desc'>").append(nl2br(e(ca.getDuty()))).append("</div>");
                }
                sb.append("</div>");
            }
        }

        if (!c.getCertificates().isEmpty()) {
            sb.append("<h2>专业证书</h2>");
            for (Certificate cert : c.getCertificates()) {
                sb.append("<div class='item'><div class='item-head'><span>").append(e(cert.getName()))
                  .append("</span><span>").append(e(cert.getObtainDate())).append("</span></div>");
                if (hasText(cert.getIssuer())) {
                    sb.append("<div class='item-sub'>发证机构：").append(e(cert.getIssuer())).append("</div>");
                }
                sb.append("</div>");
            }
        }

        if (hasText(c.getSelfEvaluation())) {
            sb.append("<h2>自我评价</h2><div class='desc'>").append(nl2br(e(c.getSelfEvaluation()))).append("</div>");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private byte[] htmlToPdf(String html) {
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

    // ==================== Word ====================

    private byte[] buildWord(ResumeContent c) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            BasicInfo b = c.getBasic();

            if (safePhoto(c.getPhoto())) {
                byte[] photo = decodePhoto(c.getPhoto());
                if (photo != null) {
                    XWPFParagraph pp = doc.createParagraph();
                    XWPFRun pr = pp.createRun();
                    int type = c.getPhoto().startsWith("data:image/png")
                            ? XWPFDocument.PICTURE_TYPE_PNG : XWPFDocument.PICTURE_TYPE_JPEG;
                    pr.addPicture(new ByteArrayInputStream(photo), type, "photo.jpg",
                            Units.toEMU(90), Units.toEMU(120));
                }
            }

            addTitle(doc, b.getName());
            addLine(doc, join(" | ", b.getPhone(), b.getEmail()));

            List<String> metas = new ArrayList<>();
            if (hasText(b.getEthnicity())) {
                metas.add(b.getEthnicity());
            }
            if (hasText(b.getBirthDate())) {
                metas.add(b.getBirthDate());
            }
            if (b.getHeight() != null) {
                metas.add(b.getHeight() + " cm");
            }
            if (hasText(b.getPoliticalStatus())) {
                metas.add(b.getPoliticalStatus());
            }
            String region = joinRegion(b.getRegion(), b.getAddress());
            if (hasText(region)) {
                metas.add(region);
            }
            if (!metas.isEmpty()) {
                addLine(doc, String.join(" | ", metas));
            }
            CareerGoal goal = c.getCareerGoal() == null ? new CareerGoal() : c.getCareerGoal();
            List<String> goals = new ArrayList<>();
            if (hasText(goal.getTargetPosition())) {
                goals.add("目标岗位：" + goal.getTargetPosition());
            }
            if (hasText(goal.getExpectedSalary())) {
                goals.add("期望薪资：" + goal.getExpectedSalary());
            }
            if (hasText(goal.getWorkCity())) {
                goals.add("工作城市：" + goal.getWorkCity());
            }
            if (!goals.isEmpty()) {
                addLine(doc, "求职意向：" + String.join(" | ", goals));
            }

            if (!c.getEducations().isEmpty()) {
                addHeading(doc, "教育背景");
                for (Education ed : c.getEducations()) {
                    addBoldLine(doc, join("    ", ed.getSchool(), ed.getDegree()));
                    if (hasText(ed.getGraduateDate())) {
                        addLine(doc, "毕业时间：" + ed.getGraduateDate());
                    }
                }
            }

            if (!c.getInternships().isEmpty()) {
                addHeading(doc, "实习经历");
                for (Internship it : c.getInternships()) {
                    addBoldLine(doc, join("    ", it.getCompany(), range(it.getStartDate(), it.getEndDate())));
                    if (hasText(it.getPosition())) {
                        addLine(doc, it.getPosition());
                    }
                    if (hasText(it.getDescription())) {
                        addPara(doc, it.getDescription());
                    }
                }
            }

            if (!c.getCampusActivities().isEmpty()) {
                addHeading(doc, "校园经历");
                for (CampusActivity ca : c.getCampusActivities()) {
                    addBoldLine(doc, join("    ", ca.getOrganization(), range(ca.getStartDate(), ca.getEndDate())));
                    if (hasText(ca.getPosition())) {
                        addLine(doc, ca.getPosition());
                    }
                    if (hasText(ca.getDuty())) {
                        addPara(doc, ca.getDuty());
                    }
                }
            }

            if (!c.getCertificates().isEmpty()) {
                addHeading(doc, "专业证书");
                for (Certificate cert : c.getCertificates()) {
                    addBoldLine(doc, join("    ", cert.getName(), cert.getObtainDate()));
                    if (hasText(cert.getIssuer())) {
                        addLine(doc, "发证机构：" + cert.getIssuer());
                    }
                }
            }

            if (hasText(c.getSelfEvaluation())) {
                addHeading(doc, "自我评价");
                addPara(doc, c.getSelfEvaluation());
            }

            doc.write(os);
            return os.toByteArray();
        } catch (Exception e) {
            log.error("生成 Word 失败", e);
            throw new BusinessException("Word 导出失败，请稍后重试");
        }
    }

    private void addTitle(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText(text == null ? "" : text);
        r.setBold(true);
        r.setFontSize(22);
    }

    private void addHeading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(240);
        p.setSpacingAfter(120);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setFontSize(16);
        r.setColor("1A5FB4");
    }

    private void addLine(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setFontSize(11);
        r.setText(text == null ? "" : text);
    }

    private void addBoldLine(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(80);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(12);
        r.setText(text == null ? "" : text);
    }

    private void addPara(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setFontSize(11);
        r.setText(text == null ? "" : text);
    }

    // ==================== 工具方法 ====================

    private String toTextSummary(ResumeContent c) {
        BasicInfo b = c.getBasic();
        StringBuilder sb = new StringBuilder();
        if (hasText(b.getName())) {
            sb.append("姓名：").append(b.getName()).append("\n");
        }
        if (hasText(b.getEthnicity())) {
            sb.append("民族：").append(b.getEthnicity()).append("\n");
        }
        if (hasText(b.getBirthDate())) {
            sb.append("出生年月：").append(b.getBirthDate()).append("\n");
        }
        if (b.getHeight() != null) {
            sb.append("身高：").append(b.getHeight()).append("cm\n");
        }
        if (hasText(b.getPoliticalStatus())) {
            sb.append("政治面貌：").append(b.getPoliticalStatus()).append("\n");
        }
        String region = joinRegion(b.getRegion(), b.getAddress());
        if (hasText(region)) {
            sb.append("住址：").append(region).append("\n");
        }

        if (!c.getEducations().isEmpty()) {
            sb.append("教育背景：\n");
            for (Education ed : c.getEducations()) {
                sb.append("- ").append(join(" ", ed.getSchool(), ed.getDegree()));
                if (hasText(ed.getGraduateDate())) {
                    sb.append("，毕业时间 ").append(ed.getGraduateDate());
                }
                sb.append("\n");
            }
        }
        if (!c.getInternships().isEmpty()) {
            sb.append("实习经历：\n");
            for (Internship it : c.getInternships()) {
                sb.append("- ").append(join(" ", it.getCompany(), it.getPosition()));
                if (hasText(it.getDescription())) {
                    sb.append("：").append(it.getDescription());
                }
                sb.append("\n");
            }
        }
        if (!c.getCampusActivities().isEmpty()) {
            sb.append("校园经历：\n");
            for (CampusActivity ca : c.getCampusActivities()) {
                sb.append("- ").append(join(" ", ca.getOrganization(), ca.getPosition()));
                if (hasText(ca.getDuty())) {
                    sb.append("：").append(ca.getDuty());
                }
                sb.append("\n");
            }
        }
        if (!c.getCertificates().isEmpty()) {
            sb.append("专业证书：\n");
            for (Certificate cert : c.getCertificates()) {
                sb.append("- ").append(join(" ", cert.getName(), cert.getIssuer())).append("\n");
            }
        }
        if (hasText(c.getSelfEvaluation())) {
            sb.append("自我评价：").append(c.getSelfEvaluation()).append("\n");
        }
        return sb.toString();
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private boolean safePhoto(String photo) {
        return hasText(photo) && photo.startsWith("data:image/") && photo.indexOf(",") > 0;
    }

    private byte[] decodePhoto(String dataUri) {
        try {
            return Base64.getDecoder().decode(dataUri.substring(dataUri.indexOf(",") + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private String joinRegion(List<String> region, String address) {
        List<String> parts = new ArrayList<>();
        if (region != null) {
            for (String r : region) {
                if (hasText(r)) {
                    parts.add(r.trim());
                }
            }
        }
        if (hasText(address)) {
            parts.add(address.trim());
        }
        return parts.isEmpty() ? "" : String.join(" ", parts);
    }

    private String e(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String nl2br(String s) {
        return s == null ? "" : s.replace("\n", "<br/>");
    }

    private String range(String start, String end) {
        if (hasText(start) && hasText(end)) {
            return start + " ~ " + end;
        }
        if (hasText(start)) {
            return start + " ~ 至今";
        }
        return hasText(end) ? end : "";
    }

    private String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String part : parts) {
            if (!hasText(part)) {
                continue;
            }
            if (!first) {
                sb.append(sep);
            }
            sb.append(part.trim());
            first = false;
        }
        return sb.toString();
    }
}