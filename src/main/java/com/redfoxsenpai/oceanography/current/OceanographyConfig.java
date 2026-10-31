package com.redfoxsenpai.oceanography.current;

import net.minecraftforge.common.ForgeConfigSpec;

public final class OceanographyConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    private static final double TICKS_PER_SECOND = 20.0D;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        COMMON = new Common(builder);
        COMMON_SPEC = builder.build();
    }

    private OceanographyConfig() {}

    public static double fastCurrentThresholdBlocksPerTick() {
        double blocksPerSecond = COMMON.fastCurrentThresholdBlocksPerSecond.get();
        return Math.max(0.0D, Math.min(CurrentField.MAX_SPEED, blocksPerSecond / TICKS_PER_SECOND));
    }

    public static final class Common {
        public final ForgeConfigSpec.DoubleValue fastCurrentThresholdBlocksPerSecond;

        private Common(ForgeConfigSpec.Builder builder) {
            builder.comment("Oceanography current visuals")
                    .push("currents");
            fastCurrentThresholdBlocksPerSecond = builder
                    .comment("Minimum horizontal speed that counts as a fast current, in blocks per second.",
                            "Fast currents show streaks and 3D shells. Default: 0.7")
                    .defineInRange("fastCurrentThresholdBlocksPerSecond", 0.7D, 0.0D,
                            CurrentField.MAX_SPEED * TICKS_PER_SECOND);
            builder.pop();
        }
    }
}
