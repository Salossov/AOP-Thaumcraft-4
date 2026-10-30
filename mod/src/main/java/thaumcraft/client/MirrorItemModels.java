package thaumcraft.client;

import java.util.List;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

/** Original extruded item icons use flat GUI lighting. */
@EventBusSubscriber(modid="thaumcraft_aop",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class MirrorItemModels {
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event){for(String name:List.of("magic_mirror","essentia_mirror")){var key=new ModelResourceLocation(ResourceLocation.parse("thaumcraft:"+name),"inventory");var base=event.getModels().get(key);if(base==null)continue;event.getModels().put(key,new BakedModelWrapper<BakedModel>(base){
        @Override public boolean isGui3d(){return false;}
        @Override public boolean usesBlockLight(){return false;}
        @Override public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous){return List.of(this);}
        @Override public BakedModel applyTransform(ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){base.applyTransform(context,pose,left);return this;}
    });}}
    private MirrorItemModels(){}
}
