# ANI-RSS-Yuri 开发指南

## 技术栈

- Java 25、Spring Boot 与 Maven：后端服务、定时 RSS 任务、qBittorrent 接入和本地配置。
- Vue 3、Vite、Element Plus 与 pnpm：WebUI。
- 配置以 JSON 文件保存，并通过实体对象向前后端传递。增加配置字段时同时考虑缺省值、已有配置升级和 UI 默认模型。

## 目录

- `ani-rss-application/`：Java 应用、服务、任务、接口和打包配置。
- `ani-rss-ui/`：Vue 页面和前端资源。
- `linux/`：Linux JAR 的 systemd 安装、控制与卸载脚本。
- `.github/workflows/`：Windows / Linux 发布构建与构建检查。
- `docs/`：本项目用户指南和开发指南。

本项目只支持 Linux 与 Windows，不维护 Docker 或 macOS 构建。

协作与安全要求见 [贡献指南](CONTRIBUTING.md) 与 [安全说明](SECURITY.md)；面向 AI 编码代理的完整仓库约定见根目录 `AGENTS.md`。

## 环境与构建

需要 Java 25。Maven 前端插件会把 Node.js 和 pnpm 下载到 `ani-rss-ui/node/`，pnpm 依赖缓存写入 `ani-rss-ui/.tools/pnpm-store/`；两处都由 `.gitignore` 排除，不需要安装到系统目录。前端依赖使用 `pnpm-lock.yaml` 锁定。

只构建 JAR：

```bash
mvn -B package -DskipTests --file pom.xml
```

构建发布用 JAR 和 Windows EXE：

```bash
bash ./package.sh
```

`package.sh` 使用 Maven `windows` profile 生成 EXE，并保留 JAR。CI 的 `build-test.yml` 执行相同打包脚本；`build.yml` 将 JAR 与 EXE 附加到 GitHub Release，不发布容器镜像。

Linux 安装脚本使用 Java 直接运行 JAR，并将服务配置目录指向安装目录。手动运行时可通过 `CONFIG=/path/to/config java -jar ani-rss.jar` 指定配置目录。

## 代码约定

- Java 使用现有包结构和 Lombok 实体风格；前端使用 Vue 3 `<script setup>` 与现有组件样式。
- 配置字段需覆盖四处：实体类、后端默认值和旧配置补齐、前端默认对象、对应设置界面。
- 复用通知模板使用 `id / name / type / content` 字段；`type` 为 `TEXT` 或 `JSON`。更改持久化格式时，保留旧字段读取能力并在 `ConfigUtil.format` 中迁移已有配置。
- 旧版 `Config.notificationTemplate` 是完整消息正文，迁移时应并入默认文本通知模板；模板应直接渲染 `${title}`、`${subgroup}`、`${text}` 等字段，不再嵌套 `${notification}`。
- 开发者设置由 `developerOptionsEnabled` 控制是否显示；通过关于页的凭据验证后，先确认并保存整份当前配置，再启用该标记并刷新页面。
- 过滤规则或业务条件有多个调用点时抽取共享逻辑。RSS 下载、RSS 预览和合集预览应保持一致。
- 修改配置语义时更新用户指南；修改发布流程时同步检查 workflow、安装脚本、README 和更新说明。
- 保持上游配置字段兼容。已有 JSON 中缺少新字段时应采用安全默认值，不要求用户手工重建配置。
- 提交遵循 Conventional Commits，例如 `feat(match): add global reverse rules`、`docs: document fork-specific behavior`、`build: remove container publishing`。

## 匹配规则维护

当前规则入口由 `MatchRuleUtil` 统一处理，RSS 下载与合集预览调用同一方法。反匹配规则由标题正则和目标字幕组组成；规则命中时使用目标字幕组。规则优先级和反匹配未命中行为定义见[用户指南](USER_GUIDE.md#标题匹配规则)。若改变判定顺序、配置迁移方式或规则格式，请同时更新该文档和两个界面的标题测试行为。

## CI 与发布

- `build-test.yml`：手动触发，运行 Java 25 Maven 打包并生成 Linux 可构建的 JAR 与 Windows EXE。
- `build.yml`：手动触发，构建后创建 GitHub Release，发布 `ani-rss.jar` 和 `ani-rss.exe`。
- 变更 Maven profile 或构建工具版本时，确认 JAR 与 EXE 都仍包含前端资源；不要恢复 Docker 镜像或 macOS 打包步骤。
- 工作流权限按实际操作设置最小范围：构建检查只需读取仓库，发布流程需要 `contents: write`。
