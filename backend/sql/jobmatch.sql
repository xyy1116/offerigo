  CREATE TABLE IF NOT EXISTS `knowledge_entry` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `type` VARCHAR(50) DEFAULT '其他' COMMENT '属性分类：基础信息/教育经历/项目经历/实习经历/技能证书/荣誉奖项/其他',
  `content` TEXT NOT NULL COMMENT '素材内容',
  `batch_id` VARCHAR(64) DEFAULT NULL COMMENT '入库批次标识（同一次入库的多条记录共享）',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='我的简历素材条目';

-- 已有数据库迁移：为 knowledge_entry 补充 type 属性分类字段（幂等由应用启动器保证）
ALTER TABLE `knowledge_entry` ADD COLUMN `type` VARCHAR(50) DEFAULT '其他' COMMENT '属性分类' AFTER `user_id`;
ALTER TABLE `knowledge_entry` ADD COLUMN `batch_id` VARCHAR(64) DEFAULT NULL COMMENT '入库批次标识' AFTER `content`;

CREATE TABLE IF NOT EXISTS `target_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `job_name` VARCHAR(200) NOT NULL COMMENT '岗位名称',
  `job_requirement` TEXT NOT NULL COMMENT '岗位要求/招聘JD',
  `company_name` VARCHAR(255) DEFAULT NULL COMMENT '招聘单位',
  `email` VARCHAR(255) DEFAULT NULL COMMENT '招聘邮箱',
  `tech_stack` VARCHAR(500) DEFAULT NULL COMMENT '核心技术栈（选填）',
  `job_type` VARCHAR(50) DEFAULT NULL COMMENT '岗位类型标签（AI 自动识别）',
  `job_link` VARCHAR(500) DEFAULT NULL COMMENT '岗位原始详情链接',
  `source_platform` VARCHAR(100) DEFAULT NULL COMMENT '来源平台',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='我的目标岗位';

-- 已有数据库迁移：为 target_job 补充 job_type 岗位类型标签字段（幂等由应用启动器保证）
ALTER TABLE `target_job` ADD COLUMN `job_type` VARCHAR(50) DEFAULT NULL COMMENT '岗位类型标签（AI 自动识别）' AFTER `tech_stack`;
-- 已有数据库迁移：为 target_job 补充招聘单位与招聘邮箱字段（幂等由应用启动器保证）
ALTER TABLE `target_job` ADD COLUMN `company_name` VARCHAR(255) DEFAULT NULL COMMENT '招聘单位' AFTER `job_requirement`;
ALTER TABLE `target_job` ADD COLUMN `email` VARCHAR(255) DEFAULT NULL COMMENT '招聘邮箱' AFTER `company_name`;
-- 已有数据库迁移：为 target_job 补充岗位原始详情链接（幂等由应用启动器保证）
ALTER TABLE `target_job` ADD COLUMN `job_link` VARCHAR(500) DEFAULT NULL COMMENT '岗位原始详情链接' AFTER `job_type`;
-- 已有数据库迁移：为 target_job 补充来源平台标签（幂等由应用启动器保证）
ALTER TABLE `target_job` ADD COLUMN `source_platform` VARCHAR(100) DEFAULT NULL COMMENT '来源平台' AFTER `job_link`;

CREATE TABLE IF NOT EXISTS `job_match_result` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `fingerprint` VARCHAR(128) NOT NULL COMMENT '知识库+岗位库内容指纹（sha256 + 版本后缀）',
  `result_json` MEDIUMTEXT NOT NULL COMMENT '排名结果 JSON',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能岗位匹配评估结果缓存';

-- 已有数据库迁移：指纹列扩容（v5 指纹 = sha256 64 位 + "|rank-v5"，共 72 字符；
-- 原 varchar(64) 在严格模式下写入报 Data too long，导致评估结果无法落库）
ALTER TABLE `job_match_result` MODIFY COLUMN `fingerprint` VARCHAR(128) NOT NULL COMMENT '知识库+岗位库内容指纹（sha256 + 版本后缀）';

CREATE TABLE IF NOT EXISTS `job_application` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `job_id` BIGINT DEFAULT NULL COMMENT '关联目标岗位ID',
  `job_name` VARCHAR(200) DEFAULT NULL COMMENT '岗位名称',
  `company_name` VARCHAR(255) DEFAULT NULL COMMENT '公司名称',
  `applied_at` DATETIME DEFAULT NULL COMMENT '投递时间',
  `resume_version` VARCHAR(200) DEFAULT NULL COMMENT '简历版本',
  `job_link` VARCHAR(500) DEFAULT NULL COMMENT '岗位跳转链接',
  `status` VARCHAR(20) DEFAULT NULL COMMENT '投递状态',
  `remark` VARCHAR(1000) DEFAULT NULL COMMENT '备注',
  `draft` TINYINT DEFAULT '1' COMMENT '1草稿/0正式',
  `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
  `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_draft` (`user_id`,`draft`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投递台账';

-- 已有数据库迁移：为 user_preference 补充投递跟进提醒天数（幂等由应用启动器保证）
ALTER TABLE `user_preference` ADD COLUMN `follow_up_days` INT DEFAULT '7' COMMENT '投递跟进提醒天数' AFTER `expected_salary`;