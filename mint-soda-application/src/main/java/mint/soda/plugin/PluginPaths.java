package mint.soda.plugin;

import mint.soda.commons.StoragePaths;
import mint.soda.util.other.ConfigUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;

import java.io.File;
import java.nio.file.Path;

/**
 * 插件目录布局。所有路径都限制在插件根目录内，防止路径穿越。
 */
public final class PluginPaths {

    /**
     * 插件 id 只允许字母、数字、点、下划线与连字符
     */
    public static final String ID_REG = "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$";

    private PluginPaths() {
    }

    public static File root() {
        ConfigUtil.getConfigDir();
        return StoragePaths.pluginsDir();
    }

    public static File registryFile() {
        return new File(root(), "installed.json");
    }

    public static void verifyId(String id) {
        Assert.notBlank(id, "插件标识不能为空");
        Assert.isTrue(ReUtil.isMatch(ID_REG, id), "插件标识不合法");
    }

    /**
     * 插件安装目录：plugins/{id}
     */
    public static File dir(String id) {
        verifyId(id);
        return resolve(root(), id);
    }

    /**
     * 插件数据目录：plugins/{id}/data
     */
    public static File dataDir(String id) {
        return resolve(dir(id), "data");
    }

    /**
     * 解析数据目录内的相对路径。
     * <p>
     * 先规范化再校验必须位于 {@code plugins/{id}/data} 内，拒绝 {@code ..} 与绝对路径。
     *
     * @param id       插件标识
     * @param relative 相对路径，可为空
     * @return 数据目录内的文件
     */
    public static File dataFile(String id, String relative) {
        File base = dataDir(id).getAbsoluteFile();
        Path basePath = base.toPath().normalize();
        Path target = basePath.resolve(StrUtil.nullToEmpty(relative).trim()).normalize();
        Assert.isTrue(target.startsWith(basePath), "非法的数据路径");
        return target.toFile();
    }

    /**
     * 插件可执行文件：plugins/{id}/plugin.jar
     */
    public static File jarFile(String id) {
        return resolve(dir(id), "plugin.jar");
    }

    public static File descriptorFile(String id) {
        return resolve(dir(id), "plugin.json");
    }

    private static File resolve(File parent, String child) {
        Path base = parent.getAbsoluteFile().toPath().normalize();
        Path target = base.resolve(child).normalize();
        Assert.isTrue(target.startsWith(base), "非法的路径");
        return target.toFile();
    }

    public static void ensure(File dir) {
        FileUtil.mkdir(dir);
    }
}
