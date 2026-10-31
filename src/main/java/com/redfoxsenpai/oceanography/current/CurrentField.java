package com.redfoxsenpai.oceanography.current;

/**
 * The ocean current field: how fast, and which way, the water is moving at any point and moment.
 *
 * This class has no Minecraft dependencies on purpose. The server (to push fish, items and boats) and the client (to push
 * the player) must compute exactly the same answer for the same position and time, and keeping it pure Java means it can
 * be tested on its own.
 *
 * How it is built:
 *  - Two layers of smooth noise make a "stream function" psi(x, z, t), a landscape of hills and hollows.
 *  - The water flows ALONG the contour lines of that landscape (velocity = psi rotated 90 degrees). Flow built this way is
 *    divergence-free: no sources or sinks, so it forms closed gyres, eddies and meandering streams like real circulation.
 *  - The landscape is reshaped so that the contour between a hill and a hollow (psi near 0) is steep: that is where narrow,
 *    fast jets run, like the boundary currents between ocean gyres. Reshaping psi itself keeps the flow divergence-free.
 *  - The pattern drifts slowly over time (tens of minutes) so currents meander instead of staying fixed.
 *  - Strength fades with depth and reverses direction in deep water, like the real surface current and deep return flow.
 *
 * Everything here is deterministic: the same inputs always give the same flow, in every world. (Making it depend on the
 * world seed would need the seed sent to clients; the SEED constant below is the one place to change that.)
 */
public final class CurrentField {

    private CurrentField() {}

    // ---------------------------------------------------------------- tuning --------------------------------------------
    /** Hard ceiling on speed, in blocks per tick (0.08 = 1.6 blocks per second). Only ever reached in the strongest jets. */
    public static final double MAX_SPEED = 0.08D;
    /** Overall strength: blocks per tick for a typical slope of the landscape. */
    private static final double SPEED_SCALE = 0.0105D;
    /** Horizontal size of the big gyres and of the smaller eddies, in blocks. */
    private static final double SCALE_LARGE = 1800.0D;
    private static final double SCALE_SMALL = 650.0D;
    /** Ticks for the pattern to change completely (36000 = 30 minutes). */
    private static final double TIME_SCALE = 36000.0D;
    /** How much faster the water runs along the hill/hollow boundary than elsewhere, and how wide that boundary is. */
    private static final double JET_BOOST = 3.0D;
    private static final double JET_WIDTH = 0.16D;
    /** Depth over which the surface flow dies away, and the wavelength of its reversal, in blocks. */
    private static final double DEPTH_DECAY = 80.0D;
    private static final double DEPTH_REVERSAL = 70.0D;

    private static final long SEED = 0x0CEA11C0DEL;
    private static final double STEP = 6.0D;        // finite-difference step for the derivatives, in blocks


    // ---------------------------------------------------------------- public API ---------------------------------------
    /**
     * Writes the water velocity (blocks per tick) at a position into out[0..2] (x, y, z). Currents are horizontal, so
     * out[1] is always 0.
     *
     * @param gameTime world time in ticks
     * @param seaLevel y of the sea surface; depth is measured down from it
     */
    public static void sample(double x, double y, double z, long gameTime, int seaLevel, double[] out) {
        double t = gameTime / TIME_SCALE;

        // slope of the reshaped landscape, in "per SCALE_LARGE" units so numbers stay around 1
        double dpsiDx = (shaped(x + STEP, z, t) - shaped(x - STEP, z, t)) / (2.0D * STEP) * SCALE_LARGE;
        double dpsiDz = (shaped(x, z + STEP, t) - shaped(x, z - STEP, t)) / (2.0D * STEP) * SCALE_LARGE;

        // flow runs along contours: rotate the slope 90 degrees
        double vx = dpsiDz * SPEED_SCALE;
        double vz = -dpsiDx * SPEED_SCALE;

        double depth = Math.max(0.0D, seaLevel - y);
        double layer = Math.exp(-depth / DEPTH_DECAY) * Math.cos(depth / DEPTH_REVERSAL);
        vx *= layer;
        vz *= layer;

        // safety ceiling; the tuning above keeps this to a handful of points in the strongest jets
        double speed = Math.sqrt(vx * vx + vz * vz);
        if (speed > MAX_SPEED) {
            double k = MAX_SPEED / speed;
            vx *= k;
            vz *= k;
        }
        out[0] = vx;
        out[1] = 0.0D;
        out[2] = vz;
    }

    /** The surface-to-depth strength multiplier on its own (1 at the surface, negative where the flow reverses). */
    public static double depthFactor(double depthBelowSurface) {
        double d = Math.max(0.0D, depthBelowSurface);
        return Math.exp(-d / DEPTH_DECAY) * Math.cos(d / DEPTH_REVERSAL);
    }

    // ---------------------------------------------------------------- the landscape ------------------------------------
    /**
     * The landscape after reshaping: psi + JET_BOOST*JET_WIDTH*tanh(psi/JET_WIDTH). Its slope is (1 + JET_BOOST) times
     * steeper along psi = 0 and returns to normal away from it, so the strongest flow is a narrow stream along the
     * boundary between a hill and a hollow. Because it is still a function of position only, flow along its contours stays
     * exactly divergence-free.
     */
    static double shaped(double x, double z, double t) {
        double p = psi(x, z, t);
        return p + JET_BOOST * JET_WIDTH * Math.tanh(p / JET_WIDTH);
    }

    static double psi(double x, double z, double t) {
        return noise(x / SCALE_LARGE, z / SCALE_LARGE, t)
                + 0.45D * noise(x / SCALE_SMALL + 17.3D, z / SCALE_SMALL - 9.1D, t / 0.6D + 3.7D);
    }

    /** Smooth value noise in 3D (x, z, time), range roughly -1..1, with continuous first and second derivatives. */
    static double noise(double x, double y, double z) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        double fx = x - x0, fy = y - y0, fz = z - z0;
        double ux = fade(fx), uy = fade(fy), uz = fade(fz);

        double c000 = lattice(x0, y0, z0),         c100 = lattice(x0 + 1, y0, z0);
        double c010 = lattice(x0, y0 + 1, z0),     c110 = lattice(x0 + 1, y0 + 1, z0);
        double c001 = lattice(x0, y0, z0 + 1),     c101 = lattice(x0 + 1, y0, z0 + 1);
        double c011 = lattice(x0, y0 + 1, z0 + 1), c111 = lattice(x0 + 1, y0 + 1, z0 + 1);

        double x00 = lerp(ux, c000, c100), x10 = lerp(ux, c010, c110);
        double x01 = lerp(ux, c001, c101), x11 = lerp(ux, c011, c111);
        return lerp(uz, lerp(uy, x00, x10), lerp(uy, x01, x11));
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0D - 15.0D) + 10.0D);     // quintic smoothstep
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    /** Pseudo-random value in -1..1 at an integer lattice point. */
    private static double lattice(int x, int y, int z) {
        long h = SEED;
        h ^= x * 0x9E3779B97F4A7C15L;
        h = Long.rotateLeft(h, 27) * 0xBF58476D1CE4E5B9L;
        h ^= y * 0x94D049BB133111EBL;
        h = Long.rotateLeft(h, 31) * 0x9E3779B97F4A7C15L;
        h ^= z * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 30; h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27; h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return ((h >>> 11) / (double) (1L << 53)) * 2.0D - 1.0D;
    }
}
