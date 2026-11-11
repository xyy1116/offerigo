-- 演示数据：为 admin(user_id=1) 准备素材、目标岗位与每日推荐，并把 admin/reviewer 密码重置为 xxy123456
-- 用法：docker exec -i chuan-mysql mysql -uroot -p<密码> --default-character-set=utf8mb4 chuan < deploy/seed-demo.sql
-- 演示用默认数据：密码统一改为 xxy123456，并为 admin 准备一份可用的素材/岗位/推荐数据
UPDATE sys_user SET password = '$2a$10$gSHf08Mp9CKOE5gK4Nw10uy9UraYC4z2jmnsxGNJCJu.LYseRzjgK' WHERE username IN ('admin', 'reviewer');

DELETE FROM knowledge_entry WHERE user_id = 1;
INSERT INTO knowledge_entry (user_id, type, content, split_version) VALUES
(1, '基础信息', '个人概况：徐宇阳，男，22岁，现居江苏南京，求职意向为 Java 后端开发', 2),
(1, '基础信息', '联系方式：电话 13800000000，邮箱 2758507584@qq.com', 2),
(1, '教育经历', '学历背景：三江学院 计算机科学与技术 本科 2023.09-2027.06，主修 Java 程序设计、数据结构、数据库原理、计算机网络', 2),
(1, '技能证书', '后端技术：Java、Spring Boot、MyBatis-Plus、MySQL、Redis、JWT 鉴权、RESTful 接口设计', 2),
(1, '技能证书', '前端技术：Vue 3、Vite、Pinia、Element Plus、Tailwind CSS、ECharts', 2),
(1, '技能证书', 'AI 应用：大模型 API 集成、RAG 检索增强、Function Calling 工具调用、Prompt 工程、向量检索', 2),
(1, '项目经历', 'OfferiGo 智能求职助手：独立开发，前端 Vue 3 + 后端 Spring Boot，集成 DeepSeek 与通义千问，实现简历素材库、岗位匹配、定制简历与求职信、语音模拟面试、AI 笔试训练', 2),
(1, '项目经历', '校园二手交易平台：负责后端开发，基于 Spring Boot + MySQL + Redis 实现商品发布、关键词搜索与订单流程，接口平均响应小于 200ms', 2),
(1, '实习经历', '电商后台管理系统实习生：参与商品与订单模块开发，负责接口编写与 SQL 慢查询优化，将列表查询耗时从 1.2s 降至 300ms', 2),
(1, '荣誉奖项', '竞赛奖项：蓝桥杯江苏省赛三等奖、校级程序设计竞赛二等奖', 2);

DELETE FROM target_job WHERE user_id = 1;
INSERT INTO target_job (user_id, job_name, job_requirement, company_name, location, salary, tech_stack, job_type, source_platform) VALUES
(1, 'Java 后端开发工程师',
 '- 负责业务系统后端接口设计与开发\n- 熟悉 Java、Spring Boot、MySQL、Redis\n- 有分布式、消息队列使用经验者优先\n- 本科及以上学历，计算机相关专业\n\n补充信息：南京，12-18K，六险一金',
 '南京某科技有限公司', '南京', '12-18K', 'Java、Spring Boot、MySQL、Redis', 'Java开发', '手动添加'),
(1, 'AI 应用开发工程师',
 '- 负责大模型应用的设计与落地\n- 熟悉 RAG、Agent、Function Calling 等应用范式\n- 具备前后端联动开发能力\n- 有 Prompt 调优经验者优先\n\n补充信息：南京，15-25K',
 '南京某智能科技公司', '南京', '15-25K', '大模型、RAG、Agent、Spring Boot', 'AI应用', '手动添加'),
(1, '前端开发工程师（Vue）',
 '- 负责 Web 前端页面开发与性能优化\n- 熟悉 Vue 3、TypeScript、构建工具\n- 具备组件化与状态管理实践经验\n\n补充信息：南京，10-15K',
 '南京某软件公司', '南京', '10-15K', 'Vue、TypeScript、Vite', '前端开发', '手动添加');

DELETE FROM job_recommendation WHERE user_id = 1;
INSERT INTO job_recommendation (user_id, job_name, company_name, location, salary, source_platform, job_requirement, match_score, recommend_date, is_new) VALUES
(1, 'Java 后端开发工程师', '南京云智信息科技', '南京', '13-18K', '智联招聘', '- 熟悉 Java、Spring Boot、MySQL\n- 有高并发场景经验优先', 92.5, CURDATE(), 1),
(1, 'AI 应用开发工程师', '南京星海智能', '南京', '16-25K', '腾讯招聘', '- 负责大模型应用落地\n- 熟悉 RAG 与工具调用', 88.0, CURDATE(), 1),
(1, '后端开发工程师（校招）', '杭州数联科技', '杭州', '12-16K', '字节跳动', '- Java 基础扎实\n- 熟悉 MySQL、Redis', 80.5, CURDATE(), 0),
(1, '全栈开发工程师', '上海联创软件', '上海', '14-20K', '智联招聘', '- 熟悉 Vue 与 Spring Boot\n- 能独立完成前后端开发', 76.0, CURDATE(), 0),
(1, '大模型应用开发（实习）', '南京智谱合作企业', '南京', '200-300元/天', '腾讯招聘', '- 熟悉 Prompt 工程与向量检索\n- 每周可到岗 4 天', 71.5, CURDATE(), 0),
(1, '前端开发工程师', '南京灵动科技', '南京', '10-14K', '智联招聘', '- 熟悉 Vue 3、TypeScript\n- 有组件库实践', 68.0, CURDATE(), 0);