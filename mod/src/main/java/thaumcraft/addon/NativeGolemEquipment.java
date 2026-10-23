package thaumcraft.addon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import thaumcraft.api.entities.GolemEquipmentApi;
import thaumcraft.entities.ThaumicGolem;

/** Native equipment adapter. The core does not interpret the backpack's mod-specific data. */
final class NativeGolemEquipment implements GolemEquipmentApi.Access {
    @Override
    public boolean supports(LivingEntity entity) {
        return entity instanceof ThaumicGolem;
    }

    @Override
    public ItemStack backpack(LivingEntity entity) {
        return entity instanceof ThaumicGolem golem ? golem.backpackEquipment() : ItemStack.EMPTY;
    }

    @Override
    public boolean canConfigure(LivingEntity entity, ServerPlayer actor) {
        if (!(entity instanceof ThaumicGolem golem) || !(entity.level() instanceof ServerLevel level) ||
                actor instanceof FakePlayer || actor.level() != level || !actor.isAlive() || actor.isRemoved() || actor.isSpectator() ||
                level.getEntity(actor.getUUID()) != actor ||
                !golem.isAlive() || golem.isRemoved() || !level.hasChunkAt(golem.blockPosition()) ||
                level.getEntity(golem.getUUID()) != golem || actor.distanceToSqr(golem) >= 64 ||
                !level.mayInteract(actor, golem.blockPosition())) return false;
        return golem.owner() == null || golem.owner().equals(actor.getUUID()) || actor.getAbilities().instabuild;
    }

    @Override
    public boolean replaceBackpack(LivingEntity entity, ServerPlayer actor,
                                    ItemStack expected, ItemStack replacement) {
        if (!canConfigure(entity, actor)) return false;
        return ((ThaumicGolem) entity).replaceBackpackEquipment(expected, replacement);
    }
}
