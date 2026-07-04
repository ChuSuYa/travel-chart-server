-- ========================================
-- 途划 TravelChart 数据库初始化脚本
-- ========================================

CREATE DATABASE IF NOT EXISTS travelchart DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE travelchart;

-- 用户表
CREATE TABLE IF NOT EXISTS `tg_user` (
  `user_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
  `password_hash` VARCHAR(128) DEFAULT NULL COMMENT 'BCrypt 密码哈希',
  `nickname` VARCHAR(64) DEFAULT NULL COMMENT '昵称',
  `avatar_url` VARCHAR(256) DEFAULT NULL COMMENT '头像URL',
  `theme_mode` VARCHAR(16) DEFAULT 'system' COMMENT '主题模式: light/dark/system',
  `language` VARCHAR(16) DEFAULT 'zh-CN' COMMENT '语言',
  `inspiration` INT DEFAULT 0 COMMENT '灵感值',
  `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用 1-正常',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 出行人表
CREATE TABLE IF NOT EXISTS `tg_traveler` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `name` VARCHAR(64) NOT NULL COMMENT '姓名',
  `age` INT DEFAULT 0 COMMENT '年龄',
  `type` VARCHAR(16) NOT NULL DEFAULT 'adult' COMMENT '类型: adult/child/senior',
  `relation_tag` VARCHAR(32) DEFAULT NULL COMMENT '关系标签: couple/parent-child/bestie/solo/family/friends',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出行人表';

-- 行程表
CREATE TABLE IF NOT EXISTS `tg_plan` (
  `plan_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `title` VARCHAR(128) DEFAULT NULL COMMENT '行程标题',
  `destinations` JSON DEFAULT NULL COMMENT '目的地列表',
  `date_range` JSON DEFAULT NULL COMMENT '日期范围 {start, end}',
  `day_count` INT DEFAULT 0 COMMENT '天数',
  `total_budget` DECIMAL(10,2) DEFAULT 0 COMMENT '总预算',
  `per_day_budget` DECIMAL(10,2) DEFAULT 0 COMMENT '日均预算',
  `pace` VARCHAR(32) DEFAULT NULL COMMENT '节奏',
  `themes` JSON DEFAULT NULL COMMENT '主题列表',
  `preferences` JSON DEFAULT NULL COMMENT '偏好快照',
  `plan_json` MEDIUMTEXT DEFAULT NULL COMMENT '完整行程JSON',
  `highlights` JSON DEFAULT NULL COMMENT '行程亮点',
  `status` VARCHAR(32) DEFAULT 'PLANNING' COMMENT 'PLANNING/TRAVELED/SHARED/SAVED',
  `is_cloned` TINYINT DEFAULT 0 COMMENT '是否克隆',
  `cloned_from` BIGINT DEFAULT NULL COMMENT '克隆源',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`plan_id`),
  KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程表';

-- POI 表
CREATE TABLE IF NOT EXISTS `tg_poi` (
  `poi_id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(128) NOT NULL COMMENT '名称',
  `city` VARCHAR(64) DEFAULT NULL COMMENT '城市',
  `type` VARCHAR(32) DEFAULT NULL COMMENT '类型: food/drink/play/enjoy/hotel/transport',
  `sub_type` VARCHAR(64) DEFAULT NULL COMMENT '子类型',
  `lat` DOUBLE DEFAULT NULL,
  `lng` DOUBLE DEFAULT NULL,
  `address` VARCHAR(256) DEFAULT NULL,
  `rating` DECIMAL(2,1) DEFAULT 0,
  `price_level` INT DEFAULT 1 COMMENT '价格档位 1-5',
  `opening_hours` VARCHAR(128) DEFAULT NULL,
  `tags` JSON DEFAULT NULL COMMENT '标签',
  `seasonality` JSON DEFAULT NULL COMMENT '季节性',
  `indoor` TINYINT DEFAULT 0 COMMENT '是否室内',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`poi_id`),
  KEY `idx_city_type` (`city`, `type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='POI表';

-- 天气快照表
CREATE TABLE IF NOT EXISTS `tg_weather_snapshot` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `plan_id` BIGINT NOT NULL COMMENT '关联行程',
  `city` VARCHAR(64) NOT NULL COMMENT '城市',
  `forecast_date` DATE NOT NULL COMMENT '预报日期',
  `temp_high` INT DEFAULT NULL COMMENT '最高温',
  `temp_low` INT DEFAULT NULL COMMENT '最低温',
  `condition_code` VARCHAR(32) DEFAULT NULL COMMENT '天气代码',
  `condition_text` VARCHAR(64) DEFAULT NULL COMMENT '天气描述',
  `humidity` INT DEFAULT NULL COMMENT '湿度',
  `uv_index` INT DEFAULT NULL COMMENT '紫外线指数',
  `aqi` INT DEFAULT NULL COMMENT '空气质量',
  `wind_direction` VARCHAR(16) DEFAULT NULL,
  `wind_speed` VARCHAR(16) DEFAULT NULL,
  `alert_level` VARCHAR(16) DEFAULT NULL COMMENT '预警级别',
  `alert_text` VARCHAR(256) DEFAULT NULL COMMENT '预警内容',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_plan_city_date` (`plan_id`, `city`, `forecast_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='天气快照表';

-- 提醒日志表
CREATE TABLE IF NOT EXISTS `tg_reminder_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `plan_id` BIGINT NOT NULL COMMENT '关联行程',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `reminder_type` VARCHAR(32) NOT NULL COMMENT '类型: PRE_TRIP_3D/PRE_TRIP_1D/DAILY_MORNING/ALERT',
  `title` VARCHAR(128) NOT NULL COMMENT '提醒标题',
  `content` TEXT DEFAULT NULL COMMENT '提醒内容',
  `is_read` TINYINT DEFAULT 0 COMMENT '是否已读',
  `push_status` VARCHAR(16) DEFAULT 'pending' COMMENT '推送状态',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`, `is_read`),
  KEY `idx_plan` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提醒日志表';

-- 分享卡片表
CREATE TABLE IF NOT EXISTS `tg_share_card` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `plan_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `card_image_url` VARCHAR(256) DEFAULT NULL COMMENT '卡片图片URL',
  `card_data` JSON DEFAULT NULL COMMENT '卡片数据',
  `share_count` INT DEFAULT 0 COMMENT '分享次数',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_plan` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分享卡片表';

-- 同行招募表
CREATE TABLE IF NOT EXISTS `tg_companion` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `plan_id` BIGINT DEFAULT NULL,
  `destinations` JSON DEFAULT NULL,
  `date_range` JSON DEFAULT NULL,
  `budget` DECIMAL(10,2) DEFAULT NULL,
  `pace` VARCHAR(32) DEFAULT NULL,
  `description` VARCHAR(512) DEFAULT NULL,
  `status` VARCHAR(16) DEFAULT 'active',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_active` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同行招募表';

-- 用户行为追踪表 (Kafka → Flink → MySQL/StarRocks 回流)
CREATE TABLE IF NOT EXISTS `tg_user_behavior` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `poi_id` BIGINT DEFAULT NULL COMMENT 'POI ID',
  `action_type` VARCHAR(32) NOT NULL COMMENT '行为类型: click/view/fav/share/search',
  `target_id` VARCHAR(128) DEFAULT NULL COMMENT '目标ID',
  `metadata` JSON DEFAULT NULL COMMENT '扩展数据',
  `event_time` DATETIME NOT NULL COMMENT '事件时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `event_time`),
  KEY `idx_poi` (`poi_id`),
  KEY `idx_action_type` (`action_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为追踪表';

-- 用户偏好画像表 (偏好学习引擎写入)
CREATE TABLE IF NOT EXISTS `tg_user_profile` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `preferred_themes` JSON DEFAULT NULL COMMENT '偏好主题权重',
  `preferred_activities` JSON DEFAULT NULL COMMENT '偏好活动权重',
  `budget_preference` VARCHAR(16) DEFAULT NULL COMMENT '预算偏好: budget/comfort/luxury',
  `pace_preference` VARCHAR(16) DEFAULT NULL COMMENT '节奏偏好: slow/compact/intensive',
  `favorite_cities` JSON DEFAULT NULL COMMENT '偏好城市列表',
  `preferred_season` VARCHAR(32) DEFAULT NULL COMMENT '偏好出行季节',
  `visit_count` INT DEFAULT 0 COMMENT '出行次数',
  `last_plan_time` DATETIME DEFAULT NULL COMMENT '最近一次规划时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户偏好画像表';

-- ========================================
-- MongoDB 集合设计 (文档说明)
-- ========================================
-- 1. poitable_list  (POI知识库全量表，含名称/坐标/标签/营业时间/票价/季节性等)
-- 2. share_templates  (攻略卡片模板，含封面/布局/字体/配色)
-- 3. weather_archives (天气历史归档，旅行结束后保存)
-- 4. climate_diaries  (气候日记，整合天气+打卡点→可视化长图数据)
-- 5. user_feature_vectors  (用户特征向量，用于协同过滤推荐)
-- 6. system_config  (系统配置/开关/运营参数)

-- ========================================
-- Elasticsearch 索引设计 (文档说明)
-- ========================================
-- 索引: travel_poi
-- 字段:
--   - id (keyword)
--   - name (text, ik_max_word)
--   - description (text, ik_smart)
--   - city (keyword)
--   - location (geo_point)
--   - tags (keyword array)
--   - type (keyword)
--   - sub_type (keyword)
--   - price_level (integer)
--   - rating (float)
--   - heatScore (integer, 用于排序)
--   - indoor (boolean)
--   - seasonality (keyword)
--   - opening_hours (keyword)
--   - image_url (keyword)
--   - create_time (date)

