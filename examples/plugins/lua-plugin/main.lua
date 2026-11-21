--[[
  Lua 插件示例。

  宿主注入全局 api 表，提供日志、配置与扩展点注册能力。
  生命周期函数：on_load / on_enable / on_disable / on_unload，全部可选。
  扩展点必须在 on_enable 里注册；停用时宿主自动清理。
]]

local function read_config()
    local text = api.get_config()
    return text or ""
end

function on_load()
    api.log("Lua 示例插件已加载")
end

function on_enable()
    -- 通知渠道
    api.register_notification_channel({
        id = "lua-demo-channel",
        name = "Lua 示例渠道",
        fields = {
            {key = "endpoint", label = "接收地址", type = "TEXT", placeholder = "https://example.com/hook", required = true},
            {key = "token", label = "令牌", type = "PASSWORD"}
        },
        send = function(payload, options)
            api.log("发送通知 [" .. tostring(payload.status) .. "] " .. tostring(payload.title))
        end
    })

    -- 订阅过滤器：包含 sample 的标题拒绝
    api.register_item_filter({
        id = "lua-demo-filter",
        order = 300,
        decide = function(subscription, item)
            local title = item.title or ""
            if string.find(title, "sample", 1, true) then
                return "REJECT"
            end
            return "ABSTAIN"
        end
    })

    -- 订阅事件
    api.register_subscription_listener({
        id = "lua-demo-listener",
        on_created = function(subscription)
            api.log("新增订阅：" .. tostring(subscription.title))
        end,
        on_updated = function(subscription)
            api.log("修改订阅：" .. tostring(subscription.title))
        end,
        on_deleted = function(subscription)
            api.log("删除订阅：" .. tostring(subscription.title))
        end
    })

    -- 周期任务：每 30 分钟
    api.register_scheduled_task({
        id = "lua-demo-task",
        interval_minutes = 30,
        run = function(pluginId)
            api.log("周期任务执行：" .. tostring(pluginId))
        end
    })

    -- 设置区块
    api.register_settings_section({
        id = "lua-demo-settings",
        title = "Lua 示例设置",
        description = "这些字段以 JSON 形式保存在插件配置中。",
        fields = {
            {key = "enabled", label = "启用附加行为", type = "SWITCH"},
            {key = "mode", label = "模式", type = "SELECT", options = {"A", "B"}}
        }
    })

    -- 模板变量：模板里可写 ${luaTag}
    api.register_template_variables({
        id = "lua-demo-variables",
        variables = function(subscription)
            return {luaTag = "Lua-" .. tostring(subscription.group)}
        end
    })

    -- 下载拦截：追加子目录；返回 nil 表示取消下载（需要 DOWNLOAD_CANCEL 权限）
    api.register_download_interceptor({
        id = "lua-demo-download",
        order = 300,
        intercept = function(candidate)
            return {name = candidate.name, savePath = candidate.savePath .. "/lua-sub"}
        end
    })

    -- 私有数据：记录启用次数，演示跨重启持久化。缺少权限时跳过而不是中断启用。
    if api.has_permission("data-read") and api.has_permission("data-write") then
        local count = tonumber(api.read_data_file("state/enable-count.txt") or "0") or 0
        api.write_data_file("state/enable-count.txt", tostring(count + 1))
        api.log("第 " .. tostring(count + 1) .. " 次启用，数据文件：" .. table.concat(api.list_data_files("state"), ","))
    end

    -- 条目改写：过滤之后、重命名之前调整标题或字幕组
    api.register_item_transformer({
        id = "lua-demo-transformer",
        order = 300,
        transform = function(subscription, item, context)
            local title = item.title or ""
            if string.find(title, "[简日]", 1, true) then
                return {subgroup = "简日双语"}
            end
            return nil
        end
    })

    -- 界面页面：出现在插件卡片的「插件页面」里
    api.register_page({
        id = "dashboard",
        title = "Lua 示例页面",
        description = "宿主按白名单区块渲染，插件不提供 HTML。",
        blocks = {
            {type = "TEXT", title = "说明", text = "表格数据来自 records 接口。"},
            {type = "STATS", title = "概览", stats = {
                {label = "示例指标", value = "42", hint = "静态值"},
                {label = "记录数", value = "0"}
            }},
            {
                type = "TABLE", title = "记录", source = "records",
                columns = {{key = "time", label = "时间"}, {key = "title", label = "内容"}}
            },
            {
                type = "ACTIONS", title = "操作",
                actions = {{id = "noop", label = "示例操作", source = "status", method = "POST", confirm = true}}
            }
        }
    })

    -- 页面表格数据：返回数组
    api.register_endpoint({
        path = "records",
        methods = {"GET"},
        handle = function() return '[{"time":"示例","title":"示例记录"}]' end
    })

    -- 自定义接口：/api/plugin/api/lua-demo-plugin/status
    api.register_endpoint({
        path = "status",
        methods = {"GET", "POST"},
        handle = function(request)
            return '{"ok":true,"plugin":"lua-demo-plugin","method":"' .. tostring(request.method) .. '"}'
        end
    })
end

function on_disable()
    api.log("Lua 示例插件已停用")
end

function on_unload()
    api.log("Lua 示例插件已卸载")
end
