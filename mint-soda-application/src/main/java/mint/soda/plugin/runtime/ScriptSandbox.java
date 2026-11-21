package mint.soda.plugin.runtime;

import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.EvaluatorException;

/**
 * JS 插件的执行沙箱。
 * <p>
 * 三重限制：脚本看不到任何 Java 类（{@link ClassShutter} 全拒绝）、标准库使用
 * {@code initSafeStandardObjects} 不含 {@code Packages}、单次调用按指令数中断，
 * 避免死循环占满线程。每个插件持有独立的工厂与截止时间，互不影响。
 */
public final class ScriptSandbox extends ContextFactory {

    /**
     * 单次脚本调用的最长执行时间
     */
    private static final long TIMEOUT_MS = 5_000L;

    /**
     * 每执行多少条指令检查一次超时
     */
    private static final int OBSERVE_INTERVAL = 10_000;

    /**
     * 当前线程的截止时间，由 {@link #invoke} 设置。
     */
    private final ThreadLocal<Long> deadline = new ThreadLocal<>();

    @Override
    protected Context makeContext() {
        Context cx = super.makeContext();
        cx.setLanguageVersion(Context.VERSION_ES6);
        // 解释执行，不生成字节码，降低内存占用与 JIT 风险。
        cx.setOptimizationLevel(-1);
        cx.setInstructionObserverThreshold(OBSERVE_INTERVAL);
        cx.setClassShutter(rejectAll());
        return cx;
    }

    @Override
    protected void observeInstructionCount(Context cx, int instructionCount) {
        Long limit = deadline.get();
        if (limit != null && System.currentTimeMillis() > limit) {
            throw new EvaluatorException("插件脚本执行超时");
        }
    }

    /**
     * 执行一段脚本逻辑，超时或异常向上抛出，由调用方记录。
     */
    public <T> T invoke(ScriptTask<T> task) {
        long previous = deadline.get() == null ? 0L : deadline.get();
        deadline.set(System.currentTimeMillis() + TIMEOUT_MS);
        // 必须走本实例的 enterContext，静态 Context.enter 会使用全局工厂，绕过沙箱配置。
        Context cx = enterContext();
        try {
            return task.run(cx);
        } finally {
            deadline.set(previous == 0L ? null : previous);
            Context.exit();
        }
    }

    @FunctionalInterface
    public interface ScriptTask<T> {
        T run(Context cx);
    }

    private static ClassShutter rejectAll() {
        return className -> false;
    }
}
