package ani.rss.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.UUID;

/** A title regex routed to a target subtitle group. */
@Data
@Accessors(chain = true)
public class ReverseMatchRule implements Serializable {
    private String id = UUID.randomUUID().toString();
    private String pattern;
    private String subgroup;
}
