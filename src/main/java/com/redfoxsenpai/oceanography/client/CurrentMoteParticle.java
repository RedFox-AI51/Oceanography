package com.redfoxsenpai.oceanography.client;

import com.redfoxsenpai.oceanography.current.ModParticles;
import com.redfoxsenpai.oceanography.current.OceanCurrents;
import com.redfoxsenpai.oceanography.current.CurrentField;
import com.redfoxsenpai.oceanography.current.OceanographyConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;

/**
 * One piece of the current-line effect. There are two kinds, told apart by how they are spawned:
 *
 *  head  - spawned with the water's velocity. It follows the real current field at STREAK_SPEED times the water's speed,
 *          so it races along the flow lines, and it drops a trail dot behind itself every tick.
 *  trail - spawned with no velocity. It sits where it was dropped and fades out.
 *
 * A fast head and a fading trail together read as a streak pointing the way the water runs; the faster the water, the longer
 * and brighter the streak. Heads vanish when they leave the water or reach still water.
 */
public class CurrentMoteParticle extends TextureSheetParticle {

    /** Heads move this many times faster than the water, which is what turns drifting specks into visible lines. */
    public static final double STREAK_SPEED = 8.0D;
    /** Minimum flow for fast-current particles, read from the mod config. */
    public static double fastCurrentThreshold() {
        return OceanographyConfig.fastCurrentThresholdBlocksPerTick();
    }
    public static final int HEAD_LIFETIME = 80;

    private static final int TRAIL_LIFETIME = 18;
    private static final int MAX_TRAIL_DOTS_PER_TICK = 16;

    private final boolean trail;
    private final boolean shell;

    protected CurrentMoteParticle(ClientLevel level, double x, double y, double z, boolean trail, SpriteSet sprites) {
        this(level, x, y, z, trail, false, sprites);
    }

    protected CurrentMoteParticle(ClientLevel level, double x, double y, double z, boolean trail, boolean shell, SpriteSet sprites) {
        super(level, x, y, z);
        this.trail = trail;
        this.shell = shell;
        this.pickSprite(sprites);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.lifetime = shell ? 50 : trail ? TRAIL_LIFETIME : HEAD_LIFETIME - 10 + this.random.nextInt(20);
        this.quadSize = shell ? 0.14F : trail ? 0.09F : 0.12F;
        this.setColor(0.75F, 0.93F, 1.0F);
        this.setAlpha(shell ? 0.9F : trail ? 1.0F : 0.0F);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        if (trail) {
            this.setAlpha(1.0F - (float) this.age / (float) this.lifetime);
            return;
        }

        Vec3 water = OceanCurrents.at(this.level, this.x, this.y, this.z);
        boolean inWater = this.level.getFluidState(BlockPos.containing(this.x, this.y, this.z)).is(FluidTags.WATER);
        double threshold = fastCurrentThreshold();
        if (!inWater || Math.hypot(water.x, water.z) < threshold) {
            this.remove();
            return;
        }

        if (shell) {
            double fadeIn = Math.min(1.0D, this.age / 8.0D);
            double fadeOut = Math.min(1.0D, (double) (this.lifetime - this.age) / 12.0D);
            this.setAlpha((float) (0.9D * fadeIn * fadeOut));
        }

        double dx = water.x * STREAK_SPEED;
        double dz = water.z * STREAK_SPEED;

        // Strong currents leave a denser trail; weak currents still leave a single dot per tick.
        double thresholdRange = CurrentField.MAX_SPEED - threshold;
        double strength = thresholdRange > 0.0D
                ? Math.max(0.0D, Math.min(1.0D,
                        (Math.hypot(water.x, water.z) - threshold) / thresholdRange))
                : 1.0D;
        int dots = 1 + (int) Math.round(strength * (MAX_TRAIL_DOTS_PER_TICK - 1));
        for (int i = 1; i <= dots; i++) {
            double f = (double) i / dots;
            this.level.addParticle(ModParticles.CURRENT_MOTE.get(), this.x + dx * f, this.y, this.z + dz * f, 0.0D, 0.0D, 0.0D);
        }

        this.xd = dx;
        this.yd = 0.0D;
        this.zd = dz;
        this.move(dx, 0.0D, dz);

        float fadeIn = Math.min(1.0F, (float) this.age / 6.0F);
        float fadeOut = Math.min(1.0F, (float) (this.lifetime - this.age) / 10.0F);
        this.setAlpha(Math.max(0.0F, fadeIn * fadeOut));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Always fully lit, so the lines stay visible in the dark of deep water. */
    @Override
    public int getLightColor(float partialTick) {
        return 15728880;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            boolean trail = xSpeed == 0.0D && ySpeed == 0.0D && zSpeed == 0.0D;
            boolean shell = ySpeed == 1.0D;
            return new CurrentMoteParticle(level, x, y, z, trail, shell, sprites);
        }
    }
}
