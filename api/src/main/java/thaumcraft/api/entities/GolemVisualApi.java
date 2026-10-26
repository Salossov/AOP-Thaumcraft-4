package thaumcraft.api.entities;

import java.util.Objects;
import net.minecraft.world.entity.LivingEntity;

/** Read-only native golem presentation state. Contains no renderer or network-mod types. */
public final class GolemVisualApi {
    public record State(boolean supported, boolean inactive, int healingTicks) {
        public static final State UNSUPPORTED = new State(false, true, 0);
        public State {
            if (healingTicks < 0 || healingTicks > 5) throw new IllegalArgumentException("Invalid healing visual timer");
        }
    }
    @FunctionalInterface public interface Access { State state(LivingEntity entity); }
    private static Access access;
    public static void install(Access implementation) {
        if (access != null) throw new IllegalStateException("Golem visual API already installed");
        access = Objects.requireNonNull(implementation);
    }
    /**
     * Read on the logical side's game/render thread. Inactive is tracking-synchronized;
     * healingTicks is the local client countdown from native entity event5 (zero on server).
     * Neither value changes AI, health, equipment or persistence.
     */
    public static State state(LivingEntity entity) {
        Objects.requireNonNull(entity);
        if (access == null) throw new IllegalStateException("Golem visual API not installed");
        return Objects.requireNonNull(access.state(entity));
    }
    private GolemVisualApi() {}
}
