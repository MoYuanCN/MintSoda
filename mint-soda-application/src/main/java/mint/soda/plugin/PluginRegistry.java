package mint.soda.plugin;

import mint.soda.handle.JsonReader;
import mint.soda.handle.JsonWriter;
import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 插件启用状态与配置的持久化，使用与主配置一致的 JSON 读写方式。
 */
@Slf4j
public class PluginRegistry {

    private final Map<String, PluginRecord> records = new LinkedHashMap<>();

    public static PluginRegistry load() {
        PluginRegistry registry = new PluginRegistry();
        var file = PluginPaths.registryFile();
        if (file.exists()) {
            try {
                List<PluginRecord> list = JsonReader.getInstance(file).toList(PluginRecord.class, new ArrayList<>());
                list.stream()
                        .filter(record -> record != null && record.getId() != null)
                        .forEach(record -> registry.records.put(record.getId(), record));
            } catch (Exception e) {
                log.error("插件状态读取失败 {}", file);
                log.error(e.getMessage(), e);
            }
        }
        return registry;
    }

    public synchronized void sync() {
        FileUtil.mkdir(PluginPaths.root());
        JsonWriter.getInstance(PluginPaths.registryFile())
                .writer(new ArrayList<>(records.values()));
    }

    public Optional<PluginRecord> get(String id) {
        return Optional.ofNullable(records.get(id));
    }

    public PluginRecord getOrCreate(String id) {
        return records.computeIfAbsent(id, key -> new PluginRecord()
                .setId(key)
                .setEnable(false)
                .setSource("")
                .setInstalledAt(System.currentTimeMillis())
                .setConfig("")
                .setLatestVersion("")
                .setUpdateAvailable(false)
                .setLastCheckTime(0L));
    }

    public void remove(String id) {
        records.remove(id);
    }

    public List<PluginRecord> list() {
        return new ArrayList<>(records.values());
    }
}
