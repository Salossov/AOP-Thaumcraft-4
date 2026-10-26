package thaumcraft.api.entities;

import java.util.*;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import thaumcraft.api.aspects.Aspect;

/** Server-only extensions of native golem work, registered during AddonRegistrationEvent. */
public final class GolemWorkApi {
    @FunctionalInterface public interface Factory { Extension create(Context context); }
    public interface Extension {
        List<PrioritizedGoal> goals();
        /** Lets native Gather collect items without a physical home inventory. */
        default boolean hasItemDestination() { return false; }
        default void close() {}
    }
    public record PrioritizedGoal(int priority, Goal goal) {
        public PrioritizedGoal {
            if (priority < 0) throw new IllegalArgumentException("Negative goal priority");
            Objects.requireNonNull(goal);
        }
    }
    /** Defensive cargo value, including complete item/fluid components. */
    public record Cargo(ItemStack item, FluidStack fluid, Aspect aspect, int essentia) {
        public Cargo {
            item = Objects.requireNonNull(item).copy();
            fluid = Objects.requireNonNull(fluid).copy();
            if (essentia < 0 || essentia > 0 && aspect == null) throw new IllegalArgumentException("Invalid essentia cargo");
            if (essentia == 0) aspect = null;
        }
        @Override public ItemStack item() { return item.copy(); }
        @Override public FluidStack fluid() { return fluid.copy(); }
    }
    public record Snapshot(int revision, int core, boolean advanced, List<Integer> upgrades,
                           boolean inactive, int action, BlockPos home, Direction homeFace,
                           List<IControllableGolem.Marker> markers, List<ItemStack> filters,
                           int carryLimit, int fluidLimit, double range, Cargo cargo) {
        public Snapshot {
            upgrades = List.copyOf(upgrades); home = home.immutable(); markers = List.copyOf(markers);
            filters = filters.stream().map(ItemStack::copy).toList(); Objects.requireNonNull(cargo);
        }
        @Override public List<ItemStack> filters() { return filters.stream().map(ItemStack::copy).toList(); }
        public int upgrade(int kind) { return kind < 0 || kind >= upgrades.size() ? 0 : upgrades.get(kind); }
        public BlockPos source() { return home.relative(homeFace.getOpposite()); }
    }
    public interface Context {
        PathfinderMob entity();
        Snapshot snapshot();
        /** Only this context's most recently issued snapshot can authorize a mutation. */
        boolean current(Snapshot expected);
        /** Native matching and current home deficits; all returned stacks are independent copies. */
        boolean matches(ItemStack stack, ItemStack filter);
        List<ItemStack> itemDemand();
        List<FluidStack> fluidDemand();
        /** Starts the native action animation only for a current, active server snapshot. */
        boolean beginAction(Snapshot expected);
        /**
         * Checks live identity, loaded chunk, configuration and full expected cargo before invoking
         * the synchronous transfer. Commits the returned cargo to native save/sync state and revokes
         * old native plans. No callback is made on failure. The callback may transact with external
         * storage but must not reenter this context or reconfigure/remove the entity. Return the
         * original cargo when no transfer succeeds. Never retain the callback beyond this call.
         */
        boolean exchange(Snapshot expected, UnaryOperator<Cargo> transfer, boolean animate);
    }

    private static final Map<ResourceLocation, Factory> FACTORIES = new LinkedHashMap<>();
    private static boolean frozen;
    public static synchronized void registerExtension(ResourceLocation id, Factory factory) {
        if (frozen) throw new IllegalStateException("Golem work registration closed");
        if (FACTORIES.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(factory)) != null)
            throw new IllegalArgumentException("Duplicate golem work extension " + id);
    }
    /** Core lifecycle: closes registration after the addon registration event. */
    public static synchronized void freeze() { frozen = true; }
    /** Core lifecycle: preserves addon registration order. */
    public static synchronized List<Factory> factories() { return List.copyOf(FACTORIES.values()); }
    private GolemWorkApi() {}
}
