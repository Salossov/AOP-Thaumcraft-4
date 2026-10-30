package thaumcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.*;

/** Original ModelCentrifuge boxes and UVs, shared by its world and inventory forms. */
@EventBusSubscriber(modid="thaumcraft_aop",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class CentrifugeRenderer {
    private final net.minecraft.client.model.geom.ModelPart model=OriginalDeviceGeometry.bake("centrifuge");
    void draw(PoseStack pose,MultiBufferSource buffers,int light,int overlay,float rotation){
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ResourceLocation.parse("thaumcraft:textures/models/centrifuge.png")));
        pose.pushPose();pose.translate(.5,.5,.5);
        for(var name:new String[]{"Top","Bottom"})model.getChild(name).render(pose,out,light,overlay);
        pose.mulPose(Axis.YP.rotationDegrees(rotation));
        for(var name:new String[]{"Crossbar","Dingus1","Dingus2","Core"})model.getChild(name).render(pose,out,light,overlay);
        pose.popPose();
    }
    private static final class ItemRenderer extends BlockEntityWithoutLevelRenderer {
        private final CentrifugeRenderer renderer=new CentrifugeRenderer();
        ItemRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
        @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay){renderer.draw(pose,buffers,light,overlay,0);}
    }
    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event){event.registerItem(new IClientItemExtensions(){private ItemRenderer renderer;@Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new ItemRenderer();return renderer;}},thaumcraft.registry.R3Content.CENTRIFUGE.get().asItem());}
}
