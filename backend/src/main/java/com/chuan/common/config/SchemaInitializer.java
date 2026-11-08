package com.chuan.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 数据库结构初始化器：应用启动时确保全部业务表存在（幂等，CREATE TABLE IF NOT EXISTS）。
 *
 * <p>本项目的表结构只维护在 MySQL 中，由本类在启动时自动建表，仓库不再维护 .sql 脚本。
 * 所有建表语句均为「完整最终形态」，已存在的表（含数据）不会被改动。
 *
 * <p>通过 {@link Order} 保证在所有业务初始化（如 {@link DataInitializer} 创建默认管理员）之前执行，
 * 避免全新环境因表尚未建立而启动失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaInitializer implements CommandLineRunner {

    private final DataSource dataSource;

    @Override
    public void run(String... args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ensureTable(stmt, "sys_user",
                    "CREATE TABLE IF NOT EXISTS `sys_user` ("
                            + "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',"
                            + "`username` varchar(50) NOT NULL COMMENT '用户名',"
                            + "`password` varchar(100) NOT NULL COMMENT '密码(BCrypt加密)',"
                            + "`nickname` varchar(50) DEFAULT NULL COMMENT '昵称',"
                            + "`avatar` mediumtext COMMENT '头像(data URI)',"
                            + "`email` varchar(100) DEFAULT NULL COMMENT '邮箱',"
                            + "`phone` varchar(20) DEFAULT NULL COMMENT '手机号',"
                            + "`security_question` varchar(200) DEFAULT NULL COMMENT '密保问题',"
                            + "`security_answer` varchar(100) DEFAULT NULL COMMENT '密保答案(BCrypt加密)',"
                            + "`status` tinyint NOT NULL DEFAULT '1' COMMENT '状态:1-启用 0-禁用',"
                            + "`create_time` datetime DEFAULT NULL COMMENT '创建时间',"
                            + "`update_time` datetime DEFAULT NULL COMMENT '更新时间',"
                            + "`deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除:0-未删除 1-已删除',"
                            + "PRIMARY KEY (`id`),"
                            + "UNIQUE KEY `uk_username` (`username`)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户表'");

            ensureTable(stmt, "resume",
                    "CREATE TABLE IF NOT EXISTS `resume` ("
                            + "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',"
                            + "`user_id` bigint NOT NULL COMMENT '用户ID',"
                            + "`content_json` mediumtext COMMENT '完整简历内容JSON（证件照 base64，需足够容量）',"
                            + "`create_time` datetime DEFAULT NULL COMMENT '创建时间',"
                            + "`update_time` datetime DEFAULT NULL COMMENT '更新时间',"
                            + "PRIMARY KEY (`id`),"
                            + "UNIQUE KEY `uk_user` (`user_id`)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='在线简历表'");

            ensureTable(stmt, "match_record",
                    "CREATE TABLE IF NOT EXISTS `match_record` ("
                            + "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',"
                            + "`user_id` bigint DEFAULT NULL COMMENT '用户ID',"
                            + "`job_title` varchar(200) DEFAULT NULL COMMENT '岗位名称',"
                            + "`resume_filename` varchar(255) DEFAULT NULL COMMENT '简历文件名',"
                            + "`overall_score` decimal(4,1) NOT NULL DEFAULT '0.0' COMMENT '总体匹配得分(0-100)',"
                            + "`result_json` text COMMENT '完整分析结果JSON(维度/高亮/原文)',"
                            + "`create_time` datetime DEFAULT NULL COMMENT '对比时间',"
                            + "PRIMARY KEY (`id`),"
                            + "KEY `idx_user_time` (`user_id`,`create_time`)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历与岗位JD匹配记录表'");

            ensureTable(stmt, "interview_record",
                    "CREATE TABLE IF NOT EXISTS `interview_record` ("
                            + "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',"
                            + "`user_id` bigint DEFAULT NULL COMMENT '用户ID',"
                            + "`position` varchar(200) DEFAULT NULL COMMENT '面试岗位',"
                            + "`tech_stack` varchar(500) DEFAULT NULL COMMENT '核心技术栈(注入面试提问)',"
                            + "`jd_text` text COMMENT '岗位JD原文(注入面试提问)',"
                            + "`question_bank_key` varchar(50) DEFAULT NULL COMMENT '题库方向:java/frontend/test/general',"
                            + "`stage` varchar(20) DEFAULT NULL COMMENT '当前阶段:overview/tech/project/scenario/reverse,完成时为NULL',"
                            + "`stage_question_count` int DEFAULT '0' COMMENT '当前阶段已问问题数',"
                            + "`question_count` int DEFAULT '0' COMMENT '面试总问题数',"
                            + "`status` varchar(20) DEFAULT 'done' COMMENT '状态:in_progress-进行中,done-已完成',"
                            + "`transcript` mediumtext COMMENT '完整对话记录(纯文本)',"
                            + "`report_json` text COMMENT '评估报告JSON(score/summary/strengths/weaknesses/suggestions)',"
                            + "`score` decimal(4,1) DEFAULT NULL COMMENT '综合得分(0-100)',"
                            + "`create_time` datetime DEFAULT NULL COMMENT '创建时间',"
                            + "`update_time` datetime DEFAULT NULL COMMENT '更新时间',"
                            + "PRIMARY KEY (`id`),"
                            + "KEY `idx_user_time` (`user_id`,`create_time`)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI面试记录表'");

            ensureTable(stmt, "exam_paper",
                    "CREATE TABLE IF NOT EXISTS `exam_paper` ("
                            + "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',"
                            + "`user_id` bigint DEFAULT NULL COMMENT '用户ID',"
                            + "`job_title` varchar(200) DEFAULT NULL COMMENT '岗位名称',"
                            + "`tech_stack` varchar(500) DEFAULT NULL COMMENT '技术栈摘要(逗号分隔)',"
                            + "`paper_type` varchar(20) DEFAULT NULL COMMENT '类型:practice-专项/mock-限时模拟',"
                            + "`mode` varchar(20) DEFAULT NULL COMMENT '专项选题维度:knowledge/type/difficulty',"
                            + "`source_record_id` bigint DEFAULT NULL COMMENT '关联match_record.id(一键跳转来源)',"
                            + "`total_count` int NOT NULL DEFAULT '0' COMMENT '总题数',"
                            + "`total_score` decimal(6,1) NOT NULL DEFAULT '0.0' COMMENT '满分',"
                            + "`user_score` decimal(6,1) DEFAULT NULL COMMENT '用户得分',"
                            + "`status` varchar(20) NOT NULL DEFAULT 'generated' COMMENT '状态:generated/answering/submitted/graded',"
                            + "`duration_seconds` int NOT NULL DEFAULT '0' COMMENT '限时秒数(专项为0)',"
                            + "`questions_json` mediumtext COMMENT '题目快照JSON(含答案/解析/测试用例)',"
                            + "`answers_json` text COMMENT '用户作答JSON',"
                            + "`report_json` text COMMENT '批改报告JSON',"
                            + "`create_time` datetime DEFAULT NULL COMMENT '创建时间',"
                            + "`update_time` datetime DEFAULT NULL COMMENT '更新时间',"
                            + "PRIMARY KEY (`id`),"
                            + "KEY `idx_user_time` (`user_id`,`create_time`)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI笔试试卷记录表'");

            ensureTable(stmt, "exam_question_bank",
                    "CREATE TABLE IF NOT EXISTS `exam_question_bank` ("
                            + "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',"
                            + "`question_hash` varchar(64) NOT NULL COMMENT '去重键MD5(规范化题干)',"
                            + "`company` varchar(200) DEFAULT NULL COMMENT '来源公司(多个用、分隔)',"
                            + "`role_category` varchar(50) DEFAULT NULL COMMENT '岗位方向:后端/前端/算法/测试/数据/运维/安全/客户端/通用IT',"
                            + "`tech_tags` varchar(500) DEFAULT NULL COMMENT '技术标签(逗号分隔)',"
                            + "`question_type` varchar(20) NOT NULL DEFAULT 'short_answer' COMMENT '题型:short_answer/coding',"
                            + "`difficulty` varchar(20) NOT NULL DEFAULT 'middle' COMMENT '难度:basic/middle/advanced',"
                            + "`stem` text NOT NULL COMMENT '题干',"
                            + "`options` text COMMENT '选项JSON(预留)',"
                            + "`answer` mediumtext COMMENT '参考答案(模型补充)',"
                            + "`analysis` mediumtext COMMENT '解析',"
                            + "`source_name` varchar(200) DEFAULT NULL COMMENT '来源名称',"
                            + "`source_url` varchar(1000) DEFAULT NULL COMMENT '来源链接',"
                            + "`frequency` int NOT NULL DEFAULT '1' COMMENT '面经出现频次',"
                            + "`crawl_date` date DEFAULT NULL COMMENT '抓取日期',"
                            + "`create_time` datetime DEFAULT NULL COMMENT '创建时间',"
                            + "`update_time` datetime DEFAULT NULL COMMENT '更新时间',"
                            + "PRIMARY KEY (`id`),"
                            + "UNIQUE KEY `uk_question_hash` (`question_hash`),"
                            + "KEY `idx_role_type` (`role_category`,`question_type`),"
                            + "KEY `idx_difficulty` (`difficulty`)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='大厂面试真题目库'");

            ensureTable(stmt, "exam_question_source",
                    "CREATE TABLE IF NOT EXISTS `exam_question_source` ("
                            + "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',"
                            + "`source_key` varchar(500) NOT NULL COMMENT '来源唯一键(原始URL)',"
                            + "`source_name` varchar(200) DEFAULT NULL COMMENT '来源名称',"
                            + "`source_url` varchar(1000) DEFAULT NULL COMMENT '来源页面',"
                            + "`content_hash` varchar(64) DEFAULT NULL COMMENT '内容hash,未变化则跳过',"
                            + "`question_count` int NOT NULL DEFAULT '0' COMMENT '本次解析题数',"
                            + "`last_status` varchar(200) DEFAULT NULL COMMENT '最近抓取状态',"
                            + "`last_crawl_time` datetime DEFAULT NULL COMMENT '最近抓取时间',"
                            + "`create_time` datetime DEFAULT NULL COMMENT '创建时间',"
                            + "`update_time` datetime DEFAULT NULL COMMENT '更新时间',"
                            + "PRIMARY KEY (`id`),"
                            + "UNIQUE KEY `uk_source_key` (`source_key`)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='大厂真题目库抓取来源'");

            ensureTable(stmt, "exam_question_usage",
                    "CREATE TABLE IF NOT EXISTS `exam_question_usage` ("
                            + "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',"
                            + "`user_id` bigint NOT NULL COMMENT '用户ID',"
                            + "`question_hash` varchar(64) NOT NULL COMMENT '题目去重键',"
                            + "`last_used_time` datetime DEFAULT NULL COMMENT '最近使用时间',"
                            + "`used_count` int NOT NULL DEFAULT '1' COMMENT '累计使用次数',"
                            + "`create_time` datetime DEFAULT NULL COMMENT '创建时间',"
                            + "`update_time` datetime DEFAULT NULL COMMENT '更新时间',"
                            + "PRIMARY KEY (`id`),"
                            + "UNIQUE KEY `uk_user_question` (`user_id`,`question_hash`),"
                            + "KEY `idx_user_time` (`user_id`,`last_used_time`)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户真题使用记录'");

            ensureTable(stmt, "wrong_question",
                    "CREATE TABLE IF NOT EXISTS `wrong_question` ("
                            + "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',"
                            + "`user_id` bigint DEFAULT NULL COMMENT '用户ID',"
                            + "`paper_id` bigint DEFAULT NULL COMMENT '来源试卷ID',"
                            + "`job_title` varchar(128) DEFAULT NULL COMMENT '岗位名称（关联试卷的岗位，自动归类）',"
                            + "`question_id` varchar(64) DEFAULT NULL COMMENT '去重键MD5(stem+type+options)',"
                            + "`question_type` varchar(20) DEFAULT NULL COMMENT '题型:single/multiple/short_answer/coding',"
                            + "`difficulty` varchar(20) DEFAULT NULL COMMENT '难度:basic/middle/advanced',"
                            + "`knowledge_point` varchar(200) DEFAULT NULL COMMENT '知识点',"
                            + "`question_json` text COMMENT '题目快照JSON',"
                            + "`user_answer` text COMMENT '用户错答',"
                            + "`standard_answer` text COMMENT '标准答案',"
                            + "`analysis` text COMMENT '解析',"
                            + "`error_reason` varchar(500) DEFAULT NULL COMMENT '错误原因',"
                            + "`extension` text COMMENT '知识点延伸建议',"
                            + "`times_wrong` int NOT NULL DEFAULT '1' COMMENT '累计错答次数',"
                            + "`status` varchar(20) NOT NULL DEFAULT 'wrong' COMMENT '状态:wrong/reviewing/mastered',"
                            + "`create_time` datetime DEFAULT NULL COMMENT '创建时间',"
                            + "`update_time` datetime DEFAULT NULL COMMENT '更新时间',"
                            + "PRIMARY KEY (`id`),"
                            + "KEY `idx_user_q` (`user_id`,`question_id`),"
                            + "KEY `idx_filter` (`user_id`,`status`,`knowledge_point`)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI笔试错题本表'");

            ensureTable(stmt, "knowledge_entry",
                    "CREATE TABLE IF NOT EXISTS `knowledge_entry` ("
                            + "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',"
                            + "`user_id` bigint NOT NULL COMMENT '所属用户ID',"
                            + "`type` varchar(50) DEFAULT '其他' COMMENT '属性分类',"
                            + "`content` text NOT NULL COMMENT '求职信息内容',"
                            + "`batch_id` varchar(64) DEFAULT NULL COMMENT '入库批次标识',"
                            + "`split_version` int DEFAULT NULL COMMENT '拆分格式版本',"
                            + "`create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                            + "PRIMARY KEY (`id`),"
                            + "KEY `idx_user_id` (`user_id`)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历素材知识库条目'");

            ensureTable(stmt, "target_job",
                    "CREATE TABLE IF NOT EXISTS `target_job` ("
                            + "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',"
                            + "`user_id` bigint NOT NULL COMMENT '所属用户ID',"
                            + "`job_name` varchar(200) NOT NULL COMMENT '岗位名称',"
                            + "`job_requirement` text NOT NULL COMMENT '岗位要求/招聘JD',"
                            + "`company_name` varchar(255) DEFAULT NULL COMMENT '招聘单位',"
                            + "`email` varchar(255) DEFAULT NULL COMMENT '招聘邮箱',"
                            + "`tech_stack` varchar(500) DEFAULT NULL COMMENT '核心技术栈（可选）',"
                            + "`job_type` varchar(50) DEFAULT NULL COMMENT '岗位类型标签（AI 自动识别）',"
                            + "`job_link` varchar(500) DEFAULT NULL COMMENT '岗位原始详情链接',"
                            + "`source_platform` varchar(100) DEFAULT NULL COMMENT '来源平台',"
                            + "`create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                            + "PRIMARY KEY (`id`),"
                            + "KEY `idx_user_id` (`user_id`)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='目标岗位表'");

            ensureTable(stmt, "job_match_result",
                    "CREATE TABLE IF NOT EXISTS `job_match_result` ("
                            + "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',"
                            + "`user_id` bigint NOT NULL COMMENT '所属用户ID',"
                            + "`fingerprint` varchar(128) NOT NULL COMMENT '知识库+岗位库内容指纹（sha256 + 版本后缀）',"
                            + "`result_json` mediumtext NOT NULL COMMENT '排名结果 JSON',"
                            + "`create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                            + "`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
                            + "PRIMARY KEY (`id`),"
                            + "KEY `idx_user_id` (`user_id`)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能岗位匹配结果缓存'");

            ensureTable(stmt, "user_preference",
                    "CREATE TABLE IF NOT EXISTS `user_preference` ("
                            + "`user_id` bigint NOT NULL COMMENT '用户ID（主键）',"
                            + "`target_position` varchar(255) DEFAULT NULL COMMENT '意向岗位',"
                            + "`expected_city` varchar(255) DEFAULT NULL COMMENT '期望城市',"
                            + "`expected_salary` varchar(255) DEFAULT NULL COMMENT '期望薪资范围',"
                            + "`follow_up_days` int DEFAULT '7' COMMENT '投递跟进提醒天数',"
                            + "`update_time` datetime DEFAULT NULL COMMENT '更新时间',"
                            + "PRIMARY KEY (`user_id`)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户求职偏好'");

            ensureTable(stmt, "job_recommendation",
                    "CREATE TABLE IF NOT EXISTS `job_recommendation` ("
                            + "`id` bigint NOT NULL AUTO_INCREMENT,"
                            + "`user_id` bigint NOT NULL COMMENT '用户ID',"
                            + "`job_name` varchar(255) DEFAULT NULL COMMENT '岗位名称',"
                            + "`company_name` varchar(255) DEFAULT NULL COMMENT '公司名称',"
                            + "`location` varchar(255) DEFAULT NULL COMMENT '工作地点',"
                            + "`salary` varchar(255) DEFAULT NULL COMMENT '薪资范围',"
                            + "`source_platform` varchar(100) DEFAULT NULL COMMENT '来源平台',"
                            + "`job_requirement` text COMMENT '标准化岗位要求全文',"
                            + "`job_link` varchar(500) DEFAULT NULL COMMENT '岗位链接',"
                            + "`match_score` decimal(5,1) DEFAULT NULL COMMENT '匹配度',"
                            + "`recommend_date` date DEFAULT NULL COMMENT '推荐日期',"
                            + "`is_new` tinyint DEFAULT '1' COMMENT '是否新推荐',"
                            + "`added` tinyint DEFAULT '0' COMMENT '是否已加入岗位库',"
                            + "`target_job_id` bigint DEFAULT NULL COMMENT '加入岗位库后回填ID',"
                            + "`create_time` datetime DEFAULT NULL COMMENT '创建时间',"
                            + "PRIMARY KEY (`id`),"
                            + "KEY `idx_user_date` (`user_id`,`recommend_date`)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能岗位推荐'");

            ensureTable(stmt, "job_application",
                    "CREATE TABLE IF NOT EXISTS `job_application` ("
                            + "`id` bigint NOT NULL AUTO_INCREMENT,"
                            + "`user_id` bigint NOT NULL COMMENT '用户ID',"
                            + "`job_id` bigint DEFAULT NULL COMMENT '关联目标岗位ID',"
                            + "`job_name` varchar(200) DEFAULT NULL COMMENT '岗位名称',"
                            + "`company_name` varchar(255) DEFAULT NULL COMMENT '公司名称',"
                            + "`applied_at` datetime DEFAULT NULL COMMENT '投递时间',"
                            + "`resume_version` varchar(200) DEFAULT NULL COMMENT '简历版本',"
                            + "`resume_file_path` varchar(500) DEFAULT NULL COMMENT '归档简历PDF路径',"
                            + "`resume_file_name` varchar(255) DEFAULT NULL COMMENT '归档简历文件名',"
                            + "`job_link` varchar(500) DEFAULT NULL COMMENT '岗位跳转链接',"
                            + "`status` varchar(20) DEFAULT NULL COMMENT '投递状态',"
                            + "`remark` varchar(1000) DEFAULT NULL COMMENT '备注',"
                            + "`draft` tinyint DEFAULT '1' COMMENT '1草稿/0正式',"
                            + "`create_time` datetime DEFAULT NULL COMMENT '创建时间',"
                            + "`update_time` datetime DEFAULT NULL COMMENT '更新时间',"
                            + "PRIMARY KEY (`id`),"
                            + "KEY `idx_user_draft` (`user_id`,`draft`)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投递台账'");

            ensureTable(stmt, "crawl_job",
                    "CREATE TABLE IF NOT EXISTS `crawl_job` ("
                            + "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',"
                            + "`job_name` varchar(255) NOT NULL COMMENT '岗位名称',"
                            + "`company_name` varchar(255) DEFAULT NULL COMMENT '公司名称',"
                            + "`location` varchar(255) DEFAULT NULL COMMENT '工作地点',"
                            + "`salary` varchar(128) DEFAULT NULL COMMENT '薪资范围',"
                            + "`job_desc` text COMMENT '岗位描述',"
                            + "`requirement` text COMMENT '岗位要求',"
                            + "`source_url` varchar(1000) NOT NULL COMMENT '外部原始岗位链接',"
                            + "`crawl_date` date NOT NULL COMMENT '爬取日期',"
                            + "`crawl_time` datetime NOT NULL COMMENT '爬取时间',"
                            + "`job_vector` longtext COMMENT '岗位向量（JSON数组）',"
                            + "PRIMARY KEY (`id`),"
                            + "KEY `idx_crawl_date` (`crawl_date`)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='爬虫岗位表'");

            ensureTable(stmt, "user_resume",
                    "CREATE TABLE IF NOT EXISTS `user_resume` ("
                            + "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',"
                            + "`user_id` bigint NOT NULL COMMENT '用户ID',"
                            + "`skill_vector` longtext COMMENT '技能栈向量（JSON数组）',"
                            + "`skill_tags` json DEFAULT NULL COMMENT '技能标签（JSON数组）',"
                            + "`material_hash` varchar(64) DEFAULT NULL COMMENT '简历素材内容哈希',"
                            + "PRIMARY KEY (`id`),"
                            + "UNIQUE KEY `uk_user` (`user_id`)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户简历技能向量'");

            ensureTable(stmt, "user_target_job",
                    "CREATE TABLE IF NOT EXISTS `user_target_job` ("
                            + "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',"
                            + "`user_id` bigint NOT NULL COMMENT '用户ID',"
                            + "`crawl_job_id` bigint NOT NULL COMMENT '爬虫岗位ID',"
                            + "`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                            + "PRIMARY KEY (`id`),"
                            + "UNIQUE KEY `uk_user_job` (`user_id`,`crawl_job_id`),"
                            + "KEY `idx_user` (`user_id`)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户目标岗位关联表'");

            // 既有 user_resume 表结构升级：记录素材哈希，素材变化后重算技能向量
            ensureColumn(stmt, "user_resume", "material_hash",
                    "ALTER TABLE `user_resume` ADD COLUMN `material_hash` varchar(64) DEFAULT NULL "
                            + "COMMENT '简历素材内容哈希' AFTER `skill_vector`");

            // 既有 user_preference 表结构升级：补充投递跟进提醒天数
            ensureColumn(stmt, "sys_user", "avatar",
                    "ALTER TABLE `sys_user` ADD COLUMN `avatar` mediumtext COMMENT '头像(data URI)' AFTER `nickname`");

            ensureColumn(stmt, "user_preference", "follow_up_days",
                    "ALTER TABLE `user_preference` ADD COLUMN `follow_up_days` int DEFAULT '7' COMMENT '投递跟进提醒天数' AFTER `expected_salary`");

            // 既有 target_job 表结构升级：补充「工作地点 / 薪资范围」两列（幂等，仅当列不存在时追加）
            ensureColumn(stmt, "target_job", "location",
                    "ALTER TABLE `target_job` ADD COLUMN `location` varchar(255) DEFAULT NULL COMMENT '工作地点' AFTER `email`");
            ensureColumn(stmt, "target_job", "salary",
                    "ALTER TABLE `target_job` ADD COLUMN `salary` varchar(255) DEFAULT NULL COMMENT '薪资范围' AFTER `location`");
            // 既有 target_job 表结构升级：补充岗位原始详情链接，投递跳转不再依赖推荐数据
            ensureColumn(stmt, "target_job", "job_link",
                    "ALTER TABLE `target_job` ADD COLUMN `job_link` varchar(500) DEFAULT NULL COMMENT '岗位原始详情链接' AFTER `job_type`");
            ensureColumn(stmt, "target_job", "source_platform",
                    "ALTER TABLE `target_job` ADD COLUMN `source_platform` varchar(100) DEFAULT NULL COMMENT '来源平台' AFTER `job_link`");
            ensureColumn(stmt, "job_application", "resume_file_path",
                    "ALTER TABLE `job_application` ADD COLUMN `resume_file_path` varchar(500) DEFAULT NULL COMMENT '归档简历PDF路径' AFTER `resume_version`");
            ensureColumn(stmt, "job_application", "resume_file_name",
                    "ALTER TABLE `job_application` ADD COLUMN `resume_file_name` varchar(255) DEFAULT NULL COMMENT '归档简历文件名' AFTER `resume_file_path`");
            // 历史推荐岗位回填：来源平台 + 原始岗位链接（仅补空值，幂等）
            try {
                stmt.executeUpdate("UPDATE `target_job` tj JOIN `job_recommendation` jr "
                        + "ON jr.`target_job_id` = tj.`id` "
                        + "SET tj.`source_platform` = COALESCE(tj.`source_platform`, jr.`source_platform`), "
                        + "tj.`job_link` = COALESCE(tj.`job_link`, jr.`job_link`) "
                        + "WHERE tj.`source_platform` IS NULL OR tj.`job_link` IS NULL");
            } catch (Exception e) {
                log.warn("推荐岗位来源/链接回填失败：{}", e.getMessage());
            }


            log.info("数据库结构初始化完成，共 {} 张表", 15);

            // 既有 job_match_result 表结构升级：指纹列扩容。
            // 指纹 = sha256(64 位十六进制) + "|rank-v5"（共 72 字符），原 varchar(64) 在严格模式下
            // 写入必然报 Data too long，导致评估结果永远无法落库（「未找到该岗位的评估结果」的根因）。
            ensureColumnLength(stmt, "job_match_result", "fingerprint", 128,
                    "ALTER TABLE `job_match_result` MODIFY COLUMN `fingerprint` varchar(128) NOT NULL "
                            + "COMMENT '知识库+岗位库内容指纹（sha256 + 版本后缀）'");
        } catch (Exception e) {
            log.error("数据库结构初始化失败：{}", e.getMessage(), e);
        }
    }

    private void ensureTable(Statement stmt, String table, String ddl) throws Exception {
        try (ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + table + "'")) {
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.execute(ddl);
                log.info("已创建 {} 表", table);
            }
        }
    }

    /** 既有表结构升级：仅当指定列不存在时执行 ALTER 语句（幂等）。 */
    private void ensureColumn(Statement stmt, String table, String column, String ddl) throws Exception {
        try (ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + table
                        + "' AND COLUMN_NAME = '" + column + "'")) {
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.execute(ddl);
                log.info("已为 {} 表新增列 {}", table, column);
            }
        }
    }

    /** 既有表结构升级：列长度不足期望值时扩容（幂等，长度已达标则跳过）。 */
    private void ensureColumnLength(Statement stmt, String table, String column, int minLength, String ddl)
            throws Exception {
        try (ResultSet rs = stmt.executeQuery(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + table
                        + "' AND COLUMN_NAME = '" + column + "'")) {
            if (!rs.next()) {
                // 列不存在（旧表结构缺失）：交给 ensureColumn 处理，这里不擅自 ALTER
                return;
            }
            long current = rs.getLong(1);
            boolean tooShort = rs.wasNull() || current < minLength;
            if (tooShort) {
                stmt.execute(ddl);
                log.info("已扩容 {}.{} 列长度至 {} 字符", table, column, minLength);
            }
        }
    }
}
