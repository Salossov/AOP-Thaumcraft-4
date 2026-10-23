package thaumcraft.api.crafting;

import java.util.Objects;

/** Installed once by Thaumcraft before addon registration. */
public final class InfusionMatrixApi {
    private static InfusionMatrixProbe probe;

    public static void install(InfusionMatrixProbe implementation) {
        if (probe != null) throw new IllegalStateException("Infusion matrix API already installed");
        probe = Objects.requireNonNull(implementation);
    }

    /** Null before native bootstrap; callers must not bypass an absent adapter. */
    public static InfusionMatrixProbe probe() {
        return probe;
    }

    private InfusionMatrixApi() {}
}
