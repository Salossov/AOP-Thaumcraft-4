package thaumcraft.equipment;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import thaumcraft.items.*;

@EventBusSubscriber(modid="thaumcraft_aop")
public final class HarnessFlight {
    private static final Map<UUID,Boolean> LEASES=new HashMap<>();
    public static boolean equipped(ItemStack stack){return stack.getItem() instanceof ThaumicArmorItem armor&&armor.kind()==ThaumicArmorItem.Kind.HARNESS;}
    public static boolean hovering(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getBoolean("hover");}
    public static void toggle(ServerPlayer player){
        var stack=player.getItemBySlot(EquipmentSlot.CHEST);if(!equipped(stack)||player.isCreative()||player.isSpectator())return;
        boolean enabled=!hovering(stack);if(enabled&&fuel(stack)<=0)return;
        CustomData.update(DataComponents.CUSTOM_DATA,stack,t->t.putBoolean("hover",enabled));
    }
    public static int fuel(ItemStack stack){
        var jar=stack.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).copyOne();
        return jar.getOrDefault(DataComponents.BLOCK_ENTITY_DATA,CustomData.EMPTY).copyTag().getCompound("Essentia").getInt("potentia");
    }
    public static boolean expend(ServerPlayer player,ItemStack harness){
        var jar=harness.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).copyOne();int fuel=fuel(harness);if(fuel<=0)return false;
        var data=harness.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();int charge=data.getInt("charge");
        int duration=EquippedCurios.count(player,ThaumicCurioItem.Kind.HOVER_GIRDLE)>0?288:360;
        if(charge<duration){CustomData.update(DataComponents.CUSTOM_DATA,harness,t->t.putInt("charge",charge+1));return true;}
        CustomData.update(DataComponents.CUSTOM_DATA,harness,t->t.putInt("charge",0));
        CustomData.update(DataComponents.BLOCK_ENTITY_DATA,jar,t->{var essentia=t.getCompound("Essentia");essentia.putInt("potentia",fuel-1);t.put("Essentia",essentia);});
        harness.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(jar)));return fuel>1;
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        if(player.isCreative()||player.isSpectator()){LEASES.remove(player.getUUID());return;}
        var stack=player.getItemBySlot(EquipmentSlot.CHEST);boolean active=equipped(stack)&&hovering(stack)&&expend(player,stack);
        if(active){
            LEASES.putIfAbsent(player.getUUID(),player.getAbilities().mayfly);
            if(!player.getAbilities().mayfly||!player.getAbilities().flying){player.getAbilities().mayfly=true;player.getAbilities().flying=true;player.onUpdateAbilities();}
            player.fallDistance=0;
        }else{
            if(equipped(stack)&&hovering(stack))CustomData.update(DataComponents.CUSTOM_DATA,stack,t->t.putBoolean("hover",false));
            var previous=LEASES.remove(player.getUUID());if(previous!=null){player.getAbilities().mayfly=previous;player.getAbilities().flying=false;player.onUpdateAbilities();}
            if(equipped(stack))player.fallDistance*=.75f;
        }
    }
    private static void revoke(net.minecraft.world.entity.player.Player player){var previous=LEASES.remove(player.getUUID());if(previous!=null&&!player.isCreative()&&!player.isSpectator()){player.getAbilities().mayfly=previous;player.getAbilities().flying=false;if(player instanceof ServerPlayer server)server.onUpdateAbilities();}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){revoke(event.getEntity());}
    @SubscribeEvent public static void clone(PlayerEvent.Clone event){revoke(event.getOriginal());if(!event.getEntity().isCreative()&&!event.getEntity().isSpectator()){event.getEntity().getAbilities().flying=false;}}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){LEASES.clear();}
    private HarnessFlight(){}
}
