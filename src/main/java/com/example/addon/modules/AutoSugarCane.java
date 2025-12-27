package com.example.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class AutoSugarCane extends Module {

    private BlockPos target;

    public AutoSugarCane() {
        super(Categories.Misc, "auto-sugarcane", "Automatically farms sugar cane in render distance.");
    }

    @Override
    public void onActivate() {
        target = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (target == null || mc.world.getBlockState(target).isAir()) {
            target = findSugarCane();
        }

        if (target == null) return;

        Vec3d targetVec = Vec3d.ofCenter(target);

        // Rotate to block
        Rotations.rotate(
            Rotations.getYaw(targetVec),
            Rotations.getPitch(targetVec),
            () -> {}
        );

        // Walk to block
        PlayerUtils.walkTowards(targetVec, 0.25);

        // Break when in range
        if (mc.player.squaredDistanceTo(targetVec) <= 4.5) {
            BlockUtils.breakBlock(target, true);
        }
    }

    private BlockPos findSugarCane() {
        BlockPos playerPos = mc.player.getBlockPos();
        int y = playerPos.getY();

        int radius = mc.options.getViewDistance().getValue() * 16;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos pos = playerPos.add(x, 0, z);

                // same Y level
                if (pos.getY() != y) continue;

                // sugar cane base
                if (mc.world.getBlockState(pos).getBlock() == Blocks.SUGAR_CANE) {
                    // check second stage (height >= 2)
                    if (mc.world.getBlockState(pos.up()).getBlock() == Blocks.SUGAR_CANE) {
                        return pos.up(); // break second block, not base
                    }
                }
            }
        }

        return null;
    }
}
