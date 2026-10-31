package com.redfoxsenpai.oceanography.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How a school swims when nothing is wrong, and how schools form, split and re-form.
 *
 * Steering is "boids" with a few extras:
 *   separation  - don't crowd any neighbour
 *   alignment   - head the way your SCHOOLMATES (within 8 blocks) are heading
 *   cohesion    - drift toward the middle of your schoolmates (within 24 blocks); stragglers swim faster to catch up
 *   wander      - every school follows one slowly curving path, derived from its school id, so schoolmates travel together
 *   individuality - each fish has a small personal angle and a slow personal meander, so no two are ever exactly parallel
 *   depth       - stay in the preferred water band (see DepthKeeping)
 *   current     - swim relative to the water, and face upstream a little when the current is strong
 *   unease      - a player or mob close by makes fish drift out of the way and bunch up, without panicking
 *
 * Schools are not fixed. Each fish belongs to a school (identified by an id); it only aligns and cohesion-steers with fish of
 * its own school. Membership changes:
 *   joining   - a fish adopts the school most of its close neighbours belong to, so small groups are absorbed by larger ones
 *               and two fish that meet merge into one school
 *   lost      - after a severe scare some fish become lost (see SchoolState): they leave their school, wander on a path of
 *               their own, and afterwards seek company, joining a school or founding a new one with other lost fish
 *   wandering off - now and then a fish leaves its school briefly anyway
 * Moderate scares do not lose anyone: the school bolts together and regroups.
 */
public class FishFlockGoal extends Goal {

    /** Alignment and separation only care about close neighbours; cohesion sees much further so a split school finds itself. */
    private static final double ALIGN_RADIUS = 8.0D;
    private static final double COHESION_RADIUS = 24.0D;
    private static final double REGROUP_RADIUS = 24.0D;
    private static final double JOIN_RADIUS = 8.0D;
    private static final double SEPARATION_DISTANCE = 1.1D;
    private static final double COHESION_DEADZONE = 2.2D;
    private static final double LOOKAHEAD = 4.0D;

    private static final double ALIGN_WEIGHT = 0.8D;            // below 1: schools are a loose crowd, not a formation
    private static final double INDIVIDUALITY_WEIGHT = 0.35D;
    private static final double SEEK_WEIGHT = 0.8D;             // pull toward any company when you have none of your own
    private static final double RHEOTAXIS_WEIGHT = 0.35D;       // face upstream when the current is strong
    private static final double RHEOTAXIS_FULL_FLOW = 0.03D;    // blocks per tick at which that is at full strength
    private static final int SWITCH_COOLDOWN_TICKS = 60;

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
        SchoolSteering.swim(fish, heading, speed, state.current(fish, now));
        state.markSteered(now);
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
        boolean lost = state.isLost(now);
        boolean regrouping = state.isRegrouping() && !lost;
        double progress = state.regroupProgress();             // 0 right after a scare .. 1 recovered
        double alignRadius = regrouping ? REGROUP_RADIUS : ALIGN_RADIUS;
        double cohesionRadius = regrouping ? REGROUP_RADIUS : COHESION_RADIUS;
        double reach = Math.max(alignRadius, cohesionRadius);
        double alert = state.getAlert();
        UUID mySchool = state.schoolIdOrSelf();
        Vec3 current = state.current(fish, now);

        List<Mob> mates = state.neighbours(reach, now);

        Vec3 separation = Vec3.ZERO;
        Vec3 alignment = Vec3.ZERO;
        Vec3 kinCentre = Vec3.ZERO;
        Vec3 everyone = Vec3.ZERO;
        int alignCount = 0;
        int kinCount = 0;
        int allCount = 0;
        Map<UUID, Integer> votes = new HashMap<>();

        for (Mob mate : mates) {
            if (!isValid(mate)) continue;
            Vec3 offset = pos.subtract(mate.position());
            double dist = offset.length();
            if (dist > reach) continue;

            SchoolState mateState = SchoolState.peek(mate);
            boolean mateLost = mateState != null && mateState.isLost(now);
            UUID mateSchool = mateState != null ? mateState.schoolIdOrSelf() : mate.getUUID();

            if (dist < SEPARATION_DISTANCE) {
                if (dist < 1.0E-3D) {
                    offset = new Vec3(fish.getRandom().nextDouble() - 0.5D, 0.0D, fish.getRandom().nextDouble() - 0.5D);
                    dist = 0.5D;
                }
                double d = Math.max(dist, 0.15D);
                separation = separation.add(offset.scale(1.0D / (d * d)));
            }

            everyone = everyone.add(mate.position());
            allCount++;

            boolean kin = !lost && !mateLost && mateSchool.equals(mySchool);
            if (kin) {
                if (dist <= cohesionRadius) {
                    kinCentre = kinCentre.add(mate.position());
                    kinCount++;
                }
                if (dist <= alignRadius) {
                    // relative to the water: a mate's ground velocity includes the current, and fish steer relative to
                    // the water, so using it directly would count the current twice and compound every round
                    alignment = alignment.add(mate.getDeltaMovement().subtract(current));
                    alignCount++;
                }
            }
            if (!mateLost && dist <= JOIN_RADIUS) votes.merge(mateSchool, 1, Integer::sum);
        }

        // ---- membership: join the school most of your close neighbours belong to. Ties go to the lower id, so two fish
        // that meet merge in one direction instead of swapping schools forever. ----
        if (!lost && now - state.getLastSwitchTick() > SWITCH_COOLDOWN_TICKS && !votes.isEmpty()) {
            int kinNear = votes.getOrDefault(mySchool, 0);
            UUID bestId = null;
            int bestCount = -1;
            for (Map.Entry<UUID, Integer> entry : votes.entrySet()) {
                if (entry.getKey().equals(mySchool)) continue;
                int c = entry.getValue();
                if (c > bestCount || (c == bestCount && entry.getKey().compareTo(bestId) < 0)) {
                    bestId = entry.getKey();
                    bestCount = c;
                }
            }
            if (bestId != null && (bestCount > kinNear || (bestCount == kinNear && bestId.compareTo(mySchool) < 0))) {
                state.setSchoolId(bestId, now);
                mySchool = bestId;
            }
        }

        // ---- every so often a fish simply wanders off ----
        if (!lost) {
            state.maybeDefect(now);
            if (state.isLost(now)) {
                lost = true;
                mySchool = state.schoolIdOrSelf();
                kinCount = 0;
                alignCount = 0;
            }
        }

        // ---- cohesion ----
        Vec3 cohesion = Vec3.ZERO;
        double distToCentre = 0.0D;
        if (kinCount > 0) {
            Vec3 toCentre = kinCentre.scale(1.0D / kinCount).subtract(pos);
            distToCentre = toCentre.length();
            if (distToCentre > COHESION_DEADZONE) {
                cohesion = toCentre.scale(1.0D / distToCentre)
                        .scale(Math.min(1.0D, (distToCentre - COHESION_DEADZONE) / 6.0D));
            }
        } else if (regrouping && state.getRally() != null) {
            // nobody of my school in sight after a scare: head for where the school was
            Vec3 toRally = state.getRally().subtract(pos);
            distToCentre = toRally.length();
            if (distToCentre > 1.5D) cohesion = toRally.scale(1.0D / distToCentre);
        }
        // no schoolmates at all but other fish around (and not lost): look for company
        if (!lost && kinCount == 0 && allCount > 0) {
            Vec3 toAll = everyone.scale(1.0D / allCount).subtract(pos);
            distToCentre = toAll.length();
            cohesion = distToCentre > 1.0E-6D ? toAll.scale(SEEK_WEIGHT / distToCentre) : Vec3.ZERO;
        }
        alignment = alignCount > 0 && alignment.lengthSqr() > 1.0E-8D ? alignment.normalize() : Vec3.ZERO;

        double sepLen = separation.length();
        separation = sepLen > 1.0E-4D ? separation.scale(Math.min(1.0D, sepLen) / sepLen) : Vec3.ZERO;

        // ---- an uneasy player or mob nearby: make room ----
        Vec3 avoid = Vec3.ZERO;
        Vec3 threat = state.getThreatPos();
        if (alert > 0.02D && threat != null) {
            Vec3 away = pos.subtract(threat);
            if (away.lengthSqr() > 1.0E-6D) avoid = away.normalize().scale(alert * 2.4D);
        }

        double wCohesion = (regrouping ? 0.10D + 1.30D * progress : 0.90D) * (1.0D + alert);
        double wAlignment = (regrouping ? 0.30D + 0.70D * progress : 1.00D) * ALIGN_WEIGHT;
        double wWander = regrouping ? 0.15D + 0.40D * progress : 0.55D;
        if (lost) {          // a lost fish follows a path of its own and ignores the school it left
            wWander = 1.0D;
            wCohesion = 0.0D;
        }

        UUID pathKey = lost ? fish.getUUID() : mySchool;
        Vec3 wander = SchoolSteering.rotateY(schoolWander(pathKey, now), state.getWanderOffset());
        Vec3 individuality = state.individualMeander(now).scale(INDIVIDUALITY_WEIGHT);
        Vec3 depth = new Vec3(0.0D, SchoolSteering.verticalBias(fish), 0.0D);

        // ---- face upstream when the current is strong ----
        double flow = Math.hypot(current.x, current.z);
        Vec3 upstream = flow > 0.004D
                ? new Vec3(-current.x / flow, 0.0D, -current.z / flow).scale(Mth.clamp(flow / RHEOTAXIS_FULL_FLOW, 0.0D, 1.0D) * RHEOTAXIS_WEIGHT)
                : Vec3.ZERO;

        Vec3 sum = separation.scale(1.6D)
                .add(alignment.scale(wAlignment))
                .add(cohesion.scale(wCohesion))
                .add(wander.scale(wWander))
                .add(individuality)
                .add(upstream)
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
     * One slowly curving path per school. Every fish with the same school id computes the same direction at the same time,
     * so the group travels together with no communication. The path loops, so schools stay roughly in one area.
     */
    private static Vec3 schoolWander(UUID key, long now) {
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
