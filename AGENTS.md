# Repository Guidelines

## 适用范围与执行优先级

本文件适用于仓库根目录及全部子目录。执行任务时依次遵循：用户当前指令、本文件、项目现有实现与惯例。需求不明确但可安全推断时，先检查相关代码再做最小假设；涉及数据丢失、Git 历史、远端状态、发布渠道或凭据时必须先确认。

开始修改前先运行 `git status --short`。工作区可能包含用户尚未提交的改动：不得覆盖、删除、回退、暂存或提交与当前任务无关的文件；若目标文件已有改动，应先阅读 diff 并在其基础上修改。

## 项目定位与技术栈

本仓库是 Project Manager IntelliJ IDEA 插件，用于保存、分类、搜索和快速打开本地项目。项目使用 Kotlin 1.9.22、Java 17、Gradle Kotlin DSL、IntelliJ Platform Gradle Plugin 1.17.2；开发基线为 IntelliJ IDEA Community 2023.2（build 232），当前声明兼容至 `300.*`。应用数据通过 IntelliJ Persistent State API 与 JSON 存储持久化。

## 目录与架构

- `src/main/kotlin/com/github/hyxf/projectmanager/feature/`：业务能力。
  - `project/`：项目模型、仓储接口与核心服务。
  - `tag/`：标签仓储接口。
  - `action/`：Tools 菜单及快捷操作。
  - `ui/`：Tool Window、列表渲染、搜索与编辑对话框。
  - `recent/`：项目打开事件与最近访问时间维护。
- `src/main/kotlin/com/github/hyxf/projectmanager/infrastructure/`：JSON 存储、持久化仓储和文件系统路径处理。
- `src/main/kotlin/com/github/hyxf/projectmanager/settings/`：应用级设置及 Settings UI。
- `src/main/resources/META-INF/plugin.xml`：服务、Tool Window、设置页、监听器、通知组和 Action 注册。
- `src/test/kotlin/com/github/hyxf/projectmanager/`：业务服务与 JSON 存储测试。
- `.github/workflows/release.yml`：标签触发的插件构建、GitHub Release 和 GitHub Pages 更新源发布。

典型数据流为：Action / Tool Window → `ProjectManagerService` → `ProjectRepository` / `TagRepository` → 持久化实现 → `ProjectJsonStore`。UI 只负责交互与展示，路径规范化、去重、搜索、排序和状态变更应优先放在服务或基础设施层。

## 变更原则

- Swing 组件只能在 EDT 创建或更新；文件 I/O、JSON 读写、目录扫描等耗时操作不得阻塞 EDT。
- 使用 IntelliJ Platform 公共 API；避免依赖 SDK 内部实现或手工模拟其生命周期。
- “从列表移除项目”不得删除磁盘目录。任何可能影响用户文件的能力都必须明确提示并单独确认。
- 项目路径比较前保持统一的绝对路径规范化规则；新增、迁移和重定位均须防止重复路径。
- 持久化结构变化时保留 `schemaVersion` 迁移入口，并兼容既有用户数据；不要无提示丢弃未知或损坏数据。
- 新增或移动 Service、Action、Tool Window、Configurable、Listener、通知组或其他扩展点时，同步核对 `plugin.xml`。
- 错误应向用户提供可操作信息，并在适当位置记录诊断上下文；不得记录项目文件正文、密钥、令牌或剪贴板内容。
- 保持改动聚焦，不夹带无关重构、格式化、依赖升级或生成文件。

## 构建与本地开发

统一使用仓库内 Gradle Wrapper 和 JDK 17。本机已有缓存时优先离线运行：

```bash
GRADLE_USER_HOME=/Users/seven/.gradle ./gradlew --offline test
GRADLE_USER_HOME=/Users/seven/.gradle ./gradlew --offline clean build
GRADLE_USER_HOME=/Users/seven/.gradle ./gradlew --offline clean build verifyPlugin
```

其他常用命令：

```bash
./gradlew runIde
./gradlew buildPlugin
./gradlew verifyPlugin
```

`runIde` 用于交互式验证；`buildPlugin` 的产物位于 `build/distributions/`。离线构建因缓存缺失失败时，先说明缺失依赖，再决定是否联网；不要提交本机 JDK、代理、缓存或 IDE 配置。

## 编码规范

- Kotlin/Java 均使用 4 空格缩进；类型用 PascalCase，函数与变量用 camelCase，常量用 UPPER_SNAKE_CASE。
- 包名保持在 `com.github.hyxf.projectmanager` 下，并按 feature / infrastructure / settings 职责归档。
- 优先使用 Kotlin 空安全；Java 公共边界遵循 IntelliJ SDK 的 `@NotNull` / `@Nullable` 约定。
- Action 在确实可于索引期间运行时实现 `DumbAware`，并声明合适的 `ActionUpdateThread`。
- import 保持显式，方法保持单一职责；注释解释设计原因、线程约束或兼容性背景，不复述代码。
- 仓库未配置自动格式化器。只格式化本次触及的代码，避免产生整文件无关 diff。

## 测试与验证

测试包结构与生产代码一致，测试类命名为 `*Test`，测试名称描述行为与预期。纯业务逻辑优先使用 Kotlin Test；涉及 Project、Action、Tool Window 或 IntelliJ 生命周期时使用 IntelliJ Platform test fixture，不要模拟 SDK 内部实现。

验证应与风险匹配：

- 服务、搜索、排序或路径逻辑：覆盖正常行为、大小写/空输入、路径规范化、重复路径和缺失项目。
- 持久化或 schema 变化：覆盖读写往返、旧版本迁移、损坏/缺失数据和身份稳定性。
- UI 或 Action：至少通过 `runIde` 冒烟验证新增、编辑、删除列表项、收藏、标签、搜索，以及当前/新窗口打开行为。
- `plugin.xml`、依赖或兼容范围变化：执行完整构建和 `verifyPlugin`。

功能修改至少运行相关测试；提交或发版前运行完整构建。无法执行某项验证时，在交付说明中明确原因和剩余风险。

## 提交与 Pull Request

提交信息使用 `<type>: <动词开头的说明>`，常用类型为 `feat`、`fix`、`refactor`、`test`、`docs`、`build`，例如 `fix: 避免重复保存规范化路径`。一个提交只处理一个逻辑变更。

PR 应说明变更目的、关键实现、影响范围和实际执行的验证命令，并关联 Issue（如有）。涉及菜单、对话框、Tool Window、图标或通知时附截图或录屏；涉及兼容版本、依赖、持久化结构或扩展点时显式标注。不得提交 `.idea/`、`build/`、沙箱数据、`.DS_Store`、证书或发布令牌。

## 发版规范

用户提出“发版”时，默认仅包含：检查并提交本次已确认的工作区改动，将提交推送到远端当前分支，再在该提交上创建并推送新的附注 Git 标签。除非用户明确要求，不上传 JetBrains Marketplace，也不手动创建 GitHub Release 或修改其他发布渠道；推送 `v*` 标签后现有 CI 会执行 GitHub Release 与 Pages 更新源流程。

版本遵循语义化版本，标签格式为 `vX.Y.Z`：

- `PATCH`：向后兼容的问题修复。
- `MINOR`：向后兼容的新功能。
- `MAJOR`：配置、持久化结构、交互或 API 存在不兼容变化。

用户给出完整版本号时仍须检查标签是否存在。没有历史标签时必须确认初始版本；改动性质与所选级别明显不符时说明原因并再次确认，不得自行改级别。

### 发版流程

1. 只读检查：`git status --short`、`git diff --stat`、`git diff`、`git branch --show-current`、`git remote -v`、`git status --branch --short`、`git tag --sort=-version:refname`。必要时先征得联网操作许可并运行 `git fetch origin --prune --tags`。
2. 识别本次应提交的文件。发现来源不明、无关、敏感或生成文件时暂停，请用户决定；不得擅自处理。
3. 向用户展示文件与变更摘要、完整提交信息、当前最新标签，以及 `PATCH` / `MINOR` / `MAJOR` 候选标签。给出有依据的推荐，但必须等待用户确认提交范围、提交信息和版本。
4. 确认后运行与风险匹配的测试；正式发版至少执行 `GRADLE_USER_HOME=/Users/seven/.gradle ./gradlew --offline clean build`。修改 `sinceBuild` / `untilBuild`、依赖或扩展点时额外执行 `verifyPlugin`。失败即停止，不提交、不推送、不打标签。
5. 仅暂存已确认文件并提交，然后用 `git diff --cached --stat`（提交前）和 `git show --stat --oneline HEAD`（提交后）核对范围。不得绕过 Git hooks，除非用户明确授权。
6. 再次确认当前分支及其远端关系。只执行 `git push origin <current-branch>`；若无 upstream、远端领先、推送被拒绝或目标不明确，停止并说明，不得强推，也不得擅自 rebase、merge 或改推其他分支。
7. 确认远端分支已包含该提交后，在同一提交创建 `git tag -a vX.Y.Z -m "vX.Y.Z"`，再执行 `git push origin vX.Y.Z`。不得移动、覆盖、复用或删除既有标签。
8. 核验远端分支与标签均指向本次提交，并报告提交哈希、提交信息、分支、标签、测试命令与结果、推送结果。

若分支推送失败，不得创建标签。若分支已推送但标签创建或推送失败，明确报告“代码已推送、标签未发布”的中间状态，问题解决后只补做标签步骤。

## 凭据与发布渠道

插件签名与 Marketplace 发布使用 `CERTIFICATE_CHAIN`、`PRIVATE_KEY`、`PRIVATE_KEY_PASSWORD`、`PUBLISH_TOKEN`。只有用户明确要求上传 Marketplace 时才执行，并另行确认版本与 channel。凭据只能通过本地环境或 CI Secret 注入，禁止写入代码、`gradle.properties`、命令行参数、日志、提交或 PR。
