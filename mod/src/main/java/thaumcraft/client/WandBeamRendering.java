package thaumcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL11;

@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class WandBeamRendering extends RenderType {
    private static ShaderInstance shader;
    private static final TransparencyStateShard ADDITIVE=new TransparencyStateShard("tc4_wand_beam_additive",()->{
        RenderSystem.enableBlend();RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
    },()->{RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
    private static final RenderType[] TYPES=new RenderType[4];
    static RenderType type(int index){
        if(TYPES[index]==null){
            var texture=ResearchGraphics.texture("misc/beam"+(index==0?"":index));
            var repeat=new TextureStateShard(texture,false,false){
                private int previousS,previousT;
                private void bound(Runnable action){
                    int unit=GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
                    int binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);Minecraft.getInstance().getTextureManager().getTexture(texture).bind();
                    try{action.run();}finally{com.mojang.blaze3d.platform.GlStateManager._bindTexture(binding);RenderSystem.activeTexture(unit);}
                }
                @Override public void setupRenderState(){super.setupRenderState();bound(()->{previousS=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S);previousT=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL11.GL_REPEAT);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL11.GL_REPEAT);});}
                @Override public void clearRenderState(){bound(()->{GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,previousS);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,previousT);});super.clearRenderState();}
            };
            TYPES[index]=create("thaumcraft_wand_beam_"+index,DefaultVertexFormat.PARTICLE,VertexFormat.Mode.QUADS,256,false,false,
                    CompositeState.builder().setShaderState(new ShaderStateShard(()->shader)).setTextureState(repeat)
                            .setTransparencyState(ADDITIVE).setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE).setLightmapState(LIGHTMAP).createCompositeState(false));
        }
        return TYPES[index];
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(WandBeamEffects::install);}
    @SubscribeEvent public static void register(RegisterShadersEvent event)throws java.io.IOException{
        event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.parse("thaumcraft:wand_beam"),DefaultVertexFormat.PARTICLE),value->shader=value);
    }
    private WandBeamRendering(String name,VertexFormat format,VertexFormat.Mode mode,int size,boolean crumble,boolean sorted,Runnable setup,Runnable clear){super(name,format,mode,size,crumble,sorted,setup,clear);}
}
