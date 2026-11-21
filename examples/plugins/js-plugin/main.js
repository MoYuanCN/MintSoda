/**
 * JavaScript 插件示例。
 *
 * 宿主把两个全局对象注入脚本：
 * - __descriptor：插件描述（id、name、version、apiVersion、author、description）
 * - __api：宿主能力，见下方 api 的用法
 *
 * 生命周期函数都是可选的：onLoad / onEnable / onDisable / onUnload。
 * 扩展点必须在 onEnable 中注册；停用时宿主会自动清理，无需手动反注册。
 */

// 读取插件保存的配置（字符串），示例给出默认值。
function readConfig() {
    try {
        return JSON.parse(__api.getConfig() || '{}');
    } catch (e) {
        return {};
    }
}

function onLoad() {
    __api.log('示例脚本插件已加载：' + __descriptor.name);
    // 私有数据：读取上次启用次数并累加，升级与重启都不会丢失。缺少权限时跳过。
    if (__api.hasPermission('data-read') && __api.hasPermission('data-write')) {
        const previous = Number(__api.readDataFile('state/enable-count.txt') || 0) || 0;
        __api.writeDataFile('state/enable-count.txt', String(previous + 1));
        __api.log('第 ' + (previous + 1) + ' 次加载，数据文件：' + __api.listDataFiles('state').join(','));
    }
}

function onEnable() {
    // ---- 通知渠道 ----
    __api.notificationChannel({
        id: 'js-demo-channel',
        name: 'JavaScript 示例渠道',
        fields: [
            {key: 'endpoint', label: '接收地址', type: 'TEXT', placeholder: 'https://example.com/hook', required: true},
            {key: 'token', label: '令牌', type: 'PASSWORD'}
        ],
        send: function (payload, options) {
            __api.log('发送通知 [' + payload.status + '] ' + payload.title + ' -> ' + payload.text);
        }
    });

    // ---- 订阅过滤器：只演示能力，不做拦截 ----
    __api.itemFilter({
        id: 'js-demo-filter',
        order: 200,
        decide: function (subscription, item) {
            if (item.title && item.title.indexOf('sample') >= 0) {
                return 'REJECT';
            }
            return 'ABSTAIN';
        }
    });

    // ---- 订阅事件 ----
    __api.subscriptionListener({
        id: 'js-demo-listener',
        onCreated: function (subscription) {
            __api.log('新增订阅：' + subscription.title);
        },
        onUpdated: function (subscription) {
            __api.log('修改订阅：' + subscription.title);
        },
        onDeleted: function (subscription) {
            __api.log('删除订阅：' + subscription.title);
        }
    });

    // ---- 周期任务：每 30 分钟一次 ----
    __api.scheduledTask({
        id: 'js-demo-task',
        intervalMinutes: 30,
        run: function (pluginId) {
            __api.log('周期任务执行：' + pluginId);
        }
    });

    // ---- 设置区块 ----
    __api.settingsSection({
        id: 'js-demo-settings',
        title: 'JavaScript 示例设置',
        description: '这些字段以 JSON 形式保存在插件配置中。',
        fields: [
            {key: 'enabled', label: '启用附加行为', type: 'SWITCH'},
            {key: 'mode', label: '模式', type: 'SELECT', options: ['A', 'B']}
        ]
    });

    // ---- 模板变量：模板里可写 ${jsTag} ----
    __api.templateVariables({
        id: 'js-demo-variables',
        variables: function (subscription) {
            return {jsTag: 'JS-' + subscription.group};
        }
    });

    // ---- 下载拦截：改写保存位置 ----
    __api.downloadInterceptor({
        id: 'js-demo-download',
        order: 200,
        intercept: function (candidate) {
            // 返回 null 可取消本次下载；修改 savePath/name 会作用到实际任务。
            return {savePath: candidate.savePath + '/js-sub', name: candidate.name};
        }
    });

    // ---- 条目改写：过滤之后、重命名之前调整标题或字幕组 ----
    __api.itemTransformer({
        id: 'js-demo-transformer',
        order: 200,
        transform: function (subscription, item, context) {
            if (item.title && item.title.indexOf('[简日]') >= 0) {
                return {subgroup: '简日双语'};
            }
            return null;
        }
    });

    // ---- 界面页面：出现在插件卡片的「插件页面」里 ----
    __api.page({
        id: 'dashboard',
        title: 'JavaScript 示例页面',
        icon: 'DataLine',
        description: '宿主按白名单区块渲染，插件不提供 HTML。',
        blocks: [
            {type: 'TEXT', title: '说明', text: '表格数据来自 records 接口。'},
            {
                type: 'STATS', title: '概览',
                stats: [{label: '示例指标', value: '42', hint: '静态值'}, {label: '记录数', value: '0'}]
            },
            {
                type: 'TABLE', title: '记录', source: 'records',
                columns: [{key: 'time', label: '时间'}, {key: 'title', label: '内容'}]
            },
            {
                type: 'ACTIONS', title: '操作',
                actions: [{id: 'noop', label: '示例操作', source: 'status', method: 'POST', confirm: true}]
            }
        ]
    });

    // ---- 页面表格数据：返回数组或 {items: [...]} ----
    __api.endpoint({
        path: 'records',
        methods: ['GET'],
        handle: function () {
            return JSON.stringify([{time: new Date().toISOString(), title: '示例记录'}]);
        }
    });

    // ---- 自定义接口：/api/plugin/api/js-demo-plugin/status ----
    __api.endpoint({
        path: 'status',
        methods: ['GET', 'POST'],
        handle: function (request) {
            return JSON.stringify({
                ok: true,
                plugin: __descriptor.id,
                method: request.method,
                config: readConfig()
            });
        }
    });
    // ---- 插件联动：订阅其他插件发布的消息 ----
    if (__api.hasPermission('plugin-message-receive')) {
        __api.subscribe('mintsoda.demo.heartbeat', function (message) {
            __api.log('收到 ' + message.source + ' 的心跳：' + message.payload);
        });

        // 应答式主题：其他插件可以 request 本插件，返回值会作为应答内容。
        __api.serve('mintsoda.demo.info', function () {
            return JSON.stringify({id: __descriptor.id, version: __descriptor.version});
        });
    }

    // ---- 插件联动：向其他插件广播与查询 ----
    if (__api.hasPermission('plugin-message-send')) {
        __api.publish('mintsoda.demo.heartbeat', JSON.stringify({at: new Date().toISOString()}));
        const replies = __api.request('mintsoda.demo.info', 'ping');
        __api.log('收到 ' + replies.length + ' 个应答');
    }

    // ---- 插件发现：列出其他已安装插件 ----
    if (__api.hasPermission('plugin-discovery')) {
        const plugins = __api.plugins().filter(item => item.id !== __descriptor.id);
        __api.log('可联动的插件：' + plugins.map(item => item.id).join(', '));
    }
}

function onDisable() {
    __api.log('示例脚本插件已停用');
}

function onUnload() {
    __api.log('示例脚本插件已卸载');
}
