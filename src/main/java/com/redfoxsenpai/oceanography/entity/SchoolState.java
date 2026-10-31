package com.redfoxsenpai.oceanography.entity;

import com.redfoxsenpai.oceanography.current.OceanCurrents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The "nerves" of one fish: what it is afraid of, how long it keeps running, and how it drifts back to its school.
 *
 * Every fish that gets our goals owns one SchoolState. The goals read it, and the outside world (players walking up,
 * splashes, explosions, panicking neighbours) writes to it. All the numbers worth tuning are at the top.
 *
 * Life cycle of a scare:
 *   CALM -> (alert: a player is close but slow, or a faint noise) -> PANIC -> REGROUP -> CALM
 *   PANIC runs away from the threat; if it is strong enough the school SCATTERS (every fish picks its own escape
 *   route). REGROUP lets cohesion ramp back up from nearly nothing, so fish drift back together instead of snapping.
 */
public final class SchoolState {

    // ---------------------------------------------------------------- tuning ----------------------------------------
    /** A threat at or above this makes the fish bolt. Below it the fish just becomes uneasy ("alert"). */
    public static final float PANIC_THRESHOLD = 0.45F;
    /** A threat at or above this breaks the school apart; every fish takes its own escape route. */
    public static final float SCATTER_THRESHOLD = 0.80F;

    /** How far away fish notice a player at all. */
    static final double NOTICE_RADIUS = 14.0D;
    /** Inside this distance a player makes fish drift politely out of the way, even if they are standing still. */
    static final double PERSONAL_SPACE = 5.0D;
    /** Closing speed (blocks per tick) that counts as "charging the school": about a sprint on land (0.28 = 5.6 b/s). */
    static final double FAST_APPROACH = 0.24D;
    /** Moving this close always spooks fish (unless the player is sneaking). */
    static final double CLOSE_RADIUS = 2.0D;

    /** Other mobs (anything that is not a water creature) are noticed from this far away. */
    static final double MOB_NOTICE_RADIUS = 12.0D;

    // ---- schools breaking apart and re-forming ------------------------------------------------------------------------
    /**
     * When true, a severe scare can make individual fish "lost": they leave their school, wander alone, and later join
     * whatever school they find (or start a new one with other lost fish). When false, scattered fish always regroup.
     */
    public static final boolean SCHOOLS_BREAK_APART = true;
    /** A scare must be stronger than this to lose any fish; the chance rises with the strength, up to LOST_CAP. */
    static final float LOST_THRESHOLD = 0.60F;
    static final float LOST_SCALE = 2.0F;
    static final float LOST_CAP = 0.85F;
    static final int LOST_MIN_TICKS = 200;           // 10 s
    static final int LOST_EXTRA_TICKS = 800;         // up to 40 s more
    /** Every so often a fish drifts off on its own anyway (per steering step; one step is 2 ticks). */
    static final double DEFECT_CHANCE_PER_STEER = 1.0D / 3000.0D;
    static final int DEFECT_MIN_TICKS = 100;
    static final int DEFECT_EXTRA_TICKS = 200;

    /** How much of a scare is passed on to neighbours, per hop. Panic spreads through the school like a ripple. */
    static final float CONTAGION_DECAY = 0.85F;
    static final double CONTAGION_RADIUS = 7.0D;
    /** Extra ticks of delay per block of distance, so the ripple is visible. */
    static final double RIPPLE_TICKS_PER_BLOCK = 1.5D;

    private static final int SENSE_INTERVAL = 4;
    private static final int NEIGHBOUR_REFRESH_TICKS = 8;
    private static final int MAX_NEIGHBOURS = 20;

    // ---------------------------------------------------------------- registry --------------------------------------
    private static final Map<Mob, SchoolState> STATES = Collections.synchronizedMap(new WeakHashMap<>());

    public static SchoolState of(Mob mob) {
        return STATES.computeIfAbsent(mob, SchoolState::new);
    }

    @Nullable
    public static SchoolState peek(Mob mob) {
        return STATES.get(mob);
    }

    public static boolean isTracked(Mob mob) {
        return STATES.containsKey(mob);
    }

    // ---------------------------------------------------------------- state -----------------------------------------
    // Weak on purpose: this object is the value in a WeakHashMap keyed by the mob, so it must not keep the mob alive.
    private final WeakReference<Mob> ref;

    private boolean installed;
    private final float speedFactor;

    private long lastUpdate = Long.MIN_VALUE;
    private long nextSense;

    private float alert;                    // 0..1 uneasy, not running
    private int panicTicks;                 // >0 while a scare is running its course
    private int startDelay;                 // ticks before a passed-on scare actually starts (the ripple)
    private float panicIntensity;           // 0..1
    private boolean scatter;
    @Nullable private Vec3 threatPos;
    private double fleeJitter;              // this fish's personal angle off the straight-away line, radians
    private double fleeDive;                // this fish's personal vertical lean while fleeing

    private int regroupLeft;
    private int regroupTotal = 1;
    @Nullable private Vec3 rally;           // where the school was when it broke, for fish that find nobody nearby

    @Nullable private UUID schoolId;
    private long lostUntil;
    private long lastSwitchTick = Long.MIN_VALUE / 2;

    // Each fish is a little different, so even fish in one school never move in perfect lockstep.
    private final double wanderOffset;      // personal angle off the school's shared path, radians
    private final double phase;
    private final double phase2;
    private final int meanderDirection;

    private long lastSteerTick = Long.MIN_VALUE / 2;
    private Vec3 currentCache = Vec3.ZERO;
    private long currentCacheTick = Long.MIN_VALUE / 2;
    private long nextMobSense;

    @Nullable private UUID lastPlayer;
    @Nullable private Vec3 lastPlayerPos;
    private long lastPlayerTick;

    private List<Mob> neighbourCache = List.of();
    private long neighbourCacheTick = Long.MIN_VALUE;
    private double neighbourCacheRadius;
    private double queryRadius;

    private SchoolState(Mob mob) {
        this.ref = new WeakReference<>(mob);
        this.speedFactor = 0.92F + mob.getRandom().nextFloat() * 0.16F;
        this.wanderOffset = (mob.getRandom().nextDouble() - 0.5D) * 0.6D;
        this.phase = mob.getRandom().nextDouble() * Math.PI * 2.0D;
        this.phase2 = mob.getRandom().nextDouble() * Math.PI * 2.0D;
        this.meanderDirection = mob.getRandom().nextInt(2) == 0 ? 1 : -1;
    }

    @Nullable
    private Mob mob() {
        Mob mob = ref.get();
        return mob == null || mob.isRemoved() ? null : mob;
    }

    /** First caller gets true; used so goals are never added twice to the same fish. */
    public boolean markInstalled() {
        if (installed) return false;
        installed = true;
        return true;
    }

    // ---------------------------------------------------------------- read by the goals ------------------------------
    public boolean isPanicking() { return panicTicks > 0 && startDelay <= 0; }
    public boolean isScatter() { return scatter; }
    public boolean isRegrouping() { return regroupLeft > 0; }
    public float getAlert() { return alert; }
    public float getPanicIntensity() { return panicIntensity; }
    public float getSpeedFactor() { return speedFactor; }
    public double getFleeJitter() { return fleeJitter; }
    public double getFleeDive() { return fleeDive; }
    @Nullable public Vec3 getThreatPos() { return threatPos; }
    @Nullable public Vec3 getRally() { return rally; }
    @Nullable public UUID getSchoolId() { return schoolId; }
    public double getWanderOffset() { return wanderOffset; }
    public long getLastSwitchTick() { return lastSwitchTick; }
    public long getLastSteerTick() { return lastSteerTick; }

    /** The school this fish belongs to; a fish with no school is its own school of one. */
    public UUID schoolIdOrSelf() {
        if (schoolId != null) return schoolId;
        Mob mob = mob();
        return mob != null ? mob.getUUID() : new UUID(0L, 0L);
    }

    public void setSchoolId(UUID id, long now) {
        this.schoolId = id;
        this.lastSwitchTick = now;
    }

    public boolean isLost(long now) { return now < lostUntil; }

    /** Leave the school and wander alone for a while. A lost fish is a school of one, with a path of its own. */
    public void becomeLost(long now, int ticks) {
        Mob mob = mob();
        if (mob == null) return;
        lostUntil = now + ticks;
        schoolId = mob.getUUID();
        lastSwitchTick = now;
        regroupLeft = 0;
        rally = null;
    }

    /** Now and then a fish simply wanders off. */
    public void maybeDefect(long now) {
        Mob mob = mob();
        if (mob == null || isLost(now)) return;
        if (mob.getRandom().nextDouble() < DEFECT_CHANCE_PER_STEER) {
            becomeLost(now, DEFECT_MIN_TICKS + mob.getRandom().nextInt(DEFECT_EXTRA_TICKS));
        }
    }

    /** A slow personal meander, added to a fish's heading so that no two fish are ever exactly parallel. */
    public Vec3 individualMeander(long now) {
        double a = phase + 0.0035D * now * meanderDirection + 0.8D * Math.sin(now / 90.0D + phase2);
        return new Vec3(Math.cos(a), 0.0D, Math.sin(a));
    }

    /** Called by the swimming goals every tick they steer the fish, so other systems know it is under its own power. */
    public void markSteered(long now) { lastSteerTick = now; }

    /** The ocean current at the fish, refreshed every few ticks (it changes slowly). */
    public Vec3 current(Mob mob, long now) {
        if (now - currentCacheTick >= 5L) {
            currentCache = OceanCurrents.at(mob.level(), mob.getX(), mob.getY(), mob.getZ());
            currentCacheTick = now;
        }
        return currentCache;
    }

    /** 0 right after a scare, ramping to 1 when the fish has fully recovered. Always 1 when not regrouping. */
    public double regroupProgress() {
        return regroupLeft <= 0 ? 1.0D : 1.0D - (double) regroupLeft / (double) regroupTotal;
    }

    /** Shake this fish's escape heading a little; scattering fish do this every few ticks so they dart about. */
    public void nudgeFleeJitter(double amount) {
        Mob mob = mob();
        if (mob == null) return;
        fleeJitter = Mth.clamp(fleeJitter + (mob.getRandom().nextDouble() - 0.5D) * amount, -1.6D, 1.6D);
    }

    /**
     * Other fish of the same kind within the given radius. The expensive query is cached for a few ticks at the widest
     * radius anyone has asked for, and each caller gets back only the fish actually inside the radius it asked for.
     */
    public List<Mob> neighbours(double radius, long now) {
        Mob mob = mob();
        if (mob == null) return List.of();

        queryRadius = Math.max(queryRadius, radius);
        if (now - neighbourCacheTick >= NEIGHBOUR_REFRESH_TICKS || neighbourCacheRadius < queryRadius - 0.1D) {
            AABB box = mob.getBoundingBox().inflate(queryRadius);
            List<Mob> found = mob.level().getEntitiesOfClass(Mob.class, box,
                    other -> other != mob && other.isAlive() && other.getType() == mob.getType());
            if (found.size() > MAX_NEIGHBOURS) found = new ArrayList<>(found.subList(0, MAX_NEIGHBOURS));
            neighbourCache = found;
            neighbourCacheTick = now;
            neighbourCacheRadius = queryRadius;
        }

        if (radius >= neighbourCacheRadius - 0.1D) return neighbourCache;
        List<Mob> inside = new ArrayList<>(neighbourCache.size());
        double r2 = radius * radius;
        for (Mob other : neighbourCache) {
            if (!other.isRemoved() && mob.distanceToSqr(other) <= r2) inside.add(other);
        }
        return inside;
    }

    // ---------------------------------------------------------------- per-tick update --------------------------------
    /** Safe to call from several goals in the same tick; only the first call does any work. */
    public void update(long now) {
        if (now == lastUpdate) return;
        Mob mob = mob();
        if (mob == null) return;

        long dt = lastUpdate == Long.MIN_VALUE ? 1L : Math.min(20L, now - lastUpdate);
        lastUpdate = now;

        if (startDelay > 0) {
            startDelay -= (int) dt;
        } else if (panicTicks > 0) {
            panicTicks -= (int) dt;
            if (panicTicks <= 0) endPanic(mob);
        } else if (regroupLeft > 0) {
            regroupLeft -= (int) dt;
        }

        alert = Math.max(0.0F, alert - 0.02F * dt);

        if (now >= nextSense) {
            nextSense = now + SENSE_INTERVAL + mob.getRandom().nextInt(3);
            sensePlayers(mob, now);
        }
        if (now >= nextMobSense) {
            nextMobSense = now + 8 + mob.getRandom().nextInt(5);
            senseMobs(mob);
        }
    }

    // ---------------------------------------------------------------- scares ----------------------------------------
    /** A noise or other event. Louder than PANIC_THRESHOLD makes the fish bolt; fainter just unsettles it. */
    public void hear(Vec3 source, float intensity) {
        directThreat(source, intensity);
    }

    /** End a scare early (for example the fish has outrun the danger) and start the swim back to the school. */
    public void calmDown() {
        Mob mob = mob();
        if (mob != null && panicTicks > 0) endPanic(mob);
    }

    private void directThreat(Vec3 from, float intensity) {
        Mob mob = mob();
        if (mob == null) return;

        if (intensity < PANIC_THRESHOLD) {
            alert = Math.max(alert, intensity);
            if (!isPanicking()) threatPos = from;
            return;
        }

        if (panicTicks > 0) {
            // Still running: keep running while the danger is there, and escalate if it got worse.
            threatPos = from;
            panicTicks = Math.max(panicTicks, 25 + (int) (60.0F * intensity));
            if (intensity > panicIntensity) {
                panicIntensity = intensity;
                if (intensity >= SCATTER_THRESHOLD && !scatter) {
                    scatter = true;
                    fleeJitter = (mob.getRandom().nextDouble() - 0.5D) * 2.6D;
                }
            }
            return;
        }

        beginPanic(mob, from, intensity, 0, true);
    }

    /** A scare passed on by a neighbour that is already running. */
    private void contagion(Vec3 from, float intensity, int delay) {
        Mob mob = mob();
        if (mob == null) return;

        if (intensity < PANIC_THRESHOLD) {
            alert = Math.max(alert, intensity);
            if (!isPanicking()) threatPos = from;
            return;
        }
        if (panicTicks > 0 && intensity <= panicIntensity + 0.05F) return;   // already running at least this hard

        beginPanic(mob, from, intensity, delay, intensity * CONTAGION_DECAY >= PANIC_THRESHOLD);
    }

    private void beginPanic(Mob mob, Vec3 from, float intensity, int delay, boolean propagate) {
        RandomSource random = mob.getRandom();

        threatPos = from;
        panicIntensity = intensity;
        startDelay = delay;
        scatter = intensity >= SCATTER_THRESHOLD;
        panicTicks = 35 + (int) (80.0F * intensity) + random.nextInt(20);
        fleeJitter = (random.nextDouble() - 0.5D) * 2.0D * (scatter ? 1.3D : 0.3D);
        fleeDive = -0.35D + random.nextDouble() * 0.45D;
        regroupLeft = 0;
        rally = schoolCentre(mob);

        if (propagate) {
            long now = mob.level().getGameTime();
            for (Mob other : neighbours(CONTAGION_RADIUS, now)) {
                if (other == mob || !other.isAlive()) continue;
                SchoolState otherState = peek(other);
                if (otherState == null) continue;
                int rippleDelay = delay + 1 + (int) (mob.distanceTo(other) * RIPPLE_TICKS_PER_BLOCK);
                otherState.contagion(from, intensity * CONTAGION_DECAY, rippleDelay);
            }
        }
    }

    private void endPanic(Mob mob) {
        boolean wasScatter = scatter;
        float intensity = panicIntensity;
        panicTicks = 0;
        startDelay = 0;
        scatter = false;
        panicIntensity = 0.0F;

        // After a severe scare some fish never find their way back: they are lost, and wander until they find company.
        if (SCHOOLS_BREAK_APART && wasScatter
                && mob.getRandom().nextFloat() < Mth.clamp((intensity - LOST_THRESHOLD) * LOST_SCALE, 0.0F, LOST_CAP)) {
            becomeLost(mob.level().getGameTime(), LOST_MIN_TICKS + mob.getRandom().nextInt(LOST_EXTRA_TICKS));
            return;
        }
        regroupTotal = (wasScatter ? 220 : 120) + mob.getRandom().nextInt(120);
        regroupLeft = regroupTotal;
    }

    @Nullable
    private Vec3 schoolCentre(Mob mob) {
        Vec3 sum = mob.position();
        int count = 1;
        for (Mob other : neighbours(12.0D, mob.level().getGameTime())) {
            if (!other.isAlive()) continue;
            sum = sum.add(other.position());
            count++;
        }
        return sum.scale(1.0D / count);
    }

    // ---------------------------------------------------------------- senses ----------------------------------------
    private void sensePlayers(Mob mob, long now) {
        Player player = mob.level().getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(),
                NOTICE_RADIUS, EntitySelector.NO_SPECTATORS);
        if (player == null) {
            lastPlayer = null;
            lastPlayerPos = null;
            return;
        }

        Vec3 playerPos = player.position().add(0.0D, player.getBbHeight() * 0.5D, 0.0D);

        // The server doesn't track a player's velocity reliably, so measure it between our own samples.
        Vec3 velocity = Vec3.ZERO;
        if (player.getUUID().equals(lastPlayer) && lastPlayerPos != null) {
            long dt = Math.max(1L, now - lastPlayerTick);
            velocity = playerPos.subtract(lastPlayerPos).scale(1.0D / dt);
            if (velocity.lengthSqr() > 4.0D) velocity = Vec3.ZERO;          // 2+ blocks per tick is a teleport
        }
        lastPlayer = player.getUUID();
        lastPlayerPos = playerPos;
        lastPlayerTick = now;

        Vec3 toFish = mob.position().subtract(playerPos);
        double dist = Math.max(toFish.length(), 1.0E-3D);
        double approach = Math.max(0.0D, velocity.dot(toFish.scale(1.0D / dist)));   // blocks/tick toward the fish
        double speed = velocity.length();

        boolean sneaking = player.isCrouching();
        double loudness = sneaking ? 0.5D : (player.isSprinting() || player.isSwimming() ? 1.15D : 1.0D);

        // Charging at the school is what causes panic: nearer and faster is worse.
        double proximity = Mth.clamp(1.0D - dist / NOTICE_RADIUS, 0.0D, 1.0D);
        double rush = Math.min(1.5D, approach / FAST_APPROACH);
        double threat = proximity * rush * 1.3D * loudness;

        // Barging right through the school spooks it even from the side.
        if (dist < CLOSE_RADIUS && speed > 0.04D) threat = Math.max(threat, 0.5D * loudness + 0.1D);

        // Slowly drifting up is fine: fish just make room.
        double polite = Mth.clamp(1.0D - dist / PERSONAL_SPACE, 0.0D, 1.0D) * (sneaking ? 0.5D : 1.0D);
        if (polite > 0.04D && !isPanicking()) {
            alert = Math.max(alert, (float) polite);
            threatPos = playerPos;
        }

        if (threat >= PANIC_THRESHOLD) directThreat(playerPos, (float) Math.min(1.0D, threat));
    }

    /**
     * Fish react to every other mob except water creatures, the same way they react to a player: a mob charging at the
     * school makes it bolt, one wandering close just makes them move over. Bigger mobs are scarier, and a mob in the
     * water is more alarming than one on the shore.
     */
    private void senseMobs(Mob self) {
        List<Mob> others = self.level().getEntitiesOfClass(Mob.class, self.getBoundingBox().inflate(MOB_NOTICE_RADIUS),
                other -> other != self && other.isAlive() && !isWaterCreature(other));
        if (others.isEmpty()) return;

        double bestThreat = 0.0D;
        Vec3 bestPos = null;
        double bestPolite = 0.0D;
        Vec3 politePos = null;

        for (Mob other : others) {
            Vec3 otherPos = other.position().add(0.0D, other.getBbHeight() * 0.5D, 0.0D);
            Vec3 toFish = self.position().subtract(otherPos);
            double dist = Math.max(toFish.length(), 1.0E-3D);
            Vec3 velocity = other.getDeltaMovement();
            double speed = velocity.length();
            double approach = Math.max(0.0D, velocity.dot(toFish.scale(1.0D / dist)));      // blocks/tick toward the fish

            double size = Mth.clamp(Math.sqrt(other.getBbWidth() * other.getBbHeight()) / 0.9D, 0.3D, 1.6D);
            double medium = other.isInWater() ? 1.0D : 0.6D;

            double proximity = Mth.clamp(1.0D - dist / MOB_NOTICE_RADIUS, 0.0D, 1.0D);
            double rush = Math.min(1.5D, approach / FAST_APPROACH);
            double threat = proximity * rush * 1.3D * size * medium;
            if (dist < CLOSE_RADIUS * size && speed > 0.04D) threat = Math.max(threat, (0.5D * size + 0.1D) * medium);

            double polite = Mth.clamp(1.0D - dist / (PERSONAL_SPACE * size), 0.0D, 1.0D) * medium * 0.8D;
            if (polite > bestPolite) {
                bestPolite = polite;
                politePos = otherPos;
            }
            if (threat > bestThreat) {
                bestThreat = threat;
                bestPos = otherPos;
            }
        }

        if (bestPolite > 0.04D && !isPanicking() && politePos != null) {
            alert = Math.max(alert, (float) bestPolite);
            threatPos = politePos;
        }
        if (bestPos != null && bestThreat >= PANIC_THRESHOLD) directThreat(bestPos, (float) Math.min(1.0D, bestThreat));
    }

    private static boolean isWaterCreature(Mob other) {
        MobCategory category = other.getType().getCategory();
        return category == MobCategory.WATER_CREATURE
                || category == MobCategory.WATER_AMBIENT
                || category == MobCategory.UNDERGROUND_WATER_CREATURE
                || category == MobCategory.AXOLOTLS;
    }
}
