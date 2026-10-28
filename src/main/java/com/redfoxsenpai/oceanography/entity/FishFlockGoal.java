package com.redfoxsenpai.oceanography.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * How a school swims when nothing is wrong, and how it pulls itself back together afterwards.
 *
 * Classic "boids" steering, three rules plus a few extras:
 *   separation  - don't crowd a neighbour
 *   alignment   - head the way your neighbours are heading
 *   cohesion    - drift toward the middle of the group (stragglers swim faster to catch up)
 *   wander      - the whole school follows one slowly curving path, derived from the lowest fish id in the group, so every
 *                 fish agrees on it without any communication and nothing needs saving
 *   depth       - stay in the preferred water band (see DepthKeeping)
 *   unease      - a player closer than personal space makes fish drift out of the way and bunch up, without panicking
 *
 * After a scare (see SchoolState) the regroup phase starts with cohesion almost off and ramps it up, and widens the search
 * radius, so scattered fish drift back together over several seconds instead of snapping into formation.
 * Fish with no company are left to DepthSwimGoal.
 */
public class FishFlockGoal extends Goal {

    /** Alignment and separation only care about close neighbours; cohesion sees much further so a split school finds itself. */
    private static final double ALIGN_RADIUS = 8.0D;
    private static final double COHESION_RADIUS = 24.0D;
    private static final double REGROUP_RADIUS = 24.0D;
    private static final double SEPARATION_DISTANCE = 1.1D;
    private static final double COHESION_DEADZONE = 2.2D;
    private static final double LOOKAHEAD = 4.0D;
    private static final int STEER_INTERVAL = 2;
    private static final int LONELY_GRACE_TICKS = 60;
    private static final int IDLE_RECHECK_TICKS = 10;
    private static final float ALERT_ENGAGE = 0.08F;
    private static final float ALERT_RELEASE = 0.03F;
    private static final double TAU = Math.PI * 2.0D;

    private final Mob fish;
    private final SchoolState state;
    private final int turnBias;

    private Vec3 heading = Vec3.ZERO;
    private double speed = SchoolSteering.CRUISE_SPEED;
    private long nextSteer;
    private long nextCheck;
    private long lastCompany;

    public FishFlockGoal(Mob fish, SchoolState state) {
        this.fish = fish;
        this.state = state;
        this.turnBias = (fish.getId() & 1) == 0 ? 1 : -1;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    /** Steering is applied every tick; by default goals only tick every other tick, which makes turns lag. */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (!fish.isInWater()) return false;
        long now = fish.level().getGameTime();
        state.update(now);
        if (state.isPanicking()) return false;
        if (now < nextCheck) return false;
        nextCheck = now + IDLE_RECHECK_TICKS + fish.getRandom().nextInt(IDLE_RECHECK_TICKS);
        return state.isRegrouping() || state.getAlert() > ALERT_ENGAGE || hasCompany(now);
    }

    @Override
    public boolean canContinueToUse() {
        if (!fish.isInWater()) return false;
        long now = fish.level().getGameTime();
        state.update(now);
        if (state.isPanicking()) return false;
        if (state.isRegrouping() || state.getAlert() > ALERT_RELEASE) return true;
        if (hasCompany(now)) {
            lastCompany = now;
            return true;
        }
        return now - lastCompany < LONELY_GRACE_TICKS;
    }

    @Override
    public void start() {
        long now = fish.level().getGameTime();
        lastCompany = now;
        nextSteer = 0;
        fish.getNavigation().stop();
        Vec3 current = fish.getDeltaMovement();
        heading = current.lengthSqr() > 1.0E-6D ? current : Vec3.directionFromRotation(0.0F, fish.getYRot());
    }

    @Override
    public void tick() {
        long now = fish.level().getGameTime();
        state.update(now);
        if (now >= nextSteer) {
            nextSteer = now + STEER_INTERVAL;
            steer(now);
        }
        SchoolSteering.swim(fish, heading, speed);
    }

    @Override
    public void stop() {
        // leave the velocity alone: DepthSwimGoal or the next goal takes over smoothly
    }

    // ------------------------------------------------------------------------------------------------ steering
    private boolean hasCompany(long now) {
        double r2 = COHESION_RADIUS * COHESION_RADIUS;
        for (Mob mate : state.neighbours(COHESION_RADIUS, now)) {
            if (isValid(mate) && fish.distanceToSqr(mate) <= r2) return true;
        }
        return false;
    }

    private static boolean isValid(Mob mate) {
        return mate.isAlive() && !mate.isRemoved();
    }

    private void steer(long now) {
        Vec3 pos = fish.position();
        boolean regrouping = state.isRegrouping();
        double progress = state.regroupProgress();             // 0 right after a scare .. 1 recovered
        double alignRadius = regrouping ? REGROUP_RADIUS : ALIGN_RADIUS;
        double cohesionRadius = regrouping ? REGROUP_RADIUS : COHESION_RADIUS;
        double reach = Math.max(alignRadius, cohesionRadius);
        double alert = state.getAlert();

        List<Mob> mates = state.neighbours(reach, now);

        Vec3 separation = Vec3.ZERO;
        Vec3 alignment = Vec3.ZERO;
        Vec3 centre = Vec3.ZERO;
        int alignCount = 0;
        int count = 0;
        boolean anyMate = false;
        UUID lowest = fish.getUUID();
        UUID currentSchool = state.getSchoolId();
        boolean sawCurrentSchool = false;

        for (Mob mate : mates) {
            if (!isValid(mate)) continue;
            Vec3 offset = pos.subtract(mate.position());
            double dist = offset.length();
            if (dist > reach) continue;
            anyMate = true;

            if (dist < SEPARATION_DISTANCE) {
                if (dist < 1.0E-3D) {
                    offset = new Vec3(fish.getRandom().nextDouble() - 0.5D, 0.0D, fish.getRandom().nextDouble() - 0.5D);
                    dist = 0.5D;
                }
                double d = Math.max(dist, 0.15D);
                separation = separation.add(offset.scale(1.0D / (d * d)));
            }
            if (dist <= alignRadius) {
                alignment = alignment.add(mate.getDeltaMovement());
                alignCount++;
            }
            if (dist <= cohesionRadius) {
                centre = centre.add(mate.position());
                count++;
            }

            // School identity is gossiped: each fish adopts the lowest id that any neighbour has heard of, so the id
            // spreads along chains of fish and a school that was pulled apart still agrees on one wander path.
            UUID id = mate.getUUID();
            if (id.compareTo(lowest) < 0) lowest = id;
            SchoolState mateState = SchoolState.peek(mate);
            UUID mateSchool = mateState != null ? mateState.getSchoolId() : null;
            if (mateSchool != null && mateSchool.compareTo(lowest) < 0) lowest = mateSchool;
            if (id.equals(currentSchool)) sawCurrentSchool = true;
        }

        // Sticky school identity: only change it when the old one is gone or a lower id turns up.
        if (anyMate && (currentSchool == null
                || (!sawCurrentSchool && !currentSchool.equals(fish.getUUID()))
                || lowest.compareTo(currentSchool) < 0)) {
            state.setSchoolId(lowest);
            currentSchool = lowest;
        }

        Vec3 cohesion = Vec3.ZERO;
        double distToCentre = 0.0D;
        if (count > 0) {
            centre = centre.scale(1.0D / count);
            Vec3 toCentre = centre.subtract(pos);
            distToCentre = toCentre.length();
            if (distToCentre > COHESION_DEADZONE) {
                cohesion = toCentre.scale(1.0D / distToCentre)
                        .scale(Math.min(1.0D, (distToCentre - COHESION_DEADZONE) / 6.0D));
            }
        } else if (regrouping && state.getRally() != null) {
            // nobody in sight after a scare: head for where the school was
            Vec3 toRally = state.getRally().subtract(pos);
            distToCentre = toRally.length();
            if (distToCentre > 1.5D) cohesion = toRally.scale(1.0D / distToCentre);
        }
        alignment = alignCount > 0 && alignment.lengthSqr() > 1.0E-8D ? alignment.normalize() : Vec3.ZERO;

        double sepLen = separation.length();
        if (sepLen > 1.0E-4D) separation = separation.scale(Math.min(1.0D, sepLen) / sepLen);
        else separation = Vec3.ZERO;

        // An uneasy player nearby: make room, and bunch up a little.
        Vec3 avoid = Vec3.ZERO;
        Vec3 threat = state.getThreatPos();
        if (alert > 0.02D && threat != null) {
            Vec3 away = pos.subtract(threat);
            if (away.lengthSqr() > 1.0E-6D) avoid = away.normalize().scale(alert * 2.4D);
        }

        double wCohesion = (regrouping ? 0.10D + 1.30D * progress : 0.90D) * (1.0D + alert);
        double wAlignment = regrouping ? 0.30D + 0.70D * progress : 1.00D;
        double wWander = regrouping ? 0.15D + 0.40D * progress : 0.55D;

        Vec3 wander = schoolWander(currentSchool, fish.getUUID(), now);
        Vec3 depth = new Vec3(0.0D, SchoolSteering.verticalBias(fish), 0.0D);

        Vec3 sum = separation.scale(1.6D)
                .add(alignment.scale(wAlignment))
                .add(cohesion.scale(wCohesion))
                .add(wander.scale(wWander))
                .add(avoid)
                .add(depth);
        sum = new Vec3(sum.x, sum.y * 0.6D, sum.z);
        if (sum.lengthSqr() < 1.0E-6D) sum = wander;

        heading = SchoolSteering.clearDirection(fish.level(), pos, sum, LOOKAHEAD, turnBias);

        // Stragglers swim faster to catch up; a recovering school swims a little faster too.
        double straggler = Mth.clamp((distToCentre - 2.5D) / 6.0D, 0.0D, 1.0D);
        double target = regrouping
                ? Mth.lerp(straggler, SchoolSteering.CRUISE_SPEED, SchoolSteering.REGROUP_SPEED)
                : SchoolSteering.CRUISE_SPEED * (1.0D + 0.6D * straggler);
        target = Math.max(target, Mth.lerp(Mth.clamp(alert, 0.0D, 1.0D), SchoolSteering.CRUISE_SPEED, SchoolSteering.ALERT_SPEED));
        speed = target * state.getSpeedFactor();
    }

    /**
     * One slowly curving path per school. Every fish that has the same school id computes the same direction at the same
     * time, so the group travels together with no communication. The path loops, so schools stay roughly in one area.
     */
    private static Vec3 schoolWander(@Nullable UUID id, UUID fallback, long now) {
        UUID key = id != null ? id : fallback;
        long h = key.getMostSignificantBits() ^ key.getLeastSignificantBits();
        h *= 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;

        double phase1 = (h & 0xFFFFL) / 65535.0D * TAU;
        double phase2 = ((h >>> 16) & 0xFFFFL) / 65535.0D * TAU;
        double phase3 = ((h >>> 32) & 0xFFFFL) / 65535.0D * TAU;
        double omega = (0.0015D + ((h >>> 40) & 0xFFL) / 255.0D * 0.0025D) * (((h >>> 48) & 1L) == 0L ? 1.0D : -1.0D);

        double angle = phase1 + omega * now + 0.7D * Math.sin(now / 137.0D + phase2);
        double vertical = 0.12D * Math.sin(now / 230.0D + phase3);
        return new Vec3(Math.cos(angle), vertical, Math.sin(angle));
    }
}
