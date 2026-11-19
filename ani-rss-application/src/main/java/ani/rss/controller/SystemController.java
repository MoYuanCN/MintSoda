package ani.rss.controller;

import ani.rss.annotation.Auth;
import ani.rss.commons.MavenUtils;
import ani.rss.entity.SystemStatus;
import ani.rss.entity.web.Result;
import ani.rss.util.other.AniUtil;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;

/**
 * 运行状态接口。只返回聚合后的数字，不包含配置、路径或凭据。
 */
@RestController
public class SystemController extends BaseController {

    private static final long START_TIME = ManagementFactory.getRuntimeMXBean().getStartTime();

    @Auth
    @Operation(summary = "运行状态")
    @PostMapping("/system/status")
    public Result<SystemStatus> status() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        int subscriptions = AniUtil.ANI_LIST.size();
        int enabled = (int) AniUtil.ANI_LIST.stream()
                .filter(ani -> Boolean.TRUE.equals(ani.getEnable()))
                .count();
        Double load = os.getSystemLoadAverage();
        SystemStatus status = new SystemStatus()
                .setVersion(MavenUtils.getVersion())
                .setUptime(System.currentTimeMillis() - START_TIME)
                .setSubscriptions(subscriptions)
                .setEnabledSubscriptions(enabled)
                .setHeapUsed(heap.getUsed())
                .setHeapMax(heap.getMax())
                .setThreads(ManagementFactory.getThreadMXBean().getThreadCount())
                .setProcessors(os.getAvailableProcessors())
                .setLoadAverage(load == null ? -1 : Math.round(load * 100) / 100.0);
        return Result.success(status);
    }
}
