package thaumcraft.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.items.ThaumometerItem;

/** Two-hand first-person lens, with labels and scanned aspects on its glass plane. */
@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class ScannerHandRenderer {
    public static boolean hasTarget(){return ScannerTarget.current()!=null;}
    @SubscribeEvent public static void render(RenderHandEvent event){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        var lensHand=mc.player.getMainHandItem().getItem() instanceof ThaumometerItem?InteractionHand.MAIN_HAND:
                mc.player.getOffhandItem().getItem() instanceof ThaumometerItem?InteractionHand.OFF_HAND:null;
        if(lensHand==null)return;
        event.setCanceled(true);
        if(event.getHand()!=lensHand||!(event.getItemStack().getItem() instanceof ThaumometerItem))return;
        var pose=event.getPoseStack();var buffers=event.getMultiBufferSource();pose.pushPose();pose.translate(0,-0.035-event.getEquipProgress()*1.5,-0.78);
        if(!mc.player.isInvisible())arms(pose,buffers,event.getPackedLight());
        pose.pushPose();pose.mulPose(Axis.ZP.rotationDegrees(90));pose.mulPose(Axis.XP.rotationDegrees(90));pose.scale(1.3f,1.3f,1.3f);
        mc.getItemRenderer().renderStatic(mc.player,event.getItemStack(),ItemDisplayContext.NONE,false,pose,buffers,mc.level,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,mc.player.getId());pose.popPose();
        // The OBJ already contains the translucent scan plate; draw it only once.
        var target=ScannerTarget.current();if(target!=null){int nameColor=target.tainted()?0xaa00aa:0xffffff;label(pose,buffers,target.name(),0,0.205f,0.0045f,nameColor,90);if(!target.detail().getString().isEmpty())label(pose,buffers,target.detail(),0,0.068f,0.0036f,target.tainted()?0xaa00aa:15642134,130);
            var values=target.aspects().entrySet().stream().filter(e->Aspect.getAspect(e.getKey())!=null).sorted(Map.Entry.comparingByKey()).toList();int row=0;
            for(int offset=0;offset<values.size();row++){int count=Math.min(Math.max(1,5-row),values.size()-offset);for(int index=0;index<count;index++){var entry=values.get(offset++);var aspect=Aspect.getAspect(entry.getKey());float x=-count*0.03f+index*0.06f,y=-row*0.06f;
                quad(pose,buffers,ResourceLocation.parse(aspect.texture()),x,y-0.03f,0.06f,0.045f,aspect.getColor(),1);label(pose,buffers,Component.literal(entry.getValue().toString()),x+0.042f,y-0.026f,0.0027f,0xffffff,40);
            }}
        }pose.popPose();
    }
    private static void label(PoseStack pose,MultiBufferSource buffers,Component text,float x,float y,float size,int color,int max){var font=Minecraft.getInstance().font;int width=font.width(text);if(width*size>max)size=max/width;pose.pushPose();pose.translate(x,y,0.05);pose.scale(size,-size,size);font.drawInBatch(text,-width/2f,0,0xff000000|color,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT);pose.popPose();}
    private static void quad(PoseStack pose,MultiBufferSource buffers,ResourceLocation texture,float x,float y,float size,float z,int color,float alpha){var v=buffers.getBuffer(ThaumcraftRenderTypes.node(texture,false,false));for(int i=0;i<4;i++){v.addVertex(pose.last().pose(),i<2?x:x+size,i==0||i==3?y:y+size,z).setColor(color>>16&255,color>>8&255,color&255,(int)(alpha*255)).setUv(i<2?0:1,i==0||i==3?1:0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,0,1);}}
    private static void arms(PoseStack pose,MultiBufferSource buffers,int light){var mc=Minecraft.getInstance();var renderer=(PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(mc.player);pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(90));for(int side:new int[]{1,-1}){pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(92));pose.mulPose(Axis.XP.rotationDegrees(45));pose.mulPose(Axis.ZP.rotationDegrees(side*-41));pose.translate(side*0.3,-1.1,0.45);if(side>0)renderer.renderRightHand(pose,buffers,light,mc.player);else renderer.renderLeftHand(pose,buffers,light,mc.player);pose.popPose();}pose.popPose();}
    private ScannerHandRenderer(){}
}
