/*
 * Purpose:      Active wear rules. Holds the rules parsed by WearRuleReloader and the
 *               block → rule map built from them once block tags are bound (DESIGN.md §6).
 *               Precedence per docs/RULES.md.
 * Dependencies: Minecraft block registry and tags; fastutil (bundled).
 */
package us.someguy.trailwright.wear;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import us.someguy.trailwright.TrailWright;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class WearRules {
    private WearRules() {}

    /** Where a rule came from; a later constant wins. */
    public enum Source { DATA_PACK, CONFIG_FOLDER }

    /**
     * Precedence key, compared field by field (docs/RULES.md "Which rule applies"):
     * source, then priority, then ID over tag, then pack load order, then file order.
     */
    public record Rank(Source source, int priority, boolean byId, int packIndex, int fileIndex)
            implements Comparable<Rank> {
        @Override
        public int compareTo(Rank o) {
            int c = source.compareTo(o.source);
            if (c == 0) c = Integer.compare(priority, o.priority);
            if (c == 0) c = Boolean.compare(byId, o.byId);
            if (c == 0) c = Integer.compare(packIndex, o.packIndex);
            if (c == 0) c = Integer.compare(fileIndex, o.fileIndex);
            return c;
        }
    }

    /** A parsed rule plus where it came from. */
    public record Loaded(WearRule rule, ResourceLocation file, Source source, int packIndex, int fileIndex) {}

    /** A branch with its target block resolved. */
    public record ActiveBranch(WearRule.Condition when, BlockState to) {}

    /** The rule chosen for one block; branches are an array so iteration does not allocate. */
    public record Active(WearRule rule, ActiveBranch[] branches) {}

    private static volatile List<Loaded> loaded = List.of();
    private static volatile Reference2ObjectOpenHashMap<Block, Active> byBlock = new Reference2ObjectOpenHashMap<>();

    /** Hot path: one identity-map read. Null when the block has no usable rule. */
    public static Active forState(BlockState state) {
        return byBlock.get(state.getBlock());
    }

    /** True when no block has a usable rule; callers may skip all wear work. */
    public static boolean isEmpty() {
        return byBlock.isEmpty();
    }

    /** The parsed rules in effect at the last reload. GameTests use it to save and restore. */
    public static List<Loaded> loaded() {
        return loaded;
    }

    /** Called by the reloader (and GameTests). Takes effect at the next {@link #rebuild()}. */
    public static void setLoaded(List<Loaded> rules) {
        loaded = List.copyOf(rules);
    }

    /**
     * Resolves block IDs and tags against the registry and keeps the winning rule per block.
     * Must run after tags are bound: on SERVER_STARTED and END_DATA_PACK_RELOAD.
     */
    public static void rebuild() {
        Reference2ObjectOpenHashMap<Block, Active> map = new Reference2ObjectOpenHashMap<>();
        Reference2ObjectOpenHashMap<Block, Rank> ranks = new Reference2ObjectOpenHashMap<>();
        for (Loaded l : loaded) {
            Active active = new Active(l.rule(), resolveBranches(l));
            for (String ref : l.rule().blocks()) {
                boolean isTag = ref.startsWith("#");
                ResourceLocation id = ResourceLocation.tryParse(isTag ? ref.substring(1) : ref);
                if (id == null) {
                    TrailWright.LOG.warn("Wear rule {}: invalid block reference '{}'", l.file(), ref);
                    continue;
                }
                Rank rank = new Rank(l.source(), l.rule().priority(), !isTag, l.packIndex(), l.fileIndex());
                for (Block block : resolveBlocks(id, isTag, l.file())) {
                    Rank current = ranks.get(block);
                    if (current == null || rank.compareTo(current) > 0) {
                        ranks.put(block, rank);
                        map.put(block, active);
                    }
                }
            }
        }
        // A winner with no usable branch (empty `next`, or every target unregistered) means
        // "this block never changes". It had to win the ranking so it can disable a lower rule,
        // but it is then dropped so the block is treated as having no rule: no wear recorded,
        // pruned like any rule-less block, and shown as "no rule" on F3.
        map.values().removeIf(a -> a.branches().length == 0);
        byBlock = map;
        if (map.isEmpty()) {
            TrailWright.LOG.warn("No wear rules are active, so TrailWright will not wear any ground. Rules load "
                    + "from data packs (data/<namespace>/wear_rules/) and config/trailwright/rules/; check "
                    + "that TrailWright's built-in data pack is enabled (/datapack list).");
        } else {
            TrailWright.LOG.info("Wear rules active for {} blocks from {} rule files", map.size(), loaded.size());
        }
    }

    private static List<Block> resolveBlocks(ResourceLocation id, boolean isTag, ResourceLocation file) {
        List<Block> out = new ArrayList<>();
        if (isTag) {
            Optional<HolderSet.Named<Block>> tag = BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, id));
            if (tag.isEmpty()) {
                // Expected for compat rules when the mod is absent (DESIGN.md §8).
                TrailWright.LOG.debug("Wear rule {}: block tag #{} not present; skipped", file, id);
                return out;
            }
            for (Holder<Block> h : tag.get()) out.add(h.value());
        } else {
            Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
            if (block.isEmpty()) {
                TrailWright.LOG.debug("Wear rule {}: block {} not registered; skipped", file, id);
                return out;
            }
            out.add(block.get());
        }
        return out;
    }

    private static ActiveBranch[] resolveBranches(Loaded l) {
        List<ActiveBranch> out = new ArrayList<>();
        for (WearRule.Branch b : l.rule().next()) {
            Optional<Block> to = BuiltInRegistries.BLOCK.getOptional(b.to());
            if (to.isEmpty()) {
                TrailWright.LOG.debug("Wear rule {}: target {} not registered; branch skipped", l.file(), b.to());
                continue;
            }
            out.add(new ActiveBranch(b.when(), to.get().defaultBlockState()));
        }
        return out.toArray(new ActiveBranch[0]);
    }
}
