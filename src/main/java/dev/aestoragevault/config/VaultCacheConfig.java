package dev.aestoragevault.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import dev.aestoragevault.VaultMod;
import dev.aestoragevault.core.CachePool;

/** Server-owned startup settings. Remote client files do not control server inventories. */
public final class VaultCacheConfig {

    public static final String CATEGORY = "storagecache";
    public static final int DEFAULT_KEY_ENTRIES = 4096;
    private static int keyEntries = DEFAULT_KEY_ENTRIES, prototypeEntries = 16384, outputEntries = 256;
    private static long keyBytes = 16L << 20, prototypeBytes = 64L << 20, maxEntryBytes = 1L << 20;
    private static long outputBytes = 8L << 20;
    private static CachePool pool = new CachePool(256L << 20);

    private VaultCacheConfig() {}

    public static void load(File file) {
        Configuration config = new Configuration(file);
        config.load();
        config.addCustomCategoryComment(
            CATEGORY,
            "Server/integrated-server storage read caches. Restart to apply. / 服务端或单人存档读取缓存，重启生效。\n"
                + "Counts and byte budgets are per channel (items/fluids); globalMaxBytes covers all caches. / 条数与局部预算分别作用于物品、流体；全局预算覆盖所有聚合器。\n"
                + "Bytes are deterministic accounting weights, NOT a JVM heap limit. 0 disables that cache/budget; never truncates inventory. / 字节为缓存计费权重，并非JVM堆硬上限；0关闭对应缓存，绝不截断库存。");
        keyEntries = (int) read(
            config,
            "keyMaxEntries",
            DEFAULT_KEY_ENTRIES,
            1000000,
            "LRU encoded identity entries per channel. / 每通道身份键LRU条数，0关闭。");
        keyBytes = read(
            config,
            "keyMaxBytes",
            16L << 20,
            Long.MAX_VALUE,
            "Encoded-key budget per channel, bytes. Weight = 2*(canonical key + payload) + 128. / 每通道键缓存预算，字节。");
        prototypeEntries = (int) read(
            config,
            "prototypeMaxEntries",
            16384,
            1000000,
            "Decoded prototypes per channel. Full scans only admit entries with free room. / 每通道原型条数；扫描不驱逐已有热项。");
        prototypeBytes = read(
            config,
            "prototypeMaxBytes",
            64L << 20,
            Long.MAX_VALUE,
            "Prototype budget per channel, bytes. Weight = 2*(canonical key + payload) + 512. / 每通道原型预算，字节。");
        outputEntries = (int) read(
            config,
            "outputMaxEntries",
            256,
            1000000,
            "Output bus prototype LRU entries, both channels combined. / 每个输出总线的原型LRU条数，物品流体合计。");
        outputBytes = read(
            config,
            "outputMaxBytes",
            8L << 20,
            Long.MAX_VALUE,
            "Output bus prototype budget, bytes, both channels combined. Same weight as prototypes. / 每个输出总线两通道合计预算，字节。");
        maxEntryBytes = read(
            config,
            "maxEntryBytes",
            1L << 20,
            Long.MAX_VALUE,
            "Maximum accounted size admitted for one cached entry, bytes. Larger entries still work uncached. / 单条缓存最大计费字节；超限仍正常读写。");
        long global = read(
            config,
            "globalMaxBytes",
            256L << 20,
            Long.MAX_VALUE,
            "Shared budget across ALL loaded vault key/prototype/output caches and both channels, bytes. 0 disables all. / 所有聚合器两通道键/原型缓存的全局预算，0全部关闭。");
        pool.clear();
        pool = new CachePool(global);
        if (config.hasChanged()) config.save();
    }

    private static long read(Configuration config, String name, long fallback, long maximum, String comment) {
        Property property = config.get(CATEGORY, name, Long.toString(fallback), comment);
        long value;
        try {
            value = Long.parseLong(
                property.getString()
                    .trim());
            if (value < 0 || value > maximum) throw new NumberFormatException();
        } catch (NumberFormatException invalid) {
            value = fallback;
            VaultMod.LOG.warn("Invalid storagecache.{}; using {}", name, fallback);
        }
        property.set(Long.toString(value));
        return value;
    }

    public static <K, V> CachePool.Cache<K, V> keyCache() {
        return pool.create(keyEntries, keyBytes, maxEntryBytes);
    }

    public static <K, V> CachePool.Cache<K, V> prototypeCache() {
        return pool.create(prototypeEntries, prototypeBytes, maxEntryBytes);
    }

    public static <K, V> CachePool.Cache<K, V> outputCache() {
        return pool.create(outputEntries, outputBytes, maxEntryBytes);
    }

    public static long usedBytes() {
        return pool.bytes();
    }

    public static void clear() {
        pool.clear();
    }
}
