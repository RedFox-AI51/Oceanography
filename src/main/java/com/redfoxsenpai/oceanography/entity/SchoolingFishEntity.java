package com.redfoxsenpai.oceanography.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class SchoolingFishEntity extends AbstractFish {

    public static final float DEFAULT_SCALE = 0.5F;
    public static final float MIN_SCALE = 0.25F;
    public static final float MAX_SCALE = 0.75F;

    public SchoolingFishEntity(EntityType<? extends AbstractFish> type, Level level) {
        super(type, level);

        float scale = DEFAULT_SCALE + (this.random.nextFloat() - 0.5F) * (MAX_SCALE - MIN_SCALE);
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));

        AttributeInstance scaleAttribute = this.getAttribute(Attributes.SCALE);
        if (scaleAttribute != null) {
            scaleAttribute.setBaseValue(scale);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.6D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.5D));
        this.goalSelector.addGoal(2, new SeekChumGoal(this, 1.2D));
        this.goalSelector.addGoal(3, new SeekFishFoodGoal(this, 1.1D));
        this.goalSelector.addGoal(4, new FishFlockGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new RandomSwimmingGoal(this, 1.0D, 10));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected SoundEvent getFlopSound() {
        return SoundEvents.COD_FLOP;
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(Items.WATER_BUCKET);
    }
}