package thaumcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import thaumcraft.api.research.ResearchDefinition;

@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class ResearchCompletionOverlay {
    private static ResearchDefinition research;private static long started;
    public static void accept(String key){ResearchHighlights.research(key);research=thaumcraft.network.ClientKnowledge.definitions().get(key);started=System.nanoTime()/1000000;var player=Minecraft.getInstance().player;if(research!=null&&player!=null)player.playSound(thaumcraft.registry.R2Sounds.LEARN.get(),.8f,1);}
    public static void drawTopmost(net.minecraft.client.gui.GuiGraphics g){
        var mc=Minecraft.getInstance();if(research==null||mc.getOverlay()!=null||(mc.screen==null&&mc.options.hideGui))return;double elapsed=(System.nanoTime()/1000000-started)/3000d;if(elapsed>=1){research=null;return;}
        double slide=elapsed*2;if(slide>1)slide=2-slide;slide=Math.max(0,1-slide*4);int y=-(int)(Math.pow(slide,4)*36);
        g.flush();g.setColor(1,1,1,1);com.mojang.blaze3d.systems.RenderSystem.enableBlend();com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();g.pose().pushPose();g.pose().translate(0,y,2000);g.blitSprite(ResourceLocation.parse("minecraft:toast/advancement"),0,0,160,32);
        g.drawString(mc.font,Component.translatable("thaumcraft.research.completed"),30,7,0xffffff00,false);
        var name=Component.translatable("tc.research_name."+research.key());float scale=Math.min(1,125f/Math.max(1,mc.font.width(name)));g.pose().pushPose();g.pose().translate(30,16+2f/scale,0);g.pose().scale(scale,scale,1);g.drawString(mc.font,name,0,0,0xffffffff,false);g.pose().popPose();
        var icon=ResourceLocation.tryParse(research.icon());if(icon!=null){if(icon.getPath().startsWith("textures/"))ResearchGraphics.icon(g,icon,8,8,16,16,1);else g.renderItem(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(icon)),8,8);}
        g.flush();g.pose().popPose();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){research=null;}
    private ResearchCompletionOverlay(){}
}
