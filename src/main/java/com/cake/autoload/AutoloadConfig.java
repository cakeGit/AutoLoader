package com.cake.autoload;

import net.neoforged.neoforge.common.ModConfigSpec;

public class AutoloadConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ALWAYS_LOAD_LAST;
    public static final ModConfigSpec.BooleanValue SILENCE_ALERTS;
    static final ModConfigSpec SPEC;

    static {
        BUILDER.push("general");

        ALWAYS_LOAD_LAST = BUILDER
                .comment("When true, always load the most recently played world on launch, ignoring the autoload file.")
                .define("always_load_last", false);
        
        SILENCE_ALERTS = BUILDER
            .comment("When true, don't send any toasts on load about the state of autoload.")
            .define("silence_alerts", false);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
