package thaumcraft.client;

import java.util.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import thaumcraft.entities.ThaumicGolem;
import thaumcraft.network.GolemDebugPayload;

/** World-space diagnostics. Cyan = real route, gold dashes = intended work target. */
@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class GolemDebugOverlay {
    private static List<GolemDebugPayload.Entry> entries=List.of();
    private static ClientLevel level;
    private static long received;
    public static List<GolemDebugPayload.Entry> entries(){return entries;}
    public static void accept(GolemDebugPayload packet){
        var current=Minecraft.getInstance().level;
        level=current;entries=packet.enabled()&&current!=null&&current.dimension().location().equals(packet.dimension())?packet.golems():List.of();
        received=current==null?0:current.getGameTime();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){var current=Minecraft.getInstance().level;if(current!=level||current==null||current.getGameTime()-received>30){entries=List.of();level=current;}}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){entries=List.of();level=null;}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||entries.isEmpty())return;
        var mc=Minecraft.getInstance();if(mc.level!=level||mc.player==null||mc.options.hideGui||mc.screen!=null)return;
        var pose=WorldOverlayPose.create(event);var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        pose.translate(-camera.x,-camera.y,-camera.z);
        for(var e:entries){
            if(!(level.getEntity(e.id()) instanceof ThaumicGolem golem)||!golem.getUUID().equals(e.uuid())||mc.player.distanceToSqr(golem)>32*32)continue;
            var pos=golem.getPosition(partial);
            for(int i=1;i<e.path().size();i++)line(pose,buffers,foot(e.path().get(i-1)),foot(e.path().get(i)),i<e.next()?0x74808b:0x45e1ff);
            if(e.next()<e.path().size()){var next=foot(e.path().get(e.next()));line(pose,buffers,pos.add(0,.1,0),next,0x45e1ff);box(pose,buffers,next,.13,0xffffff);}
            if(e.target()!=null){var start=pos.add(0,.6,0);var delta=e.target().subtract(start);int count=Math.min(128,Math.max(2,(int)(delta.length()*5)));for(int i=0;i<count;i+=2)line(pose,buffers,start.add(delta.scale(i/(double)count)),start.add(delta.scale(Math.min(i+1,count)/(double)count)),0xffcc4d);box(pose,buffers,e.target(),.52,0xffcc4d);}
            box(pose,buffers,e.home().getCenter(),.18,0x72ee83);box(pose,buffers,e.source().getCenter(),.48,0xbc91ff);
            for(var marker:e.markers())box(pose,buffers,marker.getCenter(),.25,0xff82d1);
            boolean detailed=mc.player.distanceToSqr(golem)<36||mc.hitResult instanceof EntityHitResult hit&&hit.getEntity()==golem;
            int count=detailed?e.lines().size():Math.min(4,e.lines().size());
            pose.pushPose();pose.translate(pos.x,pos.y+golem.getBbHeight()+.85+count*.24,pos.z);pose.mulPose(event.getCamera().rotation());pose.scale(.024f,-.024f,.024f);
            buffers.endBatch();boolean depth=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
            // Complete the entire shadow pass before drawing any foreground glyphs.
            // Built-in shadow ordering in SEE_THROUGH can put dark glyphs in front in world space.
            for(int i=0;i<count;i++){var text=e.lines().get(i);mc.font.drawInBatch(text.getString(),-mc.font.width(text)/2f+1,i*10+1,0xff202020,false,pose.last().pose(),buffers,Font.DisplayMode.SEE_THROUGH,0,LightTexture.FULL_BRIGHT);}
            buffers.endBatch();
            for(int i=0;i<count;i++){var text=e.lines().get(i);int color=i==0?0xffe7cc83:i==2?e.reason().equals("moving")||e.reason().equals("working")?0xff8cefc4:0xffffac89:0xffeeeeee;mc.font.drawInBatch(text,-mc.font.width(text)/2f,i*10,color,false,pose.last().pose(),buffers,Font.DisplayMode.SEE_THROUGH,0,LightTexture.FULL_BRIGHT);}
            buffers.endBatch();if(depth)com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();pose.popPose();
        }
        buffers.endBatch(ThaumcraftRenderTypes.golemRange());buffers.endBatch();
    }
    private static Vec3 foot(net.minecraft.core.BlockPos p){return new Vec3(p.getX()+.5,p.getY()+.08,p.getZ()+.5);}
    private static void line(PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,Vec3 a,Vec3 b,int color){
        var normal=b.subtract(a).normalize();if(normal.lengthSqr()<.01)return;var out=buffers.getBuffer(ThaumcraftRenderTypes.golemRange());
        for(var p:new Vec3[]{a,b})out.addVertex(pose.last(),(float)p.x,(float)p.y,(float)p.z).setColor(color>>16&255,color>>8&255,color&255,220).setNormal(pose.last(),(float)normal.x,(float)normal.y,(float)normal.z);
    }
    private static void box(PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,Vec3 centre,double radius,int color){
        for(int axis=0;axis<3;axis++)for(int a=-1;a<=1;a+=2)for(int b=-1;b<=1;b+=2){var first=switch(axis){case 0->new Vec3(-radius,a*radius,b*radius);case 1->new Vec3(a*radius,-radius,b*radius);default->new Vec3(a*radius,b*radius,-radius);};var second=switch(axis){case 0->new Vec3(radius,a*radius,b*radius);case 1->new Vec3(a*radius,radius,b*radius);default->new Vec3(a*radius,b*radius,radius);};line(pose,buffers,centre.add(first),centre.add(second),color);}
    }
    private GolemDebugOverlay(){}
}
