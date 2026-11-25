package com.middragonfly.sablelimits.config;

import com.middragonfly.sablelimits.SableLimits;
import com.middragonfly.sablelimits.SableLimitsConfig;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {

    private static final Object LOCK = new Object();
    private static volatile SableLimitsConfig config = SableLimitsConfig.defaults();

    private ConfigManager() {
    }

    public static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve(SableLimits.MODID + ".json");
    }

    public static SableLimitsConfig get() {
        return config;
    }

    /**
     * Loads the config from disk. If the file does not exist, a commented default file is generated.
     *
     * @return the newly loaded config
     */
    public static SableLimitsConfig reload() throws IOException {
        synchronized (LOCK) {
            final Path path = configPath();
            final boolean existed = Files.exists(path);
            try {
                if (SableLimitsConfig.ensureCurrentShape(path)) {
                    SableLimits.LOGGER.info(existed
                            ? "Конфиг обновлён до актуальной структуры: {}"
                            : "Создан конфиг {}", path);
                }
            } catch (final IOException e) {
                // A read-only config must not prevent the server from starting; loading below
                // will surface the real parse error, if any.
                SableLimits.LOGGER.warn("Не удалось обновить структуру конфига: {}", e.getMessage());
            }

            final SableLimitsConfig loaded = SableLimitsConfig.load(path);
            for (final String warning : loaded.warnings) {
                SableLimits.LOGGER.warn("[config] {}", warning);
            }
            config = loaded;
            SableLimits.LOGGER.info("Конфиг загружен: enabled={}, правил Sable={}, правил Create={}, defaultMaxBlocks={}",
                    loaded.enabled, loaded.rules.sableRules.size(), loaded.rules.createRules.size(), loaded.defaultMaxBlocks);
            return loaded;
        }
    }

    public static void loadOnStartup() {
        try {
            reload();
        } catch (final Exception e) {
            SableLimits.LOGGER.error("Не удалось загрузить конфиг, используются значения по умолчанию", e);
        }
    }
}
