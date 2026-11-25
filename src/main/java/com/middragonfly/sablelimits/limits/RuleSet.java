package com.middragonfly.sablelimits.limits;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parsed list of limit rules. A rule is either a single block id, a block tag or a group of ids/tags.
 * <p>
 * Rule keys use an optional scope prefix: no prefix means Sable structures, {@code C } means regular
 * Create contraptions, {@code * } means both.
 */
public final class RuleSet {

    public enum Scope {
        SABLE,
        CREATE,
        BOTH
    }

    public final List<Rule> sableRules;
    public final List<Rule> createRules;

    private RuleSet(final List<Rule> sableRules, final List<Rule> createRules) {
        this.sableRules = List.copyOf(sableRules);
        this.createRules = List.copyOf(createRules);
    }

    public static RuleSet empty() {
        return new RuleSet(List.of(), List.of());
    }

    public static RuleSet compile(final Map<String, Integer> limits,
                                  final Map<String, List<String>> groups,
                                  final List<String> warnings) {
        final List<Rule> sable = new ArrayList<>();
        final List<Rule> create = new ArrayList<>();
        final Set<String> warnedTokens = new HashSet<>();

        for (final Map.Entry<String, Integer> entry : limits.entrySet()) {
            final String rawKey = entry.getKey() == null ? "" : entry.getKey().trim();
            if (rawKey.isEmpty()) {
                continue;
            }

            Scope scope = Scope.SABLE;
            String token = rawKey;
            if (token.startsWith("*")) {
                scope = Scope.BOTH;
                token = token.substring(1).trim();
            } else if (token.length() > 1
                    && (token.charAt(0) == 'C' || token.charAt(0) == 'c')
                    && Character.isWhitespace(token.charAt(1))) {
                scope = Scope.CREATE;
                token = token.substring(1).trim();
            }

            if (token.isEmpty()) {
                warnings.add("limits[" + rawKey + "]: пустое правило после префикса");
                continue;
            }

            final int limit = entry.getValue() == null ? 0 : entry.getValue();
            if (limit <= 0) {
                warnings.add("limits[" + rawKey + "]: лимит " + limit + " отключает правило");
                continue;
            }

            final Set<Block> blocks = resolveToken(token, groups, warnings, warnedTokens);
            if (blocks.isEmpty()) {
                continue;
            }

            final Rule rule = new Rule(scope, token, limit, blocks);
            if (scope == Scope.SABLE || scope == Scope.BOTH) {
                sable.add(rule);
            }
            if (scope == Scope.CREATE || scope == Scope.BOTH) {
                create.add(rule);
            }
        }

        return new RuleSet(sable, create);
    }

    private static Set<Block> resolveToken(final String token,
                                           final Map<String, List<String>> groups,
                                           final List<String> warnings,
                                           final Set<String> warnedTokens) {
        final Set<Block> result = new ObjectOpenHashSet<>();
        if (token.startsWith("#")) {
            final String id = token.substring(1).trim();
            addTag(result, id, token, warnings, warnedTokens);
            return result;
        }

        if (token.startsWith("group:")) {
            final String name = token.substring("group:".length()).trim();
            addGroup(result, name, groups, new HashSet<>(), warnings, warnedTokens);
            return result;
        }

        addBlock(result, token, warnings, warnedTokens);
        return result;
    }

    private static void addGroup(final Set<Block> result,
                                 final String name,
                                 final Map<String, List<String>> groups,
                                 final Set<String> stack,
                                 final List<String> warnings,
                                 final Set<String> warnedTokens) {
        if (!stack.add(name)) {
            warnings.add("groups[" + name + "]: циклическая ссылка на группу, ветка пропущена");
            return;
        }

        final List<String> members = groups.get(name);
        if (members == null) {
            warnings.add("group:" + name + ": группа не описана в секции groups");
            stack.remove(name);
            return;
        }

        for (final String member : members) {
            final String token = member.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (token.startsWith("group:")) {
                addGroup(result, token.substring("group:".length()).trim(), groups, stack, warnings, warnedTokens);
            } else if (token.startsWith("#")) {
                addTag(result, token.substring(1).trim(), token, warnings, warnedTokens);
            } else {
                addBlock(result, token, warnings, warnedTokens);
            }
        }

        stack.remove(name);
    }

    private static void addTag(final Set<Block> result,
                               final String id,
                               final String configToken,
                               final List<String> warnings,
                               final Set<String> warnedTokens) {
        final ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            if (warnedTokens.add(configToken)) {
                warnings.add(configToken + ": некорректный id тега");
            }
            return;
        }

        final TagKey<Block> tag = TagKey.create(Registries.BLOCK, location);
        final var named = BuiltInRegistries.BLOCK.getTag(tag);
        if (named.isEmpty()) {
            if (warnedTokens.add(configToken)) {
                warnings.add(configToken + ": тег блока не найден");
            }
            return;
        }

        for (final Holder<Block> holder : named.get()) {
            result.add(holder.value());
        }
    }

    private static void addBlock(final Set<Block> result,
                                 final String id,
                                 final List<String> warnings,
                                 final Set<String> warnedTokens) {
        final ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            if (warnedTokens.add(id)) {
                warnings.add(id + ": некорректный id блока");
            }
            return;
        }
        if (!BuiltInRegistries.BLOCK.containsKey(location)) {
            if (warnedTokens.add(id)) {
                warnings.add(id + ": блок не найден");
            }
            return;
        }
        result.add(BuiltInRegistries.BLOCK.get(location));
    }

    public static final class Rule {
        public final Scope scope;
        public final String display;
        public final int limit;
        public final Set<Block> blocks;

        private Rule(final Scope scope, final String display, final int limit, final Set<Block> blocks) {
            this.scope = scope;
            this.display = display;
            this.limit = limit;
            this.blocks = blocks;
        }

        public boolean matches(final Block block) {
            return block != null && this.blocks.contains(block);
        }

        /**
         * Counts matching blocks in a per-block count map. The map is small (distinct blocks only),
         * so this stays O(distinct blocks) even for very large tag rules.
         */
        public int count(final Object2IntMap<Block> counts) {
            int total = 0;
            for (final Object2IntMap.Entry<Block> entry : counts.object2IntEntrySet()) {
                if (this.blocks.contains(entry.getKey())) {
                    total += entry.getIntValue();
                }
            }
            return total;
        }

        public int countAfter(final Object2IntMap<Block> counts,
                              final Block oldBlock,
                              final Block newBlock) {
            final int total = this.count(counts);
            if (oldBlock == newBlock) {
                return total;
            }

            int after = total;
            if (oldBlock != null && this.blocks.contains(oldBlock)) {
                after -= 1;
            }
            if (newBlock != null && this.blocks.contains(newBlock)) {
                after += 1;
            }
            return Math.max(0, after);
        }

        @Override
        public String toString() {
            return this.display + " (" + this.scope + " <= " + this.limit + ", " + this.blocks.size() + " бл.)";
        }
    }

    @Override
    public String toString() {
        return "RuleSet[sable=" + Arrays.toString(this.sableRules.toArray()) + ", create="
                + Arrays.toString(this.createRules.toArray()) + "]";
    }
}
