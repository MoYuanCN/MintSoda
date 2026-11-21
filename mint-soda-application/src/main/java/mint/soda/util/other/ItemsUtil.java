package mint.soda.util.other;

import mint.soda.cache.CacheUtils;
import mint.soda.commons.FileUtils;
import mint.soda.entity.Ani;
import mint.soda.entity.Config;
import mint.soda.entity.Item;
import mint.soda.entity.StandbyRss;
import mint.soda.enums.NotificationStatusEnum;
import mint.soda.enums.StringEnum;
import mint.soda.plugin.PluginHooks;
import mint.soda.util.basic.HttpReq;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.*;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;


@Slf4j
public class ItemsUtil {
    private static final Config CONFIG = ConfigUtil.CONFIG;
    /**
     * RSS 预取线程池。固定线程数、空闲线程回收后复用，避免每轮刷新都新建线程池；
     * 单轮并发上限仍由 {@link Semaphore} 控制，与原有语义一致。
     */
    private static final ThreadPoolExecutor PREFETCH_EXECUTOR = new ThreadPoolExecutor(
            16, 16, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
        Thread thread = new Thread(r, "rss-prefetch");
        thread.setDaemon(true);
        return thread;
    });
    static {
        PREFETCH_EXECUTOR.allowCoreThreadTimeOut(true);
    }

    /**
     * 开始一轮任务，清理上一轮的响应缓存。委托给 {@link RssFetcher}。
     */
    public static void beginRssBatch() {
        RssFetcher.beginBatch();
    }

    /**
     * 结束一轮任务。
     */
    public static void endRssBatch() {
        RssFetcher.endBatch();
    }

    /**
     * 并发预取一批订阅的 RSS 响应，写入本轮缓存供下载流程直接命中。
     * 单个地址的失败只记录日志，不中断整轮刷新。
     *
     * @param aniList     订阅列表
     * @param concurrency 并发数
     */
    public static void prefetchRss(List<Ani> aniList, int concurrency) {
        Set<String> urls = new LinkedHashSet<>();
        boolean standbyRss = Boolean.TRUE.equals(CONFIG.getStandbyRss());
        for (Ani ani : aniList) {
            if (StrUtil.isNotBlank(ani.getUrl())) {
                urls.add(ani.getUrl());
            }
            if (!standbyRss) {
                continue;
            }
            Optional.ofNullable(ani.getStandbyRssList())
                    .orElseGet(List::of)
                    .stream()
                    .map(StandbyRss::getUrl)
                    .filter(StrUtil::isNotBlank)
                    .forEach(urls::add);
        }

        urls.removeIf(RssFetcher::cached);
        if (urls.isEmpty()) {
            return;
        }

        int threads = Math.max(1, Math.min(Math.max(1, concurrency), urls.size()));
        Semaphore permits = new Semaphore(threads);
        List<Future<?>> futures = new ArrayList<>();
        for (String url : urls) {
            futures.add(PREFETCH_EXECUTOR.submit(() -> {
                try {
                    permits.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    getRss(url);
                } catch (Exception e) {
                    log.debug("预取 RSS 失败 {} {}", url, e.getMessage());
                } finally {
                    permits.release();
                }
            }));
        }
        // 为整体预取设定上限，间隔很短时不会因为个别站点缓慢拖住整轮刷新。
        int rssTimeout = Math.max(1, ObjectUtil.defaultIfNull(CONFIG.getRssTimeout(), 20));
        long budgetSeconds = (long) Math.ceil((double) urls.size() / threads) * rssTimeout;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(10, Math.min(budgetSeconds, rssTimeout * 2L)));
        for (Future<?> future : futures) {
            try {
                future.get(Math.max(1L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("预取 RSS 被中断");
                return;
            } catch (TimeoutException e) {
                // 单个地址超时不再等待，其余任务继续在后台完成后写入本轮缓存。
                log.debug("预取 RSS 超时");
                return;
            } catch (ExecutionException e) {
                log.debug("预取 RSS 失败 {}", e.getMessage());
            }
        }
    }

    /**
     * 获取视频列表
     *
     * @param ani 订阅
     * @return 视频列表
     */
    public static List<Item> getItems(Ani ani) {
        String url = ani.getUrl();
        String subgroup = StrUtil.blankToDefault(ani.getSubgroup(), "未知字幕组");
        List<Item> items = new ArrayList<>(ItemsUtil.getItems(ani, url, subgroup));
        for (Item item : items) {
            item.setMaster(true);
        }

        if (!Boolean.TRUE.equals(CONFIG.getStandbyRss())) {
            items.sort(Comparator.comparingDouble(Item::getEpisode));
            return items;
        }

        List<StandbyRss> standbyRssList = ani.getStandbyRssList();
        for (StandbyRss rss : standbyRssList) {
            subgroup = StrUtil.blankToDefault(rss.getLabel(), "未知字幕组");
            // 只需替换集数偏移，浅拷贝避免每个备用 RSS 都序列化整个订阅对象。
            Ani clone = new Ani();
            BeanUtil.copyProperties(ani, clone);
            clone.setOffset(rss.getOffset());
            List<Item> standbyItems = ItemsUtil.getItems(clone, rss.getUrl(), subgroup);
            for (Item item : standbyItems) {
                item.setMaster(false);
            }
            items.addAll(standbyItems);
        }
        // 多字幕组共存模式
        Boolean coexist = CONFIG.getCoexist();
        if (coexist) {
            items = CollUtil.distinct(items, Item::getReName, false);
        } else {
            items = CollUtil.distinct(items, it -> it.getEpisode().toString(), false);
        }
        items.sort(Comparator.comparingDouble(Item::getEpisode));
        return items;
    }

    /**
     * 获取视频列表
     *
     * @param ani          订阅
     * @param rssUrl       RSS链接
     * @param subgroupName 字幕组名
     * @return 视频列表
     */
    public static List<Item> getItems(Ani ani, String rssUrl, String subgroupName) {
        String xml = getRss(rssUrl);

        List<Item> items = new ArrayList<>();

        Document document = XmlUtil.readXML(xml);
        Node channel = document.getElementsByTagName("channel").item(0);
        NodeList childNodes = channel.getChildNodes();

        // 反匹配命中的条目与目标字幕组，等解析出集数后再写入路由记录。
        Map<Item, String> reverseTargets = new IdentityHashMap<>();
        // 本轮是否新增了反匹配路由记录，结束时统一写盘。
        boolean assignmentChanged = false;
        for (int i = childNodes.getLength() - 1; i >= 0; i--) {
            Node item = childNodes.item(i);
            String nodeName = item.getNodeName();
            if (!nodeName.equals("item")) {
                continue;
            }
            String itemTitle = "";
            String torrent = "";
            String length = "";
            String infoHash = "";

            String formatSize = "0MiB";

            DateTime pubDate = null;

            NodeList itemChildNodes = item.getChildNodes();
            for (int j = 0; j < itemChildNodes.getLength(); j++) {
                Node itemChild = itemChildNodes.item(j);
                String itemChildNodeName = itemChild.getNodeName();
                if (itemChildNodeName.equals("title")) {
                    itemTitle = itemChild.getTextContent();
                }

                if (itemChildNodeName.equals("enclosure")) {
                    NamedNodeMap attributes = itemChild.getAttributes();
                    torrent = attributes.getNamedItem("url").getNodeValue();
                    length = Optional.of(attributes)
                            .map(it -> it.getNamedItem("length"))
                            .map(Node::getNodeValue)
                            .filter(NumberUtil::isLong)
                            .orElse("1");

                    if (ReUtil.contains(StringEnum.MAGNET_REG, torrent)) {
                        infoHash = ReUtil.get(StringEnum.MAGNET_REG, torrent, 1);
                    }
                    if (ReUtil.contains(StringEnum.ED2K_REG, torrent)) {
                        infoHash = ReUtil.get(StringEnum.ED2K_REG, torrent, 3);
                    }
                }

                if ("guid".equals(itemChildNodeName)) {
                    if (ReUtil.isMatch("^([a-z]|[0-9])+$", itemChild.getTextContent())) {
                        infoHash = itemChild.getTextContent();
                    }
                }

                if ("nyaa:infoHash".equals(itemChildNodeName)) {
                    infoHash = itemChild.getTextContent();
                }
                if (itemChildNodeName.equals("nyaa:size")) {
                    formatSize = itemChild.getTextContent();
                }

                if (itemChildNodeName.equals("pubDate")) {
                    try {
                        pubDate = DateUtil.parse(itemChild.getTextContent());
                    } catch (Exception ignored) {
                    }
                }

                if (itemChildNodeName.equals("torrent")) {
                    try {
                        Element infoHashEl = XmlUtil.getElement((Element) itemChild, "infohash");
                        if (Objects.nonNull(infoHashEl)) {
                            infoHash = infoHashEl.getTextContent();
                        }

                        Element pubDateEl = XmlUtil.getElement((Element) itemChild, "pubDate");
                        if (Objects.nonNull(pubDateEl) && Objects.isNull(pubDate)) {
                            String pubDateStr = pubDateEl.getTextContent();
                            pubDate = DateUtil.parse(pubDateStr);
                        }

                        Element contentLength = XmlUtil.getElement((Element) itemChild, "contentLength");
                        if (Objects.nonNull(contentLength) && StrUtil.isBlank(length)) {
                            length = contentLength.getTextContent();
                        }

                        Element magneturi = XmlUtil.getElement((Element) itemChild, "magneturi");
                        if (Objects.nonNull(magneturi) && StrUtil.isBlank(torrent)) {
                            torrent = magneturi.getTextContent();
                        }
                    } catch (Exception ignored) {
                    }
                }

                if (itemChildNodeName.equals("link")) {
                    String link = itemChild.getTextContent();
                    if (link.endsWith(".torrent")) {
                        torrent = link;
                    }
                }

            }

            if (StrUtil.isBlank(torrent)) {
                continue;
            }

            if (StrUtil.isBlank(infoHash)) {
                infoHash = FileUtil.mainName(torrent);
            }

            infoHash = infoHash.toLowerCase();
            infoHash = URLUtil.decode(infoHash);

            try {
                length = StrUtil.nullToDefault(length, "0");
                if (formatSize.equals("0MiB")) {
                    formatSize = FileUtils.formatSize(Long.parseLong(length), true);
                }
            } catch (Exception e) {
                log.warn(e.getMessage());
            }

            Item addNewItem = new Item();

            addNewItem
                    .setSubgroup(subgroupName)
                    .setEpisode(1.0)
                    .setTitle(itemTitle)
                    .setReName(itemTitle)
                    .setTorrent(torrent)
                    .setInfoHash(infoHash)
                    .setFormatSize(formatSize)
                    .setPubDate(pubDate);

            MatchRuleUtil.Evaluation match = MatchRuleUtil.evaluate(ani, addNewItem.getTitle(), subgroupName);
            if (!match.accepted()) {
                continue;
            }
            addNewItem.setSubgroup(match.subgroup());
            // 集数要等重命名解析后才可知，这里先记录目标字幕组。
            if (match.reverseMatched()) {
                reverseTargets.put(addNewItem, match.subgroup());
            }
            if (!PluginHooks.accepts(ani, addNewItem)) {
                continue;
            }
            // 改写发生在过滤之后、重命名之前，改后的字幕组会进入标签与保存路径。
            PluginHooks.transform(ani, addNewItem);
            items.add(addNewItem);
        }

        items = items.stream()
                .filter(item -> {
                    try {
                        return RenameUtil.rename(ani, item);
                    } catch (Exception e) {
                        log.error("解析rss视频集次出现问题");
                        log.error(e.getMessage(), e);
                    }
                    return false;
                }).toList();
        // 集数在重命名阶段才解析，命中结果此时才能按集数记录。
        for (Map.Entry<Item, String> entry : reverseTargets.entrySet()) {
            assignmentChanged |= MatchRuleUtil.rememberAssignment(ani, entry.getKey(), entry.getValue());
        }
        if (assignmentChanged) {
            // 反匹配路由记录随订阅持久化，刷新结束后仍能保留已命中的字幕组。
            AniUtil.sync();
        }
        return CollUtil.distinct(items, item -> item.getEpisode().toString(), true);
    }

    /**
     * 获取 RSS 内容。请求、条件缓存与退避都在 {@link RssFetcher}。
     *
     * @param url RSS链接
     * @return XML
     */
    public static String getRss(String url) {
        return RssFetcher.get(url);
    }

    public static List<Integer> omitList(Ani ani, List<Item> items) {
        ArrayList<Integer> list = new ArrayList<>();
        Boolean omit = CONFIG.getOmit();
        if (!omit) {
            return list;
        }
        if (items.isEmpty()) {
            return list;
        }

        if (!ani.getOmit()) {
            return list;
        }

        Boolean ova = ani.getOva();
        if (ova) {
            return list;
        }

        Set<Integer> episodes = new HashSet<>();
        int max = Integer.MIN_VALUE;
        int min = Integer.MAX_VALUE;
        for (Item item : items) {
            int episode = item.getEpisode().intValue();
            episodes.add(episode);
            max = Math.max(max, episode);
            min = Math.min(min, episode);
        }
        if (max == min) {
            return list;
        }

        for (int ep = min; ep <= max; ep++) {
            if (episodes.contains(ep)) {
                // 包含该集
                continue;
            }
            if (50 < list.size()) {
                // 防止list过多
                return list;
            }
            list.add(ep);
        }
        return list;
    }

    /**
     * 检测是否缺集
     *
     * @param ani   订阅
     * @param items 资源列表
     */
    public static void omit(Ani ani, List<Item> items) {
        List<Integer> list = omitList(ani, items);

        if (list.isEmpty()) {
            return;
        }

        // 缺少集数大于10个时可能是误判。因此不进行通知
        if (list.size() > 10) {
            return;
        }

        Integer season = ani.getSeason();
        String id = ani.getId();

        ArrayList<String> sList = new ArrayList<>();

        for (Integer ep : list) {
            String episodeLabel = StrFormatter.format("S{}E{}", String.format("%02d", season), String.format("%02d", ep));
            String s = NotificationStatusUtil.text(NotificationStatusEnum.OMIT, ani, episodeLabel, "");
            String key = StrFormatter.format("omit:{}:ep-{}", id, ep);
            if (CacheUtils.containsKey(key)) {
                // 一天内已经提醒过了
                continue;
            }
            log.info(s);
            // 缓存一天 不重复发送
            CacheUtils.put(key, s, TimeUnit.DAYS.toMillis(1));
            sList.add(s);
        }

        if (sList.isEmpty()) {
            return;
        }

        NotificationUtil.send(CONFIG, ani, CollUtil.join(sList, "\n"), NotificationStatusEnum.OMIT);
    }

    public static int currentEpisodeNumber(Ani ani, List<Item> items) {
        Boolean standbyRss = CONFIG.getStandbyRss();
        Boolean coexist = CONFIG.getCoexist();
        if (standbyRss && coexist) {
            // 开启多字幕组共存模式则只计算主rss集数
            items = items.stream()
                    .filter(Item::getMaster)
                    .toList();
        }

        // 过滤掉x.5集
        items = items
                .stream()
                .filter(it -> it.getEpisode() == it.getEpisode().intValue())
                .toList();

        if (items.isEmpty()) {
            return 0;
        }

        // 导入或旧配置的订阅可能没有该字段，缺省按“下载全部”处理。
        Boolean downloadNew = Boolean.TRUE.equals(ani.getDownloadNew());
        if (downloadNew) {
            return items
                    .stream()
                    .mapToInt(item -> item.getEpisode().intValue())
                    .max()
                    .orElse(0);
        }
        return items.size();
    }

    public static String getSubgroup(List<Item> items) {
        String reg = "^\\[(.+?)]";
        for (Item item : items) {
            String title = item.getTitle();
            if (!ReUtil.contains(reg, title)) {
                title = FileUtil.getName(title);
            }
            if (ReUtil.contains(reg, title)) {
                return ReUtil.get(reg, title, 1);
            }
        }
        return "未知字幕组";
    }

    /**
     * 判断是否为 x.5 集
     *
     * @param item 集数
     * @return 判断结果
     */
    public static Boolean is5(Item item) {
        if (Objects.isNull(item)) {
            return false;
        }
        return is5(item.getEpisode());
    }

    /**
     * 判断是否为 x.5 集
     *
     * @param episode 集数
     * @return 判断结果
     */
    public static Boolean is5(Double episode) {
        if (Objects.isNull(episode)) {
            return false;
        }
        return episode.intValue() != episode;
    }

}
