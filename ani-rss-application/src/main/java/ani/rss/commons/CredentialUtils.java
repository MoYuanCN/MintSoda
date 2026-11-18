package ani.rss.commons;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 登录凭据的强度校验与随机生成。
 */
public class CredentialUtils {
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGIT = "23456789";
    private static final String SYMBOL = "!#%+-_=@";
    private static final String ALL = LOWER + UPPER + DIGIT + SYMBOL;

    public static final int MIN_PASSWORD_LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 校验密码强度。
     *
     * @param password 明文密码
     * @return 不满足要求时的提示，满足时返回 null
     */
    public static String checkPassword(String password) {
        if (StrUtil.isBlank(password)) {
            return "密码不能为空";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return StrUtil.format("密码至少需要 {} 位", MIN_PASSWORD_LENGTH);
        }
        int types = 0;
        if (password.chars().anyMatch(Character::isLowerCase)) types++;
        if (password.chars().anyMatch(Character::isUpperCase)) types++;
        if (password.chars().anyMatch(Character::isDigit)) types++;
        if (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) types++;
        if (types < 2) {
            return "密码需包含大写字母、小写字母、数字、符号中的至少两类";
        }
        return null;
    }

    /**
     * 校验用户名格式。
     *
     * @param username 用户名
     * @return 不满足要求时的提示，满足时返回 null
     */
    public static String checkUsername(String username) {
        if (StrUtil.isBlank(username)) {
            return "用户名不能为空";
        }
        if (username.length() < 3 || username.length() > 32) {
            return "用户名长度需在 3 到 32 之间";
        }
        if (!username.matches("[A-Za-z0-9_-]+")) {
            return "用户名只能包含字母、数字、下划线和连字符";
        }
        return null;
    }

    /**
     * 生成登录密码，保证包含四类字符。
     *
     * @return 随机密码
     */
    public static String randomPassword() {
        List<Character> chars = new ArrayList<>();
        chars.add(pick(LOWER));
        chars.add(pick(UPPER));
        chars.add(pick(DIGIT));
        chars.add(pick(SYMBOL));
        for (int i = chars.size(); i < 20; i++) {
            chars.add(pick(ALL));
        }
        Collections.shuffle(chars, RANDOM);
        StringBuilder builder = new StringBuilder(chars.size());
        chars.forEach(builder::append);
        return builder.toString();
    }

    /**
     * 生成随机用户名。
     *
     * @return 随机用户名
     */
    public static String randomUsername() {
        StringBuilder builder = new StringBuilder("ani_");
        for (int i = 0; i < 6; i++) {
            builder.append(pick(LOWER));
        }
        return builder.toString();
    }

    public static String md5(String raw) {
        return SecureUtil.md5(raw);
    }

    private static char pick(String source) {
        return source.charAt(RANDOM.nextInt(source.length()));
    }
}
