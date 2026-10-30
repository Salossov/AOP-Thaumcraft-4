package thaumcraft.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.nodes.NodeState;
import thaumcraft.blocks.AuraNodeBlockEntity;
import thaumcraft.items.ThaumometerItem;
import thaumcraft.registry.R2Content;

/** Original 32-frame aspect rings, core strips, brightness and distance fading. */
@net.neoforged.fml.common.EventBusSubscriber(modid="thaumcraft_aop",bus=net.neoforged.fml.common.EventBusSubscriber.Bus.MOD,value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class AuraNodeRenderer implements BlockEntityRenderer<AuraNodeBlockEntity> {
    private static final ResourceLocation TEXTURE=ResourceLocation.parse("thaumcraft:textures/misc/nodes.png");
    private static ShaderInstance shader;
    static ShaderInstance shader(){return shader;}
    @net.neoforged.bus.api.SubscribeEvent public static void shaders(net.neoforged.neoforge.client.event.RegisterShadersEvent event)throws java.io.IOException{
        event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.parse("thaumcraft:aura_node"),DefaultVertexFormat.POSITION_TEX_COLOR),value->shader=value);
    }
    public AuraNodeRenderer(BlockEntityRendererProvider.Context context){}
    public static boolean revealed(){var player=Minecraft.getInstance().player;return player!=null&&(thaumcraft.api.EquipmentVision.canSeeNodes(player)||player.getMainHandItem().getItem() instanceof ThaumometerItem);}
    @Override public int getViewDistance(){return 64;}
    @Override public boolean shouldRenderOffScreen(AuraNodeBlockEntity node){return true;}
    @Override public void render(AuraNodeBlockEntity node,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        RelicHud.note(node);
        NodeChargeBeam.render(node,partial,pose,buffers);
        renderAura(node.getBlockPos(),node.state.current,node.state.type,node.state.modifier,false,pose,buffers);
    }
    static void renderAura(net.minecraft.core.BlockPos pos,java.util.Map<String,Integer> aspects,NodeState.Type type,NodeState.Modifier modifier,boolean forceVisible,PoseStack pose,MultiBufferSource buffers){
        var mc=Minecraft.getInstance();var viewer=mc.getCameraEntity();if(viewer==null)return;boolean goggles=forceVisible||mc.player!=null&&thaumcraft.api.EquipmentVision.canSeeNodes(mc.player),visible=forceVisible||revealed();double range=goggles?64:48,distance=viewer.position().distanceTo(pos.getCenter());if(distance>64)return;
        int frame=(int)Math.floorMod(System.nanoTime()/40000000+pos.getX(),32);float alpha=(float)((range-distance)/range),angle=0;long time=System.nanoTime()/5000000;
        if(!visible||aspects.isEmpty()){strip(pose,buffers,frame,1,0,0.5f,0.1f,0xffffff,true,false);return;}if(distance>range)return;
        switch(modifier){case BRIGHT->alpha*=1.5f;case PALE->alpha*=0.66f;case FADING->alpha*=Mth.sin(viewer.tickCount/3f)*0.25f+0.33f;default->{}}
        int count=0;float total=0;for(var entry:aspects.entrySet()){var aspect=Aspect.getAspect(entry.getKey());if(aspect==null)continue;boolean additive=aspect.getBlend()!=771;float opacity=alpha*(additive?1:1.5f);total+=entry.getValue();float scale=0.2f+(Mth.sin(viewer.tickCount/(14f-count))*0.25f+0.5f)*(entry.getValue()/50f);angle=(time%(5000+500*count))/(5000f+500*count)*Mth.TWO_PI;strip(pose,buffers,frame,0,angle,scale,opacity/Math.max(1,aspects.size()/2f),aspect.getColor(),additive,true);count++;}
        float core=0.1f+(count==0?0:total/count)/150;int row=switch(type){case NORMAL->1;case DARK->2;case HUNGRY->3;case PURE->4;case TAINTED->5;case UNSTABLE->6;};if(type==NodeState.Type.HUNGRY)core*=0.75f;if(type==NodeState.Type.UNSTABLE)angle=0;
        strip(pose,buffers,frame,row,angle,core,alpha,0xffffff,type!=NodeState.Type.DARK&&type!=NodeState.Type.TAINTED,true);
    }
    private static void strip(PoseStack pose,MultiBufferSource buffers,int frame,int row,float angle,float scale,float alpha,int color,boolean additive,boolean throughWalls){
        pose.pushPose();pose.translate(.5,.5,.5);
        // Item GUI poses reflect Y. A quaternion cannot undo a reflection, which flattened the jar's node.
        // Transform the two screen axes through the full inverse linear transform instead.
        var composite=new org.joml.Matrix3f(new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).mul(pose.last().pose()));
        float size=composite.transform(new org.joml.Vector3f(1,0,0)).length();var inverse=new org.joml.Matrix3f(composite).invert();
        var vertices=buffers.getBuffer(ThaumcraftRenderTypes.auraNode(TEXTURE,additive,throughWalls));float u=frame/32f,v=row/32f;int opacity=Math.clamp((int)(alpha*255),0,255);float sin=Mth.sin(angle),cos=Mth.cos(angle);
        for(int i=0;i<4;i++){float x=i<2?-scale:scale,y=i==0||i==3?-scale:scale;var delta=inverse.transform(new org.joml.Vector3f((x*cos-y*sin)*size,(x*sin+y*cos)*size,0));vertices.addVertex(pose.last().pose(),delta.x,delta.y,delta.z).setColor(color>>16&255,color>>8&255,color&255,opacity).setUv(i<2?u+1/32f:u,i==0||i==3?v+1/32f:v);}
        pose.popPose();
    }
}
