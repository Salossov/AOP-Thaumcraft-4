package thaumcraft.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import thaumcraft.network.BossStatusPayload;

/** One bar: health, absorption and the active anger timer. */
@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class BossStatusBar {
    private static final Map<UUID,BossStatusPayload> STATUS=new HashMap<>();
    public static void accept(BossStatusPayload packet){if(packet.removed())STATUS.remove(packet.bar());else STATUS.put(packet.bar(),packet);}
    public static int menuTop(){return STATUS.isEmpty()?0:12+(Math.min(3,STATUS.size())-1)*33+16+12;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){STATUS.clear();}
    @SubscribeEvent public static void render(CustomizeGuiOverlayEvent.BossEventProgress event){
        var status=STATUS.get(event.getBossEvent().getId());if(status==null)return;
        event.setCanceled(true);event.setIncrement(33);
        var g=event.getGuiGraphics();int x=event.getX(),y=event.getY(),width=182,height=16,track=154;
        float maximum=Math.max(1,status.maxHealth()+status.maxShield());
        int health=Math.clamp(Math.round(track*Math.max(0,status.health())/maximum),0,track);
        int shield=Math.clamp(Math.round(track*Math.max(0,status.shield())/maximum),0,track-health);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(ResearchGraphics.texture("gui/boss_status_background"),x,y,0,0,width,height,width,height);
        if(health>0)g.blit(ResearchGraphics.texture("gui/boss_status_health"),x+14,y+4,0,0,health,8,track,8);
        if(shield>0)g.blit(ResearchGraphics.texture("gui/boss_status_shield"),x+14+health,y+4,0,0,shield,8,track,8);
        if(status.rage()>0)g.blit(ResearchGraphics.texture("gui/boss_status_rage"),x,y,0,0,width,height,width,height);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        var font=Minecraft.getInstance().font;var title=event.getBossEvent().getName();g.drawString(font,title,x+width/2-font.width(title)/2,y-9,0xffffffff,true);
        var label=Component.translatable("thaumcraft.boss.status",number(status.health()),number(status.maxHealth()),number(status.shield()),number(status.maxShield()),String.format(Locale.ROOT,"%.1f",Math.max(0,status.rage())/20f));
        float scale=Math.min(1,(track-4f)/Math.max(1,font.width(label)));g.pose().pushPose();g.pose().translate(x+width/2f,y+(height-font.lineHeight*scale)/2f,0);g.pose().scale(scale,scale,1);g.drawString(font,label,-font.width(label)/2,0,0xffffffff,true);g.pose().popPose();
    }
    private static String number(float value){return String.format(Locale.ROOT,"%.0f",Math.max(0,value));}
    private BossStatusBar(){}
}
