package thaumcraft.effects;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import thaumcraft.registry.*;
import thaumcraft.fluids.LiquidDeathBlock;

@EventBusSubscriber(modid="thaumcraft_aop")
public final class CleansingEvents {
    @SubscribeEvent public static void saltExpires(ItemExpireEvent event){var item=event.getEntity();if(!item.getItem().is(R4Content.BATH_SALTS.get()))return;var pos=item.blockPosition();var level=item.level();var state=level.getBlockState(pos);if(state.is(Blocks.WATER)&&state.getFluidState().isSource())level.setBlockAndUpdate(pos,R4Content.PURIFYING_FLUID.get().defaultBlockState());}
    @SubscribeEvent public static void dissolved(LivingDropsEvent event){
        if(!event.getSource().is(LiquidDeathBlock.DISSOLVE))return;var entity=event.getEntity();if(!(entity.level() instanceof net.minecraft.server.level.ServerLevel level))return;
        var aspects=thaumcraft.data.ThaumcraftData.aspects(level.getServer(),entity);
        for(var entry:aspects.entrySet()){if(level.random.nextBoolean())continue;int amount=Math.max(1,(1+level.random.nextInt(entry.getValue()))/2);var stack=new ItemStack(R3Content.ESSENTIA_CRYSTAL.get(),amount);R3Content.setAspect(stack,thaumcraft.api.aspects.Aspect.getAspect(entry.getKey()));event.getDrops().add(new ItemEntity(level,entity.getX(),entity.getEyeY(),entity.getZ(),stack));}
    }
    private CleansingEvents(){}
}
