package thaumcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.blocks.AuraNodeBlockEntity;
import thaumcraft.network.ClientKnowledge;
import thaumcraft.production.TransportBlockEntity;

/**
 * Original RenderEventHandler.blockHighlight: looking at an aspect container
 * (node, warded jar) while wearing Goggles of Revealing draws its aspect icons
 * with amounts floating above the block. Unknown aspects show the ? icon.
 */
@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class GogglesAspectOverlay {
    private static float tagScale;
    private static final FrameAnimation clock=new FrameAnimation();
    private static Object world;
    @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event){world=null;tagScale=0;clock.reset();}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();
        if(world!=mc.level){world=mc.level;tagScale=0;clock.reset();}
        double seconds=clock.seconds();float previous=tagScale;
        tagScale=FrameAnimation.aspect(previous,seconds,false);
        if(mc.player==null||mc.level==null||mc.hitResult==null||mc.hitResult.getType()!=HitResult.Type.BLOCK)return;
        if(!thaumcraft.api.EquipmentVision.canSeeAspects(mc.player))return;
        var pos=((BlockHitResult)mc.hitResult).getBlockPos();
        Map<String,Integer> aspects=null;
        if(mc.level.getBlockEntity(pos) instanceof AuraNodeBlockEntity node&&!node.state.current.isEmpty())aspects=node.state.current;
        else if(mc.level.getBlockEntity(pos) instanceof thaumcraft.blocks.JarredNodeBlockEntity jar)aspects=jar.state.current;
        else if(mc.level.getBlockEntity(pos) instanceof thaumcraft.visnet.EnergizedNodeBlockEntity node)aspects=node.capacity();
        else if(mc.level.getBlockEntity(pos) instanceof TransportBlockEntity jar){
            var stored=jar.store.first();int amount=jar.store.amount();
            if(stored!=null&&amount>0)aspects=Map.of(stored.getTag(),amount);
        }
        else if(mc.level.getBlockEntity(pos) instanceof thaumcraft.production.CrucibleBlockEntity crucible){var list=crucible.getAspects();aspects=new LinkedHashMap<>();for(var aspect:list.getAspects())aspects.put(aspect.getTag(),list.getAmount(aspect));}
        if(aspects==null||aspects.isEmpty())return;
        var entries=aspects.entrySet().stream().filter(e->Aspect.getAspect(e.getKey())!=null).sorted(Map.Entry.comparingByKey()).toList();
        if(entries.isEmpty())return;
        tagScale=FrameAnimation.aspect(previous,seconds,true);
        boolean above=mc.level.isEmptyBlock(pos.above());
        var side=above?net.minecraft.core.Direction.UP:((BlockHitResult)mc.hitResult).getDirection();
        var pose=WorldOverlayPose.create(event);var camera=event.getCamera().getPosition();
        var buffers=mc.renderBuffers().bufferSource();
        var knowledge=ClientKnowledge.snapshot();
        for(int i=0;i<entries.size();i++){
            var entry=entries.get(i);var aspect=Aspect.getAspect(entry.getKey());
            int row=i/5,col=i%5,perRow=Math.min(5,entries.size()-row*5);
            float x=(col-(perRow-1)/2f)*tagScale*tagScale*4,y=.5f+(above?.4f:0)+row*tagScale*1.05f;
            pose.pushPose();
            pose.translate(pos.getX()+.5-camera.x+side.getStepX()*tagScale*2,pos.getY()+y-camera.y+side.getStepY()*tagScale*2,pos.getZ()+.5-camera.z+side.getStepZ()*tagScale*2);
            pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
            pose.scale(tagScale,tagScale,tagScale);
            pose.translate(x/tagScale,0,0);
            var texture=knowledge.knows(aspect)?ResourceLocation.parse(aspect.texture()):ResearchGraphics.texture("aspects/_unknown");
            var color=aspect.getColor();
            var v=buffers.getBuffer(ThaumcraftRenderTypes.unlit(texture,false,true));
            var m=pose.last();
            v.addVertex(m.pose(),-.5f,-.5f,0).setColor(color>>16&255,color>>8&255,color&255,191).setUv(0,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(m,0,0,1);
            v.addVertex(m.pose(),.5f,-.5f,0).setColor(color>>16&255,color>>8&255,color&255,191).setUv(1,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(m,0,0,1);
            v.addVertex(m.pose(),.5f,.5f,0).setColor(color>>16&255,color>>8&255,color&255,191).setUv(1,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(m,0,0,1);
            v.addVertex(m.pose(),-.5f,.5f,0).setColor(color>>16&255,color>>8&255,color&255,191).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(m,0,0,1);
            // Keep the amount in the icon's camera-facing coordinate system.
            // Offsetting world X separately made text drift as the camera turned.
            pose.pushPose();
            pose.translate(0,-.24f,.01f);
            float scale=.04f;
            pose.scale(scale,-scale,scale);
            var amount=Component.literal(String.valueOf(entry.getValue()));
            int width=mc.font.width(amount);
            buffers.endBatch();
            boolean depth=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
            mc.font.drawInBatch(amount,14-width,1,0xff3f3f3f,false,pose.last().pose(),buffers,net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,0,LightTexture.FULL_BRIGHT);
            buffers.endBatch();
            mc.font.drawInBatch(amount,13-width,0,0xffffffff,false,pose.last().pose(),buffers,net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,0,LightTexture.FULL_BRIGHT);
            buffers.endBatch();
            if(depth)com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
            pose.popPose();
            pose.popPose();
        }
        buffers.endBatch();
    }
    private GogglesAspectOverlay(){}
}
