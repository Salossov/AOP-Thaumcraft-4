package thaumcraft.equipment;

import java.util.*;
import net.minecraft.world.level.storage.loot.entries.*;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import thaumcraft.mixin.*;
import thaumcraft.registry.R3Equipment;



import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/** Adds TC4's original weighted entries to the modern chest pool, retaining vanilla loot. */
@EventBusSubscriber(modid="thaumcraft_aop")
public final class KnowledgeFragmentLoot {
    private static final Set<String> TABLES=Set.of("chests/simple_dungeon","chests/jungle_temple","chests/desert_pyramid","chests/abandoned_mineshaft","chests/stronghold_corridor","chests/stronghold_crossing","chests/stronghold_library");
    @SubscribeEvent public static void load(LootTableLoadEvent event){
        var id=event.getName();if(!id.getNamespace().equals("minecraft")||!TABLES.contains(id.getPath()))return;
        var pools=((LootTableAccessor)event.getTable()).thaumcraft$pools();if(pools.isEmpty())return;
        var pool=(LootPoolAccessor)pools.getFirst();var entries=new ArrayList<>(pool.thaumcraft$entries());
        
        
        boolean major=id.getPath().equals("chests/simple_dungeon")||id.getPath().endsWith("_temple")||id.getPath().endsWith("_pyramid");
        entries.add(entry(major?4:3,1,2));
        if(id.getPath().equals("chests/stronghold_library"))entries.add(entry(20,3,6));
        pool.thaumcraft$entries(List.copyOf(entries));
    }
    private static LootPoolEntryContainer entry(int weight,int min,int max){return LootItem.lootTableItem(R3Equipment.KNOWLEDGE_FRAGMENT.get()).setWeight(weight).apply(SetItemCountFunction.setCount(UniformGenerator.between(min,max))).build();}
    private KnowledgeFragmentLoot(){}
}
