package thaumcraft.world;

import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import thaumcraft.config.ThaumcraftConfig;

/** Finds actual natural placements, including cold chunks, using the ordinary population stream. */
@EventBusSubscriber(modid="thaumcraft_aop")
public final class NaturalStructureLocate {
    public static final String[] KINDS={"mound","hilltop","ring_altar","obelisk","cultists","totem","spider_tree","magical_forest","tainted_land","cancel"};
    private static final Map<String,Search> SEARCHES=new LinkedHashMap<>();
    private static final Map<ServerLevel,Integer> CURSORS=new IdentityHashMap<>();
    private static String owner(CommandSourceStack source){return source.getEntity()==null?"console":source.getEntity().getUUID().toString();}
    public static Component coordinates(BlockPos pos){
        String xyz=pos.getX()+" "+pos.getY()+" "+pos.getZ();
        return Component.literal("["+xyz+"]").withStyle(style->style.withColor(ChatFormatting.GREEN).withUnderlined(true)
            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/tp @s "+xyz))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.translatable("chat.coordinates.tooltip"))));
    }
    public static int start(CommandSourceStack source,String text){
        String name=text.toLowerCase(Locale.ROOT).replace('-','_');String owner=owner(source);
        if(name.equals("cancel")){var old=SEARCHES.remove(owner);if(old!=null)old.close();source.sendSuccess(()->Component.literal(old==null?"No locate search is running":"Locate cancelled"),false);return old==null?0:1;}
        var kind=switch(name){case "mound"->StructureIndexData.Kind.MOUND;case "hilltop","loot_altar"->StructureIndexData.Kind.HILLTOP;case "ring","ring_altar","obelisk"->StructureIndexData.Kind.RING_ALTAR;case "cultists"->StructureIndexData.Kind.CULTIST_RING;case "totem"->StructureIndexData.Kind.TOTEM;case "spider_tree"->StructureIndexData.Kind.SPIDER_TREE;default->null;};
        var biome=switch(name){case "magical_forest"->WorldBiomes.MAGICAL;case "tainted_land"->WorldBiomes.TAINT;default->null;};
        if(kind==null&&biome==null){source.sendFailure(Component.literal("Unknown locate target: "+text));return 0;}
        var level=source.getLevel();
        if(biome!=null&&level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream().noneMatch(b->b.is(biome))){source.sendFailure(Component.literal(text+" does not generate in this dimension"));return 0;}
        if(kind!=null&&(!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)||level.getChunkSource().getGenerator() instanceof FlatLevelSource||!ThaumcraftConfig.GENERATE_STRUCTURES.get()&&kind!=StructureIndexData.Kind.SPIDER_TREE||PopulationPolicy.blacklist(ThaumcraftConfig.DIMENSION_BLACKLIST.get(),level.dimension().location())!=-1)){
            var known=StructureIndexData.get(level).nearest(level,kind,BlockPos.containing(source.getPosition()).getX(),BlockPos.containing(source.getPosition()).getZ());
            if(known!=null){found(source,text,new BlockPos(known.x(),known.y()+1,known.z()));return 1;}
            source.sendFailure(Component.literal("Natural "+text+" generation is disabled for this world or dimension"));return 0;
        }
        var old=SEARCHES.remove(owner);if(old!=null)old.close();SEARCHES.put(owner,new Search(source,text,kind,biome));
        source.sendSuccess(()->Component.literal("Searching for "+text+" in generated and unexplored chunks... /thaumcraft locate cancel stops the search."),false);return 1;
    }
    private static void found(CommandSourceStack source,String text,BlockPos pos){source.sendSuccess(()->Component.literal(text+" found at ").append(coordinates(pos)),false);}
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){
        if(!(event.getLevel() instanceof ServerLevel level))return;
        for(var entry:List.copyOf(SEARCHES.entrySet())){var job=entry.getValue();if(job.level!=level)continue;var entity=job.source.getEntity();if(entity!=null&&(entity.isRemoved()||entity.level()!=level)){job.close();SEARCHES.remove(entry.getKey());}}
        var jobs=SEARCHES.entrySet().stream().filter(e->e.getValue().level==level).toList();if(jobs.isEmpty()){CURSORS.remove(level);return;}
        int cursor=Math.floorMod(CURSORS.getOrDefault(level,0),jobs.size());CURSORS.put(level,cursor+1);var entry=jobs.get(cursor);var job=entry.getValue();
        try{if(job.advance()){job.close();SEARCHES.remove(entry.getKey());}}
        catch(RuntimeException error){job.close();SEARCHES.remove(entry.getKey());job.source.sendFailure(Component.literal("Locate failed: "+error.getMessage()));thaumcraft.Thaumcraft.LOGGER.error("Natural locate failed",error);}
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level){for(var entry:List.copyOf(SEARCHES.entrySet()))if(entry.getValue().level==level){entry.getValue().close();SEARCHES.remove(entry.getKey());}CURSORS.remove(level);}}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){for(var entry:List.copyOf(SEARCHES.entrySet()))if(entry.getValue().level.getServer()==event.getServer()){entry.getValue().close();SEARCHES.remove(entry.getKey());}CURSORS.keySet().removeIf(level->level.getServer()==event.getServer());}
    private static final class Search implements AutoCloseable {
        final CommandSourceStack source;final ServerLevel level;final String name;final BlockPos origin;final ChunkPos originChunk;
        final StructureIndexData.Kind kind;final ResourceKey<Biome> biome;
        final ScatteredStructureSearch nativeSearch;
        int radius,index,checked,ticks;List<ChunkPos> ring;ChunkPos pending;ChunkPreparation chunks;StructureIndexData.Entry best;double distance=Double.MAX_VALUE;
        Search(CommandSourceStack source,String name,StructureIndexData.Kind kind,ResourceKey<Biome> biome){this.source=source;this.level=source.getLevel();this.name=name;this.kind=kind;this.biome=biome;origin=BlockPos.containing(source.getPosition());originChunk=new ChunkPos(origin);refresh();nativeSearch=kind==null?null:new ScatteredStructureSearch(level,origin,net.minecraft.resources.ResourceLocation.parse("thaumcraft:locate/"+kind.name().toLowerCase(Locale.ROOT)),512);}
        private void refresh(){if(kind==null)return;best=StructureIndexData.get(level).nearest(level,kind,origin.getX(),origin.getZ());if(best!=null)distance=square(best.x()-origin.getX())+square(best.z()-origin.getZ());}
        private static double square(double v){return v*v;}
        private double minimum(ChunkPos p){double x=Math.max(0,Math.max(p.getMinBlockX()-origin.getX(),origin.getX()-p.getMaxBlockX()))-32,z=Math.max(0,Math.max(p.getMinBlockZ()-origin.getZ(),origin.getZ()-p.getMaxBlockZ()))-32;return square(Math.max(0,x))+square(Math.max(0,z));}
        boolean advance(){
            if(nativeSearch!=null){if(!nativeSearch.advance(128))return false;var pos=nativeSearch.result();if(best!=null&&(pos==null||square(best.x()-origin.getX())+square(best.z()-origin.getZ())<square(pos.getX()-origin.getX())+square(pos.getZ()-origin.getZ())))pos=new BlockPos(best.x(),best.y()+1,best.z());if(pos==null)source.sendFailure(Component.literal("No "+name+" within 8192 blocks"));else found(source,name,pos);return true;}
            if(++ticks>6000){source.sendFailure(Component.literal("Locate timed out after "+checked+" chunks. No matching natural placement found; search origin: ").append(coordinates(origin)));return true;}
            if(ticks%200==0)source.sendSuccess(()->Component.literal("Still searching for "+name+": "+checked+" chunks checked, radius "+radius*16+" blocks"),false);
            for(int budget=0;budget<8;budget++){
                if(pending!=null){
                    if(!chunks.advance(1))return false;
                    int x=pending.getMiddleBlockX(),z=pending.getMiddleBlockZ(),y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);if(level.getBiome(new BlockPos(x,y,z)).is(biome)){found(source,name,new BlockPos(x,y,z));return true;}
                    release();checked++;index++;
                }
                if(ring==null){ring=new ArrayList<>();for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)if(Math.max(Math.abs(x),Math.abs(z))==radius)ring.add(new ChunkPos(originChunk.x+x,originChunk.z+z));ring.sort(Comparator.comparingDouble(this::minimum));index=0;}
                if(index==ring.size()){
                    if(++radius>256){source.sendFailure(Component.literal("No "+name+" within 4096 blocks"));return true;}
                    ring=null;continue;
                }
                var candidate=ring.get(index);if(minimum(candidate)>distance){index++;continue;}
                if(biome!=null){var generator=level.getChunkSource().getGenerator();int x=candidate.getMiddleBlockX(),z=candidate.getMiddleBlockZ();int y=generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,level,level.getChunkSource().randomState());if(!generator.getBiomeSource().getNoiseBiome(x>>2,y>>2,z>>2,level.getChunkSource().randomState().sampler()).is(biome)){checked++;index++;continue;}}
                pending=candidate;chunks=new ChunkPreparation(level,candidate.x,candidate.x,candidate.z,candidate.z);
            }
            return false;
        }
        private void release(){if(chunks!=null)chunks.close();chunks=null;pending=null;}
        @Override public void close(){if(nativeSearch!=null)nativeSearch.close();release();}
    }
    private NaturalStructureLocate(){}
}
