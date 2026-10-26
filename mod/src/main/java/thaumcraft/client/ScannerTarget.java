package thaumcraft.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.phys.*;
import thaumcraft.api.knowledge.PlayerKnowledge.ScanKind;
import thaumcraft.api.nodes.NodeState;
import thaumcraft.blocks.AuraNodeBlockEntity;
import thaumcraft.data.*;
import thaumcraft.network.ClientKnowledge;

/** A display-only view. Rewards and scan completion remain entirely server-owned. */
record ScannerTarget(Component name,Component detail,Map<String,Integer> aspects) {
    /** Original thaumometer renders tainted targets in dark purple. */
    boolean tainted(){return aspects.containsKey("vitium");}
    static ScannerTarget current(){var mc=Minecraft.getInstance();var player=mc.player;if(player==null||mc.level==null)return null;
        var eye=player.getEyePosition();var end=eye.add(player.getLookAngle().scale(10));var hit=player.pick(5,0,true);double max=hit.getType()==HitResult.Type.MISS?100:eye.distanceToSqr(hit.getLocation());Entity closest=null;
        for(var entity:mc.level.getEntities(player,player.getBoundingBox().expandTowards(player.getLookAngle().scale(10)).inflate(0.5),e->e.isAlive()&&!e.isSpectator())){var intersection=entity.getBoundingBox().inflate(0.5).clip(eye,end);if(intersection.isPresent()&&eye.distanceToSqr(intersection.get())<max){max=eye.distanceToSqr(intersection.get());closest=entity;}}
        var data=ClientKnowledge.aspects();var knowledge=ClientKnowledge.snapshot();
        if(closest instanceof ItemEntity dropped)return stack(dropped.getItem());
        if(closest!=null){String key=EntityAspectRules.scanKey(closest,data.variants());return new ScannerTarget(closest.getDisplayName(),Component.empty(),knowledge.scanned(ScanKind.ENTITY).contains(key)?EntityAspectRules.aspects(closest,data.entities(),data.variants(),data.entityAdditions(),data.variantAdditions()):Map.of());}
        if(!(hit instanceof BlockHitResult block)||hit.getType()!=HitResult.Type.BLOCK)return null;var state=mc.level.getBlockState(block.getBlockPos());
        if(mc.level.getBlockEntity(block.getBlockPos()) instanceof AuraNodeBlockEntity node){boolean scanned=knowledge.scanned(ScanKind.PHENOMENON).contains("NODE"+node.id());Component detail=Component.empty();if(scanned){detail=Component.translatable("nodetype."+node.state.type.name()+".name");if(node.state.modifier!=NodeState.Modifier.NONE)detail=detail.copy().append(", ").append(Component.translatable("nodemod."+node.state.modifier.name()+".name"));}return new ScannerTarget(state.getBlock().getName(),detail,scanned?Map.copyOf(node.state.current):Map.of());}
        if(mc.level.getBlockEntity(block.getBlockPos()) instanceof thaumcraft.blocks.JarredNodeBlockEntity jar){boolean scanned=knowledge.scanned(ScanKind.PHENOMENON).contains("NODE"+jar.id());return new ScannerTarget(state.getBlock().getName(),scanned?Component.translatable("nodetype."+jar.state.type.name()+".name"):Component.empty(),scanned?Map.copyOf(jar.state.current):Map.of());}
        String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();if(id.equals("minecraft:redstone_ore")&&state.getValue(RedStoneOreBlock.LIT))id="minecraft:lit_redstone_ore";
        String key=data.scanGroups().getOrDefault(id,id);return new ScannerTarget(state.getBlock().getName(),Component.empty(),knowledge.scanned(ScanKind.OBJECT).contains(key)?StackAspectRules.blockAspects(state,data.objects().getOrDefault(id,Map.of()),data.modifiers()):Map.of());
    }
    private static ScannerTarget stack(ItemStack stack){var data=ClientKnowledge.aspects();String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),key=StackAspectRules.scanKey(stack,data.scanGroups());return new ScannerTarget(stack.getHoverName(),Component.empty(),ClientKnowledge.snapshot().scanned(ScanKind.OBJECT).contains(key)?StackAspectRules.aspects(stack,data.objects().getOrDefault(id,Map.of()),data.modifiers()):Map.of());}
}
