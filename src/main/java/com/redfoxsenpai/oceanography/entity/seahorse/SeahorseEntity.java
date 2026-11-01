
package com.redfoxsenpai.oceanography.entity.seahorse;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.level.Level;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class SeahorseEntity extends WaterAnimal implements GeoEntity {

    // GeckoLib animation definition
    private static final RawAnimation SWIM_ANIMATION = RawAnimation.begin().thenLoop("Swim");

    // GeckoLib animation instance cache
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public SeahorseEntity(EntityType<? extends SeahorseEntity> entityType,
            Level level) {

        super(entityType, level);

        // Aquatic movement controls
        this.moveControl = new SmoothSwimmingMoveControl(
                this, 85, 10, 0.02F, 0.1F, true);

        this.lookControl = new SmoothSwimmingLookControl(this, 10);
    }

    // Entity attributes
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.15D)
                .add(Attributes.FOLLOW_RANGE, 12.0D);
    }

    // Aquatic navigation
    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    // AI goals
    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.goalSelector.addGoal(
                0, new RandomSwimmingGoal(this, 1.0D, 40));

        this.goalSelector.addGoal(
                1, new RandomLookAroundGoal(this));
    }

    // GeckoLib animation controller
    @Override
    public void registerControllers(
            AnimatableManager.ControllerRegistrar controllers) {

        controllers.add(
                new AnimationController<>(
                        this,
                        "swimming",
                        0,
                        this::swimAnimation));
    }

    private <T extends GeoEntity> PlayState swimAnimation(
            AnimationState<T> state) {

        if (this.isInWater()) {
            return state.setAndContinue(SWIM_ANIMATION);
        }

        return PlayState.STOP;
    }

    // GeckoLib instance cache
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
