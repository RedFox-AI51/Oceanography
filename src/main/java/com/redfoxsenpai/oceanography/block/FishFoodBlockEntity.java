package com.redfoxsenpai.oceanography.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FishFoodBlockEntity extends BlockEntity {

    private int capacity = 1;
    private final Set<UUID> claimedFish = new HashSet<>();

    public FishFoodBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FISH_FOOD.get(), pos, state);
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
        setChanged();
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean tryEat(Mob fish) {
        if (capacity <= 0) return false;
        if (claimedFish.contains(fish.getUUID())) return false;
        claimedFish.add(fish.getUUID());
        capacity--;
        setChanged();
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Capacity", capacity);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        capacity = tag.getInt("Capacity");
    }
}