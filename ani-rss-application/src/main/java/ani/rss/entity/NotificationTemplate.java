package ani.rss.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.UUID;

/** A reusable Telegram message and/or custom request body template. */
@Data
@Accessors(chain = true)
@Schema(description = "通知模板")
public class NotificationTemplate implements Serializable {
    private String id = UUID.randomUUID().toString();
    private String name;
    private String telegramTemplate;
    private String customRequestBody;
}
