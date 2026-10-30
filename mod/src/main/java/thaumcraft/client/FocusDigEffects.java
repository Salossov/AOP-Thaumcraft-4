package thaumcraft.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import thaumcraft.network.FocusDigPayload;

@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class FocusDigEffects {
    private record Beam(Vec3 endpoint,long expires){}
    private static final Map<Integer,Beam> beams=new HashMap<>();
    public static void accept(FocusDigPayload packet){var mc=Minecraft.getInstance();if(mc.level==null)return;if(packet.active())beams.put(packet.player(),new Beam(packet.endpoint(),mc.level.getGameTime()+6));else beams.remove(packet.player());}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;var mc=Minecraft.getInstance();if(mc.level==null)return;beams.entrySet().removeIf(e->e.getValue().expires<mc.level.getGameTime());if(beams.isEmpty())return;
        var pose=WorldOverlayPose.create(event);var camera=event.getCamera().getPosition();pose.translate(-camera.x,-camera.y,-camera.z);var buffers=mc.renderBuffers().bufferSource();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false),time=mc.level.getGameTime()+partial;
        for(var entry:beams.entrySet())if(mc.level.getEntity(entry.getKey()) instanceof Player player&&player.isUsingItem()){
            float side=player.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT?-.15f:.15f;if(player.getUsedItemHand()==net.minecraft.world.InteractionHand.OFF_HAND)side=-side;
            var offset=new Vec3(side,-.15,.5).xRot(-player.getXRot()*(float)Math.PI/180).yRot(-player.getYRot()*(float)Math.PI/180);var start=player.getEyePosition(partial).add(offset);var end=entry.getValue().endpoint;
            beam(pose,buffers,start,end,time);
            pose.pushPose();pose.translate(end.x,end.y,end.z);pose.mulPose(event.getCamera().rotation());
            var vertices=buffers.getBuffer(ThaumcraftRenderTypes.unlit(ResearchGraphics.texture("misc/particles"),true,false));var m=pose.last().pose();float u=((int)time%16)/16f,v=.3125f,size=.15f;
            // FXBeamWand impact uses one 16x16 tile; the 8x8 spark sampler exposed four tiles.
            vertices.addVertex(m,-size,-size,0).setUv(u+.0624375f,v+.0624375f).setColor(0f,1f,.4f,.66f);vertices.addVertex(m,size,-size,0).setUv(u,v+.0624375f).setColor(0f,1f,.4f,.66f);vertices.addVertex(m,size,size,0).setUv(u,v).setColor(0f,1f,.4f,.66f);vertices.addVertex(m,-size,size,0).setUv(u+.0624375f,v).setColor(0f,1f,.4f,.66f);pose.popPose();
        }
    }
    private static void beam(com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,Vec3 start,Vec3 end,float time){
        var delta=end.subtract(start);var axis=delta.normalize();var reference=Math.abs(axis.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0);var first=axis.cross(reference).normalize();var second=axis.cross(first);var vertices=buffers.getBuffer(ThaumcraftRenderTypes.unlit(ResearchGraphics.texture("misc/beam2"),true,false));
        for(int i=0;i<3;i++){double angle=Math.toRadians(time*5+i*60);var cross=first.scale(Math.cos(angle)*.15).add(second.scale(Math.sin(angle)*.15));var far=cross.scale(2);float scroll=-time*.2f+i/3f;Vec3[] points={end.subtract(far),start.subtract(cross),start.add(cross),end.add(far)};for(int c=0;c<4;c++)vertices.addVertex(pose.last().pose(),(float)points[c].x,(float)points[c].y,(float)points[c].z).setUv(c<2?1:0,scroll+(c==0||c==3?(float)delta.length():0)).setColor(0f,1f,.4f,.4f);}
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){beams.clear();}
    private FocusDigEffects(){}
}
