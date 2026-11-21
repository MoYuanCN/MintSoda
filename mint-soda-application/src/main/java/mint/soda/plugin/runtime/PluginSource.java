package mint.soda.plugin.runtime;

import mint.soda.plugin.api.Plugin;

/**
 * 不同类型的插件来源统一为同一种可加载单元。
 * <p>
 * Java 插件由类加载器加载入口类，脚本插件由脚本引擎解释执行；管理器只依赖
 * 本接口，无需关心具体运行时，后续新增语言只需实现一个工厂。
 */
public interface PluginSource {

    /**
     * 加载并返回插件实例。
     */
    Plugin load();

    /**
     * 释放运行时资源，例如类加载器或脚本上下文。
     */
    default void close() {
    }
}
