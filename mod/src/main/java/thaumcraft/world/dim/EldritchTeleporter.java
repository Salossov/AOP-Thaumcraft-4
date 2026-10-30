package thaumcraft.world.dim;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import thaumcraft.registry.R5Content;

/** TC4's 128-block, three-dimensional portal search. Cache lifetime belongs to a world. */
@EventBusSubscriber(modid="thaumcraft_aop")
public final class EldritchTeleporter {
    public static final ResourceKey<Level> OUTER=ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse("thaumcraft:outer_lands"));
    private record Cached(BlockPos pos,long used){}
    private static final Map<ServerLevel,Map<Long,Cached>> CACHE=new IdentityHashMap<>();
    private static final Map<ServerLevel,Map<Long,Preparation>> PREPARING=new IdentityHashMap<>();
    private static final class Preparation {
        final thaumcraft.world.ChunkPreparation chunks;
        long used;
        Preparation(ServerLevel level,Vec3 position){int x=Mth.floor(position.x),z=Mth.floor(position.z);chunks=new thaumcraft.world.ChunkPreparation(level,(x-128)>>4,(x+128)>>4,(z-128)>>4,(z+128)>>4);used=level.getGameTime();}
    }
    /** Called while a player remains in a portal; no chunk load or future join on this path. */
    public static boolean prepare(ServerLevel destination,Vec3 position){
        long key=ChunkPos.asLong(Mth.floor(position.x)>>4,Mth.floor(position.z)>>4);
        var cache=CACHE.get(destination);var cached=cache==null?null:cache.get(key);
        if(cached!=null&&destination.hasChunkAt(cached.pos)&&destination.getBlockState(cached.pos).is(R5Content.ELDRITCH_PORTAL.get()))return true;
        boolean loaded=true;int cx=Mth.floor(position.x)>>4,cz=Mth.floor(position.z)>>4;
        for(int x=cx-8;x<=cx+8&&loaded;x++)for(int z=cz-8;z<=cz+8;z++)if(destination.getChunkSource().getChunkNow(x,z)==null){loaded=false;break;}
        if(loaded)return true;
        var jobs=PREPARING.computeIfAbsent(destination,l->new LinkedHashMap<>());
        // An overlapping request in the same origin chunk can have a one-block shifted range.
        // Cover the entire possible range for that chunk, retaining the exact search bounds below.
        var job=jobs.computeIfAbsent(key,k->new Preparation(destination,new Vec3((Mth.floor(position.x)>>4)*16+8,position.y,(Mth.floor(position.z)>>4)*16+8)));
        job.used=destination.getGameTime();
        return job.chunks.advance(0);
    }
    public static void releasePreparation(ServerLevel destination,Vec3 position){var jobs=PREPARING.get(destination);if(jobs==null)return;var job=jobs.remove(ChunkPos.asLong(Mth.floor(position.x)>>4,Mth.floor(position.z)>>4));if(job!=null)job.chunks.close();}

    public static BlockPos findPortal(ServerLevel destination,Vec3 position){
        int originX=Mth.floor(position.x),originZ=Mth.floor(position.z);
        long key=ChunkPos.asLong(originX>>4,originZ>>4);
        var cache=CACHE.computeIfAbsent(destination,l->new HashMap<>());
        var cached=cache.get(key);
        if(cached!=null){
            if(destination.getBlockState(cached.pos).is(R5Content.ELDRITCH_PORTAL.get())){
                cache.put(key,new Cached(cached.pos,destination.getGameTime()));return cached.pos;
            }
            cache.remove(key);
        }
        int minX=originX-128,maxX=originX+128,minZ=originZ-128,maxZ=originZ+128;
        BlockPos best=null;double distance=Double.POSITIVE_INFINITY;
        // Palette filtering skips empty sections while preserving the old full-height search.
        for(int cx=minX>>4;cx<=maxX>>4;cx++)for(int cz=minZ>>4;cz<=maxZ>>4;cz++){
            var chunk=destination.getChunk(cx,cz);var sections=chunk.getSections();
            for(int section=sections.length-1;section>=0;section--){
                var blocks=sections[section];if(!blocks.maybeHas(s->s.is(R5Content.ELDRITCH_PORTAL.get())))continue;
                int baseY=destination.getMinBuildHeight()+section*16;
                for(int x=Math.max(minX,cx*16);x<=Math.min(maxX,cx*16+15);x++)for(int z=Math.max(minZ,cz*16);z<=Math.min(maxZ,cz*16+15);z++)for(int y=15;y>=0;y--){
                    if(!blocks.getBlockState(x&15,y,z&15).is(R5Content.ELDRITCH_PORTAL.get()))continue;
                    var candidate=new BlockPos(x,baseY+y,z);double squared=Vec3.atCenterOf(candidate).distanceToSqr(position);
                    if(squared<distance||(squared==distance&&before(candidate,best))){best=candidate;distance=squared;}
                }
            }
        }
        if(best!=null)cache.put(key,new Cached(best,destination.getGameTime()));return best;
    }
    private static boolean before(BlockPos a,BlockPos b){return b==null||a.getX()<b.getX()||a.getX()==b.getX()&&(a.getZ()<b.getZ()||a.getZ()==b.getZ()&&a.getY()>b.getY());}
    public static DimensionTransition transition(ServerLevel destination,Entity entity){
        var portal=findPortal(destination,entity.position());
        if(portal!=null){var random=destination.random;var pos=new Vec3(portal.getX()+.5+(random.nextBoolean()?1:-1),portal.getY(),portal.getZ()+.5+(random.nextBoolean()?1:-1));return new DimensionTransition(destination,pos,Vec3.ZERO,entity.getYRot(),entity.getXRot(),DimensionTransition.PLACE_PORTAL_TICKET);}
        // The original teleporter never constructs a replacement portal.
        if(destination.dimension()==Level.END){int x=Mth.floor(entity.getX()),z=Mth.floor(entity.getZ());return new DimensionTransition(destination,new Vec3(x,destination.getHeight(Heightmap.Types.MOTION_BLOCKING,x,z)+4,z),Vec3.ZERO,entity.getYRot(),0,DimensionTransition.PLACE_PORTAL_TICKET);}
        return new DimensionTransition(destination,entity.position(),entity.getDeltaMovement(),entity.getYRot(),entity.getXRot(),DimensionTransition.PLACE_PORTAL_TICKET);
    }
    public static void expire(ServerLevel level,long now){if(now%100!=0)return;var cache=CACHE.get(level);if(cache!=null)cache.values().removeIf(value->value.used<now-600);}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){for(var level:event.getServer().getAllLevels()){
        expire(level,level.getGameTime());var jobs=PREPARING.get(level);if(jobs==null)continue;int budget=2;
        for(var iterator=jobs.values().iterator();iterator.hasNext();){var job=iterator.next();if(job.used<level.getGameTime()-100){job.chunks.close();iterator.remove();continue;}int before=job.chunks.submitted();job.chunks.advance(budget);budget-=job.chunks.submitted()-before;}
    }}
    @SubscribeEvent public static void unloaded(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level){CACHE.remove(level);var jobs=PREPARING.remove(level);if(jobs!=null)jobs.values().forEach(job->job.chunks.close());}}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){CACHE.keySet().removeIf(level->level.getServer()==event.getServer());for(var level:List.copyOf(PREPARING.keySet()))if(level.getServer()==event.getServer()){PREPARING.remove(level).values().forEach(job->job.chunks.close());}}
    private EldritchTeleporter(){}
}
