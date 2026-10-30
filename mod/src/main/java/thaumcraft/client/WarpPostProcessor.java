package thaumcraft.client;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.lwjgl.opengl.*;
import thaumcraft.effects.WarpEffects;

/** Compose on the completed framebuffer without replacing vanilla or Sodium shaders. */
@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class WarpPostProcessor {
    private static PostChain chain;private static final java.util.List<net.minecraft.client.renderer.PostPass> passes=new java.util.ArrayList<>();private static com.mojang.blaze3d.pipeline.TextureTarget backup;private static RenderTarget main;private static int width,height;private static boolean failed;
    public static void reload(){if(chain!=null)chain.close();if(backup!=null)backup.destroyBuffers();chain=null;passes.clear();backup=null;main=null;failed=false;}
    private static void prepare(Minecraft mc)throws java.io.IOException{var target=mc.getMainRenderTarget();if(chain!=null&&target==main&&width==target.width&&height==target.height)return;reload();main=target;width=target.width;height=target.height;chain=new PostChain(mc.getTextureManager(),mc.getResourceManager(),main,ResourceLocation.parse("thaumcraft:shaders/post/warp.json"));var swap=chain.getTempTarget("warp_swap");passes.add(chain.addPass("thaumcraft:warp",main,swap,false));passes.add(chain.addPass("thaumcraft:warp",swap,main,false));for(int i=0;i<passes.size();i++){passes.get(i).getEffect().safeGetUniform("BlurDir").set(i==0?1f:0f,i==0?0f:1f);passes.get(i).getEffect().safeGetUniform("ApplyColor").set(i==0?0f:1f);}chain.resize(width,height);backup=new TextureTarget(width,height,true,Minecraft.ON_OSX);}
    private static void copy(RenderTarget source,RenderTarget target){GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,source.frameBufferId);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,target.frameBufferId);GL30.glBlitFramebuffer(0,0,source.width,source.height,0,0,target.width,target.height,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||failed||!thaumcraft.config.ThaumcraftClientConfig.WARP_SHADERS.get())return;var mc=Minecraft.getInstance();if(mc.player==null)return;boolean blur=mc.player.hasEffect(WarpEffects.BLURRED_VISION),hunger=mc.player.hasEffect(WarpEffects.UNNATURAL_HUNGER),death=mc.player.hasEffect(WarpEffects.DEATH_GAZE),sun=mc.player.hasEffect(WarpEffects.SUN_SCORNED);if(!blur&&!hunger&&!death&&!sun)return;
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);int depthFunction=GL11.glGetInteger(GL11.GL_DEPTH_FUNC),srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);boolean saved=false;
        try{prepare(mc);copy(main,backup);saved=true;for(var pass:passes){var shader=pass.getEffect();shader.safeGetUniform("BlurRadius").set(blur?10f:0);shader.safeGetUniform("Saturation").set((death?.8f:1)*(hunger?1.1f:1));shader.safeGetUniform("ColorScale").set(1f,hunger?.8f:1f,hunger?.8f:1f);shader.safeGetUniform("SunScorned").set(sun?1f:0);shader.safeGetUniform("Vignette").set(death?.85f:0);}RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.depthMask(true);chain.process(event.getPartialTick().getGameTimeDeltaPartialTick(false));main.copyDepthFrom(backup);}catch(java.io.IOException|RuntimeException error){if(saved)try{copy(backup,main);}catch(RuntimeException restore){error.addSuppressed(restore);}reload();failed=true;thaumcraft.Thaumcraft.LOGGER.error("Warp postprocessing failed; disabled until resource reload",error);}finally{mc.getMainRenderTarget().bindWrite(true);RenderSystem.depthFunc(depthFunction);RenderSystem.depthMask(depthMask);RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();}}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){reload();}
    private WarpPostProcessor(){}
}
