package thaumcraft.client;

import net.minecraft.client.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import thaumcraft.items.WandFocus;

@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class FocusControls {
    static final KeyMapping FOCUS=new KeyMapping("key.thaumcraft.focus",org.lwjgl.glfw.GLFW.GLFW_KEY_F,"key.categories.thaumcraft");
    static final KeyMapping MISC=new KeyMapping("key.thaumcraft.wand_misc",org.lwjgl.glfw.GLFW.GLFW_KEY_G,"key.categories.thaumcraft");
    @EventBusSubscriber(modid="thaumcraft_aop",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration{@SubscribeEvent public static void register(RegisterKeyMappingsEvent event){event.register(FOCUS);event.register(MISC);}}
    @SubscribeEvent public static void crosshair(RenderGuiLayerEvent.Pre event){if(Minecraft.getInstance().screen instanceof FocusRadialScreen&&event.getName().equals(net.minecraft.resources.ResourceLocation.withDefaultNamespace("crosshair")))event.setCanceled(true);}
    // Open before vanilla consumes a conflicting offhand-swap binding.
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event){var mc=Minecraft.getInstance();if(mc.player==null)return;while(MISC.consumeClick())if(mc.screen==null)net.neoforged.neoforge.network.PacketDistributor.sendToServer(new thaumcraft.network.WandMiscPayload());while(FOCUS.consumeClick())if(mc.screen==null){if(mc.player.getMainHandItem().getItem() instanceof thaumcraft.items.GolemBellItem){if(FOCUS.getKey().equals(mc.options.keySwapOffhand.getKey()))while(mc.options.keySwapOffhand.consumeClick()){}net.neoforged.neoforge.network.PacketDistributor.sendToServer(new thaumcraft.network.GolemBellResetPayload());continue;}var hand=WandFocus.supports(mc.player.getMainHandItem())?net.minecraft.world.InteractionHand.MAIN_HAND:net.minecraft.world.InteractionHand.OFF_HAND;if(WandFocus.supports(mc.player.getItemInHand(hand))){if(FOCUS.getKey().equals(mc.options.keySwapOffhand.getKey()))while(mc.options.keySwapOffhand.consumeClick()){}if(mc.player.isShiftKeyDown())net.neoforged.neoforge.network.PacketDistributor.sendToServer(new thaumcraft.network.FocusSelectPayload(hand==net.minecraft.world.InteractionHand.OFF_HAND,-1));else mc.setScreen(new FocusRadialScreen(hand));}}}
    private FocusControls(){}
}
