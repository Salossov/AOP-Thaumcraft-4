package thaumcraft.api.aspects;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/** Pure conversions of the core's portable phials, jars and labels.
 * These methods never modify the supplied stack, inventory or world. Callers
 * must perform permissions, power and storage transactions on the server.
 * Aspect-bearing ingredients such as crystals and wisp essence are not containers.
 */
public final class EssentiaItemApi {
    /** A null aspect means empty; a null filter means unlabelled. */
    public record Contents(int capacity, boolean partial, Aspect aspect, int amount, Aspect filter) {
        public Contents {
            if (capacity <= 0 || amount < 0 || amount > capacity || (amount == 0) != (aspect == null)) {
                throw new IllegalArgumentException("Invalid portable essentia contents");
            }
        }
    }

    /** Result of converting one item. The stack is defensively copied on both boundaries. */
    public record TransferResult(int actualUnits, ItemStack resultStack) {
        public TransferResult {
            if (actualUnits <= 0 || resultStack.isEmpty() || resultStack.getCount() != 1) {
                throw new IllegalArgumentException("Invalid portable essentia transfer");
            }
            resultStack = resultStack.copy();
        }
        @Override public ItemStack resultStack() { return resultStack.copy(); }
    }

    public interface Access {
        Optional<Contents> inspect(ItemStack stack);
        Optional<TransferResult> fill(ItemStack stack, Aspect aspect, int offered);
        Optional<TransferResult> drain(ItemStack stack, Aspect aspect, int requested);
        Optional<ItemStack> configureLabel(ItemStack stack, Aspect aspect);
    }

    private static Access access;
    public static void install(Access value) {
        if (access != null) throw new IllegalStateException("Essentia item API already installed");
        access = Objects.requireNonNull(value);
    }
    public static Optional<Contents> inspect(ItemStack stack) {
        return access == null || stack.isEmpty() ? Optional.empty() : access.inspect(stack.copy());
    }
    /** Phials require all eight units; jars accept up to their remaining capacity. */
    public static Optional<TransferResult> fill(ItemStack stack, Aspect aspect, int offered) {
        Objects.requireNonNull(aspect);
        return access == null || stack.isEmpty() || offered <= 0 ? Optional.empty() : access.fill(stack.copy(), aspect, offered);
    }
    /** Phials drain all eight units or nothing; jars permit partial extraction. */
    public static Optional<TransferResult> drain(ItemStack stack, Aspect aspect, int requested) {
        Objects.requireNonNull(aspect);
        return access == null || stack.isEmpty() || requested <= 0 ? Optional.empty() : access.drain(stack.copy(), aspect, requested);
    }
    /** Configures the whole label stack without changing its count or consuming essentia. */
    public static Optional<ItemStack> configureLabel(ItemStack stack, Aspect aspect) {
        Objects.requireNonNull(aspect);
        return access == null || stack.isEmpty() ? Optional.empty() : access.configureLabel(stack.copy(), aspect).map(ItemStack::copy);
    }
    private EssentiaItemApi() {}
}
