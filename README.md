# Project Atlas

Project Atlas 是一个原生 IntelliJ IDEA 插件，用来集中保存、分类、搜索和快速打开本地项目。项目记录在用户级配置中，因此可以跨 IDE 项目使用；插件不会要求把配置写进各个项目目录。

## 主要功能

- 保存当前项目，或选择任意本地目录添加项目。
- 扫描一个或多个目录，识别 IntelliJ、Git、Gradle、Maven、Node.js、Rust 和 Go 项目，预览后批量导入。
- 在 Tool Window 中使用 **All / Recent / Favorites** 列表视图或 **Tags** 分组视图，并按名称、路径、最近打开或最近保存排序。
- 按名称、绝对路径和标签搜索；搜索结果会优先展示名称匹配项。
- 在当前窗口或新窗口打开项目，并在 IDE 欢迎页浏览和打开已保存项目。
- 编辑名称、路径、标签和收藏状态；创建、重命名和删除标签。
- 复制项目目录、复制路径、在 Finder / Explorer 中显示，以及通过 Terminal 插件打开目录。
- 在项目目录移动后重新定位记录，并明确标记缺失目录。
- 将记录从 Project Atlas 中移除而不影响磁盘文件；也可经确认后将整个项目目录移到系统废纸篓或永久删除。

## 安装

### 从插件仓库安装

1. 打开 **Settings / Preferences → Plugins**。
2. 点击齿轮图标，选择 **Manage Plugin Repositories**。
3. 添加以下仓库地址：

   ```text
   https://hyxf.github.io/project-atlas/updatePlugins.xml
   ```

4. 回到 Plugins 页面搜索 **Project Atlas** 并安装；已有版本可在 **Updates** 中升级。

### 从本地 ZIP 安装

在 Plugins 页面点击齿轮图标，选择 **Install Plugin from Disk...**，然后选择 `build/distributions/` 中生成的插件 ZIP。不要解压 ZIP。

插件面向 IntelliJ Platform build 232 及以上版本（开发基线为 IntelliJ IDEA Community 2023.2）。

## 使用

安装并重启 IDE 后，可从左侧 **Project Atlas** Tool Window 或 **Tools → Project Atlas** 进入：

- **Save Current Project...**：保存当前项目；重复保存同一路径会更新已有记录。
- **Add Project...**：选择一个目录并设置名称、标签和收藏状态。
- **Import Local Projects...**：选择目录、设置扫描深度、检查识别结果并批量导入；可选择是否更新已有路径的记录。
- **Open Project... / Open Project in New Window...**：从轻量 Quick Open Popup 打开项目。
- **Search Projects**：使用完整搜索面板查找并按默认打开方式打开项目。
- **Switch Project View**：在列表与标签视图之间切换。

在 Tool Window 中双击项目即可按默认方式打开。右键菜单还提供编辑、复制项目、编辑标签、收藏、复制路径、显示目录、在终端打开、定位缺失项目、删除项目目录和仅移除记录等操作。

> **数据安全：** **Remove from Project Atlas...** 只删除管理记录；**Delete Project...** 会删除磁盘上的整个项目目录。删除对话框默认选择不可恢复的直接删除，取消该选项则尝试移到系统废纸篓。当前正在打开的项目不能被删除。

## 快捷键

| 操作 | Windows / Linux | macOS |
| --- | --- | --- |
| 搜索项目 | `Ctrl+Shift+P` | `⌘⇧P` |
| 切换列表 / 标签视图 | `Ctrl+Shift+T` | `⌘⇧T` |
| 显示 / 隐藏 Tool Window | `Ctrl+Shift+,` | `⌘⇧,` |

若快捷键与现有 Keymap 冲突，可在 **Settings / Preferences → Keymap → Project Atlas** 中重新绑定。

## 设置与数据

在 **Settings / Preferences → Tools → Project Atlas** 中可以设置：

- 默认在当前窗口或新窗口打开项目；
- 列表视图与标签视图中的项目间距。

排序方式、当前视图和列表筛选也会随使用状态保存。所有项目、标签和设置存储在：

```text
~/.project-manager/project.json
```

Tool Window 工具栏可直接打开该文件。存储层采用原子替换写入、保留可识别范围外的 JSON 字段，并包含 `schemaVersion` 迁移入口。如果文件损坏，插件会继续使用最后一次有效数据并阻止覆盖写入，修复文件后执行刷新即可重新加载。

## 本地开发

需要 JDK 17，使用仓库内 Gradle Wrapper。项目基于 Kotlin 2.2.20、IntelliJ Platform Gradle Plugin 2.11.0 和 IntelliJ IDEA Community 2023.2 SDK。

```bash
# 运行测试
GRADLE_USER_HOME=/Users/seven/.gradle ./gradlew --offline test

# 完整构建
GRADLE_USER_HOME=/Users/seven/.gradle ./gradlew --offline clean build

# 启动沙箱 IDE 进行交互验证
./gradlew runIde

# 验证插件兼容性
./gradlew verifyPlugin
```

离线命令要求相关 IDE 和依赖已存在于本机 Gradle 缓存中。插件 ZIP 位于 `build/distributions/`；默认开发版本为 `1.0.0-SNAPSHOT`，可通过 `-PpluginVersion=<version>` 覆盖。

## 发布机制

推送 `vX.Y.Z` 标签会触发 GitHub Actions：构建插件、创建 GitHub Release，并将自定义插件仓库的 `updatePlugins.xml` 发布到 GitHub Pages。插件签名和 JetBrains Marketplace 发布仅在提供相应凭据并显式执行发布任务时发生。
