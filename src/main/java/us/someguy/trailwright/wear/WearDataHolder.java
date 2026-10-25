/*
 * Purpose:      Duck interface mixed into ServerLevel to cache that level's WearData, so the
 *               block-change hook (DESIGN.md §4.6) costs one field read.
 * Dependencies: Implemented by mixin.ServerLevelMixin.
 */
package us.someguy.trailwright.wear;

public interface WearDataHolder {
    WearData trailwright$wearData();
    void trailwright$setWearData(WearData data);
}
