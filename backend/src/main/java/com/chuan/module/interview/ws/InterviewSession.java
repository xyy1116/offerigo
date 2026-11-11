package com.chuan.module.interview.ws;

import com.chuan.common.llm.ParaformerRealtimeClient;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 一次 AI 面试会话的运行时状态。
 */
@Data
@RequiredArgsConstructor
public class InterviewSession {

    public static final String PHASE_INTRO = "intro";
    public static final String PHASE_QUESTIONING = "questioning";
    public static final String PHASE_CLOSING = "closing";
    public static final String PHASE_DONE = "done";

    private final Long userId;
    private final WebSocketSession browserSession;
    /** 录音期间才建立的 Paraformer 会话；未录音时为 null */
    private ParaformerRealtimeClient.AsrSession asrSession;

    /** 完整对话记录（纯文本，含「面试官/候选人」标签，供大模型使用） */
    private final StringBuilder history = new StringBuilder();

    /** 逐条对话（供前端展示与入库） */
    private final List<String> transcript = new ArrayList<>();

    private String position;
    /** 从其他模块带入的岗位 JD 原文（用于生成针对性面试题目） */
    private String jdText;
    /** 从其他模块带入的候选人简历原文（用于结合经历追问） */
    private String resumeText;
    /** 核心技术栈（注入面试提问） */
    private String techStack;
    /** 题库方向：java / frontend / test / general */
    private String questionBankKey;
    /** 本场锁定的岗位族（如 A-后端、B-产品、A-后端+F-Agent），开场由模型判断后固定 */
    private String roleFamily;

    /** 面试官调研简报：开场前由 Agent 通过工具检索素材库与题库生成，注入系统提示 */
    private String interviewerBrief;

    /** 开场简报的异步任务：与 WS 连接准备并行，避免阻塞回复线程 */
    private java.util.concurrent.Future<com.chuan.module.interview.service.InterviewBriefAgent.Brief> briefFuture;
    /** 当前所处阶段：overview/tech/project/scenario/reverse */
    private String stage;
    /** 当前阶段已提出的正式问题数 */
    private int questionsInStage = 0;
    /** 当前知识点连续追问次数（限制上限，防止短回答触发无限追问死循环） */
    private int followUpCount = 0;
    /** 已覆盖的简历锚点/技术点，用于避免重复提问 */
    private final Set<String> coveredTopics = new LinkedHashSet<>();
    /** 已使用的压力提问次数（上限 3，见 InterviewOrchestrator.MAX_PRESSURE） */
    private int pressureCount = 0;
    /** 当前问题已发出的超时提醒次数（上限 2，收到正式作答后清零） */
    private int noAnswerCount = 0;
    /** 面试开始时间戳（毫秒），用于计算面试时长 */
    private long startTime = System.currentTimeMillis();

    private String phase = PHASE_QUESTIONING;
    /** 已提出的正式问题总数 */
    private int questionCount = 0;
    private Long recordId;

    /** 对话“轮次”计数：用户每次发声/打断自增，用于使在途的回复生成失效 */
    private volatile long generation = 0;

    /** 面试官是否正在说话（正在播放语音或生成回复），用于检测用户打断 */
    private volatile boolean speaking = false;

    /** 本轮待合并的语音识别文本缓冲：长回答可能被 Paraformer 拆成多句，合并后一次处理 */
    private final StringBuilder pendingUtterance = new StringBuilder();

    /** 最近一次追加的识别片段，用于相邻去重 */
    private String lastAppendedSegment;

    /** 最近一次已处理的完整语音文本，用于 3 秒窗口去重（Paraformer 偶发重复下发同一句） */
    private volatile String lastUtterance;

    /** 上述文本的处理时间戳（毫秒） */
    private volatile long lastUtteranceAt;

    /** 合并刷新定时任务（停止说话后短暂缓冲再合并成一条） */
    private volatile java.util.concurrent.ScheduledFuture<?> flushTask;
}