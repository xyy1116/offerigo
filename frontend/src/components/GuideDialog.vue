<template>
  <el-dialog v-model="visible" title="使用指南" width="640px" align-center class="guide-dialog">
    <div class="guide">
      <p class="guide-intro">
        覆盖求职全流程的 AI 工作台：从简历素材、岗位推荐与匹配，到定制文书、模拟面试、笔试训练和投递跟进，一个平台完成。
      </p>
      <h4 class="guide-heading">七大模块</h4>
      <ol class="guide-steps">
        <li><strong>简历素材库</strong>：集中管理基础信息、教育、项目、实习、技能与证书；支持导入简历、AI 拆分长文本，素材一处更新，全站生效。</li>
        <li><strong>岗位获取与收藏</strong>：每日 08:00 推送匹配岗位 TOP10；支持粘贴、链接、文档、截图导入；收藏的岗位就是你的匹配岗位池。</li>
        <li><strong>匹配评估</strong>：技能、经历、学历、契合四维量化打分，列出匹配优势与待补足项，每条结论都能追溯到简历素材；90 分以上岗位高亮提示。</li>
        <li><strong>定制文书</strong>：选择模板、上传证件照，按岗位生成定制简历与求职信，支持 PDF / Word 导出，可随素材迭代反复优化。</li>
        <li><strong>AI 模拟面试</strong>：基于岗位 JD 定向提问、语音作答，结束后生成多维面试报告，历史记录可回看复盘。</li>
        <li><strong>AI 笔试助手</strong>：按岗位匹配真题，支持自由练习与限时模拟；错题自动归档，可重做、标记掌握，题库每日更新。</li>
        <li><strong>投递与跟进</strong>：投递记录进入个人中心台账，可上传归档当次简历、维护状态并设置跟进提醒；面试、笔试、错题记录统一管理。</li>
      </ol>
      <p class="guide-tip">
        温馨提示：匹配评估、文书生成、模拟面试、笔试与投递台账需要登录后使用；素材与记录保存在你的账号下。
      </p>
    </div>
    <template #footer>
      <button class="button" @click="visible = false">关闭</button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'

const visible = ref(false)

function open() {
  visible.value = true
}

defineExpose({ open })
</script>

<style scoped>
.guide {
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 6px;
  font-size: 14px;
  color: var(--color-text-2);
  line-height: 1.7;
}
.guide-intro {
  margin: 0 0 16px;
}
.guide-heading {
  margin: 0 0 10px;
  font-size: 15px;
  color: var(--color-text);
}
.guide-steps {
  margin: 0;
  padding-left: 20px;
}
.guide-steps li {
  margin-bottom: 8px;
}
.guide-steps strong {
  color: var(--color-text);
  font-weight: 600;
}
.guide-tip {
  margin: 14px 0 0;
  font-size: 13px;
  color: var(--color-text-3);
}
</style>