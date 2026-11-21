# 贡献指南

本文说明 MintSoda 的开发、评审与发布约定。功能说明见 [用户指南](USER_GUIDE.md)，目录与构建细节见 [开发指南](DEVELOPMENT.md)，接口约定见 [API 参考](API.md)，安全要求见 [安全说明](SECURITY.md)。全部文档见 [文档导航](README.md)。

## 本文目录

- [参与方式](#参与方式)
- [分支与提交](#分支与提交)
- [代码风格](#代码风格)
- [配置与兼容](#配置与兼容)
- [业务约束](#业务约束)
- [构建与验证](#构建与验证)
- [评审清单](#评审清单)
- [发布流程](#发布流程)
- [安全问题](#安全问题)

## 参与方式

- 提交前先确认问题能在最新发布版本复现，并使用 Issue 模板说明运行平台（Linux / Windows）、程序版本和日志。
- 功能改动先描述使用场景与行为，再实现；涉及配置格式或匹配语义的改动需要在说明中写清兼容策略。
- 订阅与下载行为以本仓库现有实现为准，调整前先确认 `MatchRuleUtil` 与下载链路的既有约定。

## 分支与提交

- 从 `main` 派生功能分支，命名体现范围，例如 `feat/reverse-match`、`fix/config-migration`、`docs/security`。
- 使用 Conventional Commits：`<type>(<scope>): <中文摘要>`。
  - `type` 与 `scope` 保持英文小写，**摘要必须使用中文**，不要用英文动词开头。
  - 常用 type：`feat`、`fix`、`refactor`、`perf`、`build`、`ci`、`docs`、`test`、`chore`。
  - 常用 scope：`match`、`notification`、`config`、`security`、`ui`、`download`、`plugin`、`build`、`release`、`docs`。
  - 示例：`feat(match): 支持全局反匹配规则`、`fix(config): 保留旧的分钟字段`、`docs(plugin): 补充 Lua 与权限说明`。
- 摘要不超过 80 个字符，句末不加句号；正文同样使用中文，说明动机、兼容影响与验证方式。
- 提交信息可用 `bash .github/scripts/check-commit-messages.sh` 自查；启用仓库自带钩子后，每次提交都会自动校验：

  ```bash
  git config core.hooksPath .githooks
  ```

- PR 标题按同样格式校验，CI 会检查提交信息、PR 标题与工作流语法。
- 一次提交只解决一件事；功能改动与配置迁移、格式化、依赖升级分开提交。
- 不要提交 `.tools/`、`mint-soda-ui/node/`、`mint-soda-ui/.tools/`、`target/`、`dist/`、`node_modules/`、配置目录、日志、备份或本机交接文件。

## 代码风格

- 编码统一 UTF-8。仓库内文本以 LF 存储（本地 `core.autocrlf=true` 的检出会是 CRLF），不要引入混合行尾；新增 shell 脚本保持 LF 与可执行位。
- 后端：Java 25、Spring Boot、Lombok；实体使用 `@Data` + `@Accessors(chain = true)`，日志使用 `@Slf4j`，依赖注入沿用 `@Resource` 或构造注入。业务异常交给 `CustomExceptionHandler` 统一转换为 `Result`。
- 前端：Vue 3 `<script setup>` + Element Plus。`.vue` 模板与脚本 2 空格缩进，`src/js/config.js` 等配置对象 4 空格缩进。请求统一走 `src/js/api.js` / `src/js/http.js`。
- 界面文案统一走 `vue-i18n`，不要在模板或脚本中硬编码中文；新增或修改文案的流程见[多语言与翻译贡献](I18N.md)。
- 命名沿用现有风格：Java 类 `PascalCase`、方法与变量 `camelCase`；前端组件与视图用 `PascalCase.vue`，工具模块用小写文件名。
- 注释只写“为什么”，不写“做了什么”，语言与所在文件一致；配置迁移、匹配优先级等易错逻辑应保留简短说明。

## 配置与兼容

配置以 JSON 持久化（默认 `config.v2.json`），升级用户可能保留任意旧版本文件。新增或修改配置字段时必须同时处理：

1. 实体：`mint-soda-application/.../entity/Config.java`（或对应实体）。
2. 默认值：`config/DefaultConfigFactory.java`。
3. 旧配置补齐与迁移：`util/other/ConfigUtil.java` 的 `format` 及相关方法。
4. 前端默认对象：`mint-soda-ui/src/js/config.js`。
5. 设置界面：`mint-soda-ui/src/view/config/` 下对应视图。
6. 文档：`docs/USER_GUIDE.md`，必要时同步 `docs/DEVELOPMENT.md`。

约束：

- 旧 JSON 缺少新字段时使用安全默认值，不要求用户重建配置。
- 迁移只做显式转换，不静默丢弃数据；无法无损迁移时保留旧字段并写迁移逻辑。
- `ConfigService#setConfig` 中“空值表示不修改”的字段（如密码类）在改动时要保持该语义。
- 兼容字段（`rssSleepMinutes`、`notificationTemplate`、`telegramTemplate`、`customRequestBody` 等）继续保留写入，不要删除读取路径。

## 业务约束

- 匹配规则唯一入口是 `util/other/MatchRuleUtil`；RSS 下载、RSS 预览与合集预览必须共用同一判定。优先级为反匹配 > 全局匹配 > 排除。
- 规则语法与未命中行为改动时，同步更新用户指南和两个前端测试区。
- 通知仅保留 Telegram、自定义 HTTP 请求、媒体库刷新；模板字段为 `id / name / type / content`，`type` 为 `TEXT` 或 `JSON`，直接使用 `${title}` 等字段变量，不再嵌套 `${notification}`。
- 下载器只有 qBittorrent，且 `ConfigUtil.format` 会归一化下载器类型；不要新增其他下载器或恢复已移除功能。
- 平台只支持 Linux 与 Windows；不要恢复 Docker 镜像、容器发布或 macOS 打包。

## 构建与验证

```bash
mvn -B package -DskipTests --file pom.xml   # JAR（含前端资源）
bash ./package.sh                            # JAR + Windows EXE
```

- 环境要求 Java 25；前端工具链由 Maven 插件下载到忽略目录，无需系统安装 Node。
- Windows profile 的 `generate-resources` 阶段需要 `bash` 与 `curl`。
- 现有自动化测试仅 `MintSodaApplicationTests`，且会访问外部接口；除非任务要求，不要为小改动新增测试。
- 改动工作流后，用 YAML 校验工具检查 `.github/workflows/*.yml`。
- 交付前至少确认：项目可打包、旧配置可加载、下载与预览判定一致、日志无敏感信息、文档已同步。

## 评审清单

- 行为是否符合 Issue 描述，边界情况（无规则、空规则、无效正则、旧配置）是否有明确结果。
- 是否新增了未鉴权接口；新增公开接口必须有“不含敏感信息”的理由。
- 是否新增外部请求；URL 是否经过 SSRF 校验、下载是否校验大小与哈希。
- 是否读写配置文件之外的用户路径；路径是否做了规范化与根目录约束。
- 是否泄露凭据、令牌、真实配置或日志内容到代码、注释、测试数据与文档。
- 前端是否引入新的 `v-html`、内联脚本或不可信外部资源。
- 配置六处（实体、默认值、迁移、前端默认对象、设置界面、文档）是否同步更新，字段表见 [配置参考](CONFIG.md)。
- 新增或删除接口时，是否同步更新 `docs/API.md` 与公开接口说明。

## 发布流程

1. 更新版本号：`./new_version.sh <x.y.z>`（同步 reactor 与子模块版本）。
2. 确认 `README.md`、`docs/USER_GUIDE.md`、`docs/API.md` 的行为描述与当前实现一致。
3. 推送后在 `ci.yml` 中确认提交信息、PR 标题与构建全部通过。
4. 手动触发 `release.yml`：标签默认取 `pom.xml` 的版本并加 `v` 前缀，标签已存在会失败，需要先提升版本号。
5. Release 附加 `MintSoda.jar` 与 `MintSoda.exe`，说明由 GitHub 按 `.github/release.yml` 的分类自动生成；仓库中不维护独立的更新说明文件。
6. 旧 Release 由 `delete-old-releases.yml` 手动清理，可先以试运行确认删除范围。

## 安全问题

不要通过公开 Issue 报告安全漏洞，按 [安全说明](SECURITY.md) 的渠道私下报告。
