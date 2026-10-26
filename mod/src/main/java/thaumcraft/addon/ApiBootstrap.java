package thaumcraft.addon;

import java.util.*;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.addon.*;
import thaumcraft.api.aspects.*;
import thaumcraft.api.research.ResearchApi;
import thaumcraft.api.wands.*;
import thaumcraft.items.*;
import thaumcraft.knowledge.KnowledgeService;
import thaumcraft.data.*;

/** Adapts existing mechanics to the API. Optional viewers never link these implementation classes. */
public final class ApiBootstrap {
    private static boolean addonsRegistered;
    public static void install(){
        thaumcraft.api.world.SpecialMiningApi.install(thaumcraft.equipment.NativeOreDrops::specialMiningResult);
        thaumcraft.api.client.ParticleApi.installBroadcast(thaumcraft.network.ParticleEffectPayload::send);
        thaumcraft.api.client.ParticleApi.installSmokeBroadcast(thaumcraft.network.SmokeSpiralPayload::send);
        thaumcraft.api.world.BlockEntityMovementApi.install(new BlockEntityMovement());
        thaumcraft.api.world.BlockPlacementApi.install(new SelectedBlockPlacement());
        thaumcraft.api.world.BlockReplacementApi.install(new ProtectedBlockReplacement());
        thaumcraft.api.world.ChunkPreparationApi.install(new PreparedChunks());
        thaumcraft.api.world.ContentRules.install(new thaumcraft.api.world.ContentRules.Access(){
            public boolean mirrorsEnabled(){return thaumcraft.config.ThaumcraftConfig.ALLOW_MIRRORS.get();}
            public boolean modernWeaponCooldown(){return thaumcraft.config.ThaumcraftConfig.MODERN_WEAPON_COOLDOWN.get();}
        });
        thaumcraft.api.entities.MobBehaviorApi.install(new thaumcraft.entities.MobBehaviorAccess());
        thaumcraft.api.entities.GolemEquipmentApi.install(new NativeGolemEquipment());
        thaumcraft.api.entities.GolemVisualApi.install(entity->entity instanceof thaumcraft.entities.ThaumicGolem golem
                ?new thaumcraft.api.entities.GolemVisualApi.State(true,golem.visualInactive(),golem.visualHealingTicks())
                :thaumcraft.api.entities.GolemVisualApi.State.UNSUPPORTED);
        thaumcraft.api.crafting.InfusionMatrixApi.install(new NativeInfusionMatrixProbe());
        thaumcraft.api.wands.FocusSwingApi.install(thaumcraft.items.FocusCasting::tryBeginSwing);
        thaumcraft.api.entities.EffectDurationApi.install((effect,ticks)->((thaumcraft.mixin.MobEffectDurationAccessor)(Object)effect).thaumcraft$setDuration(ticks));
        thaumcraft.api.entities.PlayerMotionApi.install((player,velocity)->{
            player.setDeltaMovement(velocity);player.fallDistance=0;player.hurtMarked=true;
            if(player.connection!=null){
                ((thaumcraft.mixin.FloatingCounterAccessor)player.connection).thaumcraft$resetFloatingTicks(0);
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
            }
        });
        ResearchApi.install(new ResearchApi.Access(){public thaumcraft.api.knowledge.PlayerKnowledge knowledge(net.minecraft.server.level.ServerPlayer p){return KnowledgeService.get(p);}public void sync(net.minecraft.server.level.ServerPlayer p){KnowledgeService.sync(p);}public boolean grant(net.minecraft.server.level.ServerPlayer p,String key){return KnowledgeService.mutate(p,k->k.completeResearch(key));}public Map<String,thaumcraft.api.research.ResearchDefinition> definitions(net.minecraft.server.level.ServerPlayer p){return ThaumcraftData.get(p.server).research();}public boolean exists(net.minecraft.server.level.ServerPlayer p,String key){return ThaumcraftData.get(p.server).research().containsKey(key);}public void addWarp(net.minecraft.server.level.ServerPlayer p,ResearchApi.WarpKind kind,int amount){thaumcraft.knowledge.WarpService.add(p,kind==ResearchApi.WarpKind.PERMANENT?amount:0,kind==ResearchApi.WarpKind.STICKY?amount:0,kind==ResearchApi.WarpKind.TEMPORARY?amount:0);}public boolean completeAspect(net.minecraft.server.level.ServerPlayer p,Aspect aspect,short amount){return KnowledgeService.mutate(p,k->{k.discover(aspect);k.setPool(aspect.getTag(),amount);return true;});}});
        thaumcraft.api.research.ResearchSharingApi.install(new thaumcraft.api.research.ResearchSharingApi.Access(){
            public List<String> completed(net.minecraft.server.MinecraftServer server,UUID author){return KnowledgeService.completedResearch(server,author);}
            public boolean complete(net.minecraft.server.level.ServerPlayer recipient,String key){return KnowledgeService.completeSharedResearch(recipient,key);}
        });
        EssentiaItemApi.install(new NativeEssentiaItems());
        AspectApi.install(new AspectApi.Access(){
            public ItemStack wispEssence(Aspect aspect){return WispEssenceItem.of(aspect);}
            public Map<String,Integer> aspects(net.minecraft.world.level.Level level,ItemStack stack){if(level instanceof net.minecraft.server.level.ServerLevel server)return ThaumcraftData.aspects(server.getServer(),stack);var data=thaumcraft.network.ClientKnowledge.aspects();return StackAspectRules.aspects(stack,data.objects().getOrDefault(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),Map.of()),data.modifiers());}
            public Map<String,Integer> aspects(net.minecraft.world.level.Level level,net.minecraft.world.level.block.state.BlockState state){if(level instanceof net.minecraft.server.level.ServerLevel server)return ThaumcraftData.aspects(server.getServer(),state);var data=thaumcraft.network.ClientKnowledge.aspects();return StackAspectRules.blockAspects(state,data.objects().getOrDefault(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),Map.of()),data.modifiers());}
            public Map<String,Integer> aspects(net.minecraft.world.entity.Entity entity){if(entity.level() instanceof net.minecraft.server.level.ServerLevel server)return ThaumcraftData.aspects(server.getServer(),entity);var data=thaumcraft.network.ClientKnowledge.aspects();return EntityAspectRules.aspects(entity,data.entities(),data.variants(),data.entityAdditions(),data.variantAdditions());}
            public List<ItemStack> variants(){var stacks=new ArrayList<ItemStack>();for(var aspect:Aspect.all().values()){
                stacks.add(WispEssenceItem.of(aspect));for(var item:List.of(thaumcraft.registry.R3Content.ESSENTIA_PHIAL.get(),thaumcraft.registry.R3Content.ESSENTIA_CRYSTAL.get())){var stack=new ItemStack(item);thaumcraft.registry.R3Content.setAspect(stack,aspect);stacks.add(stack);}
                for(var block:List.of(thaumcraft.registry.R3Content.WARDED_JAR.get(),thaumcraft.registry.R3Content.VOID_JAR.get())){var stack=new ItemStack(block);var tag=new net.minecraft.nbt.CompoundTag();var contents=new net.minecraft.nbt.CompoundTag();contents.putInt(aspect.getTag(),64);tag.put("Essentia",contents);tag.putString("AspectFilter",aspect.getTag());tag.putInt("TC4ProductionVersion",1);tag.putString("id","thaumcraft:essentia_transport");stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.of(tag));stacks.add(stack);}
            }return stacks;}
            public boolean scanned(ItemStack stack){var data=thaumcraft.network.ClientKnowledge.aspects();String key=StackAspectRules.scanKey(stack,data.scanGroups());return ResearchApi.clientKnowledge().scanned(thaumcraft.api.knowledge.PlayerKnowledge.ScanKind.OBJECT).contains(key);}
            public boolean scanned(net.minecraft.server.level.ServerPlayer player,ItemStack stack){return KnowledgeService.get(player).scanned(thaumcraft.api.knowledge.PlayerKnowledge.ScanKind.OBJECT).contains(ThaumcraftData.objectScanKey(player.server,stack));}
        });
        WandApi.install(new WandApi.Access(){
            public WandApi.Kind kind(ItemStack s){return s.getItem() instanceof thaumcraft.items.CastingWandItem wand?WandApi.Kind.valueOf(wand.kind().name()):WandApi.Kind.NONE;}
            public WandParts parts(ItemStack s){return thaumcraft.items.WandMaterials.parts(s);}
            public float craftingModifier(ItemStack s,net.minecraft.world.entity.player.Player p,Aspect a){return thaumcraft.items.WandMaterials.modifier(s,a,p==null?0:WandVis.discount(p,a));}
            public Map<Aspect,Integer> quoteCrafting(ItemStack s,net.minecraft.world.entity.player.Player p,Map<Aspect,Integer> wholeVis){
                var result=new java.util.LinkedHashMap<Aspect,Integer>();wholeVis.forEach((a,n)->{
                    if(a==null||!a.isPrimal()||n<=0)throw new IllegalArgumentException("Invalid crafting vis cost");
                    result.put(a,(int)(Math.multiplyExact(n,100)*craftingModifier(s,p,a)));
                });return result;
            }
            public int get(ItemStack s,Aspect a){return WandVis.get(s,a);}public int capacity(ItemStack s){return WandVis.capacity(s);}public void set(ItemStack s,Aspect a,int n){WandVis.set(s,a,n);}
            public Map<Aspect,Integer> quote(ItemStack s,net.minecraft.world.entity.player.Player p,Map<Aspect,Integer> n){return WandFocus.costs(s,p,n);}
            public boolean consume(ItemStack s,net.minecraft.world.entity.player.Player p,Map<Aspect,Integer> n,boolean commit){if(commit&&(p.level().isClientSide||!((net.minecraft.server.level.ServerLevel)p.level()).getServer().isSameThread()))throw new IllegalStateException("Server thread required");var q=quote(s,p,n);for(var e:q.entrySet())if(get(s,e.getKey())<e.getValue())return false;if(commit)q.forEach((a,v)->set(s,a,get(s,a)-v));return true;}
            public ItemStack focus(ItemStack s){return WandFocus.get(s);}public void focus(ItemStack wand,ItemStack focus){WandFocus.set(wand,focus);}public void parts(ItemStack s,WandParts parts){if(!WandRod.rods.containsKey(parts.rod())||!WandCap.caps.containsKey(parts.cap()))throw new IllegalArgumentException("Unknown wand parts");s.set(thaumcraft.registry.WandComponents.PARTS.get(),parts);}
        });
        thaumcraft.api.crafting.WorkbenchApi.install(thaumcraft.menu.ArcaneMenu.class,()->thaumcraft.registry.R2Content.ARCANE_MENU.get());
        RecipeCatalogBridge.install();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event)->{if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)thaumcraft.items.FocusCasting.logout(player);});
    }
    public static synchronized void registerAddons(){
        thaumcraft.blocks.PortableHoleBlacklist.initialize();
        if(addonsRegistered)return;
        // The global NeoForge bus is deliberately shut down during common setup.
        // This bus is active before client model loading and the first server data reload.
        AddonRegistrationEvent.BUS.post(new AddonRegistrationEvent());
        thaumcraft.api.entities.GolemWorkApi.freeze();
        AddonRegistry.freeze();
        addonsRegistered=true;
    }
    private ApiBootstrap(){}
}
