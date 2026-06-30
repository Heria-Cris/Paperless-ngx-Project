CREATE DATABASE IF NOT EXISTS paperless_ngx_project
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE paperless_ngx_project;

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE COMMENT '登录账号',
  password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
  nickname VARCHAR(80) NOT NULL COMMENT '显示名称',
  avatar_url VARCHAR(500) NULL COMMENT '头像地址',
  email VARCHAR(120) NULL COMMENT '邮箱',
  phone VARCHAR(30) NULL COMMENT '手机号',
  bio VARCHAR(500) NULL COMMENT '个人简介',
  role VARCHAR(20) NOT NULL COMMENT 'ADMIN / USER',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_sys_user_role (role),
  INDEX idx_sys_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS document_category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL UNIQUE COMMENT '分类名称',
  description VARCHAR(255) NULL COMMENT '分类描述',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档分类表';

CREATE TABLE IF NOT EXISTS document_tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL UNIQUE COMMENT '标签名称',
  color VARCHAR(20) NOT NULL DEFAULT '#62c7bd' COMMENT '标签颜色',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档标签表';

CREATE TABLE IF NOT EXISTS document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL COMMENT '文档标题',
  original_filename VARCHAR(255) NOT NULL COMMENT '原始文件名',
  stored_filename VARCHAR(255) NULL COMMENT '存储文件名',
  storage_path VARCHAR(500) NULL COMMENT '本地存储路径',
  file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小，单位字节',
  file_type VARCHAR(50) NOT NULL COMMENT '文件类型或扩展名',
  category_id BIGINT NULL COMMENT '分类ID',
  upload_user_id BIGINT NOT NULL COMMENT '上传人ID',
  description VARCHAR(1000) NULL COMMENT '文档描述',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1逻辑删除',
  uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_document_user_deleted (upload_user_id, deleted),
  INDEX idx_document_category (category_id),
  INDEX idx_document_file_type (file_type),
  INDEX idx_document_uploaded_at (uploaded_at),
  INDEX idx_document_title (title),
  CONSTRAINT fk_document_category FOREIGN KEY (category_id) REFERENCES document_category(id),
  CONSTRAINT fk_document_user FOREIGN KEY (upload_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档元数据表';

CREATE TABLE IF NOT EXISTS document_tag_rel (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_document_tag (document_id, tag_id),
  INDEX idx_document_tag_rel_document (document_id),
  INDEX idx_document_tag_rel_tag (tag_id),
  CONSTRAINT fk_document_tag_rel_document FOREIGN KEY (document_id) REFERENCES document(id),
  CONSTRAINT fk_document_tag_rel_tag FOREIGN KEY (tag_id) REFERENCES document_tag(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档标签关联表';

CREATE TABLE IF NOT EXISTS operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NULL COMMENT '操作用户ID',
  username VARCHAR(50) NULL COMMENT '操作用户名',
  operation_type VARCHAR(50) NOT NULL COMMENT '操作类型',
  target_type VARCHAR(50) NULL COMMENT '操作对象类型',
  target_id BIGINT NULL COMMENT '操作对象ID',
  ip_address VARCHAR(64) NULL COMMENT '操作IP',
  result VARCHAR(30) NOT NULL DEFAULT 'SUCCESS' COMMENT '操作结果',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_operation_log_user (user_id),
  INDEX idx_operation_log_type (operation_type),
  INDEX idx_operation_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';
