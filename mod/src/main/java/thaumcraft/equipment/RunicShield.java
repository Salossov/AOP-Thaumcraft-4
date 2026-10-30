package thaumcraft.equipment;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.effect.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.IRunicArmor;
import thaumcraft.api.aspects.Aspects;
import thaumcraft.items.*;
import thaumcraft.network.RunicShieldPayload;
import thaumcraft.config.ThaumcraftConfig;

/** Charges are transient, as in TC4; hardening belongs to the persistent item. */
@EventBusSubscriber(modid="thaumcraft_aop")
public final class RunicShield {
    private static final Map<UUID,State> STATES=new HashMap<>();
    private static final class State {
        int charge,max,charged,healing,kinetic,emergency,delay;
        long nextRecharge,kineticCooldown,healingCooldown,emergencyCooldown;
    }
    public static int hardening(ItemStack stack){return stack.getItem() instanceof IRunicArmor?Math.max(0,stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getInt("RS.HARDEN")):0;}
    public static int capacity(ItemStack stack){return stack.getItem() instanceof IRunicArmor armor?armor.getRunicCharge(stack)+hardening(stack):0;}
    public static void harden(ItemStack stack){CustomData.update(DataComponents.CUSTOM_DATA,stack,t->t.putInt("RS.HARDEN",hardening(stack)+1));}
    public static int charge(ServerPlayer player){var state=STATES.get(player.getUUID());return state==null?0:state.charge;}
    private static void gear(ServerPlayer player,State s){
        s.max=0;s.charged=0;s.healing=0;s.kinetic=0;s.emergency=0;
        for(var stack:player.getArmorSlots())s.max+=capacity(stack);
        for(var stack:EquippedCurios.stacks(player)){
            s.max+=capacity(stack);
            if(stack.getItem() instanceof ThaumicCurioItem item)switch(item.kind()){
                case RING_CHARGED -> s.charged++;
                case RING_HEALING -> s.healing++;
                case BELT_KINETIC -> s.kinetic++;
                case AMULET_EMERGENCY -> s.emergency++;
                default -> {}
            }
        }
        s.charge=Math.min(s.charge,s.max);
    }
    private static void sync(ServerPlayer player,State state,boolean struck){
        PacketDistributor.sendToPlayer(player,new RunicShieldPayload(player.getId(),state.charge,state.max,struck));
        if(struck)PacketDistributor.sendToPlayersTrackingEntity(player,new RunicShieldPayload(player.getId(),state.charge,state.max,true));
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer player)||!player.isAlive())return;
        boolean first=!STATES.containsKey(player.getUUID());var state=STATES.computeIfAbsent(player.getUUID(),id->new State());int old=state.charge,oldMax=state.max;
        gear(player,state);
        if(state.delay>0)state.delay--;
        else if(state.charge<state.max&&player.level().getGameTime()>=state.nextRecharge
                &&WandVis.consumeInventory(player,Map.of(Aspects.AIR,ThaumcraftConfig.RUNIC_VIS_COST.get(),Aspects.EARTH,ThaumcraftConfig.RUNIC_VIS_COST.get()))){
            state.charge++;state.nextRecharge=player.level().getGameTime()+Math.max(1,(ThaumcraftConfig.RUNIC_RECHARGE_MS.get()-state.charged*500)/50);
        }
        if(first||old!=state.charge||oldMax!=state.max)sync(player,state,false);
    }
    @SubscribeEvent public static void damage(LivingIncomingDamageEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player)||event.getAmount()<=0)return;
        var source=event.getSource();
        if(source.is(DamageTypes.DROWN)||source.is(DamageTypes.STARVE)||source.is(DamageTypes.IN_WALL)||source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        var state=STATES.computeIfAbsent(player.getUUID(),id->new State());gear(player,state);
        if(state.charge<=0)return;
        float damage=event.getAmount();int available=state.charge;
        state.charge=damage<available?(int)(available-damage):0;
        event.setAmount(Math.max(0,damage-available));
        long now=player.level().getGameTime();
        if(state.charge==0){
            if(state.kinetic>0&&now>=state.kineticCooldown){
                state.kineticCooldown=now+400;
                player.level().explode(player,player.getX(),player.getY()+player.getBbHeight()/2,player.getZ(),1.5f+state.kinetic*.5f,Level.ExplosionInteraction.NONE);
            }
            if(state.healing>0&&now>=state.healingCooldown){
                state.healingCooldown=now+400;player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,240,state.healing));
            }
            if(state.emergency>0&&now>=state.emergencyCooldown){
                state.emergencyCooldown=now+1200;state.charge=Math.min(state.max,8*state.emergency);
            }
            if(state.charge==0)state.delay=ThaumcraftConfig.RUNIC_RECHARGE_DELAY.get();
        }
        sync(player,state,true);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){STATES.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void death(PlayerEvent.Clone event){STATES.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void died(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event){if(event.getEntity() instanceof ServerPlayer player){STATES.remove(player.getUUID());sync(player,new State(),false);}}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event){if(event.getEntity() instanceof ServerPlayer player){var state=new State();gear(player,state);STATES.put(player.getUUID(),state);sync(player,state,false);}}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){STATES.clear();}
    private RunicShield(){}
}
