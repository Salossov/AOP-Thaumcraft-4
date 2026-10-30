package thaumcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import thaumcraft.production.ArcaneBoreBlockEntity;

@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class BoreProgressHud {
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.options.hideGui||mc.screen!=null||!(mc.hitResult instanceof BlockHitResult hit)||!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof ArcaneBoreBlockEntity bore)||bore.base()||bore.target()==null)return;
        var g=event.getGuiGraphics();int x=Math.min(g.guiWidth()-126,g.guiWidth()/2+12),y=g.guiHeight()/2+14;float progress=bore.miningProgress(mc.getTimer().getGameTimeDeltaPartialTick(false));
        g.fill(x-4,y-4,x+122,y+26,0x99301e0d);g.drawString(mc.font,Component.translatable("thaumcraft.bore.progress",Math.round(progress*100)),x,y,0xffffe0a0,true);g.fill(x,y+14,x+118,y+20,0xff352d27);g.fill(x,y+14,x+Math.round(118*progress),y+20,bore.powered()?0xff69cf8b:0xffad8b55);
    }
    private BoreProgressHud(){}
}
