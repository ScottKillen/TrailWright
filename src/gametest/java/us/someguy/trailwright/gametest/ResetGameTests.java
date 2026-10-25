/*
 * Purpose:      Fabric GameTests for wear resets (TESTING.md T5.1–T5.4). Wear is seeded
 *               directly through WearData so the tests do not depend on advancement rolls.
 * Dependencies: fabric-gametest-api-v1 (dev only); WearData; vanilla BoneMealItem, BrushItem,
 *               dispenser.
 */
package us.someguy.trailwright.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import us.someguy.trailwright.wear.WearData;

public final class ResetGameTests implements FabricGameTest {
    private static final BlockPos SPOT = new BlockPos(2, 1, 2);

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    /** Grass at {@code rel} with 5 points of stored wear. */
    private static void wornGrass(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel, Blocks.GRASS_BLOCK);
        WearData.get(h.getLevel()).addWear(h.absolutePos(rel).asLong(), 5f, h.getLevel().getGameTime(), 0f);
    }

    private static boolean hasWear(GameTestHelper h, BlockPos rel) {
        return WearData.get(h.getLevel()).has(h.absolutePos(rel).asLong());
    }

    // T5.1
    @GameTest(template = EMPTY_STRUCTURE)
    public void blockTypeChangeResetsButStateChangeDoesNot(GameTestHelper h) {
        floor(h);
        wornGrass(h, SPOT);
        h.setBlock(SPOT, Blocks.GRASS_BLOCK.defaultBlockState().setValue(SnowyDirtBlock.SNOWY, true));
        h.assertTrue(hasWear(h, SPOT), "state-only change cleared wear");
        h.setBlock(SPOT, Blocks.DIRT_PATH);
        h.assertFalse(hasWear(h, SPOT), "block replacement kept wear");
        h.succeed();
    }

    // T5.2 (hand): growCrop is what BoneMealItem#useOn calls.
    @GameTest(template = EMPTY_STRUCTURE)
    public void boneMealGrowthResetsWear(GameTestHelper h) {
        floor(h);
        wornGrass(h, SPOT);
        boolean grew = BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), h.getLevel(), h.absolutePos(SPOT));
        h.assertTrue(grew, "bone meal was refused");
        h.assertFalse(hasWear(h, SPOT), "growth on top did not clear wear");
        h.succeed();
    }

    // T5.2 (no growth): a solid block above makes vanilla refuse bone meal on grass.
    @GameTest(template = EMPTY_STRUCTURE)
    public void boneMealWithoutGrowthKeepsWear(GameTestHelper h) {
        floor(h);
        wornGrass(h, SPOT);
        h.setBlock(SPOT.above(), Blocks.STONE);
        boolean grew = BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), h.getLevel(), h.absolutePos(SPOT));
        h.assertFalse(grew, "bone meal applied under a solid block");
        h.assertTrue(hasWear(h, SPOT), "wear cleared without growth");
        h.succeed();
    }

    // T5.2 (dispenser): powered dispenser facing the worn grass.
    @GameTest(template = EMPTY_STRUCTURE)
    public void dispenserBoneMealResetsWear(GameTestHelper h) {
        floor(h);
        wornGrass(h, SPOT);
        BlockPos dispenser = SPOT.west();
        h.setBlock(dispenser, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        if (h.getLevel().getBlockEntity(h.absolutePos(dispenser)) instanceof DispenserBlockEntity be) {
            be.setItem(0, new ItemStack(Items.BONE_MEAL));
        } else {
            h.fail("no dispenser block entity", dispenser);
        }
        h.setBlock(dispenser.north(), Blocks.REDSTONE_BLOCK);
        h.succeedWhen(() -> h.assertFalse(hasWear(h, SPOT), "dispenser bone meal did not clear wear"));
    }

    // T5.3
    @GameTest(template = EMPTY_STRUCTURE)
    public void plantingResetsGroundBelow(GameTestHelper h) {
        floor(h);
        wornGrass(h, SPOT);
        h.setBlock(SPOT.above(), Blocks.POPPY);
        h.assertFalse(hasWear(h, SPOT), "planting did not clear wear below");
        h.succeed();
    }

    // T5.4: a mock player standing on the block, looking straight down.
    @GameTest(template = EMPTY_STRUCTURE)
    public void brushStrokeResetsWear(GameTestHelper h) {
        floor(h);
        wornGrass(h, SPOT);
        Player player = h.makeMockPlayer();
        BlockPos feet = h.absolutePos(SPOT.above());
        player.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0f, 90f);
        ItemStack brush = new ItemStack(Items.BRUSH);
        int duration = Items.BRUSH.getUseDuration(brush);

        // useTicks = duration - remaining + 1; tick 6 is not a stroke, tick 5 is.
        Items.BRUSH.onUseTick(h.getLevel(), player, brush, duration - 6 + 1);
        h.assertTrue(hasWear(h, SPOT), "non-stroke tick cleared wear");
        Items.BRUSH.onUseTick(h.getLevel(), player, brush, duration - 5 + 1);
        h.assertFalse(hasWear(h, SPOT), "brush stroke did not clear wear");
        h.succeed();
    }
}
