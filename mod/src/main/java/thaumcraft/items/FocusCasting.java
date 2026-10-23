package thaumcraft.items;

import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import thaumcraft.api.wands.FocusUpgradeType;
import thaumcraft.blocks.FocusActions;
import thaumcraft.entities.*;
import thaumcraft.registry.R2Sounds;

public final class FocusCasting {
    private static final Map<ServerPlayer,Long> COOLDOWN=new WeakHashMap<>();
    /** TC4 claims its shared cooldown before delegating a selected focus's swing. */
    public static boolean tryBeginSwing(ServerPlayer player,ItemStack wand){
        if(!player.server.isSameThread())throw new IllegalStateException("Focus swing requires its server game thread");
        if(player instanceof net.neoforged.neoforge.common.util.FakePlayer||!player.isAlive()||player.isRemoved()||player.isSpectator()
                ||player.serverLevel().getEntity(player.getUUID())!=player
                ||(player.getMainHandItem()!=wand&&player.getOffhandItem()!=wand)||!WandFocus.supports(wand))return false;
        var selected=WandFocus.get(wand);
        if(!(selected.getItem() instanceof thaumcraft.api.wands.ItemFocusBasic focus))return false;
        long now=player.server.getTickCount();
        if(COOLDOWN.getOrDefault(player,Long.MIN_VALUE)>now)return false;
        COOLDOWN.put(player,now+Math.max(1,(focus.getActivationCooldown(selected)+49L)/50L));
        return true;
    }
    public static boolean continuous(ItemStack wand){var focus=WandFocus.get(wand);var addon=thaumcraft.api.wands.FocusBehaviors.get(focus);if(addon!=null)return addon.behavior().continuous(focus);return focus.getItem() instanceof ThaumicFocusItem item&&(item.kind()==ThaumicFocusItem.Kind.EXCAVATION||item.kind()==ThaumicFocusItem.Kind.FIRE&&!item.isUpgradedWith(focus,FocusUpgradeType.fireball)||item.kind()==ThaumicFocusItem.Kind.SHOCK&&!item.isUpgradedWith(focus,FocusUpgradeType.earthshock));}
    public static boolean begin(ServerPlayer player,InteractionHand hand){var wand=player.getItemInHand(hand);var addon=thaumcraft.api.wands.FocusBehaviors.get(WandFocus.get(wand));if(addon!=null){endAddon(player);if(COOLDOWN.getOrDefault(player,Long.MIN_VALUE)>player.server.getTickCount()||!WandFocus.supports(wand)||!addon.behavior().begin(player,wand,WandFocus.get(wand),hand))return false;ACTIVE.put(player,new Active(wand,WandFocus.get(wand).copy(),addon.behavior()));if(continuous(wand)){player.startUsingItem(hand);return true;}try{return cast(player,wand);}finally{endAddon(player);}}if(!WandFocus.supports(wand)||!(WandFocus.get(wand).getItem() instanceof ThaumicFocusItem||WandFocus.get(wand).getItem() instanceof PechFocusItem))return false;if(continuous(wand)){player.startUsingItem(hand);return true;}return cast(player,wand);}
    public static void tick(ServerPlayer player,ItemStack wand){if(!continuous(wand)){player.stopUsingItem();return;}cast(player,wand);}
    private record Active(ItemStack wand,ItemStack focus,thaumcraft.api.wands.FocusBehavior behavior){}
    private static final Map<ServerPlayer,Active> ACTIVE=new WeakHashMap<>();
    private static void endAddon(ServerPlayer player){var active=ACTIVE.remove(player);if(active!=null)active.behavior.end(player,active.wand,active.focus);}
    public static void stop(ServerPlayer player){endAddon(player);FocusActions.stop(player);}
    private static LivingEntity target(ServerPlayer player,double range){
        var start=player.getEyePosition();var end=start.add(player.getLookAngle().scale(range));LivingEntity nearest=null;double distance=Double.MAX_VALUE;
        // TC4 EntityUtils uses at least .8 block of aim padding, independently of vanilla projectile picking.
        for(var entity:player.serverLevel().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().expandTowards(player.getLookAngle().scale(range)).inflate(1.1),e->!(e instanceof FocusFireBat)&&e.isPickable()&&FocusProjectile.allowed(player,e))){
            if(player.serverLevel().clip(new net.minecraft.world.level.ClipContext(start,entity.getEyePosition(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player)).getType()!=HitResult.Type.MISS)continue;
            var bounds=entity.getBoundingBox().inflate(Math.max(.8f,entity.getPickRadius()));var hit=bounds.clip(start,end);double d=bounds.contains(start)?0:hit.map(start::distanceToSqr).orElse(Double.MAX_VALUE);if(d<distance){nearest=entity;distance=d;}
        }return nearest;
    }
    public static boolean cast(ServerPlayer player,ItemStack wand){
        var selected=WandFocus.get(wand);var addon=thaumcraft.api.wands.FocusBehaviors.get(selected);if(addon!=null&&selected.getItem() instanceof thaumcraft.api.wands.ItemFocusBasic focus){long now=player.server.getTickCount();if(COOLDOWN.getOrDefault(player,Long.MIN_VALUE)>now||!WandFocus.supports(wand))return false;if(addon.behavior().managesVis())return castManagedAddon(player,wand,selected,focus,addon.behavior(),now);var costs=focus.getVisCost(selected);var quoted=WandFocus.costs(wand,player,costs);if(!WandFocus.consume(wand,player,costs,false)){player.stopUsingItem();endAddon(player);return false;}var before=new java.util.LinkedHashMap<thaumcraft.api.aspects.Aspect,Integer>();quoted.forEach((a,n)->{before.put(a,WandVis.get(wand,a));WandVis.set(wand,a,WandVis.get(wand,a)-n);});boolean success=false;try{success=addon.behavior().cast(player,wand,selected);}finally{if(!success)before.forEach((a,n)->WandVis.set(wand,a,Math.min(WandVis.capacity(wand),n)));}if(success){if(WandFocus.get(wand).getItem()==selected.getItem())WandFocus.set(wand,selected);var active=ACTIVE.get(player);if(active!=null)ACTIVE.put(player,new Active(wand,selected.copy(),active.behavior));COOLDOWN.put(player,now+Math.max(1,(focus.getActivationCooldown(selected)+49)/50));}return success;}if(selected.getItem() instanceof PechFocusItem pech){long now=player.server.getTickCount();if(COOLDOWN.getOrDefault(player,Long.MIN_VALUE)>now)return false;boolean success=pech.cast(player,wand,selected);player.swing(player.getOffhandItem()==wand?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND,true);if(success)COOLDOWN.put(player,now+(pech.getActivationCooldown(selected)+49)/50);return success;}
        var focus=WandFocus.get(wand);if(!(focus.getItem() instanceof ThaumicFocusItem item))return false;long now=player.server.getTickCount();if(COOLDOWN.getOrDefault(player,Long.MIN_VALUE)>now)return false;
        var level=player.serverLevel();var costs=item.activationCost(focus,level.random);boolean actionPays=item.kind()==ThaumicFocusItem.Kind.WARDING||item.kind()==ThaumicFocusItem.Kind.TRADE||item.kind()==ThaumicFocusItem.Kind.PORTABLE_HOLE;if(!actionPays&&!WandFocus.consume(wand,player,costs,false)){player.stopUsingItem();stop(player);level.playSound(null,player.blockPosition(),R2Sounds.WAND_FAIL.get(),SoundSource.PLAYERS,.1f,.8f);return false;}
        int potency=item.getUpgradeLevel(focus,FocusUpgradeType.potency);boolean success=false,paid=false;var hit=player.pick(10,0,false);
        switch(item.kind()){
            case FIRE->{boolean ball=item.isUpgradedWith(focus,FocusUpgradeType.fireball),beam=item.isUpgradedWith(focus,FocusUpgradeType.firebeam);for(int i=0;i<(ball?1:2+potency);i++){var projectile=projectile(player,ball?FocusProjectile.Kind.FIREBALL:FocusProjectile.Kind.EMBER,ball?1+potency*.4f:beam?(2+potency+.5f)*1.5f:2+potency,ball&&item.isUpgradedWith(focus,FocusUpgradeType.alchemistsfire)?1:0,ball?1.5f:1,beam?.25f:15);projectile.upgrade=item.getUpgradeLevel(focus,FocusUpgradeType.alchemistsfire);projectile.duration=beam?30:20;success|=level.addFreshEntity(projectile);}if(ball)level.levelEvent(net.minecraft.world.level.block.LevelEvent.SOUND_GHAST_FIREBALL,new net.minecraft.core.BlockPos((int)player.getX(),(int)player.getY(),(int)player.getZ()),0);else if(now%10==0)level.playSound(null,player.blockPosition(),R2Sounds.FIRE_LOOP.get(),SoundSource.PLAYERS,.33f,2);}
            case FROST->{boolean scatter=item.isUpgradedWith(focus,FocusUpgradeType.scattershot),boulder=item.isUpgradedWith(focus,FocusUpgradeType.iceboulder);for(int i=0;i<(scatter?5+potency*2:1);i++){var projectile=projectile(player,FocusProjectile.Kind.FROST,scatter?1:boulder?4+potency*2:3+potency*1.5f,scatter?1:0,1.5f,scatter?8:1);projectile.upgrade=item.getUpgradeLevel(focus,FocusUpgradeType.alchemistsfrost);if(boulder){projectile.bounce=.8;projectile.bounces=6;}success|=level.addFreshEntity(projectile);}level.playSound(null,player.blockPosition(),R2Sounds.ICE.get(),SoundSource.PLAYERS,.4f,1+level.random.nextFloat()*.1f);}
            case PRIMAL->{var orb=projectile(player,FocusProjectile.Kind.PRIMAL,2,item.isUpgradedWith(focus,FocusUpgradeType.seeker)?1:0,.5f,1);success=level.addFreshEntity(orb);level.playSound(null,orb.getX(),orb.getY(),orb.getZ(),R2Sounds.ICE.get(),SoundSource.PLAYERS,.3f,.8f+level.random.nextFloat()*.1f);}
            case HELLBAT->{var target=target(player,32);if(target!=null&&!(target instanceof FocusFireBat)){var bat=new FocusFireBat(R3Entities.FIREBAT.get(),level);bat.summon(player,target,potency,item.isUpgradedWith(focus,FocusUpgradeType.devilbats),item.isUpgradedWith(focus,FocusUpgradeType.batbombs),item.isUpgradedWith(focus,FocusUpgradeType.vampirebats));var pos=player.position().add(0,player.getBbHeight()/2+.2,0).add(player.getLookAngle().scale(.5));bat.moveTo(pos.x,pos.y+bat.getBbHeight(),pos.z,player.getYRot(),0);success=level.addFreshEntity(bat);level.sendParticles(ParticleTypes.FLAME,pos.x,pos.y,pos.z,12,.2,.2,.2,.02);level.playSound(null,bat.blockPosition(),R2Sounds.ICE.get(),SoundSource.PLAYERS,.2f,.95f+level.random.nextFloat()*.1f);}}
            case SHOCK->{
                if(item.isUpgradedWith(focus,FocusUpgradeType.earthshock)){
                    var orb=projectile(player,FocusProjectile.Kind.SHOCK,5+(int)(potency*1.33),0,1.5f,1);orb.area=4+item.getUpgradeLevel(focus,FocusUpgradeType.enlarge)*2;success=level.addFreshEntity(orb);
                    level.playSound(null,orb.getX(),orb.getY(),orb.getZ(),R2Sounds.ZAP.get(),SoundSource.PLAYERS,1,1+(level.random.nextFloat()-level.random.nextFloat())*.2f);
                }else success=shock(player,focus,item,potency);
            }
            case EXCAVATION->{if(hit instanceof BlockHitResult block&&hit.getType()==HitResult.Type.BLOCK)success=FocusActions.excavate(player,wand,focus,item,block);else FocusActions.stop(player);paid=true;}
            case TRADE->{if(hit instanceof BlockHitResult block&&hit.getType()==HitResult.Type.BLOCK)success=FocusActions.exchange(player,wand,focus,item,block);paid=true;}
            case WARDING->{if(hit instanceof BlockHitResult block&&hit.getType()==HitResult.Type.BLOCK)success=FocusActions.ward(player,wand,focus,item,block);paid=true;}
            case PORTABLE_HOLE->{if(hit instanceof BlockHitResult block&&hit.getType()==HitResult.Type.BLOCK)success=FocusActions.hole(player,wand,focus,item,block);paid=true;}
        }
        if(success){if(!paid)WandFocus.consume(wand,player,costs,true);COOLDOWN.put(player,now+(item.getActivationCooldown(focus)+49)/50);if(!continuous(wand))player.swing(player.getOffhandItem()==wand?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND,true);}return success;
    }
    /** A delayed/conditional addon decides when to pay; the core still enforces cooldown and focus ownership. */
    private static boolean castManagedAddon(ServerPlayer player,ItemStack wand,ItemStack selected,
            thaumcraft.api.wands.ItemFocusBasic focus,thaumcraft.api.wands.FocusBehavior behavior,long now){
        boolean success=behavior.cast(player,wand,selected);
        if(success){
            if(WandFocus.get(wand).getItem()==selected.getItem())WandFocus.set(wand,selected);
            var active=ACTIVE.get(player);if(active!=null)ACTIVE.put(player,new Active(wand,selected.copy(),active.behavior));
            COOLDOWN.put(player,now+Math.max(1,(focus.getActivationCooldown(selected)+49)/50));
        }
        return success;
    }
    private static FocusProjectile projectile(ServerPlayer player,FocusProjectile.Kind kind,float damage,int flags,float speed,float scatter){var projectile=new FocusProjectile(kind==FocusProjectile.Kind.PRIMAL?R3Entities.PRIMAL_ORB.get():R3Entities.FOCUS_PROJECTILE.get(),player.level()).configure(kind,damage,flags);projectile.launch(player,speed,scatter);return projectile;}
    private static Entity shockTarget(ServerPlayer player){
        var from=player.getEyePosition();var direction=player.getLookAngle();var to=from.add(direction.scale(20));Entity pointed=null;double nearest=0;
        for(var entity:player.level().getEntities(player,player.getBoundingBox().expandTowards(direction.scale(20)).inflate(1.1f))){
            if(!entity.isPickable()||player.level().clip(new net.minecraft.world.level.ClipContext(from,entity.getEyePosition(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player)).getType()!=HitResult.Type.MISS)continue;
            var bounds=entity.getBoundingBox().inflate(Math.max(.8f,entity.getPickRadius()));var intercept=bounds.clip(from,to);
            if(bounds.contains(from)){if(nearest==0){pointed=entity;nearest=0;}}
            else if(intercept.isPresent()){double distance=from.distanceTo(intercept.get());if(nearest==0||distance<nearest){pointed=entity;nearest=distance;}}
        }
        return pointed;
    }
    private static boolean shock(ServerPlayer player,ItemStack focus,ThaumicFocusItem item,int potency){
        var level=player.serverLevel();var pointed=shockTarget(player);var block=player.pick(10,0,false);var end=block.getType()==HitResult.Type.BLOCK?block.getLocation():player.getEyePosition().add(player.getLookAngle().scale(10));
        sendLightning(player,player,pointed,end,true,block.getType()==HitResult.Type.BLOCK);
        level.playSound(null,player.blockPosition(),R2Sounds.SHOCK.get(),SoundSource.PLAYERS,.25f,1);
        if(pointed instanceof LivingEntity target&&FocusProjectile.allowed(player,target)){
            int chains=item.getUpgradeLevel(focus,FocusUpgradeType.chainlightning)*2;target.hurt(level.damageSources().playerAttack(player),(chains>0?6:4)+potency);
            if(chains>0){
                int remaining=chains+item.getUpgradeLevel(focus,FocusUpgradeType.enlarge)*2;var used=new HashSet<UUID>();used.add(target.getUUID());LivingEntity centre=target;
                while(remaining-->0){
                    LivingEntity closest=null;double nearest=Double.MAX_VALUE;var position=centre.position();
                    for(var candidate:level.getEntitiesOfClass(LivingEntity.class,new AABB(position,position).inflate(8),entity->FocusProjectile.allowed(player,entity)&&!used.contains(entity.getUUID()))){
                        double distance=candidate.distanceToSqr(centre);if(distance<nearest){closest=candidate;nearest=distance;}
                    }
                    if(closest==null)continue;sendLightning(player,centre,closest,closest.position(),false,false);used.add(closest.getUUID());closest.hurt(level.damageSources().playerAttack(player),4+potency);centre=closest;
                }
            }
        }
        return true;
    }
    private static void sendLightning(ServerPlayer player,Entity source,Entity target,Vec3 endpoint,boolean fromWand,boolean blockSparkles){
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(player.serverLevel(),null,source.getX(),source.getY(),source.getZ(),64,new thaumcraft.network.FocusLightningPayload(source.getId(),target==null?-1:target.getId(),endpoint,fromWand,blockSparkles));
    }
    public static void logout(ServerPlayer player){stop(player);COOLDOWN.remove(player);}
    private FocusCasting(){}
}
