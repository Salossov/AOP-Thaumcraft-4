package thaumcraft.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import thaumcraft.blocks.*;
import thaumcraft.registry.R5Content;

/** Native render states own the effect; no fixed-function GL state escapes the draw. */
@EventBusSubscriber(modid="thaumcraft_aop",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class EldritchVoidRenderer implements BlockEntityRenderer<EldritchVoidBlockEntity> {
    private static ShaderInstance shader;
    private static final RenderType NEAR=RenderType.create("thaumcraft_eldritch_void",DefaultVertexFormat.POSITION,VertexFormat.Mode.QUADS,1024,false,false,RenderType.CompositeState.builder().setShaderState(new RenderStateShard.ShaderStateShard(()->{if(shader!=null)shader.safeGetUniform("Clock").set((System.currentTimeMillis()%700000L)/250000f);return shader;})).setTextureState(RenderStateShard.MultiTextureStateShard.builder().add(texture("tunnel"),false,false).add(texture("particlefield"),false,false).build()).setCullState(RenderStateShard.NO_CULL).createCompositeState(false));
    private static final RenderType FAR=RenderType.create("thaumcraft_eldritch_void_far",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,1024,false,false,RenderType.CompositeState.builder().setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader)).setTextureState(new RenderStateShard.TextureStateShard(texture("particlefield32"),false,false)).setCullState(RenderStateShard.NO_CULL).createCompositeState(false));
    private static ResourceLocation texture(String name){return ResourceLocation.parse("thaumcraft:textures/misc/"+name+".png");}
    public EldritchVoidRenderer(BlockEntityRendererProvider.Context context){}
    static void verticalCore(PoseStack pose,MultiBufferSource buffers,boolean near,float height){var vertices=buffers.getBuffer(near?NEAR:FAR);var matrix=pose.last().pose();for(var face:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST})for(int i=0;i<4;i++){var v=EldritchVoidGeometry.vertex(face,i);int axis=face.getAxis()==Direction.Axis.X?0:2;v[axis]=face.getAxisDirection()==Direction.AxisDirection.POSITIVE?.99f:.01f;vertices.addVertex(matrix,v[0],v[1]*height,v[2]);if(!near)vertices.setUv(v[3],v[4]).setColor(127,127,127,255);}}
    static void lockPlane(PoseStack pose,MultiBufferSource buffers,Direction facing,boolean near){var vertices=buffers.getBuffer(near?NEAR:FAR);var matrix=pose.last().pose();for(int i=0;i<4;i++){var v=EldritchLockGeometry.planeVertex(facing,i);vertices.addVertex(matrix,v[0],v[1],v[2]);if(!near)vertices.setUv(v[3],v[4]).setColor(127,127,127,255);}}
    @SubscribeEvent public static void shaders(RegisterShadersEvent event)throws java.io.IOException{event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.parse("thaumcraft:eldritch_void"),DefaultVertexFormat.POSITION),value->shader=value);}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(R5Content.VOID_ENTITY.get(),EldritchVoidRenderer::new);}
    @Override public void render(EldritchVoidBlockEntity tile,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var level=tile.getLevel();if(level==null)return;
        var cameraEntity=Minecraft.getInstance().getCameraEntity();
        boolean near=cameraEntity!=null&&EldritchVoidGeometry.near(cameraEntity.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(tile.getBlockPos())));
        var vertices=buffers.getBuffer(near?NEAR:FAR);var matrix=pose.last().pose();
        for(var face:Direction.values()){var neighbor=tile.getBlockPos().relative(face);if(level.getBlockState(neighbor).isSolidRender(level,neighbor))continue;for(int i=0;i<4;i++){var v=EldritchVoidGeometry.vertex(face,i);vertices.addVertex(matrix,v[0],v[1],v[2]);if(!near)vertices.setUv(v[3],v[4]).setColor(127,127,127,255);}}
    }
}
