package com.chuan.module.interview.service;

import com.chuan.common.exception.BusinessException;
import com.chuan.common.llm.ParaformerRealtimeClient;
import com.chuan.common.llm.QwenChatClient;
import com.chuan.module.interview.entity.InterviewRecord;
import com.chuan.module.interview.vo.DimensionScore;
import com.chuan.module.interview.vo.InterviewReportVO;
import com.chuan.module.interview.ws.InterviewSession;
import com.chuan.module.resume.dto.ResumeContent;
import com.chuan.module.resume.service.ResumeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * AI 面试官编排器：协调语音识别、对话生成、语音合成与结构化面试阶段管理。
 *
 * <p>采用「强约束系统 Prompt + 岗位知识注入 + 5 阶段结构化流程 + 深度追问 + 结构化报告」，
 * 实现大厂级高压面试官的专业度，并支持中断续接。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewOrchestrator {

    // ==================== 5 阶段结构化流程 ====================
    private static final String STAGE_OVERVIEW = "overview";
    private static final String STAGE_TECH = "tech";
    private static final String STAGE_PROJECT = "project";
    private static final String STAGE_SCENARIO = "scenario";
    private static final String STAGE_REVERSE = "reverse";

    private static final String[] STAGE_ORDER = {STAGE_OVERVIEW, STAGE_TECH, STAGE_PROJECT, STAGE_SCENARIO, STAGE_REVERSE};

    private static final Map<String, Integer> STAGE_LIMIT = Map.of(
            STAGE_OVERVIEW, 2, STAGE_TECH, 3, STAGE_PROJECT, 3, STAGE_SCENARIO, 2, STAGE_REVERSE, 1);

    private static final Map<String, String> STAGE_LABEL = Map.of(
            STAGE_OVERVIEW, "履历概览", STAGE_TECH, "专业深挖", STAGE_PROJECT, "项目拷问",
            STAGE_SCENARIO, "场景题", STAGE_REVERSE, "反问环节");

    /** 同一知识点最多连续追问层数（达到上限后强制进入下一题，防止无限追问） */
    private static final int MAX_FOLLOW_UP = 2;

    /** 每场面试最多压力提问次数 */
    private static final int MAX_PRESSURE = 3;

    /** 超时提醒固定文案：提醒不是新题，因此不走大模型，避免被当成一轮提问 */
    private static final String[] NO_ANSWER_NUDGES = {
            "没关系，你可以先梳理一下思路，想到哪说到哪，我在听。",
            "如果没听清问题，我可以再重复一遍；你也可以先说说你的思路。"
    };

    /** 固定评分维度（顺序即报告展示顺序，模型输出后由代码强制回填名称） */
    private static final String[] DIMENSION_NAMES = {"专业能力深度", "岗位匹配度", "问题解决能力", "表达逻辑"};

    /**
     * 岗位族考察块。开场由模型判断并锁定岗位族，后续只注入对应块，
     * 既避免每轮重新判断导致考察重点漂移，也避免把技术岗标准套到非技术岗。
     */
    private static final java.util.Map<String, String> ROLE_FAMILY_BLOCKS = java.util.Map.of(
            "A", """
                    A 技术研发（后端/前端/客户端/全栈/算法/数据/运维/安全/嵌入式）
                    - 考察轴：技术深度、系统设计、性能与稳定性、工程质量、业务落地。
                    - 提问域：核心原理与选型、数据与存储、并发、性能与容量、故障与降级、可观测性、成本、安全、研发流程。
                    - 前端补充：渲染/加载性能、状态管理、兼容性、可访问性、构建发布、体验指标。
                    - 后端补充：一致性、缓存、队列、限流降级、数据迁移。
                    - 算法/数据补充：数据质量、离线/在线一致性、评估指标、偏差、成本。
                    - 运维/安全补充：SLO、变更风险、故障演练、告警、权限与合规。
                    """,
            "B", """
                    B 产品设计（产品经理/交互/视觉/用户研究）
                    - 考察轴：用户洞察、需求判断、方案设计、数据验证、推动落地。
                    - 提问域：用户问题、需求优先级、方案替代、指标定义、AB 实验、上线复盘、跨团队协作。
                    - 产品补充：需求来源、优先级取舍、指标树、实验设计、商业化。
                    - 设计/用研补充：信息架构、交互细节、可用性测试、设计取舍、无障碍。
                    - 指标口径：留存、转化、任务完成率、NPS、时长、收入、成本。
                    """,
            "C", """
                    C 测试质量（测试开发/QA/质量工程）
                    - 考察轴：测试设计、风险识别、自动化、质量度量、故障复盘。
                    - 提问域：用例设计、边界与异常、自动化分层、CI/CD、缺陷定位、线上质量、压测。
                    - 补充：测试左移/右移、质量门禁、漏测分析、稳定性治理。
                    """,
            "D", """
                    D 业务运营（运营/销售/市场/增长/客户成功）
                    - 考察轴：业务理解、目标拆解、策略设计、执行复盘、数据与 ROI。
                    - 提问域：目标拆解、用户/客户分层、渠道、转化、活动、复盘、跨部门协同。
                    - 运营/增长补充：漏斗、留存、拉新/召回、活动 ROI、实验。
                    - 销售/客户成功补充：客户分层、Pipeline、续约/流失、客单价、服务成本。
                    - 市场/品牌补充：定位、投放、内容、线索成本、品牌指标。
                    """,
            "E", """
                    E 职能管理（HR/财务/法务/行政/团队管理）
                    - 考察轴：专业判断、流程合规、沟通影响、风险控制、组织与团队。
                    - 提问域：专业场景、冲突处理、合规边界、跨部门推动、团队管理、决策取舍。
                    - HR 补充：招聘、绩效、员工关系、组织发展。
                    - 财务/法务补充：预算、成本、合规、合同与风险。
                    - 管理补充：目标对齐、授权、冲突、团队梯队。
                    """
    );

    /** AI/Agent 专项：叠加在 A 或 B 之上，岗位或简历明确涉及时必须覆盖。 */
    private static final String AI_FAMILY_BLOCK = """
            F AI/Agent 专项（叠加考察，必须覆盖）
            - 考察轴：模型能力边界、运行可靠性、评测、成本与延迟、安全合规、业务价值。
            - 必问域：
              1. 幻觉与事实性：怎么发现、怎么抑制、怎么兜底？
              2. 工具调用：失败、重试、幂等、超时、权限怎么处理？
              3. 多步规划：哪一步最容易失败？失败后怎么恢复？
              4. 上下文与记忆：截断、遗忘、串话、污染怎么处理？
              5. 非确定性：同一输入结果不稳定，怎么评测和回归？
              6. 线上监控：任务完成率、人工接管率、单位成本看哪些指标？
              7. 安全：prompt injection、数据泄露、越权、内容安全怎么防？
              8. 降级：模型不可用时退到规则/小模型/人工，怎么切换？
              9. 成本与延迟：token、缓存、并发、单位成本怎么优化？
            """;

    /** 首轮岗位族判断：只出现一次，之后改用已锁定族的考察块。 */
    private static final String FAMILY_INFERENCE_BLOCK = """
            # 第一步：锁定岗位族（整场不变，不得中途切换）
            根据岗位名称、JD 主职责、简历与技术栈判断岗位族：
            - A 技术研发：后端/前端/客户端/全栈/算法/数据/运维/安全/嵌入式
            - B 产品设计：产品经理/交互/视觉/用户研究
            - C 测试质量：测试开发/QA/质量工程
            - D 业务运营：运营/销售/市场/增长/客户成功
            - E 职能管理：HR/财务/法务/行政/团队管理
            - F AI/Agent 专项：LLM 应用、Agent 平台、算法工程；若涉及，叠加在 A 或 B 上。
            信息不足时以 JD 主职责为准。禁止把技术岗的考察方式套到非技术岗。
            请在 roleFamily 字段输出你的判断，例如：A-后端、B-产品、D-销售、A-后端+F-Agent。
            """;

    /** 全岗位共用的提问铁则、深挖链、横切必问与管控权衡。 */
    private static final String DEEP_DIVE_BLOCK = """
            # 提问铁则
            1. 每题必须有锚点：简历中的具体经历，或 JD 中的具体职责/技术点。先在 focus 写明锚点。
            2. 一次只问一个问题，1-2 句，不铺垫、不客套。
            3. 禁止跨族提问：产品岗不问 JVM，前端岗不问分库分表，职能岗不问架构治理。
            4. 禁止默认治理视角：只有岗位或经历明确相关时才问平台、规范、安全。
            5. 禁止八股原题、与岗位无关的闲聊、一次抛多问、空泛评价、复述错别字、暴露 AI 身份。
            6. 简历信息不足时，围绕 JD 和候选人已说出的内容提问，绝不编造。
            7. 已覆盖话题不要重复；优先补未覆盖的考察轴。
            8. 候选人跑题或答非所问：一句话拉回当前问题，不展开。

            # 深挖链（一轮只追一环）
            1. 证据：结论有没有数据、基线、口径、时间范围？
            2. 取舍：为什么选这个方案？替代方案是什么？代价是什么？
            3. 边界：异常、极端、失败、降级、回滚怎么办？
            4. 价值：对用户/业务/成本的具体影响？怎么衡量？
            5. 归因：你具体做了什么？换个场景还能复用吗？
            出现以下任一情况，判定为“没问透”，action 必须是 follow_up：
            - 只有结论，没有推导或证据；
            - 只有方案，没有取舍和替代对比；
            - 只讲正常路径，不讲异常与边界；
            - 只讲“提升了/做成了”，没有基线、口径、数据；
            - 用“我们/团队”模糊个人贡献。
            追问时只推进一环，不要一次问多个。

            # 横切必问（整场至少覆盖一项）
            - 业务价值：这件事对用户、收入、成本、效率的影响？成功指标怎么定义？
            - 量化验证：基线、口径、反例分别是什么？
            - 权衡：资源或时间减半，你会砍什么？为什么？
            - 复盘：如果重来一次，哪里会改？

            # 管控与灵活性（仅条件触发）
            仅当岗位涉及平台、中台、规范、安全、流程、架构治理时启用，且必须成对考察：
            - 这条规范/能力解决什么风险？不做会怎样？
            - 管控的代价：研发效率、交付周期、例外处理成本，有没有量化？
            - 例外机制：白名单、自助、审批、回收怎么设计？
            - 过度管控的反例：业务绕行、影子系统、效率下降，怎么发现？
            - 标准化 vs 自治：哪些必须统一，哪些应该放开？边界依据是什么？
            - 冲突时谁拍板？事后怎么复盘？
            禁止默认从治理视角提问；对业务研发岗，优先问业务落地、性能、故障、成本。
            """;

    /** 按岗位族给压力话术，禁止跨族使用。 */
    private static final String PRESSURE_BLOCK = """
            # 压力提问（只用与岗位族对应的一行，禁止跨族）
            A 技术：流量/数据量涨10倍哪里先崩；依赖故障怎么降级；回滚方案；监控盲区；个人贡献。
            B 产品：需求被砍、资源减半、指标不涨、老板拍板、竞品压力。
            C 测试：时间减半、需求频繁变更、线上事故、漏测复盘。
            D 业务：预算减半、目标翻倍、转化下滑、竞争加剧。
            E 职能：合规红线、利益冲突、资源不足、跨部门阻力。
            F AI/Agent：模型幻觉、工具连续失败、注入攻击、成本超支、人工接管。
            约束：一场最多 %s 次；不要连续两轮施压；只挑战方案，不攻击人；语气克制专业。
            """;

    private static final String STAGE_BLOCK = """
            # 阶段规则
            1. 履历概览（2题）：自我介绍；挑一段最相关的经历展开，确认基本匹配度。
            2. 专业深挖（3题）：非技术岗按本岗位专业能力深挖；技术岗按岗位族考察轴。
            3. 项目拷问（3题）：必须从简历中的具体项目/业务出发展开，按深挖链推进。
            4. 场景题（2题）：设计贴近该岗位真实工作的场景，考察拆解与判断。
            5. 反问环节（1次）：请候选人提问；基于 JD 和岗位如实回答，不知道或涉及公司内部信息就直说“这个我无法代表公司回答”，禁止编造；随后致谢收尾。
            """;

    private static final String OUTPUT_FORMAT_BLOCK = """
            # 输出格式（严格 JSON，不要代码块，不要额外文字）
            {
              "roleFamily": "已锁定的岗位族；首轮填写你的判断，后续原样返回",
              "axis": "本轮考察轴，例如：性能与稳定性",
              "action": "follow_up 或 next",
              "pressure": true 或 false,
              "focus": "锚点 + 考察意图，例如：简历·订单系统 / 验证降级方案",
              "say": "你对候选人说的原话，只包含一个追问或一个问题，1-2 句口语"
            }
            输出前自检：
            1. 岗位族是否与本轮问题匹配？有没有跨族提问？
            2. action=follow_up 仅当本轮可继续深挖 %s > 0；否则必须为 next。
            3. pressure=true 仅当本场还可压力提问 %s > 0。
            4. say 中只能有一个问号，禁止编号、列表、标题、“首先/其次/总结/作为AI”等字样。
            5. say 必须让候选人一听就知道你在问简历里的哪段经历或哪个职责。
            """;

    /** 断句合并缓冲时长（毫秒）。
     * max-sentence-silence 已降到 800ms 即时响应档，Paraformer 一次回答通常只下发一句；
     * 此缓冲仅用于合并近乎同时到达的重复句（偶发重复下发）。取极小值以缩短「停止说话→进入思考」的响应延迟；
     * 重复防护由 lastAppendedSegment（相邻片段去重）与 flushUtterance 内的 3 秒整句去重兜底。 */
    private static final long ASR_COALESCE_MS = 50;

    private final ObjectMapper objectMapper;
    private final QwenChatClient qwenChatClient;
    private final ParaformerRealtimeClient paraformerRealtimeClient;
    private final InterviewService interviewService;
    private final QuestionBankService questionBankService;
    private final ResumeService resumeService;

    /** sessionId -> 面试会话 */
    private final Map<String, InterviewSession> sessions = new ConcurrentHashMap<>();

    /** 用于异步生成回复与语音合成，避免阻塞语音识别 WebSocket 回环 */
    private final ExecutorService replyExecutor = Executors.newFixedThreadPool(8);

    /** 用于长回答断句合并的调度器 */
    private final ScheduledExecutorService asrScheduler = Executors.newSingleThreadScheduledExecutor();

    // ==================== 连接生命周期 ====================

    public void onConnected(WebSocketSession browserSession, Long userId) {
        InterviewSession session = new InterviewSession(userId, browserSession);
        sessions.put(browserSession.getId(), session);
        send(session, "ready", Map.of("message", "AI 面试官已就绪"));
    }

    public void onDisconnected(WebSocketSession browserSession) {
        InterviewSession s = sessions.remove(browserSession.getId());
        if (s != null) {
            if (s.getFlushTask() != null) {
                s.getFlushTask().cancel(true);
            }
            // 面试未结束且有实质内容时持久化快照，支持中断续接
            if (!InterviewSession.PHASE_DONE.equals(s.getPhase())
                    && StringUtils.hasText(s.getPosition()) && s.getQuestionCount() > 0) {
                saveSnapshotSafely(s);
            }
            if (s.getAsrSession() != null) {
                s.getAsrSession().close();
            }
        }
    }

    // ==================== 消息处理 ====================

    public void onText(WebSocketSession browserSession, String payload) {
        InterviewSession s = sessions.get(browserSession.getId());
        if (s == null) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("type").asText("");
            switch (type) {
                case "start" -> handleStart(s, node);
                case "resume" -> handleResume(s, node);
                case "finish" -> doFinish(s);
                case "user_text" -> handleUserUtterance(s, node.path("text").asText(""));
                case "interrupt" -> handleInterrupt(s);
                case "no_answer" -> handleNoAnswer(s);
                case "mic_start" -> handleMicStart(s);
                case "mic_stop" -> handleMicStop(s);
                default -> log.warn("未知面试消息类型: {}", type);
            }
        } catch (Exception e) {
            log.error("处理面试消息失败", e);
            send(s, "error", Map.of("message", "消息处理失败"));
        }
    }

    public void onBinary(WebSocketSession browserSession, byte[] pcm) {
        InterviewSession s = sessions.get(browserSession.getId());
        // 状态校验：面试已完成或未开始录音时拒绝识别音频
        if (s != null && s.getAsrSession() != null && !InterviewSession.PHASE_DONE.equals(s.getPhase())) {
            s.getAsrSession().sendAudio(pcm);
        }
    }

    /**
     * 用户点麦克风时才连接 Paraformer。
     * 避免浏览器连上后长时间没有音频，被百炼判为 NO_INPUT_AUDIO_ERROR。
     */
    private void handleMicStart(InterviewSession s) {
        ParaformerRealtimeClient.AsrSession old = s.getAsrSession();
        if (old != null) {
            old.close();
        }
        // 持有当前会话引用，丢弃已关闭旧会话的迟到回调
        final ParaformerRealtimeClient.AsrSession[] holder = new ParaformerRealtimeClient.AsrSession[1];
        ParaformerRealtimeClient.AsrSession asr = paraformerRealtimeClient.connect(new ParaformerRealtimeClient.AsrListener() {
            private boolean stale() {
                return s.getAsrSession() != holder[0];
            }

            @Override
            public void onReady() {
                if (!stale()) {
                    send(s, "asr_ready", Map.of("ready", true));
                }
            }

            @Override
            public void onResult(String text, boolean sentenceEnd) {
                if (!stale() && sentenceEnd && StringUtils.hasText(text)) {
                    handleAsrUtterance(s.getBrowserSession().getId(), text.trim());
                }
            }

            @Override
            public void onError(String message) {
                if (stale()) {
                    return;
                }
                // ASR 是可选输入通道：失败时只降级为文字输入，不作为阻塞错误弹窗
                log.warn("语音识别失败: {}", message);
                send(s, "asr_status", Map.of("ready", false,
                        "message", "语音识别暂不可用，可改用文字输入"));
            }
        });
        holder[0] = asr;
        s.setAsrSession(asr);
        asr.start();
    }

    private void handleMicStop(InterviewSession s) {
        ParaformerRealtimeClient.AsrSession asr = s.getAsrSession();
        if (asr != null) {
            asr.close();
            s.setAsrSession(null);
        }
    }

    // ==================== 编排逻辑 ====================

    private void handleStart(InterviewSession s, JsonNode node) {
        String position = node.path("position").asText("");
        if (!StringUtils.hasText(position)) {
            send(s, "error", Map.of("message", "请先选择面试岗位"));
            return;
        }
        String jdText = node.path("jdText").asText("");
        String resumeText = node.path("resumeText").asText("");
        String techStack = node.path("techStack").asText("");
        s.setPosition(position.trim());
        if (StringUtils.hasText(jdText)) {
            s.setJdText(jdText.trim());
        }
        if (StringUtils.hasText(resumeText)) {
            s.setResumeText(resumeText.trim());
        }
        if (StringUtils.hasText(techStack)) {
            s.setTechStack(techStack.trim());
        }
        // 前端未带入简历时，从在线简历模块兜底加载（面试提问的唯一事实来源）
        if (!StringUtils.hasText(s.getResumeText())) {
            s.setResumeText(loadResumeDigest(s.getUserId()));
        }

        // 全新面试：推断题库方向，进入履历概览阶段
        s.setQuestionBankKey(questionBankService.inferKey(s.getPosition(), s.getTechStack()));
        s.setStage(STAGE_OVERVIEW);
        s.setQuestionsInStage(0);
        s.setQuestionCount(0);
        s.setStartTime(System.currentTimeMillis());
        askFirstQuestion(s);
    }

    /** 中断续接：加载该用户进行中的面试，恢复阶段与对话记录后继续。 */
    private void handleResume(InterviewSession s, JsonNode node) {
        String position = node.path("position").asText("");
        if (StringUtils.hasText(position)) {
            s.setPosition(position.trim());
        }
        InterviewRecord record = interviewService.getActive(s.getUserId());
        if (record == null || !"in_progress".equals(record.getStatus())) {
            // 没有进行中的面试，退化为全新开始
            s.setQuestionBankKey(questionBankService.inferKey(s.getPosition(), s.getTechStack()));
            s.setStage(STAGE_OVERVIEW);
            s.setQuestionsInStage(0);
            s.setQuestionCount(0);
            s.setStartTime(System.currentTimeMillis());
            if (!StringUtils.hasText(s.getResumeText())) {
                s.setResumeText(loadResumeDigest(s.getUserId()));
            }
            askFirstQuestion(s);
            return;
        }
        resumeSession(s, record);
    }

    private void resumeSession(InterviewSession s, InterviewRecord record) {
        s.setPosition(record.getPosition());
        s.setJdText(record.getJdText());
        s.setTechStack(record.getTechStack());
        // 简历未持久化在面试记录中，续接时按用户重新加载最新版本
        if (!StringUtils.hasText(s.getResumeText())) {
            s.setResumeText(loadResumeDigest(s.getUserId()));
        }
        s.setQuestionBankKey(record.getQuestionBankKey());
        if (!StringUtils.hasText(s.getQuestionBankKey())) {
            s.setQuestionBankKey(questionBankService.inferKey(s.getPosition(), s.getTechStack()));
        }
        s.setStage(StringUtils.hasText(record.getStage()) ? record.getStage() : STAGE_OVERVIEW);
        s.setQuestionsInStage(record.getStageQuestionCount() == null ? 0 : record.getStageQuestionCount());
        s.setQuestionCount(record.getQuestionCount() == null ? 0 : record.getQuestionCount());
        s.setFollowUpCount(0);
        s.setStartTime(System.currentTimeMillis());
        s.setRecordId(record.getId());

        // 回填对话记录（供大模型上下文与前端恢复对话区）
        if (StringUtils.hasText(record.getTranscript())) {
            for (String line : record.getTranscript().split("\n")) {
                if (StringUtils.hasText(line)) {
                    s.getTranscript().add(line);
                    s.getHistory().append(line).append("\n");
                }
            }
        }
        send(s, "resume_history", Map.of(
                "position", s.getPosition(),
                "transcript", new ArrayList<>(s.getTranscript())));

        // 续接：进入当前阶段下一题
        askNext(s, "我们继续进行面试。");
    }

    /** 全新面试开场：引导放松并问履历概览第一题。 */
    private void askFirstQuestion(InterviewSession s) {
        send(s, "thinking", Map.of("value", true));
        long gen = s.getGeneration();
        replyExecutor.submit(() -> {
            if (s.getGeneration() != gen) {
                return;
            }
            try {
                TurnDecision d = generateTurn(s,
                        "面试正式开始。请用一句话自然开场，然后开启【履历概览】阶段，发出本阶段第1个问题（如自我介绍或概览TA的经历）。",
                        0, MAX_PRESSURE);
                if (s.getGeneration() != gen) {
                    return;
                }
                addCovered(s, d.focus());
                s.setQuestionsInStage(1);
                s.setQuestionCount(1);
                s.setFollowUpCount(0);
                sendStage(s, 1);
                deliver(s, d.say(), gen);
                saveSnapshotSafely(s);
            } catch (Exception e) {
                log.error("生成开场问题失败", e);
                if (s.getGeneration() == gen) {
                    send(s, "error", Map.of("message", "AI 面试官响应失败，请稍后重试"));
                }
            }
        });
    }

    /** 续接时当前阶段发下一题（含阶段边界推进）。 */
    private void askNext(InterviewSession s, String preamble) {
        long gen = s.getGeneration();
        // 反问阶段在快照中已完成提问，直接收尾
        if (STAGE_REVERSE.equals(s.getStage())) {
            closeAndFinish(s, gen);
            return;
        }
        advanceStageIfComplete(s);
        int nextNo = s.getQuestionsInStage() + 1;
        int pressureBudget = Math.max(0, MAX_PRESSURE - s.getPressureCount());
        String stageName = stageLabel(s.getStage());
        send(s, "thinking", Map.of("value", true));
        replyExecutor.submit(() -> {
            if (s.getGeneration() != gen) {
                return;
            }
            try {
                TurnDecision d = generateTurn(s,
                        preamble + "请围绕【" + stageName + "】阶段继续面试，发出本阶段第" + nextNo + "个问题。",
                        0, pressureBudget);
                if (s.getGeneration() != gen) {
                    return;
                }
                addCovered(s, d.focus());
                s.setQuestionsInStage(nextNo);
                s.setQuestionCount(s.getQuestionCount() + 1);
                s.setFollowUpCount(0);
                sendStage(s, nextNo);
                deliver(s, d.say(), gen);
                saveSnapshotSafely(s);
            } catch (Exception e) {
                log.error("生成续接问题失败", e);
                if (s.getGeneration() == gen) {
                    send(s, "error", Map.of("message", "AI 面试官响应失败，请稍后重试"));
                }
            }
        });
    }

    /** 反问环节作答完毕：致谢收尾并生成评估报告。 */
    private void closeAndFinish(InterviewSession s, long gen) {
        s.setPhase(InterviewSession.PHASE_CLOSING);
        send(s, "stage", Map.of("stage", STAGE_REVERSE, "stageLabel", stageLabel(STAGE_REVERSE), "closing", true));
        replyExecutor.submit(() -> {
            if (s.getGeneration() != gen) {
                return;
            }
            try {
                TurnDecision d = generateTurn(s,
                        "请简短致谢，并告知候选人面试环节已经结束，稍后将生成评估报告。", 0, 0);
                if (s.getGeneration() == gen) {
                    deliver(s, d.say(), gen);
                }
            } catch (Exception e) {
                log.error("生成收尾语失败", e);
            }
            // 即使收尾语生成失败也继续出报告，避免候选人卡在结束环节
            if (s.getGeneration() == gen) {
                doFinish(s);
            }
        });
    }

    /** 语音识别整句结果：追加至本轮缓冲，短暂等待以合并长回答被拆分的多句。 */
    private void handleAsrUtterance(String sessionId, String text) {
        InterviewSession s = sessions.get(sessionId);
        if (s == null || !StringUtils.hasText(text)) {
            return;
        }
        // 状态校验：识别完成/面试结束后不再接收新的语音片段
        if (InterviewSession.PHASE_DONE.equals(s.getPhase())) {
            return;
        }
        String cleaned = text.trim();
        // 相邻重复句去重：Paraformer 偶发重复下发同一句
        if (cleaned.equals(s.getLastAppendedSegment())) {
            return;
        }
        // 片段包含去重：若该片段已完整包含在当前缓冲中（偶发重复/累积重发），跳过
        if (s.getPendingUtterance().toString().contains(cleaned)) {
            return;
        }
        s.setLastAppendedSegment(cleaned);
        s.getPendingUtterance().append(cleaned);

        // 每来一句重置计时：停止说话满 ASR_COALESCE_MS 后将整段作为一条内容处理，
        // 避免长回答被 Paraformer 按静音边界拆成两段分别触发。
        if (s.getFlushTask() != null) {
            s.getFlushTask().cancel(false);
        }
        s.setFlushTask(asrScheduler.schedule(() -> flushUtterance(s), ASR_COALESCE_MS, TimeUnit.MILLISECONDS));
    }

    /** 合并到期：将缓冲的整段语音一次性交给对话处理。 */
    private void flushUtterance(InterviewSession s) {
        s.setFlushTask(null);
        if (InterviewSession.PHASE_DONE.equals(s.getPhase())) {
            return;
        }
        String text = s.getPendingUtterance().toString().trim();
        s.getPendingUtterance().setLength(0);
        s.setLastAppendedSegment(null);
        if (!StringUtils.hasText(text)) {
            return;
        }
        // 3 秒窗口去重：Paraformer 偶发对同一次发言重复下发整句，避免重复进入对话处理
        long now = System.currentTimeMillis();
        if (text.equals(s.getLastUtterance()) && (now - s.getLastUtteranceAt()) <= 3000) {
            log.info("ASR 重复语句去重（3s 窗口）: {}", text);
            return;
        }
        s.setLastUtterance(text);
        s.setLastUtteranceAt(now);
        if (isRelevantUtterance(text)) {
            // 语音识别结果回传前端填充输入框，不自动触发回复；用户编辑后手动点击发送才提交
            send(s, "asr_result", Map.of("text", text));
        }
    }

    /** 轻量规则预筛：仅过滤明显的纯噪音/无意义片段（过短、无有效字符），零延迟、不调用大模型。 */
    private boolean isRelevantUtterance(String text) {
        String t = text == null ? "" : text.trim();
        if (t.length() < 2) {
            return false;
        }
        return t.matches(".*[\\u4e00-\\u9fa5A-Za-z0-9].*");
    }

    private void handleUserUtterance(String sessionId, String text) {
        InterviewSession s = sessions.get(sessionId);
        if (s != null) {
            handleUserUtterance(s, text);
        }
    }

    private void handleUserUtterance(InterviewSession s, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        // 状态校验：面试已结束则拒绝处理新的用户输入（含文字与语音路径）
        if (InterviewSession.PHASE_DONE.equals(s.getPhase())) {
            return;
        }

        // 收到正式作答：重置超时提醒计数
        s.setNoAnswerCount(0);

        // 用户开始作答：立即使任何在途回复生成失效并停止"面试官正在说话"状态（打断机制）
        s.setSpeaking(false);
        s.setGeneration(s.getGeneration() + 1);
        long gen = s.getGeneration();

        String userText = text.trim();
        s.getTranscript().add("候选人：" + userText);
        send(s, "user_final", Map.of("text", userText));
        // 进入思考状态：此前端暂停用户语音识别，避免思考期语音输入被处理
        send(s, "thinking", Map.of("value", true));

        // 反问环节作答完毕 → 直接收尾（不再新增问题数）
        if (STAGE_REVERSE.equals(s.getStage())) {
            closeAndFinish(s, gen);
            return;
        }

        // 阶段已满则先推进；阶段切换的这一轮不再追问，直接发新阶段首题
        boolean advanced = advanceStageIfComplete(s);
        boolean deepDiveStage = STAGE_TECH.equals(s.getStage()) || STAGE_PROJECT.equals(s.getStage());
        int followUpBudget = (!advanced && deepDiveStage)
                ? Math.max(0, MAX_FOLLOW_UP - s.getFollowUpCount()) : 0;
        int pressureBudget = Math.max(0, MAX_PRESSURE - s.getPressureCount());
        String stageName = stageLabel(s.getStage());
        String instruction = advanced
                ? "候选人的回答已结束上一阶段。现在进入【" + stageName + "】阶段，请发出本阶段第1个问题。"
                : "请根据候选人的上一轮回答决定是否继续深挖；若不需要深挖，则围绕【" + stageName + "】阶段发出下一题。";

        replyExecutor.submit(() -> {
            if (s.getGeneration() != gen) {
                return;
            }
            try {
                s.getHistory().append("候选人：").append(userText).append("\n");
                TurnDecision d = generateTurn(s, instruction, followUpBudget, pressureBudget);
                if (s.getGeneration() != gen) {
                    return;
                }
                addCovered(s, d.focus());
                if (d.pressure() && pressureBudget > 0) {
                    s.setPressureCount(s.getPressureCount() + 1);
                }
                if ("follow_up".equals(d.action()) && followUpBudget > 0) {
                    s.setFollowUpCount(s.getFollowUpCount() + 1);
                    sendStage(s, s.getQuestionsInStage());
                } else {
                    s.setQuestionsInStage(s.getQuestionsInStage() + 1);
                    s.setQuestionCount(s.getQuestionCount() + 1);
                    s.setFollowUpCount(0);
                    sendStage(s, s.getQuestionsInStage());
                    saveSnapshotSafely(s);
                }
                deliver(s, d.say(), gen);
            } catch (Exception e) {
                log.error("生成面试官回复失败", e);
                if (s.getGeneration() == gen) {
                    send(s, "error", Map.of("message", "AI 面试官响应失败，请稍后重试"));
                }
            }
        });
    }

    /** 若当前阶段题数已满，推进到下一阶段（反问阶段结束后不再推进）。 */
    private boolean advanceStageIfComplete(InterviewSession s) {
        if (s.getQuestionsInStage() >= stageLimit(s.getStage())) {
            String next = nextStage(s.getStage());
            if (next != null) {
                s.setStage(next);
                s.setQuestionsInStage(0);
                return true;
            }
        }
        return false;
    }

    /** 用户打断（前端 VAD 检测到用户开始说话时触发）。 */
    private void handleInterrupt(InterviewSession s) {
        s.setSpeaking(false);
        s.setGeneration(s.getGeneration() + 1);
    }

    /** 回答超时：最多温和提醒两次；提醒不走大模型，不会被当成新一轮提问。 */
    private void handleNoAnswer(InterviewSession s) {
        if (InterviewSession.PHASE_DONE.equals(s.getPhase())
                || s.getNoAnswerCount() >= NO_ANSWER_NUDGES.length) {
            return;
        }
        s.setGeneration(s.getGeneration() + 1);
        long gen = s.getGeneration();
        String nudge = NO_ANSWER_NUDGES[s.getNoAnswerCount()];
        s.setNoAnswerCount(s.getNoAnswerCount() + 1);
        deliver(s, nudge, gen);
    }

    private void doFinish(InterviewSession s) {
        if (InterviewSession.PHASE_DONE.equals(s.getPhase())) {
            return;
        }
        // 结束面试：使任何在途回复失效并停止播放
        s.setSpeaking(false);
        s.setGeneration(s.getGeneration() + 1);
        s.setPhase(InterviewSession.PHASE_DONE);
        send(s, "thinking", Map.of("value", true));
        try {
            InterviewReportVO report = generateReport(s);
            BigDecimal score = report.getScore() == null ? null : BigDecimal.valueOf(report.getScore());
            Long recordId;
            if (s.getRecordId() != null) {
                // 续接场景：将原 in_progress 快照转为完成，避免产生重复记录
                interviewService.finalizeRecord(s.getRecordId(), toTranscript(s), report, score);
                recordId = s.getRecordId();
            } else {
                recordId = interviewService.saveRecord(s.getUserId(), s.getPosition(), toTranscript(s), report, score);
            }
            s.setRecordId(recordId);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("report", report);
            payload.put("score", score);
            payload.put("recordId", recordId);
            send(s, "report", payload);
            send(s, "phase", Map.of("phase", InterviewSession.PHASE_DONE));
        } catch (Exception e) {
            log.error("生成评估报告失败", e);
            send(s, "error", Map.of("message", "评估报告生成失败，请稍后重试"));
        }
    }

    // ==================== 大模型调用 ====================

    private List<Map<String, Object>> buildReplyMessages(InterviewSession s, String instruction,
                                                         int followUpBudget, int pressureBudget) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(role("system", buildSystemPrompt(s, followUpBudget, pressureBudget)));
        messages.add(role("user", instruction + "\n\n【面试对话记录】\n" + s.getHistory()));
        return messages;
    }

    /**
     * 一次调用同时拿到「本轮决策 + 面试官原话」。
     * 模型返回非 JSON 时退化为纯文本：整段作为口语、按下一题处理，避免面试中断。
     */
    private TurnDecision generateTurn(InterviewSession s, String instruction,
                                      int followUpBudget, int pressureBudget) {
        String raw = qwenChatClient.chatJson(buildReplyMessages(s, instruction, followUpBudget, pressureBudget));
        try {
            JsonNode node = objectMapper.readTree(raw);
            String say = node.path("say").asText("").trim();
            if (!StringUtils.hasText(say)) {
                say = raw.trim();
            }
            String action = "follow_up".equals(node.path("action").asText("")) ? "follow_up" : "next";
            String family = node.path("roleFamily").asText("").trim();
            // 开场锁定岗位族，后续整场复用，避免考察重点漂移
            if (StringUtils.hasText(family) && !StringUtils.hasText(s.getRoleFamily())) {
                s.setRoleFamily(family);
            }
            return new TurnDecision(family, action, node.path("pressure").asBoolean(false),
                    node.path("focus").asText("").trim(), say);
        } catch (Exception e) {
            log.warn("面试官输出非 JSON，按纯文本处理: {}", raw);
            return new TurnDecision("", "next", false, "", raw.trim());
        }
    }

    private record TurnDecision(String roleFamily, String action, boolean pressure, String focus, String say) {
    }

    /** 记录已覆盖话题，限制条数避免系统提示词无限增长。 */
    private void addCovered(InterviewSession s, String focus) {
        if (!StringUtils.hasText(focus)) {
            return;
        }
        String f = focus.trim();
        if (f.length() > 60) {
            f = f.substring(0, 60);
        }
        if (s.getCoveredTopics().size() < 20) {
            s.getCoveredTopics().add(f);
        }
    }

    /** 读取用户在线简历并序列化为紧凑文本，作为面试提问的唯一事实来源。失败时返回空串。 */
    private String loadResumeDigest(Long userId) {
        try {
            ResumeContent c = resumeService.get(userId);
            if (c == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            if (c.getBasic() != null && StringUtils.hasText(c.getBasic().getName())) {
                sb.append("姓名：").append(c.getBasic().getName()).append("\n");
            }
            if (c.getEducations() != null && !c.getEducations().isEmpty()) {
                sb.append("教育经历：\n");
                for (ResumeContent.Education e : c.getEducations()) {
                    sb.append("- ").append(safe(e.getSchool())).append(" ").append(safe(e.getDegree()))
                            .append(" ").append(safe(e.getGraduateDate())).append("\n");
                }
            }
            if (c.getInternships() != null && !c.getInternships().isEmpty()) {
                sb.append("实习/项目经历：\n");
                for (ResumeContent.Internship i : c.getInternships()) {
                    sb.append("- ").append(safe(i.getCompany())).append(" / ").append(safe(i.getPosition()))
                            .append(" / ").append(safe(i.getStartDate())).append("~").append(safe(i.getEndDate()))
                            .append("：").append(safe(i.getDescription())).append("\n");
                }
            }
            if (c.getCampusActivities() != null && !c.getCampusActivities().isEmpty()) {
                sb.append("校园经历：\n");
                for (ResumeContent.CampusActivity a : c.getCampusActivities()) {
                    sb.append("- ").append(safe(a.getOrganization())).append(" / ").append(safe(a.getPosition()))
                            .append("：").append(safe(a.getDuty())).append("\n");
                }
            }
            if (c.getCertificates() != null && !c.getCertificates().isEmpty()) {
                sb.append("证书：");
                for (ResumeContent.Certificate cert : c.getCertificates()) {
                    sb.append(safe(cert.getName())).append(" ");
                }
                sb.append("\n");
            }
            if (StringUtils.hasText(c.getSelfEvaluation())) {
                sb.append("自我评价：").append(c.getSelfEvaluation().trim()).append("\n");
            }
            if (c.getCareerGoal() != null && StringUtils.hasText(c.getCareerGoal().getTargetPosition())) {
                sb.append("求职意向：").append(c.getCareerGoal().getTargetPosition()).append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("加载候选人简历失败: {}", e.getMessage());
            return "";
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /** 评分 Agent 系统提示词：固定维度 + 锚点区间 + 强制证据，总分由代码聚合。 */
    private static final String SCORING_SYSTEM_PROMPT = """
            你是面试评估专家。请只根据【岗位 JD、候选人简历、完整面试对话】评分，不得脑补未出现的内容。

            固定 4 个维度，每个 0-25 分。每个维度必须先写 evidence（引用候选人原话或具体表现），再按锚点给分；
            没有 evidence 时分数不得超过 10；该维度本轮未充分考察时不得超过 12，并在 comment 注明“本轮未充分考察”。
            “专业能力深度”按候选人所在岗位族评价；非技术岗不要用编程、架构、平台治理标准打分。

            锚点：
            - 专业能力深度：0-5 无有效信息或答错；6-10 只知名词；11-15 懂方法但缺取舍；16-20 有取舍、踩坑复盘；21-25 能对比替代方案并量化。
            - 岗位匹配度：0-5 方向不符；6-10 部分匹配；11-15 基本匹配；16-20 高度匹配且有相关项目；21-25 经验直接对应且可迁移。
            - 问题解决能力：0-5 无思路；6-10 有思路无落地；11-15 能落地但不深入；16-20 有取舍和验证；21-25 系统化拆解且能反思。
            - 表达逻辑：0-5 混乱；6-10 勉强听懂；11-15 有结构；16-20 重点突出；21-25 简洁精准有说服力。

            只输出一个 JSON 对象，不要代码块、不要任何其他文字。结构与字段：
            {
              "summary": "一句话总体评价",
              "dimensions": [
                {"name":"专业能力深度","score":0,"comment":"一句话评语","evidence":"原话或具体表现"},
                {"name":"岗位匹配度","score":0,"comment":"一句话评语","evidence":"原话或具体表现"},
                {"name":"问题解决能力","score":0,"comment":"一句话评语","evidence":"原话或具体表现"},
                {"name":"表达逻辑","score":0,"comment":"一句话评语","evidence":"原话或具体表现"}
              ],
              "strengths": ["核心优势1", "核心优势2"],
              "weaknesses": ["具体可执行的待提升点1", "具体可执行的待提升点2"]
            }
            """;

    private InterviewReportVO generateReport(InterviewSession s) {
        int durationMinutes = (int) Math.max(1, (System.currentTimeMillis() - s.getStartTime()) / 60000);
        StringBuilder prompt = new StringBuilder();
        prompt.append("面试岗位：").append(s.getPosition()).append("\n");
        if (StringUtils.hasText(s.getRoleFamily())) {
            prompt.append("岗位族：").append(s.getRoleFamily()).append("\n");
        }
        if (StringUtils.hasText(s.getJdText())) {
            prompt.append("岗位 JD：\n").append(s.getJdText().trim()).append("\n");
        }
        if (StringUtils.hasText(s.getResumeText())) {
            prompt.append("候选人简历：\n").append(s.getResumeText().trim()).append("\n");
        }
        prompt.append("面试总问题数：").append(s.getQuestionCount()).append("\n");
        prompt.append("\n【面试对话记录】\n").append(s.getHistory());

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(role("system", SCORING_SYSTEM_PROMPT));
        messages.add(role("user", prompt.toString()));
        // 低温度降低同一对话多次评分的波动
        String json = qwenChatClient.chatJson(messages, 0.1);
        try {
            InterviewReportVO report = objectMapper.readValue(json, InterviewReportVO.class);
            int total = normalizeDimensions(report.getDimensions());
            // 总分只由代码聚合，不信任模型自报的总分
            report.setScore(total);
            report.setLevel(levelOf(total));
            report.setPosition(s.getPosition());
            report.setQuestionCount(s.getQuestionCount());
            report.setDurationMinutes(durationMinutes);
            return report;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("评估报告 JSON 解析失败: {}", json, e);
            throw new BusinessException("评估报告解析失败");
        }
    }

    /**
     * 强制固定维度名、钳位分数、无证据降档，并返回由代码聚合的总分。
     * 包内可见以便单元测试覆盖评分稳定性逻辑。
     */
    static int normalizeDimensions(List<DimensionScore> dims) {
        if (dims == null || dims.size() != DIMENSION_NAMES.length) {
            throw new BusinessException("评估报告维度不完整");
        }
        int total = 0;
        for (int i = 0; i < dims.size(); i++) {
            DimensionScore d = dims.get(i);
            // 名称由代码固定，避免模型改维度名导致前后端展示错位
            d.setName(DIMENSION_NAMES[i]);
            int score = d.getScore() == null ? 0 : Math.max(0, Math.min(25, d.getScore()));
            // 无证据视为未考察，强制降档，防止模型凭印象抬分
            if (!StringUtils.hasText(d.getEvidence())) {
                score = Math.min(score, 10);
            }
            d.setScore(score);
            total += score;
        }
        return total;
    }

    private String levelOf(int score) {
        if (score >= 85) {
            return "优秀";
        }
        if (score >= 70) {
            return "良好";
        }
        if (score >= 60) {
            return "中等";
        }
        return "待提升";
    }

    /** 面试官 Agent 系统提示词：简历锚定 + 结构化回合决策 + 深挖/压力规则 + JSON 输出契约。 */
    private String buildSystemPrompt(InterviewSession s, int followUpBudget, int pressureBudget) {
        String position = s.getPosition() == null ? "" : s.getPosition();
        String jdText = s.getJdText();
        String techStack = s.getTechStack();
        String resume = s.getResumeText();
        List<String> bank = questionBankService.getQuestions(s.getQuestionBankKey());
        String covered = s.getCoveredTopics().isEmpty() ? "（暂无）" : String.join("；", s.getCoveredTopics());

        StringBuilder sb = new StringBuilder();
        sb.append("# 角色\n")
                .append("你是互联网/科技行业的资深面试官，正在面试「").append(position).append("」岗位。")
                .append("你判断精准、节奏克制，只问与该岗位和候选人经历强相关的问题。")
                .append("你的目标不是聊天，而是在有限时间内验证简历真实性、评估岗位胜任力。\n");

        sb.append("\n# 可用信息（唯一事实来源）\n");
        sb.append("1. 岗位 JD：\n");
        if (StringUtils.hasText(jdText)) {
            sb.append(jdText.trim()).append("\n");
        } else {
            sb.append("（未提供）\n");
        }
        sb.append("2. 核心技术栈/工具：").append(StringUtils.hasText(techStack) ? techStack.trim() : "（未提供）").append("\n");
        sb.append("3. 候选人简历（禁止编造简历中不存在的项目、公司、数据）：\n");
        if (StringUtils.hasText(resume)) {
            sb.append(resume.trim()).append("\n");
        } else {
            sb.append("（未获取到简历；请围绕 JD 与候选人已陈述的内容提问，禁止编造）\n");
        }
        if (bank != null && !bank.isEmpty()) {
            sb.append("4. 参考高频题（只用于校准难度和方向，禁止照搬；若与岗位族不匹配，直接忽略）：\n");
            for (int i = 0; i < bank.size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(bank.get(i)).append("\n");
            }
        }

        String family = s.getRoleFamily();
        if (!StringUtils.hasText(family)) {
            sb.append("\n").append(FAMILY_INFERENCE_BLOCK);
        } else {
            sb.append("\n# 本场岗位族（已锁定，不得更改）\n").append(family).append("\n");
            appendFamilyBlock(sb, family);
        }

        sb.append("\n# 当前面试状态\n")
                .append("- 阶段：").append(stageLabel(s.getStage()))
                .append("（本阶段已提问 ").append(s.getQuestionsInStage()).append("/").append(stageLimit(s.getStage())).append(" 题）\n")
                .append("- 已覆盖话题：").append(covered).append("\n")
                .append("- 本轮可继续深挖：").append(followUpBudget).append(" 层\n")
                .append("- 本场还可压力提问：").append(pressureBudget).append(" 次\n");

        sb.append("\n").append(DEEP_DIVE_BLOCK);
        sb.append("\n").append(PRESSURE_BLOCK.formatted(pressureBudget));
        sb.append("\n").append(STAGE_BLOCK);
        sb.append("\n").append(OUTPUT_FORMAT_BLOCK.formatted(followUpBudget, pressureBudget));
        return sb.toString();
    }

    /** 只注入已锁定岗位族的考察块；AI/Agent 作为叠加块。 */
    private void appendFamilyBlock(StringBuilder sb, String family) {
        String key = family.substring(0, 1).toUpperCase();
        if ("F".equals(key)) {
            sb.append(ROLE_FAMILY_BLOCKS.get("A")).append(AI_FAMILY_BLOCK);
            return;
        }
        sb.append(ROLE_FAMILY_BLOCKS.getOrDefault(key, ROLE_FAMILY_BLOCKS.get("A")));
        if (family.toUpperCase().contains("F")) {
            sb.append(AI_FAMILY_BLOCK);
        }
    }

    // ==================== 消息下发 ====================

    /** 下发阶段信息：顶部状态栏展示当前岗位 + 当前阶段 + 阶段内题号。 */
    private void sendStage(InterviewSession s, int question) {
        String stage = s.getStage();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stage", stage);
        data.put("stageLabel", stageLabel(stage));
        data.put("question", question);
        data.put("stageLimit", stageLimit(stage));
        send(s, "stage", data);
    }

    /** 下发面试官回复：仅下发文字（已移除语音播报），保证文字即时显示。任意阶段被用户打断则丢弃后续下发。 */
    private void deliver(InterviewSession s, String text, long gen) {
        if (!StringUtils.hasText(text) || s.getGeneration() != gen) {
            return;
        }
        s.getHistory().append("面试官：").append(text).append("\n");
        s.getTranscript().add("面试官：" + text);
        s.setSpeaking(true);
        send(s, "assistant_text", Map.of("text", text));
    }

    private void saveSnapshotSafely(InterviewSession s) {
        try {
            Long id = interviewService.saveSnapshot(
                    s.getUserId(), s.getPosition(), s.getJdText(), s.getTechStack(),
                    s.getQuestionBankKey(), s.getStage(),
                    s.getQuestionsInStage(), s.getQuestionCount(), toTranscript(s));
            if (id != null) {
                s.setRecordId(id);
            }
        } catch (Exception e) {
            log.warn("保存面试快照失败: {}", e.getMessage());
        }
    }

    private void send(InterviewSession s, String type, Map<String, Object> data) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", type);
        if (data != null) {
            message.putAll(data);
        }
        // 统一时间戳：供前端做消息时序同步，防止状态类消息乱序导致视觉提示回退
        message.put("ts", System.currentTimeMillis());
        try {
            String json = objectMapper.writeValueAsString(message);
            synchronized (s.getBrowserSession()) {
                s.getBrowserSession().sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.warn("下发面试消息失败: {}", e.getMessage());
        }
    }

    private Map<String, Object> role(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private String toTranscript(InterviewSession s) {
        return String.join("\n", s.getTranscript());
    }

    // ==================== 阶段工具 ====================

    private int stageLimit(String stage) {
        return STAGE_LIMIT.getOrDefault(stage, 1);
    }

    private String nextStage(String stage) {
        for (int i = 0; i < STAGE_ORDER.length; i++) {
            if (STAGE_ORDER[i].equals(stage) && i + 1 < STAGE_ORDER.length) {
                return STAGE_ORDER[i + 1];
            }
        }
        return null;
    }

    private String stageLabel(String stage) {
        return STAGE_LABEL.getOrDefault(stage, "面试中");
    }
}