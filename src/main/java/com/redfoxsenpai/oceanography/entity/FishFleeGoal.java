package com.redfoxsenpai.oceanography.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Running from danger. Runs whenever SchoolState says the fish is panicking, ahead of feeding and schooling.
 *
 * A moderate scare makes the school bolt together: everyone heads roughly away from the threat, each at a slightly different
 * angle. A severe scare (SchoolState.SCATTER_THRESHOLD) breaks the school up: each fish takes its own wide escape angle and
 * keeps changing it every few ticks, so they dart in different directions. When the fish is far enough from the danger, or
 * the panic runs out, SchoolState starts the regroup phase and FishFlockGoal pulls them back together.
 */
public class FishFleeGoal extends Goal {

    private static final double SAFE_DISTANCE = 22.0D;
    private static final double LOOKAHEAD = 5.0D;
    private static final double SEPARATION_DISTANCE = 1.0D;
    private static final int STEER_INTERVAL = 2;

    private final Mob fish;
    private final SchoolState state;
    private final int turnBias;

    private Vec3 heading = Vec3.ZERO;
    private double speed = SchoolSteering.FLEE_SPEED;
    private long nextSteer;
    private long nextDart;

    public FishFleeGoal(Mob fish, SchoolState state) {
        this.fish = fish;
        this.state = state;
        this.turnBias = (fish.getId() & 1) == 0 ? 1 : -1;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (!fish.isInWater()) return false;
        state.update(fish.level().getGameTime());
        return state.isPanicking();
    }

    @Override
    public boolean canContinueToUse() {
        if (!fish.isInWater()) return false;
        state.update(fish.level().getGameTime());
        if (!state.isPanicking()) return false;

        Vec3 threat = state.getThreatPos();
        if (threat != null && fish.position().distanceToSqr(threat) > SAFE_DISTANCE * SAFE_DISTANCE) {
            state.calmDown();           // outran it: start swimming back
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        nextSteer = 0;
        nextDart = fish.level().getGameTime() + 8 + fish.getRandom().nextInt(10);
        fish.getNavigation().stop();
        if (fish.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.BUBBLE, fish.getX(), fish.getY() + 0.1D, fish.getZ(), 4, 0.15D, 0.1D, 0.15D, 0.02D);
        }
    }

    @Override
    public void tick() {
        long now = fish.level().getGameTime();
        state.update(now);

        if (state.isScatter() && now >= nextDart) {
            nextDart = now + 8 + fish.getRandom().nextInt(10);
            state.nudgeFleeJitter(0.9D);            // scattering fish dart about instead of running dead straight
        }
        if (now >= nextSteer) {
            nextSteer = now + STEER_INTERVAL;
            steer(now);
        }
        SchoolSteering.swim(fish, heading, speed);
    }

    @Override
    public void stop() {
        // regrouping is started by SchoolState; leave the velocity alone so the next goal takes over smoothly
    }

    private void steer(long now) {
        Vec3 pos = fish.position();
        Vec3 threat = state.getThreatPos();

        Vec3 away = threat != null ? pos.subtract(threat) : Vec3.ZERO;
        if (away.lengthSqr() < 0.04D) {
            // right on top of the danger: any way out will do
            away = new Vec3(fish.getRandom().nextDouble() - 0.5D, 0.0D, fish.getRandom().nextDouble() - 0.5D);
        }
        away = new Vec3(away.x, 0.0D, away.z).normalize();

        Vec3 flee = SchoolSteering.rotateY(away, state.getFleeJitter());
        flee = new Vec3(flee.x, state.getFleeDive(), flee.z);

        Vec3 separation = Vec3.ZERO;
        Vec3 alignment = Vec3.ZERO;
        for (Mob mate : state.neighbours(6.0D, now)) {
            if (!mate.isAlive() || mate.isRemoved()) continue;
            Vec3 offset = pos.subtract(mate.position());
            double dist = offset.length();
            if (dist < SEPARATION_DISTANCE && dist > 1.0E-3D) separation = separation.add(offset.scale(1.0D / (dist * dist)));
            alignment = alignment.add(mate.getDeltaMovement());
        }
        double sepLen = separation.length();
        if (sepLen > 1.0E-4D) separation = separation.scale(Math.min(1.0D, sepLen) / sepLen);
        alignment = alignment.lengthSqr() > 1.0E-8D ? alignment.normalize() : Vec3.ZERO;

        Vec3 sum = flee.add(separation.scale(0.9D));
        if (!state.isScatter()) sum = sum.add(alignment.scale(0.6D));      // a moderate scare keeps the school together
        sum = sum.add(0.0D, SchoolSteering.verticalBias(fish) * 0.8D, 0.0D);

        heading = SchoolSteering.clearDirection(fish.level(), pos, sum, LOOKAHEAD, turnBias);

        double t = Mth.clamp((state.getPanicIntensity() - SchoolState.PANIC_THRESHOLD) / (1.0D - SchoolState.PANIC_THRESHOLD), 0.0D, 1.0D);
        speed = Mth.lerp(t, SchoolSteering.FLEE_SPEED, SchoolSteering.SCATTER_SPEED) * state.getSpeedFactor();
    }
}
