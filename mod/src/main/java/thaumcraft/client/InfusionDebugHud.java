package thaumcraft.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.network.InfusionDebugRequestPayload;
import thaumcraft.production.InfusionMatrixBlockEntity;

@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class InfusionDebugHud {
    private static boolean enabled;
    private static BlockPos requested;
    private static long nextRefresh;
    public static void enabled(boolean value){enabled=value;requested=null;nextRefresh=0;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(!enabled||mc.level==null||mc.player==null||!(mc.hitResult instanceof BlockHitResult hit)||!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof InfusionMatrixBlockEntity))return;
        if(!hit.getBlockPos().equals(requested)||mc.level.getGameTime()>=nextRefresh){requested=hit.getBlockPos();nextRefresh=mc.level.getGameTime()+20;PacketDistributor.sendToServer(new InfusionDebugRequestPayload(requested));}
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(!enabled||mc.level==null||mc.player==null||mc.options.hideGui||mc.screen!=null)return;var lines=new ArrayList<Component>();
        if(mc.hitResult instanceof BlockHitResult hit&&mc.level.getBlockEntity(hit.getBlockPos()) instanceof InfusionMatrixBlockEntity tile){
            lines.add(Component.translatable("thaumcraft.infusion.debug_title"));
            lines.add(Component.translatable("thaumcraft.infusion.debug_pedestals",tile.pedestalCount,tile.pedestalPenalty));
            lines.add(Component.translatable("thaumcraft.infusion.debug_stabilizers",tile.stabilizerCount,String.format(Locale.ROOT,"%.1f",tile.stabilization)));
            lines.add(Component.translatable("thaumcraft.infusion.debug_balance",tile.symmetry));
            lines.add(Component.translatable("thaumcraft.infusion.debug_instability",tile.recipeInstability(),tile.instability));
            lines.add(Component.translatable("thaumcraft.infusion.debug_remaining",tile.remainingEssentia().values().stream().mapToInt(Integer::intValue).sum(),tile.remainingIngredients()));
            lines.add(Component.translatable(tile.crafting?"thaumcraft.infusion.debug_crafting":tile.active?"thaumcraft.infusion.debug_ready":"thaumcraft.infusion.debug_inactive"));
        }else lines.add(Component.translatable("thaumcraft.infusion.debug_aim"));
        var g=event.getGuiGraphics();int width=lines.stream().mapToInt(mc.font::width).max().orElse(0)+12;g.fill(4,4,Math.min(g.guiWidth()-4,width+4),10+lines.size()*11,0xb020172b);for(int i=0;i<lines.size();i++)g.drawString(mc.font,lines.get(i),10,10+i*11,0xffe4d3ff,true);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){enabled(false);}
    private InfusionDebugHud(){}
}
