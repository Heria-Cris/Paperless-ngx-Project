# Development Technical Notes

## 阶段 6：真实文件上传与下载

### 目标

阶段 6 的目标是把阶段 5 的“元数据占位上传”升级为真实文件上传与下载。系统不再要求用户手动输入原始文件名和文件类型，而是从上传文件中自动解析并保存到数据库，同时将真实文件写入本地 `uploads` 目录。

### 技术方案

- 使用 Spring MVC `MultipartFile` 接收上传文件。
- 使用 `FileStorageService` 封装文件存储逻辑，避免 Controller 直接处理底层文件路径。
- 使用 `app.upload-dir` 配置上传根目录，默认值为 `uploads`。
- 文件实体仍然保存到本地磁盘，数据库只保存文件路径和元数据。
- 使用 `ResponseEntity<Resource>` 返回下载响应。
- 使用 `Content-Disposition: attachment` 保证浏览器按原始文件名下载。

### 文件存储实现

新增服务：

```text
src/main/java/com/paperless/local/service/FileStorageService.java
```

核心职责：

- 校验文件是否为空。
- 使用 `StringUtils.cleanPath` 清洗原始文件名。
- 拒绝包含 `..`、`/`、`\` 的文件名，避免路径穿越。
- 校验扩展名，只允许 `pdf`、`doc`、`docx`、`xls`、`xlsx`、`ppt`、`pptx`、`png`、`jpg`、`jpeg`、`txt`、`csv`。
- 按日期和用户 ID 创建目录：

```text
uploads/{year}/{month}/user_{userId}/
```

- 使用 UUID 生成服务器存储文件名，避免同名覆盖。
- 返回 `StoredFile` 记录原始文件名、存储文件名、存储路径、文件大小和文件类型。

### 上传流程

路由：

```text
POST /documents
```

实现流程：

1. 用户在 `/documents/upload` 选择真实文件并填写标题、分类、标签、描述。
2. Controller 校验标题不能为空。
3. 调用 `FileStorageService.store(file, userId)` 将文件保存到本地。
4. 将文件元数据写入 `document` 表：
   - `original_filename`
   - `stored_filename`
   - `storage_path`
   - `file_size`
   - `file_type`
   - `category_id`
   - `upload_user_id`
   - `description`
5. 将选择的标签写入 `document_tag_rel`。
6. 上传成功后重定向到文档详情页。

### 下载流程

路由：

```text
GET /documents/{id}/download
```

实现流程：

1. 根据文档 ID 查询文档。
2. 调用已有权限逻辑判断当前用户是否可访问该文档。
3. 通过 `storage_path` 解析真实文件路径。
4. 检查文件是否存在且是普通文件。
5. 使用 `UrlResource` 包装文件。
6. 使用 UTF-8 编码的原始文件名设置下载响应头。
7. 返回文件资源。

权限规则：

- 管理员可以下载全部文档。
- 普通用户只能下载 `upload_user_id` 属于自己的文档。
- 无权限或文件不存在时返回 404。

### 事务与异常处理

上传文档涉及两类资源：

- 文件系统中的真实文件。
- MySQL 中的文档元数据和标签关系。

当前实现使用 `@Transactional` 保护数据库写入。如果文件已经保存但数据库写入失败，会尝试调用 `deleteIfExists` 清理刚上传的文件，并将当前事务标记为回滚。

逻辑删除文档时暂不物理删除文件，因为当前项目后续还计划支持回收站。此时数据库中的文档记录被逻辑删除，文件仍保留在磁盘中，后续可在“彻底删除”功能中统一清理物理文件。

### 页面改造

`app.html` 上传页从手动输入文件名和文件类型改为：

```html
<form method="post" action="/documents" enctype="multipart/form-data">
    <input type="file" name="file" required>
</form>
```

详情页新增：

- `Download` 按钮。
- 文件大小展示。

### 本阶段验证

已执行：

```powershell
mvn package
```

结果：构建成功，说明 Controller、Service、模板和依赖编译通过。

> 本文件用于记录开发中的技术方案、实现细节和阶段性决策，重点服务于后期答辩和代码讲解。`progress.md` 记录进度，`development-log.md` 记录过程，本文件更偏技术实现说明。

## 阶段 0：项目骨架与运行入口

### 目标

阶段 0 的目标是先创建一个可以启动、可以访问页面的 Spring Boot Web 项目，为后续界面和业务功能开发提供稳定基础。

### 技术选型

- Java 17：符合项目文档要求，也适配 Spring Boot 3。
- Spring Boot 3：用于快速搭建 Web 应用。
- Spring MVC：负责 Controller 路由和页面请求处理。
- Thymeleaf：负责服务端页面渲染。
- Maven：负责依赖管理和项目构建。
- Lombok：为后续实体类、DTO、VO 减少样板代码。
- Hutool：为后续文件处理、日期处理、UUID、加密摘要等工具能力预留。
- Spring Validation：为后续表单参数校验预留。

### 项目结构

阶段 0 建立基础结构：

```text
src/main/java/com/paperless/local
src/main/resources/templates
src/main/resources/static/css
src/main/resources/application.yml
pom.xml
```

核心文件：

- `PaperlessLocalApplication.java`：Spring Boot 启动类。
- `HomeController.java`：阶段 0 首页入口。
- `application.yml`：项目端口、Thymeleaf、上传大小等基础配置。
- `dashboard.html`：阶段 0 首页模板。
- `app.css`：基础页面样式。

### 实现方案

阶段 0 没有直接引入数据库依赖，原因是此时还没有配置 MySQL，如果提前加入 MyBatis-Plus 和 MySQL Driver，应用启动可能因为找不到数据源而失败。

实现流程：

1. 创建 Maven 工程和 `pom.xml`。
2. 引入 Web、Thymeleaf、Validation、Lombok、Hutool。
3. 创建启动类。
4. 创建首页 Controller。
5. 创建基础首页模板。
6. 配置端口 `8080`。
7. 使用 `mvn package` 验证构建。
8. 临时启动 Jar，访问 `/dashboard` 验证页面。

### 测试方式

构建测试：

```powershell
mvn package
```

启动测试：

```powershell
java -jar target/paperless-local-0.0.1-SNAPSHOT.jar
```

浏览器访问：

```text
http://localhost:8080/dashboard
```

预期：

- 项目正常启动。
- 页面返回 HTTP 200。
- 首页可以正常渲染。

## 阶段 1：基础可访问界面

### 目标

阶段 1 的目标是先落地一套基础可访问、可点击、可演示的后台管理界面。页面可以先使用模拟数据，不急于接数据库和真实业务。

### 界面参考

界面风格参考 Paperless-ngx 官方截图：

```text
https://docs.paperless-ngx.com/#screenshots
```

主要参考点：

- 深绿色顶部导航栏。
- 左侧浅色侧边栏。
- 文档列表密集表格。
- 筛选工具条。
- 彩色标签。
- 仪表盘统计面板。
- 后台管理系统风格。

### 页面路由

阶段 1 落地以下页面：

```text
/login
/dashboard
/documents
/documents/upload
/documents/{id}
/documents/{id}/edit
/categories
/tags
/users
/logs
```

### 前端实现

使用 Thymeleaf 服务端渲染。

核心模板：

- `login.html`：登录页。
- `app.html`：后台主模板。
- `app.css`：统一样式。

`app.html` 使用 `activePage` 控制不同页面区域显示：

```text
activePage == 'dashboard'
activePage == 'documents'
activePage == 'upload'
activePage == 'document-detail'
activePage == 'document-edit'
activePage == 'categories'
activePage == 'tags'
activePage == 'users'
activePage == 'logs'
```

这样设计的原因：

- 早期可以快速统一界面风格。
- 避免多个模板重复写顶栏和侧边栏。
- 方便后续逐步替换局部页面为真实数据。

### 后端实现

阶段 1 主要由 `HomeController` 提供页面路由。

页面数据暂时使用 Java 内存模拟数据：

- `DOCUMENTS`：模拟文档列表。
- `CATEGORIES`：模拟分类。
- `TAGS`：模拟标签。

模拟数据的作用：

- 页面可以完整展示文档、标签、统计信息。
- 数据库未完成时仍能演示系统形态。
- 后续阶段可以逐步替换为真实数据库查询。

### 测试方式

启动项目：

```powershell
mvn spring-boot:run
```

浏览器依次访问：

```text
http://localhost:8080/login
http://localhost:8080/dashboard
http://localhost:8080/documents
http://localhost:8080/documents/upload
http://localhost:8080/documents/0
http://localhost:8080/documents/0/edit
http://localhost:8080/categories
http://localhost:8080/tags
http://localhost:8080/users
http://localhost:8080/logs
```

预期：

- 所有页面可以访问。
- 页面风格统一。
- 菜单可以跳转。
- 文档、分类、标签区域有模拟数据。

## 阶段 2：基础登录与页面访问控制

### 目标

阶段 2 的目标是让系统从静态页面进入“有登录状态”的基础 Web 系统，实现登录、Session、退出和基础权限控制。

### 技术方案

使用：

- Session：保存当前登录用户。
- Spring MVC Controller：处理登录和退出。
- HandlerInterceptor：统一拦截未登录访问和管理员权限。
- 内存账号：阶段性替代数据库用户表。

没有使用 Spring Security，原因：

- 当前只需要基础登录和角色判断。
- Session + Interceptor 足够满足 MVP。
- 避免引入复杂配置，降低课程项目实现难度。

### 核心类

- `LoginUser`：保存登录用户信息。
- `AuthService`：校验账号密码。
- `AuthController`：处理 `/login` 和 `/logout`。
- `SessionKeys`：统一 Session key。
- `AuthInterceptor`：未登录拦截和权限判断。
- `WebMvcConfig`：注册拦截器。

### 登录流程

1. 用户访问 `/login`。
2. 提交用户名和密码到 `POST /login`。
3. `AuthController` 调用 `AuthService` 校验。
4. 登录成功后将 `LoginUser` 存入 Session。
5. 重定向到 `/dashboard`。
6. 登录失败则回到登录页并显示错误提示。

测试账号：

```text
admin / admin123
user / user123
```

### 拦截流程

拦截器对后台页面进行统一检查：

1. 获取当前 Session。
2. 从 Session 中读取 `LOGIN_USER`。
3. 如果不存在，跳转 `/login`。
4. 如果访问管理员页面且当前用户不是管理员，跳转 `/dashboard?denied`。
5. 如果权限通过，将当前用户放入 request，供 Thymeleaf 页面读取。

管理员页面：

```text
/categories
/tags
/users
/logs
/dev
```

### 页面实现

登录页：

- 表单提交到 `POST /login`。
- 登录失败显示“用户名或密码错误”。
- 退出后显示“已退出登录”。

后台页：

- 顶栏显示当前用户名称。
- 顶栏显示角色。
- 提供退出入口。
- 管理员显示分类、标签、用户、日志菜单。
- 普通用户隐藏管理员菜单。

### 测试方式

未登录拦截：

```text
直接访问 http://localhost:8080/dashboard
```

预期跳转：

```text
/login
```

管理员测试：

```text
admin / admin123
```

预期：

- 登录成功。
- 可访问 `/categories`、`/tags`、`/users`、`/logs`。

普通用户测试：

```text
user / user123
```

预期：

- 可访问 `/dashboard`、`/documents`。
- 访问 `/categories`、`/tags` 会跳转 `/dashboard?denied`。

退出测试：

```text
http://localhost:8080/logout
```

预期：

- 跳转 `/login?logout`。
- 再访问 `/dashboard` 会重新跳转登录页。

## 阶段 3：数据库表与基础数据模型

### 目标

阶段 3 的目标是接入 MySQL 和 MyBatis-Plus，完成系统核心表结构、实体类、Mapper 和 Service，为后续真实 CRUD 做准备。

### 数据库配置

在 `application.yml` 中配置：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: ${DB_URL:jdbc:mysql://localhost:3306/paperless_ngx_project?...}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:123456}
```

支持环境变量：

```text
DB_URL
DB_USERNAME
DB_PASSWORD
```

这样设计的原因：

- 默认配置方便本地快速启动。
- 环境变量可以避免把个人数据库密码写入 Git。

### MyBatis-Plus 配置

配置内容：

- Mapper XML 扫描路径。
- 下划线转驼峰。
- SQL 日志输出。
- 主键自增。
- 逻辑删除字段 `deleted`。

启动类增加：

```java
@MapperScan("com.paperless.local.mapper")
```

用于扫描 Mapper 接口。

### 表结构设计

核心表：

```text
sys_user
document
document_category
document_tag
document_tag_rel
operation_log
```

设计关系：

- 用户与文档：一对多。
- 分类与文档：一对多。
- 标签与文档：多对多，通过 `document_tag_rel` 实现。
- 操作日志记录用户关键行为。

### 代码模型

实体类：

- `User`
- `Document`
- `DocumentCategory`
- `DocumentTag`
- `DocumentTagRel`
- `OperationLog`

Mapper：

- 每张表对应一个 Mapper。
- Mapper 继承 `BaseMapper<T>`。

Service：

- 每张表对应一个 Service 接口。
- Service 实现类继承 `ServiceImpl<Mapper, Entity>`。

这样设计的原因：

- MyBatis-Plus 可以提供基础 CRUD。
- 后续业务代码可以优先写在 Service 层。
- Controller 不直接操作 Mapper，保持分层清晰。

### SQL 脚本

建表脚本：

```text
docs/database/schema.sql
```

演示数据：

```text
docs/database/demo-data.sql
```

数据库说明：

```text
docs/database/README.md
```

### 数据库检查接口

新增开发期接口：

```text
/dev/database-check
```

作用：

- 验证项目是否能连接数据库。
- 验证 Mapper 和 Service 是否能查询表数据。
- 返回各表记录数。

该接口被归入管理员权限，需要管理员登录后访问。

### 测试方式

先执行 SQL：

```sql
source E:/workspace/2606 ShiXun/Paperless-ngx-Project/docs/database/schema.sql;
source E:/workspace/2606 ShiXun/Paperless-ngx-Project/docs/database/demo-data.sql;
```

启动项目：

```powershell
mvn spring-boot:run
```

管理员登录后访问：

```text
http://localhost:8080/dev/database-check
```

预期返回：

```json
{
  "status": "ok",
  "users": 2,
  "documents": 3,
  "categories": 5,
  "tags": 5,
  "documentTagRelations": 5,
  "operationLogs": 3
}
```

## 阶段 4：分类和标签真实 CRUD

### 目标

阶段 4 的目标是把分类和标签管理从“静态模拟页面”改造为“真实数据库 CRUD 页面”，为后续文档上传、文档编辑和文档筛选提供基础数据。

### 涉及数据表

分类表：

```text
document_category
```

核心字段：

- `id`：分类 ID。
- `name`：分类名称，唯一。
- `description`：分类描述。
- `created_at`：创建时间。
- `updated_at`：更新时间。

标签表：

```text
document_tag
```

核心字段：

- `id`：标签 ID。
- `name`：标签名称，唯一。
- `color`：标签颜色。
- `created_at`：创建时间。
- `updated_at`：更新时间。

文档标签中间表：

```text
document_tag_rel
```

用于表示文档和标签的多对多关系。

### 后端实现

新增 Controller：

- `CategoryController`
- `TagController`

分类路由：

```text
GET  /categories
POST /categories
POST /categories/{id}/update
POST /categories/{id}/delete
```

标签路由：

```text
GET  /tags
POST /tags
POST /tags/{id}/update
POST /tags/{id}/delete
```

使用的 Service：

- `DocumentCategoryService`
- `DocumentTagService`
- `DocumentService`
- `DocumentTagRelService`

查询方式：

```java
Wrappers.<DocumentCategory>lambdaQuery()
```

使用 Lambda Query 的原因：

- 避免直接写数据库字段字符串。
- 字段重命名时更容易被 IDE 和编译器发现。
- 和 MyBatis-Plus 风格保持一致。

### 分类实现方案

新增分类：

1. 接收 `name` 和 `description`。
2. 去除名称前后空格。
3. 校验名称不能为空。
4. 查询数据库，校验名称不能重复。
5. 创建 `DocumentCategory` 对象。
6. 调用 `categoryService.save(category)` 保存。
7. 重定向回 `/categories`。

编辑分类：

1. 根据 ID 查询分类。
2. 校验分类是否存在。
3. 校验新名称不能为空。
4. 查询是否存在同名但不同 ID 的分类。
5. 调用 `categoryService.updateById(category)` 更新。

删除分类：

1. 根据分类 ID 统计文档数量。
2. 如果 `document.category_id = 当前分类ID` 的文档数量大于 0，禁止删除。
3. 如果没有文档引用，调用 `categoryService.removeById(id)` 删除。

这样设计的原因：

- 分类和文档是一对多关系。
- 如果分类下还有文档，直接删除会导致文档分类丢失。
- 因此先禁止删除，后续可以扩展“转移分类后删除”。

### 标签实现方案

新增标签：

1. 接收 `name` 和 `color`。
2. 校验名称不能为空。
3. 校验名称不能重复。
4. 如果颜色为空，使用默认颜色 `#62c7bd`。
5. 调用 `tagService.save(tag)` 保存。

编辑标签：

1. 根据 ID 查询标签。
2. 校验标签是否存在。
3. 校验名称不能为空。
4. 排除当前 ID 后校验名称重复。
5. 更新名称和颜色。

删除标签：

1. 先删除 `document_tag_rel` 中所有 `tag_id = 当前标签ID` 的关联。
2. 再删除 `document_tag` 中的标签记录。

这样设计的原因：

- 标签和文档是多对多关系。
- 删除标签不应该删除文档，只需要解除文档和标签之间的绑定。
- 先删除中间表记录可以避免外键约束冲突。

### 前端实现

仍然使用 Thymeleaf 服务端渲染。

分类和标签共用 `app.html` 中的管理区域：

- 根据 `activePage == 'categories'` 显示分类页面。
- 根据 `activePage == 'tags'` 显示标签页面。

页面包含：

- 新增表单。
- 编辑表单。
- 数据列表。
- 编辑按钮。
- 删除按钮。
- 成功提示。
- 失败提示。

编辑状态通过 URL 参数控制：

```text
/categories?editId=1
/tags?editId=1
```

后端根据 `editId` 查找当前编辑对象，并传给模板中的 `editItem`。

### 权限控制

阶段 2 已经实现管理员拦截：

```text
/categories
/tags
/users
/logs
```

阶段 4 继续复用该权限规则：

- 管理员可以访问分类和标签管理。
- 普通用户不能访问分类和标签管理。
- 普通用户访问会跳转到 `/dashboard?denied`。

### 测试结果

已完成以下验证：

- `mvn package` 构建成功。
- 管理员访问 `/categories` 成功。
- 管理员访问 `/tags` 成功。
- 分类新增成功。
- 分类编辑成功。
- 分类删除成功。
- 标签新增成功。
- 标签编辑成功。
- 标签删除成功。
- 临时测试数据已清理。

### 后续衔接

阶段 5 文档元数据 CRUD 会使用本阶段完成的真实分类和标签数据：

- 文档上传页加载真实分类。
- 文档上传页加载真实标签。
- 文档编辑页可以修改分类和标签。
- 文档列表展示真实分类和标签。

## 阶段 5：文档元数据 CRUD

### 目标

阶段 5 的目标是将文档管理从模拟数据切换为真实数据库数据，完成文档元数据的新增、列表、详情、编辑和逻辑删除。

本阶段仍然不处理真实文件内容。真实文件上传、存储和下载会在阶段 6 完成。

### 涉及数据表

文档表：

```text
document
```

核心字段：

- `id`：文档 ID。
- `title`：文档标题。
- `original_filename`：原始文件名。
- `stored_filename`：服务器存储文件名。
- `storage_path`：服务器存储路径。
- `file_size`：文件大小。
- `file_type`：文件类型。
- `category_id`：分类 ID。
- `upload_user_id`：上传人 ID。
- `description`：文档描述。
- `deleted`：逻辑删除标记。
- `uploaded_at`：上传时间。
- `updated_at`：更新时间。

文档标签关系表：

```text
document_tag_rel
```

用于保存一个文档绑定多个标签的关系。

### 后端实现

新增 Controller：

```text
DocumentController
```

文档路由：

```text
GET  /documents
GET  /documents/upload
POST /documents
GET  /documents/{id}
GET  /documents/{id}/edit
POST /documents/{id}/update
POST /documents/{id}/delete
```

使用的 Service：

- `DocumentService`
- `DocumentTagRelService`
- `DocumentTagService`
- `UserService`
- `DocumentCategoryService`

### 新增文档元数据流程

1. 用户访问 `/documents/upload`。
2. 页面加载真实分类和标签。
3. 用户填写标题、原始文件名、文件类型、分类、标签和描述。
4. 提交到 `POST /documents`。
5. 后端校验标题、原始文件名、文件类型不能为空。
6. 根据当前登录用户查询 `sys_user`，获得上传人 ID。
7. 创建 `Document` 对象并保存到 `document` 表。
8. 保存标签关系到 `document_tag_rel`。
9. 重定向到文档详情页。

阶段 5 中：

- `stored_filename` 是占位生成值。
- `storage_path` 是占位路径。
- `file_size` 暂时为 0。

这样设计的原因：

- 先完成文档元数据 CRUD，让数据库关系跑通。
- 真实文件上传涉及 Multipart、文件校验、本地存储、异常处理，放到阶段 6 更清晰。

### 文档编辑流程

1. 用户访问 `/documents/{id}/edit`。
2. 后端检查文档是否存在。
3. 后端检查当前用户是否有权限访问。
4. 页面展示当前文档标题、原始文件名、文件类型、分类、标签、描述。
5. 用户提交编辑表单。
6. 后端更新 `document` 表。
7. 删除旧的 `document_tag_rel` 关系。
8. 插入新的标签关系。
9. 重定向到文档详情页。

标签更新采用“删除旧关联，再插入新关联”的原因：

- 表单提交的是最终标签集合。
- 重建关联比逐项 diff 更简单。
- 当前项目数据规模小，适合课程实训。

### 文档删除流程

文档删除调用：

```java
documentService.removeById(id)
```

由于 `Document.deleted` 字段使用 `@TableLogic`，MyBatis-Plus 会执行逻辑删除，而不是直接物理删除。

这样设计的原因：

- 符合项目文档中“删除后进入回收站”的后续扩展方向。
- 阶段 5 先让删除后的文档不再出现在正常列表中。
- 阶段后续可以基于 `deleted` 字段实现回收站。

### 文档筛选

当前支持：

- 关键词筛选：标题、原始文件名、描述。
- 分类筛选：`category_id`。
- 标签筛选：通过 `document_tag_rel` 找到文档 ID。

实现方式：

- 先查询文档列表。
- 根据当前用户权限过滤。
- 根据分类、标签、关键词过滤。
- 转换为页面视图对象 `DocumentView`。

后续可优化：

- 将过滤逻辑下沉到数据库 SQL。
- 增加分页。
- 增加排序。

### 权限控制

文档权限规则：

- 管理员可以查看、编辑、删除所有文档。
- 普通用户只能查看、编辑、删除自己的文档。

实现方式：

```text
currentUser.isAdmin() || currentUserId == document.uploadUserId
```

当前登录用户 ID 通过 `sys_user.username` 查询获得。

### 事务处理

文档新增、编辑、删除使用 `@Transactional`。

原因：

- 文档表和标签关系表需要一起更新。
- 如果标签关系保存失败，不应该留下半成品文档。
- 删除文档和后续文件处理也需要事务边界。

### 测试结果

已完成以下验证：

- `mvn package` 构建成功。
- 管理员访问 `/documents` 返回 HTTP 200。
- 文档元数据新增成功。
- 文档详情展示成功。
- 文档元数据编辑成功。
- 文档逻辑删除成功。
- 普通用户访问自己的文档成功。
- 普通用户访问其他用户文档被拒绝。

### 后续衔接

阶段 6 将在当前文档元数据基础上接入真实文件能力：

- Multipart 文件上传。
- 文件大小校验。
- 文件类型校验。
- 唯一文件名。
- 本地目录存储。
- 下载时使用原始文件名。
- 下载前权限校验。

## 阶段 7：搜索、筛选、分页与首页统计

### 目标

阶段 7 的目标是完善文档列表的检索体验和首页统计信息，让系统从“能列出文档”进一步变成“能按条件查找、能分页浏览、能展示真实统计”的管理系统。

### 功能范围

- 文档关键词搜索。
- 分类筛选。
- 标签筛选。
- 搜索条件组合使用。
- 文档列表分页。
- 每页条数选择。
- 分页时保留筛选条件。
- Dashboard 首页统计改为真实数据。
- 首页统计按当前用户权限范围计算。
- 首页新增存储空间统计。

### 搜索与筛选实现

文档列表入口：`GET /documents`。

支持参数：`keyword`、`categoryId`、`tagId`、`page`、`size`。

筛选逻辑位于 `DocumentController.filterDocuments(...)`：先按当前用户权限过滤文档，再按分类、标签、关键词组合筛选，最后转换为页面展示对象 `DocumentView`。

当前实现仍然采用 Java 内存过滤，原因是项目数据量处于课程实训规模，优先保证实现清晰和页面可演示。后续如果文档数量增大，可将筛选、排序和分页下沉到 MyBatis-Plus 分页查询或自定义 SQL。

### 分页实现

新增参数：`page` 表示当前页，默认 1；`size` 表示每页条数，默认 10，最大限制 50。

分页处理流程：先得到过滤后的完整结果列表，再根据 `size` 计算 `totalPages`，修正非法页码，使用 `fromIndex` 和 `toIndex` 截取当前页数据，并向模板传递 `page`、`pageSize`、`totalPages`、`hasPrevious`、`hasNext`、`previousPage`、`nextPage`、`resultTotal`。

页面上新增每页条数下拉框、结果数量、当前页/总页数、Previous/Next 分页按钮。分页链接会保留 `keyword`、`categoryId`、`tagId`、`size`，避免翻页后筛选条件丢失。

### 首页统计实现

首页由 `HomeController.dashboard(...)` 渲染，`prepareApp(...)` 新增了可接收 `LoginUser` 的重载方法：`prepareApp(model, activePage, pageTitle, currentUser)`。

统计规则：管理员统计全部文档；普通用户只统计自己可访问的文档。

统计项包括：`documentTotal`、`inboxTotal`、`storageTotal`、`categoryTotal`、`tagTotal`。其中 `storageTotal` 会通过 `formatBytes(...)` 格式化为 `B`、`KB`、`MB`。

### 权限边界

阶段 7 没有改变阶段 5、阶段 6 的文档权限规则：管理员可搜索、筛选、分页浏览全部文档；普通用户只能搜索、筛选、分页浏览自己的文档；首页统计也遵守同样的数据范围。

### 本阶段验证

## 阶段 8A：界面汉化与用户模块完善

阶段 8A 的目标是让系统更适合课程答辩和中文用户演示，同时补齐用户账号的基础生命周期能力。

### 技术实现

- `sys_user` 表扩展 `avatar_url`、`email`、`phone`、`bio` 字段。
- `User` 实体同步新增头像、邮箱、手机号和简介属性。
- `LoginUser` 增加用户 ID 和头像地址，便于页面展示和权限相关操作定位当前用户。
- `AuthService` 从内存账号改为查询数据库用户，新增密码哈希和密码校验方法。
- 新密码使用 Hutool `DigestUtil.sha256Hex(...)` 保存。
- 为旧演示数据保留 `TEMP_HASH_admin123_replace_later` 和 `TEMP_HASH_user123_replace_later` 兼容逻辑。
- 新增 `UserController`，统一承载注册、个人中心和管理员用户管理。
- `/register` 不经过登录拦截，注册用户默认角色为 `USER`。
- `/profile` 和 `/profile/password` 仅允许登录用户维护自己的资料和密码。
- `/users` 仍由拦截器限制为管理员访问，管理员只管理普通用户。
- 删除普通用户前检查该用户是否已有文档，避免破坏文档表外键和上传人归属。

### 页面实现

- `login.html` 同时承载登录和注册两个模式。
- `app.html` 主导航、工作台、文档管理、上传、详情、分类、标签、用户管理等区域改为中文。
- 顶栏显示用户头像、昵称、角色和个人中心入口。
- 新增个人中心页面区域，支持资料修改和密码修改。
- 用户管理页面从静态占位改为真实普通用户列表。

### 数据库升级

新建数据库直接执行：

```sql
source docs/database/schema.sql;
source docs/database/demo-data.sql;
```

已有旧库先执行：

```sql
source docs/database/upgrade-stage8-users.sql;
source docs/database/demo-data.sql;
```

### 本阶段验证

- `mvn package` 构建成功。
- 后续本地浏览器测试重点包括注册、登录、个人资料修改、密码修改、管理员创建用户、禁用用户、重置密码和删除无文档用户。

已执行 `mvn package`，构建成功。

## 阶段 8B：用户体验与文档展示优化

阶段 8B 的目标是在已完成用户模块基础上，补齐忘记密码、头像本地上传和个人中心布局优化，同时修正文档列表上传人和所有者显示逻辑。

### 忘记密码

新增路由：

- `GET /forgot-password`：展示忘记密码表单。
- `POST /forgot-password`：根据用户名和邮箱校验用户身份，校验通过后重置密码。

当前实现适合本地课程演示：用户输入账号、注册邮箱、新密码和确认密码。系统校验账号与邮箱匹配且账号启用后，将新密码以 SHA-256 哈希写回 `sys_user.password_hash`。

### 头像本地上传

个人中心资料表单改为 `multipart/form-data`，新增 `avatarFile` 文件字段。用户可以继续填写头像地址，也可以选择本地图片文件；如果上传了本地文件，系统优先使用本地上传头像。

头像存储规则：

- 存储目录：`uploads/avatars/user_{userId}/`。
- 访问地址：`/avatars/user_{userId}/{filename}`。
- 支持类型：PNG、JPG、JPEG、GIF、WEBP、SVG。
- 大小限制：2MB。

头像读取通过 `UserController.avatar(...)` 返回本地文件资源，避免把运行期上传文件写入 `src/main/resources/static`。

### 个人中心布局

个人中心页面拆分为：

- 资料卡：展示头像、昵称、账号，并提供资料编辑表单。
- 密码卡：单独处理原密码、新密码、确认新密码。

CSS 新增 `profile-card`、`password-card`、`profile-form`、`password-form`、`compact-submit` 等样式，解决保存资料和更新密码按钮被网格撑得过大的问题。

### 文档列表昵称展示

`HomeController.ownerName(...)` 不再使用固定的 `User 3` 或硬编码管理员名称，而是根据 `document.uploadUserId` 查询 `sys_user`，优先展示 `nickname`。因此文档列表中的“上传人”和“所有者”会显示用户昵称。

### 本阶段验证

- `mvn package` 构建成功。
- 浏览器中应重点验证：忘记密码、个人中心头像上传、个人中心按钮布局、文档列表昵称显示。

## 阶段 8C：快捷视图与上传人维护

阶段 8C 的目标是完善侧边栏中尚未落地的资料维护入口，让“快捷视图”“上传人”具备真实业务含义。原计划中的“存储路径”维护因浏览器下载目录并非后端系统可控，容易造成误解，已删除该入口。

### 快捷视图

文档列表新增 `view` 参数：

- `view=all`：全部可查看文档。
- `view=recent`：最近 7 天上传的可查看文档。

`DocumentController.documents(...)` 接收 `view` 参数后，传入 `filterDocuments(...)`。当 `view=recent` 时，会使用 `LocalDateTime.now().minusDays(7)` 作为最近上传时间边界。页面筛选表单和分页链接都会保留 `view`，避免翻页或搜索后丢失当前快捷视图。

### 上传人维护

新增 `MaintenanceController`：

- `GET /uploaders`：展示当前用户可查看范围内已有上传记录的用户。
- `GET /uploaders/{userId}/documents`：展示某个上传人的可查看文档。

上传人统计包括：

- 上传人昵称和账号。
- 头像。
- 文档数量。
- 占用空间。
- 最近上传时间。

权限规则：

- 管理员可看到所有上传人。
- 普通用户只能看到自己权限范围内的上传人和文档。

上传人详情页提供文档详情、编辑、删除入口，复用现有文档权限校验。

### 存储路径入口删除说明

服务端系统可以决定上传文件保存到服务器本地的 `uploads` 目录，但用户下载文件后保存到电脑哪个目录由浏览器控制，后端无法强制指定。因此“默认下载路径提示”实际业务价值有限，且容易让用户误以为系统能控制浏览器下载目录。

本阶段已删除侧边栏“存储路径”入口、`/storage-paths` 页面和相关设置逻辑。文件真实上传、存储、下载能力仍由 `FileStorageService` 和 `DocumentController` 保持不变。

### 本阶段验证

- `mvn package` 构建成功。
- 浏览器中应重点验证：快捷视图筛选、上传人列表、上传人文档详情。

## 阶段 9：文件任务与真实操作日志

阶段 9 的目标是补齐系统管理中的“文件任务”和“操作日志”两个核心演示模块。

### 文件任务

新增数据表：`document_task`。

核心字段：

- `document_id`：关联文档。
- `user_id`：任务所属用户。
- `status`：任务状态，`PENDING` 或 `DONE`。
- `priority`：优先级，`HIGH`、`MEDIUM`、`LOW`。
- `due_at`：最晚处理时间。
- `handled_at`：实际处理完成时间。
- `note`：任务说明。

新增路由：

- `GET /file-tasks`：展示当前登录用户的文件任务。
- `POST /file-tasks`：创建文件任务。
- `POST /file-tasks/{id}/update`：更新任务截止时间、优先级和说明。
- `POST /file-tasks/{id}/complete`：标记任务为已处理。

任务排序规则：

1. 待处理任务优先。
2. 已逾期任务优先。
3. 高优先级任务优先。
4. 最晚处理时间更早的任务优先。

提醒策略：

- 每次页面渲染时，`HomeController.prepareApp(...)` 会统计当前登录用户的待处理任务。
- 如果存在逾期任务，页面顶部显示红色提示。
- 如果存在 24 小时内到期任务，页面顶部显示黄色提示。
- 当前项目未引入 WebSocket，因此“在线提醒”通过登录、刷新和页面跳转时提示实现。

### 操作日志

`OperationLogService` 新增 `record(...)` 方法，用于统一写入 `operation_log`。

当前已记录的操作：

- 登录：`LOGIN`
- 查看文档列表：`LIST_DOCUMENTS`
- 查看文档详情：`VIEW_DOCUMENT`
- 上传文档：`UPLOAD_DOCUMENT`
- 编辑文档：`UPDATE_DOCUMENT`
- 删除文档：`DELETE_DOCUMENT`
- 下载文档：`DOWNLOAD_DOCUMENT`
- 创建文件任务：`CREATE_FILE_TASK`
- 更新文件任务：`UPDATE_FILE_TASK`
- 完成文件任务：`COMPLETE_FILE_TASK`

`/logs` 页面仅管理员可访问，展示最近 100 条日志，包括时间、用户、操作、对象、IP 和结果。

### 数据库升级

已有旧数据库执行：

```sql
source docs/database/upgrade-stage9-tasks-logs.sql;
```

新建数据库仍按顺序执行：

```sql
source docs/database/schema.sql;
source docs/database/demo-data.sql;
```

### 本阶段验证

- `mvn package` 构建成功。
- 浏览器中应重点验证：创建文件任务、更新任务、标记已处理、逾期提示、文档操作写入日志、管理员查看日志。

## 阶段 9B：文件任务与待处理标签联动

阶段 9B 的目标是优化文件任务模块，让它自动吸收带有“待处理”标签的文档，并支持在任务中直接覆盖更新文件。

### 待处理标签生成任务

联动规则：

- 用户上传文档时，如果选择了名为“待处理”的标签，系统自动创建一条 `PENDING` 文件任务。
- 用户进入 `/file-tasks` 时，系统会扫描当前用户可访问且带有“待处理”标签的文档。
- 如果某个待处理标签文档尚未有任务记录，系统会自动补齐一条默认任务。
- 默认任务优先级为 `MEDIUM`，最晚处理时间为当前时间加 1 天。

这样历史文档和新上传文档都能进入任务列表，侧边栏待处理任务数也会同步包含这些文档。

### 覆盖更新文件

文件任务列表中的“更新文件”改为本地文件上传表单：

- 用户选择本地新文件。
- 提交到 `POST /file-tasks/{id}/replace-file`。
- 后端使用 `FileStorageService.store(...)` 保存新文件。
- 更新 `document` 表中的原始文件名、存储文件名、路径、文件大小、文件类型和更新时间。
- 删除旧的本地文件。
- 将任务状态改为 `DONE` 并记录处理时间。

该设计适合“补充最新版合同、替换新版报告、更新发票扫描件”等课程演示场景。

### 日志记录

覆盖文件成功后会记录：

- `REPLACE_TASK_DOCUMENT`
- `COMPLETE_FILE_TASK`

### 本阶段验证

- `mvn package` 构建成功。
- 浏览器中应重点验证：上传带“待处理”标签文档后自动出现任务、历史待处理标签文档自动补齐任务、任务中上传本地文件覆盖原文档、覆盖后任务转为已处理。

## 阶段 9C：文件任务界面优化

阶段 9C 的目标是优化文件任务页在任务较多时的浏览体验，并让任务紧急程度在视觉上更容易区分。

### 左侧导航固定

后台主布局中的左侧导航栏改为 `position: sticky`，固定在顶部导航下方：

```css
.side-nav {
    position: sticky;
    top: 64px;
    height: calc(100vh - 64px);
    overflow-y: auto;
}
```

这样页面内容纵向滚动时，左侧菜单仍保持可见，方便用户在文档管理、文件任务、操作日志等模块之间切换。移动端窄屏下会恢复为普通流式布局，避免固定侧栏影响可用空间。

### 文件任务分页

`GET /file-tasks` 新增分页参数：

- `page`：当前页，默认 1。
- `size`：每页条数，支持 5、10、20，默认 5。

后端仍然先按照“待处理优先、逾期优先、高优先级优先、最晚处理时间更早优先”的规则排序，再使用 `subList(...)` 截取当前页任务。页面新增任务总数、当前页、每页条数和上一页/下一页控件。

待处理任务数和逾期任务数仍按当前用户全部任务统计，不受当前页影响。

### 紧急程度背景色

任务列表行根据任务状态和紧急程度增加不同背景色：

- 已逾期：红色系背景。
- 24 小时内到期：黄色系背景。
- 高优先级：橙色系背景。
- 中优先级：蓝色系背景。
- 低优先级：绿色系背景。
- 已处理：浅灰背景。

优先级颜色只影响未逾期、非临期的待处理任务；逾期和临期状态优先级更高，方便用户先处理风险最高的文档。

### 本阶段验证

- `mvn package` 构建成功。
- 浏览器中应重点验证：页面滚动时左侧导航保持可见、文件任务分页按钮和每页条数生效、不同紧急程度任务显示不同背景色。
