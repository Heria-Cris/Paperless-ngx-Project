# Database Setup

> 阶段 3 开始接入 MySQL 和 MyBatis-Plus。本说明用于本地 IDEA、Navicat、DataGrip 或 MySQL 命令行初始化数据库。

## 你需要准备什么

- 本地安装 MySQL 8。
- 确认 MySQL 服务已启动。
- 准备一个可建库、建表的账号，例如 `root`。
- 在 IDEA 中安装 Java 17，并使用 Maven 导入项目。

## 默认数据库配置

项目默认读取以下配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/paperless_ngx_project?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: 123456
```

如果你的 MySQL 账号密码不是 `root/123456`，有两种方式修改。

方式一：直接修改 `src/main/resources/application.yml`。

方式二：在 IDEA Run Configuration 中添加环境变量：

```text
DB_URL=jdbc:mysql://localhost:3306/paperless_ngx_project?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
DB_USERNAME=你的用户名
DB_PASSWORD=你的密码
```

推荐方式二，避免把自己的本地密码提交到 Git。

## 初始化数据库

在 MySQL 客户端执行：

```sql
source E:/workspace/2606 ShiXun/Paperless-ngx-Project/docs/database/schema.sql;
source E:/workspace/2606 ShiXun/Paperless-ngx-Project/docs/database/demo-data.sql;
```

如果你已经在本地初始化过旧版本数据库，不想删库重建，请先执行本阶段升级脚本一次：

```sql
source E:/workspace/2606 ShiXun/Paperless-ngx-Project/docs/database/upgrade-stage8-users.sql;
```

然后再执行 `demo-data.sql` 刷新演示账号信息。升级脚本会为 `sys_user` 增加头像、邮箱、手机号和简介字段，并把演示账号密码更新为 SHA-256 哈希。

如果使用 Navicat 或 DataGrip：

1. 连接本地 MySQL。
2. 打开 `docs/database/schema.sql`，执行全部 SQL。
3. 打开 `docs/database/demo-data.sql`，执行全部 SQL。

## 阶段 3 验收重点

- 表是否创建成功。
- 演示数据是否插入成功。
- Maven 是否能正常编译。
- 启动项目时 MySQL 连接是否正常。

## 注意

- 当前阶段只完成表结构、实体、Mapper、Service 基础模型。
- 页面仍然使用阶段 1 的模拟数据。
- 阶段 4 开始会把分类和标签页面接入真实数据库 CRUD。
- `sys_user.password_hash` 当前演示数据是临时值，后续接入数据库登录时会改为真实密码哈希。
