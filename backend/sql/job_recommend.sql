-- 智能岗位推荐：爬虫岗位 + 用户简历技能向量 + 用户目标岗位关联
-- 运行时同样由 SchemaInitializer 幂等建表，本文件用于手工初始化/交付。
CREATE TABLE IF NOT EXISTS `crawl_job` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `job_name` varchar(255) NOT NULL COMMENT '岗位名称',
  `company_name` varchar(255) DEFAULT NULL COMMENT '公司名称',
  `location` varchar(255) DEFAULT NULL COMMENT '工作地点',
  `salary` varchar(128) DEFAULT NULL COMMENT '薪资范围',
  `job_desc` text COMMENT '岗位描述',
  `requirement` text COMMENT '岗位要求',
  `source_url` varchar(1000) NOT NULL COMMENT '外部原始岗位链接',
  `crawl_date` date NOT NULL COMMENT '爬取日期',
  `crawl_time` datetime NOT NULL COMMENT '爬取时间',
  `job_vector` longtext COMMENT '岗位向量（JSON 数组）',
  PRIMARY KEY (`id`),
  KEY `idx_crawl_date` (`crawl_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='爬虫岗位表';

CREATE TABLE IF NOT EXISTS `user_resume` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `skill_vector` longtext COMMENT '技能栈向量（JSON 数组）',
  `skill_tags` json DEFAULT NULL COMMENT '技能标签（JSON 数组）',
  `material_hash` varchar(64) DEFAULT NULL COMMENT '简历素材内容哈希',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户简历技能向量';

CREATE TABLE IF NOT EXISTS `user_target_job` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `crawl_job_id` bigint NOT NULL COMMENT '爬虫岗位ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_job` (`user_id`,`crawl_job_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户目标岗位关联表';