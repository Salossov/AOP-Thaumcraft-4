package thaumcraft.client;

import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import thaumcraft.config.ThaumcraftClientConfig;
import thaumcraft.config.ThaumcraftClientConfig.GuardianFogMode;
import thaumcraft.network.ClientGuardianMist;

@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class GuardianFogEvents {
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var minecraft=Minecraft.getInstance();if(minecraft.level==null){ClientGuardianMist.clear();return;}
        if(!minecraft.isPaused())ClientGuardianMist.tick();
    }
    @SubscribeEvent public static void fog(ViewportEvent.RenderFog event) {
        float density=ClientGuardianMist.density();var mode=ThaumcraftClientConfig.GUARDIAN_FOG_MODE.get();
        if(density<=0||mode==GuardianFogMode.OFF||mode==GuardianFogMode.SHADER&&!GuardianFogPostProcessor.failed())return;
        // Match the exponential fog's 50% distance using the native smooth fog curve.
        float end=(float)(2*Math.log(2)/density);event.setNearPlaneDistance(Math.min(0,event.getNearPlaneDistance()));
        event.setFarPlaneDistance(Math.min(end,event.getFarPlaneDistance()));event.setFogShape(FogShape.SPHERE);event.setCanceled(true);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_LEVEL&&ThaumcraftClientConfig.GUARDIAN_FOG_MODE.get()==GuardianFogMode.SHADER)
            GuardianFogPostProcessor.render(event.getProjectionMatrix(),ClientGuardianMist.density(),event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {ClientGuardianMist.clear();GuardianFogPostProcessor.reload();}
    private GuardianFogEvents() {}
}
