package thaumcraft.client;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

@EventBusSubscriber(modid="thaumcraft_aop",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class OuterDimensionEffects extends DimensionSpecialEffects {
    public OuterDimensionEffects(){super(Float.NaN,false,SkyType.NONE,false,false);}
    @Override public Vec3 getBrightnessDependentFogColor(Vec3 color,float daylight){return new Vec3(160/255.0*.15,128/255.0*.15,160/255.0*.15);}
    @Override public boolean isFoggyAt(int x,int z){return true;}
    @Override public float[] getSunriseColor(float time,float partial){return null;}
    @SubscribeEvent public static void register(RegisterDimensionSpecialEffectsEvent event){event.register(ResourceLocation.parse("thaumcraft:outer_lands"),new OuterDimensionEffects());}
}
