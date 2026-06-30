USE paperless_ngx_project;

ALTER TABLE sys_user
  ADD COLUMN avatar_url VARCHAR(500) NULL COMMENT '头像地址' AFTER nickname,
  ADD COLUMN email VARCHAR(120) NULL COMMENT '邮箱' AFTER avatar_url,
  ADD COLUMN phone VARCHAR(30) NULL COMMENT '手机号' AFTER email,
  ADD COLUMN bio VARCHAR(500) NULL COMMENT '个人简介' AFTER phone;

UPDATE sys_user
SET
  password_hash = '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9',
  avatar_url = COALESCE(avatar_url, '/images/default-avatar.svg'),
  email = COALESCE(email, 'admin@example.com'),
  phone = COALESCE(phone, '13800000001'),
  bio = COALESCE(bio, '系统管理员账号')
WHERE username = 'admin';

UPDATE sys_user
SET
  password_hash = 'e606e38b0d8c19b24cf0ee3808183162ea7cd63ff7912dbb22b5e803286b4446',
  avatar_url = COALESCE(avatar_url, '/images/default-avatar.svg'),
  email = COALESCE(email, 'user@example.com'),
  phone = COALESCE(phone, '13800000002'),
  bio = COALESCE(bio, '演示普通用户账号')
WHERE username = 'user';
