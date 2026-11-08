package com.chuan.module.jobmatch.service;

import com.chuan.module.jobmatch.dto.BasicInfoRequest;
import com.chuan.module.jobmatch.dto.KnowledgeItem;
import com.chuan.module.jobmatch.vo.AnalyzeTaskVO;
import com.chuan.module.jobmatch.vo.GenerateMaterialVO;
import com.chuan.module.jobmatch.vo.JobImportVO;
import com.chuan.module.jobmatch.vo.JobLinkCrawlVO;
import com.chuan.module.jobmatch.vo.JobOcrVO;
import com.chuan.module.jobmatch.vo.JobRankVO;
import com.chuan.module.jobmatch.vo.JobResumeVO;
import com.chuan.module.jobmatch.vo.KnowledgeEntryVO;
import com.chuan.module.jobmatch.vo.MatchVerifyVO;
import com.chuan.module.jobmatch.vo.KnowledgeSplitVO;
import com.chuan.module.jobmatch.vo.ResumeImportVO;
import com.chuan.module.jobmatch.vo.ResumeTemplateVO;
import com.chuan.module.jobmatch.vo.TargetJobVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 智能岗位匹配服务。
 */
public interface JobMatchService {

    List<KnowledgeEntryVO> listKnowledge(Long userId);

    KnowledgeEntryVO addKnowledge(Long userId, String content);

    /** 将一段素材拆分为「单属性单内容」记录，并检测疑似重复（不入库）。 */
    KnowledgeSplitVO splitKnowledge(Long userId, String content);

    /** 导入简历文档并预览：解析原文 + AI 拆分 + 疑似重复提示（不入库）。 */
    ResumeImportVO importResumePreview(Long userId, MultipartFile file);

    /** 必填信息（姓名/学历/毕业院校/专业）拆分为对应属性记录并检测疑似重复（不入库）。 */
    KnowledgeSplitVO buildBasicInfo(Long userId, BasicInfoRequest request);

    /** 批量入库（支持按 replaceId 替换旧记录）。 */
    List<KnowledgeEntryVO> batchSaveKnowledge(Long userId, List<KnowledgeItem> items);

    /** 编辑单条记录（修改属性分类、内容与可选入库时间）。 */
    KnowledgeEntryVO updateKnowledge(Long userId, Long id, String type, String content, String createTime);

    /** 向已有入库批次中新增单条素材记录。 */
    KnowledgeEntryVO addKnowledgeItem(Long userId, String type, String content, String batchId, String createTime);

    /** 快速补充：将岗位待补足项转化为简历化的素材草稿（含自动匹配分类，不入库）。 */
    GenerateMaterialVO generateMaterial(Long userId, String requirement);

    void deleteKnowledge(Long id, Long userId);

    void clearKnowledge(Long userId);

    List<TargetJobVO> listJobs(Long userId);

    /** 粘贴导入岗位并预览：AI 提取整理为「岗位名称 + 岗位要求」单条结构化记录（不入库）。 */
    JobImportVO previewJobByText(Long userId, String content);

    /** 链接导入岗位并预览：双引擎爬取网页正文后 AI 标准化（不入库），失败时返回分类错误与部分原文。 */
    JobLinkCrawlVO previewJobByUrl(Long userId, String url);

    /** 导入岗位文档（PDF/DOC/DOCX，图片型 PDF 自动 OCR）并预览提取结果（不入库）。 */
    JobImportVO importJobPreview(Long userId, MultipartFile file);

    /** 新增目标岗位：岗位类型标签为空时由 AI 自动识别。 */
    TargetJobVO addJob(Long userId, String jobName, String jobRequirement, String companyName, String email, String location, String salary, String techStack, String jobType, String jobLink, String sourcePlatform);

    /** 编辑目标岗位：修改岗位名称与要求，入库时间保持不变，并同步更新岗位类型标签。 */
    TargetJobVO updateJob(Long userId, Long id, String jobName, String jobRequirement, String companyName, String email, String location, String salary);

    /** 批量删除目标岗位，返回删除数量。 */
    int deleteJobs(Long userId, List<Long> ids);

    /** 对目标岗位截图进行 OCR 识别，提取全文与技术栈并回填该岗位。 */
    JobOcrVO ocrJobScreenshot(Long userId, Long jobId, MultipartFile file);

    void deleteJob(Long id, Long userId);

    void clearJobs(Long userId);

    List<JobRankVO> analyze(Long userId);

    List<JobRankVO> latestAnalysis(Long userId);

    /** 异步发起排名计算：命中缓存直接返回已完成任务，否则创建后台任务返回任务句柄。 */
    AnalyzeTaskVO analyzeAsync(Long userId);

    /** 查询异步排名任务进度与分批结果。 */
    AnalyzeTaskVO analyzeTaskProgress(String taskId);

    /** 校验存储结果与当前数据版本是否一致（不触发计算，供前端渲染前快速核对）。 */
    MatchVerifyVO verifyMatch(Long userId);

    /**
     * 快速补充关闭待补足项：新增素材并确定性补分（该缺口 quota 直接加到当前分，补满封顶 100），
     * 不重新调用 LLM，确保「预计加分 = 实际加分」且「全部待补足项加分 + 当前分 = 100」严格成立。
     */
    List<JobRankVO> closeGap(Long userId, Long jobId, String requirement, String type, String content);

    JobResumeVO generateResume(Long userId, Long jobId);

    /** 简历模板列表（实时爬取 + 内置兜底）。 */
    List<ResumeTemplateVO> listResumeTemplates();

    /** 导出岗位定制简历为 Word(.docx)。 */
    byte[] exportResumeWord(JobResumeVO resume, String templateId);

    byte[] exportResumePdf(JobResumeVO resume, String templateId);
}