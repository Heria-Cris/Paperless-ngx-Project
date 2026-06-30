USE paperless_ngx_project;

CREATE TABLE IF NOT EXISTS document_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  document_id BIGINT NOT NULL COMMENT '关联文档ID',
  user_id BIGINT NOT NULL COMMENT '任务所属用户ID',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / DONE',
  priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' COMMENT 'HIGH / MEDIUM / LOW',
  due_at DATETIME NOT NULL COMMENT '最晚处理时间',
  handled_at DATETIME NULL COMMENT '处理完成时间',
  note VARCHAR(500) NULL COMMENT '任务说明',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_document_task_user_status_due (user_id, status, due_at),
  INDEX idx_document_task_document (document_id),
  CONSTRAINT fk_document_task_document FOREIGN KEY (document_id) REFERENCES document(id),
  CONSTRAINT fk_document_task_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件处理任务表';

INSERT INTO document_task (id, document_id, user_id, status, priority, due_at, note)
SELECT 1, 1, 1, 'PENDING', 'HIGH', DATE_ADD(NOW(), INTERVAL 1 DAY), '检查需求文档是否需要补充最新版'
WHERE EXISTS (SELECT 1 FROM document WHERE id = 1)
ON DUPLICATE KEY UPDATE
  status = VALUES(status),
  priority = VALUES(priority),
  due_at = VALUES(due_at),
  note = VALUES(note);
