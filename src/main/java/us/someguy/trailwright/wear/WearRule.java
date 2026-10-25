/*
 * Purpose:      One parsed wear rule (docs/RULES.md): which blocks it matches, thresholds,
 *               and the ordered branches with their conditions. Includes the JSON codec.
 *               Block and tag IDs stay unresolved here; WearRules resolves them once tags
 *               are bound.
 * Dependencies: DataFixerUpper codecs (bundled), Minecraft registry/tag/level types.
 */
package us.someguy.trailwright.wear;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Set;

public record WearRule(List<String> blocks, int priority, float baseThreshold, float jitter,
                       float advanceChance, boolean mobsCanAdvance, List<Branch> next) {

    public record Branch(Condition when, ResourceLocation to) {
        static final Codec<Branch> CODEC = RecordCodecBuilder.create(i -> i.group(
                Condition.CODEC.fieldOf("when").forGetter(Branch::when),
                ResourceLocation.CODEC.fieldOf("to").forGetter(Branch::to)
        ).apply(i, Branch::new));
    }

    /** A branch condition. Evaluated only when a block advances, never per footfall. */
    public sealed interface Condition permits Always, NearWater, BiomeTag, Raining {
        String type();

        boolean test(ServerLevel level, BlockPos pos);

        Set<String> TYPES = Set.of("always", "near_water", "biome_tag", "raining");

        // Validating the key first gives "Unknown condition type" instead of a codec NPE.
        Codec<String> TYPE_CODEC = Codec.STRING.flatXmap(
                t -> TYPES.contains(t) ? DataResult.success(t)
                        : DataResult.error(() -> "Unknown condition type '" + t + "'; expected one of " + TYPES),
                DataResult::success);

        Codec<Condition> CODEC = TYPE_CODEC.dispatch("type", Condition::type, Condition::codecFor);

        private static Codec<? extends Condition> codecFor(String type) {
            return switch (type) {
                case "near_water" -> NearWater.CODEC;
                case "biome_tag" -> BiomeTag.CODEC;
                case "raining" -> Raining.CODEC;
                default -> Always.CODEC;
            };
        }
    }

    public record Always() implements Condition {
        // RecordCodecBuilder (not Codec.unit) so dispatch inlines it as a map with only "type".
        static final Codec<Always> CODEC = RecordCodecBuilder.create(i -> i.point(new Always()));
        public String type() { return "always"; }
        public boolean test(ServerLevel level, BlockPos pos) { return true; }
    }

    /** Water within {@code radius} horizontally, at the block's Y or one below (DESIGN.md §4.3). */
    public record NearWater(int radius) implements Condition {
        static final Codec<NearWater> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 4).optionalFieldOf("radius", 2).forGetter(NearWater::radius)
        ).apply(i, NearWater::new));

        public String type() { return "near_water"; }

        public boolean test(ServerLevel level, BlockPos pos) {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int dy = 0; dy >= -1; dy--) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                        // Never load a chunk to answer a condition.
                        if (level.isLoaded(m) && level.getFluidState(m).is(FluidTags.WATER)) return true;
                    }
                }
            }
            return false;
        }
    }

    /** Biome at the block is in {@code tag}. Accepts the tag with or without a leading '#'. */
    public record BiomeTag(TagKey<Biome> tag) implements Condition {
        static final Codec<BiomeTag> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.comapFlatMap(BiomeTag::parse, t -> "#" + t.location())
                        .fieldOf("tag").forGetter(BiomeTag::tag)
        ).apply(i, BiomeTag::new));

        private static DataResult<TagKey<Biome>> parse(String s) {
            ResourceLocation id = ResourceLocation.tryParse(s.startsWith("#") ? s.substring(1) : s);
            return id == null ? DataResult.error(() -> "Invalid biome tag '" + s + "'")
                    : DataResult.success(TagKey.create(Registries.BIOME, id));
        }

        public String type() { return "biome_tag"; }
        public boolean test(ServerLevel level, BlockPos pos) { return level.getBiome(pos).is(tag); }
    }

    public record Raining() implements Condition {
        static final Codec<Raining> CODEC = RecordCodecBuilder.create(i -> i.point(new Raining()));
        public String type() { return "raining"; }
        public boolean test(ServerLevel level, BlockPos pos) { return level.isRainingAt(pos.above()); }
    }

    /** {@code block}: a single ID or #tag, or a list of them. */
    private static final Codec<List<String>> BLOCKS_CODEC = Codec.either(Codec.STRING, Codec.STRING.listOf())
            .xmap(e -> e.map(List::of, l -> l),
                  l -> l.size() == 1 ? Either.left(l.get(0)) : Either.right(l));

    public static final Codec<WearRule> CODEC = RecordCodecBuilder.create(i -> i.group(
            BLOCKS_CODEC.fieldOf("block").forGetter(WearRule::blocks),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(WearRule::priority),
            Codec.floatRange(0.01f, 100000f).fieldOf("base_threshold").forGetter(WearRule::baseThreshold),
            Codec.floatRange(0f, 0.9f).optionalFieldOf("jitter", 0.35f).forGetter(WearRule::jitter),
            Codec.floatRange(0f, 1f).fieldOf("advance_chance").forGetter(WearRule::advanceChance),
            Codec.BOOL.optionalFieldOf("mobs_can_advance", false).forGetter(WearRule::mobsCanAdvance),
            Branch.CODEC.listOf().fieldOf("next").forGetter(WearRule::next)
    ).apply(i, WearRule::new));
}
