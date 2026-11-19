package ani.rss.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class DeveloperSettings implements Serializable {
    private String username;
    /** MD5 digest, matching the application's existing login transport. */
    private String password;
    private String customTorrentUrl;
    /** 自定义 User-Agent，留空使用内置标识 */
    private String customUserAgent;
    /** 是否记录出站请求耗时与状态码 */
    private Boolean devHttpTrace;
}
