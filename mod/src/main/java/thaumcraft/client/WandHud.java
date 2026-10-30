package thaumcraft.client;

import com.mojang.math.Axis;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.config.ThaumcraftClientConfig;
import thaumcraft.items.*;

/** TC4's 32px dial, rotated coloured gauges, focus-cost pip, change arrows, focus icon and cooldown. */
@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class WandHud {
    private static final java.text.DecimalFormat COST=new java.text.DecimalFormat("0.##",java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT));
    private static final java.text.DecimalFormat SECONDS=new java.text.DecimalFormat("0.0");
    private static final Map<Aspect,Integer> OLD=new LinkedHashMap<>();private static int slot=-1;private static long next;
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){var mc=Minecraft.getInstance();var player=mc.player;if(player==null||mc.screen!=null||mc.options.hideGui)return;var wand=player.getMainHandItem();if(!(wand.getItem() instanceof CastingWandItem))return;var aspects=thaumcraft.api.aspects.Aspects.PRIMALS;long now=System.currentTimeMillis();if(slot!=player.getInventory().selected||now>=next){slot=player.getInventory().selected;OLD.clear();aspects.forEach(a->OLD.put(a,WandVis.get(wand,a)));next=now+1000;}
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        var g=event.getGuiGraphics();var texture=ResearchGraphics.texture("gui/hud");boolean bottom=ThaumcraftClientConfig.WAND_DIAL_BOTTOM.get();g.pose().pushPose();g.pose().translate(0,bottom?g.guiHeight()-32:0,200);g.pose().pushPose();g.pose().scale(0.5f,0.5f,1);ResearchGraphics.blit(g,texture,0,0,0,0,64,64,256,256);g.pose().popPose();g.pose().translate(16,16,0);
        var focus=WandFocus.get(wand);var focusItem=focus.getItem() instanceof thaumcraft.api.wands.ItemFocusBasic?focus:null;
        int index=0;for(var aspect:aspects){int amount=WandVis.get(wand,aspect);g.pose().pushPose();g.pose().mulPose(Axis.ZP.rotationDegrees((bottom?0:90)-15+index++*24));g.pose().translate(0,-32,0);g.pose().scale(0.5f,0.5f,1);int pixels=30*amount/WandVis.capacity(wand),color=aspect.getColor();g.setColor((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,0.8f);ResearchGraphics.blit(g,texture,-4,35-pixels,104,0,8,pixels,256,256);g.setColor(1,1,1,1);ResearchGraphics.blit(g,texture,-8,-3,72,0,16,42,256,256);
            int shift=0;if(focusItem!=null&&((thaumcraft.api.wands.ItemFocusBasic)focusItem.getItem()).getVisCost(focusItem).getOrDefault(aspect,0)>0){ResearchGraphics.blit(g,texture,-4,-8,136,0,8,8,256,256);shift=8;}
            int previous=OLD.getOrDefault(aspect,amount);if(previous!=amount)ResearchGraphics.blit(g,texture,-4,-8-shift,previous>amount?128:120,0,8,8,256,256);
            if(player.isShiftKeyDown()){g.pose().pushPose();g.pose().mulPose(Axis.ZP.rotationDegrees(-90));g.drawString(mc.font,COST.format(amount/100d),-32,-4,0xffffff);g.pose().popPose();
                if(focusItem!=null&&((thaumcraft.api.wands.ItemFocusBasic)focusItem.getItem()).getVisCost(focusItem).getOrDefault(aspect,0)>0){var item=(thaumcraft.api.wands.ItemFocusBasic)focusItem.getItem();int cost=WandFocus.costs(wand,player,item.getVisCost(focusItem)).getOrDefault(aspect,0);g.pose().pushPose();g.pose().mulPose(Axis.ZP.rotationDegrees(-90));g.drawString(mc.font,COST.format(cost/100f),8,-4,0xffffff);g.pose().popPose();}}
            g.pose().popPose();}
        if(focusItem!=null){g.pose().pushPose();g.pose().translate(-24,-24,90);g.renderItem(focusItem,16,16);g.pose().popPose();
            float cooldown=cooldownLeft(wand);if(cooldown>0){g.pose().pushPose();g.pose().scale(0.5f,0.5f,1);String secs=SECONDS.format(cooldown)+"s";g.drawString(mc.font,secs,-mc.font.width(secs)/2,-4,0xffffff);g.pose().popPose();}}
        g.pose().popPose();
    }
    private static long castReady;
    public static void castAt(long gameTime){castReady=gameTime;}
    private static float cooldownLeft(ItemStack wand){var mc=Minecraft.getInstance();if(mc.level==null)return 0;long left=castReady-mc.level.getGameTime();return left>0?left/20f:0;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){OLD.clear();slot=-1;next=0;castReady=0;}
    private WandHud(){}
}
