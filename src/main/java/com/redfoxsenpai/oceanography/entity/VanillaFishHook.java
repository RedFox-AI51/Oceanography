package com.redfoxsenpai.oceanography.entity;

import com.redfoxsenpai.oceanography.Oceanography;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.animal.Salmon;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;

@Mod.EventBusSubscriber(modid = Oceanography.MODID)
public class VanillaFishHook {

    private static final float DEFAULT_SCALE = 0.5F;
    private static final float MIN_SCALE = 0.25F;
    private static final float MAX_SCALE = 0.75F;

    private static final Field GOAL_SELECTOR_FIELD;

    static {
        Field field = null;
        try {
            field = Mob.class.getDeclaredField("goalSelector");
            field.setAccessible(true);
        } catch (NoSuchFieldException e) {
            Oceanography.LOGGER.error("Could not locate goalSelector field for vanilla fish hook", e);
        }
        GOAL_SELECTOR_FIELD = field;
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (GOAL_SELECTOR_FIELD == null || event.getLevel().isClientSide) return;

        Mob mob = null;
        if (event.getEntity() instanceof Cod cod) mob = cod;
        else if (event.getEntity() instanceof Salmon salmon) mob = salmon;
        else if (event.getEntity() instanceof TropicalFish tropicalFish) mob = tropicalFish;

        if (mob == null) return;

        // The same fish can join a level more than once (dimension change); only set it up the first time.
        if (!SchoolState.of(mob).markInstalled()) return;

        // Randomize size, same range as herring
        AttributeInstance scaleAttribute = mob.getAttribute(Attributes.SCALE);
        if (scaleAttribute != null) {
            float scale = DEFAULT_SCALE + (mob.getRandom().nextFloat() - 0.5F) * (MAX_SCALE - MIN_SCALE);
            scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
            scaleAttribute.setBaseValue(scale);
        }

        try {
            GoalSelector goalSelector = (GoalSelector) GOAL_SELECTOR_FIELD.get(mob);
            goalSelector.addGoal(2, new SeekChumGoal(mob, 1.2D));
            goalSelector.addGoal(1, new SeekFishFoodGoal(mob, 1.1D));
            SchoolGoals.stripVanillaPlayerAvoidance(goalSelector);
            SchoolGoals.install(mob, goalSelector, 0, 3);
        } catch (IllegalAccessException e) {
            Oceanography.LOGGER.error("Failed to inject schooling goal into vanilla fish", e);
        }
    }
}