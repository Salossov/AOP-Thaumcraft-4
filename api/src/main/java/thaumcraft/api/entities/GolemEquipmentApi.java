package thaumcraft.api.entities;

import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Persistent, tracking-synchronized backpack equipment on native golems.
 * No network-mod types or AI replacements are part of this contract.
 */
public final class GolemEquipmentApi {
    public interface Access {
        boolean supports(LivingEntity entity);
        ItemStack backpack(LivingEntity entity);
        boolean canConfigure(LivingEntity entity, ServerPlayer actor);
        boolean replaceBackpack(LivingEntity entity, ServerPlayer actor, ItemStack expected, ItemStack replacement);
    }

    private static Access access;

    public static void install(Access implementation) {
        if (access != null) throw new IllegalStateException("Golem equipment API already installed");
        access = Objects.requireNonNull(implementation);
    }

    private static Access access() {
        if (access == null) throw new IllegalStateException("Golem equipment API not installed");
        return access;
    }

    public static boolean supports(LivingEntity entity) {
        return access().supports(Objects.requireNonNull(entity));
    }

    /** Independent snapshot, readable on either logical side. Unsupported entities return empty. */
    public static ItemStack backpack(LivingEntity entity) {
        return access().backpack(Objects.requireNonNull(entity)).copy();
    }

    /** Server game thread only; checks live identity, world, distance, owner and world permissions. */
    public static boolean canConfigure(LivingEntity entity, ServerPlayer actor) {
        requireServerThread(Objects.requireNonNull(entity));
        return access().canConfigure(entity, Objects.requireNonNull(actor));
    }

    /**
     * Atomically replaces the slot only if its full item/count/components match expected.
     * Replacement must be empty or one item. Inputs are copied before reaching the adapter.
     * This low-level operation does not consume or return player inventory items: the addon
     * must perform its inventory transfer only after success, on the same server game thread.
     * Native death/dismantling clears and drops the stored stack once; unload preserves it.
     */
    public static boolean replaceBackpack(LivingEntity entity, ServerPlayer actor,
                                          ItemStack expected, ItemStack replacement) {
        requireServerThread(Objects.requireNonNull(entity));
        Objects.requireNonNull(actor);
        Objects.requireNonNull(expected);
        Objects.requireNonNull(replacement);
        if (!replacement.isEmpty() && replacement.getCount() != 1)
            throw new IllegalArgumentException("Backpack equipment must contain one item");
        return access().replaceBackpack(entity, actor, expected.copy(), replacement.copy());
    }

    private static void requireServerThread(LivingEntity entity) {
        if (entity.level().isClientSide || entity.getServer() == null || !entity.getServer().isSameThread())
            throw new IllegalStateException("Golem equipment requires its server game thread");
    }

    private GolemEquipmentApi() {}
}
