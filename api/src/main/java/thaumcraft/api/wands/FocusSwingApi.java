package thaumcraft.api.wands;

import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Native shared focus cooldown for a server-authoritative addon swing action. */
public final class FocusSwingApi {
    @FunctionalInterface
    public interface Access {
        boolean tryBegin(ServerPlayer player, ItemStack wand);
    }

    private static Access access;

    public static void install(Access implementation) {
        if (access != null) throw new IllegalStateException("Focus swing API already installed");
        access = Objects.requireNonNull(implementation);
    }

    /**
     * Requires the player's server game thread and the actual wand held in either
     * hand. Atomically claims the native cooldown using the selected focus's own
     * activation duration. False leaves it unchanged. True starts the cooldown
     * even if the caller's subsequent action fails, matching TC4 onEntitySwing.
     * Does not pay Vis, invoke effects or call the right-click casting pipeline.
     */
    public static boolean tryBegin(ServerPlayer player, ItemStack wand) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(wand);
        if (!player.server.isSameThread())
            throw new IllegalStateException("Focus swing requires its server game thread");
        if (access == null) throw new IllegalStateException("Focus swing API not installed");
        return access.tryBegin(player, wand);
    }

    private FocusSwingApi() {}
}
