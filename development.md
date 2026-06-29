# Development Technical Notes

> 本文件用于记录开发中的技术方案、实现细节和阶段性决策，重点服务于后期答辩和代码讲解。`progress.md` 记录进度，`development-log.md` 记录过程，本文件更偏技术实现说明。

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
