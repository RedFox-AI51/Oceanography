package com.redfoxsenpai.oceanography.item;

import com.redfoxsenpai.oceanography.block.FishFoodBlockEntity;
import com.redfoxsenpai.oceanography.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.*;

public class FishFoodItem extends Item {

    private static final int TOTAL_CAPACITY = 6;
    private static final int MAX_BLOCKS = 6;

    public FishFoodItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(stack);
        }

        BlockHitResult blockHit = (BlockHitResult) hit;
        BlockPos waterPos = blockHit.getBlockPos();

        if (!isWaterSource(level, waterPos)) {
            return InteractionResultHolder.pass(stack);
        }

        BlockPos startPos = waterPos.above();
        if (!level.isEmptyBlock(startPos)) {
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        List<BlockPos> targets = findSpreadPositions(level, startPos);
        if (targets.isEmpty()) {
            return InteractionResultHolder.pass(stack);
        }

        int n = targets.size();
        int base = TOTAL_CAPACITY / n;
        int remainder = TOTAL_CAPACITY % n;

        for (int i = 0; i < n; i++) {
            BlockPos pos = targets.get(i);
            level.setBlockAndUpdate(pos, ModBlocks.FISH_FOOD.get().defaultBlockState());
            if (level.getBlockEntity(pos) instanceof FishFoodBlockEntity be) {
                int cap = base + (i < remainder ? 1 : 0); // spread the remainder across the first few blocks
                be.setCapacity(cap);
            }
        }

        level.playSound(null, startPos, SoundEvents.LILY_PAD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /** Breadth-first search outward from the placement point for up to MAX_BLOCKS valid water-surface tiles. */
    private List<BlockPos> findSpreadPositions(Level level, BlockPos start) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && result.size() < MAX_BLOCKS) {
            BlockPos current = queue.poll();

            if (isValidSurfacePosition(level, current)) {
                result.add(current);
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockPos next = current.relative(dir);
                    if (!visited.contains(next)) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            }
        }

        return result;
    }

    private boolean isValidSurfacePosition(Level level, BlockPos pos) {
        if (!level.isEmptyBlock(pos)) return false;
        return isWaterSource(level, pos.below());
    }

    private boolean isWaterSource(Level level, BlockPos pos) {
        FluidState fluidState = level.getFluidState(pos);
        return fluidState.is(FluidTags.WATER) && fluidState.isSource();
    }
}