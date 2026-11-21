package mint.soda.service;

import mint.soda.commons.FileUtils;
import mint.soda.commons.StoragePaths;
import mint.soda.entity.Ani;
import mint.soda.util.other.AniUtil;
import mint.soda.util.other.ConfigUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ClearService {

    /**
     * 清理文件夹
     *
     * @param dir 文件夹
     */
    public void clearDir(String dir) {
        clearDir(new File(dir));
    }

    /**
     * 清理文件夹
     *
     * @param dir 文件夹
     */
    public void clearDir(File dir) {
        clearDir(dir, true, true, 2);
    }

    /**
     * 清理文件夹
     *
     * @param dir          文件夹
     * @param excludeImage 排除图片
     * @param excludeNfo   排除nfo
     * @param max          向上删除深度
     */
    public void clearDir(File dir, boolean excludeImage, boolean excludeNfo, int max) {
        File parentFile = dir;
        for (int i = 0; i < max; i++) {
            if (Objects.isNull(parentFile)) {
                return;
            }
            if (!isEmpty(parentFile, excludeImage, excludeNfo)) {
                // 不为空则不进行清理
                return;
            }
            log.info("清理空文件夹 {}", parentFile);
            FileUtil.del(parentFile);
            parentFile = parentFile.getParentFile();
        }
    }

    /**
     * 文件夹是否为空
     *
     * @param excludeImage 排除图片
     * @param excludeNfo   排除nfo
     * @param dir          文件夹
     * @return 是否为空
     */
    public Boolean isEmpty(File dir, boolean excludeImage, boolean excludeNfo) {
        List<File> list = FileUtils.listFileList(dir);

        long count = list.stream()
                .filter(f -> {
                    if (f.isDirectory()) {
                        return true;
                    }

                    if (excludeImage && FileUtils.isImageFormat(f.getName())) {
                        return false;
                    }

                    String name = f.getName();
                    String extName = FileUtil.extName(name);
                    if (StrUtil.isNotBlank(extName)) {
                        if (excludeNfo && extName.equalsIgnoreCase("nfo")) {
                            return false;
                        }
                    }

                    List<String> excludeFileNames = List.of(".DS_Store", "Thumbs.db", "bangumi.ini");
                    for (String excludeFileName : excludeFileNames) {
                        if (excludeFileName.equalsIgnoreCase(name)) {
                            return false;
                        }
                    }
                    return true;
                })
                .count();

        return count < 1;
    }

    /**
     * 手动清理未被引用的缓存，并记录清理时间。
     *
     * @return 释放的大小
     */
    public Long clearUnusedCache() {
        Long size = clearCover();
        ConfigUtil.CONFIG.setCacheLastClearTime(System.currentTimeMillis());
        ConfigUtil.sync();
        return size;
    }

    /**
     * 清理残余封面
     *
     * @return 清理大小 bytes
     */
    public Long clearCover() {
        File filesDir = StoragePaths.coversDir();
        File imgDir = StoragePaths.previewsDir();

        FileUtil.mkdir(filesDir);
        FileUtil.mkdir(imgDir);

        Set<String> covers = AniUtil.ANI_LIST
                .stream()
                .map(Ani::getCover)
                .map(s -> FileUtils.getAbsolutePath(Path.of(filesDir.getPath(), s).toFile()))
                .collect(Collectors.toSet());

        Set<File> files = FileUtil.loopFiles(filesDir)
                .stream()
                .filter(file -> {
                    String fileName = FileUtils.getAbsolutePath(file);
                    return !covers.contains(fileName);
                }).collect(Collectors.toSet());
        long filesSize = files.stream()
                .mapToLong(File::length)
                .sum();

        for (File file : files) {
            FileUtil.del(file);
            clearDir(file, false, true, 2);
        }
        // 预览图只是转发缓存，整目录删除后会按需重新下载。
        long imgSize = FileUtil.size(imgDir);
        FileUtil.del(imgDir);
        FileUtil.mkdir(imgDir);

        return filesSize + imgSize;
    }

}
