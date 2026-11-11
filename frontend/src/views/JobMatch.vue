<template>
  <div class="jobmatch-page" @click="notifyRecommendSeen">
    <div class="job-view-bar" v-reveal>
      <div class="job-view-heading">
        <h1 class="job-view-title">{{ viewTitle }}</h1>
        <span class="job-view-count">{{ initialLoading ? '加载中…' : `${displayedJobs.length} 个岗位` }}</span>
        <span v-if="jobActiveCat === 'recommend'" class="job-view-update">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="12" r="8" />
            <path d="M12 7.5V12l3 2" />
          </svg>
          每日 08:00 自动更新
        </span>
      </div>
      <div class="page-header-tools job-view-tools">
        <el-input v-model="jobFilterKeyword" clearable placeholder="搜索岗位名称" class="toolbar-search" />
        <el-select v-model="jobSortKey" class="toolbar-sort">
          <el-option v-for="opt in SORT_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <button v-if="jobActiveCat === 'all'" class="button button--small" @click="importChoiceVisible = true">导入岗位</button>
        <el-tooltip content="调整技能、经历、学历、契合度在总分中的权重" placement="top">
          <button class="button button--plain button--small" @click="weightConfigVisible = true">匹配权重</button>
        </el-tooltip>
        <template v-if="jobActiveCat === 'all' && selectedCount">
          <label class="select-all-toggle" title="全选当前列表">
            <input type="checkbox" :checked="allSelected" @change="toggleSelectAll" />
            <span>{{ allSelected ? '取消全选' : '全选' }}</span>
          </label>
          <span class="selected-count">已选 {{ selectedCount }}</span>
          <button
            class="batch-action batch-action--danger"
            :disabled="batchDeleting"
            @click="batchDeleteCurrent"
          >批量删除</button>
        </template>
        <button v-if="jobActiveCat === 'all' && !initialLoading && !jobList.length" class="button button--plain button--small" @click="jobPasteBox && focusJobPaste()">粘贴导入</button>
      </div>
    </div>

    <!-- 我的简历素材：右上角固定悬浮入口（挂到 body，避免路由切换的 transform 把 fixed 变成相对页面） -->
    <Teleport to="body">
      <Transition name="fab">
        <div v-if="fabVisible" class="material-fab-slot">
          <el-tooltip :content="sidebarOpen ? '收起我的简历素材' : '展开我的简历素材'" placement="left">
            <button
              type="button"
              class="material-fab"
              :aria-label="sidebarOpen ? '收起我的简历素材' : '展开我的简历素材'"
              @click="toggleSidebar"
            >
              <el-icon :size="18"><Files /></el-icon>
            </button>
          </el-tooltip>
        </div>
      </Transition>
    </Teleport>

    <!-- 工作台：主体岗位库（70%）+ 右侧简历素材侧边栏（30%） -->
    <div class="workspace" :class="{ 'is-open': sidebarOpen }">
      <!-- 主体：我的目标岗位 -->
      <main class="main-stage">
        <!-- 粘贴导入（默认收起，点击工具栏「粘贴导入」展开）-->
        <div v-if="jobPasteExpanded" class="feed-box">
          <div class="add-section-title">粘贴导入</div>
          <el-input
            ref="jobPasteBox"
            v-model="jobPasteContent"
            type="textarea"
            :rows="5"
            maxlength="5000"
            show-word-limit
            placeholder="粘贴完整招聘 JD / 岗位描述文本，AI 将自动标准化为「岗位名称 + 分点式岗位要求」结构…"
          />
          <div class="free-add-row">
            <button class="button button--small" :disabled="!jobPasteContent.trim() || jobPasteSaving" :class="{ 'is-loading': jobPasteSaving }" @click="submitJobPaste">提交添加</button>
            <span v-if="jobPasteSaving" class="add-hint">正在标准化岗位信息…</span>
          </div>
        </div>

        <!-- 必填信息缺失提醒 -->
        <div v-if="!initialLoading && !requiredReady" class="required-banner">
          <span class="basic-warn">缺少必填简历信息：{{ missingRequired.join('、') }}，请完善后再进行岗位匹配评估。</span>
          <button class="button button--small" @click="openSidebarFocus('required')">去补齐</button>
        </div>

        <!-- 岗位列表 -->
        <div v-if="initialLoading" class="job-skeleton" aria-hidden="true">
          <div v-for="n in 3" :key="'sk-' + n" class="job-skeleton-card">
            <div class="sk-row">
              <span class="sk sk-badge"></span>
              <span class="sk sk-title"></span>
              <span class="sk sk-meta"></span>
            </div>
            <div class="sk-row">
              <span class="sk sk-score"></span>
              <span class="sk sk-line"></span>
            </div>
            <span class="sk sk-line sk-line--long"></span>
            <span class="sk sk-line sk-line--short"></span>
          </div>
        </div>
        <div v-else-if="analyzing && !jobList.length" class="analyzing"><span class="spin"></span><span>匹配分析中…</span></div>
        <div v-else-if="!displayedJobs.length" class="empty-tip empty-tip--action">
          <div v-if="!jobList.length" class="start-guide">
            <h3 class="start-guide-title">
              {{ knowledgeList.length ? '三步开始你的岗位匹配' : '先补充简历素材，再看每日推荐' }}
            </h3>
            <ol class="start-guide-steps">
              <li>
                <span class="start-guide-num">1</span>
                <div class="start-guide-body">
                  <b>完善简历素材</b>
                  <span>右侧「我的简历素材」添加经历、技能与证书</span>
                </div>
                <button class="button button--small button--plain" @click="openSidebarFocus('free')">去补充</button>
              </li>
              <li>
                <span class="start-guide-num">2</span>
                <div class="start-guide-body">
                  <b>导入或收藏岗位</b>
                  <span v-if="jobActiveCat === 'all'">直接导入岗位，或从「最新推荐」加入我的收藏</span>
                  <span v-else>切换到「我的收藏」后可导入岗位</span>
                </div>
                <button v-if="jobActiveCat === 'all'" class="button button--small" @click="importChoiceVisible = true">导入岗位</button>
                <button v-else class="button button--small" @click="switchCat('all')">去我的收藏导入</button>
              </li>
              <li>
                <span class="start-guide-num">3</span>
                <div class="start-guide-body">
                  <b>查看 AI 匹配结果</b>
                  <span>自动打分、待补足项和最相关的简历素材</span>
                </div>
              </li>
            </ol>
          </div>
          <template v-else>
            <span>{{ stageEmptyText }}</span>
          </template>
        </div>
        <div v-if="analyzing && jobList.length" class="analyze-banner" role="status" aria-live="polite">
          <div class="analyze-banner-head">
            <span class="spin"></span>
            <span class="analyze-banner-text">正在计算岗位匹配度</span>
            <span class="analyze-banner-count">{{ analyzeDone }} / {{ analyzeTotal }}</span>
            <span class="analyze-banner-meta">{{ analyzePercent }}% · {{ analyzeTimeText }}</span>
          </div>
          <div class="analyze-banner-track"><span class="analyze-banner-fill" :style="{ width: analyzePercent + '%' }"></span></div>
        </div>
        <div v-if="!initialLoading && !knowledgeList.length && displayedJobs.length" class="material-hint" role="status">
          <span>你的简历素材还是空的，下面的匹配度会一直是 0</span>
          <button class="button button--small button--plain" @click="openSidebarFocus('free')">去补充素材</button>
        </div>
        <div v-if="recommendStale" class="analyze-banner" role="status" aria-live="polite">
          <div class="analyze-banner-head">
            <span class="spin"></span>
            <span class="analyze-banner-text">今日岗位更新中，正在拉取最新岗位</span>
            <span class="analyze-banner-meta">当前展示 {{ recommendVersion.date }} 的数据</span>
          </div>
        </div>
        <div v-if="displayedJobs.length" :key="jobActiveCat" class="job-list">
          <div
            v-for="(job, jobIndex) in displayedJobs"
            :key="job.id"
            v-reveal="Math.min(jobIndex, 8) * 24"
            class="job-card"
            :data-job-id="job.id"
            :class="{
              'is-open': expandedJobs[job.id],
              'is-flash': jobFlashIds.has(job.id),
              'is-score-changed': scoreChangedIds.has(job.id),
              'is-hotmatch': isHighMatch(job),
              'is-recommendation': job.isRecommendation
            }"
          >
            <!-- 左65%：基础信息 + 大分 + 待补足 + 底部操作 -->
            <div class="job-card-left" @click="toggleJobDetail(job)">
              <div class="job-base-row">
                <span v-if="showRanking" class="job-rank" :class="'job-rank--' + (jobIndex + 1)">{{ jobIndex + 1 }}</span>
                <label v-if="jobActiveCat === 'all'" class="job-select" title="勾选" @click.stop>
                  <input type="checkbox" :checked="isJobSelected(job)" @change="toggleJobSelect(job)" />
                </label>
                <span class="job-name" title="查看岗位详情" @click.stop="openJobDetail(job)">
                  <template v-for="(segment, index) in jobNameSegments(job.jobName)" :key="index">
                    <mark v-if="segment.hit" class="job-name-hit">{{ segment.text }}</mark>
                    <template v-else>{{ segment.text }}</template>
                  </template>
                </span>
                <span v-if="job.isRecommendation" class="job-rec-tag">今日推荐</span>
                <span class="job-company">{{ job.companyName || '未填公司' }}</span>
                <span v-if="job.sourcePlatform" class="job-source-tag" title="岗位来源">{{ job.sourcePlatform }}</span>
                <span v-if="job.location" class="job-loc">{{ job.location }}</span>
                <span v-if="job.salary" class="job-salary">{{ job.salary }}</span>
              </div>

              <div class="job-score-wrap">
                <div class="job-score-big" :class="[scoreGradeByValue(animatedScoreOf(job)), { 'is-recalc': isRecalcPending(job), 'is-score-pulse': scorePulseIds.has(job.id) }]">
                  <span v-if="isRecalcPending(job)" class="recalc-label"><span class="spin"></span>重算中</span>
                  <span v-else-if="job.match" class="score-num-text">{{ Math.round(animatedScoreOf(job)) }}</span>
                  <span v-else-if="analyzing" class="score-pending"><span class="spin"></span></span>
                  <template v-else>—</template>
                  <span v-if="job.match && !isRecalcPending(job) && scorePopVisible[job.id] && scoreGainMap[job.id]" class="score-pop-tag">
                    +{{ scoreGainMap[job.id].delta }}
                  </span>
                </div>
                <el-tooltip v-if="isHighMatch(job)" content="匹配度 90 分以上，建议优先投递" placement="top">
                  <span class="high-match-badge">高匹配</span>
                </el-tooltip>
                <button
                  v-if="!job.isRecommendation"
                  type="button"
                  class="apply-elf"
                  :class="{ 'is-popping': elfPopId === job.id, 'is-intro': elfIntro }"
                  aria-label="打开求职小精灵"
                  @click.stop="openAssistantFromElf(job)"
                >
                  <span class="elf-icon" aria-hidden="true">
                    <svg class="elf-svg" viewBox="0 0 24 24">
                      <path d="M12 2.4 15.2 8.2H8.8Z" />
                      <circle cx="12" cy="12.3" r="4.3" />
                      <path class="elf-wing" d="M7.7 11.1Q4.7 8.7 5.2 12.1 5.7 15.5 7.9 13.5Z" />
                      <path class="elf-wing" d="M16.3 11.1q3-2.4 2.5 1-.5 3.4-2.7 1.4Z" />
                      <circle class="elf-eye" cx="10.6" cy="11.7" r=".65" />
                      <circle class="elf-eye" cx="13.4" cy="11.7" r=".65" />
                      <path class="elf-mouth" d="M10.5 13.6q1.5 1.2 3 0" />
                      <path class="elf-twinkle" d="M19 2.2 20.1 4.7 22.6 5.8 20.1 6.9 19 9.4 17.9 6.9 15.4 5.8 17.9 4.7Z" />
                    </svg>
                    <span class="elf-sparks"></span>
                  </span>
                  <span class="elf-label">求职小精灵</span>
                </button>
                <div v-if="job.match && !isRecalcPending(job) && hasDims(job.match)" class="dim-bars dim-bars--inline">
                  <div v-for="d in dimItems(job.match)" :key="d.label" class="dim-bar-mini">
                    <span class="dim-bar-label">{{ d.label }}</span>
                    <div class="dim-bar-track"><div class="dim-bar-fill" :class="dimFillClass(d.value)" :style="{ width: clamp(d.value) + '%' }"></div></div>
                    <span class="dim-bar-val">{{ dash(d.value) }}</span>
                  </div>
                </div>
              </div>

              <div class="job-summary-row" title="岗位要求中尚未覆盖的项，展开可查看补齐建议">
                <span v-if="isRecalcPending(job)" class="miss-pending">重算中…</span>
                <span v-else-if="isJobEvaluating(job)" class="miss-pending">评估中…</span>
                <span v-else-if="!job.match && analyzing" class="miss-pending">评估中…</span>
                <span v-else>待补足 <b class="miss-count">{{ matchMissCount(job) }}</b> 项核心要求</span>
                <span class="job-caret">{{ expandedJobs[job.id] ? '▾' : '▸' }}</span>
              </div>

              <div class="job-card-actions">
                <template v-if="job.isRecommendation">
                  <button
                    class="button button--small recommend-add-btn"
                    :class="{ 'is-loading': addingRecommendId === job.recommendId, 'is-collected': job.isInTarget }"
                    :disabled="job.isInTarget || addingRecommendId === job.recommendId"
                    :title="job.isInTarget ? '该岗位已在「我的收藏」中' : ''"
                    @click.stop="addRecommendationToTarget(job)"
                  >{{ job.isInTarget ? '已加入我的收藏' : addingRecommendId === job.recommendId ? '加入中…' : '加入我的收藏' }}</button>
                  <button class="button button--plain button--small" @click.stop="openExternalLink(job.jobLink)">查看原岗位</button>
                </template>
                <template v-else>
                  <el-tooltip :content="isItJob(job) ? '用该岗位 JD 生成 AI 笔试卷（大厂真题优先）' : 'AI 笔试仅支持 IT 技术岗位'" placement="top">
                    <button
                      class="button button--plain button--small"
                      :disabled="!isItJob(job)"
                      :title="isItJob(job) ? '用该岗位 JD 生成 AI 笔试卷' : 'AI 笔试仅支持 IT 技术岗位'"
                      @click.stop="startAiExam(job)"
                    >AI 笔试</button>
                  </el-tooltip>
                  <el-tooltip content="进入该岗位的 AI 模拟面试" placement="top">
                    <button class="button button--plain button--small" @click.stop="startAiInterview(job)">AI 面试</button>
                  </el-tooltip>
                  <button
                    class="button button--plain button--small apply-btn"
                    :class="{ 'apply-btn--hot': isHighMatch(job) }"
                    :title="isHighMatch(job) ? '高匹配，建议现在就投！' : '投简历'"
                    :disabled="applyingJobId === job.id"
                    @click.stop="openApplyFromJob(job)"
                  >
                    <span v-if="applyingJobId === job.id" class="spin"></span>
                    {{ applyingJobId === job.id ? '识别中...' : '投简历' }}
                  </button>
                </template>
              </div>
            </div>

            <!-- 右35%：匹配素材 Top -->
            <div class="job-card-right" @click.stop>
              <div class="match-panel-head">
                <el-tooltip content="与该岗位最相关的简历素材，可直接引用" placement="top">
                  <span class="match-panel-title">匹配素材 Top</span>
                </el-tooltip>
                <span class="match-panel-count">{{ matchHitCount(job) }}</span>
              </div>
              <template v-if="matchHighlights(job).length">
                <div
                  v-for="(d, di) in (expandedHighlights[job.id] ? matchHighlights(job) : matchHighlights(job).slice(0, TOP_MATCH_SHOWN))"
                  :key="'h-' + di"
                  class="match-chip"
                  :class="{ 'is-open': openHighlights[job.id] === d.knowledgeId, 'is-hover-source': hoverSourceId != null && d.knowledgeId === hoverSourceId }"
                  @click.stop="toggleHighlight(job.id, d)"
                  @mouseenter.stop="hoverSourceEnter({ id: d.knowledgeId })"
                  @mouseleave.stop="hoverSourceLeave"
                >
                  <div class="match-chip-head">
                    <span class="match-chip-tag">{{ d.source || '素材' }}</span>
                    <span class="match-chip-text">{{ d.text }}</span>
                    <span class="match-chip-arrow">▾</span>
                  </div>
                  <div v-if="openHighlights[job.id] === d.knowledgeId" class="match-chip-detail">
                    <p class="match-chip-detail-text">{{ d.text }}</p>
                    <span v-if="d.requirement" class="match-chip-evidence">覆盖「{{ d.requirement }}」 · +{{ d.quota }}分</span>
                  </div>
                </div>
                <button
                  v-if="matchHighlights(job).length > TOP_MATCH_SHOWN"
                  class="match-toggle-more"
                  @click.stop="toggleHighlights(job.id)"
                >
                  {{ expandedHighlights[job.id] ? '收起' : '查看全部匹配项' }}
                </button>
              </template>
              <div v-else class="match-panel-empty">
                <span v-if="analyzing">评估中…</span>
                <template v-else>
                  <span>我的简历素材中暂无与该岗位匹配的条目</span>
                  <span v-if="job.techStack" class="match-panel-empty-hint">岗位要求：{{ job.techStack }}</span>
                  <button class="text-link" @click.stop="openSidebarFocus('free')">去补充素材</button>
                </template>
              </div>
            </div>

            <!-- 匹配详情展开面板（仅保留待补足 + 总结 + AI 面试）-->
            <div v-if="expandedJobs[job.id]" class="job-detail">
              <div v-if="isRecalcPending(job)" class="empty-tip"><span class="spin"></span> 正在按最新素材重算该岗位…</div>
              <div v-else-if="!job.match" class="empty-tip">暂无匹配结果，匹配计算中…</div>
              <template v-else>
                <div v-if="job.match.hardRequirementMisses && job.match.hardRequirementMisses.length" class="hard-misses">
                  <div class="hard-miss-title">硬性要求校验 · 未达标</div>
                  <div v-for="(h, hi) in job.match.hardRequirementMisses" :key="'h-' + hi" class="hard-miss">{{ h }}</div>
                </div>

                <div class="match-insight">
                  <div class="insight-overview">
                    <el-progress
                      type="circle"
                      :percentage="matchScorePercent(job)"
                      :width="72"
                      :stroke-width="6"
                      :color="coverageColor(job)"
                    />
                    <div class="insight-overview-text">
                      <div class="insight-overview-title">
                        <span>综合匹配</span>
                        <span class="insight-overview-count">{{ matchCoveredCount(job) }} / {{ matchTotalCount(job) }} 项已覆盖</span>
                      </div>
                      <p class="insight-overview-sub">{{ matchOverviewSub(job) }}</p>
                    </div>
                  </div>

                  <div class="insight-grid">
                    <section class="insight-card insight-card--hit">
                      <header class="insight-card-head">
                        <CheckCircle2 :size="15" aria-hidden="true" />
                        <span>匹配优势</span>
                        <span class="insight-card-count">{{ matchAdvantages(job).length }}</span>
                      </header>
                      <ul v-if="matchAdvantages(job).length" class="insight-list">
                        <li
                          v-for="(item, mi) in matchAdvantages(job)"
                          :key="'insight-hit-' + mi"
                          class="insight-item"
                        >
                          <span class="insight-dot insight-dot--hit" aria-hidden="true"></span>
                          <div class="insight-item-body">
                            <p class="insight-item-text">
                              <template v-for="(seg, si) in highlightSkill(item.requirement, job)" :key="'insight-hk-' + mi + '-' + si">
                                <mark v-if="seg.hit" class="insight-kw">{{ seg.text }}</mark><template v-else>{{ seg.text }}</template>
                              </template>
                            </p>
                            <p v-if="item.evidence" class="insight-item-evidence">素材：{{ item.evidence }}</p>
                          </div>
                        </li>
                      </ul>
                      <p v-else class="insight-empty">暂无明显匹配项</p>
                    </section>

                    <section class="insight-card insight-card--gap">
                      <header class="insight-card-head" :class="{ 'is-success': !matchMisses(job).length }">
                        <CheckCircle2 v-if="!matchMisses(job).length" :size="15" aria-hidden="true" />
                        <AlertCircle v-else :size="15" aria-hidden="true" />
                        <span>待补足项</span>
                        <span class="insight-card-count">{{ matchMisses(job).length }}</span>
                      </header>
                      <ul v-if="matchMisses(job).length" class="insight-list">
                        <li
                          v-for="(d, di) in matchMisses(job)"
                          :key="'insight-gap-' + (d.requirement || d.text || '').slice(0, 30) + '-' + di"
                          class="insight-item"
                        >
                          <span class="insight-dot insight-dot--gap" aria-hidden="true"></span>
                          <div class="insight-item-body">
                            <p class="insight-item-text">
                              <template v-for="(seg, si) in highlightSkill(d.text, job)" :key="'insight-gk-' + di + '-' + si">
                                <mark v-if="seg.hit" class="insight-kw insight-kw--gap">{{ seg.text }}</mark><template v-else>{{ seg.text }}</template>
                              </template>
                            </p>
                            <div class="insight-item-actions">
                              <span class="gain-pill">{{ estimateGain(d, job) }}</span>
                              <button class="button button--small" @click.stop="quickFill(d, job)">快速补充</button>
                            </div>
                          </div>
                        </li>
                      </ul>
                      <div v-else class="insight-success">
                        <CheckCircle2 :size="15" aria-hidden="true" />
                        <span>{{ matchTotalCount(job) ? '暂无缺口，当前素材已覆盖全部岗位要求' : '暂无可评估的岗位要求' }}</span>
                      </div>
                    </section>
                  </div>

                  <div v-if="job.match.optimization" class="insight-tip">
                    <Lightbulb :size="16" aria-hidden="true" />
                    <p><b>优化建议</b>{{ job.match.optimization }}</p>
                    <button v-if="matchMisses(job).length" class="text-link" @click.stop="openSidebarFocus('free')">去补充素材</button>
                  </div>
                </div>
                <div v-if="job.match.coreRequirements || job.match.techStack" class="rank-tags job-detail-tags">
                  <span v-for="t in job.match.coreRequirements" :key="'req-' + t" class="tag tag-req">{{ t }}</span>
                  <span v-for="t in job.match.techStack" :key="'tech-' + t" class="tag tag-tech">{{ t }}</span>
                </div>
                <!-- 展开详情保留匹配洞察与待补足项，AI 面试入口已移到卡片操作区 -->
              </template>
            </div>
          </div>
        </div>
      </main>

      <!-- 右侧：我的简历素材侧边栏（30%）-->
      <aside class="material-drawer">
        <div class="drawer-head">
          <h3 class="panel-title">我的简历素材</h3>
          <div class="panel-actions">
            <button class="button button--small" @click="openAddMaterial()">新增素材</button>
          </div>
        </div>

        <div class="module-hint">素材新增/编辑/删除后，岗位匹配分自动重算；点击岗位「匹配亮点」可直达对应素材。</div>

        <!-- 必填信息区（缺项时显示） -->
        <div v-if="!initialLoading && !requiredReady" id="side-required" class="side-section">
          <div class="basic-warn">缺少必填简历信息：{{ missingRequired.join('、') }}</div>
          <div class="basic-grid basic-grid--side">
            <div class="basic-field"><label class="req-label">姓名</label><el-input v-model="basicForm.name" placeholder="请输入姓名" maxlength="50" /></div>
            <div class="basic-field"><label class="req-label">学历</label><el-input v-model="basicForm.education" placeholder="例：本科 / 硕士" maxlength="50" /></div>
            <div class="basic-field"><label class="req-label">毕业院校</label><el-input v-model="basicForm.school" placeholder="请输入毕业院校" maxlength="100" /></div>
            <div class="basic-field"><label class="req-label">专业</label><el-input v-model="basicForm.major" placeholder="请输入专业" maxlength="100" /></div>
          </div>
          <div class="add-actions"><button class="button button--small" :disabled="!basicComplete || basicSaving" :class="{ 'is-loading': basicSaving }" @click="submitBasicInfo">提交添加</button></div>
        </div>

        <!-- 素材分类折叠列表 -->
        <div class="side-section side-list">
          <div class="side-filter">
            <el-input v-model="materialKeyword" clearable placeholder="搜索素材内容" class="filter-search" />
            <el-select v-model="materialType" placeholder="全部类型" class="filter-select">
              <el-option label="全部类型" value="" />
              <el-option v-for="t in MATERIAL_TYPES" :key="t" :label="t" :value="t" />
            </el-select>
          </div>
          <p class="side-count">{{ initialLoading ? '加载中…' : `共 ${filteredMaterialCount} 条素材` }}</p>
          <el-collapse v-model="materialActiveNames" class="material-collapse">
            <el-collapse-item v-for="g in materialsByType" :key="g.type" :name="g.type">
              <template #title>
                <span class="mtype-title">{{ g.type }}</span>
                <span class="mtype-count">{{ g.items.length }}</span>
              </template>
              <div v-for="item in g.items" :key="item.id" :id="'mat-item-' + item.id" class="material-item" :class="{ 'is-flash': flashKnowledgeId === item.id }" @mouseenter="hoverSourceEnter(item)" @mouseleave="hoverSourceLeave">
                <div class="material-item-text" @click="focusMaterial(item.id)">{{ item.content || '(空内容)' }}</div>
                <div v-if="item.createTime" class="material-item-meta">{{ shortTime(item.createTime) }}</div>
                <div class="material-item-actions">
                  <button class="button button--small button--plain" @click="focusMaterial(item.id)">查看</button>
                  <button class="button button--small button--plain" @click="openEditMaterial(item)">编辑</button>
                  <button class="button button--small button--plain button--danger" @click="removeMaterial(item)">删除</button>
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>
          <div v-if="!initialLoading && !knowledgeList.length" class="empty-tip empty-tip--action">
            <span>暂无素材，先添加一条简历素材</span>
            <button class="button button--small" @click="openSidebarFocus('free')">新增素材</button>
          </div>
        </div>
      </aside>
    </div>

    <!-- 新增素材弹窗：文件导入 / 自由文本录入 -->
    <el-dialog
      v-model="addMaterialVisible"
      title="新增素材"
      width="560px"
      align-center
      class="lib-dialog add-material-dialog"
      :class="{ 'is-minimized': importMinimized }"
      :close-on-click-modal="false"
      :modal="!importMinimized"
    >
      <template #header>
        <div class="dialog-header">
          <span>新增素材</span>
          <button
            v-if="importing"
            class="minimize-btn"
            :title="importMinimized ? '展开' : '最小化'"
            @click="importMinimized = !importMinimized"
          >{{ importMinimized ? '▢' : '—' }}</button>
        </div>
      </template>
      <template v-if="!importMinimized">
        <el-tabs v-model="addMaterialTab" class="add-material-tabs">
          <el-tab-pane label="文件导入" name="file">
            <div class="import-tip">支持PDF、DOC、DOCX格式，单文件不超过20MB；推荐上传文本型PDF/Word，识别更快更准；扫描图片型将自动OCR识别</div>
            <el-upload
              drag
              :show-file-list="false"
              accept=".pdf,.doc,.docx"
              :http-request="doImport"
              :before-upload="beforeImport"
              :disabled="importing"
            >
              <div class="upload-zone">
                <span class="upload-zone-title">将文件拖拽到此处，或<em>点击上传</em></span>
                <span class="upload-zone-sub">仅支持 PDF / DOC / DOCX，≤ 20MB</span>
              </div>
            </el-upload>
          </el-tab-pane>
          <el-tab-pane label="自由文本录入" name="text">
            <el-input v-model="freeAddContent" type="textarea" :rows="6" maxlength="3000" show-word-limit placeholder="自由输入任意内容，AI 将自动归类并拆分为多条单属性记录" />
            <div class="free-add-row"><button class="button" :disabled="!freeAddContent.trim() || freeSaving" :class="{ 'is-loading': freeSaving }" @click="submitFreeAdd">提交添加</button></div>
          </el-tab-pane>
        </el-tabs>
      </template>
      <div v-if="importing" class="import-progress">
        <div class="phase-row">
          <span class="phase-text">{{ IMPORT_PHASE_TEXT[importPhase] }}</span>
          <span class="phase-percent">{{ Math.round(importProgress) }}%</span>
        </div>
        <div class="import-bar"><span class="import-bar-fill" :style="{ width: importProgress + '%' }"></span></div>
        <span class="import-hint-text">{{ importHint }}</span>
      </div>
    </el-dialog>

    <!-- 预览比对弹窗（导入 / 自由添加 共用） -->
    <el-dialog v-model="previewVisible" :title="pendingSplit ? pendingSplit.title : '预览拆解结果'" width="820px" align-center class="lib-dialog">
      <div class="preview-cols">
        <div class="preview-col">
          <div class="preview-col-title">原文内容</div>
          <div class="preview-original">{{ pendingSplit ? pendingSplit.originalText : '' }}</div>
        </div>
        <div class="preview-col">
          <div class="preview-col-title">AI 拆解结果（{{ (pendingSplit && pendingSplit.records) ? pendingSplit.records.length : 0 }} 条）</div>
          <div v-if="previewGroups.length" class="material-list material-list--scroll">
            <div v-for="(grp, gi) in previewGroups" :key="gi" class="preview-group">
              <span class="preview-group-label">{{ grp.type }}</span>
              <span class="type-divider" aria-hidden="true"></span>
              <div class="preview-group-body">
                <p v-for="(r, ri) in grp.items" :key="ri" class="preview-group-item"><span class="item-no">{{ ri + 1 }}</span>{{ r.content }}</p>
              </div>
            </div>
          </div>
          <div v-else class="empty-tip">未识别出可入库记录</div>
        </div>
      </div>
      <template #footer>
        <button class="button button--plain" @click="previewVisible = false">取消</button>
        <button class="button" :disabled="previewSaving" :class="{ 'is-loading': previewSaving }" @click="confirmPreview">确认添加</button>
      </template>
    </el-dialog>

    <!-- 疑似重复弹窗 -->
    <el-dialog v-model="duplicateDialogVisible" title="检测到疑似重复记录" width="660px" align-center class="lib-dialog">
      <div class="dup-hint">以下新增记录与已有记录整体内容重合度达 90% 及以上，请逐一选择处理方式：</div>
      <div class="dup-list">
        <div v-for="(d, i) in duplicatePairs" :key="i" class="dup-item">
          <div class="dup-similarity" v-if="d.similarity != null">内容重合度：{{ d.similarity }}%</div>
          <div class="dup-cols">
            <div class="dup-col">
              <div class="dup-col-title">新增记录</div>
              <span class="material-type">{{ (pendingRecords[d.recordIndex] && pendingRecords[d.recordIndex].type) || '其他' }}</span>
              <p class="dup-content">{{ pendingRecords[d.recordIndex] ? pendingRecords[d.recordIndex].content : '' }}</p>
            </div>
            <div class="dup-col">
              <div class="dup-col-title">已有记录</div>
              <span class="material-type">{{ d.existing ? (d.existing.type || '其他') : '其他' }}</span>
              <p class="dup-content">{{ d.existing ? d.existing.content : '' }}</p>
            </div>
          </div>
          <div class="dup-options">
            <label class="dup-opt"><input type="radio" value="new" v-model="d.decision" /> 保留新记录</label>
            <label class="dup-opt"><input type="radio" value="old" v-model="d.decision" /> 保留旧记录</label>
            <label class="dup-opt"><input type="radio" value="both" v-model="d.decision" /> 全部保留</label>
          </div>
        </div>
      </div>
      <template #footer>
        <button class="button button--plain" @click="cancelDuplicates">取消</button>
        <button class="button" :disabled="dupSaving" :class="{ 'is-loading': dupSaving }" @click="confirmDuplicates">确认入库</button>
      </template>
    </el-dialog>

    <!-- 我的目标岗位：岗位管理弹窗（所有记录收纳于此） -->
    <!-- 导入方式选择 -->
    <el-dialog v-model="importChoiceVisible" title="导入岗位" width="420px" align-center class="lib-dialog">
      <div class="import-choice">
        <button class="import-choice-item" @click="chooseImport('file')">
          <el-icon :size="20"><Document /></el-icon>
          <span class="import-choice-main">
            <span class="import-choice-title">文件导入</span>
            <span class="import-choice-desc">上传 PDF / Word 岗位文件，自动识别岗位信息</span>
          </span>
        </button>
        <button class="import-choice-item" @click="chooseImport('link')">
          <el-icon :size="20"><Link /></el-icon>
          <span class="import-choice-main">
            <span class="import-choice-title">链接导入</span>
            <span class="import-choice-desc">粘贴招聘页面链接，自动读取岗位信息</span>
          </span>
        </button>
      </div>
    </el-dialog>

    <!-- 导入岗位弹窗 -->
    <el-dialog
      v-model="jobImportVisible"
      title="导入岗位"
      width="480px"
      align-center
      class="lib-dialog"
      :class="{ 'is-minimized': jobImportMinimized }"
      :close-on-click-modal="false"
      :modal="!jobImportMinimized"
    >
      <template #header>
        <div class="dialog-header">
          <span>导入岗位</span>
          <button
            v-if="jobImporting"
            class="minimize-btn"
            :title="jobImportMinimized ? '展开' : '最小化'"
            @click="jobImportMinimized = !jobImportMinimized"
          >{{ jobImportMinimized ? '▢' : '—' }}</button>
        </div>
      </template>
      <template v-if="!jobImportMinimized">
        <div class="import-tip">支持PDF、DOC、DOCX格式，单文件不超过20MB；推荐上传文本型PDF/Word，识别更快更准；扫描图片型将自动OCR识别</div>
        <el-upload
          drag
          :show-file-list="false"
          accept=".pdf,.doc,.docx"
          :http-request="doJobImport"
          :before-upload="beforeJobImport"
          :disabled="jobImporting"
        >
          <div class="upload-zone">
            <span class="upload-zone-title">将文件拖拽到此处，或<em>点击上传</em></span>
            <span class="upload-zone-sub">仅支持 PDF / DOC / DOCX，≤ 20MB</span>
          </div>
        </el-upload>
      </template>
      <div v-if="jobImporting" class="import-progress">
        <div class="phase-row">
          <span class="phase-text">{{ IMPORT_PHASE_TEXT[jobImportPhase] }}</span>
          <span class="phase-percent">{{ Math.round(jobImportProgress) }}%</span>
        </div>
        <div class="import-bar"><span class="import-bar-fill" :style="{ width: jobImportProgress + '%' }"></span></div>
        <span class="import-hint-text">{{ jobImportHint }}</span>
      </div>
    </el-dialog>

    <!-- 链接导入弹窗 -->
    <el-dialog v-model="jobLinkVisible" title="链接导入" width="560px" align-center class="lib-dialog">
      <div class="import-tip">粘贴岗位详情页链接，系统将自动抓取页面正文并标准化为岗位信息，支持企业官网与主流招聘平台</div>
      <el-input
        v-model="jobLinkUrl"
        type="textarea"
        :rows="3"
        placeholder="请粘贴岗位详情页 URL（如 https://...）"
      />
      <template #footer>
        <button class="button button--plain" @click="jobLinkVisible = false">取消</button>
        <button
          class="button"
          :disabled="!jobLinkUrl.trim() || jobLinkSaving"
          :class="{ 'is-loading': jobLinkSaving }"
          @click="submitJobLink"
        >{{ jobLinkSaving ? '正在读取并标准化…' : '读取并标准化' }}</button>
      </template>
    </el-dialog>

    <!-- 岗位信息详情弹窗（点击岗位名称查看导入后格式化信息） -->
    <el-dialog v-model="jobDetailVisible" title="岗位信息" width="680px" align-center class="lib-dialog">
      <template v-if="jobDetailTarget">
        <div class="job-detail-fields">
          <div class="job-detail-field"><span class="f-label">岗位名称</span><span class="f-value">{{ jobDetailTarget.jobName || '—' }}</span></div>
          <div class="job-detail-field"><span class="f-label">招聘单位</span><span class="f-value">{{ jobDetailTarget.companyName || '未填公司' }}</span></div>
          <div class="job-detail-field"><span class="f-label">招聘邮箱</span><span class="f-value">{{ jobDetailTarget.email || '未填写' }}</span></div>
          <div class="job-detail-field"><span class="f-label">工作地点</span><span class="f-value">{{ jobDetailTarget.location || '未注明' }}</span></div>
          <div class="job-detail-field"><span class="f-label">薪资范围</span><span class="f-value">{{ jobDetailTarget.salary || '面议' }}</span></div>
          <div class="job-detail-field"><span class="f-label">岗位类型</span><span class="f-value">{{ jobDetailTarget.jobType && jobDetailTarget.jobType !== '其他' ? jobDetailTarget.jobType : '—' }}</span></div>
          <div class="job-detail-field"><span class="f-label">技术栈</span><span class="f-value">{{ jobDetailTarget.techStack || '—' }}</span></div>
        </div>
        <div class="job-detail-req">
          <div class="job-detail-req-title">岗位要求</div>
          <pre class="job-detail-req-text">{{ jobDetailTarget.jobRequirement || '暂无' }}</pre>
        </div>
      </template>
      <template #footer>
        <button class="button button--plain" @click="jobDetailVisible = false">关闭</button>
      </template>
    </el-dialog>

    <!-- 岗位导入预览确认弹窗（导入 / 粘贴 / 链接 共用） -->
    <el-dialog
      v-model="jobPreviewVisible"
      :title="pendingJob && pendingJob.fileName ? pendingJob.fileName : '岗位导入预览'"
      width="820px"
      align-center
      class="lib-dialog"
    >
      <div class="preview-cols">
        <div class="preview-col">
          <div class="preview-col-title">原始文本</div>
          <div class="preview-original">{{ pendingJob ? pendingJob.originalText : '' }}</div>
        </div>
        <div class="preview-col">
          <div class="preview-col-title">AI 标准化结果</div>
          <div v-if="pendingJob" class="extract-box">
            <div class="extract-row">
              <span class="extract-label">岗位名称</span>
              <span class="extract-name">{{ pendingJob.jobName || '未识别' }}</span>
            </div>
            <div class="extract-row">
              <span class="extract-label">岗位类型</span>
              <span class="tag tag-type">{{ pendingJob.jobType && pendingJob.jobType !== '其他' ? pendingJob.jobType : '待识别' }}</span>
            </div>
            <div v-if="pendingJob.techStack" class="extract-row">
              <span class="extract-label">核心技术栈</span>
              <span class="extract-tech">{{ pendingJob.techStack }}</span>
            </div>
            <div class="extract-field">
              <span class="extract-label">岗位要求</span>
              <div class="preview-original extract-require">{{ pendingJob.jobRequirement }}</div>
            </div>
          </div>
        </div>
      </div>
      <div v-if="pendingJob" class="preview-extra">
        <div class="edit-field">
          <label class="edit-label">招聘单位</label>
          <el-input v-model="pendingJob.companyName" maxlength="100" placeholder="系统将智能识别原文公司名称，可修改" />
        </div>
        <div class="edit-field">
          <label class="edit-label">招聘邮箱</label>
          <el-input v-model="pendingJob.email" maxlength="100" placeholder="系统将智能识别原文招聘邮箱，可修改" />
        </div>
        <div class="edit-field">
          <label class="edit-label">工作地点</label>
          <el-input v-model="pendingJob.location" maxlength="100" placeholder="系统将智能识别工作地点，可修改" />
        </div>
        <div class="edit-field">
          <label class="edit-label">薪资范围</label>
          <el-input v-model="pendingJob.salary" maxlength="100" placeholder="系统将智能识别薪资范围，可修改" />
        </div>
        <p class="preview-extra-tip">请至少填写招聘单位或招聘邮箱，方可添加入库</p>
      </div>
      <template #footer>
        <button class="button button--plain" @click="jobPreviewVisible = false">取消</button>
        <button
          class="button"
          :disabled="jobPreviewSaving || !(pendingJob && (pendingJob.companyName || '').trim()) && !(pendingJob && (pendingJob.email || '').trim())"
          :class="{ 'is-loading': jobPreviewSaving }"
          @click="confirmJobPreview"
        >确认添加</button>
      </template>
    </el-dialog>

    <!-- 生成定制简历弹窗 -->
    <el-dialog
      v-model="resumeVisible"
      :title="`针对【${currentJobName}】的定制简历`"
      width="1100px"
      align-center
      class="resume-dialog"
      :close-on-click-modal="true"
      :close-on-press-escape="true"
    >
      <GenerationProgress
        v-if="resumeLoading"
        :active="resumeLoading"
        :stage="resumeStage"
        :estimate="30"
        label="正在生成定制简历"
        kind="tailor"
      />

      <div v-else-if="resumeError" class="resume-error">{{ resumeError }}</div>

      <template v-else-if="resume">
        <div class="resume-workspace">
          <!-- 左：编辑面板 -->
          <div class="resume-editor">
            <div class="resume-editor-block">
              <div class="resume-editor-label">切换模板</div>
              <div class="resume-tpl-list">
                <button
                  v-for="t in resumeTemplates"
                  :key="t.id"
                  class="resume-tpl-card"
                  :class="{ 'is-active': t.id === activeTemplateId }"
                  @click="selectResumeTemplate(t)"
                >
                  <span class="resume-tpl-thumb" :class="'thumb-' + (t.layout || 'single')" :style="{ '--tpl-primary': t.primary, '--tpl-secondary': t.secondary }">
                    <i class="thumb-photo"></i>
                    <i class="thumb-bar"></i>
                    <i class="thumb-line thumb-l1"></i>
                    <i class="thumb-line thumb-l2"></i>
                    <i class="thumb-line thumb-l3"></i>
                  </span>
                  <span class="resume-tpl-name">{{ t.name }}</span>
                </button>
              </div>
            </div>

            <div class="resume-editor-block">
              <div class="resume-editor-label">证件照（一寸 3:4）</div>
              <div class="resume-photo-edit">
                <div v-if="resume.photo" class="resume-photo-thumb">
                  <img :src="resume.photo" alt="证件照" />
                  <button class="resume-photo-del" title="删除证件照" @click="removePhoto">×</button>
                </div>
                <button v-else class="resume-photo-upload" :disabled="photoUploading" @click="$refs.photoInput.click()">
                  {{ photoUploading ? '处理中…' : '+ 上传证件照' }}
                </button>
                <span class="resume-photo-tip">自动裁剪缩放，可替换/删除</span>
              </div>
              <input ref="photoInput" type="file" accept="image/*" style="display:none" @change="onPhotoChange" />
            </div>

            <div class="resume-editor-block">
              <div class="resume-editor-label">基础信息</div>
              <input v-model="resume.name" class="resume-field-input" placeholder="姓名" />
              <textarea v-model="resume.basicInfo" class="resume-field-input resume-field-textarea" rows="2" placeholder="联系电话 / 邮箱 / 城市 / 求职意向"></textarea>
            </div>

            <div class="resume-editor-block">
              <div class="resume-editor-label">自我评价</div>
              <textarea v-model="resume.selfEvaluation" class="resume-field-input resume-field-textarea" rows="3" placeholder="简要自我评价"></textarea>
            </div>

            <div class="resume-editor-block">
              <div class="resume-editor-label">核心技能</div>
              <div v-for="(s, i) in resume.skills" :key="'s-' + i" class="resume-edit-row">
                <input v-model="s.name" class="resume-field-input resume-edit-input" placeholder="技能名称" />
                <button class="resume-mini-btn" title="上移" @click="moveItem(resume.skills, i, -1)">↑</button>
                <button class="resume-mini-btn" title="下移" @click="moveItem(resume.skills, i, 1)">↓</button>
                <button class="resume-mini-btn resume-mini-btn--danger" title="删除" @click="removeSkillItem(i)">×</button>
              </div>
              <button class="resume-add-btn" @click="addSkillItem">+ 添加技能</button>
            </div>

            <div class="resume-editor-block">
              <div class="resume-editor-label">实习/项目经历</div>
              <div v-for="(e, i) in resume.experiences" :key="'exp-' + i" class="resume-edit-row">
                <input v-model="resume.experiences[i]" class="resume-field-input resume-edit-input" placeholder="经历描述" />
                <button class="resume-mini-btn" title="上移" @click="moveItem(resume.experiences, i, -1)">↑</button>
                <button class="resume-mini-btn" title="下移" @click="moveItem(resume.experiences, i, 1)">↓</button>
                <button class="resume-mini-btn resume-mini-btn--danger" title="删除" @click="removeListItem(resume.experiences, i)">×</button>
              </div>
              <button class="resume-add-btn" @click="addListItem(resume.experiences)">+ 添加经历</button>
            </div>

            <div class="resume-editor-block">
              <div class="resume-editor-label">项目亮点</div>
              <div v-for="(p, i) in resume.projectHighlights" :key="'ph-' + i" class="resume-edit-row">
                <input v-model="resume.projectHighlights[i]" class="resume-field-input resume-edit-input" placeholder="项目亮点" />
                <button class="resume-mini-btn" title="上移" @click="moveItem(resume.projectHighlights, i, -1)">↑</button>
                <button class="resume-mini-btn" title="下移" @click="moveItem(resume.projectHighlights, i, 1)">↓</button>
                <button class="resume-mini-btn resume-mini-btn--danger" title="删除" @click="removeListItem(resume.projectHighlights, i)">×</button>
              </div>
              <button class="resume-add-btn" @click="addListItem(resume.projectHighlights)">+ 添加亮点</button>
            </div>

            <div class="resume-editor-block">
              <div class="resume-editor-label">教育背景</div>
              <div v-for="(e, i) in resume.education" :key="'edu-' + i" class="resume-edit-row">
                <input v-model="resume.education[i]" class="resume-field-input resume-edit-input" placeholder="教育经历" />
                <button class="resume-mini-btn" title="上移" @click="moveItem(resume.education, i, -1)">↑</button>
                <button class="resume-mini-btn" title="下移" @click="moveItem(resume.education, i, 1)">↓</button>
                <button class="resume-mini-btn resume-mini-btn--danger" title="删除" @click="removeListItem(resume.education, i)">×</button>
              </div>
              <button class="resume-add-btn" @click="addListItem(resume.education)">+ 添加教育经历</button>
            </div>

            <div class="resume-editor-block">
              <div class="resume-editor-label">证书奖项</div>
              <div v-for="(c, i) in resume.certificates" :key="'cer-' + i" class="resume-edit-row">
                <input v-model="resume.certificates[i]" class="resume-field-input resume-edit-input" placeholder="证书/奖项" />
                <button class="resume-mini-btn" title="上移" @click="moveItem(resume.certificates, i, -1)">↑</button>
                <button class="resume-mini-btn" title="下移" @click="moveItem(resume.certificates, i, 1)">↓</button>
                <button class="resume-mini-btn resume-mini-btn--danger" title="删除" @click="removeListItem(resume.certificates, i)">×</button>
              </div>
              <button class="resume-add-btn" @click="addListItem(resume.certificates)">+ 添加证书</button>
            </div>
          </div>

          <!-- 右：实时预览（所见即所得） -->
          <div class="resume-preview">
            <div ref="resumePaperRef" class="resume-paper" :class="'layout-' + resumeLayout" :style="paperStyle">
              <!-- 侧边栏双栏版 -->
              <template v-if="resumeLayout === 'sidebar'">
                <div class="rs-cols">
                  <aside class="rs-aside">
                    <div class="resume-photo">
                      <img v-if="resume.photo" :src="resume.photo" alt="证件照" />
                      <span v-else>证件照</span>
                    </div>
                    <div class="resume-name">{{ resume.name || '求职者' }}</div>
                    <p v-if="resume.basicInfo" class="resume-basic">{{ resume.basicInfo }}</p>
                    <div v-if="resume.skills && resume.skills.length" class="resume-section">
                      <h4 class="resume-section-title">核心技能</h4>
                      <div class="resume-skills">
                        <span v-for="s in resume.skills" :key="s.name" class="resume-skill" :class="{ 'is-matched': s.matched }">{{ s.name }}</span>
                      </div>
                    </div>
                    <div v-if="resume.certificates && resume.certificates.length" class="resume-section">
                      <h4 class="resume-section-title">证书奖项</h4>
                      <ol class="resume-ol">
                        <li v-for="(c, i) in resume.certificates" :key="'c-' + i">{{ c }}</li>
                      </ol>
                    </div>
                  </aside>
                  <main class="rs-main">
                    <div v-if="resume.selfEvaluation" class="resume-section">
                      <h4 class="resume-section-title">自我评价</h4>
                      <p class="resume-item resume-self">{{ resume.selfEvaluation }}</p>
                    </div>
                    <div v-if="resume.experiences && resume.experiences.length" class="resume-section">
                      <h4 class="resume-section-title">实习/项目经历</h4>
                      <ol class="resume-ol"><li v-for="(e, i) in resume.experiences" :key="'e-' + i">{{ e }}</li></ol>
                    </div>
                    <div v-if="resume.projectHighlights && resume.projectHighlights.length" class="resume-section">
                      <h4 class="resume-section-title">项目亮点</h4>
                      <ol class="resume-ol"><li v-for="(p, i) in resume.projectHighlights" :key="'p-' + i">{{ p }}</li></ol>
                    </div>
                    <div v-if="resume.education && resume.education.length" class="resume-section">
                      <h4 class="resume-section-title">教育背景</h4>
                      <ol class="resume-ol"><li v-for="(e, i) in resume.education" :key="'ed-' + i">{{ e }}</li></ol>
                    </div>
                  </main>
                </div>
              </template>

              <!-- 极简单栏 / 宽页眉 / 轻商务衬线 -->
              <template v-else>
                <div v-if="resumeLayout === 'banner'" class="rs-banner">
                  <div class="rs-banner-main">
                    <div class="resume-name">{{ resume.name || '求职者' }}</div>
                    <p v-if="resume.basicInfo" class="resume-basic">{{ resume.basicInfo }}</p>
                  </div>
                  <div class="resume-photo">
                    <img v-if="resume.photo" :src="resume.photo" alt="证件照" />
                    <span v-else>证件照</span>
                  </div>
                </div>
                <div v-else class="resume-head">
                  <div class="resume-head-main">
                    <div class="resume-name">{{ resume.name || '求职者' }}</div>
                    <p v-if="resume.basicInfo" class="resume-basic">{{ resume.basicInfo }}</p>
                  </div>
                  <div class="resume-photo">
                    <img v-if="resume.photo" :src="resume.photo" alt="证件照" />
                    <span v-else>证件照</span>
                  </div>
                </div>

                <div v-for="s in resumeSections" :key="s.key" class="resume-section">
                  <h4 class="resume-section-title">{{ s.title }}</h4>
                  <p v-if="s.kind === 'text'" class="resume-item resume-self">{{ s.text }}</p>
                  <div v-else-if="s.kind === 'skills'" class="resume-skills">
                    <span v-for="sk in s.items" :key="sk.name" class="resume-skill" :class="{ 'is-matched': sk.matched }">{{ sk.name }}</span>
                  </div>
                  <ol v-else class="resume-ol">
                    <li v-for="(it, idx) in s.items" :key="idx">{{ it }}</li>
                  </ol>
                </div>
              </template>
            </div>
          </div>
        </div>

        <div v-if="resume.missingSkills && resume.missingSkills.length && !missingDismissed" class="resume-missing">
          <h4 class="resume-section-title">待补充技术栈建议</h4>
          <div class="missing-list">
            <div v-for="m in resume.missingSkills" :key="m.name" class="missing-item">
              <span class="missing-name">{{ m.name }}</span>
              <span class="missing-suggestion">{{ m.suggestion }}</span>
            </div>
          </div>
          <div class="missing-question">
            <span>是否将以上技术栈以「学习中/计划掌握」的形式补齐到简历中？</span>
            <div class="missing-buttons">
              <button class="button button--plain button--small" @click="fillMissingSkills">补齐至简历</button>
              <button class="button button--plain button--small" @click="skipFill">暂不补齐</button>
            </div>
          </div>
        </div>
      </template>

      <template #footer>
        <div class="resume-footer">
          <div class="resume-filename">
            <span class="resume-filename-label">文件名</span>
            <input v-model="resumeFilename" class="resume-field-input resume-filename-input" placeholder="岗位名称-姓名-简历" />
          </div>
          <button class="button button--plain" @click="resumeVisible = false">关闭</button>
          <el-dropdown trigger="click" @command="onConfirmDownload">
            <button class="button" :disabled="!resume || exporting" :class="{ 'is-loading': exporting }">
              {{ exporting ? '导出中…' : '确认下载' }}
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="pdf">导出 PDF 格式</el-dropdown-item>
                <el-dropdown-item command="docx">导出 Word 可编辑格式</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </template>
    </el-dialog>

    <!-- 投简历双渠道选择弹窗 -->
    <el-dialog v-model="applyVisible" title="投递简历" width="560px" align-center class="lib-dialog">
      <div class="apply-body">
        <div class="apply-head">
          <span class="apply-label">目标岗位</span>
          <span class="apply-name">{{ applyJob?.jobName || '—' }}</span>
          <span v-if="applyChannel?.companyName" class="apply-company">{{ applyChannel.companyName }}</span>
        </div>

        <div v-if="applyLoading" class="apply-loading">
          <span class="spin"></span>
          <span>正在读取岗位投递信息…</span>
        </div>

        <div v-else class="apply-channels">
          <!-- 岗位详情页投递 -->
          <div
            class="apply-channel"
            :class="{ 'is-disabled': !applyChannel?.companyName && !applyChannel?.jobLinkAvailable }"
            @click="!applyChannel?.companyName && openApplyPatch('company')"
          >
            <div class="apply-channel-main">
              <div class="apply-channel-title">岗位详情页投递</div>
              <div class="apply-channel-desc">
                {{ applyChannel?.jobLinkAvailable
                  ? '已保存该岗位的原始链接，点击直达投递页面'
                  : applyChannel?.companyName
                    ? '将按招聘单位检索该岗位的详情页，点击去投递'
                    : '缺少招聘单位或岗位链接，暂无法投递' }}
              </div>
              <div v-if="!applyChannel?.companyName" class="apply-channel-reason">
                {{ applyChannel?.jobLinkAvailable ? '未填写招聘单位（不影响直接投递），点击可补填' : '请先补填招聘单位，点击此处' }}
              </div>
            </div>
            <button
              class="button button--small"
              :disabled="(!applyChannel?.companyName && !applyChannel?.jobLinkAvailable) || applyDetailLoading"
              @click.stop="openPortal"
            >{{ applyDetailLoading ? '查找中…' : '去投递' }}</button>
          </div>
        </div>

        <div class="apply-note">仅提供岗位详情页跳转辅助，投递操作由你手动完成；不记录投递状态、不存储任何账号信息。</div>
      </div>
      <template #footer>
        <div class="resume-footer">
          <button class="button button--plain" @click="applyVisible = false">关闭</button>
        </div>
      </template>
    </el-dialog>

    <!-- 投简历渠道补填弹窗 -->
    <el-dialog v-model="applyPatchVisible" title="补填招聘单位" width="440px" align-center class="lib-dialog" append-to-body>
      <div class="apply-company-input">
        <p class="apply-company-tip">填写招聘单位后，官网投递渠道将立即激活并同步入库</p>
        <input v-model="applyPatchValue" class="resume-field-input" placeholder="请输入招聘单位" @keyup.enter="confirmApplyPatch" />
      </div>
      <template #footer>
        <div class="resume-footer">
          <button class="button button--plain" @click="applyPatchVisible = false">取消</button>
          <button class="button" :disabled="applyPatchSaving" :class="{ 'is-loading': applyPatchSaving }" @click="confirmApplyPatch">确认</button>
        </div>
      </template>
    </el-dialog>

    <!-- 求职准备（匹配评估 / 定制简历 / 求职信 / ATS 检查） -->
    <ApplicationAssistant
      v-model="applicationVisible"
      :job="applicationJob"
      :match="applicationMatch"
      :has-materials="knowledgeList.length > 0"
      :materials-version="materialsVersion"
      @open-materials="closeAssistantAndOpenMaterials"
    />

    
    <!-- 素材单条编辑弹窗（侧边栏）-->
    <el-dialog v-model="materialEditVisible" :title="materialEditForm.id == null ? '补充素材' : '编辑素材'" width="620px" align-center class="lib-dialog">
      <div v-if="materialEditForm.id == null" class="ai-draft-hint">AI生成草稿，可编辑调整</div>
      <div class="edit-field">
        <label class="edit-label">素材类型</label>
        <el-select v-model="materialEditForm.type" placeholder="请选择类型" class="full-width">
          <el-option v-for="t in MATERIAL_TYPES" :key="t" :label="t" :value="t" />
        </el-select>
      </div>
      <div class="edit-field">
        <label class="edit-label">素材内容</label>
        <el-input v-model="materialEditForm.content" type="textarea" :rows="6" maxlength="3000" show-word-limit placeholder="请输入素材内容" />
      </div>
      <template #footer>
        <button class="button button--plain" @click="materialEditVisible = false">取消</button>
        <button class="button" :disabled="materialEditSaving" :class="{ 'is-loading': materialEditSaving }" @click="submitEditMaterial">{{ materialEditForm.id == null ? '确认添加' : '保存' }}</button>
      </template>
    </el-dialog>

    <!-- 匹配权重配置弹窗 -->
    <el-dialog v-model="weightConfigVisible" title="匹配权重配置" width="520px" align-center class="lib-dialog" append-to-body>
      <div class="weight-desc">
        匹配总分按四个维度加权合成（技能+经历+学历证书+岗位契合）。权重总和必须等于 100，调整后即时生效。
      </div>
      <div class="weight-rows">
        <div v-for="w in weightInputs" :key="w.key" class="weight-row">
          <span class="weight-label">{{ w.label }}</span>
          <el-input-number
            v-model="w.value"
            :min="0"
            :max="100"
            :step="1"
            size="small"
            class="weight-input"
          />
          <span class="weight-unit">%</span>
        </div>
      </div>
      <div class="weight-total" :class="{ 'is-error': weightTotal !== 100 }">
        权重合计：<b>{{ weightTotal }}</b> / 100
        <span v-if="weightTotal !== 100" class="weight-error">（必须等于 100）</span>
        <span v-else class="weight-ok">✓ 校验通过</span>
      </div>
      <template #footer>
        <button class="button button--plain" @click="resetWeightConfig">恢复默认</button>
        <button class="button" :disabled="weightTotal !== 100" @click="applyWeightConfig">应用配置</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { AlertCircle, CheckCircle2, Lightbulb } from 'lucide-vue-next'
import { getToken } from '@/utils/token'
import { buildCatalog, evaluateJob } from '@/utils/jobMatchEngine'
import { highlightSegments } from '@/utils/highlight'
import { batchAddTargetJobs, getTodayRecommend } from '@/api/jobRecommend'
import ApplicationAssistant from '@/components/ApplicationAssistant.vue'
import GenerationProgress from '@/components/GenerationProgress.vue'
import { fetchResumeTemplates, readCachedResumeTemplates } from '@/utils/resumeTemplates'
import { recordGenerationDuration } from '@/utils/generationTiming'
import { cropAndCompressPhoto } from '@/utils/photo'
import { useLinkageStore } from '@/stores/linkage'
import { usePositiveMomentStore } from '@/stores/positiveMoment'
import { useJobViewStore } from '@/stores/jobView'
import {
  listKnowledge, deleteKnowledge, splitKnowledge, importResume, batchSaveKnowledge, saveBasicInfo, updateKnowledge,
  addKnowledgeItem, generateMaterial,
  listJobs, addJob, deleteJobs, previewJob, previewJobByUrl, importJob, getLatestAnalysis, verifyMatch, analyzeAsync, getAnalyzeTask, generateResume, exportResumeDocx, ocrJob, recognizeApplyChannel, patchApplyChannel, resolveApplyDetailLink, createApplyDraft
} from '@/api/jobmatch'
import { getGenerationProgress } from '@/api/application'
import { getRecommendVersion } from '@/api/jobRecommend'

const props = defineProps({
  fabVisible: { type: Boolean, default: true }
})

const router = useRouter()
const linkage = useLinkageStore()
const positiveMoment = usePositiveMomentStore()
const jobViewStore = useJobViewStore()

const MATERIAL_TYPES = ['基础信息', '教育经历', '项目经历', '实习经历', '技能证书', '荣誉奖项', '其他']

// 进入岗位匹配时，所有小精灵短暂亮出名称，之后仍可 hover 查看
const elfIntro = ref(false)
let elfIntroTimer = null

const knowledgeList = ref([])
// 素材版本：内容变化后让求职助手的评估/定制简历/求职信缓存失效
const materialsVersion = computed(() =>
  (knowledgeList.value || []).map((item) => `${item.id}:${item.content || ''}`).join('|')
)

// 明确已补齐的待补足项映射：{ 要求原文 -> 素材id }。
// 快速补充是用户明确针对某个待补足项的操作，即便 LLM 改写措辞导致关键词匹配不到，也应判定为已覆盖。
// 按登录 token 隔离持久化，避免刷新后待补足项因关键词匹配不到而"复活"。
const CLOSED_GAPS_KEY = 'jobmatch_closed_gaps_' + (getToken() || 'anon')
const closedGaps = ref((() => {
  try { return JSON.parse(localStorage.getItem(CLOSED_GAPS_KEY) || '{}') } catch (e) { return {} }
})())
watch(closedGaps, (val) => {
  try { localStorage.setItem(CLOSED_GAPS_KEY, JSON.stringify(val || {})) } catch (e) { /* ignore */ }
}, { deep: true })

// 必填信息区（固定在主页面）
const basicForm = ref({ name: '', education: '', school: '', major: '' })
const basicSaving = ref(false)

// 自由文本录入（新增素材弹窗）
const freeAddContent = ref('')
const freeSaving = ref(false)

// 新增素材（文件导入 / 自由文本录入）
const addMaterialVisible = ref(false)
const addMaterialTab = ref('file')
function openAddMaterial(tab = 'file') {
  addMaterialTab.value = tab
  addMaterialVisible.value = true
}
const importing = ref(false)
const importProgress = ref(0)
const importHint = ref('正在解析并拆分简历…')
const importPhase = ref(0) // 0 处理文件 / 1 识别文字 / 2 格式化整体
const importMinimized = ref(false)
let importPhaseTimer = null
const importPendingPreview = ref(null) // 最小化期间缓存预览数据，点击通知时打开

// 预览比对
const previewVisible = ref(false)
const previewSaving = ref(false)
const pendingSplit = ref(null)

// 疑似重复
const duplicateDialogVisible = ref(false)
const duplicatePairs = ref([])
const pendingRecords = ref([])
const duplicateTime = ref(null)
const dupSaving = ref(false)

// 将若干条「属性+内容」记录按属性类型分组，保持类型首次出现顺序
function groupByType(items) {
  const map = new Map()
  for (const it of items || []) {
    const t = it.type || '其他'
    if (!map.has(t)) map.set(t, [])
    map.get(t).push(it)
  }
  return Array.from(map.entries()).map(([type, list]) => ({ type, items: list }))
}

// AI 拆解结果预览：按属性类型合并展示
const previewGroups = computed(() => groupByType((pendingSplit.value && pendingSplit.value.records) || []))

const basicComplete = computed(() => {
  const f = basicForm.value
  return !!(f.name.trim() && f.education.trim() && f.school.trim() && f.major.trim())
})

// 从「标签：值」格式内容中提取值，兼容中英文冒号
function extractLabelValue(content, label) {
  if (!content) return ''
  const m = String(content).match(new RegExp('^\\s*' + label + '\\s*[:：]\\s*(.*)$', 's'))
  return m ? m[1].trim() : ''
}

// 4 项必填简历信息：姓名属于基础信息，学历/毕业院校/专业属于教育经历
const REQUIRED_FIELDS = [
  { key: 'name', label: '姓名', scope: '基础信息', kw: null, extra: ['个人概况'] },
  { key: 'education', label: '学历', scope: '教育经历', kw: /本科|硕士|博士|研究生|大专|专科|中专|高中|学士|学位|学历|在读/, extra: ['学历背景'] },
  { key: 'school', label: '毕业院校', scope: '教育经历', kw: /大学|学院|学校|院校/, extra: ['学历背景', '院校'] },
  { key: 'major', label: '专业', scope: '教育经历', kw: /专业/, extra: ['学历背景'] }
]

// 逐项判定素材库中是否存在「内容非空、语义有效」的必填记录
const requiredStatus = computed(() => {
  const list = knowledgeList.value || []
  return REQUIRED_FIELDS.map((f) => ({
    ...f,
    present: list.some((i) => {
      if ((i.type || '') !== f.scope) return false
      const c = (i.content || '').trim()
      if (!c) return false
      if (extractLabelValue(c, f.label)) return true
      if (f.extra && f.extra.some((ex) => extractLabelValue(c, ex))) return true
      if (f.kw) return f.kw.test(c)
      // 姓名兜底：去除「姓名：」前缀后为 2-4 个中英文字符的短串
      const t = c.replace(/^(姓名|name)\s*[:：]\s*/i, '').trim()
      return t.length >= 2 && t.length <= 4 && !/[\d@.\-:/：()（）]/.test(t) && /[\u4e00-\u9fa5A-Za-z]/.test(t)
    })
  }))
})

const missingRequired = computed(() => requiredStatus.value.filter((f) => !f.present).map((f) => f.label))
const requiredReady = computed(() => missingRequired.value.length === 0)

// 记录按batchId 分组为「卡片壳」，每组对应一次入库
// —— 我的目标岗位 ——
const jobList = ref([])
const jobPasteContent = ref('')
const jobPasteBox = ref(null)
const jobPasteSaving = ref(false)
const ocrLoading = ref(null)

// 链接导入
const jobLinkVisible = ref(false)
const jobLinkUrl = ref('')
const jobLinkSaving = ref(false)

// 导入岗位
const jobImportVisible = ref(false)
const jobImporting = ref(false)
const jobImportHint = ref('正在解析并标准化岗位信息…')
const jobImportProgress = ref(0)
const jobImportPhase = ref(0) // 0 处理文件 / 1 识别文字 / 2 格式化整体

// 导入方式选择：文件 / 链接
const importChoiceVisible = ref(false)
function chooseImport(type) {
  importChoiceVisible.value = false
  if (type === 'file') jobImportVisible.value = true
  else if (type === 'link') jobLinkVisible.value = true
}
const jobImportMinimized = ref(false)
let jobImportPhaseTimer = null
const jobImportPendingPreview = ref(null)

// 岗位导入预览（导入/ 粘贴 共用）
const jobPreviewVisible = ref(false)
const jobPreviewSaving = ref(false)
const pendingJob = ref(null)

const jobFilterKeyword = ref('')
const jobActiveCat = computed(() => jobViewStore.view)
const jobTypeFilters = computed(() => jobViewStore.currentFilters)
// 首次数据回来前不渲染空态，避免切页瞬间闪一下「0 个岗位 / 暂无素材」
const initialLoading = ref(true)
const recommendJobs = ref([])
const recommendJobsLoading = ref(false)
const addingRecommendId = ref(null)
let recommendFailures = 0
let recommendRetryAt = 0

// 主标签切换：综合榜和推荐榜统一按匹配度排，名次才有含义
function switchCat(cat) {
  jobViewStore.setView(cat)
  if (cat === 'combined' || cat === 'recommend') jobSortKey.value = 'score'
}

// 在最新推荐里点任意内容，视为已看当天推荐
function notifyRecommendSeen() {
  if (jobViewStore.view === 'recommend') {
    window.dispatchEvent(new CustomEvent('recommend-seen'))
  }
}
const jobFlashIds = ref(new Set())
const batchDeleting = ref(false)

// —— 工作台布局：右滑简历素材侧边栏 ——
const MATERIAL_DRAWER_KEY = 'jobmatch_material_drawer_open'
const sidebarOpen = ref((() => { try { return localStorage.getItem(MATERIAL_DRAWER_KEY) !== '0' } catch (e) { return true } })())
function openSidebar(v) {
  sidebarOpen.value = v
  try { localStorage.setItem(MATERIAL_DRAWER_KEY, v ? '1' : '0') } catch (e) { /* 忽略 */ }
}
function toggleSidebar() {
  openSidebar(!sidebarOpen.value)
}
function openSidebarFocus(section) {
  if (section === 'free') { openAddMaterial('text'); return }
  openSidebar(true)
  nextTick(() => {
    const el = document.getElementById('side-required')
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

// 粘贴导入（默认收起）
const jobPasteExpanded = ref(false)
function focusJobPaste() {
  jobPasteExpanded.value = true
  nextTick(() => {
    const el = jobPasteBox.value
    if (el && typeof el.focus === 'function') el.focus()
  })
}

// —— 岗位排序 ——
const SORT_OPTIONS = [
  { label: '匹配度从高到低', value: 'score' },
  { label: '添加时间降序', value: 'createTime' },
  { label: '岗位名称（按拼音）', value: 'jobName' },
]
const jobSortKey = ref('score')

// 岗位名称排序：中文按拼音，空名称沉底
const jobNameCollator = new Intl.Collator('zh-CN', { sensitivity: 'base', numeric: true })

// 匹配度排序基准：仅「有实际匹配分（>0）」参与排序，0 分/无匹配数据返回 null（统一沉底）
function matchSortValue(job) {
  const r = job && job.match
  const s = num(r && r.score)
  return s > 0 ? s : null
}

// 标准时间戳，供所有排序兜底使用
function jobTimeValue(job) {
  return parseTime(job && job.createTime) || 0
}

// 匹配结果按jobId 建立索引
const jobMatchMap = computed(() => {
  const map = {}
  for (const r of rankList.value || []) {
    if (r.jobId != null) map[r.jobId] = r
  }
  return map
})

// 岗位 + 匹配结果合并（同源）
/**
 * 岗位要求清单（catalog）：每个岗位只在「JD 变化」或「首次」时构建一次，之后固定复用。
 * 这样素材增删改只会触发本地确定性重算（毫秒级），不会因为大模型重新解析 JD 而让分数跳动。
 */
const _catalogCache = new Map()   // jobId -> { sig, catalog }
const CATALOG_STORAGE_KEY = 'jobmatch_catalog_v2'
const _catalogStored = (() => {
  try { return JSON.parse(localStorage.getItem(CATALOG_STORAGE_KEY) || '{}') } catch (e) { return {} }
})()
function jobSig(job) {
  return [job.jobName || '', job.jobRequirement || '', job.techStack || ''].join('\u0001')
}
function catalogFor(job) {
  if (!job || job.id == null) return []
  const sig = jobSig(job)
  // 会话内缓存：首次构建后固定复用
  const cached = _catalogCache.get(job.id)
  if (cached && cached.sig === sig) return cached.catalog
  // 跨会话持久化的稳定目录：首次构建后固定复用。
  // 关键：不能随后端重新评估的 details 重建 —— 后端 details 会随知识库变化而缩水，
  // 导致刷新后岗位要求清单变短、分数被误算回满分。
  const stored = _catalogStored[job.id]
  if (stored && stored.sig === sig && Array.isArray(stored.catalog) && stored.catalog.length) {
    _catalogCache.set(job.id, { sig, catalog: stored.catalog })
    return stored.catalog
  }
  // 首次构建（JD 未变则只构建这一次）：buildCatalog 内部优先大模型 details、缺省 JD 文本兜底
  const catalog = buildCatalog(job)
  if (catalog && catalog.length) {
    _catalogCache.set(job.id, { sig, catalog })
    _catalogStored[job.id] = { sig, catalog }
    try { localStorage.setItem(CATALOG_STORAGE_KEY, JSON.stringify(_catalogStored)) } catch (e) { /* ignore */ }
  }
  return catalog || []
}

/**
 * 岗位展示数据 = 岗位库 + 实时评估结果。
 * 分数 / 维度 / 待补足 / 优势 / 不足 / 优化建议全部由「当前素材库 × 岗位要求清单」确定性计算，
 * 素材一改立刻重算，不再依赖大模型异步返回。
 */
const mergedJobs = computed(() =>
  (jobList.value || []).map((j) => {
    const llm = jobMatchMap.value[j.id] || null
    const withLlm = { ...j, match: llm }
    const catalog = catalogFor(withLlm)
    // 引擎结果是纯评估数据，这里补回岗位标识（投简历/生成简历/面试等入口都靠 jobId 定位岗位）
    const stamp = (m) => {
      if (!m) return m
      m.jobId = j.id
      m.jobName = j.jobName
      return m
    }
    // 素材库为空时，任何岗位都不可能有匹配，直接给「0 分 + 全部待补足」，
    // 绝不回退到缓存/大模型的历史分数，避免“清空素材后分数还在”。
    if (!knowledgeList.value.length) {
      return { ...j, match: stamp(evaluateJob(catalog, [], matchWeights.value, closedGaps.value)) }
    }
    if (!catalog.length) return withLlm
    return { ...j, match: stamp(evaluateJob(catalog, knowledgeList.value || [], matchWeights.value, closedGaps.value)) }
  })
)

// 今日推荐岗位：用与目标岗位相同的引擎、按当前素材重算，保证榜单内分数可直接比较
const recommendJobEntries = computed(() => {
  // 素材为空时匹配度恒为 0，推荐没有参考价值：不展示列表，交给空状态引导补素材
  if (!knowledgeList.value.length) return []
  const list = (recommendJobs.value || []).map((rec) => {
  const job = {
    id: `rec-${rec.id}`,
    recommendId: rec.id,
    isRecommendation: true,
    jobName: rec.jobName,
    companyName: rec.companyName,
    location: rec.location,
    salary: rec.salary,
    jobRequirement: rec.requirement || rec.jobDesc || '',
    techStack: '',
    jobLink: rec.sourceUrl || '',
    sourcePlatform: rec.source || '',
    jobType: rec.jobType || '',
    isInTarget: !!rec.isInTarget,
    createTime: rec.crawlTime || rec.crawlDate || ''
  }
  const match = evaluateJob(buildCatalog(job), knowledgeList.value || [], matchWeights.value, closedGaps.value)
  if (match) {
    match.jobId = job.id
    match.jobName = job.jobName
  }
  return { ...job, match }
  })
  // 后端给的是候选池，这里按本地引擎的真实匹配度排序，只展示最高的 10 条
  list.sort((a, b) => (b.match?.score || 0) - (a.match?.score || 0))
  return list.slice(0, 10)
})

function recommendLinkKey(job) {
  return String((job && (job.jobLink || job.sourceUrl)) || '').trim().replace(/\/+$/, '').toLowerCase()
}
function recommendNameKey(job) {
  return [job && job.jobName, job && job.companyName]
    .map((v) => String(v || '').trim().toLowerCase())
    .join('|')
}

// 目标岗位 + 今日推荐；已存在于目标库的推荐按链接/名称去重
const combinedJobs = computed(() => {
  const targets = mergedJobs.value || []
  const targetLinks = new Set(targets.map(recommendLinkKey).filter(Boolean))
  const targetNames = new Set(targets.map(recommendNameKey).filter((k) => k !== '|'))
  const recommendations = recommendJobEntries.value.filter((job) => {
    const link = recommendLinkKey(job)
    if (link && targetLinks.has(link)) return false
    const name = recommendNameKey(job)
    return !(name !== '|' && targetNames.has(name))
  })
  return [...targets, ...recommendations]
})

// 当前主标签下的基础列表：最新推荐只看推荐，我的收藏看目标岗位，查看所有合并
const baseJobsOfCat = computed(() => {
  if (jobActiveCat.value === 'recommend') return recommendJobEntries.value
  if (jobActiveCat.value === 'combined') return combinedJobs.value
  return mergedJobs.value
})

// 三个视图各自的类型选项：导航 hover 面板直接读取
function jobTypeOptions(list) {
  const set = new Set()
  for (const job of list || []) {
    const type = (job.jobType || '').trim()
    if (type && type !== '其他') set.add(type)
  }
  return Array.from(set)
}
const filterOptionsByView = computed(() => ({
  recommend: jobTypeOptions(recommendJobEntries.value),
  all: jobTypeOptions(mergedJobs.value),
  combined: jobTypeOptions(combinedJobs.value)
}))
watch([filterOptionsByView, initialLoading], ([options, loading]) => {
  jobViewStore.setOptions(options)
  if (!loading) jobViewStore.pruneTypes(options)
}, { immediate: true, deep: true })

// 展示列表：分类多选（取并集）/搜索筛选 + 排序
const displayedJobs = computed(() => {
  const kw = jobFilterKeyword.value.trim().toLowerCase()
  let list = baseJobsOfCat.value
  if (jobTypeFilters.value.length) {
    list = list.filter((j) => jobTypeFilters.value.includes((j.jobType || '').trim()))
  }
  if (kw) {
    list = list.filter((j) => (j.jobName || '').toLowerCase().includes(kw))
  }
  const sorted = [...list]
  const key = jobSortKey.value
  const timeIdDesc = (a, b) => (jobTimeValue(b) - jobTimeValue(a)) || (b.id || 0) - (a.id || 0)

  if (key === 'createTime') {
    // 添加时间降序：最新添加靠前
    sorted.sort(timeIdDesc)
  } else if (key === 'jobName') {
    // 岗位名称按拼音排序，同名与空名称兜底用添加时间
    sorted.sort((a, b) => {
      const na = (a.jobName || '').trim()
      const nb = (b.jobName || '').trim()
      if (!na && !nb) return timeIdDesc(a, b)
      if (!na) return 1
      if (!nb) return -1
      return jobNameCollator.compare(na, nb) || timeIdDesc(a, b)
    })
  } else {
    // 匹配度从高到低：综合匹配总分严格降序，0 分/无匹配沉底，同分按添加时间降序
    sorted.sort((a, b) => {
      const va = matchSortValue(a)
      const vb = matchSortValue(b)
      if (va == null && vb == null) return timeIdDesc(a, b)
      if (va == null) return 1
      if (vb == null) return -1
      return (vb - va) || timeIdDesc(a, b)
    })
  }
  return sorted
})

// 搜索只匹配岗位名称；命中的关键词在名称里高亮
function jobNameSegments(name) {
  const keyword = jobFilterKeyword.value.trim()
  return highlightSegments(name, keyword ? [keyword] : [])
}

// 当前列表里真正带小精灵的岗位数：切到“我的收藏/查看所有”后也能触发名称提示
const elfJobCount = computed(() => displayedJobs.value.filter((job) => !job.isRecommendation).length)
watch([() => props.fabVisible, elfJobCount], ([visible, count]) => {
  if (elfIntroTimer) {
    clearTimeout(elfIntroTimer)
    elfIntroTimer = null
  }
  if (!visible || !count) {
    elfIntro.value = false
    return
  }
  elfIntro.value = true
  elfIntroTimer = window.setTimeout(() => {
    elfIntro.value = false
    elfIntroTimer = null
  }, 1600)
}, { immediate: true })

// 名次只在「匹配度排序」下展示，避免与当前展示顺序矛盾
const showRanking = computed(() =>
  (jobActiveCat.value === 'combined' || jobActiveCat.value === 'recommend') && jobSortKey.value === 'score'
)

// 顶部工具条的视图名
const viewTitle = computed(() => {
  if (jobActiveCat.value === 'recommend') return '最新推荐'
  if (jobActiveCat.value === 'combined') return '查看所有'
  return '我的收藏'
})

const stageEmptyText = computed(() => {
  if (jobTypeFilters.value.length || jobFilterKeyword.value.trim()) return '没有符合筛选条件的岗位'
  if (jobActiveCat.value === 'recommend') return '今日暂无推荐岗位，稍后会自动更新'
  return '暂无岗位，先导入一个岗位开始匹配'
})

// 卡片展开状态（按 jobId） 匹配分变化高亮
const expandedJobs = ref({})
const scoreChangedIds = ref(new Set())
const scorePulseIds = ref(new Set())

/** 分数文字脉冲：只负责 1.2s 的视觉反馈，不参与任何评分逻辑 */
function pulseScoreText(id) {
  scorePulseIds.value = new Set([...scorePulseIds.value, id])
  setTimeout(() => {
    const next = new Set(scorePulseIds.value)
    next.delete(id)
    scorePulseIds.value = next
  }, 1200)
}
function toggleJobDetail(job) {
  expandedJobs.value = { ...expandedJobs.value, [job.id]: !expandedJobs.value[job.id] }
}

// —— 批量勾选（删除）——
const selectedJobIds = ref(new Set())
function isJobSelected(job) { return !!(job && job.id != null && selectedJobIds.value.has(job.id)) }
function toggleJobSelect(job) {
  if (!job || job.id == null) return
  const next = new Set(selectedJobIds.value)
  if (next.has(job.id)) next.delete(job.id)
  else next.add(job.id)
  selectedJobIds.value = next
}
const allSelected = computed(() => {
  const list = displayedJobs.value || []
  return list.length > 0 && list.every((j) => selectedJobIds.value.has(j.id))
})
function toggleSelectAll() {
  if (allSelected.value) {
    selectedJobIds.value = new Set()
  } else {
    selectedJobIds.value = new Set((displayedJobs.value || []).map((j) => j.id))
  }
}
const selectedCount = computed(() => selectedJobIds.value.size)

// 分类、搜索、排序变化后清空勾选，避免批量操作误删当前不可见的岗位
watch([jobActiveCat, jobFilterKeyword, jobSortKey, jobTypeFilters], () => {
  selectedJobIds.value = new Set()
})

// 岗位信息详情弹窗（点击岗位名称展示导入后格式化信息）
const jobDetailVisible = ref(false)
const jobDetailTarget = ref(null)
function openJobDetail(job) {
  if (!job) return
  jobDetailTarget.value = job
  jobDetailVisible.value = true
}

// 排名结果全局 ref（早声明，避免后续 watch 初始化顺序报错）
const rankList = ref([])

// —— 简历素材内容清理：剥离临时状态词，仅保留事实性陈述 ——
function stripTemporaryStatus(text) {
  if (!text) return text
  let s = String(text)
  // 1. 剥括号内状态词：「CISSP认证（备考中）」→「CISSP认证」
    s = s.replace(/[（(]\s*(备考中|在读|学习中|计划中|准备中|意向|进行中|尚未获取|尚未通过|即将参加|备考|学习|培训中|准备获取)\s*[)）]|[（(]\s*备考[^)）]*[)）]/g, '')
  // 2. 剥「系统掌握...」空泛描述块（LLM 沿状态词过度展开）
    s = s.replace(/[；;，,]?\s*系统掌握[^；;]*?(八大知识域|全部内容|全部领域|完整体系)[^；;]*/g, '；')
  // 3. 剥 分号/逗号 间的状态词：「...；进行中；...」
    s = s.replace(/([；;，,])\s*(备考中|在读|学习中|计划中|准备中|进行中|备考|学习|培训中|准备获取|尚未获取|尚未通过)\s*([；;，,]|$)/g, '$1$3')
  // 4. 剥类别前缀后的状态词：「技能证书：备考中...」→「技能证书：...」
    s = s.replace(/[:：]\s*(备考中|在读|学习中|计划中|准备中|进行中|备考|学习|培训中)[^；;，,]*/g, '：')
  // 5. 剥句尾状态词（宽松版，允许「认证备考中」无空格跟在末尾）
    s = s.replace(/(备考中|在读|学习中|计划中|准备中|进行中|备考|学习|培训中|准备获取|尚未获取|尚未通过)$/g, '')
  s = s.replace(/[，,]\s*(备考中|在读|学习中|计划中|准备中|进行中|备考|学习|培训中|准备获取|尚未获取|尚未通过)\s*$/g, '')
  // 6. 中间兜底：剥离「备考+证书+中」这类模式（如「备考CEH」「备考CISSP中」）
  s = s.replace(/\s*备考[^\s，,；;]*中?\s*/g, ' ')
  // 7. 压缩多余分隔符 + 空格 + 悬挂分隔符
    s = s.replace(/[；;]\s*[；;]+/g, '；').replace(/[，,]\s*[，,]+/g, '，')
  s = s.replace(/[：:]\s*[；;]+/g, '：').replace(/[，,]\s*[；;]+/g, '，')
  s = s.replace(/\s+/g, ' ').trim()
  s = s.replace(/^[；;，,、\s]+/, '').replace(/[；;，,、\s]+$/, '')
  s = s.replace(/^[:：]\s*/, '').replace(/\s*[:：]$/, '')
  return s
}

// —— reactive proxy 安全的数组替换 ——
// Vue 3 里 ref([]).value 自动包装成 reactive proxy，对 proxy 赋.value 不起作用
// 必须用 splice 原地替换，才能触发响应式更新
function _replaceRankList(newArr) {
  const normalized = (newArr || []).map((r) => _normalizeRankVO({ ...r }))
  const arr = rankList.value || []
  arr.splice(0, arr.length, ...normalized)
}

// —— 素材变更 → 受影响岗位进入「重算中」占位（不显示中间值，算完直接出权威分数）——
// 素材删/改后，AI 评分需要重新计算（约 10-20 秒）。这段时间内：
// // 1）不再显示上一轮的旧分数，避免「素材都删完了分数还停在 87」的误导；
// // 2）用覆盖度判断哪些岗位真的受影响（已匹配的岗位要求失去了素材覆盖），只给这些岗位打占位；
// 3）重算结果到达后立即清除占位并显示新分数。
const recalcPendingIds = ref(new Set())

/** 该岗位是否正在等待素材变更后的重算结果。*/
function isRecalcPending(job) {
  return !!(job && job.id != null && recalcPendingIds.value.has(job.id))
}

/** 清除「重算中」占位（权威结果已到达）。*/
function clearRecalcPending() {
  if (recalcPendingIds.value.size) recalcPendingIds.value = new Set()
}

// —— 后端异步全量重算（带重试 3 次 + 请求合并）——
let backendRetryCount = 0
const BACKEND_MAX_RETRY = 3
const BACKEND_RETRY_BASE_MS = 500
let backendInFlight = false

function scheduleAnalyze() {
  dataDirty.value = true
  clearTimeout(analyzeDebounceTimer)
  analyzeDebounceTimer = setTimeout(() => {
    if (!knowledgeList.value.length || !jobList.value.length) {
      dataDirty.value = false
      return
    }
    backendRetryCount = 0
    runAnalyzeWithRetry(true)
  }, 150) // 150ms debounce 合并连续操作（缩短刷新明细的延迟）
}

async function runAnalyzeWithRetry(silent) {
  if (backendInFlight) { _pendingAnalyze = true; return } // 请求合并：已有飞行中则标记待补算
  backendInFlight = true
  try {
    await runAnalyze(silent)
    backendRetryCount = 0
  } catch (e) {
    backendRetryCount++
    if (backendRetryCount <= BACKEND_MAX_RETRY) {
      const delay = BACKEND_RETRY_BASE_MS * (1 << (backendRetryCount - 1)) // 500, 1000, 2000
      setTimeout(() => runAnalyzeWithRetry(silent), delay)
    }
  } finally {
    backendInFlight = false
  }
}

// —— 新卡片：顶部匹配素材、涨分预估、动画分数 ——
const TOP_MATCH_SHOWN = 4
const expandedHighlights = ref({})
function toggleHighlights(jobId) { expandedHighlights.value = { ...expandedHighlights.value, [jobId]: !expandedHighlights.value[jobId] } }
// 每条「匹配素材 Top」可独立展开详情；同一岗位内只展开当前点击的那一条
const openHighlights = ref({})
function toggleHighlight(jobId, d) {
  const cur = openHighlights.value[jobId]
  openHighlights.value = { ...openHighlights.value, [jobId]: cur === d.knowledgeId ? null : d.knowledgeId }
  locateMaterial(d)
}
const hotMatchBreakthrough = ref({})
function isHighMatch(job) { return !!(job && job.match && !isRecalcPending(job) && Number(job.match.score) >= 90) }

/** 只做数据一致性对齐 + details 全 matched 的合理推导：
 *  ① 清除 gaps 残留文本（details 全 matched 时 gaps 应该为空）
 *  ② details 全 matched 时 四个维度子分自动顶 100 → 总分 100（正确语义推导，不是 force-cap）
 *  ③ 绝不模板化改写 summary/disadvantages/optimization（后续 LLM 生成的才是真实的）
 *  ④ 绝不随便修改 score（只有 details 全 matched 这个确定性条件才顶 100）
 */
function _normalizeRankVO(r) {
  const gapList = Array.isArray(r.gaps) ? r.gaps.filter((g) => g && String(g).trim()) : []

  // 统一取整（后端可能返回 96.7，前端 optimistic 也可能产生小数）
  r.score = Math.round(Number(r.score) || 0)
  r.skillScore = Math.round(Number(r.skillScore) || 0)
  r.experienceScore = Math.round(Number(r.experienceScore) || 0)
  r.educationScore = Math.round(Number(r.educationScore) || 0)
  r.fitScore = Math.round(Number(r.fitScore) || 0)

  // 一律以后端权威结果为准：即使 details 全部 matched，也可能仍有 gaps、也可能没到 100 分。
  // （旧逻辑「details 全 matched → 强制 100 分 + 清空 gaps」会把 AI 扣过的分和待补足项一起抹掉，
  //   表现为「删了匹配素材分数仍是 100」。）
  r.gaps = gapList
  r.score = clamp(Math.round(num(r.score)))
  return r
}

/** 预估加分：直接读取 detail 上的 quota（由确定性引擎 evaluateJob 计算，预计加分 = 实际加分）。 */
function estimateGain(item) {
  const detail = item && (item.d || item)
  if (detail && detail.quota != null) return '+' + detail.quota
  return '+0'
}

// 动画分数（从 rankList 快照 lerp 到当前）
const scorePrevMap = ref({})
const scoreGainMap = ref({})
const scorePopVisible = ref({})
const animatedScore = ref({})
let rafTimer = null
function startScoreAnim() {
  if (rafTimer) return
  // 动画单步：用内部 step 递归，而不是 requestAnimationFrame(startScoreAnim)。
  // 后者会因 startScoreAnim 开头的 if(rafTimer) 守卫在下一帧直接 return，导致分数只动一帧就卡在中间值。
  const step = () => {
    const next = {}
    let changed = false
    for (const [id, target] of Object.entries(scorePrevMap.value)) {
      const cur = animatedScore.value[id] != null ? animatedScore.value[id] : target
      const diff = target - cur
      if (Math.abs(diff) > 0.05) { next[id] = cur + diff * 0.25; changed = true }
      else next[id] = target
    }
    animatedScore.value = next
    if (changed) rafTimer = requestAnimationFrame(step)
    else { rafTimer = null }
  }
  rafTimer = requestAnimationFrame(step)
}
/**
 * 展示分变化驱动：以「实际展示的分数」（本地引擎计算结果）为准做动画与夸赞提示。
 * 素材一改 → mergedJobs 立刻重算 → 这里检测涨分 → 数字滚动 + 涨分气泡 + 连击夸赞。
 */
const displayScores = computed(() => {
  const map = {}
  for (const job of mergedJobs.value || []) {
    if (job.id == null) continue
    map[job.id] = Number(job.match && job.match.score) || 0
  }
  return map
})
watch(displayScores, (now) => {
  if (!now) return
  const nameById = {}
  for (const job of mergedJobs.value || []) nameById[job.id] = job.jobName
  let upCount = 0
  let upTotal = 0
  let topGain = null
  for (const [idStr, s] of Object.entries(now)) {
    const id = Number(idStr)
    const old = scorePrevMap.value[id]
    if (old != null && s > old) {
      const delta = s - old
      scoreGainMap.value[id] = { old, new: s, delta: Math.round(delta) }
      scorePopVisible.value[id] = true
      setTimeout(() => { scorePopVisible.value = { ...scorePopVisible.value, [id]: false } }, 2200)
      // 初次出分（0 → X）不做「涨分」提示，避免一进页面就弹夸赞
      const isUserGain = old > 0 && !analyzing.value
      if (isUserGain) {
        pulseScoreText(id)
        scoreChangedIds.value = new Set([...scoreChangedIds.value, id])
        setTimeout(() => {
          const next = new Set(scoreChangedIds.value)
          next.delete(id)
          scoreChangedIds.value = next
        }, 2500)
        upCount++
        upTotal += delta
        if (!topGain || delta > topGain.delta) topGain = { delta, name: nameById[id] }
        if (old < 90 && s >= 90 && !hotMatchBreakthrough.value[id]) {
          hotMatchBreakthrough.value = { ...hotMatchBreakthrough.value, [id]: true }
          positiveMoment.trigger({
            type: 'milestone',
            title: '突破 90 分！',
            message: `「${nameById[id] || '该岗位'}」匹配度首次突破90分，投递成功率超高，现在就投出你的简历吧～`
          })
        }
      }
    } else if (old != null && s < old && old > 0 && !analyzing.value) {
      pulseScoreText(id)
    }
  }
  // 连击夸赞（限频，避免刷屏）
  const nowTs = Date.now()
  if (upCount > 0 && nowTs - lastCheerToastAt > CHEER_COOLDOWN_MS) {
    lastCheerToastAt = nowTs
    if (upCount === 1 && topGain) {
      positiveMoment.trigger({
        type: 'score_up',
        title: `「${topGain.name}」匹配度 +${Math.round(topGain.delta)} 分`,
        message: pickCheer()
      })
    } else {
      positiveMoment.trigger({
        type: 'score_up',
        title: `${upCount} 个岗位平均涨了 ${(upTotal / upCount).toFixed(1)} 分`,
        message: pickCheer()
      })
    }
  }
  scorePrevMap.value = now
  startScoreAnim()
}, { deep: false })
function animatedScoreOf(job) {
  if (!job) return 0
  if (job.rankEntry && job.rankEntry.jobId != null) {
    const v = animatedScore.value[job.rankEntry.jobId]
    if (v != null) return v
  }
  if (job.id != null) {
    const v = animatedScore.value[job.id]
    if (v != null) return v
  }
  return Number(job.match && job.match.score) || 0
}
function scoreGradeByValue(v) {
  if (v >= 80) return 'score-high'
  if (v >= 60) return 'score-mid'
  if (v > 0) return 'score-low'
  return ''
}

// 侧边栏→卡片 悬浮高亮（hover 素材条目 → 全卡片对应 chip 高亮）
const hoverSourceId = ref(null)
function hoverSourceEnter(item) { hoverSourceId.value = item && item.id != null ? item.id : null }
function hoverSourceLeave() { hoverSourceId.value = null }

// —— 匹配详情：亮点/ 待补足项 与素材库联动 ——
const DIM_TO_TYPE = {
  '技能': '技能证书',
  '学历': '教育经历',
  '教育': '教育经历',
  '项目': '项目经历',
  '实习': '实习经历',
  '证书': '技能证书',
  '荣誉': '荣誉奖项',
  '奖项': '荣誉奖项',
  '基础': '基础信息',
  '其他': null,
}
function dimensionToType(dim) {
  if (!dim) return null
  const d = String(dim).trim()
  for (const [k, v] of Object.entries(DIM_TO_TYPE)) {
    if (d.includes(k)) return v
  }
  return null
}

/**
 * 「匹配素材Top」：展示分数的真实依据 —— 每条素材覆盖了哪条岗位要求、对应加了多少分。
 * 直接从确定性引擎的 details[].evidenceIds 反查素材，与分数严格一致（不再是相关度排序）。
 */
const matchHighlightsIndex = computed(() => {
  const map = {}
  const entriesById = {}
  for (const e of knowledgeList.value || []) entriesById[e.id] = e
  for (const job of [...(mergedJobs.value || []), ...(recommendJobEntries.value || [])]) {
    const details = (job.match && Array.isArray(job.match.details)) ? job.match.details : []
    const seen = new Set()
    const highlights = []
    for (const d of details) {
      if (d.matched === false) continue
      for (const id of (d.evidenceIds || [])) {
        if (id == null || seen.has(id)) continue
        const entry = entriesById[id]
        if (!entry) continue
        seen.add(id)
        highlights.push({
          knowledgeId: id,
          id,
          type: entry.type || '其他',
          source: entry.type || '素材',
          text: entry.content || '',
          requirement: d.requirement || '',
          quota: d.quota || 0,
        })
      }
    }
    map[job.id] = highlights
  }
  return map
})

function matchHighlights(job) {
  return (job && matchHighlightsIndex.value[job.id]) || []
}

function matchMisses(job) {
  const r = job && job.match
  if (!r) return []
  let list = []
  if (Array.isArray(r.details)) {
    list = r.details
      .filter((d) => d.matched === false)
      .map((d) => {
        const req = d.requirement || ''
        const reason = d.missReason || ''
        const text = (req && reason && req !== reason) ? `${req} · ${reason}` : (req || reason || '未满足')
        return { d, text, requirement: req }
      })
  }
  if (!list.length && Array.isArray(r.gaps)) {
    list = r.gaps.map((g) => ({ d: null, text: g, requirement: g }))
  }
  return list
}

function matchHitCount(job) { return matchHighlights(job).length }
function matchMissCount(job) { return matchMisses(job).length }

const SKILL_TERMS = [
  'Python', 'PyTorch', 'TensorFlow', 'RAG', 'Agent', 'LLM', '大模型', 'Transformer',
  '机器学习', '深度学习', 'NLP', '多模态', 'Java', 'Spring', 'Vue', 'React',
  'MySQL', 'Redis', 'Docker', 'Kubernetes', 'SQL', '数据分析', 'Prompt'
]

function matchDetailRows(job) {
  const rows = job && job.match && job.match.details
  return Array.isArray(rows) ? rows : []
}

function matchAdvantages(job) {
  const rows = matchDetailRows(job).filter((d) => d && d.matched)
  const items = rows
    .map((d) => ({
      requirement: String(d.requirement || '').trim(),
      evidence: String(d.resumeContent || '').trim()
    }))
    .filter((item) => item.requirement)
  if (items.length) return items
  const raw = String((job && job.match && job.match.advantages) || '')
    .replace(/^已匹配[:：]\s*/, '')
    .trim()
  if (!raw || raw.includes('暂无')) return []
  return raw.split(/[；;]/).map((t) => t.trim()).filter(Boolean)
    .map((requirement) => ({ requirement, evidence: '' }))
}

function matchTotalCount(job) {
  const total = Number(job && job.match && job.match.catalogSize)
  if (Number.isFinite(total) && total > 0) return total
  return matchDetailRows(job).length
}

function matchCoveredCount(job) {
  const rows = matchDetailRows(job)
  if (rows.length) return rows.filter((d) => d && d.matched).length
  return Math.max(0, matchTotalCount(job) - matchMisses(job).length)
}

function matchCoveragePercent(job) {
  const coverage = Number(job && job.match && job.match.coverage)
  if (Number.isFinite(coverage)) return Math.max(0, Math.min(100, Math.round(coverage)))
  const total = matchTotalCount(job)
  return total ? Math.round((matchCoveredCount(job) / total) * 100) : 0
}

function matchScorePercent(job) {
  const score = Number(job && job.match && job.match.score)
  if (Number.isFinite(score)) return Math.max(0, Math.min(100, Math.round(score)))
  return matchCoveragePercent(job)
}

function coverageColor(job) {
  const percent = matchScorePercent(job)
  if (percent >= 90) return 'var(--color-success)'
  if (percent >= 60) return 'var(--color-accent)'
  return 'var(--color-warning)'
}

function matchOverviewSub(job) {
  const summary = String((job && job.match && job.match.summary) || '')
  if (summary.includes('我的简历素材为空')) return summary
  const total = matchTotalCount(job)
  if (!total) return summary || '暂无可评估的岗位要求'
  const gaps = matchMisses(job).length
  const covered = matchCoveredCount(job)
  if (!gaps) return `已覆盖全部 ${total} 项岗位要求`
  return `已覆盖 ${covered}/${total} 项要求（按条数 ${matchCoveragePercent(job)}%），还有 ${gaps} 项待补足`
}

function highlightSkill(text, job) {
  const words = new Set()
  const match = job && job.match
  if (match) {
    for (const list of [match.techStack, match.coreRequirements]) {
      if (Array.isArray(list)) {
        list.forEach((word) => {
          const value = String(word || '').trim()
          if (value) words.add(value)
        })
      }
    }
  }
  if (job && typeof job.techStack === 'string') {
    job.techStack.split(/[、,，;；/|]+/).forEach((word) => {
      const value = word.trim()
      if (value) words.add(value)
    })
  }
  SKILL_TERMS.forEach((word) => words.add(word))
  return highlightSegments(text, [...words])
}

const STOP_WORDS = new Set(['的','了','和','与','及','或','在','有','是','为','以','等','我们','你','我','他','她','它','提供','要求','需要','负责','参与','具有','具备','相关','能力','经验','优先','进行','以及','上述','以上','以下','并且','同时','熟悉','掌握','了解','良好','较强','一定','加分项','任职','岗位','学历','工作'])

function highlightKeyword(text) {
  const s = String(text || '')
  if (!s) return [{ text: '', hit: false }]
  const rg = /[\u4e00-\u9fa5]{2,4}|[A-Za-z][A-Za-z0-9+#./-]{1,24}/g
  const segs = []
  let last = 0
  let m
  while ((m = rg.exec(s))) {
    if (m.index > last) segs.push({ text: s.slice(last, m.index), hit: false })
    segs.push({ text: m[0], hit: !STOP_WORDS.has(m[0]) })
    last = m.index + m[0].length
  }
  if (last < s.length) segs.push({ text: s.slice(last), hit: false })
  if (!segs.length) segs.push({ text: s, hit: false })
  return segs
}

/** 素材条目定位只滚动侧边栏内部滚动区，避免 scrollIntoView 连带整页滚动跳动 */
function scrollSideListTo(el) {
  if (!el) return
  const list = el.closest('.side-list')
  if (!list) return
  const box = list.getBoundingClientRect()
  const r = el.getBoundingClientRect()
  list.scrollTo({ top: list.scrollTop + (r.top - box.top) - (box.height - r.height) / 2, behavior: 'smooth' })
}

async function locateMaterial(d) {
  openSidebar(true)
  const id = d && d.knowledgeId
  const explicitType = d && d.source && d.source !== '简历素材' ? d.source : null
  const dimType = dimensionToType(d && d.dimension)
  const fallbackType = explicitType || dimType
  const item = id != null ? (knowledgeList.value || []).find((k) => k.id === id) : null
  const type = item ? (item.type || '其他') : (fallbackType || null)
  if (type && !materialActiveNames.value.includes(type)) {
    materialActiveNames.value = [...materialActiveNames.value, type]
  }
  await nextTick()
  if (id != null) {
    const el = document.getElementById('mat-item-' + id)
    if (el) {
      scrollSideListTo(el)
      flashKnowledgeId.value = id
      setTimeout(() => { if (flashKnowledgeId.value === id) flashKnowledgeId.value = null }, 2200)
    } else if (type) {
      ElMessage.info(`已展开「${type}」分类，素材条目加载中…`)
    } else {
      ElMessage.info('已展开素材侧边栏')
    }
  } else if (type) {
    // 没有定位到具体条目，但维度/分类能推断 → 展开分类并提示
    ElMessage.info(`已展开「${type}」分类（AI 未能匹配到具体素材条目）`)
  } else {
    ElMessage.info('已展开素材侧边栏')
  }
}

// 待补足项快捷补全：AI 生成简历化素材草稿 → 弹窗编辑确认后再入库
const quickFilling = ref(false)
async function quickFill(d, job) {
  const req = (d.requirement || d.text || '').trim()
  if (!req) {
    ElMessage.warning('该待补足项无有效内容')
    return
  }
  if (quickFilling.value) return
  quickFilling.value = true
  try {
    const res = await generateMaterial(req)
    const data = res.data || {}
    materialEditForm.value = { id: null, type: data.type || '其他', content: data.content || '', gapToClose: req }
    openSidebar(true)
    materialEditVisible.value = true
  } catch (e) {
    // 错误已由拦截器统一提示
  } finally {
    quickFilling.value = false
  }
}

// —— 素材侧边栏：分类折叠 + 单条增删改 ——
const materialKeyword = ref('')
const materialType = ref('')
const materialActiveNames = ref([])
const flashKnowledgeId = ref(null)

const materialsByType = computed(() => {
  const kw = materialKeyword.value.trim().toLowerCase()
  const list = (knowledgeList.value || []).filter((i) => {
    if (materialType.value && (i.type || '其他') !== materialType.value) return false
    if (kw && !((i.content || '').toLowerCase().includes(kw))) return false
    return true
  })
  const grouped = groupByType(list)
  const map = new Map(grouped.map((g) => [g.type, g.items]))
  // 按全部分类的顺序全量展示，没有条目的类型也显示（数量为 0）
  if (materialType.value) {
    return [{ type: materialType.value, items: map.get(materialType.value) || [] }]
  }
  return MATERIAL_TYPES.map((t) => ({ type: t, items: map.get(t) || [] }))
})

const filteredMaterialCount = computed(() =>
  (materialsByType.value || []).reduce((n, g) => n + g.items.length, 0)
)

function shortTime(t) {
  const ts = parseTime(t)
  if (ts == null) return ''
  const d = new Date(ts)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

// —— 匹配权重配置（可配置，总和必须 100）——
const WEIGHT_STORAGE_KEY = 'jobmatch.weights'
const DEFAULT_WEIGHTS = { skill: 40, experience: 30, education: 15, fit: 15 }
function loadWeights() {
  try {
    const raw = localStorage.getItem(WEIGHT_STORAGE_KEY)
    if (!raw) return { ...DEFAULT_WEIGHTS }
    const p = JSON.parse(raw)
    const sum = (p.skill || 0) + (p.experience || 0) + (p.education || 0) + (p.fit || 0)
    if (sum !== 100) return { ...DEFAULT_WEIGHTS }
    return {
      skill: clamp0100(Math.round(p.skill)),
      experience: clamp0100(Math.round(p.experience)),
      education: clamp0100(Math.round(p.education)),
      fit: clamp0100(Math.round(p.fit)),
    }
  } catch { return { ...DEFAULT_WEIGHTS } }
}
function clamp0100(n) { return Math.max(0, Math.min(100, isFinite(n) ? n : 0)) }

// 实时参与确定性打分的维度权重（比例形式）。素材或权重变化后 mergedJobs 立即用该值重算，
// 与「匹配权重」配置保持一致，确保用户自定义权重真正生效（此前只改了 rankList 缓存，未作用于展示分）。
const matchWeights = ref({ skill: 0.4, experience: 0.3, education: 0.15, fit: 0.15 })
function syncMatchWeights() {
  const p = loadWeights()
  matchWeights.value = {
    skill: p.skill / 100,
    experience: p.experience / 100,
    education: p.education / 100,
    fit: p.fit / 100,
  }
}
syncMatchWeights()

const weightConfigVisible = ref(false)
const weightInputs = ref([
  { key: 'skill', label: '技能匹配度', value: loadWeights().skill },
  { key: 'experience', label: '经历匹配度', value: loadWeights().experience },
  { key: 'education', label: '学历证书匹配', value: loadWeights().education },
  { key: 'fit', label: '岗位契合度', value: loadWeights().fit },
])
const weightTotal = computed(() => weightInputs.value.reduce((s, w) => s + (Number(w.value) || 0), 0))

function resetWeightConfig() {
  weightInputs.value.forEach((w) => { w.value = DEFAULT_WEIGHTS[w.key] })
}

/** 应用权重配置：localStorage 持久化 + 即时更新实时打分权重（mergedJobs 会立刻重算所有岗位分数） */
function applyWeightConfig() {
  if (weightTotal.value !== 100) {
    ElMessage.warning('权重总和必须等于 100，请调整')
    return
  }
  const cfg = { skill: Number(weightInputs.value[0].value) || 0, experience: Number(weightInputs.value[1].value) || 0, education: Number(weightInputs.value[2].value) || 0, fit: Number(weightInputs.value[3].value) || 0 }
  localStorage.setItem(WEIGHT_STORAGE_KEY, JSON.stringify(cfg))
  // 更新实时权重 → mergedJobs/evaluateJob 立即按新权重重算展示分（<30ms）
  syncMatchWeights()
  weightConfigVisible.value = false
  ElMessage.success('权重配置已应用，岗位匹配分已即时更新')
}

const materialEditVisible = ref(false)
const materialEditSaving = ref(false)
const materialEditForm = ref({ id: null, type: '其他', content: '', gapToClose: null })

function openEditMaterial(item) {
  materialEditForm.value = { id: item.id, type: item.type || '其他', content: item.content || '', gapToClose: null }
  materialEditVisible.value = true
}

async function submitEditMaterial() {
  const f = materialEditForm.value
  const content = (f.content || '').trim()
  if (!content) {
    ElMessage.warning('内容不能为空')
    return
  }
  materialEditSaving.value = true
  const cleanContent = stripTemporaryStatus(content)
  f.content = cleanContent
  try {
    if (f.id == null) {
      const res = await addKnowledgeItem({ type: f.type || '其他', content: cleanContent })
      const saved = res.data
      if (saved && saved.id) {
        // 乐观更新：直接追加到本地，避免 listKnowledge 触发后端迁移/重分类 LLM 导致分数更新延迟
        knowledgeList.value = [saved, ...knowledgeList.value]
        // 快速补充：明确补齐该待补足项，记录映射使 evaluateJob 判定为已覆盖（绕过关键词匹配的改写盲区）
        if (f.gapToClose) closedGaps.value = { ...closedGaps.value, [f.gapToClose]: saved.id }
        focusMaterial(saved.id)
      }
      materialEditVisible.value = false
      ElMessage.success('已添加，岗位匹配度已重新计算')
      positiveMoment.trigger({
        type: 'material_added',
        title: '素材已入库',
        message: '又补上一块拼图，简历更完整了！'
      })
    } else {
      await updateKnowledge(f.id, { type: f.type || '其他', content: cleanContent })
      const idx = knowledgeList.value.findIndex((i) => i.id === f.id)
      if (idx >= 0) knowledgeList.value[idx] = { ...knowledgeList.value[idx], type: f.type || '其他', content: cleanContent }
      materialEditVisible.value = false
      ElMessage.success('已保存，岗位匹配度已重新计算')
      positiveMoment.trigger({
        type: 'material_added',
        title: '素材已更新',
        message: '内容越打磨，匹配度越稳～'
      })
    }
    // 素材增删改后由 mergedJobs（确定性引擎）立即重算展示分，无需再触发后端慢重算
  } catch (e) {
    // 错误已由拦截器统一提示
  } finally {
    materialEditSaving.value = false
  }
}

async function removeMaterial(item) {
  try {
    await ElMessageBox.confirm('确定删除该条素材吗？删除后岗位匹配度将自动重算。', '提示', { type: 'warning' })
  } catch (e) {
    return
  }
  await deleteKnowledge(item.id)
  // 素材删除后 knowledgeList 变更 → mergedJobs 立即重算，删掉素材覆盖的要求立刻变回「待补足项」并下调分数
  knowledgeList.value = knowledgeList.value.filter((i) => i.id !== item.id)
  // 同步清除该素材对应的「已补齐」映射，让对应待补足项随素材删除而重新出现
  closedGaps.value = Object.fromEntries(Object.entries(closedGaps.value).filter(([, matId]) => matId !== item.id))
  ElMessage.success('已删除，岗位匹配度已重新计算')
}

function focusMaterial(id) {
  const type = (knowledgeList.value.find((k) => k.id === id) || {}).type || '其他'
  if (!materialActiveNames.value.includes(type)) {
    materialActiveNames.value = [...materialActiveNames.value, type]
  }
  nextTick(() => {
    const el = document.getElementById('mat-item-' + id)
    if (el) scrollSideListTo(el)
    flashKnowledgeId.value = id
    setTimeout(() => { if (flashKnowledgeId.value === id) flashKnowledgeId.value = null }, 2200)
  })
}

// —— 求职准备 / 投递：由岗位卡片等价调用原排名入口 ——
// 始终取当前素材下的最新本地匹配结果，刷新评估与卡片分数保持一致
const applicationMatch = computed(() => {
  const id = applicationJob.value?.id
  if (id == null) return null
  const target = (mergedJobs.value || []).find((j) => j.id === id)
  if (target) return target.match || null
  const rec = (recommendJobEntries.value || []).find((j) => j.id === id)
  return rec ? rec.match || null : null
})
function openApplicationFromJob(job) {
  applicationJob.value = { id: job.id, jobName: job.jobName, companyName: job.companyName }
  applicationVisible.value = true
}
function openApplyFromJob(job) {
  openApply(job.match || { jobId: job.id, jobName: job.jobName, companyName: job.companyName })
}

// —— 求职小精灵：点击播放弹跳 + 星星，同时打开求职准备 ——
const elfPopId = ref(null)
let elfPopTimer = null
function openAssistantFromElf(job) {
  elfPopId.value = job.id
  clearTimeout(elfPopTimer)
  elfPopTimer = setTimeout(() => { elfPopId.value = null }, 620)
  openApplicationFromJob(job)
}

// —— 今日岗位推荐：每天 8 点版本变化后自动刷新 TOP10 ——
const recommendVersion = ref(null)

/** 今日岗位是否还没爬到：此时页面展示的是历史数据，需要轮询等待后台补爬完成 */
const localDateText = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const recommendStale = computed(
  () => !!recommendVersion.value?.date && recommendVersion.value.date !== localDateText()
)
let recommendPollTimer = null
let recommendPollCount = 0

function scheduleRecommendPoll() {
  // 最多轮询 12 次（约 1 分钟），避免后台爬取失败时无限请求
  if (recommendPollTimer || recommendPollCount >= 12) return
  recommendPollTimer = setTimeout(async () => {
    recommendPollTimer = null
    recommendPollCount += 1
    await loadRecommendVersion()
    if (recommendStale.value) scheduleRecommendPoll()
  }, 5000)
}

async function loadRecommendVersion() {
  try {
    const res = await getRecommendVersion()
    const data = res.data?.data || {}
    const version = Number(data.version) || 0
    const count = Number(data.count) || 0
    const previousVersion = recommendVersion.value?.version || 0
    // 切换前是否还在展示历史数据：用于判断这次刷新是不是「今日岗位刚更新完成」
    const wasStale = recommendStale.value
    recommendVersion.value = { date: data.date || '', version, count }
    if (!version || !count) {
      recommendJobs.value = []
      return
    }
    // 版本变化或本地还没拿到推荐列表时，同步拉取当天 TOP10
    if (version !== previousVersion || !recommendJobs.value.length) {
      await loadTodayRecommendJobs()
      // 从「昨日数据」切到「今日数据」：明确提示并高亮今日岗位
      if (wasStale && !recommendStale.value) {
        ElMessage.success('今日岗位已更新')
        recommendJobs.value.forEach((job) => flashJob(job.id))
      }
    }
    // 今日岗位还没就绪：轮询等待后台补爬，就绪后版本变化会自动触发刷新
    if (recommendStale.value) {
      scheduleRecommendPoll()
    } else {
      recommendPollCount = 0
      if (recommendPollTimer) {
        clearTimeout(recommendPollTimer)
        recommendPollTimer = null
      }
    }
  } catch (e) {
    // 版本检查失败不打扰用户
  }
}

async function loadTodayRecommendJobs() {
  if (recommendJobsLoading.value || Date.now() < recommendRetryAt) return
  recommendJobsLoading.value = true
  try {
    const res = await getTodayRecommend()
    recommendJobs.value = Array.isArray(res.data) ? res.data : []
    recommendFailures = 0
    recommendRetryAt = 0
  } catch (e) {
    recommendFailures++
    const delay = Math.min(5 * 60 * 1000, 60 * 1000 * Math.pow(2, recommendFailures - 1))
    recommendRetryAt = Date.now() + delay
  } finally {
    recommendJobsLoading.value = false
  }
}

// 推荐加入目标库后：同步推荐状态并刷新目标岗位，让综合榜原地换成目标岗位卡片
async function onRecommendAdded(jobIds) {
  const ids = new Set(jobIds || [])
  if (ids.size) {
    recommendJobs.value = recommendJobs.value.map((job) => (ids.has(job.id) ? { ...job, isInTarget: true } : job))
  }
  try {
    const res = await listJobs()
    jobList.value = res.data || []
  } catch (e) {
    // 错误已由拦截器统一提示
  }
}

async function addRecommendationToTarget(job) {
  if (!job || job.recommendId == null || addingRecommendId.value != null) return
  addingRecommendId.value = job.recommendId
  try {
    const res = await batchAddTargetJobs({ jobIds: [job.recommendId] })
    const added = Number(res.data) || 0
    ElMessage.success(added > 0 ? '已加入我的收藏' : '该岗位已在「我的收藏」中')
    await onRecommendAdded([job.recommendId])
  } catch (e) {
    // 错误已由拦截器统一提示
  } finally {
    addingRecommendId.value = null
  }
}

function openExternalLink(url) {
  const link = String(url || '').trim()
  if (!/^https?:\/\//i.test(link)) {
    ElMessage.warning('该岗位没有可跳转的外部链接')
    return
  }
  const a = document.createElement('a')
  a.href = link
  a.target = '_blank'
  a.rel = 'noopener noreferrer'
  document.body.appendChild(a)
  a.click()
  a.remove()
}

// —— 批量删除（作用于勾选的岗位） ——
async function batchDeleteCurrent() {
  const visibleIds = new Set((displayedJobs.value || []).map((job) => job.id))
  const ids = [...selectedJobIds.value].filter((id) => visibleIds.has(id))
  if (!ids.length) {
    ElMessage.info('请先勾选要删除的岗位')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${ids.length} 个岗位吗？此操作不可恢复。`, '批量删除', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  batchDeleting.value = true
  try {
    await deleteJobs(ids)
    jobList.value = jobList.value.filter((j) => !ids.includes(j.id))
    selectedJobIds.value = new Set([...selectedJobIds.value].filter((id) => !ids.includes(id)))
    _replaceRankList((rankList.value || []).filter((r) => r.jobId == null || !ids.includes(r.jobId)))
    clearRankCache()
    dataDirty.value = true
    ElMessage.success('已删除')
  } catch (e) {
    // 错误已由拦截器统一提示
  } finally {
    batchDeleting.value = false
  }
}

const analyzing = ref(false)
const dataDirty = ref(false)
const analyzeProgress = ref(null)   // { total, done, jobIds, startedAt }
const analyzeSamples = ref([])      // 轮询到的真实完成数样本：{ t, done }

const analyzeDone = computed(() => (analyzeProgress.value && analyzeProgress.value.done) || 0)
const analyzeTotal = computed(() => (analyzeProgress.value && analyzeProgress.value.total) || jobList.value.length || 0)
const analyzePercent = computed(() => {
  if (!analyzeTotal.value) return 0
  return Math.max(3, Math.min(100, Math.round((analyzeDone.value / analyzeTotal.value) * 100)))
})

// 计算耗时/剩余时间用独立时钟驱动，避免依赖轮询频率
const analyzeNow = ref(Date.now())
let analyzeClockTimer = null
const analyzeElapsedSec = computed(() => {
  const startedAt = analyzeProgress.value?.startedAt
  if (!startedAt) return 0
  return Math.max(0, Math.floor((analyzeNow.value - startedAt) / 1000))
})
// 按最近 10s 的真实完成速度估算剩余区间；没有足够样本时不猜，直接提示仍在并行评估
const analyzeRemainingRange = computed(() => {
  const total = analyzeTotal.value
  const done = analyzeDone.value
  if (total <= 0 || done >= total) return null
  const window = analyzeSamples.value.filter((s) => analyzeNow.value - s.t <= 10000)
  if (window.length < 2) return null
  const first = window[0]
  const last = window[window.length - 1]
  if (last.done <= first.done || last.t <= first.t) return null
  const rate = (last.done - first.done) / ((last.t - first.t) / 1000)
  if (!Number.isFinite(rate) || rate <= 0) return null
  const remaining = total - done
  const seconds = remaining / rate
  const min = Math.max(1, Math.round(seconds * 0.7))
  const max = Math.max(min + 1, Math.round(seconds * 1.3))
  return [min, max]
})
const analyzeTimeText = computed(() => {
  if (!analyzeTotal.value) return '正在准备…'
  const done = analyzeDone.value
  const total = analyzeTotal.value
  if (done >= total) return '即将完成'
  const remaining = total - done
  const elapsed = analyzeElapsedSec.value
  const head = `${elapsed > 0 ? `已用 ${formatAnalyzeSec(elapsed)}` : '刚开始'} · 剩余 ${remaining} 个岗位`
  const range = analyzeRemainingRange.value
  return range
    ? `${head} · 预计还需约 ${formatAnalyzeSec(range[0])}–${formatAnalyzeSec(range[1])}`
    : `${head}并行评估中`
})
function formatAnalyzeSec(seconds) {
  const sec = Math.max(0, Math.round(seconds))
  if (sec < 60) return `${sec}s`
  return `${Math.floor(sec / 60)}m${sec % 60 ? ` ${sec % 60}s` : ''}`
}
function startAnalyzeClock() {
  stopAnalyzeClock()
  analyzeNow.value = Date.now()
  analyzeClockTimer = setInterval(() => { analyzeNow.value = Date.now() }, 1000)
}
function stopAnalyzeClock() {
  if (analyzeClockTimer) {
    clearInterval(analyzeClockTimer)
    analyzeClockTimer = null
  }
}
/** 本次要评估、但还没有结果的岗位 id（前端据此展示「评估中」）。 */
const analyzePendingIds = computed(() => {
  const p = analyzeProgress.value
  if (!p || !Array.isArray(p.jobIds)) return []
  const got = new Set((rankList.value || []).map((r) => r && r.jobId).filter((id) => id != null))
  return p.jobIds.filter((id) => !got.has(id))
})
/** 该岗位是否正在本次评估队列里且还没有结果。 */
function isJobEvaluating(job) {
  if (!job || job.id == null) return false
  return analyzePendingIds.value.includes(job.id)
}

// 匹配自动重算的防抖计时器 + 重算前分数快照（用于匹配分变化高亮）
let analyzeDebounceTimer = null
let prevScoresForDiff = {}
let lastCheerToastAt = 0
const CHEER_COOLDOWN_MS = 4000
const CHEER_TEMPLATES = [
  '简历含金量直接拉满！',
  '离 offer 又近一步啦～',
  '你的简历适配度越来越强了！',
  '越来越能打了，继续冲！',
  '这波优化稳赚不亏',
  '又解锁了一个匹配亮点！',
  '涨分啦涨分啦，美滋滋',
]

function pickCheer() { return CHEER_TEMPLATES[Math.floor(Math.random() * CHEER_TEMPLATES.length)] }

// —— 排名结果本地持久化（按登录 token 隔离，刷新/跨页返回秒开历史结果）——
const rankCacheKey = () => 'jobmatch_rank_cache_' + (getToken() || 'anon')

function clearRankCache() {
  try {
    localStorage.removeItem(rankCacheKey())
  } catch (e) {
    // 移除失败忽略
  }
}

// saveRankCache 加 debounce：100ms 内多次调用合并为一次，避免批量操作时高频写 localStorage
let _rankCacheDebounce = null
function saveRankCache() {
  clearTimeout(_rankCacheDebounce)
  _rankCacheDebounce = setTimeout(() => {
    try {
      localStorage.setItem(rankCacheKey(), JSON.stringify({
        results: rankList.value,
        savedAt: Date.now()
      }))
    } catch (e) {
      // 存储失败（隐私模式/超限）静默忽略，不影响展示
    }
  }, 100)
}

function loadRankCache() {
  try {
    const raw = localStorage.getItem(rankCacheKey())
    if (!raw) return false
    const data = JSON.parse(raw)
    if (data && Array.isArray(data.results) && data.results.length) {
      _replaceRankList(data.results) // results 已经是 normalize 后，_replaceRankList 会再次 normalize（幂等）
      return true
    }
  } catch (e) {
    // 解析失败忽略，回退到后端回填
  }
  return false
}

// 投简历双渠道
const applyVisible = ref(false)
const applyJob = ref(null)
const applyLoading = ref(false)
const applyChannel = ref(null)
const applyingJobId = ref(null)
// 生成简历单岗位加载态隔离（按 jobId）
const generatingJobId = ref(null)

// 渠道补填弹窗
const applyPatchVisible = ref(false)
const applyPatchField = ref('company') // 仅官网投递补填招聘单位
const applyPatchValue = ref('')
const applyPatchSaving = ref(false)

// 求职准备（匹配评估/定制简历/求职信/ATS 检查）
const applicationVisible = ref(false)
const applicationJob = ref(null)


// 全量刷新岗位列表（仅更新数据，不动分类/搜索/排序状态）
// 新增岗位 1.5 秒高亮闪烁
function flashJob(id) {
  if (id == null) return
  jobFlashIds.value = new Set([...jobFlashIds.value, id])
  setTimeout(() => {
    const next = new Set(jobFlashIds.value)
    next.delete(id)
    jobFlashIds.value = next
  }, 1500)
}

onMounted(loadAll)

// —— 跨视图 / 跨标签同步：在别的页面改了素材库，回到本页时立即重新拉取并重算 ——
let _lastFocusSync = 0
function syncOnFocus() {
  const now = Date.now()
  if (now - _lastFocusSync < 4000) return
  _lastFocusSync = now
  if (analyzing.value) return
  refreshKnowledge().catch(() => {})
  loadLatest().catch(() => {})
}
function onVisibilityChange() {
  if (document.visibilityState === 'visible') syncOnFocus()
}
onMounted(() => {
  window.addEventListener('focus', syncOnFocus)
  document.addEventListener('visibilitychange', onVisibilityChange)
  window.addEventListener('focus', loadRecommendVersion)
  loadRecommendVersion()
})
onUnmounted(() => {
  window.removeEventListener('focus', syncOnFocus)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  window.removeEventListener('focus', loadRecommendVersion)
  if (recommendPollTimer) clearTimeout(recommendPollTimer)
  recommendPollTimer = null
  if (elfIntroTimer) clearTimeout(elfIntroTimer)
  elfIntroTimer = null
  stopAnalyzeClock()
  clearInterval(analyzePollTimer)
  stopResumeStagePoll()
})

async function loadAll() {
  // 优先读取本地历史结果直接渲染，避免刷新/跨页返回后闪现空状态
    loadRankCache()
  try {
    const [k, j] = await Promise.all([listKnowledge(), listJobs()])
    knowledgeList.value = k.data || []
    jobList.value = j.data || []
  } catch (e) {
    // 错误已由拦截器统一提示
  } finally {
    initialLoading.value = false
  }
  // 后端持久化结果回填（本地缓存兜底），数据版本变更才提示手动刷新
    await loadLatest()
  // 页面加载自动执行全量匹配计算：数据失效或尚无结果显示时静默重算
    if (jobList.value.length && knowledgeList.value.length && (dataDirty.value || !rankList.value.length)) {
    runAnalyze(true)
  }
}

// 进入页面时先回显最近一次评估结果，并校验数据版本一致性，不触发任何重算
async function loadLatest() {
  try {
    const [verify, latest] = await Promise.all([verifyMatch(), getLatestAnalysis()])
    const v = verify.data || {}
    const backendResult = latest.data && latest.data.length ? latest.data : []
    // 后端存有结果但数据版本已变更（resultCount>0 且 matched=false）才算「失效需刷新」
        const staleBackend = !v.matched && (v.resultCount || 0) > 0
    if (backendResult.length) {
      if (v.matched) {
        // 数据未变更：渲染后端结果，回填本地缓存，保持二者同一份数据
                _replaceRankList(backendResult)
        clearRecalcPending()
        saveRankCache()
        dataDirty.value = false
      } else if (staleBackend) {
        // 后端结果已失效：清空旧结果，提示手动刷新
        clearRankCache()
        rankList.value.splice(0)
        dataDirty.value = true
      }
      // 后端有结果但版本校验异常（matched 异常）：以本地为准，不强行清空
        } else if (staleBackend) {
      // 后端无结果但校验报告存有失效结果（异常兜底）：清空并提示刷新
      clearRankCache()
      rankList.value.splice(0)
      dataDirty.value = true
    } else if (rankList.value.length) {
      // 后端没有任何评估结果（尚未落库/已被清理），本地还有上次的结果：
      // 先继续展示，避免整个列表空成「—」让用户干等；同时标记待重算，
            // 由 loadAll 静默重算并用新结果覆盖（快速补充在后端缓存缺失时会自动入库+重算，不会再报错）。
            dataDirty.value = true
    } else {
      dataDirty.value = false
    }
  } catch (e) {
    // 后端接口异常：保留本地历史结果，不置错误
  }
}

function parseTime(t) {
  if (!t) return null
  const d = new Date(String(t).replace('T', ' ').replace(' ', 'T'))
  return Number.isNaN(d.getTime()) ? null : d.getTime()
}

async function refreshKnowledge() {
  const res = await listKnowledge()
  knowledgeList.value = res.data || []
}

// —— 必填信息 ——
async function submitBasicInfo() {
  if (!basicComplete.value) return
  basicSaving.value = true
  try {
    const res = await saveBasicInfo({
      name: basicForm.value.name.trim(),
      education: basicForm.value.education.trim(),
      school: basicForm.value.school.trim(),
      major: basicForm.value.major.trim()
    })
    const data = res.data || {}
    const records = data.records || []
    if (!records.length) {
      ElMessage.warning('请完善必填信息后再保存')
      return
    }
    const dups = data.duplicates || []
    if (dups.length) {
      openDuplicate(records, dups, null)
    } else {
      await doBatchSave(records, [])
      basicForm.value = { name: '', education: '', school: '', major: '' }
      positiveMoment.trigger({
        type: 'info_completed',
        title: '必填信息已补全',
        message: '基础资料齐活，匹配评估更准了～'
      })
    }
  } catch (e) {
    // 错误已由拦截器统一提示
  } finally {
    basicSaving.value = false
  }
}

// —— 自由添加 ——
async function submitFreeAdd() {
  const content = freeAddContent.value.trim()
  if (!content) return
  freeSaving.value = true
  try {
    const res = await splitKnowledge(content)
    const data = res.data || {}
    const records = data.records || []
    if (!records.length) {
      ElMessage.warning('未识别出可入库的内容，请补充更完整的信息')
      return
    }
    addMaterialVisible.value = false
    openPreview({
      title: '自由添加拆解预览',
      originalText: content,
      records,
      duplicates: data.duplicates || [],
      time: null
    })
  } catch (e) {
    // 错误已由拦截器统一提示
  } finally {
    freeSaving.value = false
  }
}

// —— 导入简历 ——
function beforeImport(file) {
  if (!/\.(pdf|doc|docx)$/i.test(file.name || '')) {
    ElMessage.warning('仅支持 PDF、DOC、DOCX 格式')
    return false
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning('文件大小不能超过20MB，请压缩后重新上传')
    return false
  }
  return true
}

// OCR 导入分阶段文案：处理文件 → 识别文字 → 格式化整理
const IMPORT_PHASE_TEXT = ['正在处理文件…', '正在识别文字内容…', '正在格式化整理…']

/** 启动分阶段进度模拟：按时间推进进度条与阶段文案，直到请求完成被外部停止。 */
function startPhaseTimer(phaseRef, progressRef) {
  phaseRef.value = 0
  progressRef.value = 0
  let elapsed = 0
  return setInterval(() => {
    elapsed += 200
    let p
    if (elapsed < 3000) p = (elapsed / 3000) * 15
    else if (elapsed < 15000) p = 15 + ((elapsed - 3000) / 12000) * 70
    else p = 85 + Math.min(7, (elapsed - 15000) / 1000)
    progressRef.value = Math.min(92, p)
    phaseRef.value = progressRef.value < 15 ? 0 : progressRef.value < 85 ? 1 : 2
  }, 200)
}

/** 停止分阶段计时器并把进度推到 100%。 */
function stopPhaseTimer(timer, phaseRef, progressRef) {
  if (timer) {
    clearInterval(timer)
  }
  phaseRef.value = 2
  progressRef.value = 100
}

async function doImport(opts) {
  const file = opts.file
  if (!file) return
  importing.value = true
  importMinimized.value = false
  importProgress.value = 0
  importPhase.value = 0
  importHint.value = /\.pdf$/i.test(file.name || '') ? '正在进行OCR识别，图片型简历耗时较长，可最小化后继续操作' : '正在解析并拆分简历…'
  importPhaseTimer = startPhaseTimer(importPhase, importProgress)
  try {
    const res = await importResume(file)
    stopPhaseTimer(importPhaseTimer, importPhase, importProgress)
    const data = res.data || {}
    if (!data.records || !data.records.length) {
      ElMessage.warning('未从简历中识别出可入库的内容，推荐上传文本型PDF/Word（更快更准），或更换清晰扫描件')
      return
    }
    const previewPayload = {
      title: data.fileName || '简历导入拆解预览',
      originalText: data.originalText || '',
      records: data.records,
      duplicates: data.duplicates || [],
      time: null
    }
    if (importMinimized.value) {
      // 最小化状态下缓存结果，通过通知引导用户打开预览
      importPendingPreview.value = previewPayload
      ElNotification({
        title: '简历识别完成',
        message: '点击查看拆解预览并确认入库',
        type: 'success',
        duration: 0,
        onClick: () => openPendingImportPreview()
      })
    } else {
      addMaterialVisible.value = false
      openPreview(previewPayload)
    }
  } catch (e) {
    stopPhaseTimer(importPhaseTimer, importPhase, importProgress)
    // 错误已由拦截器统一提示
  } finally {
    importing.value = false
  }
}

/** 从最小化通知中恢复并打开简历预览。 */
function openPendingImportPreview() {
  if (!importPendingPreview.value) return
  openPreview(importPendingPreview.value)
  importPendingPreview.value = null
  importMinimized.value = false
  addMaterialVisible.value = false
}

// —— 预览 / 疑似重复 ——
function openPreview(payload) {
  pendingSplit.value = payload
  previewVisible.value = true
}

async function confirmPreview() {
  const s = pendingSplit.value
  if (!s || !s.records || !s.records.length) {
    ElMessage.warning('没有可入库的记录')
    return
  }
  const dups = s.duplicates || []
  if (dups.length) {
    openDuplicate(s.records, dups, s.time || null)
    return
  }
  previewSaving.value = true
  try {
    const added = await doBatchSave(s.records, [], s.time || null)
    previewVisible.value = false
    pendingSplit.value = null
    if (added) {
      positiveMoment.trigger({
        type: 'material_added',
        title: `新增 ${added} 条素材`,
        message: '简历素材更充分，匹配度正在路上～'
      })
    }
  } finally {
    previewSaving.value = false
  }
}

function openDuplicate(records, dups, time) {
  pendingRecords.value = records
  duplicatePairs.value = dups.map((d) => ({ recordIndex: d.recordIndex, existing: d.existing, similarity: d.similarity, decision: 'both' }))
  duplicateTime.value = time || null
  previewVisible.value = false
  duplicateDialogVisible.value = true
}

function cancelDuplicates() {
  duplicateDialogVisible.value = false
  pendingRecords.value = []
  duplicatePairs.value = []
  duplicateTime.value = null
}

async function confirmDuplicates() {
  const records = pendingRecords.value
  const decisions = duplicatePairs.value.map((d) => ({
    recordIndex: d.recordIndex,
    decision: d.decision,
    existing: d.existing
  }))
  dupSaving.value = true
  try {
    const added = await doBatchSave(records, decisions, duplicateTime.value)
    if (added) {
      positiveMoment.trigger({
        type: 'material_added',
        title: `新增 ${added} 条素材`,
        message: '简历素材更充分，匹配度正在路上～'
      })
    }
    duplicateDialogVisible.value = false
    pendingRecords.value = []
    duplicatePairs.value = []
    duplicateTime.value = null
    pendingSplit.value = null
  } finally {
    dupSaving.value = false
  }
}

async function doBatchSave(records, decisions, time) {
  const items = (records || []).map((r) => ({
    type: r.type || '其他',
    content: stripTemporaryStatus((r.content || '').trim()),
    createTime: time || null
  }))
  for (const d of decisions) {
    const idx = d.recordIndex
    if (d.decision === 'new' && d.existing && d.existing.id) {
      if (items[idx]) items[idx].replaceId = d.existing.id
    } else if (d.decision === 'old') {
      items[idx] = null
    }
  }
  const finalItems = items.filter(Boolean)
  if (!finalItems.length) {
    ElMessage.info('已按选择保留原有记录，未新增')
    return 0
  }
  const res = await batchSaveKnowledge(finalItems)
  // 乐观更新：直接追加到本地，避免 listKnowledge 触发后端迁移/重分类 LLM 导致分数更新延迟
  const saved = res.data || []
  if (saved.length) knowledgeList.value = [...saved, ...knowledgeList.value]
  freeAddContent.value = ''
  const addedCount = saved.length || finalItems.length
  ElMessage.success(`已入库 ${addedCount} 条记录，岗位匹配分已立即更新`)
  return addedCount
}

// —— 我的目标岗位：粘贴导入 ——
async function submitJobPaste() {
  const content = jobPasteContent.value.trim()
  if (!content) return
  jobPasteSaving.value = true
  try {
    const res = await previewJob(content)
    const data = res.data || {}
    openJobPreview({ ...data, originalText: data.originalText || content })
  } catch (e) {
    // 错误已由拦截器统一提示
  } finally {
    jobPasteSaving.value = false
  }
}

// —— 我的目标岗位：链接导入 ——
async function submitJobLink() {
  const url = jobLinkUrl.value.trim()
  if (!url) return
  jobLinkSaving.value = true
  try {
    const res = await previewJobByUrl(url)
    const data = res.data || {}
    if (data && data.success) {
      jobLinkVisible.value = false
      jobLinkUrl.value = ''
      openJobPreview({ ...data.jobImport, jobLink: url })
      return
    }
    // 抓取失败：分类型提示 + 一键转粘贴导入（带入已识别部分内容）
        const msg = data.message || '链接抓取失败，请更换链接或改用粘贴导入'
    const partial = data.partialText || ''
    try {
      await ElMessageBox.confirm(msg, '链接导入失败', {
        confirmButtonText: '一键转为粘贴导入',
        cancelButtonText: '取消',
        type: 'warning'
      })
      jobLinkVisible.value = false
      if (partial) jobPasteContent.value = partial
      const el = jobPasteBox.value && (jobPasteBox.value.$el || jobPasteBox.value)
      if (el && el.scrollIntoView) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    } catch (e) {
      // 用户取消，停留在链接导入弹窗
    }
  } catch (e) {
    // 网络/业务错误已由拦截器统一提示
  } finally {
    jobLinkSaving.value = false
  }
}

// —— 我的目标岗位：导入岗位文档 ——
function beforeJobImport(file) {
  if (!/\.(pdf|doc|docx)$/i.test(file.name || '')) {
    ElMessage.warning('仅支持 PDF、DOC、DOCX 格式')
    return false
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning('文件大小不能超过20MB，请压缩后重新上传')
    return false
  }
  return true
}

async function doJobImport(opts) {
  const file = opts.file
  if (!file) return
  jobImporting.value = true
  jobImportMinimized.value = false
  jobImportProgress.value = 0
  jobImportPhase.value = 0
  jobImportHint.value = /\.pdf$/i.test(file.name || '')
    ? '正在进行OCR识别并标准化岗位信息，可最小化后继续操作'
    : '正在解析并标准化岗位信息…'
  jobImportPhaseTimer = startPhaseTimer(jobImportPhase, jobImportProgress)
  try {
    const res = await importJob(file)
    stopPhaseTimer(jobImportPhaseTimer, jobImportPhase, jobImportProgress)
    const data = res.data || {}
    if (!data.jobName && !data.jobRequirement) {
      ElMessage.warning('未从文件中识别出岗位信息，推荐上传文本型PDF/Word（更快更准），或更换清晰扫描件')
      return
    }
    if (jobImportMinimized.value) {
      jobImportPendingPreview.value = data
      ElNotification({
        title: '岗位识别完成',
        message: '点击查看标准化预览并确认入库',
        type: 'success',
        duration: 0,
        onClick: () => openPendingJobPreview()
      })
    } else {
      jobImportVisible.value = false
      openJobPreview(data)
    }
  } catch (e) {
    stopPhaseTimer(jobImportPhaseTimer, jobImportPhase, jobImportProgress)
    // 错误已由拦截器统一提示
  } finally {
    jobImporting.value = false
  }
}

/** 从最小化通知中恢复并打开岗位预览。 */
function openPendingJobPreview() {
  if (!jobImportPendingPreview.value) return
  openJobPreview(jobImportPendingPreview.value)
  jobImportPendingPreview.value = null
  jobImportMinimized.value = false
  jobImportVisible.value = false
}

// —— 我的目标岗位：预览确认 ——
function openJobPreview(data) {
  pendingJob.value = data
  jobPreviewVisible.value = true
}

async function confirmJobPreview() {
  const j = pendingJob.value
  if (!j) return
  if (!j.jobName || !j.jobRequirement) {
    ElMessage.warning('岗位名称与岗位要求不能为空')
    return
  }
  const companyName = (j.companyName || '').trim()
  const email = (j.email || '').trim()
  if (!companyName && !email) {
    ElMessage.warning('请至少填写招聘单位或招聘邮箱，方可添加入库')
    return
  }
  jobPreviewSaving.value = true
  try {
    // 无重复检测：确认后直接入库，入库时间取当前系统时间
        const res = await addJob({
      jobName: j.jobName,
      jobRequirement: j.jobRequirement,
      companyName,
      email,
      location: (j.location || '').trim(),
      salary: (j.salary || '').trim(),
      techStack: j.techStack || '',
      jobType: j.jobType || '',
      jobLink: j.jobLink || ''
    })
    if (res.data) {
      const created = res.data
      jobViewStore.setView('all')
      jobViewStore.clearTypes('all')
      jobFilterKeyword.value = ''
      jobSortKey.value = 'createTime'
      jobList.value.unshift(created)
      flashJob(created.id)
      nextTick(() => {
        const card = document.querySelector(`.job-card[data-job-id="${created.id}"]`)
        const reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches
        card?.scrollIntoView({ behavior: reduced ? 'auto' : 'smooth', block: 'center' })
      })
    }
    jobPreviewVisible.value = false
    pendingJob.value = null
    jobPasteContent.value = ''
    scheduleAnalyze()
    ElMessage.success('岗位已添加')
  } catch (e) {
    // 错误已由拦截器统一提示
  } finally {
    jobPreviewSaving.value = false
  }
}

// —— 岗位截图 OCR 识别 ——
async function uploadOcr(item, opts) {
  const file = opts.file
  if (!file) return
  ocrLoading.value = item.id
  try {
    const res = await ocrJob(item.id, file)
    item.techStack = res.data?.techStack || ''
    item.ocrText = res.data?.ocrText || ''
    scheduleAnalyze()
    ElMessage.success('截图识别完成，技术栈已自动回填')
    opts.onSuccess?.(res)
  } catch (e) {
    opts.onError?.(e)
  } finally {
    ocrLoading.value = null
  }
}

// —— 综合分析（异步任务 + 轮询 + 分批渲染）——
let analyzePollTimer = null
let analyzePolling = false
/** 计算期间又发生素材/岗位变更：本轮结束后立即补一轮，保证改动一定生效*/
let _pendingAnalyze = false
/** 「结果落后于当前数据」的自动补算次数，防止异常情况下无限重算 */
let _staleRunCount = 0

async function runAnalyze(silent = false) {
  if (analyzing.value) { _pendingAnalyze = true; return }
  if (!knowledgeList.value.length || !jobList.value.length) return
  // 记录重算前的分数快照，用于匹配分变化高亮提示
  prevScoresForDiff = {}
  ;(rankList.value || []).forEach((r) => { if (r.jobId != null) prevScoresForDiff[r.jobId] = num(r.score) })
  analyzing.value = true
  // ⚠️ 不清空 rankList！乐观 bump 写在里面的值先保留，
  //    等后端 poll 拿到权威结果再覆盖，避免中间 1-3s 空窗期显示 "—"
  analyzeProgress.value = { total: jobList.value.length, done: 0, jobIds: [], startedAt: Date.now() }
  analyzeSamples.value = []
  startAnalyzeClock()
  clearInterval(analyzePollTimer)
  try {
    const res = await analyzeAsync()
    const task = res.data || {}
    if (task.status === 'DONE' && task.results) {
      // 后端已是最新权威结果，直接覆盖本地缓存
      _replaceRankList(task.results)
      clearRecalcPending()
      dataDirty.value = false
      saveRankCache()
      return
    }
    if (!task.taskId) throw new Error('未获取到任务ID')
    await pollTask(task.taskId, silent)
    // 计算期间素材/岗位可能又被改过（后端用的是任务开始时的数据快照）：
    // 结束后校验一次数据版本，落后就立刻补算，避免「改了素材但分数/内容没变」
    await requeueIfStale()
  } catch (e) {
    // 评估失败时不把「重算中」占位挂死，回退到上一轮分数并提示重试
    clearRecalcPending()
    if (!silent) {
      // 非静默才清空，静默重算（由素材增删改触发）保留乐观值兜底
      rankList.value.splice(0)
    }
  } finally {
    analyzing.value = false
    clearInterval(analyzePollTimer)
    analyzePolling = false
    analyzeProgress.value = null
    analyzeSamples.value = []
    stopAnalyzeClock()
    if (_pendingAnalyze) {
      _pendingAnalyze = false
      scheduleAnalyze()
    }
  }
}

/** 本轮结果是否已落后于当前素材/岗位：落后则补算一轮（最多 2 次，避免异常自旋）。*/
async function requeueIfStale() {
  try {
    const res = await verifyMatch()
    const v = res.data || {}
    if (v.matched) {
      _staleRunCount = 0
      return
    }
    const stored = String(v.storedFingerprint || '')
    // 后端标记为 partial（有岗位评估失败）时不自动补算，避免无意义连跑；可由用户手动重试
    if ((v.resultCount || 0) > 0 && !stored.includes('|partial') && _staleRunCount < 2) {
      _staleRunCount++
      _pendingAnalyze = true
    }
  } catch (e) {
    // 版本校验失败不影响主流程
  }
}

async function pollTask(taskId, silent) {
  let attempts = 0
  const maxAttempts = 500 // 兜底：约 5 分钟（600ms 一次轮询）
  await new Promise((resolve) => {
    const finish = () => { clearInterval(analyzePollTimer); resolve() }
    analyzePollTimer = setInterval(() => {
      if (analyzePolling) return
      analyzePolling = true
      getAnalyzeTask(taskId)
        .then((res) => {
          const task = res.data || {}
          if (task.results && task.results.length) _replaceRankList(task.results)
          const nextDone = task.done != null ? task.done : (rankList.value.length || 0)
          const lastSample = analyzeSamples.value[analyzeSamples.value.length - 1]
          if (!lastSample || nextDone > lastSample.done) {
            analyzeSamples.value.push({ t: Date.now(), done: nextDone })
            if (analyzeSamples.value.length > 30) analyzeSamples.value.shift()
          }
          const prevStartedAt = analyzeProgress.value?.startedAt || 0
          analyzeProgress.value = {
            total: task.total || 0,
            done: nextDone,
            jobIds: Array.isArray(task.jobIds) ? task.jobIds : [],
            startedAt: task.startedAt || prevStartedAt || Date.now(),
          }
          if (task.status === 'DONE') {
            // 本轮结果是新的权威数据，直接覆盖本地缓存
            _replaceRankList(task.results || [])
            clearRecalcPending()
            dataDirty.value = false
            saveRankCache()
            // 有岗位没算出来（单岗失败/超时）：已出结果照常显示，提示可再次重试
            if ((task.done || 0) < (task.total || 0)) {
              ElMessage.warning('部分岗位评估失败，已显示成功的结果，可稍后重新评估补齐')
            }
            finish()
          } else if (task.status === 'ERROR') {
            finish()
          } else if (attempts >= maxAttempts) {
            // 超时兜底：保留已出结果，后台继续计算，完成后可再次刷新
            finish()
          }
        })
        .catch(() => {
          // 单次轮询瞬时失败忽略，由 attempts 上限兜底
          if (attempts >= maxAttempts) {
            finish()
          }
        })
        .finally(() => { analyzePolling = false })
      attempts++
    }, 600)
  })
}

function num(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function clamp(v) {
  return Math.max(0, Math.min(100, num(v)))
}

function dash(v) {
  if (v === null || v === undefined || v === '') return '—'
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(1) : '—'
}

function hasDims(r) {
  return r.skillScore != null || r.experienceScore != null || r.educationScore != null || r.fitScore != null
}

function dimItems(r) {
  return [
    { label: '技能', value: r.skillScore },
    { label: '经历', value: r.experienceScore },
    { label: '学历', value: r.educationScore },
    { label: '契合', value: r.fitScore },
  ].filter((d) => d.value != null).map((d) => ({ label: d.label, value: num(d.value) }))
}

function dimFillClass(v) {
  const n = num(v)
  if (n >= 80) return 'is-high'
  if (n >= 60) return 'is-mid'
  return 'is-low'
}

// 排名结果定位到目标岗位：优先按 jobId，其次按岗位名
function findJob(r) {
  if (r.jobId != null) {
    const byId = jobList.value.find((j) => j.id === r.jobId)
    if (byId) return byId
  }
  return jobList.value.find((j) => j.jobName === r.jobName)
}

const resumeTemplates = ref([])
const activeTemplateId = ref('')

const activeTemplate = computed(() =>
  resumeTemplates.value.find((t) => t.id === activeTemplateId.value) || resumeTemplates.value[0] || null
)

const paperStyle = computed(() => {
  const t = activeTemplate.value
  if (!t) return {}
  return {
    '--resume-accent': t.primary || '#17505c',
    '--resume-secondary': t.secondary || '#d3e5df',
    '--resume-bg': t.background || '#ffffff',
    '--resume-text': t.text || '#141414',
    '--resume-heading-font': t.headingFont || 'SimHei, Microsoft YaHei, sans-serif',
    '--resume-body-font': t.bodyFont || t.headingFont || 'SimHei, Microsoft YaHei, sans-serif'
  }
})

// 当前简历版式：single / sidebar / banner / serif
const resumeLayout = computed(() => activeTemplate.value?.layout || 'single')

// 简历正文各区块（用于单页/页眉版式统一渲染）
const resumeSections = computed(() => {
  const r = resume.value
  if (!r) return []
  const sec = []
  if (r.selfEvaluation) sec.push({ key: 'self', title: '自我评价', kind: 'text', text: r.selfEvaluation })
  if (r.skills && r.skills.length) sec.push({ key: 'skills', title: '核心技能', kind: 'skills', items: r.skills })
  if (r.experiences && r.experiences.length) sec.push({ key: 'exp', title: '实习/项目经历', kind: 'list', items: r.experiences })
  if (r.projectHighlights && r.projectHighlights.length) sec.push({ key: 'ph', title: '项目亮点', kind: 'list', items: r.projectHighlights })
  if (r.education && r.education.length) sec.push({ key: 'edu', title: '教育背景', kind: 'list', items: r.education })
  if (r.certificates && r.certificates.length) sec.push({ key: 'cer', title: '证书奖项', kind: 'list', items: r.certificates })
  return sec
})

// —— 定制简历 ——
const resumeVisible = ref(false)
const resumeLoading = ref(false)
const resumeStage = ref('')
let resumeStageTimer = null
const resumeError = ref('')
const resume = ref(null)
const currentJobName = ref('')
const currentJobId = ref(null)
const missingDismissed = ref(false)
const exporting = ref(false)
const resumePaperRef = ref(null)
const photoUploading = ref(false)
const resumeFilename = ref('')

/** 从目标岗位卡片一键进入 AI 面试，自动带入岗位 JD 与技术栈 */
function startAiInterview(job) {
  if (!job) {
    ElMessage.warning('未找到对应岗位')
    return
  }
  linkage.setContext({
    from: 'jobmatch',
    jobTitle: job.jobName,
    jdText: job.jobRequirement || '',
    techStack: job.techStack || '',
    resumeText: ''
  })
  router.push('/interview')
}

/** 从目标岗位卡片一键进入 AI 笔试，自动带入岗位 JD 与技术栈 */
function startAiExam(job) {
  if (!job) {
    ElMessage.warning('未找到对应岗位')
    return
  }
  linkage.setContext({
    from: 'jobmatch',
    jobTitle: job.jobName,
    jdText: job.jobRequirement || '',
    techStack: job.techStack || '',
    resumeText: ''
  })
  router.push('/exam')
}

/** 与后端行业准入保持一致的轻量判断，用于提前禁用非 IT 岗位的笔试入口 */
function isItJob(job) {
  const title = String(job?.jobName || '').toLowerCase()
  if (/(产品经理|产品运营|运营|销售|市场|商务|人力|hr|行政|财务|会计|法务|客服|教师|教研|医生|护士|采购|供应链|物流|设计师|视觉设计|交互设计|ui设计|品牌|公关|文案|编辑)/i.test(title)) {
    return false
  }
  return /(开发|研发|程序员|前端|后端|服务端|全栈|java|python|golang|c\+\+|算法|机器学习|深度学习|大模型|ai|人工智能|数据|测试|qa|运维|sre|devops|安全|渗透|网络|架构|嵌入式|android|ios|客户端|移动端|计算机|软件|互联网|云|dba|数据库|技术支持|实施工程师|工程师)/i.test(title)
}

function startResumeStagePoll(jobId) {
  stopResumeStagePoll()
  resumeStageTimer = setInterval(async () => {
    try {
      const res = await getGenerationProgress(jobId, 'resume')
      const stage = res.data?.data?.stage
      if (stage && stage !== 'IDLE') resumeStage.value = stage
    } catch (e) {
      // 进度轮询失败不打断主生成请求
    }
  }, 800)
}

function stopResumeStagePoll() {
  if (resumeStageTimer) {
    clearInterval(resumeStageTimer)
    resumeStageTimer = null
  }
  resumeStage.value = ''
}

async function openResume(r) {
  if (generatingJobId.value) return
  if (!knowledgeList.value.length) {
    ElMessage.warning('请先在我的简历素材中添加素材，再生成定制简历')
    openSidebarFocus('free')
    return
  }
  const job = findJob(r)
  if (!job) {
    ElMessage.warning('未找到对应岗位，请先在我的收藏中添加该岗位')
    return
  }
  generatingJobId.value = job.id
  currentJobName.value = r.jobName
  currentJobId.value = job.id
  resume.value = null
  resumeError.value = ''
  missingDismissed.value = false
  resumeVisible.value = true
  resumeLoading.value = true
  startResumeStagePoll(job.id)
  loadResumeTemplates()
  const started = performance.now()
  try {
    const res = await generateResume(job.id)
    const data = res.data || null
    if (data) {
      data.skills = data.skills || []
      data.experiences = data.experiences || []
      data.projectHighlights = data.projectHighlights || []
      data.education = data.education || []
      data.certificates = data.certificates || []
      if (!data.photo) data.photo = ''
    }
    resume.value = data
    resumeFilename.value = defaultResumeFilename()
    if (resume.value) {
      ElMessage.success('定制简历已生成')
      recordGenerationDuration('tailor', (performance.now() - started) / 1000)
    }
  } catch (e) {
    resume.value = null
    resumeError.value = '简历生成失败，请稍后重试'
  } finally {
    resumeLoading.value = false
    stopResumeStagePoll()
    generatingJobId.value = null
  }
}

function selectResumeTemplate(t) {
  if (!t || t.id === activeTemplateId.value) return
  activeTemplateId.value = t.id
  ElMessage({ type: 'success', message: `已切换模板：${t.name}`, duration: 1500 })
}

function applyResumeTemplates(list) {
  resumeTemplates.value = list
  if (!list.some((t) => t.id === activeTemplateId.value)) {
    activeTemplateId.value = list[0]?.id || ''
  }
}

async function loadResumeTemplates() {
  const cached = readCachedResumeTemplates()
  if (cached) applyResumeTemplates(cached)
  try {
    applyResumeTemplates(await fetchResumeTemplates())
  } catch (e) {
    if (!cached) resumeTemplates.value = []
  }
}

function defaultResumeFilename() {
  const who = (resume.value?.name || '候选人').toString().trim() || '候选人'
  const job = (currentJobName.value || '岗位').toString().trim() || '岗位'
  return `${job}-${who}-简历`
}

// —— 证件照：3:4 居中裁剪 + JPEG 压缩 ——
function onPhotoChange(e) {
  const file = e.target.files && e.target.files[0]
  e.target.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请上传图片格式的证件照')
    return
  }
  photoUploading.value = true
  cropAndCompressPhoto(file)
    .then((dataUrl) => {
      resume.value.photo = dataUrl
      ElMessage.success('证件照已就位')
    })
    .catch(() => ElMessage.error('证件照处理失败，请重试'))
    .finally(() => { photoUploading.value = false })
}

function removePhoto() {
  resume.value.photo = ''
  ElMessage.success('已删除证件照')
}

// —— 在线编辑：增删条目/ 调整顺序 ——
function moveItem(arr, i, dir) {
  const j = i + dir
  if (j < 0 || j >= arr.length) return
  const tmp = arr[i]
  arr[i] = arr[j]
  arr[j] = tmp
}
function removeListItem(arr, i) {
  arr.splice(i, 1)
}
function addListItem(arr) {
  arr.push('')
}
function removeSkillItem(i) {
  resume.value.skills.splice(i, 1)
}
function addSkillItem() {
  resume.value.skills.push({ name: '', matched: false })
}

// 求职准备：打开匹配评估/定制简历/求职信/ATS 检查弹窗
function closeAssistantAndOpenMaterials() {
  applicationVisible.value = false
  openSidebarFocus('free')
}

function openApplication(r) {
  const job = findJob(r)
  if (!job) {
    ElMessage.warning('未找到对应岗位，请先在我的收藏中添加该岗位')
    return
  }
  applicationJob.value = { id: job.id, jobName: job.jobName || r.jobName, companyName: job.companyName || r.companyName }
  applicationVisible.value = true
}

// 投简历双渠道：点击后识别公司+邮箱，弹出渠道选择弹窗
async function openApply(r) {
  const job = findJob(r)
  if (!job) {
    ElMessage.warning('未找到对应岗位，请先在我的收藏中添加该岗位')
    return
  }
  applyJob.value = r
  applyingJobId.value = job.id
  applyChannel.value = null
  applyLoading.value = true
  applyPatchVisible.value = false
  applyPatchValue.value = ''
  applyVisible.value = true
  try {
    const res = await recognizeApplyChannel(job.id)
    applyChannel.value = res.data || null
  } catch (e) {
    applyChannel.value = null
    ElMessage.error('读取投递信息失败，请稍后重试')
  } finally {
    applyLoading.value = false
    applyingJobId.value = null
  }
}

// 打开渠道补填弹窗（仅官网投递补填招聘单位）
function openApplyPatch() {
  const cur = applyChannel.value || {}
  applyPatchField.value = 'company'
  applyPatchValue.value = cur.companyName || ''
  applyPatchVisible.value = true
}

// 确认补填并同步入库，实时激活官网投递渠道
async function confirmApplyPatch() {
  const value = applyPatchValue.value.trim()
  if (!value) {
    ElMessage.warning('请输入招聘单位')
    return
  }
  const job = findJob(applyJob.value)
  if (!job) return
  applyPatchSaving.value = true
  try {
    const res = await patchApplyChannel({
      jobId: job.id,
      companyName: value,
      email: applyChannel.value?.email || ''
    })
    applyChannel.value = res.data || null
    // 同步更新岗位列表，保证后续打开默认显示
    const idx = jobList.value.findIndex((i) => i.id === job.id)
    if (idx >= 0 && res.data) {
      jobList.value[idx] = { ...jobList.value[idx], companyName: res.data.companyName }
    }
    applyPatchVisible.value = false
    ElMessage.success('已补填并激活渠道')
  } catch (e) {
    ElMessage.error('补填失败，请稍后重试')
  } finally {
    applyPatchSaving.value = false
  }
}

// 官网投递：跳转浏览器（命中模板库直达官方门户，否则搜索引擎兜底）
const applyDetailLoading = ref(false)

function celebrateApply() {
  positiveMoment.trigger({
    type: 'applied',
    title: '投递进行时',
    message: '已为你打开岗位详情页，愿好消息如约而至～'
  })
}

/** 岗位页确实打开后再生成投递草稿，避免弹窗被拦截时产生无效草稿。 */
function triggerApplyDraft(jobId) {
  createApplyDraft(jobId)
    .then(() => window.dispatchEvent(new CustomEvent('job-application-draft-created')))
    .catch(() => {})
}

async function openPortal() {
  const job = findJob(applyJob.value)
  if (!job) {
    ElMessage.warning('未找到对应岗位')
    return
  }
  if (applyDetailLoading.value) return
  // 已有原始链接：直接打开，不再等待检索
  const direct = String(applyChannel.value?.jobLink || '').trim()
  if (direct) {
    const win = window.open(direct, '_blank')
    if (win) {
      win.opener = null
      triggerApplyDraft(job.id)
      celebrateApply()
    } else {
      ElMessage.warning('浏览器阻止了新窗口，请允许弹窗后重试')
    }
    return
  }
  // 无链接：先在用户手势内打开带提示的页签（避免弹窗拦截和空白页），检索完成后直接导航
  const pending = window.open('', '_blank')
  if (pending) {
    try {
      pending.document.write('<!doctype html><meta charset="utf-8"><title>正在查找岗位详情页…</title>'
        + '<body style="font-family:sans-serif;padding:32px;color:#333">'
        + '<p>正在查找该岗位的详情页，请稍候…</p></body>')
      pending.document.close()
    } catch (e) {
      // 无法写入提示时保持空白，不影响后续跳转
    }
  }
  applyDetailLoading.value = true
  try {
    const res = await resolveApplyDetailLink(job.id)
    const url = String(res.data || '').trim()
    if (!url) {
      // 检索失败不跳公司官网/搜索页，引导用户自行搜索
      if (pending && !pending.closed) pending.close()
      ElMessage.warning('暂无可跳转的岗位详情链接，请自行搜索该岗位投递')
      return
    }
    // 本次会话内后续点击直接复用，避免重复检索
    applyChannel.value = { ...applyChannel.value, jobLink: url, jobLinkAvailable: true }
    if (pending && !pending.closed) {
      pending.opener = null
      pending.location.href = url
      triggerApplyDraft(job.id)
    } else {
      const win = window.open(url, '_blank')
      if (win) {
        win.opener = null
        triggerApplyDraft(job.id)
      } else {
        ElMessage.warning('浏览器阻止了新窗口，请允许弹窗后重试')
      }
    }
    celebrateApply()
  } catch (e) {
    if (pending && !pending.closed) pending.close()
    // 错误已由拦截器统一提示
  } finally {
    applyDetailLoading.value = false
  }
}

function fillMissingSkills() {
  if (!resume.value) return
  const skills = resume.value.skills || []
  const missing = resume.value.missingSkills || []
  missing.forEach((m) => {
    skills.push({ name: `${m.name}（学习中）`, matched: false })
  })
  resume.value.skills = skills
  resume.value.missingSkills = []
  missingDismissed.value = true
  ElMessage.success('已补齐至简历，预览已更新')
}

function skipFill() {
  missingDismissed.value = true
}

function normalizedFilename(ext) {
  const base = (resumeFilename.value || defaultResumeFilename()).toString().trim() || defaultResumeFilename()
  return base.toLowerCase().endsWith(ext) ? base : base + ext
}

function buildExportPayload() {
  const clean = (arr) => (arr || []).filter((x) => x && String(x).trim())
  return {
    jobName: currentJobName.value,
    name: resume.value.name,
    photo: resume.value.photo || '',
    basicInfo: resume.value.basicInfo,
    selfEvaluation: resume.value.selfEvaluation,
    skills: (resume.value.skills || []).filter((s) => s.name && String(s.name).trim()),
    experiences: clean(resume.value.experiences),
    projectHighlights: clean(resume.value.projectHighlights),
    education: clean(resume.value.education),
    certificates: clean(resume.value.certificates)
  }
}

// 确认下载入口：选择导出格式
function onConfirmDownload(format) {
  if (format === 'pdf') exportResumePdf()
  else if (format === 'docx') exportResumeWord()
}

async function exportResumePdf() {
  if (!resume.value || !resumePaperRef.value) return
  exporting.value = true
  try {
    const [{ default: html2canvas }, { jsPDF }] = await Promise.all([
      import('html2canvas'),
      import('jspdf')
    ])
    const el = resumePaperRef.value
    const canvas = await html2canvas(el, { scale: 2, useCORS: true, backgroundColor: '#ffffff' })
    const imgData = canvas.toDataURL('image/png')
    const pdf = new jsPDF('p', 'mm', 'a4')
    const pageWidth = pdf.internal.pageSize.getWidth()
    const pageHeight = pdf.internal.pageSize.getHeight()
    const imgWidth = pageWidth
    const imgHeight = (canvas.height * imgWidth) / canvas.width
    let heightLeft = imgHeight
    let position = 0
    pdf.addImage(imgData, 'PNG', 0, position, imgWidth, imgHeight)
    heightLeft -= pageHeight
    while (heightLeft > 0) {
      position -= pageHeight
      pdf.addPage()
      pdf.addImage(imgData, 'PNG', 0, position, imgWidth, imgHeight)
      heightLeft -= pageHeight
    }
    pdf.save(normalizedFilename('.pdf'))
    ElMessage.success('PDF 导出成功')
  } catch (e) {
    ElMessage.error('PDF 导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}

async function exportResumeWord() {
  if (!resume.value) return
  exporting.value = true
  try {
    const res = await exportResumeDocx(buildExportPayload(), activeTemplateId.value, resumeFilename.value)
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = normalizedFilename('.docx')
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('Word 导出成功')
  } catch (e) {
    ElMessage.error('Word 导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped>
.jobmatch-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}

.page-header {
  padding: 4px 2px 0;
}
.page-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0;
}
.page-sub {
  margin: 6px 0 0;
  color: var(--color-text-2);
  font-size: 13px;
}

/* 右上角固定悬浮入口：仅图标，hover 显示文字提示 */
.material-fab-slot {
  position: fixed;
  top: 72px;
  right: 24px;
  z-index: 900;
}
.material-fab {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  padding: 0;
  color: #ffffff;
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  border-radius: 999px;
  box-shadow: var(--shadow-teal);
  cursor: pointer;
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s var(--ease-out-expo);
}
.material-fab:hover {
  background: var(--color-primary-mid);
  border-color: var(--color-primary-mid);
  transform: translateY(-1px);
  box-shadow: 0 8px 22px rgba(31, 95, 107, 0.28);
}
.material-fab:active {
  transform: translateY(0) scale(0.96);
}
.material-fab:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 3px;
}
.fab-enter-active,
.fab-leave-active {
  transition: opacity 0.22s ease, transform 0.28s var(--ease-out-expo);
}
.fab-enter-from,
.fab-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.92);
}
.fab-leave-active {
  pointer-events: none;
}
@media (max-width: 720px) {
  .material-fab-slot {
    top: 68px;
    right: 16px;
  }
  .material-fab {
    width: 40px;
    height: 40px;
  }
}

.top-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
  align-items: stretch;
}
@media (max-width: 900px) {
  .top-grid {
    grid-template-columns: 1fr;
  }
}

.right-col {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
}


.panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.match-dirty {
  font-size: 12px;
  color: var(--color-warning);
  background: var(--color-warning-bg);
  border: 1px solid #f0d9a8;
  border-radius: 999px;
  padding: 3px 12px;
  white-space: nowrap;
}

.module-hint {
  font-size: 12px;
  line-height: 1.7;
  color: var(--color-text-2);
  background: var(--color-surface-muted);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
}
.module-hint::before {
  content: '提示';
  display: inline-block;
  margin-right: 8px;
  padding: 0 6px;
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-2);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 4px;
  line-height: 18px;
  vertical-align: 1px;
}

.feed-box {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1 1 auto;
}
.empty-tip--action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.empty-tip {
  padding: 26px 18px;
  text-align: center;
  color: var(--color-text-2);
  font-size: 13px;
  background: var(--color-surface-muted);
  border: 1px dashed var(--color-border-strong);
  border-radius: 10px;
  line-height: 1.7;
}

.info-card,
.job-card {
  position: relative;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 14px;

}

.info-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--color-text);
  font-size: 13px;
  line-height: 1.7;
}
.info-meta {
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.info-time,
.job-time {
  color: var(--color-text-2);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.job-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.job-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
  cursor: pointer;
}
.job-name:hover {
  text-decoration: underline;
  color: var(--color-accent);
}
.job-name-hit {
  background: var(--color-warning-bg);
  color: inherit;
  border-radius: 3px;
  padding: 0 1px;
}
.job-select {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
}
.job-select input,
.select-all-toggle input {
  appearance: none;
  -webkit-appearance: none;
  flex: none;
  width: 16px;
  height: 16px;
  margin: 0;
  border: 1px solid var(--color-border-strong);
  border-radius: 4px;
  background: var(--color-surface);
  display: inline-block;
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease;
}
.job-select input:hover,
.select-all-toggle input:hover {
  border-color: var(--color-accent);
}
.job-select input:checked,
.select-all-toggle input:checked {
  border-color: var(--color-accent);
  background-color: var(--color-accent);
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16'%3E%3Cpath d='M3.5 8.5l3 3 6-6' fill='none' stroke='white' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E");
  background-size: 12px 12px;
  background-position: center;
  background-repeat: no-repeat;
}
.job-select input:focus-visible,
.select-all-toggle input:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}
.job-detail-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 16px;
}
.job-detail-field {
  display: flex;
  gap: 8px;
  font-size: 13px;
}
.job-detail-field .f-label {
  color: var(--color-text-2);
  white-space: nowrap;
}
.job-detail-field .f-value {
  color: var(--color-text);
  font-weight: 600;
  word-break: break-all;
}
.job-detail-req {
  margin-top: 14px;
}
.job-detail-req-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 6px;
}
.job-detail-req-text {
  margin: 0;
  padding: 10px 12px;
  background: var(--color-surface-muted);
  border-radius: 6px;
  font-size: 12.5px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--color-text);
  max-height: 320px;
  overflow-y: auto;
}
.job-head-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}
.job-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  margin-top: 4px;
}
.job-meta-item {
  font-size: 12px;
  color: var(--color-text-3);
  line-height: 1.5;
}
.job-tags,
.rank-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 20px;
}
.tag-tech {
  background: var(--color-surface-muted);
  color: var(--color-accent);
}
.tag-req {
  background: var(--color-surface-muted);
  color: var(--color-accent);
}
.job-require {
  margin: 10px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--color-text-2);
  font-size: 13px;
  line-height: 1.7;
}
.job-foot {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
}
.job-foot .material-time {
  margin-top: 0;
}
.job-ocr {
  margin-top: 12px;
  padding: 10px 12px;
  background: var(--color-surface-muted);
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-sm);
}
.job-ocr-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-accent);
  margin-bottom: 4px;
}
.job-ocr-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--color-text-2);
  font-size: 12px;
  line-height: 1.6;
}
.tag-type {
  flex-shrink: 0;
  background: var(--color-surface-muted);
  color: var(--color-text-2);
  border: 1px solid var(--color-border);
}
.job-card.is-latest {
  border-color: var(--color-accent);
  background: var(--color-accent-soft);

}
.job-card.is-flash {
  border-color: var(--color-accent);
  animation: job-flash 1.5s ease;
}
@keyframes job-flash {
  0%, 100% { background: var(--color-surface); }
  20%, 50%, 80% { background: var(--color-accent-soft); }
}

/* —— 我的目标岗位：AI 提取结果 —— */
.extract-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  flex: 1 1 auto;
}
.extract-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.extract-label {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--color-text-2);
}
.extract-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
  word-break: break-word;
}
.extract-tech {
  font-size: 13px;
  color: var(--color-text-2);
  word-break: break-word;
}
.extract-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding-top: 10px;
  border-top: 1px solid var(--color-border);
}
.extract-require {
  max-height: 260px;
}

/* —— 查询弹窗 —— */
.lib-count {
  font-size: 13px;
  color: var(--color-text-2);
}
/* —— 排名结果 —— */
.result-panel {
  gap: 16px;
}
.analyzing {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px 20px;
  color: var(--color-accent);
  font-size: 14px;
}
.analyzing--inline {
  justify-content: flex-start;
  padding: 14px 4px;
  font-size: 13px;
}
.spin {
  width: 20px;
  height: 20px;
  border: 2px solid var(--color-border);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: rotate 0.8s linear infinite;
}
@keyframes rotate {
  to {
    transform: rotate(360deg);
  }
}

/* 逐岗评估进度条：边算边显示，明确告知预计还剩多少 */
.analyze-banner {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
  margin: 0 0 10px;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-accent-soft);
  color: var(--color-accent);
  font-size: 13px;
}

/* 空素材提示：素材为空时匹配度恒为 0，先引导用户补素材 */
.material-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: 0 0 10px;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface-muted);
  color: var(--color-text-2);
  font-size: 12px;
}
.analyze-banner-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 8px;
  min-width: 0;
}
.analyze-banner .spin {
  width: 14px;
  height: 14px;
  flex: 0 0 auto;
}
.analyze-banner-text {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.analyze-banner-count {
  flex: 0 0 auto;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
.analyze-banner-meta {
  flex: 0 0 auto;
  color: var(--color-text-3);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.analyze-banner-track {
  flex: none;
  width: 100%;
  height: 8px;
  border-radius: 999px;
  background: var(--color-surface-muted);
  overflow: hidden;
}
.analyze-banner-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--color-accent);
  transition: width 0.35s ease;
}
.score-pending {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.score-pending .spin {
  width: 22px;
  height: 22px;
  border-color: var(--color-border);
  border-top-color: var(--color-accent);
}
.miss-pending {
  color: var(--color-accent);
}
/* 素材变更后分数位占位：不显示可能过期的旧分数，算完直接出权威分数 */
.job-score-big.is-recalc {
  display: flex;
  align-items: center;
  justify-content: flex-start;
}
.recalc-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-3);
}
.recalc-label .spin {
  width: 16px;
  height: 16px;
  border-color: var(--color-border);
  border-top-color: var(--color-accent);
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.rank-card {
  display: flex;
  gap: 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 16px;

}
.rank-badge {
  flex-shrink: 0;
  width: 72px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  border-radius: 10px;
  background: var(--color-surface-muted);
  padding: 12px 8px;
}
.rank-no {
  font-size: 12px;
  color: var(--color-text-2);
  font-variant-numeric: tabular-nums;
}
.rank-score {
  font-size: 26px;
  font-weight: 600;
  color: var(--color-text);
  line-height: 1;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}
.rank-body {
  flex: 1;
  min-width: 0;
}
.rank-job {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
}
.rank-summary {
  margin: 10px 0 0;
  color: var(--color-text-2);
  font-size: 13px;
  line-height: 1.6;
}

/* —— 四维子分数条 —— */
.dim-bars {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}
.dim-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.dim-label {
  flex-shrink: 0;
  width: 34px;
  font-size: 12px;
  color: var(--color-text-3);
}
.dim-track {
  flex: 1;
  height: 8px;
  border-radius: 999px;
  background: var(--color-surface-muted);
  overflow: hidden;
}
.dim-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.5s var(--ease-out-expo);
}
.dim-fill.is-high {
  background: var(--color-accent);
}
.dim-fill.is-mid {
  background: var(--color-text-3);
}
.dim-fill.is-low {
  background: var(--color-border-strong);
}
.dim-val {
  flex-shrink: 0;
  width: 38px;
  text-align: right;
  font-size: 12px;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}

/* —— 硬性要求缺口 —— */
.hard-misses {
  margin-top: 12px;
  padding: 10px 12px;
  background: var(--color-danger-bg);
  border: 1px solid #f2c9c4;
  border-radius: var(--radius-sm);
}
.hard-miss-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-danger);
  margin-bottom: 6px;
}
.hard-miss {
  font-size: 12px;
  color: var(--color-danger);
  line-height: 1.6;
  padding-left: 12px;
  position: relative;
}
.hard-miss::before {
  content: '✕';
  position: absolute;
  left: 0;
  color: var(--color-danger);
  font-weight: 600;
}

/* —— 匹配详情：岗位要求 ↔ 简历内容一一对应 —— */
.match-details {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.match-detail-group {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.match-detail-dim {
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-2);
  background: var(--color-surface-muted);
  border-bottom: 1px solid var(--color-border);
}
.match-detail-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--color-border);
}
.match-detail-row:last-child {
  border-bottom: none;
}
.match-detail-row.is-hit {
  background: var(--color-accent-soft);
}
.match-detail-row.is-miss {
  background: var(--color-danger-bg);
}
.match-detail-icon {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  line-height: 18px;
  text-align: center;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 600;
  margin-top: 1px;
}
.match-detail-row.is-hit .match-detail-icon {
  color: var(--color-accent);
  background: var(--color-accent-soft);
  border: 1px solid var(--color-border);
}
.match-detail-row.is-miss .match-detail-icon {
  color: var(--color-danger);
  background: var(--color-danger-bg);
  border: 1px solid #f2c9c4;
}
.match-detail-text {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  line-height: 1.55;
  word-break: break-word;
}
.match-detail-row.is-hit .match-detail-text {
  color: var(--color-accent);
}
.match-detail-row.is-miss .match-detail-text {
  color: var(--color-danger);
  font-weight: 600;
}
.match-detail-type {
  margin-left: 6px;
  display: inline-block;
  padding: 0 6px;
  font-size: 11px;
  line-height: 16px;
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-accent);
  border: 1px solid var(--color-border);
}

/* —— 已匹配 / 待补足胶囊 —— */
.chip-group {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
.chip-label {
  font-size: 12px;
  font-weight: 600;
  margin-right: 2px;
}
.chip-label--matched {
  color: var(--color-accent);
}
.chip-label--gaps {
  color: var(--color-warning);
}
.chip {
  display: inline-block;
  max-width: 100%;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 1.4;
}
.chip--matched {
  background: var(--color-accent-soft);
  color: var(--color-accent);
  border: 1px solid var(--color-border);
}
.chip--gaps {
  background: var(--color-warning-bg);
  color: var(--color-warning);
  border: 1px solid #f0d9a8;
}

.rank-analysis-toggle {
  margin-top: 10px;
}
.text-link {
  border: none;
  background: none;
  padding: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-accent);
  cursor: pointer;
}
.rank-analysis {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.rank-analysis p {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-2);
  line-height: 1.6;
}
.rank-analysis b {
  color: var(--color-accent);
}

/* 视觉层级：第1名强调 / 2-3名次之 / 其余正常 */
.rank-card.rank-first {
  border-color: var(--color-accent);
  background: var(--color-surface);

  padding: 20px;
}
.rank-card.rank-first .rank-job {
  font-size: 18px;
}
.rank-card.rank-first .rank-score {
  color: var(--color-text);
  font-size: 30px;
}
.rank-card.rank-top {
  border-color: var(--color-border-strong);
}
.rank-card.rank-top .rank-score {
  color: var(--color-text-2);
}

.rank-actions {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
  padding: 16px 20px;
  border-left: 1px solid var(--color-border);
  flex-shrink: 0;
  align-items: stretch;
}

.rank-actions .button {
  width: 100%;
  white-space: nowrap;
}

.rank-footer {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

/* —— 对比视图 —— */
.compare-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.compare-view :deep(.job-compare-chart) {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 14px 16px;

}
.compare-cards {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.compare-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 16px;

}
.compare-card-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.compare-job {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}
.compare-score {
  margin-left: auto;
  font-size: 20px;
  font-weight: 600;
  color: var(--color-accent);
  font-variant-numeric: tabular-nums;
}

.verdict {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 20px;
}
.verdict-sm {
  font-size: 11px;
  padding: 1px 8px;
  line-height: 18px;
}
.verdict.is-qualified {
  background: var(--color-accent-soft);
  color: var(--color-accent);
  border: 1px solid var(--color-border);
}
.verdict.is-unqualified {
  background: var(--color-danger-bg);
  color: var(--color-danger);
  border: 1px solid #f2c9c4;
}
.verdict.is-unknown {
  background: var(--color-surface-muted);
  color: var(--color-text-3);
  border: 1px solid var(--color-border);
}

.compare-block {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}
.compare-block-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-2);
  margin-bottom: 8px;
}
.compare-miss {
  font-size: 12px;
  color: var(--color-danger);
  line-height: 1.7;
  padding-left: 14px;
  position: relative;
}
.compare-miss::before {
  content: '✕';
  position: absolute;
  left: 0;
  color: var(--color-danger);
  font-weight: 600;
}
.compare-ok {
  font-size: 12px;
  color: var(--color-accent);
  line-height: 1.7;
  padding-left: 14px;
  position: relative;
}
.compare-ok::before {
  content: '✓';
  position: absolute;
  left: 0;
  color: var(--color-accent);
  font-weight: 600;
}
.compare-detail {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.compare-detail-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
.compare-detail-label {
  font-size: 12px;
  font-weight: 600;
  margin-right: 2px;
}
.compare-detail-label.is-ok {
  color: var(--color-accent);
}
.compare-detail-label.is-gap {
  color: var(--color-warning);
}

/* —— 榜单视图标题行 —— */
.rank-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

/* —— 匹配状态提示 —— */
.match-status {
  font-size: 12px;
  color: var(--color-text-3);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}
.match-status.is-loading {
  color: var(--color-accent);
}
.match-status .spin {
  width: 14px;
  height: 14px;
}

/* —— 匹配度等级徽章 —— */
.rank-level {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 30px;
  height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
}
.lv-S {
  color: #fff;
  background: var(--color-text-2);
}
.lv-A {
  color: #fff;
  background: var(--color-text-3);
}
.lv-B {
  color: #fff;
  background: var(--color-accent);
}
.lv-C {
  color: #fff;
  background: var(--color-text-3);
}
.lv-不合格 {
  color: #fff;
  background: var(--color-danger);
}

/* 不合格岗位卡片：红色警告标识 + 浅红背景边框强调 */
.rank-card.rank-fail {
  background: var(--color-danger-bg);
  border-color: #f2c9c4;
}
.rank-card.rank-fail .rank-score {
  color: var(--color-danger);
}
.rank-card.rank-fail .rank-badge {
  background: var(--color-danger-bg);
}

/* —— 投简历弹窗 —— */
.apply-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.apply-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px 14px;
  background: var(--color-surface-muted);
  border-radius: var(--radius-sm);
}
.apply-label {
  font-size: 12px;
  color: var(--color-text-3);
  flex-shrink: 0;
}
.apply-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}
.apply-company {
  font-size: 12px;
  color: var(--color-accent);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  padding: 2px 10px;
  border-radius: 999px;
  white-space: nowrap;
}
.apply-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px 20px;
  color: var(--color-accent);
  font-size: 14px;
}
.apply-channels {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.apply-channel {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
}
.apply-channel.is-disabled {
  opacity: 0.55;
  background: var(--color-surface-muted);
  border-color: var(--color-border);
}
.apply-channel-icon {
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  background: var(--color-surface);
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
}
.apply-channel-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.apply-channel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}
.apply-channel-desc {
  font-size: 12px;
  color: var(--color-text-3);
  line-height: 1.5;
  word-break: break-all;
}
.apply-channel-reason {
  font-size: 12px;
  color: var(--color-danger);
}
.apply-channel-actions {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.apply-extra-tip {
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  background: var(--color-danger-bg);
  color: var(--color-danger);
  font-size: 13px;
}
.apply-note {
  font-size: 12px;
  color: var(--color-text-3);
  line-height: 1.6;
}
.apply-company-input {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.apply-company-tip {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-2);
  line-height: 1.6;
}
.apply-company-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 40vh;
  overflow-y: auto;
}
.apply-company-item {
  padding: 12px 14px;
  text-align: left;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s var(--ease-out-expo);
}
.apply-company-item:hover {
  border-color: var(--color-accent);
  background: var(--color-surface-muted);
}

/* —— 邮箱投稿：提交表单 —— */
.apply-submit-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 4px;
}
.submit-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.submit-field label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-2);
}
.submit-textarea {
  min-height: 140px;
  resize: vertical;
  line-height: 1.6;
  font-family: inherit;
}
.submit-check {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-3);
  cursor: pointer;
  user-select: none;
}
.submit-check input {
  accent-color: var(--color-accent);
}
.submit-tip {
  margin: 0;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
}

/* —— 定制简历弹窗 —— */
.resume-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px 20px;
  color: var(--color-accent);
  font-size: 14px;
}
.resume-error {
  padding: 40px 20px;
  text-align: center;
  color: var(--color-danger);
  font-size: 14px;
}

.resume-paper {
  width: 100%;
  background: var(--resume-bg, #ffffff);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 40px 46px;

  color: var(--resume-text, #222);
  font-family: var(--resume-body-font, SimHei, Microsoft YaHei, sans-serif);
  box-sizing: border-box;
}
.resume-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 4px;
}
.resume-head-main {
  flex: 1;
  min-width: 0;
}
.resume-photo {
  flex-shrink: 0;
  width: 96px;
  height: 128px;
  border: 1px solid var(--resume-secondary, var(--color-accent));
  border-radius: 4px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-surface-muted);
  color: var(--color-text-3);
  font-size: 12px;
  text-align: center;
}
.resume-photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.resume-name {
  font-size: 22px;
  font-weight: 600;
  color: var(--resume-text, var(--color-text));
  font-family: var(--resume-heading-font, SimHei, Microsoft YaHei, sans-serif);
  letter-spacing: 0.02em;
}
.resume-basic {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--resume-text, #555);
  padding-bottom: 16px;
  border-bottom: 1px solid var(--resume-secondary, var(--color-border));
}
.resume-section {
  margin-top: 24px;
}
.resume-section-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--resume-text, var(--color-text));
  font-family: var(--resume-heading-font, SimHei, Microsoft YaHei, sans-serif);
  padding-bottom: 8px;
  border-bottom: 2px solid var(--resume-accent, var(--color-accent));
}
.resume-ol {
  margin: 0;
  padding-left: 24px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.resume-ol li {
  font-size: 13px;
  line-height: 1.7;
  color: var(--resume-text, #333);
  padding-left: 4px;
}
.resume-skills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.resume-skill {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 13px;
  background: var(--color-surface-muted);
  border: 1px solid var(--color-border);
  color: var(--color-text-2);
}
.resume-skill.is-matched {
  background: var(--color-accent-soft);
  border-color: var(--color-border);
  color: var(--color-accent);
  font-weight: 600;
}
.resume-item {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-2);
  white-space: pre-wrap;
}

/* —— 版式差异 —— */
/* 侧边栏双栏 */
.rs-cols {
  display: flex;
  gap: 24px;
  align-items: stretch;
}
.rs-aside {
  flex: 0 0 180px;
  background: color-mix(in srgb, var(--resume-accent, #143252) 10%, #ffffff);
  border-radius: 6px;
  padding: 18px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.rs-aside .resume-photo {
  width: 96px;
  height: 128px;
  margin: 0 auto 12px;
}
.rs-aside .resume-name {
  font-size: 18px;
  text-align: center;
}
.rs-aside .resume-basic {
  text-align: center;
  font-size: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid color-mix(in srgb, var(--resume-accent, #143252) 20%, #ffffff);
}
.rs-aside .resume-section {
  margin-top: 16px;
}
.rs-aside .resume-section-title {
  font-size: 13px;
  border-bottom-color: color-mix(in srgb, var(--resume-accent, #143252) 35%, #ffffff);
}
.rs-main {
  flex: 1;
  min-width: 0;
}

/* 宽页眉 */
.rs-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin: -40px -46px 4px;
  padding: 28px 46px;
  background: var(--resume-accent, #1e3a5f);
  color: #ffffff;
}
.rs-banner .resume-name {
  color: #ffffff;
}
.rs-banner .resume-basic {
  color: #ffffff;
  border-bottom: none;
  padding-bottom: 0;
}
.rs-banner .resume-photo {
  border-color: #ffffff;
  background: var(--color-surface);
  color: var(--color-text-3);
}

/* 轻商务衬线 */
.layout-serif .resume-name,
.layout-serif .resume-section-title {
  letter-spacing: 0.04em;
}
.layout-serif .resume-section-title {
  border-bottom: 1px solid var(--resume-accent, #2f4f3e);
  padding-bottom: 6px;
  font-weight: 600;
}
.layout-serif .resume-basic {
  border-bottom: 1px solid #d8d4c8;
}
.layout-serif .resume-ol li {
  line-height: 1.8;
}

/* 证件照在衬线版中小一号、更克制 */
.layout-serif .resume-photo {
  width: 88px;
  height: 117px;
  border-radius: 2px;
}

.resume-missing {
  margin-top: 18px;
  padding: 16px;
  background: var(--color-warning-bg);
  border: 1px solid #f0d9a8;
  border-radius: 10px;
}
.resume-missing .resume-section-title {
  color: var(--color-warning);
  border-color: #f0d9a8;
}
.missing-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.missing-item {
  display: flex;
  gap: 12px;
  align-items: baseline;
  font-size: 13px;
}
.missing-name {
  flex-shrink: 0;
  font-weight: 600;
  color: var(--color-warning);
  background: var(--color-warning-bg);
  border-radius: var(--radius-sm);
  padding: 1px 8px;
}
.missing-suggestion {
  color: var(--color-text-2);
  line-height: 1.5;
}
.missing-question {
  margin-top: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
  color: var(--color-text-3);
}
.missing-buttons {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

/* —— 简历工作区：左编辑 + 右预览 —— */
.resume-workspace {
  display: flex;
  gap: 18px;
  align-items: stretch;
}
.resume-editor {
  flex: 0 0 430px;
  max-width: 430px;
  max-height: 62vh;
  overflow-y: auto;
  padding: 4px 10px 4px 2px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  box-sizing: border-box;
}
.resume-preview {
  flex: 1;
  min-width: 0;
  max-height: 62vh;
  overflow-y: auto;
  padding: 2px 2px 2px 8px;
  box-sizing: border-box;
}
.resume-editor-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.resume-editor-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}
.resume-tpl-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.resume-tpl-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 8px;
  width: 104px;
  border-radius: 10px;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  cursor: pointer;
  transition: all 0.2s var(--ease-out-expo);
}
.resume-tpl-card:hover {
  border-color: var(--color-accent);
}
.resume-tpl-card.is-active {
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
}
.resume-tpl-name {
  font-size: 12px;
  color: var(--color-text-2);
}
.resume-tpl-card.is-active .resume-tpl-name {
  color: var(--color-accent);
  font-weight: 600;
}
/* 模板缩略图：用方块示意不同版式区别 */
.resume-tpl-thumb {
  position: relative;
  display: block;
  width: 84px;
  height: 60px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  /* 简历模板缩略图保持白纸观感，不随暗色主题变暗 */
  background: #ffffff;
  border: 1px solid var(--color-border);
}
.resume-tpl-thumb .thumb-photo {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 10px;
  height: 13px;
  background: var(--color-border-strong);
  border-radius: 2px;
}
.resume-tpl-thumb .thumb-bar {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 8px;
  background: var(--tpl-primary);
  display: none;
}
.resume-tpl-thumb .thumb-line {
  position: absolute;
  height: 3px;
  border-radius: 2px;
  background: var(--color-border-strong);
}
.resume-tpl-thumb .thumb-l1 { top: 12px; left: 8px; right: 18px; }
.resume-tpl-thumb .thumb-l2 { top: 26px; left: 8px; right: 8px; }
.resume-tpl-thumb .thumb-l3 { top: 40px; left: 8px; right: 26px; }

/* 侧边栏版：左侧色块 */
.resume-tpl-thumb.thumb-sidebar .thumb-bar { display: block; }
.resume-tpl-thumb.thumb-sidebar .thumb-line { left: 14px; }
.resume-tpl-thumb.thumb-sidebar .thumb-l1 { right: 18px; }
.resume-tpl-thumb.thumb-sidebar .thumb-l2 { right: 8px; }
.resume-tpl-thumb.thumb-sidebar .thumb-l3 { right: 26px; }

/* 宽页眉版：顶部通栏色带 */
.resume-tpl-thumb.thumb-banner::before {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 16px;
  background: var(--tpl-primary);
}
.resume-tpl-thumb.thumb-banner .thumb-l1 { top: 24px; }
.resume-tpl-thumb.thumb-banner .thumb-l2 { top: 38px; }
.resume-tpl-thumb.thumb-banner .thumb-l3 { display: none; }

/* 单栏/衬线：顶部细标题线 + 正文 */
.resume-tpl-thumb.thumb-single .thumb-l1,
.resume-tpl-thumb.thumb-serif .thumb-l1 {
  height: 4px;
  background: var(--tpl-primary);
}
.resume-tpl-thumb.thumb-single .thumb-l2,
.resume-tpl-thumb.thumb-serif .thumb-l2 { top: 30px; }
.resume-tpl-thumb.thumb-single .thumb-l3,
.resume-tpl-thumb.thumb-serif .thumb-l3 { top: 44px; }

.resume-photo-edit {
  display: flex;
  align-items: center;
  gap: 12px;
}
.resume-photo-thumb {
  position: relative;
  width: 66px;
  height: 88px;
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid var(--color-border);
}
.resume-photo-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.resume-photo-del {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 18px;
  height: 18px;
  line-height: 18px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}
.resume-photo-upload {
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  border: 1px dashed var(--color-border-strong);
  background: var(--color-surface-muted);
  color: var(--color-text-2);
  font-size: 13px;
  cursor: pointer;
}
.resume-photo-tip {
  font-size: 12px;
  color: var(--color-text-3);
}
.resume-field-input {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 10px;
  border: 1px solid var(--color-border-strong);
  border-radius: 6px;
  font-size: 13px;
  color: var(--color-text);
  background: var(--color-surface);
  font-family: inherit;
  transition: border-color 0.2s var(--ease-out-expo);
}
.resume-field-input:focus {
  outline: none;
  border-color: var(--color-text);
}
.resume-field-textarea {
  resize: vertical;
  min-height: 44px;
  line-height: 1.5;
}
.resume-edit-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.resume-edit-input {
  flex: 1;
  min-width: 0;
}
.resume-mini-btn {
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  transition: all 0.2s var(--ease-out-expo);
}
.resume-mini-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}
.resume-mini-btn--danger:hover {
  border-color: var(--color-danger);
  color: var(--color-danger);
}
.resume-add-btn {
  align-self: flex-start;
  padding: 5px 12px;
  border-radius: 6px;
  border: 1px dashed var(--color-border-strong);
  background: var(--color-surface-muted);
  color: var(--color-text-2);
  font-size: 12px;
  cursor: pointer;
}
.resume-add-btn:hover {
  border-color: var(--color-accent);
}

.resume-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.resume-filename {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.resume-filename-label {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--color-text-3);
  white-space: nowrap;
}
.resume-filename-input {
  flex: 1;
  min-width: 0;
}

:global(.resume-dialog .el-dialog__body) {
  max-height: 70vh;
  overflow-y: auto;
}
:global(.lib-dialog .el-dialog__body) {
  max-height: 62vh;
  overflow-y: auto;
}

/* —— 匹配权重配置弹窗 ——*/
.weight-desc { font-size: 13px; color: var(--color-text-3); margin-bottom: 14px; }
.weight-rows { display: flex; flex-direction: column; gap: 12px; margin: 8px 0 4px; }
.weight-row { display: flex; align-items: center; gap: 12px; }
.weight-label { flex: 0 0 110px; font-size: 14px; color: var(--color-text-2); }
.weight-input { width: 100px !important; }
.weight-input :deep(.el-input-number) { width: 100%; }
.weight-unit { color: var(--color-text-3); font-size: 13px; }
.weight-total { margin-top: 18px; padding: 12px 16px; background: var(--color-surface-muted); border-radius: 10px; font-size: 14px; color: var(--color-text-2); display: flex; align-items: center; gap: 10px; }
.weight-total b { color: var(--color-accent); font-size: 17px; min-width: 36px; text-align: center; }
.weight-total.is-error { background: var(--color-danger-bg); }
.weight-total.is-error b { color: var(--color-danger); }
.weight-error { color: var(--color-danger); font-size: 13px; }
.weight-ok { color: var(--color-accent); font-size: 13px; font-weight: 600; }

/* —— 微交互 —— */
.rank-card {
  transition: transform 0.3s var(--ease-out-expo), box-shadow 0.3s var(--ease-out-expo), border-color 0.3s var(--ease-out-expo);
  will-change: transform, box-shadow;
  animation: page-enter 0.45s var(--ease-out-expo) backwards;
}
.rank-list .rank-card:nth-child(1) { animation-delay: 0ms; }
.rank-list .rank-card:nth-child(2) { animation-delay: 55ms; }
.rank-list .rank-card:nth-child(3) { animation-delay: 110ms; }
.rank-list .rank-card:nth-child(4) { animation-delay: 165ms; }
.rank-list .rank-card:nth-child(5) { animation-delay: 220ms; }
.rank-list .rank-card:nth-child(6) { animation-delay: 275ms; }

.rank-card:hover {
  transform: translateY(-3px);

}
.rank-first:hover {
  transform: translateY(-3px);

}

/* —— 我的简历素材 —— */
.library-panel {
  position: relative;
  min-height: 320px;
}
.library-panel > .material-list {
  max-height: 400px;
  overflow-y: auto;
  padding-right: 4px;
}
.material-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1 1 auto;
  min-height: 0;
}
.material-list--scroll {
  max-height: 340px;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 4px;
}
.material-row {
  position: relative;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 12px 14px;

  transition: border-color 0.25s var(--ease-out-expo), box-shadow 0.25s var(--ease-out-expo);
}
.material-row:hover {
  border-color: var(--color-border-strong);

}
.material-row.is-newest {
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
}
.material-main {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.material-type {
  flex-shrink: 0;
  margin-top: 1px;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 20px;
  background: var(--color-surface-muted);
  color: var(--color-text-2);
  white-space: nowrap;
}
.material-content {
  margin: 0;
  word-break: break-word;
  white-space: pre-wrap;
  color: var(--color-text);
  font-size: 13px;
  line-height: 1.7;
  flex: 1;
  min-width: 0;
}
.preview-group {
  flex-shrink: 0;
  display: flex;
  align-items: stretch;
  gap: 10px;
  padding: 10px 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 10px;

}
.preview-group-label {
  flex: 0 0 auto;
  align-self: center;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  color: var(--color-text-2);
}
.preview-group-body {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.preview-group-item {
  margin: 0;
  word-break: break-word;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  color: var(--color-text);
  font-size: 13px;
  line-height: 1.6;
}
.material-ops {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}
.material-time {
  margin-top: 8px;
  text-align: right;
  color: var(--color-text-2);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.new-fab {
  position: absolute;
  right: 16px;
  bottom: 16px;
  padding: 0 20px;
  height: 42px;
  border: none;
  border-radius: 999px;
  cursor: pointer;
  font-weight: 600;
  font-size: 14px;
  color: #fff;
  background: var(--color-accent);

  transition: transform 0.25s var(--ease-out-expo), box-shadow 0.25s var(--ease-out-expo);
}
.new-fab:hover {
  transform: translateY(-1px);
}
.new-fab.is-on {
  background: var(--color-accent);
}

/* —— 添加记录：筛选栏 —— */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  box-sizing: border-box;
}
.filter-search {
  flex: 1 1 220px;
  min-width: 180px;
  max-width: 100%;
  box-sizing: border-box;
}
.filter-select {
  flex-shrink: 0;
  width: 130px;
  box-sizing: border-box;
}
.filter-date {
  width: 110px;
}
.job-manage-layout {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  min-height: 0;
}
.job-cat-list {
  flex: 0 0 120px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 440px;
  overflow-y: auto;
}
.job-cat-item {
  font-size: 13px;
  color: var(--color-text-2);
  padding: 7px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.job-cat-item:hover {
  background: var(--color-surface-muted);
  color: var(--color-accent);
}
.job-cat-item.is-active {
  background: var(--color-accent-soft);
  color: var(--color-accent);
  font-weight: 600;
}
.job-manage-main {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.material-filter-box {
  margin-top: 14px;
}
.material-filter-box .lib-count {
  display: block;
  margin-bottom: 8px;
}
.add-divider {
  margin: 18px 0;
  height: 1px;
  background: var(--color-surface-muted);
}
.add-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.add-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}
.req-note {
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-2);
}
.basic-warn {
  margin-bottom: 12px;
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  background: var(--color-warning-bg);
  border: 1px solid #f0d9a8;
  border-left: 3px solid var(--color-warning);
  color: var(--color-warning);
  font-size: 13px;
  line-height: 1.6;
}
.basic-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 14px;
}
.basic-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.req-label {
  font-size: 13px;
  color: var(--color-text-2);
}
.req-label::before {
  content: '*';
  color: var(--color-danger);
  margin-right: 2px;
}
.add-actions {
  display: flex;
  justify-content: flex-end;
}
.free-add-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.add-hint {
  font-size: 13px;
  color: var(--color-text-3);
  line-height: 1.5;
}
.free-add-time {
  flex: 1 1 220px;
  min-width: 200px;
  max-width: 100%;
  box-sizing: border-box;
}

/* —— 导入简历 —— */
/* 导入方式选择 */
.import-choice {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.import-choice-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 14px 16px;
  font-family: inherit;
  text-align: left;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s var(--ease-out-expo);
}
.import-choice-item:hover {
  border-color: var(--color-border-hover);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}
.import-choice-item:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}
.import-choice-item > .el-icon {
  flex: none;
  color: var(--color-accent);
}
.import-choice-main {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.import-choice-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}
.import-choice-desc {
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-3);
}

.import-tip {
  margin-bottom: 16px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--color-text-3);
  background: var(--color-surface-muted);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
}
.upload-zone {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 26px 16px;
}
.upload-zone-title {
  font-size: 14px;
  color: var(--color-text-2);
}
.upload-zone-title em {
  font-style: normal;
  color: var(--color-accent);
  font-weight: 600;
}
.upload-zone-sub {
  font-size: 12px;
  color: var(--color-text-2);
}
/* —— 新增素材弹窗 —— */
.add-material-tabs :deep(.el-tabs__header) {
  margin-bottom: 14px;
}
.add-material-tabs .free-add-row {
  margin-top: 12px;
  justify-content: flex-end;
}
.import-progress {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 12px;
  color: var(--color-accent);
}
.import-hint-text {
  line-height: 1.5;
}
.import-bar {
  position: relative;
  height: 6px;
  border-radius: 4px;
  background: var(--color-surface-muted);
  overflow: hidden;
}
.import-bar-fill {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  width: 0;
  border-radius: 4px;
  background: var(--color-accent);
  transition: width 0.25s ease;
}
.phase-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.phase-text {
  font-size: 13px;
  color: var(--color-accent);
  font-weight: 600;
}
.phase-percent {
  font-size: 12px;
  color: var(--color-text-3);
}
.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.minimize-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 16px;
  color: var(--color-text-3);
  width: 28px;
  height: 28px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.minimize-btn:hover {
  background: var(--color-accent-soft);
  color: var(--color-accent);
}
.is-minimized .el-dialog__body {
  padding-top: 0;
  padding-bottom: 12px;
}
/* —— 预览比对 —— */
.preview-cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  align-items: stretch;
}
.preview-col {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 12px;
  background: var(--color-surface);
}
.preview-col-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}
.preview-extra {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 14px;
}
.preview-extra .edit-field {
  margin: 0;
}
.preview-extra-tip {
  grid-column: 1 / -1;
  margin: 0;
  font-size: 12px;
  color: var(--color-text-3);
}
.preview-original {
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--color-text-2);
  font-size: 13px;
  line-height: 1.7;
  max-height: 320px;
  overflow-y: auto;
}

/* —— 疑似重复 —— */
.dup-hint {
  margin-bottom: 14px;
  font-size: 12px;
  color: var(--color-warning);
  background: var(--color-warning-bg);
  border: 1px solid #f0d9a8;
  border-radius: var(--radius-sm);
  padding: 10px 12px;
}
.dup-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.dup-item {
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 12px;
}
.dup-similarity {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-danger);
  margin-bottom: 8px;
}
.dup-cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.dup-col {
  min-width: 0;
}
.dup-col-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-2);
  margin-bottom: 6px;
}
.dup-content {
  margin: 6px 0 0;
  word-break: break-word;
  white-space: pre-wrap;
  color: var(--color-text-2);
  font-size: 13px;
  line-height: 1.6;
}
.dup-options {
  margin-top: 12px;
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
}
.dup-opt {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-2);
  cursor: pointer;
}

/* —— 编辑记录 —— */
.edit-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
}
.edit-label {
  font-size: 13px;
  color: var(--color-text-2);
}
.edit-select {
  width: 100%;
}
.ai-draft-hint {
  margin: -4px 0 12px;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--color-text-2);
  background: var(--color-surface-muted);
  border: 1px solid var(--color-border);
  border-radius: 6px;
}

/* —— 关键词高亮 —— */
.hl {
  background: var(--color-surface-muted);
  color: var(--color-text-2);
  border-radius: 4px;
  padding: 0 1px;
}

/* —— 主页面：必填信息 / 自由添加区 —— */
.main-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.eval-btn-wrap {
  display: inline-flex;
}

/* —— 素材管理：卡片壳分组 —— */
.group-scroll {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: 52vh;
  overflow-y: auto;
  padding-right: 4px;
}
.group-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 14px 16px;

  transition: border-color 0.25s var(--ease-out-expo), box-shadow 0.25s var(--ease-out-expo);
}
.group-card:hover {
  border-color: var(--color-border-strong);

}
.group-card.is-latest {
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
}
.group-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.sub-record {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.type-block {
  flex-shrink: 0;
  display: flex;
  align-items: stretch;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
}
.type-block-label {
  flex: 0 0 auto;
  align-self: center;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  color: var(--color-text-2);
}
.type-divider {
  flex: 0 0 1px;
  width: 1px;
  align-self: stretch;
  background: var(--color-border-strong);
}
.item-no {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--color-text-2);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  margin-right: 8px;
  box-sizing: border-box;
}
.type-block-body {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.type-block-item {
  margin: 0;
  word-break: break-word;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  color: var(--color-text);
  font-size: 13px;
  line-height: 1.6;
}
.group-foot {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.group-ops {
  display: flex;
  align-items: center;
  gap: 14px;
}

/* —— 编辑卡片壳 —— */
.edit-subs {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 4px;
}
.edit-sub {
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 12px;
  background: var(--color-surface);
}
.edit-sub-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.edit-sub-idx {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-accent);
}
.edit-sub-type {
  flex-shrink: 0;
  width: 160px;
}
.edit-sub-del {
  margin-left: auto;
  border: none;
  background: transparent;
  color: var(--color-danger);
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
}
.edit-sub-del:hover {
  text-decoration: underline;
}
.edit-add {
  margin-top: 10px;
  width: 100%;
  height: 38px;
  border: 1px dashed var(--color-border-strong);
  background: var(--color-surface);
  border-radius: 10px;
  color: var(--color-accent);
  font-size: 13px;
  cursor: pointer;
}
.edit-add:hover {
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
}

/* ================= 工作台一体化布局（岗位库主体 + 简历素材侧边栏） ================= */
.job-view-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 0 2px 14px;
  border-bottom: 1px solid var(--color-border);
}
.job-view-heading {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
  min-width: 0;
}
.job-view-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--color-text);
}
.job-view-count {
  font-size: 12px;
  color: var(--color-text-3);
}
.job-view-update {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--color-text-3);
}
.job-view-update svg {
  width: 12px;
  height: 12px;
  fill: none;
  stroke: var(--color-mist);
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.job-view-tools {
  flex: 1;
  justify-content: flex-end;
}
.job-view-tools .toolbar-search {
  width: 200px;
}
.job-view-tools .toolbar-sort {
  width: 160px;
}
.jobmatch-page .page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--gradient-section);
}
.page-header-left {
  min-width: 0;
}
.stage-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 0 2px 10px;
}
.stage-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
}
.stage-title::before {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 8px;
  border-radius: 50%;
  background: var(--color-mist);
  vertical-align: 2px;
}
.stage-count {
  font-size: 12px;
  color: var(--color-text-3);
}
.page-header-right {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
}
.page-header-tools {
  display: flex;
  align-items: center;
  flex: 1;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
}
.page-header-tools .toolbar-search {
  width: 200px;
}
.page-header-tools .toolbar-sort {
  width: 160px;
}
.workspace {
  display: flex;
  align-items: flex-start;
  gap: 18px;
}
.main-stage {
  flex: 7;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
  transition: flex-grow 0.3s var(--ease-out-expo, cubic-bezier(0.22, 0.61, 0.36, 1)),
    flex-basis 0.3s var(--ease-out-expo, cubic-bezier(0.22, 0.61, 0.36, 1));
}
.material-drawer {
  flex: 3;
  min-width: 0;
  position: sticky;
  top: 16px;
  max-height: calc(100vh - 32px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background: var(--color-surface);
  border: none;
  border-left: 1px solid var(--color-border);
  border-radius: 0;
  padding: 16px;

  transition: flex-grow 0.3s var(--ease-out-expo, cubic-bezier(0.22, 0.61, 0.36, 1)),
    flex-basis 0.3s var(--ease-out-expo, cubic-bezier(0.22, 0.61, 0.36, 1)),
    opacity 0.25s ease, padding 0.3s ease, border-color 0.3s ease;
}
.workspace:not(.is-open) .material-drawer {
  flex: 0 0 0;
  padding: 0 16px;
  border-width: 0;
  opacity: 0;
  pointer-events: none;
}
/* 窄屏：素材抽屉移到岗位库下方，避免两栏被挤压 */
@media (max-width: 900px) {
  .workspace {
    flex-direction: column;
  }
  .main-stage {
    flex: none;
    width: 100%;
  }
  .material-drawer {
    flex: none;
    width: 100%;
    position: static;
    max-height: none;
    overflow: visible;
    border-left: none;
    border-top: 1px solid var(--color-border);
    padding: 16px 0 0;
  }
  .workspace:not(.is-open) .material-drawer {
    display: none;
  }
}
.drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.drawer-head .panel-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}

/* —— 顶部工具栏 ——*/
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 12px 14px;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  min-width: 0;
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
/* 工具栏的次要操作改为文字按钮，避免一排边框盒子 */
.toolbar-right .button--plain {
  background: transparent;
  border-color: transparent;
  color: var(--color-text-2);
}
.toolbar-right .button--plain:hover {
  background: var(--color-surface-muted);
  border-color: transparent;
  color: var(--color-text);
}
.toolbar-right .button--plain.button--danger {
  color: var(--color-danger);
}
.toolbar-right .button--plain.button--danger:hover {
  background: var(--color-danger-bg);
  color: var(--color-danger);
}
.select-all-toggle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--color-text-2);
  cursor: pointer;
  user-select: none;
}
.selected-count {
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--color-accent-soft);
  color: var(--color-accent);
  font-size: 11px;
  font-weight: 600;
  line-height: 1.6;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.batch-actions {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 34px;
  padding: 0 8px 0 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
}
.batch-action {
  height: 26px;
  padding: 0 7px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-2);
  font-family: inherit;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.batch-action:hover:not(:disabled) {
  background: var(--color-surface);
  color: var(--color-text);
}
.batch-action:disabled {
  color: var(--color-text-3);
  opacity: 0.5;
  cursor: not-allowed;
}
.batch-action--danger {
  color: var(--color-danger);
}
.batch-action--danger:hover:not(:disabled) {
  background: var(--color-danger-bg);
  color: var(--color-danger);
}
.cat-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
/* 工具栏里分类标签独占一行，搜索与排序放到下一行 */
.toolbar > .cat-tabs {
  flex: 1 1 100%;
}
.cat-tab {
  border: 1px solid transparent;
  background: transparent;
  color: var(--color-text-3);
  font-size: 13px;
  padding: 5px 12px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.18s ease;
  white-space: nowrap;
}
.cat-tab:hover {
  color: var(--color-accent);
  background: var(--color-surface-muted);
}
.cat-tab.is-active {
  color: #ffffff;
  background: var(--color-primary);
  border-color: var(--color-primary);
  font-weight: 500;
}
.cat-tab.is-active:hover {
  color: #ffffff;
  background: var(--color-primary-mid);
}
/* 分类筛选：比主标签弱一级，可多选 */
.cat-divider {
  flex: none;
  width: 1px;
  height: 16px;
  margin: 0 6px;
  background: var(--color-border-strong);
}
.cat-tab--sub {
  font-size: 12px;
  padding: 3px 9px;
}
.cat-tab--sub.is-active {
  color: var(--color-primary);
  background: var(--color-accent-soft);
  border-color: var(--color-primary-light);
  font-weight: 500;
}
.cat-tab--sub.is-active:hover {
  color: var(--color-primary);
  background: var(--color-accent-soft);
}
.toolbar-search,
.toolbar-sort {
  --el-component-size: 30px;
  font-size: 13px;
}
.toolbar-search :deep(.el-input__wrapper),
.toolbar-sort :deep(.el-select__wrapper) {
  border-radius: 999px;
}
.toolbar-search {
  width: 220px;
  flex: none;
}
.toolbar-sort {
  width: 170px;
  flex: none;
}
.button--danger {
  color: var(--color-danger);
  border-color: #f2c9c4;
  background: var(--color-surface);
}
.button--danger:hover {
  background: var(--color-danger-bg);
  border-color: #e7aca5;
}

.required-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: var(--color-warning-bg);
  border: 1px solid #f0d9a8;
  border-radius: var(--radius-md);
  padding: 12px 14px;
}

/* —— 岗位卡片列表 ——*/
.job-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.job-rank {
  flex: none;
  align-self: center;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--color-surface-muted);
  color: var(--color-text-3);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.job-rank--1 {
  background: var(--color-accent-soft);
  color: var(--color-accent);
}
.job-rank--2 {
  background: var(--color-surface-muted);
  color: var(--color-text);
  box-shadow: inset 0 0 0 1px var(--color-border-strong);
}
.job-rank--3 {
  background: var(--color-warning-bg);
  color: var(--color-warning);
}
.job-rec-tag {
  flex: none;
  padding: 0 8px;
  border: 1px dashed var(--color-accent);
  border-radius: 999px;
  color: var(--color-accent);
  font-size: 11px;
  line-height: 1.6;
}
.job-card {
  transition: border-color 0.25s var(--ease-out-expo, cubic-bezier(0.22, 0.61, 0.36, 1)),
    box-shadow 0.25s var(--ease-out-expo, cubic-bezier(0.22, 0.61, 0.36, 1)),
    transform 0.35s var(--ease-out-expo, cubic-bezier(0.16, 1, 0.3, 1));
}
.job-card:hover {
  transform: translateY(-2px);
  border-color: var(--color-border-hover);
  box-shadow: var(--shadow-hover);
}
.job-card.is-hotmatch:hover {
  border-color: var(--color-lilac);
  box-shadow: var(--shadow-lilac);
}
.job-card.is-score-changed {
  animation: score-flash 2.2s ease;
}
@keyframes score-flash {
  0%, 100% { background: var(--color-surface); }
  20%, 60% { background: var(--color-lilac-bg); }
}
.job-card-main {
  cursor: pointer;
}
.job-base-row {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 14px;
}
.job-company,
.job-loc,
.job-salary {
  font-size: 12px;
  color: var(--color-text-3);
}
.job-source-tag {
  font-size: 11px;
  line-height: 1.6;
  padding: 0 6px;
  border-radius: 999px;
  color: var(--color-accent);
  background: var(--color-accent-soft);
  white-space: nowrap;
}
.job-loc,
.job-salary {
  color: var(--color-text-3);
  font-variant-numeric: tabular-nums;
}
.job-match-row {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.job-score {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 52px;
  height: 30px;
  padding: 0 10px;
  border-radius: var(--radius-sm);
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  background: var(--color-accent);
  font-variant-numeric: tabular-nums;
}
.job-score.score-high { background: var(--color-lilac-deep); }
.job-score.score-mid { background: var(--color-text-3); }
.job-score.score-low { background: var(--color-border-strong); }
.dim-bars--inline {
  flex: 1 1 320px;
  display: grid;
  grid-template-columns: repeat(4, minmax(90px, 1fr));
  gap: 8px 14px;
}
.dim-bar-mini {
  display: flex;
  align-items: center;
  gap: 6px;
}
.dim-bar-label {
  flex: none;
  width: 20px;
  font-size: 12px;
  color: var(--color-text-3);
}
.dim-bar-track {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: var(--color-surface-muted);
  overflow: hidden;
}
.dim-bar-fill {
  height: 100%;
  border-radius: 999px;
  background: var(--color-accent);
  transition: width 0.4s var(--ease-out-expo, cubic-bezier(0.22, 0.61, 0.36, 1));
}
.dim-bar-fill.is-high { background: var(--color-mist); }
.dim-bar-fill.is-mid { background: #9fb4c0; }
.dim-bar-fill.is-low { background: var(--color-border-strong); }
.dim-bar-val {
  flex: none;
  width: 26px;
  text-align: right;
  font-size: 12px;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}
.job-summary-row {
  margin-top: 4px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--color-text-2);
}
.hit-count { color: var(--color-accent); font-weight: 600; }
.miss-count { color: var(--color-text); font-weight: 600; }
.job-caret { color: var(--color-text-3); }

.job-card-actions {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.recommend-add-btn {
  min-width: 106px;
}
.recommend-add-btn.is-collected:disabled {
  opacity: 1;
  background: var(--color-surface-muted);
  border-color: var(--color-border);
  color: var(--color-text-3);
  cursor: not-allowed;
}
.job-edit-link,
.job-del-link {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 12px;
  color: var(--color-text-3);
  padding: 2px 4px;
}
.job-edit-link:hover { color: var(--color-accent); }
.job-del-link:hover { color: var(--color-danger); }

/* —— 新卡片：左右两栏 + 大分 + 匹配素材 chips ——*/
.job-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.job-card > .job-card-left,
.job-card > .job-card-right {
  width: 100%;
}
@media (min-width: 1024px) {
  .job-card {
    flex-direction: row;
    flex-wrap: wrap;
    align-items: flex-start;
    gap: 14px;
  }
  .job-card > .job-card-left {
    flex: 0 0 65%;
    min-width: 0;
  }
  .job-card > .job-card-right {
    flex: 0 0 calc(35% - 14px);
    min-width: 0;
    border-left: 1px solid var(--color-border);
    padding-left: 14px;
    align-self: stretch;
  }
  .job-card > .job-detail {
    flex-basis: 100%;
    width: 100%;
    box-sizing: border-box;
  }
}

.job-card.is-hotmatch {
  border-color: var(--color-hotmatch-border) !important;
  background-image: linear-gradient(180deg, var(--color-hotmatch-tint) 0%, var(--color-surface) 72%);
}

.job-card-left {
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

/* 大分数区 */
.job-score-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  flex-wrap: wrap;
  position: relative;
}

/* 求职小精灵：线稿 + 呼吸光晕 */
.apply-elf {
  position: relative;
  flex: none;
  display: inline-flex;
  align-items: center;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-primary);
  cursor: pointer;
  animation: elf-float 3.2s ease-in-out infinite;
}
.elf-icon {
  position: relative;
  display: inline-grid;
  place-items: center;
  width: 36px;
  height: 36px;
  /* 光学对齐：数字的视觉中心略高于行盒中心 */
  transform: translateY(3px);
}
.elf-icon::before {
  content: '';
  position: absolute;
  inset: 1px;
  border-radius: 50%;
  background: var(--color-primary);
  filter: blur(7px);
  animation: elf-glow 2.6s ease-in-out infinite;
}
.elf-svg {
  position: relative;
  width: 29px;
  height: 29px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.4;
  stroke-linecap: round;
  stroke-linejoin: round;
  overflow: visible;
  transition: transform 0.2s ease;
}
.apply-elf:hover .elf-svg {
  transform: scale(1.12);
}
.elf-label {
  position: absolute;
  left: 100%;
  top: 50%;
  margin-left: 2px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  letter-spacing: 0.02em;
  color: var(--color-text-2);
  white-space: nowrap;
  pointer-events: none;
  opacity: 0;
  transform: translate(-4px, -50%);
  transition: opacity 0.28s ease, transform 0.32s var(--ease-out-expo), color 0.28s ease;
}
.apply-elf:hover .elf-label,
.apply-elf:focus-visible .elf-label,
.apply-elf.is-intro .elf-label {
  opacity: 1;
  transform: translate(0, -50%);
  color: var(--color-primary);
}
.elf-eye,
.elf-twinkle {
  fill: currentColor;
  stroke: none;
}
.elf-twinkle {
  transform-origin: 19px 5.8px;
  animation: elf-twinkle 2.2s ease-in-out infinite;
}
.elf-sparks {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.elf-sparks::before,
.elf-sparks::after {
  content: '';
  position: absolute;
  top: 4px;
  left: 50%;
  width: 6px;
  height: 6px;
  background: var(--color-primary);
  clip-path: polygon(50% 0, 62% 38%, 100% 50%, 62% 62%, 50% 100%, 38% 62%, 0 50%, 38% 38%);
  opacity: 0;
}
.apply-elf.is-popping {
  animation: elf-pop 0.62s var(--ease-spring, cubic-bezier(0.34, 1.56, 0.64, 1));
}
.apply-elf.is-popping .elf-sparks::before {
  animation: elf-spark-l 0.62s ease-out forwards;
}
.apply-elf.is-popping .elf-sparks::after {
  animation: elf-spark-r 0.62s 0.06s ease-out forwards;
}
@keyframes elf-glow {
  0%, 100% { opacity: 0.06; transform: scale(0.8); }
  50% { opacity: 0.24; transform: scale(1.12); }
}
@keyframes elf-twinkle {
  0%, 100% { opacity: 0.3; transform: scale(0.75); }
  50% { opacity: 1; transform: scale(1.2); }
}
@keyframes elf-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-3px); }
}
@keyframes elf-pop {
  0% { transform: scale(1); }
  35% { transform: scale(1.3) rotate(-8deg); }
  65% { transform: scale(0.94) rotate(4deg); }
  100% { transform: scale(1); }
}
@keyframes elf-spark-l {
  from { opacity: 1; transform: translate(0, 0) scale(0.6); }
  to { opacity: 0; transform: translate(-13px, -13px) scale(1.1); }
}
@keyframes elf-spark-r {
  from { opacity: 1; transform: translate(0, 0) scale(0.6); }
  to { opacity: 0; transform: translate(13px, -13px) scale(1.1); }
}
.job-score-big {
  position: relative;
  font-size: 36px;
  font-weight: 700;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.5px;
  min-width: 72px;
}
.job-score-big.score-high,
.job-score-big.score-mid,
.job-score-big.score-low {
  color: var(--color-text);
}
/* 高匹配分数用渐变高光，强化第一眼的识别度 */
.job-score-big.score-high .score-num-text {
  background: var(--gradient-score-high);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.job-score-big:not(.score-high):not(.score-mid):not(.score-low) {
  color: var(--color-text-3);
}

/* 分数更新：轻微抖动 + 小幅放大 + 循环彩色渐变，1.2s 后恢复原样 */
.score-num-text {
  display: inline-block;
  transform-origin: left center;
}
.job-score-big.is-score-pulse .score-num-text {
  animation: score-pop 1.2s linear;
}
@supports ((-webkit-background-clip: text) or (background-clip: text)) {
  .job-score-big.is-score-pulse .score-num-text {
    background-image: linear-gradient(
      90deg,
      #17505c 0%,
      #6fa8c4 35%,
      #9a8bbf 65%,
      #17505c 100%
    );
    background-size: 300% 100%;
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }
}
@keyframes score-pop {
  0% {
    transform: translate(0, 0) scale(1);
    background-position: 0% 50%;
  }
  10% {
    transform: translate(-2px, -1px) scale(1.06);
  }
  20% {
    transform: translate(2px, 1px) scale(1.07);
  }
  30% {
    transform: translate(-1.5px, 0.5px) scale(1.05);
  }
  40% {
    transform: translate(0, 0) scale(1.02);
  }
  50% {
    background-position: 100% 50%;
  }
  70% {
    background-position: 0% 50%;
  }
  85% {
    background-position: 100% 50%;
  }
  100% {
    transform: translate(0, 0) scale(1);
    background-position: 50% 50%;
  }
}
@media (prefers-reduced-motion: reduce) {
  .job-score-big.is-score-pulse .score-num-text {
    animation: none;
    background: none;
    -webkit-background-clip: border-box;
    background-clip: border-box;
    -webkit-text-fill-color: currentColor;
  }
}
.score-pop-tag {
  position: absolute;
  left: 100%;
  top: -4px;
  margin-left: 2px;
  display: inline-block;
  padding: 2px 6px;
  border-radius: 6px;
  background: var(--color-accent-soft);
  border: 1px solid var(--color-border);
  color: var(--color-accent);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  animation: floatUpFade 2.2s ease-out forwards;
  pointer-events: none;
}
@keyframes floatUpFade {
  0%   { transform: translateY(0); opacity: 0; }
  10%  { opacity: 1; }
  80%  { transform: translateY(-18px); opacity: 1; }
  100% { transform: translateY(-24px); opacity: 0; }
}
.high-match-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--color-lilac-bg);
  border: 1px solid var(--color-lilac-border);
  color: var(--color-lilac-text);
  font-size: 11px;
  font-weight: 600;
  animation: scorePopIn 0.45s ease-out;
}
@keyframes scorePopIn {
  0%   { transform: scale(0.4); opacity: 0; }
  70%  { transform: scale(1.15); }
  100% { transform: scale(1); opacity: 1; }
}

/* 匹配素材 chip 列*/
.job-card-right {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.match-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--color-text);
  font-weight: 600;
}
.match-panel-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--color-accent-soft);
  color: var(--color-accent);
  font-size: 11px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.match-panel-empty {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: var(--color-text-3);
  padding: 10px 0;
  text-align: center;
}
.match-panel-empty-hint {
  font-size: 11.5px;
  color: var(--color-text-3);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.match-chip {
  display: block;
  background: var(--color-accent-soft);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 12.5px;
  line-height: 1.4;
  margin-bottom: 6px;
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.2s, background 0.2s;
  color: var(--color-text-2);
}
.match-chip:hover {

  background: var(--color-accent-soft);
  border-color: var(--color-accent);
}
.match-chip.is-hover-source {
  background: var(--color-accent-soft);
  border-color: var(--color-accent);
}
.match-chip-tag {
  flex: none;
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  color: var(--color-text-2);
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}
.match-chip-head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.match-chip-text {
  display: block;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
.match-chip-arrow {
  flex: none;
  font-size: 10px;
  line-height: 1;
  transition: transform 0.2s ease;
}
.match-chip.is-open .match-chip-arrow {
  transform: rotate(180deg);
}
/* 展开详情限制最大高度，内部滚动，避免卡片被无限拉长 */
.match-chip-detail {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed var(--color-border);
  max-height: 120px;
  overflow-y: auto;
  animation: matchDetailIn 0.18s ease-out;
}
.match-chip-detail-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-2);
  white-space: pre-wrap;
  word-break: break-word;
}
@keyframes matchDetailIn {
  from { opacity: 0; transform: translateY(-3px); }
  to { opacity: 1; transform: none; }
}
.match-chip-detail .match-chip-evidence {
  white-space: normal;
  margin-top: 4px;
}
.match-chip-evidence {
  display: block;
  font-size: 11px;
  color: var(--color-text-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.match-toggle-more {
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 4px 6px;
  font-size: 12px;
  color: var(--color-accent);
  font-weight: 600;
  align-self: flex-start;
}
.match-toggle-more:hover {
  color: var(--color-text);
  text-decoration: underline;
}

/* 底部操作升级 */
.apply-btn {
  transition: box-shadow 0.2s, transform 0.2s, background 0.2s;
}
.apply-btn--hot {
  background: var(--color-primary) !important;
  border-color: var(--color-primary) !important;
  color: #fff !important;
  font-weight: 600;
}
.apply-btn--hot:hover:not(:disabled) {
  transform: translateY(-1px);

}

/* 涨分预估 pill */
.gain-pill {
  flex: none;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--color-accent-soft);
  color: var(--color-accent);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
  border: 1px solid var(--color-border);
}

/* —— 匹配详情：覆盖率 + 优势/缺口卡片 —— */
.match-insight {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
}
.insight-overview {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-muted);
}
.insight-overview :deep(.el-progress__text) {
  font-family: var(--font-display);
  font-size: 15px !important;
  font-weight: 600;
  color: var(--color-text);
}
.insight-overview :deep(.el-progress__text i) {
  font-size: 10px;
  color: var(--color-text-3);
}
.insight-overview-text {
  flex: 1;
  min-width: 0;
}
.insight-overview-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}
.insight-overview-count {
  flex: none;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-3);
  font-variant-numeric: tabular-nums;
}
.insight-overview-sub {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-3);
}
.insight-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.insight-card {
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}
.insight-card-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}
.insight-card--hit .insight-card-head svg {
  color: var(--color-success);
}
.insight-card--gap .insight-card-head svg {
  color: var(--color-warning);
}
.insight-card--gap .insight-card-head.is-success svg {
  color: var(--color-success);
}
.insight-card-count {
  margin-left: auto;
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--color-surface-muted);
  color: var(--color-text-3);
  font-size: 11px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}
.insight-list {
  display: flex;
  flex-direction: column;
  gap: 9px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.insight-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.insight-dot {
  flex: none;
  width: 6px;
  height: 6px;
  margin-top: 7px;
  border-radius: 50%;
}
.insight-dot--hit {
  background: var(--color-success);
}
.insight-dot--gap {
  background: var(--color-warning);
}
.insight-item-body {
  flex: 1;
  min-width: 0;
}
.insight-item-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.65;
  color: var(--color-text-2);
  word-break: break-word;
}
.insight-item-evidence {
  margin: 3px 0 0;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  font-size: 11px;
  line-height: 1.55;
  color: var(--color-text-3);
}
.insight-kw {
  padding: 0 3px;
  border-radius: 3px;
  background: var(--color-accent-soft);
  color: inherit;
  font-weight: 500;
}
.insight-kw--gap {
  background: var(--color-warning-bg);
  color: var(--color-warning);
}
.insight-item-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}
.insight-success {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  background: var(--color-success-bg);
  color: var(--color-success);
  font-size: 12px;
  line-height: 1.6;
}
.insight-empty {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-3);
}
.insight-tip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--color-accent-soft);
  color: var(--color-text-2);
  font-size: 12px;
  line-height: 1.65;
}
.insight-tip > svg {
  flex: none;
  margin-top: 2px;
  color: var(--color-accent);
}
.insight-tip p {
  flex: 1;
  min-width: 0;
  margin: 0;
}
.insight-tip p b {
  margin-right: 6px;
  color: var(--color-text);
}
.insight-tip .text-link {
  flex: none;
  white-space: nowrap;
}
@media (max-width: 720px) {
  .insight-grid {
    grid-template-columns: 1fr;
  }
  .insight-overview {
    gap: 12px;
  }
}

/* —— 匹配详情展开面板 ——*/
.job-detail {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 12px;
  animation: job-detail-in 0.26s var(--ease-out-expo) both;
}
@keyframes job-detail-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
.detail-block-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 8px;
}
.detail-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  margin-bottom: 6px;
}
.detail-item--hit {
  background: var(--color-accent-soft);
  border: 1px solid var(--color-border);
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease;
}
.detail-item--hit:hover {
  background: var(--color-accent-soft);
  border-color: var(--color-accent);
}
.detail-item--miss {
  background: var(--color-danger-bg);
  border: 1px solid #f2c9c4;
}
.detail-icon {
  flex: none;
  font-weight: 600;
  font-size: 13px;
}
.detail-item--hit .detail-icon { color: var(--color-accent); }
.detail-item--miss .detail-icon { color: var(--color-danger); }
.detail-text {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-2);
  word-break: break-word;
}
.detail-item--miss .detail-text { flex: 1 1 auto; }
.detail-source {
  flex: none;
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  padding: 2px 8px;
}
.hl-miss { background: var(--color-danger-bg); color: var(--color-danger); border-radius: 4px; }
.job-detail .rank-summary {
  margin: 4px 0 0;
}
.job-detail-tags {
  margin-top: 0;
}

/* 首屏加载骨架：避免列表出现前只有一行“加载中” */
.job-skeleton {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.job-skeleton-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}
.sk-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.sk {
  display: block;
  height: 12px;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--color-surface-muted) 25%, var(--color-border) 37%, var(--color-surface-muted) 63%);
  background-size: 400% 100%;
  animation: sk-shimmer 1.4s ease infinite;
}
.sk-badge { width: 36px; height: 20px; border-radius: 999px; }
.sk-title { width: 220px; max-width: 45%; height: 16px; }
.sk-meta { width: 120px; }
.sk-score { width: 64px; height: 34px; border-radius: 8px; }
.sk-line { flex: 1; }
.sk-line--long { width: 80%; }
.sk-line--short { width: 55%; }
@keyframes sk-shimmer {
  0% { background-position: 100% 0; }
  100% { background-position: -100% 0; }
}
@media (prefers-reduced-motion: reduce) {
  .sk { animation: none; }
}

/* 空状态引导：三步开始 */
.start-guide {
  width: 100%;
  max-width: 640px;
  text-align: left;
}
.start-guide-title {
  margin: 0 0 12px;
  font-size: 15px;
  color: var(--color-text);
}
.start-guide-steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.start-guide-steps li {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
}
.start-guide-num {
  flex: none;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--color-accent-soft);
  color: var(--color-accent);
  font-size: 12px;
  font-weight: 600;
}
.start-guide-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.start-guide-body b {
  font-size: 13px;
  color: var(--color-text);
}
.start-guide-body span {
  font-size: 12px;
  color: var(--color-text-3);
}

/* —— 侧边栏内容 ——*/
.side-section {
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}
.side-section:first-of-type {
  padding-top: 0;
  border-top: none;
}
.side-list {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}
.side-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.filter-search { flex: 1 1 auto; min-width: 0; }
.filter-select { flex: none; width: 110px; }
.side-count {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--color-text-3);
}
.material-collapse {
  border: none;
  background-color: transparent;
}
.material-collapse :deep(.el-collapse-item),
.material-collapse :deep(.el-collapse-item__header),
.material-collapse :deep(.el-collapse-item__wrap),
.material-collapse :deep(.el-collapse-item__content) {
  background-color: transparent;
}
.material-collapse :deep(.el-collapse-item__header) {
  border-bottom: 1px solid var(--color-border);
  height: 40px;
  line-height: 40px;
}
.material-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: 1px solid var(--color-border);
}
.mtype-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}
.mtype-count {
  margin-left: auto;
  margin-right: 12px;
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-surface-muted);
  border-radius: 999px;
  padding: 1px 8px;
}
.material-item {
  padding: 10px 4px 8px;
  border-bottom: 1px solid var(--color-border);
}
.material-item.is-flash {
  animation: mat-flash 2.2s ease;
  border-radius: var(--radius-sm);
}
@keyframes mat-flash {
  0%, 100% { background: transparent; }
  20%, 60% { background: var(--color-accent-soft); }
}
.material-item-text {
  cursor: pointer;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-2);
  word-break: break-word;
  white-space: pre-wrap;
}
.material-item-text:hover { color: var(--color-accent); }
.material-item-meta {
  margin-top: 4px;
  font-size: 11px;
  color: var(--color-text-3);
  font-variant-numeric: tabular-nums;
}
.material-item-actions {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.basic-grid--side {
  grid-template-columns: 1fr 1fr;
}
.full-width { width: 100%; }

/* —— 卡片底色：统一白底 + 1px 描边 —— */
.job-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}
.material-drawer {
  background: var(--color-surface);
  border-color: var(--color-border);
}

/* ============================================================
   卡片依次浮现入场动画（panel-settle / content-rise / chart-unmask）
   ============================================================ */
@media (prefers-reduced-motion: no-preference) {
  .job-card {
    animation: panel-settle 0.74s cubic-bezier(0.16, 1, 0.3, 1) backwards;
  }
  .job-list .job-card:nth-child(1) { animation-delay: 0.04s; }
  .job-list .job-card:nth-child(2) { animation-delay: 0.1s; }
  .job-list .job-card:nth-child(3) { animation-delay: 0.16s; }
  .job-list .job-card:nth-child(4) { animation-delay: 0.22s; }
  .job-list .job-card:nth-child(5) { animation-delay: 0.28s; }
  .job-list .job-card:nth-child(6) { animation-delay: 0.34s; }
  .job-list .job-card:nth-child(7) { animation-delay: 0.4s; }
  .job-list .job-card:nth-child(8) { animation-delay: 0.46s; }
  .job-list .job-card:nth-child(n + 9) { animation-delay: 0.52s; }

  .page-title,
  .panel-title,
  .section-title {
    animation: content-rise 0.54s cubic-bezier(0.16, 1, 0.3, 1) backwards;
    animation-delay: 0.1s;
  }

  .dim-bars--inline,
  .chart,
  .bar-chart {
    animation: chart-unmask 0.86s cubic-bezier(0.16, 1, 0.3, 1) backwards;
    animation-delay: 0.22s;
  }
}

@keyframes panel-settle {
  from { opacity: 0; transform: translateY(12px) scale(0.988); clip-path: inset(3% round 10px); }
  62% { opacity: 1; }
  to { opacity: 1; transform: translateY(0) scale(1); clip-path: inset(0 round 10px); }
}
@keyframes content-rise {
  from { opacity: 0; transform: translateY(9px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes chart-unmask {
  from { opacity: 0; transform: translateY(10px); clip-path: inset(100% 0 0 0); }
  to { opacity: 1; transform: translateY(0); clip-path: inset(0 0 0 0); }
}
</style>