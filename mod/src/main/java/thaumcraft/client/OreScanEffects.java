package thaumcraft.client;

import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import thaumcraft.network.OreScanPayload;

@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class OreScanEffects {
    private static List<OreScanPayload.Marker> markers=List.of();
    private static BlockPos origin=BlockPos.ZERO;
    private static final ResourceLocation NODE=ResourceLocation.parse("thaumcraft:textures/misc/nodes.png"),GLASS=ResourceLocation.parse("thaumcraft:textures/blocks/wardedglass.png");
    private static long expires;
    public static void accept(OreScanPayload packet){
        var mc=Minecraft.getInstance();origin=packet.origin();markers=packet.markers();expires=mc.level==null?0:mc.level.getGameTime()+100;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.level.getGameTime()>=expires){markers=List.of();return;}
        var pose=WorldOverlayPose.create(event);var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();
        // AFTER_LEVEL supplies a fresh stack after LevelRenderer restores the global view.
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        float alpha=Math.min(1,(expires-mc.level.getGameTime())/30f);int frame=(int)(mc.level.getGameTime()%32);
        for(var marker:markers){
            float opacity=alpha*(1-(float)marker.position().distSqr(origin)/64);if(opacity<=0)continue;
            pose.pushPose();pose.translate(marker.position().getX()+.5,marker.position().getY()+.5,marker.position().getZ()+.5);
            if(marker.value()<0)fluid(pose,buffers,marker.value()==-5?3986684:16734721,opacity);
            else{pose.mulPose(event.getCamera().rotation());float size=marker.value()/7f;var out=buffers.getBuffer(ThaumcraftRenderTypes.unlit(NODE,true,true));billboard(pose,out,.1f*size,opacity,frame,0xAAAA11);billboard(pose,out,.25f*size,opacity,frame,0xAA1122);}
            pose.popPose();
        }
        pose.popPose();buffers.endBatch(ThaumcraftRenderTypes.unlit(NODE,true,true));buffers.endBatch(ThaumcraftRenderTypes.unlit(GLASS,false,true));
    }
    private static void billboard(PoseStack pose,VertexConsumer out,float radius,float opacity,int frame,int colour){
        for(int i=0;i<4;i++)out.addVertex(pose.last(),i<2?-radius:radius,i==0||i==3?-radius:radius,0).setColor(colour>>16&255,colour>>8&255,colour&255,Math.clamp((int)(opacity*255),0,255)).setUv((frame+(i<2?1:0))/32f,(i==0||i==3?1:0)/32f);
    }
    private static void fluid(PoseStack pose,MultiBufferSource buffers,int colour,float opacity){
        float a=-.5f,b=.5f;float[][][] faces={{{a,a,a},{a,b,a},{b,b,a},{b,a,a}},{{b,a,b},{b,b,b},{a,b,b},{a,a,b}},{{a,a,b},{a,b,b},{a,b,a},{a,a,a}},{{b,a,a},{b,b,a},{b,b,b},{b,a,b}},{{a,b,a},{a,b,b},{b,b,b},{b,b,a}},{{a,a,b},{a,a,a},{b,a,a},{b,a,b}}};
        var out=buffers.getBuffer(ThaumcraftRenderTypes.unlit(GLASS,false,true));for(var face:faces)for(int i=0;i<4;i++)out.addVertex(pose.last(),face[i][0],face[i][1],face[i][2]).setColor(colour>>16&255,colour>>8&255,colour&255,Math.clamp((int)(opacity*255),0,255)).setUv(i<2?0:1,i==0||i==3?1:0);
    }
    private OreScanEffects(){}
}
