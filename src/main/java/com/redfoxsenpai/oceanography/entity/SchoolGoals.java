package com.redfoxsenpai.oceanography.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;

/** Adds the school behaviour to a fish. Lower priority number = more important. */
public final class SchoolGoals {

    private SchoolGoals() {}

    /**
     * @param fleePriority  must be more important than the feeding goals, so a scared fish stops eating and runs
     * @param flockPriority must be less important than the feeding goals, so a hungry fish still goes for the chum
     */
    public static void install(Mob mob, GoalSelector selector, int fleePriority, int flockPriority) {
        SchoolState state = SchoolState.of(mob);
        selector.addGoal(fleePriority, new FishFleeGoal(mob, state));
        selector.addGoal(flockPriority, new FishFlockGoal(mob, state));
    }

    /**
     * Vanilla fish already run from any player within 8 blocks, however slowly it approaches. That would drown out the
     * graded reaction (polite drift for a slow player, panic for a charging one), so remove it from fish that use ours.
     */
    public static void stripVanillaPlayerAvoidance(GoalSelector selector) {
        selector.getAvailableGoals().removeIf(wrapped -> wrapped.getGoal() instanceof AvoidEntityGoal<?>);
    }
}
