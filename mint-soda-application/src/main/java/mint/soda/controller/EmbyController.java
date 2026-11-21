package mint.soda.controller;

import mint.soda.annotation.Auth;
import mint.soda.entity.EmbyViews;
import mint.soda.entity.NotificationConfig;
import mint.soda.entity.web.Result;
import mint.soda.service.EmbyRefreshService;
import jakarta.annotation.Resource;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping
public class EmbyController extends BaseController {

    @Resource
    private EmbyRefreshService embyRefreshService;

    @Auth
    @Operation(summary = "获取媒体库")
    @PostMapping("/getEmbyViews")
    public Result<List<EmbyViews>> getEmbyViews(@RequestBody NotificationConfig notificationConfig) {
        List<EmbyViews> views = embyRefreshService.getViews(notificationConfig);
        return Result.success(views);
    }

}
