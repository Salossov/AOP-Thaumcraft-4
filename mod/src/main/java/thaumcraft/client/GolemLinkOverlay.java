package thaumcraft.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import thaumcraft.entities.*;
import thaumcraft.items.*;

/** TC4 face glyphs and the flowing script ribbon from a selected golem's head. */
@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class GolemLinkOverlay {
    private static final ResourceLocation MARK=ResourceLocation.parse("thaumcraft:textures/misc/mark.png"),HOME=ResourceLocation.parse("thaumcraft:textures/misc/home.png"),SCRIPT=ResourceLocation.parse("thaumcraft:textures/misc/script.png"),EMPTY=ResourceLocation.parse("thaumcraft:textures/blocks/empty.png");
    private static final int[] COLORS={0xF0F0F0,15435844,12801229,6719955,14602026,4312372,14188952,0x434343,0xA0A0A0,2651799,8073150,2437522,5320730,3887386,11743532,0x1E1B1B};
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;var stack=mc.player.getMainHandItem();boolean bell=stack.getItem() instanceof GolemBellItem;if(!bell&&!(stack.getItem() instanceof GolemPlacerItem))return;
        var golem=bell?GolemBellItem.selected(stack,mc.level):null;var data=GolemBellItem.data(stack);if(bell&&data.hasUUID("Golem")&&golem==null)return;var markers=GolemBellItem.markers(stack);if(markers.isEmpty()&&golem==null)return;
        var pose=WorldOverlayPose.create(event);var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false),time=(float)(System.nanoTime()/30000000L);int quality=thaumcraft.config.ThaumcraftClientConfig.GOLEM_LINK_QUALITY.get();
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        if(golem!=null){range(pose,buffers,BlockPos.of(data.getLong("Home")).getCenter(),golem.range(),0xe8c568,110);double search=switch(golem.core()){case 3->Math.ceil(golem.range()/4);case 7->Math.ceil(golem.range()/3);case 11->Math.ceil(golem.range()/2);default->golem.range();};if(search<golem.range()){var home=BlockPos.of(data.getLong("Home"));if(golem.core()==3)cropBounds(pose,buffers,home,(int)search);else range(pose,buffers,home.getCenter(),search,0x67d8cd,160);}}
        if(golem!=null&&data.contains("Home")&&mc.player.distanceToSqr(Vec3.atLowerCornerOf(BlockPos.of(data.getLong("Home"))))<4096){var pos=BlockPos.of(data.getLong("Home"));var face=Direction.from3DDataValue(data.getByte("HomeFace"));faceGlyph(pose,buffers,HOME,pos.relative(face.getOpposite()).getCenter().add(Vec3.atLowerCornerOf(face.getNormal()).scale(.51)),face,.65f,-1,time);}
        for(var marker:markers){if(!marker.dimension().equals(mc.level.dimension())||mc.player.distanceToSqr(marker.pos().relative(marker.face()).getCenter())>=4096)continue;
            var face=marker.face();var center=marker.pos().getCenter().add(Vec3.atLowerCornerOf(face.getNormal()).scale(.51));faceGlyph(pose,buffers,MARK,center,face,.4f,marker.color(),time);
            if(mc.level.isEmptyBlock(marker.pos()))for(var side:Direction.values())faceGlyph(pose,buffers,EMPTY,marker.pos().getCenter().add(Vec3.atLowerCornerOf(side.getNormal()).scale(.49)),side,.98f,marker.color(),time);
            if(golem!=null&&quality>3)ribbon(pose,buffers,golem.getPosition(partial).add(0,golem.getBbHeight(),0),marker,quality,time,golem.getBbHeight());
        }
        pose.popPose();buffers.endBatch(ThaumcraftRenderTypes.golemRange());for(var texture:new ResourceLocation[]{MARK,HOME,SCRIPT,EMPTY})buffers.endBatch(ThaumcraftRenderTypes.node(texture,true,false));
    }
    private static void cropBounds(PoseStack pose,MultiBufferSource buffers,BlockPos home,int radius){
        // Original crop search covers a square of columns and home Y +/- 3, not a sphere.
        double[] xs={home.getX()-radius,home.getX()+radius+1},ys={home.getY()-3,home.getY()+4},zs={home.getZ()-radius,home.getZ()+radius+1};var out=buffers.getBuffer(ThaumcraftRenderTypes.golemRange());
        for(int axis=0;axis<3;axis++)for(int a=0;a<2;a++)for(int b=0;b<2;b++){
            var first=switch(axis){case 0->new Vec3(xs[0],ys[a],zs[b]);case 1->new Vec3(xs[a],ys[0],zs[b]);default->new Vec3(xs[a],ys[b],zs[0]);};var second=switch(axis){case 0->new Vec3(xs[1],ys[a],zs[b]);case 1->new Vec3(xs[a],ys[1],zs[b]);default->new Vec3(xs[a],ys[b],zs[1]);};var normal=second.subtract(first).normalize();
            for(var point:new Vec3[]{first,second})out.addVertex(pose.last(),(float)point.x,(float)point.y,(float)point.z).setColor(103,216,205,160).setNormal(pose.last(),(float)normal.x,(float)normal.y,(float)normal.z);
        }
    }
    private static void range(PoseStack pose,MultiBufferSource buffers,Vec3 centre,double radius,int color,int alpha){
        var out=buffers.getBuffer(ThaumcraftRenderTypes.golemRange());for(int plane=0;plane<3;plane++)for(int i=0;i<96;i++){double a=i*Math.PI/48,b=(i+1)*Math.PI/48;Vec3 first=ring(plane,a,radius).add(centre),second=ring(plane,b,radius).add(centre);var normal=second.subtract(first).normalize();for(var point:new Vec3[]{first,second})out.addVertex(pose.last(),(float)point.x,(float)point.y,(float)point.z).setColor(color>>16&255,color>>8&255,color&255,alpha).setNormal(pose.last(),(float)normal.x,(float)normal.y,(float)normal.z);}
    }
    private static Vec3 ring(int plane,double angle,double radius){double a=Math.cos(angle)*radius,b=Math.sin(angle)*radius;return switch(plane){case 0->new Vec3(a,0,b);case 1->new Vec3(a,b,0);default->new Vec3(0,a,b);};}
    private static float[] color(int color,float time,int side,int step){if(color>=0){int c=COLORS[color];return new float[]{(c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f};}return new float[]{net.minecraft.util.Mth.sin(time%32767/12+side+step)*.2f+.8f,net.minecraft.util.Mth.sin(time%32767/14+side+step)*.2f+.8f,net.minecraft.util.Mth.sin(time%32767/16+side+step)*.2f+.8f};}
    private static void faceGlyph(PoseStack pose,MultiBufferSource buffers,ResourceLocation texture,Vec3 center,Direction side,float size,int color,float time){
        var normal=Vec3.atLowerCornerOf(side.getNormal());var right=switch(side){case UP,DOWN->new Vec3(1,0,0);case EAST->new Vec3(0,0,-1);case WEST->new Vec3(0,0,1);case NORTH->new Vec3(0,1,0);case SOUTH->new Vec3(0,-1,0);};var up=switch(side){case UP->new Vec3(0,0,-1);case DOWN->new Vec3(0,0,1);case EAST,WEST->new Vec3(0,1,0);case NORTH->new Vec3(-1,0,0);case SOUTH->new Vec3(1,0,0);};var rgb=color(color,time,side.get3DDataValue(),0);var out=buffers.getBuffer(ThaumcraftRenderTypes.node(texture,true,false));
        for(int i=0;i<4;i++){var pos=center.add(right.scale((i==0||i==3?-.5:.5)*size)).add(up.scale((i<2?.5:-.5)*size));vertex(pose,out,pos,i==0||i==3?0:1,i<2?1:0,rgb,1);}
    }
    public record RibbonPoint(Vec3 low,Vec3 high,float u,float alpha){}
    /** Geometry is public so native client verification can check motion, endpoints and all six faces. */
    public static java.util.List<RibbonPoint> points(Vec3 origin,GolemMarker marker,int quality,float time,double height){
        var result=new java.util.ArrayList<RibbonPoint>();int side=marker.face().get3DDataValue();var n=Vec3.atLowerCornerOf(marker.face().getNormal());var middle=marker.pos().getCenter().add(n.scale(.5));var end=middle;var control=middle.add(n.scale(.5));var lineCoordinate=Vec3.atLowerCornerOf(marker.pos()).add(n.scale(.5));float distance=(float)lineCoordinate.distanceTo(origin.subtract(0,height,0));int segments=Math.round(distance)*quality;if(segments<=0)return result;
        for(int i=0;i<=segments;i++){float fraction=i/(float)segments,envelope=1-Math.abs(i-segments/2f)/(segments/2f),fade=0;var wobble=new Vec3(net.minecraft.util.Mth.sin((float)(side*20+lineCoordinate.z%16+distance*(1-fraction)*quality-time%32767/5)/4)*.5f*envelope,net.minecraft.util.Mth.sin((float)(side*20+lineCoordinate.x%16+distance*(1-fraction)*quality-time%32767/5)/3)*.5f*envelope,net.minecraft.util.Mth.sin((float)(side*20+lineCoordinate.y%16+distance*(1-fraction)*quality-time%32767/5)/2)*.5f*envelope);
            if(i>segments-quality/2){float blend=(segments-i)/(quality/2f);fade=1-blend;control=middle.add(n.scale(.5)).lerp(end,fade);}
            var p=origin.add(control.subtract(origin).add(wobble).scale(fraction));result.add(new RibbonPoint(p.add(0,-.05,0),p.add(0,.05,0),(1-fraction)*distance-time*.005f,Math.min(.75f,fraction*1.5f)*(1-fade)));
        }return result;
    }
    private static void ribbon(PoseStack pose,MultiBufferSource buffers,Vec3 origin,GolemMarker marker,int quality,float time,double height){var points=points(origin,marker,quality,time,height);var out=buffers.getBuffer(ThaumcraftRenderTypes.node(SCRIPT,true,false));for(int i=1;i<points.size();i++){var a=points.get(i-1);var b=points.get(i);var ca=color(marker.color(),time,marker.face().get3DDataValue(),i-1);var cb=color(marker.color(),time,marker.face().get3DDataValue(),i);vertex(pose,out,a.low(),a.u(),1,ca,a.alpha());vertex(pose,out,a.high(),a.u(),0,ca,a.alpha());vertex(pose,out,b.high(),b.u(),0,cb,b.alpha());vertex(pose,out,b.low(),b.u(),1,cb,b.alpha());}}
    private static void vertex(PoseStack pose,VertexConsumer out,Vec3 pos,float u,float v,float[] rgb,float alpha){out.addVertex(pose.last(),(float)pos.x,(float)pos.y,(float)pos.z).setColor(rgb[0],rgb[1],rgb[2],alpha).setUv(u,v).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(200).setNormal(pose.last(),0,1,0);}
    private GolemLinkOverlay(){}
}
