package mint.soda.auth.enums;

import mint.soda.auth.fun.ApiKey;
import mint.soda.auth.fun.Form;
import mint.soda.auth.fun.Header;
import mint.soda.auth.fun.IpWhitelist;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.function.Function;

/**
 * 可以扩展出 api_key cookie 校验
 */
@AllArgsConstructor
public enum AuthType {
    HEADER(Header.class),
    FORM(Form.class),
    API_KEY(ApiKey.class),
    IP_WHITE_LIST(IpWhitelist.class);

    @Getter
    private final Class<? extends Function<HttpServletRequest, Boolean>> clazz;
}
