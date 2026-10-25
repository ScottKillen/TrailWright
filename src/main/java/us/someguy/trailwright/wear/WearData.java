/*
 * Purpose:      Per-dimension SavedData holding sparse wear values keyed by packed BlockPos,
 *               with lazy decay and periodic pruning. See DESIGN.md §4.5 and §5.
 * Dependencies: fastutil (bundled), Minecraft SavedData / CompoundTag / ServerLevel,
 *               TrailWrightConfig (decayPerDay, pruneIntervalTicks), WearRules.
 */
package us.someguy.trailwright.wear;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import us.someguy.trailwright.TrailWright;
import us.someguy.trailwright.config.TrailWrightConfig;

import java.util.function.LongPredicate;

public final class WearData extends SavedData {
    public static final String ID = "trailwright_wear";
    public static final long TICKS_PER_DAY = 24000L;

    // NBT keys. Entries are stored as three parallel arrays (no per-entry compound tags),
    // which keeps the file small and load allocation-light at tens of thousands of entries.
    private static final String KEY_POS = "pos", KEY_WEAR = "wear", KEY_TICK = "tick";

    /** Mutable so updating an existing entry on the hot path does not allocate. */
    static final class Entry {
        float wear;
        long lastTick;
        Entry(float wear, long lastTick) { this.wear = wear; this.lastTick = lastTick; }
    }

    private final Long2ObjectOpenHashMap<Entry> entries = new Long2ObjectOpenHashMap<>();
    private long lastPruneTick = Long.MIN_VALUE;   // transient: first check after load prunes

    /**
     * Cached on the level (WearDataHolder), so per-footfall and per-block-change callers skip
     * the saved-data storage's by-name lookup. The storage keeps the same instance for the
     * level's lifetime, so the cache cannot go stale.
     */
    public static WearData get(ServerLevel level) {
        WearDataHolder holder = (WearDataHolder) level;
        WearData data = holder.trailwright$wearData();
        if (data == null) {
            data = level.getDataStorage().computeIfAbsent(WearData::load, WearData::new, ID);
            holder.trailwright$setWearData(data);
        }
        return data;
    }

    public boolean isEmpty() { return entries.isEmpty(); }

    /** Linear decay, floored at 0. Pure, so it is unit-tested directly. */
    static float decayed(float wear, long elapsedTicks, float decayPerDay) {
        if (elapsedTicks <= 0) return wear;
        float v = wear - decayPerDay * (elapsedTicks / (float) TICKS_PER_DAY);
        return v > 0f ? v : 0f;
    }

    /** Decays the stored value to {@code now}, adds {@code points}, and returns the result. */
    public float addWear(long pos, float points, long now, float decayPerDay) {
        Entry e = entries.get(pos);
        if (e == null) {
            e = new Entry(points, now);
            entries.put(pos, e);
        } else {
            e.wear = decayed(e.wear, now - e.lastTick, decayPerDay) + points;
            e.lastTick = now;
        }
        setDirty();
        return e.wear;
    }

    /** Current decayed wear without modifying the entry, or 0 if none is stored. */
    public float peek(long pos, long now, float decayPerDay) {
        Entry e = entries.get(pos);
        return e == null ? 0f : decayed(e.wear, now - e.lastTick, decayPerDay);
    }

    public boolean has(long pos) { return entries.containsKey(pos); }

    public int size() { return entries.size(); }

    public void reset(long pos) {
        if (entries.remove(pos) != null) setDirty();
    }

    /** Applies decay to every entry and drops entries that reach 0. Returns the number dropped. */
    public int prune(long now, float decayPerDay) {
        return prune(now, decayPerDay, pos -> false);
    }

    /** As {@link #prune(long, float)}, and also drops entries for which {@code drop} is true. */
    public int prune(long now, float decayPerDay, LongPredicate drop) {
        if (entries.isEmpty()) return 0;
        int removed = 0;
        ObjectIterator<Long2ObjectMap.Entry<Entry>> it = Long2ObjectMaps.fastIterator(entries);
        while (it.hasNext()) {
            Long2ObjectMap.Entry<Entry> me = it.next();
            Entry e = me.getValue();
            float v = decayed(e.wear, now - e.lastTick, decayPerDay);
            if (v <= 0f || drop.test(me.getLongKey())) {
                it.remove();
                removed++;
            } else {
                e.wear = v;
                e.lastTick = now;
            }
        }
        setDirty();
        return removed;
    }

    /** Called every server tick per level; prunes once per {@code pruneIntervalTicks}. */
    public void maybePrune(ServerLevel level) {
        long now = level.getGameTime();
        TrailWrightConfig cfg = TrailWrightConfig.get();
        if (lastPruneTick != Long.MIN_VALUE && now - lastPruneTick < cfg.pruneIntervalTicks) return;
        lastPruneTick = now;
        // Drop entries whose block lost its rule, but only in loaded chunks: the prune never
        // loads a chunk. Entries in unloaded chunks wait for decay (DESIGN.md §5).
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        prune(now, cfg.decayPerDay, pos -> {
            m.set(pos);
            return level.isLoaded(m) && WearRules.forState(level.getBlockState(m)) == null;
        });
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        int n = entries.size();
        long[] pos = new long[n];
        int[] wear = new int[n];
        long[] tick = new long[n];
        int i = 0;
        for (Long2ObjectMap.Entry<Entry> me : Long2ObjectMaps.fastIterable(entries)) {
            pos[i] = me.getLongKey();
            wear[i] = Float.floatToRawIntBits(me.getValue().wear);
            tick[i] = me.getValue().lastTick;
            i++;
        }
        tag.putLongArray(KEY_POS, pos);
        tag.putIntArray(KEY_WEAR, wear);
        tag.putLongArray(KEY_TICK, tick);
        return tag;
    }

    public static WearData load(CompoundTag tag) {
        WearData data = new WearData();
        long[] pos = tag.getLongArray(KEY_POS);
        int[] wear = tag.getIntArray(KEY_WEAR);
        long[] tick = tag.getLongArray(KEY_TICK);
        if (pos.length != wear.length || pos.length != tick.length) {
            TrailWright.LOG.warn("{} has mismatched arrays ({}/{}/{}); starting empty",
                    ID, pos.length, wear.length, tick.length);
            return data;
        }
        for (int i = 0; i < pos.length; i++) {
            data.entries.put(pos[i], new Entry(Float.intBitsToFloat(wear[i]), tick[i]));
        }
        return data;
    }
}
