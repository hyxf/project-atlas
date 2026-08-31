# Project Atlas

Project Atlas 是一个原生 IntelliJ IDEA 插件，用于保存、分类、搜索并快速切换大量本地项目。

## MVP 功能

- 保存当前项目，或通过原生目录选择器添加已有项目
- 编辑名称、标签和收藏状态；从列表移除时不会删除磁盘目录
- Project Atlas Tool Window：All/Recent/Favorites 列表导航与 Tags 树导航
- 原生图标工具栏：保存当前项目、编辑用户配置、搜索和刷新
- 搜索对话框与 Quick Open Popup，可搜索名称、路径和标签
- 使用 IntelliJ Project API 在当前窗口或新窗口打开项目
- 当前项目标识、缺失目录提示、复制路径和在系统文件管理器中显示
- 应用级持久化以及 `schemaVersion` 数据迁移入口
- Settings → Tools → Project Atlas 中配置默认打开模式和排序方式

## 构建

需要 JDK 17。项目统一使用 Gradle Wrapper：

```bash
GRADLE_USER_HOME=/Users/seven/.gradle ./gradlew --offline clean build
```

插件包生成于 `build/distributions/project-atlas-*.zip`。

## 使用方式

### 在 IntelliJ IDEA 中安装插件仓库

1. 打开 IntelliJ IDEA。
2. 进入 **Settings**（macOS 上为 **Preferences**）。
3. 选择 **Plugins**。
4. 点击右上角的 **⚙️**（齿轮图标）。
5. 选择 **Manage Plugin Repositories**。
6. 点击 **+**。
7. 填入插件仓库地址：

   ```text
   https://hyxf.github.io/project-atlas/updatePlugins.xml
   ```

8. 确认并保存。
9. 搜索插件名称，或在 **Updates** 中检查更新。
