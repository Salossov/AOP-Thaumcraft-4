package thaumcraft.items;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.*;
import thaumcraft.entities.PrimalArrow;

/** Original bow hooks: primal ammunition is selected before ordinary arrows. */
@EventBusSubscriber(modid="thaumcraft_aop")
public final class BowEvents {
    public static int enchantment(ItemStack stack,Level l,ResourceKey<Enchantment> key){return EnchantmentHelper.getItemEnchantmentLevel(l.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key),stack);}
    public static ItemStack ammunition(Player p){for(var stack:p.getInventory().items)if(!stack.isEmpty()&&stack.getItem() instanceof PrimalArrowItem)return stack;var off=p.getOffhandItem();return off.getItem() instanceof PrimalArrowItem?off:ItemStack.EMPTY;}
    /** Source arrow scatter uses the projectile RNG, including one random sign per axis. */
    public static void launch(net.minecraft.world.entity.projectile.Projectile arrow,net.minecraft.world.entity.LivingEntity owner,float speed,float yawOffset){thaumcraft.entities.LegacyArrowLaunch.owner(arrow,owner,speed,yawOffset);}
    @SubscribeEvent public static void nock(ArrowNockEvent event){if(!(event.getBow().getItem() instanceof BowItem)||ammunition(event.getEntity()).isEmpty())return;event.getEntity().startUsingItem(event.getHand());event.setAction(net.minecraft.world.InteractionResultHolder.consume(event.getBow()));}
    @SubscribeEvent public static void loose(ArrowLooseEvent event){if(!(event.getBow().getItem() instanceof BowItem))return;var p=event.getEntity();var ammo=ammunition(p);if(ammo.isEmpty())return;boolean bone=event.getBow().getItem() instanceof BoneBowItem;float f=bone?BoneBowItem.power(event.getCharge()):BowItem.getPowerForTime(event.getCharge());if(f<.1f)return;var l=p.level();if(!l.isClientSide){var arrow=new PrimalArrow(l,p,ammo,((PrimalArrowItem)ammo.getItem()).element);launch(arrow,p,f*(bone?3.75f:3),0);if(bone)arrow.setBaseDamage(arrow.tc4Damage()+.5);else if(f==1)arrow.setCritArrow(true);int power=enchantment(event.getBow(),l,Enchantments.POWER);if(power>0)arrow.setBaseDamage(arrow.tc4Damage()+power*.5+.5);arrow.knockback(enchantment(event.getBow(),l,Enchantments.PUNCH)+(arrow.element()==3?1:0));if(enchantment(event.getBow(),l,Enchantments.FLAME)>0)arrow.igniteForSeconds(100);event.getBow().hurtAndBreak(1,p,net.minecraft.world.entity.LivingEntity.getSlotForHand(p.getUsedItemHand()));
            l.playSound(null,p.getX(),p.getY(),p.getZ(),net.minecraft.sounds.SoundEvents.ARROW_SHOOT,net.minecraft.sounds.SoundSource.PLAYERS,1,1/(l.random.nextFloat()*.4f+1.2f)+f*.5f);
            // Keep TC4's actual condition: survival always spends a primal arrow;
            // creative conserves it only when Infinity's 0.33 chance succeeds.
            boolean conserve=enchantment(event.getBow(),l,Enchantments.INFINITY)>0&&l.random.nextFloat()<.33f;if(!p.getAbilities().instabuild||!conserve){ammo.shrink(1);if(ammo.isEmpty())p.getInventory().removeItem(ammo);}l.addFreshEntity(arrow);}
        if(l.isClientSide)l.playSound(null,p.getX(),p.getY(),p.getZ(),net.minecraft.sounds.SoundEvents.ARROW_SHOOT,net.minecraft.sounds.SoundSource.PLAYERS,1,1/(l.random.nextFloat()*.4f+1.2f)+f*.5f);event.setCanceled(true);
    }
    private BowEvents(){}
}
