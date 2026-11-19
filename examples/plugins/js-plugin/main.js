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
}

function onDisable() {
    __api.log('示例脚本插件已停用');
}

function onUnload() {
    __api.log('示例脚本插件已卸载');
}
