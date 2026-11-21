package mint.soda.service;

import mint.soda.task.BaseTask;
import mint.soda.task.BgmTask;
import mint.soda.task.CacheClearTask;
import mint.soda.task.RenameTask;
import mint.soda.task.RssTask;
import cn.hutool.core.text.NamingCase;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class TaskService {
    public static final AtomicBoolean LOOP = new AtomicBoolean(false);
    public static final List<Thread> THREADS = new Vector<>();

    public void stop() {
        LOOP.set(false);
        for (Thread thread : THREADS) {
            try {
                // 等待现有任务结束
                while (thread.isAlive()) {
                    thread.interrupt();
                    ThreadUtil.sleep(100);
                }
                thread.join();
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
        THREADS.clear();
    }

    public void restart() {
        stop();
        start();
    }

    public void start() {
        if (LOOP.get() && !THREADS.isEmpty()) {
            log.warn("任务已经在运行中");
            return;
        }
        LOOP.set(true);

        List<Class<? extends BaseTask>> classList = List.of(
                RenameTask.class, RssTask.class, BgmTask.class, CacheClearTask.class);

        for (Class<? extends BaseTask> aClass : classList) {
            BaseTask task = SpringUtil.getBean(aClass);
            String name = aClass.getSimpleName();
            String threadName = NamingCase.toKebabCase(name);
            THREADS.add(new Thread(() -> task.run(threadName, LOOP)));
        }
        for (Thread thread : THREADS) {
            thread.start();
        }
    }
}
