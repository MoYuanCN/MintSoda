# 插件开发指南

本文说明如何为 ANI-RSS-Yuri 编写、打包、发布与更新插件。插件系统面向“扩展而不侵入”：第三方代码以独立 JAR 加载，通过稳定接口接入通知、订阅过滤、订阅事件、周期任务与设置界面。

> 安全边界见 [安全说明](SECURITY.md)。插件与服务同权限运行，**只安装你信任的插件**。

## 概念与架构

| 组件 | 说明 |
| --- | --- |
| `ani-rss-plugin-api` | 稳定的插件接口模块，插件以 `provided` 依赖编译。 |
| `PluginManager` | 负责加载、启用、停用、重载与卸载插件。 |
| `PluginContext` | 宿主传给插件的能力入口：注册扩展点、读写插件配置与日志。 |
| `PluginHook` | 宿主内部桥接，把匹配规则之后的条目交给插件过滤器。 |
| 插件市场 | 可配置的 JSON 索引，支持列出、安装与更新插件。 |

- 插件接口版本由 `PluginApi.VERSION` 表示，当前为 `1`。宿主只加载 `apiVersion` 不大于自身版本的插件，破坏性变更时提升该值。
- 插件包是**单个 JAR**，根目录包含 `plugin.json` 与编译后的类；不得打包接口模块本身。
- 每个插件独立类加载器，`ani.rss.plugin.api.*` 与 JDK 类始终由宿主提供，避免类型不一致。
- 插件加载、启用或运行失败只影响该插件，主程序继续运行。

## 目录布局

```text
<配置目录>/plugins/
├── installed.json          # 启用状态、来源、sha256 与插件配置
└── <plugin-id>/
    ├── plugin.jar          # 插件包
    ├── plugin.json         # 接口版本、入口类等
    └── data/               # 插件私有数据目录
```

所有插件路径都会被规范化并限制在 `plugins/` 之内；插件标识只允许 `A-Z a-z 0-9 . _ -`，长度 1-64。

## 最小插件

### 1. `plugin.json`

```json
{
  "id": "my-plugin",
  "name": "我的插件",
  "version": "1.0.0",
  "apiVersion": 1,
  "mainClass": "com.example.MyPlugin",
  "author": "you",
  "description": "一句话说明插件作用",
  "homepage": "https://example.com",
  "updateUrl": "",
  "permissions": ["notification"],
  "minAppVersion": ""
}
```

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `id` | 是 | 唯一标识，同时作为目录名；发布后不要修改 |
| `name` | 是 | 显示名称 |
| `version` | 是 | 插件版本，市场与更新比较时使用 |
| `apiVersion` | 是 | 编译时依赖的接口版本 |
| `mainClass` | 是 | `Plugin` 实现类全限定名 |
| `author` | 否 | 作者 |
| `description` | 否 | 简介 |
| `homepage` | 否 | 项目主页 |
| `updateUrl` | 否 | 插件自身的更新索引地址 |
| `permissions` | 否 | 声明使用的能力，仅用于展示与审计 |
| `minAppVersion` | 否 | 最低宿主版本 |

### 2. 入口类

```java
package com.example;

import ani.rss.plugin.api.Plugin;
import ani.rss.plugin.api.PluginContext;

public class MyPlugin implements Plugin {

    @Override
    public void onLoad(PluginContext context) {
        context.log("loaded");
    }

    @Override
    public void onEnable(PluginContext context) {
        // 在这里注册扩展点
    }

    @Override
    public void onDisable(PluginContext context) {
        // 释放线程、连接等资源
    }
}
```

生命周期：

1. `onLoad`：插件被识别时调用一次，此时可能尚未启用；不要在构造函数里访问宿主能力。
2. `onEnable`：启用时调用，用于注册扩展点；停用后注册项会被清理。
3. `onDisable`：停用或重载前调用。
4. `onUnload`：卸载前调用。

## 扩展点

### 通知渠道

```java
context.registerNotificationChannel(new NotificationChannel() {
    @Override
    public String id() {
        return "my-channel";
    }

    @Override
    public String name() {
        return "我的通知渠道";
    }

    @Override
    public List<ConfigField> fields() {
        return List.of(
                new ConfigField("endpoint", "接收地址", ConfigField.FieldType.TEXT,
                        "https://example.com/hook", true, List.of()),
                new ConfigField("token", "令牌", ConfigField.FieldType.PASSWORD)
        );
    }

    @Override
    public void send(NotificationPayload payload, Map<String, String> options) throws Exception {
        // 使用 options 中的字段投递 payload
    }
});
```

- `id` 全局唯一，建议加插件前缀，避免与其他插件冲突。
- 通知正文已由宿主按模板渲染，插件只需投递；同一渠道可复用 `请选择消息模板` 的文本模板。
- `test(payload, options)` 默认复用 `send`，需要区分时可覆盖。
- 字段类型支持 `TEXT`、`PASSWORD`、`NUMBER`、`SWITCH`、`SELECT`；值统一以字符串传递。

`NotificationPayload` 字段：`status`、`statusLabel`、`title`、`subgroup`、`text`、`episode`、`coverUrl`、`tags`。

### 订阅过滤器

```java
context.registerItemFilter(new ItemFilter() {
    @Override
    public String id() {
        return "my-filter";
    }

    @Override
    public int order() {
        return 100; // 数值越小越先执行
    }

    @Override
    public Decision decide(SubscriptionSnapshot ani, ItemSnapshot item, Map<String, Object> ctx) {
        return item.title().contains("1080p") ? Decision.REJECT : Decision.ABSTAIN;
    }
});
```

- 过滤发生在宿主匹配规则之后，顺序固定为：反匹配 > 全局匹配 > 排除 > 插件过滤。
- `REJECT` 会拒绝条目；`ABSTAIN` / `ACCEPT` 不改变宿主结论。
- RSS 下载、预览与合集预览共用同一插件过滤逻辑。
- 插件异常会被记录日志并放行，不会中断主流程。

### 订阅事件

```java
context.registerSubscriptionListener(new SubscriptionListener() {
    @Override
    public String id() {
        return "my-listener";
    }

    @Override
    public void onCreated(ItemFilter.SubscriptionSnapshot subscription) {
    }

    @Override
    public void onUpdated(ItemFilter.SubscriptionSnapshot subscription) {
    }

    @Override
    public void onDeleted(ItemFilter.SubscriptionSnapshot subscription) {
    }
});
```

### 周期任务

```java
context.registerScheduledTask(new ScheduledPluginTask() {
    @Override
    public String id() {
        return "my-task";
    }

    @Override
    public Duration interval() {
        return Duration.ofMinutes(30);
    }

    @Override
    public void run(PluginContext context) throws Exception {
        // 宿主使用独立守护线程调度，异常不会影响其他插件
    }
});
```

### 设置区块

```java
context.registerSettingsSection(new SettingsSection(
        "my-settings",
        "我的插件设置",
        "这些字段会以 JSON 形式保存。",
        List.of(
                new ConfigField("enabled", "启用", ConfigField.FieldType.SWITCH),
                new ConfigField("mode", "模式", ConfigField.FieldType.SELECT,
                        "", false, List.of("A", "B"))
        )
));
```

插件页按声明自动渲染表单，值通过 `PluginContext#getConfig` / `saveConfig` 读写。

## 编译与打包

插件需要以 `provided` 依赖接口模块：

```xml
<dependency>
    <groupId>ani.rss</groupId>
    <artifactId>ani-rss-plugin-api</artifactId>
    <version>3.3.0</version>
    <scope>provided</scope>
</dependency>
```

打包要点：

- `plugin.json` 必须位于 JAR 根目录。
- 不要把 `ani-rss-plugin-api` 打进插件包。
- 示例见 `examples/plugins/hello-plugin/`，构建脚本会把 `plugin.json` 加入 JAR：

```bash
cd examples/plugins/hello-plugin
./build.sh
# 产物：target/plugin.jar
```

## 安装与更新

安装方式：

1. **上传安装**：插件页“安装插件”，选择 `plugin.jar`。
2. **链接安装**：填写 JAR 地址，可同时提供 `sha256` 与字节数。
3. **市场安装**：填写市场索引地址后，从插件市场列表安装。

校验要求：

- 地址必须是公网 `http`/`https`，回环与内网字面地址会被拒绝。
- 提供 `sha256` 时严格比对；提供字节数时严格比对大小。
- 安装先写入临时目录并校验，通过后替换正式目录，避免留下半成品。
- 安装不会自动启用插件，需在插件页手动启用。

## 插件市场

市场是一个 JSON 索引：

```json
{
  "name": "示例市场",
  "updatedAt": "2026-01-01",
  "plugins": [
    {
      "id": "my-plugin",
      "name": "我的插件",
      "version": "1.0.0",
      "description": "简介",
      "author": "you",
      "homepage": "https://example.com",
      "downloadUrl": "https://example.com/plugins/my-plugin-1.0.0.jar",
      "sha256": "<jar 的 sha256>",
      "size": 12345,
      "apiVersion": 1,
      "permissions": ["notification"],
      "updateUrl": ""
    }
  ]
}
```

- 市场地址保存在配置项 `pluginMarketUrl`，可在插件页修改。
- 检查更新时优先使用市场条目；没有市场条目时回退到插件自带的 `updateUrl`，该地址返回单个同上结构的条目。
- 版本号不同即视为有更新；建议使用语义化版本。
- 示例索引见 `examples/plugins/market.json`。

## 接口兼容策略

- 新增可选方法或字段：不提升 `PluginApi.VERSION`，插件保持兼容。
- 删除或改变既有语义：提升 `PluginApi.VERSION`，旧插件在插件页显示“不兼容”。
- 宿主核心类不属于接口，插件不得依赖 `ani.rss.*`（`plugin.api` 除外）；跨版本使用时宿主实现可能变化。
- 插件应自行兜底空值：快照字段可能为空字符串或 `null`。

## 最佳实践

- 在 `onEnable` 中注册、在 `onDisable` 中释放资源；不要依赖 `onUnload` 做关键清理。
- 不要在静态字段中缓存宿主对象，重载后可能失效。
- 网络请求设置超时，失败时抛异常交由宿主记录与重试，不要静默吞掉。
- 日志不要输出令牌、密码或完整响应体。
- 周期任务保持幂等，避免在任务里做长时间阻塞操作。
- 需要持久化时使用 `dataDir()` 或 `saveConfig`，不要在插件目录外写文件。
