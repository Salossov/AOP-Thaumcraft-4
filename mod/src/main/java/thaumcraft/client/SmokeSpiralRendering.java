package thaumcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** Preserve the source GL_GREATER 1/255 alpha cutoff instead of vanilla particle .1. */
@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class SmokeSpiralRendering {
    private static ShaderInstance shader;
    static final ParticleRenderType PARTICLES=(tessellator,textures)->{
        RenderSystem.depthMask(false);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(()->shader);RenderSystem.setShaderTexture(0,ResearchGraphics.texture("misc/particles"));
        return tessellator.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.PARTICLE);
    };
    @SubscribeEvent public static void register(RegisterShadersEvent event)throws java.io.IOException{
        event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.parse("thaumcraft:smoke_spiral"),DefaultVertexFormat.PARTICLE),value->shader=value);
    }
    private SmokeSpiralRendering(){}
}
