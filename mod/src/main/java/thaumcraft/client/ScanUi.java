package thaumcraft.client;

import java.util.*;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.knowledge.PlayerKnowledge.ScanKind;
import thaumcraft.items.ThaumometerItem;
import thaumcraft.network.*;

/** Vanilla tooltip plus scan status/aspects; server owns timing and rewards. */
@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class ScanUi {
    private static AbstractContainerMenu activeMenu;private static int activeSlot=-1,scanBeganTick;private static ItemStack activeStack=ItemStack.EMPTY;private static long sequence;private static UUID session;
    private static String justScanned="";
    private static boolean holding(){var player=Minecraft.getInstance().player;return player!=null&&((Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen?screen.getMenu().getCarried():player.containerMenu.getCarried()).getItem() instanceof ThaumometerItem);}
    private static String key(ItemStack stack){return thaumcraft.data.StackAspectRules.scanKey(stack,ClientKnowledge.aspects().scanGroups());}
    public static boolean scanned(ItemStack stack){return ClientKnowledge.snapshot().scanned(ScanKind.OBJECT).contains(key(stack));}
    @SubscribeEvent public static void tooltip(RenderTooltipEvent.GatherComponents event){
        var stack=event.getItemStack();if(stack.isEmpty())return;boolean scanner=holding();if(scanner&&stack.getItem() instanceof ThaumometerItem)event.getTooltipElements().add(Either.left(Component.translatable("thaumcraft.inventory_scanning.enabled").withStyle(net.minecraft.ChatFormatting.GOLD)));
        if(scanned(stack)){
            if(scanner&&!key(stack).equals(justScanned)&&!(activeMenu!=null&&ItemStack.matches(activeStack,stack)))event.getTooltipElements().add(Either.left(Component.translatable("tc.alreadyscanned").withStyle(net.minecraft.ChatFormatting.GREEN)));
        }else if(scanner&&activeMenu!=null&&ItemStack.matches(activeStack,stack)){int percent=Math.clamp((Minecraft.getInstance().player.tickCount-scanBeganTick)*5,0,100);event.getTooltipElements().add(Either.left(Component.translatable("thaumcraft.inventory_scanning.progress",percent).withStyle(net.minecraft.ChatFormatting.GOLD)));}
    }
    @SubscribeEvent public static void aspects(RenderTooltipEvent.Pre event){
        var stack=event.getItemStack();if(stack.isEmpty()||!scanned(stack)||!(holding()||Screen.hasShiftDown()!=thaumcraft.config.ThaumcraftClientConfig.SHOW_TAGS.get()))return;
        var data=ClientKnowledge.aspects();var values=thaumcraft.data.StackAspectRules.aspects(stack,data.objects().getOrDefault(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),Map.of()),data.modifiers());if(values.isEmpty())return;
        var renderer=new AspectTooltipRenderer(new AspectTooltipData(values));var font=event.getFont();int width=event.getComponents().stream().mapToInt(c->c.getWidth(font)).max().orElse(0),height=event.getComponents().stream().mapToInt(c->c.getHeight()).sum();
        var anchor=event.getTooltipPositioner().positionTooltip(event.getScreenWidth(),event.getScreenHeight(),event.getX(),event.getY(),width,height);int panelWidth=renderer.getWidth(font),panelHeight=renderer.getHeight(),x=Math.clamp(anchor.x(),3,Math.max(3,event.getScreenWidth()-panelWidth-5)),y=anchor.y()-panelHeight-6;
        if(y<3){int shift=3-y;event.setY(event.getY()+shift);y=3;}
        // Inventory's player preview is rendered at z=1050; the panel can overlap it.
        var graphics=event.getGraphics();graphics.pose().pushPose();graphics.pose().translate(0,0,1200);renderer.renderImage(font,x,y,graphics);graphics.flush();graphics.pose().popPose();
    }
    @SubscribeEvent public static void screen(ScreenEvent.Render.Post event){
        var mc=Minecraft.getInstance();var player=mc.player;if(player==null||ClientKnowledge.session()==null)return;
        if(!ClientKnowledge.session().equals(session)){session=ClientKnowledge.session();sequence=0;clear();}
        if(!(event.getScreen() instanceof AbstractContainerScreen<?> screen)||!holding()){cancel();return;}
        var slot=screen.getSlotUnderMouse();
        if(slot==null||!slot.hasItem()){justScanned="";cancel();return;}
        if(!key(slot.getItem()).equals(justScanned))justScanned="";
        if(slot.getItem().getItem() instanceof ThaumometerItem)cancel();
        else if(scanned(slot.getItem())){if(activeMenu!=null&&ItemStack.matches(activeStack,slot.getItem()))justScanned=key(slot.getItem());cancel();}else {
            var menu=screen.getMenu();int slotId=slot.index;var catalog=ItemStack.EMPTY;boolean creativeCursor=false;
            if(screen instanceof CreativeModeInventoryScreen){menu=player.inventoryMenu;slotId=-2;creativeCursor=screen.getMenu().getCarried().getItem() instanceof ThaumometerItem;for(var owned:menu.slots)if(owned.container==slot.container&&owned.getSlotIndex()==slot.getSlotIndex()){slotId=owned.index;break;}if(slotId==-2)catalog=thaumcraft.data.StackAspectRules.inventoryScanStack(slot.getItem());}
            if(activeMenu!=menu||activeSlot!=slotId||!ItemStack.matches(activeStack,slot.getItem())){activeMenu=menu;activeSlot=slotId;activeStack=slot.getItem().copy();scanBeganTick=player.tickCount;PacketDistributor.sendToServer(new InventoryScanPayload(session,menu.containerId,slotId,++sequence,catalog,creativeCursor));}
        }
        if(screen.getMenu().getCarried().getItem() instanceof ThaumometerItem)event.getGuiGraphics().renderTooltip(mc.font,slot.getItem(),event.getMouseX(),event.getMouseY());
    }
    @SubscribeEvent public static void closing(ScreenEvent.Closing event){justScanned="";cancel();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){var mc=Minecraft.getInstance();if(activeMenu!=null&&mc.player!=null&&mc.level!=null&&mc.player.tickCount-scanBeganTick<20&&mc.player.tickCount%2==0)mc.player.playSound(thaumcraft.registry.R2Sounds.CAMERA_TICKS.get(),.2f,.45f+mc.level.random.nextFloat()*.1f);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){justScanned="";clear();sequence=0;session=null;}
    private static void cancel(){if(activeMenu!=null&&Minecraft.getInstance().getConnection()!=null&&session!=null)PacketDistributor.sendToServer(new InventoryScanPayload(session,activeMenu.containerId,-1,++sequence));clear();}
    private static void clear(){activeMenu=null;activeSlot=-1;activeStack=ItemStack.EMPTY;}
    private ScanUi(){}
}
