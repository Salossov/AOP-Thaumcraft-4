package thaumcraft.api.world;

import java.util.Objects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only native ore conversion. The caller owns chance, loot count and world changes. */
public final class SpecialMiningApi {
    @FunctionalInterface
    public interface Access { ItemStack result(Level level, BlockState state, ItemStack originalDrop); }
    private static Access access;

    public static void install(Access implementation) {
        if (access != null) throw new IllegalStateException("Special mining API already installed");
        access = Objects.requireNonNull(implementation);
    }

    /** A fresh, one-item native cluster or EMPTY; never rolls randomness or changes the drop. */
    public static ItemStack specialMiningResult(Level level, BlockState state, ItemStack originalDrop) {
        Objects.requireNonNull(level); Objects.requireNonNull(state); Objects.requireNonNull(originalDrop);
        if (level.isClientSide() || level.getServer() == null || !level.getServer().isSameThread())
            throw new IllegalStateException("Server thread required");
        if (access == null) throw new IllegalStateException("Special mining API not installed");
        if (originalDrop.isEmpty()) return ItemStack.EMPTY;
        var result = Objects.requireNonNull(access.result(level, state, originalDrop.copy()));
        return result.isEmpty() ? ItemStack.EMPTY : result.copyWithCount(1);
    }

    private SpecialMiningApi() { }
}
