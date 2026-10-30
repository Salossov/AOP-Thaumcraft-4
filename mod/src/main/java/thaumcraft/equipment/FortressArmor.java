package thaumcraft.equipment;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import thaumcraft.items.ThaumicArmorItem;

@EventBusSubscriber(modid="thaumcraft_aop")
public final class FortressArmor {
    public static boolean goggles(ItemStack stack){return stack.getItem() instanceof thaumcraft.items.GogglesItem||stack.getItem() instanceof ThaumicArmorItem armor&&(armor.kind()==ThaumicArmorItem.Kind.VOID_ROBE&&armor.getType()==ArmorItem.Type.HELMET||armor.kind()==ThaumicArmorItem.Kind.FORTRESS&&stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getBoolean("goggles"));}
    public static int mask(ItemStack stack){var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();return stack.getItem() instanceof ThaumicArmorItem armor&&armor.kind()==ThaumicArmorItem.Kind.FORTRESS&&data.contains("mask")?data.getInt("mask"):-1;}
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOW) public static void damage(LivingIncomingDamageEvent event){
        var wearer=event.getEntity();if(wearer.level().isClientSide)return;
        var source=event.getSource();var helmet=wearer.getItemBySlot(EquipmentSlot.HEAD);
        if(mask(helmet)==1&&source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker&&wearer.getRandom().nextFloat()<event.getAmount()/10)
            attacker.addEffect(new MobEffectInstance(MobEffects.WITHER,80));
        if(source.getEntity() instanceof net.minecraft.world.entity.player.Player attacker&&mask(attacker.getItemBySlot(EquipmentSlot.HEAD))==2&&attacker.getRandom().nextFloat()<event.getAmount()/12)attacker.heal(1);
        // A mixed addon set is handled once at the actual armor stage, with original priorities.
        if(SpecialArmorProtection.active(wearer))return;
        double set=.875;int allPoints=0,specialPoints=0;double specialRatio=0;
        for(var stack:wearer.getArmorSlots())if(stack.getItem() instanceof ArmorItem armor){
            allPoints+=armor.getDefense();if(armor instanceof ThaumicArmorItem gear&&gear.kind()==ThaumicArmorItem.Kind.FORTRESS){set+=.125;if(mask(stack)>=0)set+=.05;}
        }
        boolean magic=source.is(DamageTypes.MAGIC)||source.is(DamageTypes.INDIRECT_MAGIC),fire=source.is(DamageTypeTags.IS_FIRE)||source.is(DamageTypeTags.IS_EXPLOSION),bypass=source.is(DamageTypeTags.BYPASSES_ARMOR);
        for(var stack:wearer.getArmorSlots())if(stack.getItem() instanceof ThaumicArmorItem armor&&(armor.kind()==ThaumicArmorItem.Kind.FORTRESS||armor.kind()==ThaumicArmorItem.Kind.VOID_ROBE)){
            specialPoints+=armor.getDefense();double ratio=magic?armor.getDefense()/35d:armor.kind()==ThaumicArmorItem.Kind.FORTRESS&&fire?armor.getDefense()/20d:bypass?0:armor.getDefense()/25d;
            if(armor.kind()==ThaumicArmorItem.Kind.FORTRESS)ratio*=set;
            specialRatio+=Math.min(ratio,(stack.getMaxDamage()+1-stack.getDamageValue())/Math.max(1,event.getAmount()));
        }
        if(specialPoints==0)return;
        final double retained=allPoints==0?0:(allPoints-specialPoints)/(double)allPoints,desired=Math.min(1,specialRatio);final float damage=event.getAmount();
        if(bypass){event.setAmount(damage*(float)(1-desired));}
        else event.addReductionModifier(DamageContainer.Reduction.ARMOR,(container,reduction)->(float)Math.min(damage,reduction*retained+damage*desired));
    }
    private FortressArmor(){}
}
