package thaumcraft.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.equipment.HarnessFlight;
import thaumcraft.equipment.EquipmentEvents;
import thaumcraft.items.*;
import thaumcraft.network.HarnessTogglePayload;

@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class HarnessControls {
    private static final KeyMapping TOGGLE=new KeyMapping("key.thaumcraft.harness",org.lwjgl.glfw.GLFW.GLFW_KEY_H,"key.categories.thaumcraft");
    @EventBusSubscriber(modid="thaumcraft_aop",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration{@SubscribeEvent public static void register(RegisterKeyMappingsEvent event){event.register(TOGGLE);}}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        while(TOGGLE.consumeClick())if(mc.screen==null)PacketDistributor.sendToServer(new HarnessTogglePayload());
        var armor=mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        if(HarnessFlight.equipped(armor)&&HarnessFlight.hovering(armor)&&!mc.player.isCreative()){
            float modifier=.7f+.075f*EquipmentEvents.enchantmentLevel(armor,mc.player.level(),"haste");
            if(EquippedCurios.count(mc.player,ThaumicCurioItem.Kind.HOVER_GIRDLE)>0)modifier+=.21f;
            var movement=mc.player.getDeltaMovement();mc.player.setDeltaMovement(movement.multiply(Math.min(1,modifier),1,Math.min(1,modifier)));
        }
    }
    private HarnessControls(){}
}
