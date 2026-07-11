-- Apply this migration to existing travelchart databases before deploying travel-chart-manage.
CREATE TABLE IF NOT EXISTS `tg_admin_user` (
  `admin_id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(64) NOT NULL,
  `password_hash` VARCHAR(128) NOT NULL,
  `display_name` VARCHAR(64) DEFAULT NULL,
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0-禁用 1-正常',
  `last_login_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`admin_id`),
  UNIQUE KEY `uk_admin_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理后台管理员账号';

ALTER TABLE `tg_poi`
  ADD COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0-下线 1-上架' AFTER `indoor`;
