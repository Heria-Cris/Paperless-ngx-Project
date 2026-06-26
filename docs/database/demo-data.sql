USE paperless_ngx_project;

INSERT INTO sys_user (id, username, password_hash, nickname, role, status)
VALUES
  (1, 'admin', 'TEMP_HASH_admin123_replace_later', '管理员', 'ADMIN', 1),
  (2, 'user', 'TEMP_HASH_user123_replace_later', '普通用户', 'USER', 1)
ON DUPLICATE KEY UPDATE
  nickname = VALUES(nickname),
  role = VALUES(role),
  status = VALUES(status);

INSERT INTO document_category (id, name, description)
VALUES
  (1, '合同', '业务合同、协议等文件'),
  (2, '发票', '财务票据和报销材料'),
  (3, '证书', '证书扫描件和证明材料'),
  (4, '课程资料', '课件、作业和实训材料'),
  (5, '报告', '总结、调研和分析报告')
ON DUPLICATE KEY UPDATE
  description = VALUES(description);

INSERT INTO document_tag (id, name, color)
VALUES
  (1, '重要', '#c9773d'),
  (2, '待处理', '#62c7bd'),
  (3, '已归档', '#51a548'),
  (4, '学校', '#5d35b7'),
  (5, '公司', '#9b4bd8')
ON DUPLICATE KEY UPDATE
  color = VALUES(color);

INSERT INTO document (
  id, title, original_filename, stored_filename, storage_path,
  file_size, file_type, category_id, upload_user_id, description, deleted
)
VALUES
  (1, 'Java Web 实训需求文档', 'java-web-prd.pdf', '20260626_1_java-web-prd.pdf', 'uploads/2026/06/user_1/20260626_1_java-web-prd.pdf', 204800, 'PDF', 4, 1, '课程实训需求说明', 0),
  (2, '报销发票样例', 'invoice-demo.png', '20260626_2_invoice-demo.png', 'uploads/2026/06/user_2/20260626_2_invoice-demo.png', 102400, 'PNG', 2, 2, '普通用户上传的发票样例', 0),
  (3, '项目阶段总结', 'project-summary.docx', '20260626_3_project-summary.docx', 'uploads/2026/06/user_1/20260626_3_project-summary.docx', 307200, 'DOCX', 5, 1, '阶段性总结报告', 0)
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  description = VALUES(description),
  deleted = VALUES(deleted);

INSERT INTO document_tag_rel (document_id, tag_id)
VALUES
  (1, 1),
  (1, 4),
  (2, 2),
  (3, 1),
  (3, 3)
ON DUPLICATE KEY UPDATE
  document_id = VALUES(document_id);

INSERT INTO operation_log (user_id, username, operation_type, target_type, target_id, ip_address, result)
VALUES
  (1, 'admin', 'LOGIN', 'USER', 1, '127.0.0.1', 'SUCCESS'),
  (1, 'admin', 'UPLOAD_DOCUMENT', 'DOCUMENT', 1, '127.0.0.1', 'SUCCESS'),
  (2, 'user', 'UPLOAD_DOCUMENT', 'DOCUMENT', 2, '127.0.0.1', 'SUCCESS');
