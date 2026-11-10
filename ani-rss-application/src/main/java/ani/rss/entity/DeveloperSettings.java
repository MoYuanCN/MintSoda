package ani.rss.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class DeveloperSettings implements Serializable {
    private String username;
    /** MD5 digest, matching the application's existing login transport. */
    private String password;
    private String customTorrentUrl;
}
