package thaumcraft.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import thaumcraft.visnet.VisRelayBlockEntity;

@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class VisRelayOverlay {
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.screen!=null||mc.options.hideGui||!(mc.hitResult instanceof BlockHitResult hit)||!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof VisRelayBlockEntity relay))return;
        if(!thaumcraft.api.EquipmentVision.canSeeNodes(mc.player))return;
        var g=event.getGuiGraphics();var font=mc.font;int x=8,y=8,width=Math.max(40,Math.min(290,g.guiWidth()-16));
        if(mc.player.getMainHandItem().getItem() instanceof thaumcraft.items.CastingWandItem&&!thaumcraft.config.ThaumcraftClientConfig.WAND_DIAL_BOTTOM.get())y=110;
        boolean connected=relay.getRootSource()!=null;
        var heading=font.split(Component.translatable("thaumcraft.vis_relay.configured",relay.attunementName()),width-22);
        var status=font.split(Component.translatable(connected?"thaumcraft.vis_relay.connected":"thaumcraft.vis_relay.disconnected"),width-22);
        var hints=List.of(font.split(instruction("tune"),width),font.split(Component.translatable("thaumcraft.vis_relay.automatic"),width),font.split(instruction("reset"),width));
        int top=Math.max(20,(heading.size()+status.size())*font.lineHeight+3),height=top+3;
        for(var lines:hints)height+=lines.size()*font.lineHeight+3;height-=3;
        int actual=22;for(var line:heading)actual=Math.max(actual,22+font.width(line));for(var line:status)actual=Math.max(actual,22+font.width(line));for(var lines:hints)for(var line:lines)actual=Math.max(actual,font.width(line));
        g.pose().pushPose();g.pose().translate(0,0,250);background(g,x,y,actual,height);
        g.renderItem(new net.minecraft.world.item.ItemStack(thaumcraft.registry.R3Content.BALANCED_SHARD.get()),x,y);
        int lineY=draw(g,heading,x+22,y,0xffffe0a0);draw(g,status,x+22,lineY+3,connected?0xff55ff55:0xffff5555);
        lineY=y+top+3;for(var lines:hints)lineY=draw(g,lines,x,lineY,0xffe0d6c5)+3;
        g.flush();g.pose().popPose();
    }
    private static Component instruction(String key){return Component.translatable("thaumcraft.vis_relay."+key,Component.translatable("thaumcraft.vis_relay."+key+".action").withStyle(net.minecraft.ChatFormatting.BOLD,net.minecraft.ChatFormatting.UNDERLINE));}
    private static int draw(net.minecraft.client.gui.GuiGraphics g,List<net.minecraft.util.FormattedCharSequence> lines,int x,int y,int color){var font=Minecraft.getInstance().font;for(var line:lines){g.drawString(font,line,x,y,color,true);y+=font.lineHeight;}return y;}
    /** Vanilla tooltip silhouette and purple frame, with a 63% opaque background. */
    private static void background(net.minecraft.client.gui.GuiGraphics g,int x,int y,int w,int h){
        int bg=0xa0100010,top=0x805000ff,bottom=0x8028007f;
        g.fill(x-3,y-4,x+w+3,y-3,bg);g.fill(x-3,y+h+3,x+w+3,y+h+4,bg);g.fill(x-3,y-3,x+w+3,y+h+3,bg);
        g.fill(x-4,y-3,x-3,y+h+3,bg);g.fill(x+w+3,y-3,x+w+4,y+h+3,bg);
        g.fillGradient(x-3,y-2,x-2,y+h+2,top,bottom);g.fillGradient(x+w+2,y-2,x+w+3,y+h+2,top,bottom);
        g.fill(x-3,y-3,x+w+3,y-2,top);g.fill(x-3,y+h+2,x+w+3,y+h+3,bottom);
    }
    private VisRelayOverlay(){}
}
