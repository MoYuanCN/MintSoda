package mint.soda.commons;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.XmlUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.system.OsInfo;
import cn.hutool.system.SystemUtil;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.info.BuildProperties;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.Serializable;
import java.util.Objects;

@Slf4j
public class MavenUtils {
    private static String version;

    public static CurrentFile getCurrentFile() {
        // Launch4j exposes the actual wrapper path separately from java.class.path,
        // which points to the bundled JAR when the application is started as an EXE.
        String launch4jExe = System.getProperty("launch4j.exefile");
        if (StrUtil.isNotBlank(launch4jExe)) {
            File exeFile = new File(launch4jExe);
            if (exeFile.isFile()) {
                return new CurrentFile().setFile(exeFile);
            }
        }

        OsInfo osInfo = SystemUtil.getOsInfo();
        String splitStr = osInfo.isWindows() ? ";" : ":";
        String s = System.getProperty("java.class.path")
                .split(splitStr)[0];
        return new CurrentFile()
                .setFile(new File(s));
    }

    /**
     * 程序所在目录，即 JAR 或 EXE 的父目录。
     * <p>
     * 开发环境（classpath 指向 classes 目录）无法确定安装位置，返回 {@code null}。
     *
     * @return 程序目录；无法判断时返回 null
     */
    public static File getApplicationDir() {
        CurrentFile currentFile = getCurrentFile();
        File file = currentFile.getFile();
        // 只有确实存在且为文件的 JAR / EXE 才能确定安装位置；
        // 开发环境的 classes 目录或空 classpath 一律视为未知。
        if (file == null || !file.isFile()) {
            return null;
        }
        return file.getAbsoluteFile().getParentFile();
    }

    public static String getVersion() {
        if (Objects.nonNull(version)) {
            return version;
        }
        File file = new File("pom.xml");
        if (file.exists()) {
            Document document = XmlUtil.readXML(file);
            Element element = XmlUtil.getElement(document.getDocumentElement(), "version");
            if (Objects.nonNull(element)) {
                version = element.getTextContent();
                return version;
            }
        }
        BuildProperties buildProperties = SpringUtil.getBean(BuildProperties.class);
        version = buildProperties.getVersion();
        return version;
    }

    @Data
    @Accessors(chain = true)
    public static class CurrentFile implements Serializable {
        private File file;

        public String getName() {
            return file.getName();
        }

        public Boolean isDirectory() {
            return file.isDirectory();
        }

        public Boolean isFile() {
            return file.isFile();
        }

        public Boolean isExe() {
            if (isDirectory()) {
                return false;
            }

            String extName = FileUtil.extName(file);
            if (StrUtil.isBlank(extName)) {
                return false;
            }

            return "exe".equalsIgnoreCase(extName);
        }

        public Boolean isJar() {
            if (isDirectory()) {
                return false;
            }

            String extName = FileUtil.extName(file);
            if (StrUtil.isBlank(extName)) {
                return false;
            }

            return "jar".equalsIgnoreCase(extName);
        }
    }

}
