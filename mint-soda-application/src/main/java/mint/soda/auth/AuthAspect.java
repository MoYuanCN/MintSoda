package mint.soda.auth;

import mint.soda.annotation.Auth;
import mint.soda.entity.Global;
import mint.soda.entity.web.Result;
import mint.soda.entity.web.ResultCode;
import mint.soda.exception.ResultException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuthAspect {
    @Before("@annotation(auth)")
    public void before(JoinPoint joinPoint, Auth auth) {
        HttpServletRequest request = Global.REQUEST.get();
        if (AuthUtil.test(request, auth)) {
            // 鉴权通过
            return;
        }

        throw new ResultException(
                Result.error(r ->
                        r.setCode(ResultCode.HTTP_FORBIDDEN)
                                .setMessage("登录已失效")
                )
        );
    }
}
