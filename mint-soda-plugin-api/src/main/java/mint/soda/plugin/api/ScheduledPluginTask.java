package mint.soda.plugin.api;

import java.time.Duration;

/**
 * 插件周期任务。宿主使用独立线程调度，异常不会中断其他插件。
 */
public interface ScheduledPluginTask {

    String id();

    /**
     * 执行间隔
     */
    Duration interval();

    /**
     * 首次执行前的延迟
     */
    default Duration initialDelay() {
        return interval();
    }

    void run(PluginContext context) throws Exception;
}
