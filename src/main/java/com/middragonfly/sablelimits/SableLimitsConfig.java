package com.middragonfly.sablelimits;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.middragonfly.sablelimits.limits.RuleSet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable representation of config/sablelimits.json plus the compiled rule set.
 */
public final class SableLimitsConfig {

    public static final int CURRENT_VERSION = 1;

    public final boolean enabled;
    public final int cacheExpirationDays;
    public final int cleanupIntervalHours;
    public final int defaultMaxBlocks;
    public final int assemblyActorRadius;
    public final int messageCooldownTicks;
    public final boolean assemblyUsesPlayerLimit;
    public final Map<String, Integer> limits;
    public final Map<String, List<String>> groups;
    public final List<String> forbiddenBlocks;
    public final Messages messages;
    public final RuleSet rules;
    public final List<String> warnings;

    private SableLimitsConfig(final boolean enabled,
                              final int cacheExpirationDays,
                              final int cleanupIntervalHours,
                              final int defaultMaxBlocks,
                              final int assemblyActorRadius,
                              final int messageCooldownTicks,
                              final boolean assemblyUsesPlayerLimit,
                              final Map<String, Integer> limits,
                              final Map<String, List<String>> groups,
                              final List<String> forbiddenBlocks,
                              final Messages messages,
                              final List<String> warnings) {
        this.enabled = enabled;
        this.cacheExpirationDays = cacheExpirationDays;
        this.cleanupIntervalHours = cleanupIntervalHours;
        this.defaultMaxBlocks = defaultMaxBlocks;
        this.assemblyActorRadius = assemblyActorRadius;
        this.messageCooldownTicks = messageCooldownTicks;
        this.assemblyUsesPlayerLimit = assemblyUsesPlayerLimit;
        this.limits = Map.copyOf(limits);
        this.groups = Map.copyOf(groups);
        this.forbiddenBlocks = List.copyOf(forbiddenBlocks);
        this.messages = messages;
        this.rules = RuleSet.compile(this.limits, this.groups, this.forbiddenBlocks, warnings);
        this.warnings = List.copyOf(warnings);
    }

    public static SableLimitsConfig defaults() {
        return new SableLimitsConfig(
                true,
                14,
                24,
                3500,
                32,
                20,
                false,
                Map.of(),
                Map.of(),
                List.of(),
                new Messages(),
                new ArrayList<>()
        );
    }

    public static SableLimitsConfig load(final Path path) throws IOException {
        final String raw = Files.readString(path, StandardCharsets.UTF_8);
        final JsonElement parsed;
        try {
            parsed = JsonParser.parseString(raw);
        } catch (final RuntimeException e) {
            throw new IOException("Некорректный JSON в " + path.getFileName() + ": " + e.getMessage(), e);
        }
        if (!parsed.isJsonObject()) {
            throw new IOException(path.getFileName() + ": корень файла должен быть JSON-объектом");
        }

        final JsonObject root = parsed.getAsJsonObject();
        final List<String> warnings = new ArrayList<>();

        final boolean enabled = getBoolean(root, "enabled", true, warnings);
        final int cacheExpirationDays = getInt(root, "cacheExpirationDays", 14, 0, Integer.MAX_VALUE, warnings);
        final int cleanupIntervalHours = getInt(root, "cleanupIntervalHours", 24, 0, Integer.MAX_VALUE, warnings);
        final int defaultMaxBlocks = getInt(root, "defaultMaxBlocks", 3500, 0, Integer.MAX_VALUE, warnings);
        final int assemblyActorRadius = getInt(root, "assemblyActorRadius", 32, 0, 128, warnings);
        final int messageCooldownTicks = getInt(root, "messageCooldownTicks", 20, 0, 20 * 60, warnings);
        final boolean assemblyUsesPlayerLimit = getBoolean(root, "assemblyUsesPlayerLimit", false, warnings);
        final List<String> forbiddenBlocks = getStringList(root, "forbiddenBlocks", warnings);

        final Map<String, Integer> limits = new LinkedHashMap<>();
        final JsonObject limitsObject = getObject(root, "limits", warnings);
        if (limitsObject != null) {
            for (final Map.Entry<String, JsonElement> entry : limitsObject.entrySet()) {
                final String key = entry.getKey().trim();
                if (key.isEmpty()) {
                    continue;
                }
                final JsonElement value = entry.getValue();
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                    warnings.add("limits[" + key + "]: значение должно быть числом, запись пропущена");
                    continue;
                }
                int limit;
                try {
                    limit = value.getAsInt();
                } catch (final RuntimeException e) {
                    warnings.add("limits[" + key + "]: некорректное число, запись пропущена");
                    continue;
                }
                limits.put(key, limit);
            }
        }

        final Map<String, List<String>> groups = new LinkedHashMap<>();
        final JsonObject groupsObject = getObject(root, "groups", warnings);
        if (groupsObject != null) {
            for (final Map.Entry<String, JsonElement> entry : groupsObject.entrySet()) {
                final String name = entry.getKey().trim();
                if (name.isEmpty()) {
                    continue;
                }
                if (!entry.getValue().isJsonArray()) {
                    warnings.add("groups[" + name + "]: ожидается массив строк, группа пропущена");
                    continue;
                }
                final List<String> members = new ArrayList<>();
                for (final JsonElement member : entry.getValue().getAsJsonArray()) {
                    if (member.isJsonPrimitive() && member.getAsJsonPrimitive().isString()) {
                        final String token = member.getAsString().trim();
                        if (!token.isEmpty()) {
                            members.add(token);
                        }
                    } else {
                        warnings.add("groups[" + name + "]: элементы должны быть строками");
                    }
                }
                groups.put(name, members);
            }
        }

        final Messages messages = Messages.parse(root, warnings);

        return new SableLimitsConfig(enabled, cacheExpirationDays, cleanupIntervalHours,
                defaultMaxBlocks, assemblyActorRadius, messageCooldownTicks, assemblyUsesPlayerLimit,
                limits, groups, forbiddenBlocks, messages, warnings);
    }

    public static void writeDefault(final Path path) throws IOException {
        final Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        final JsonObject root = new JsonObject();
        final JsonArray readme = new JsonArray();
        readme.add("sablelimits — серверные лимиты блоков для Sable / Create: Aeronautics и обычных Create-контрапшенов.");
        readme.add("Ключи в limits:");
        readme.add("  create:deployer          — лимит на структурах Sable (без префикса)");
        readme.add("  C create:deployer        — лимит только для обычных Create Contraptions");
        readme.add("  * create:deployer        — лимит и для Sable, и для Create");
        readme.add("  #create:seats            — лимит по тегу блока");
        readme.add("  group:engines            — лимит по группе из секции groups");
        readme.add("Значение 0 или меньше отключает конкретное правило.");
        readme.add("forbiddenBlocks — блоки, с которыми сборка отменяется: ID, #тег или group:имя.");
        readme.add("  в forbiddenBlocks те же префиксы: без префикса — Sable, C — Create, * — оба типа.");
        readme.add("defaultMaxBlocks — общий лимит блоков на Sable-структуру (0 = без лимита).");
        readme.add("cacheExpirationDays — сколько дней хранить запись кэша (0 = никогда не удалять).");
        readme.add("assemblyUsesPlayerLimit — при сборке учитывать личный limit.size ближайшего игрока (иначе только defaultMaxBlocks).");
        readme.add("Права: sablelimits.bypass.blocks, sablelimits.bypass.size, мета sablelimits.limit.size.");
        root.add("_readme", readme);

        root.addProperty("enabled", true);
        root.addProperty("cacheExpirationDays", 14);
        root.addProperty("cleanupIntervalHours", 24);
        root.addProperty("defaultMaxBlocks", 3500);
        root.addProperty("assemblyActorRadius", 32);
        root.addProperty("messageCooldownTicks", 20);
        root.addProperty("assemblyUsesPlayerLimit", false);

        final JsonObject limits = new JsonObject();
        limits.addProperty("create:deployer", 16);
        limits.addProperty("C create:deployer", 32);
        limits.addProperty("#create:seats", 16);
        limits.addProperty("* create:mechanical_drill", 64);
        limits.addProperty("group:engines", 16);
        root.add("limits", limits);

        final JsonObject groups = new JsonObject();
        final JsonArray engines = new JsonArray();
        for (final String color : new String[]{
                "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"}) {
            engines.add("simulated:" + color + "_portable_engine");
        }
        groups.add("engines", engines);
        root.add("groups", groups);

        root.add("forbiddenBlocks", new JsonArray());

        final JsonObject messages = new JsonObject();
        messages.addProperty("blockLimit", "&cЛимит: &f%block% &7(%current%/%limit%)");
        messages.addProperty("sizeLimit", "&cЛимит размера корабля: &f%current%/%limit% &7блоков");
        messages.addProperty("assemblyBlockLimit", "&cСборка отменена: &f%block% &7(%current%/%limit%)");
        messages.addProperty("assemblySizeLimit", "&cСборка отменена: &f%current%&7/&f%limit% &7блоков (максимум)");
        messages.addProperty("forbiddenBlock", "&cБлок запрещён на конструкции: &f%block%");
        messages.addProperty("reloaded", "&aSableLimits: конфиг перезагружен, правил: &f%rules%&a.");
        root.add("messages", messages);

        final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        Files.writeString(path, gson.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static boolean getBoolean(final JsonObject root, final String key, final boolean fallback, final List<String> warnings) {
        final JsonElement element = root.get(key);
        if (element == null) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            warnings.add(key + ": ожидается true/false, используется " + fallback);
            return fallback;
        }
        return element.getAsBoolean();
    }

    private static int getInt(final JsonObject root, final String key, final int fallback,
                              final int min, final int max, final List<String> warnings) {
        final JsonElement element = root.get(key);
        if (element == null) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            warnings.add(key + ": ожидается число, используется " + fallback);
            return fallback;
        }
        final int value = element.getAsInt();
        if (value < min || value > max) {
            final int clamped = Math.max(min, Math.min(max, value));
            warnings.add(key + ": значение " + value + " вне диапазона " + min + ".." + max + ", используется " + clamped);
            return clamped;
        }
        return value;
    }

    private static JsonObject getObject(final JsonObject root, final String key, final List<String> warnings) {
        final JsonElement element = root.get(key);
        if (element == null) {
            return null;
        }
        if (!element.isJsonObject()) {
            warnings.add(key + ": ожидается объект, секция пропущена");
            return null;
        }
        return element.getAsJsonObject();
    }

    private static List<String> getStringList(final JsonObject root, final String key,
                                              final List<String> warnings) {
        final JsonElement element = root.get(key);
        if (element == null) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            warnings.add(key + ": ожидается массив строк, секция пропущена");
            return List.of();
        }

        final List<String> values = new ArrayList<>();
        for (final JsonElement value : element.getAsJsonArray()) {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                final String token = value.getAsString().trim();
                if (!token.isEmpty()) {
                    values.add(token);
                }
            } else {
                warnings.add(key + ": элементы массива должны быть строками");
            }
        }
        return values;
    }

    public static final class Messages {
        public String blockLimit = "&cЛимит: &f%block% &7(%current%/%limit%)";
        public String sizeLimit = "&cЛимит размера корабля: &f%current%/%limit% &7блоков";
        public String assemblyBlockLimit = "&cСборка отменена: &f%block% &7(%current%/%limit%)";
        public String assemblySizeLimit = "&cСборка отменена: &f%current%&7/&f%limit% &7блоков (максимум)";
        public String forbiddenBlock = "&cБлок запрещён на конструкции: &f%block%";
        public String reloaded = "&aSableLimits: конфиг перезагружен, правил: &f%rules%&a.";

        static Messages parse(final JsonObject root, final List<String> warnings) {
            final Messages messages = new Messages();
            final JsonElement element = root.get("messages");
            if (element == null) {
                return messages;
            }
            if (!element.isJsonObject()) {
                warnings.add("messages: ожидается объект, используются значения по умолчанию");
                return messages;
            }
            final JsonObject object = element.getAsJsonObject();
            messages.blockLimit = getString(object, "blockLimit", messages.blockLimit, warnings);
            messages.sizeLimit = getString(object, "sizeLimit", messages.sizeLimit, warnings);
            messages.assemblyBlockLimit = getString(object, "assemblyBlockLimit", messages.assemblyBlockLimit, warnings);
            messages.assemblySizeLimit = getString(object, "assemblySizeLimit", messages.assemblySizeLimit, warnings);
            messages.forbiddenBlock = getString(object, "forbiddenBlock", messages.forbiddenBlock, warnings);
            messages.reloaded = getString(object, "reloaded", messages.reloaded, warnings);
            return messages;
        }

        private static String getString(final JsonObject object, final String key,
                                        final String fallback, final List<String> warnings) {
            final JsonElement element = object.get(key);
            if (element == null) {
                return fallback;
            }
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                warnings.add("messages." + key + ": ожидается строка, используется значение по умолчанию");
                return fallback;
            }
            return element.getAsString();
        }
    }
}
