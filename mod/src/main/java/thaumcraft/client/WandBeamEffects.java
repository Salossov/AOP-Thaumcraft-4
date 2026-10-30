package thaumcraft.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import thaumcraft.api.client.WandBeamApi;

/** Fresh, eight-tick source beams. RenderType scopes culling, additive blending and depth writes. */
@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class WandBeamEffects {
    private static final List<Beam> ACTIVE=new ArrayList<>();
    static void install(){WandBeamApi.install((player,effect)->{
        var minecraft=Minecraft.getInstance();var displayed=minecraft.level;
        if(displayed==null||player.level()!=displayed)return;
        minecraft.execute(()->{if(minecraft.level==displayed&&!player.isRemoved())ACTIVE.add(new Beam(player,effect));});
    });}
    private static final class Beam {
        final Player player;final net.minecraft.world.level.Level level;final WandBeamApi.Beam effect;
        int age;float previousSize;WandBeamShape.Aim aim;float previousYaw,previousPitch;
        Beam(Player player,WandBeamApi.Beam effect){this.player=player;level=player.level();this.effect=effect;aim=WandBeamShape.aim(player.getEyePosition(),effect.target());previousYaw=aim.yaw();previousPitch=aim.pitch();}
        boolean tick(){
            previousYaw=aim.yaw();previousPitch=aim.pitch();aim=WandBeamShape.aim(player.getEyePosition(),effect.target());
            previousYaw=WandBeamShape.unwrap(previousYaw,aim.yaw());previousPitch=WandBeamShape.unwrap(previousPitch,aim.pitch());
            return age++>=8;
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var minecraft=Minecraft.getInstance();
        ACTIVE.removeIf(beam->minecraft.level!=beam.level||beam.player.isRemoved());
        if(!minecraft.isPaused())ACTIVE.removeIf(Beam::tick);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||ACTIVE.isEmpty())return;
        var minecraft=Minecraft.getInstance();if(minecraft.level==null||minecraft.player==null)return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var camera=event.getCamera().getPosition();var buffers=minecraft.renderBuffers().bufferSource();
        for(var beam:ACTIVE){
            if(beam.level!=minecraft.level||beam.player.isRemoved())continue;
            var pose=WorldOverlayPose.create(event);var player=beam.player;
            var current=player.getEyePosition();var previous=new Vec3(player.xo,player.yo+player.getEyeHeight(),player.zo);
            var at=WandBeamShape.origin(previous,current,player.yRotO,player.getYRot(),WandBeamShape.look(player.getYRot(),player.getXRot()),camera,partial);
            var frame=WandBeamShape.frame(beam.age,8,beam.previousSize,partial,minecraft.player.tickCount,minecraft.level.getDayTime(),beam.effect.reverse());
            float yaw=(float)((double)beam.previousYaw+(double)(beam.aim.yaw()-beam.previousYaw)*(double)partial);
            float pitch=(float)((double)beam.previousPitch+(double)(beam.aim.pitch()-beam.previousPitch)*(double)partial);
            pose.translate(at.x,at.y,at.z);pose.mulPose(Axis.XP.rotationDegrees(90));pose.mulPose(Axis.ZN.rotationDegrees(180+yaw));
            pose.mulPose(Axis.XP.rotationDegrees(pitch));pose.mulPose(Axis.YP.rotationDegrees(frame.rotation()));
            var type=WandBeamRendering.type(beam.effect.texture());var vertices=buffers.getBuffer(type);int rgb=beam.effect.color();
            for(int ribbon=0;ribbon<3;ribbon++){
                var shape=WandBeamShape.ribbon(beam.aim.length(),frame,beam.effect.endWidth(),ribbon);pose.mulPose(Axis.YP.rotationDegrees(60));
                vertex(vertices,pose,shape.farLeft(),shape.length(),1,shape.v1(),rgb,frame.opacity());
                vertex(vertices,pose,shape.nearLeft(),0,1,shape.v0(),rgb,frame.opacity());
                vertex(vertices,pose,shape.nearRight(),0,0,shape.v0(),rgb,frame.opacity());
                vertex(vertices,pose,shape.farRight(),shape.length(),0,shape.v1(),rgb,frame.opacity());
            }
            // The source updates prevSize per render, not per tick. Keep that pulse.
            beam.previousSize=frame.size();buffers.endBatch(type);
        }
    }
    private static void vertex(VertexConsumer out,com.mojang.blaze3d.vertex.PoseStack pose,double x,double y,float u,double v,int rgb,float alpha){
        out.addVertex(pose.last().pose(),(float)x,(float)y,0).setUv(u,(float)v)
                .setColor((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f,alpha).setLight(200);
    }
    private WandBeamEffects(){}
}
