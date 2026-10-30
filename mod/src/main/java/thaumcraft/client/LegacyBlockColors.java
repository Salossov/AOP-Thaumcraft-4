package thaumcraft.client;

import net.minecraft.client.renderer.BiomeColors;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import thaumcraft.registry.R2Content;

/** TC4 taint adopts grass colour outside its tainted biome; inventory samples stay purple. */
@EventBusSubscriber(modid="thaumcraft_aop",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class LegacyBlockColors {
    @SubscribeEvent public static void blocks(RegisterColorHandlersEvent.Block event){event.register((state,level,pos,tint)->{
        if(level==null||pos==null)return 0x8a21b5;
        return BiomeColors.getAverageGrassColor(level,pos);
    },R2Content.TAINT_FIBRES.get(),R2Content.TAINT_GROWTH.get(),R2Content.TAINT_TENDRILS.get(),R2Content.TAINT_SPORE_STALK.get(),R2Content.TAINTED_SOIL.get());}
    // Modern ItemRenderer also multiplies alpha, so the old RGB-only tint hides every quad.
    @SubscribeEvent public static void items(RegisterColorHandlersEvent.Item event){event.register((stack,tint)->0xff8a21b5,R2Content.TAINT_FIBRES.get().asItem(),R2Content.TAINT_GROWTH.get().asItem(),R2Content.TAINT_TENDRILS.get().asItem(),R2Content.TAINT_SPORE_STALK.get().asItem(),R2Content.TAINTED_SOIL.get().asItem());}
}
