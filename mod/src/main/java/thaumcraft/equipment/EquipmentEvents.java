package thaumcraft.equipment;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import thaumcraft.api.IRepairable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.config.ThaumcraftConfig;
import thaumcraft.data.ThaumcraftData;
import thaumcraft.items.*;

@EventBusSubscriber(modid="thaumcraft_aop")
public final class EquipmentEvents {
    private static final ResourceLocation LEGACY_SPEED=ResourceLocation.parse("thaumcraft:legacy_attack_speed");
    private static final ResourceLocation TRAVELLER_STEP=ResourceLocation.parse("thaumcraft:traveller_step");
    @SubscribeEvent public static void attributes(ItemAttributeModifierEvent event){
        if(ThaumcraftConfig.MODERN_WEAPON_COOLDOWN.get()||!(event.getItemStack().getItem() instanceof IRepairable)||!(event.getItemStack().getItem() instanceof TieredItem))return;
        event.removeAllModifiersFor(Attributes.ATTACK_SPEED);
        event.addModifier(Attributes.ATTACK_SPEED,new AttributeModifier(LEGACY_SPEED,1020,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND);
    }
    public static int enchantmentLevel(ItemStack stack,net.minecraft.world.level.Level level,String path){
        var key=net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT,ResourceLocation.fromNamespaceAndPath("thaumcraft",path));
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key).map(e->EnchantmentHelper.getItemEnchantmentLevel(e,stack)).orElse(0);
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){
        var player=event.getEntity();var boots=player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
        boolean traveller=boots.getItem() instanceof ThaumicArmorItem armor&&armor.kind()==ThaumicArmorItem.Kind.TRAVELLER;
        var step=player.getAttribute(Attributes.STEP_HEIGHT);
        boolean walk=traveller&&!player.isShiftKeyDown()&&!player.getAbilities().flying;
        if(step!=null){if(walk&&!step.hasModifier(TRAVELLER_STEP))step.addTransientModifier(new AttributeModifier(TRAVELLER_STEP,.4,AttributeModifier.Operation.ADD_VALUE));else if(!walk)step.removeModifier(TRAVELLER_STEP);}
        if(traveller)player.fallDistance=Math.max(0,player.fallDistance-.25f);
        if(player.zza>0&&!player.getAbilities().flying){
            float bonus=enchantmentLevel(boots,player.level(),"haste")*.015f;
            if(!player.onGround())bonus/=2;if(player.isInWater())bonus/=2;
            if(traveller)bonus+=player.onGround()?(player.isInWater()?.055f/4:.055f):.03f;
            if(bonus>0)player.moveRelative(bonus,new Vec3(0,0,1));
        }
        if(!(player instanceof ServerPlayer server)||server.isCreative()||server.tickCount%40!=0)return;
        for(int i=0;i<server.getInventory().getContainerSize();i++){
            var stack=server.getInventory().getItem(i);
            if(stack.isDamaged()&&stack.getItem() instanceof IRepairable&&(!(stack.getItem() instanceof ThaumicArmorItem armor)||armor.kind()!=ThaumicArmorItem.Kind.HARNESS||i>=36))repair(server,stack);
        }
    }
    public static boolean repair(ServerPlayer player,ItemStack stack){
        int level=Math.min(2,enchantmentLevel(stack,player.level(),"repair"));if(level<=0||!stack.isDamaged())return false;
        var primals=new LinkedHashMap<Aspect,Integer>();
        ThaumcraftData.aspects(player.server,stack).forEach((tag,n)->reduce(primals,Aspect.getAspect(tag),n));
        if(primals.isEmpty())return false;
        primals.replaceAll((aspect,n)->(int)Math.sqrt(n*2)*level*100);
        if(!WandVis.consumeInventory(player,primals))return false;
        stack.setDamageValue(Math.max(0,stack.getDamageValue()-level));return true;
    }
    private static void reduce(Map<Aspect,Integer> result,Aspect aspect,int amount){
        if(aspect==null)return;if(aspect.isPrimal())result.merge(aspect,amount,Integer::sum);
        else for(var component:aspect.getComponents())reduce(result,component,amount);
    }
    @SubscribeEvent public static void jump(LivingJumpEvent event){
        var boots=event.getEntity().getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
        if(boots.getItem() instanceof ThaumicArmorItem armor&&armor.kind()==ThaumicArmorItem.Kind.TRAVELLER)event.getEntity().setDeltaMovement(event.getEntity().getDeltaMovement().add(0,.275,0));
    }
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event){
        int charge=RunicShield.capacity(event.getItemStack());if(charge>0)event.getToolTip().add(net.minecraft.network.chat.Component.translatable("item.runic.charge").append(" +"+charge).withStyle(net.minecraft.ChatFormatting.GOLD));
    }
    private EquipmentEvents(){}
}
