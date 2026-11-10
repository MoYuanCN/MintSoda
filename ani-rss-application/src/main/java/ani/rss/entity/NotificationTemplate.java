package ani.rss.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.UUID;

/** A reusable plain-text or JSON notification template. */
@Data
@Accessors(chain = true)
@Schema(description = "通知模板")
public class NotificationTemplate implements Serializable {
    private String id = UUID.randomUUID().toString();
    private String name;

    /** Template content type: TEXT or JSON. */
    private String type;

    /** Shared content used by the selected template type. */
    private String content;

    /** Legacy text-template field retained for configuration compatibility. */
    private String telegramTemplate;

    /** Legacy JSON-template field retained for configuration compatibility. */
    private String customRequestBody;
}
