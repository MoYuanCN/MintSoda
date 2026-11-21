package mint.soda.entity.dto;

import mint.soda.entity.Ani;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Data
@Accessors(chain = true)
public class ImportAniDataDTO implements Serializable {
    private String filename;
    private List<Ani> aniList;
    private Conflict conflict;

    public enum Conflict {
        REPLACE,
        SKIP
    }
}
