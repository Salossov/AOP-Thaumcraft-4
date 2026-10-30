package thaumcraft.addon;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import thaumcraft.api.world.ChunkPreparationApi;
import thaumcraft.world.ChunkPreparation;

/** Owner lifetime is independent of the FULL tickets held for its operation. */
@EventBusSubscriber(modid="thaumcraft_aop")
public final class PreparedChunks implements ChunkPreparationApi.Access {
    private static final Set<Job> ACTIVE=Collections.newSetFromMap(new IdentityHashMap<>());
    @Override public ChunkPreparationApi.Request prepare(BlockEntity owner,Collection<ChunkPos> positions){
        if(!(owner.getLevel() instanceof ServerLevel level))throw new IllegalStateException("Registered server owner required");
        return prepare(owner,level,positions);
    }
    @Override public ChunkPreparationApi.Request prepare(BlockEntity owner,ServerLevel destination,Collection<ChunkPos> positions){
        if(!(owner.getLevel() instanceof ServerLevel level)||!level.getServer().isSameThread())throw new IllegalStateException("Registered server owner and server thread required");
        if(destination.getServer()!=level.getServer())throw new IllegalArgumentException("Owner and destination must belong to the same server");
        var job=new Job(level,destination,owner,positions);
        if(!job.present()){job.close();throw new IllegalStateException("Chunk preparation owner is not registered");}
        ACTIVE.add(job);return job;
    }
    @Override public ChunkPreparationApi.Request prepare(ServerPlayer owner,Collection<ChunkPos> positions){
        return prepare(owner,owner.serverLevel(),positions);
    }
    @Override public ChunkPreparationApi.Request prepare(ServerPlayer owner,ServerLevel destination,Collection<ChunkPos> positions){
        var level=owner.serverLevel();
        if(!level.getServer().isSameThread())throw new IllegalStateException("Server thread required");
        if(destination.getServer()!=level.getServer())throw new IllegalArgumentException("Owner and destination must belong to the same server");
        var job=new Job(level,destination,owner,positions);
        if(!job.present()){job.close();throw new IllegalStateException("Chunk preparation player is not connected and alive");}
        ACTIVE.add(job);return job;
    }
    private static final class Job implements ChunkPreparationApi.Request {
        final ServerLevel ownerLevel,level;final BlockEntity owner;final ServerPlayer player;final BlockPos origin;
        final List<ChunkPos> positions;final ChunkPreparation preparation;boolean closed;
        Job(ServerLevel ownerLevel,ServerLevel level,BlockEntity owner,Collection<ChunkPos> positions){
            this.ownerLevel=ownerLevel;this.level=level;this.owner=owner;this.player=null;origin=owner.getBlockPos().immutable();
            var unique=new LinkedHashSet<ChunkPos>();for(var pos:positions)unique.add(Objects.requireNonNull(pos));
            this.positions=List.copyOf(unique);preparation=new ChunkPreparation(level,this.positions);
        }
        Job(ServerLevel ownerLevel,ServerLevel level,ServerPlayer player,Collection<ChunkPos> positions){
            this.ownerLevel=ownerLevel;this.level=level;this.owner=null;this.player=player;origin=player.blockPosition().immutable();
            var unique=new LinkedHashSet<ChunkPos>();for(var pos:positions)unique.add(Objects.requireNonNull(pos));
            this.positions=List.copyOf(unique);preparation=new ChunkPreparation(level,this.positions);
        }
        boolean present(){
            if(level.getServer().getLevel(level.dimension())!=level||ownerLevel.getServer().getLevel(ownerLevel.dimension())!=ownerLevel)return false;
            if(player!=null)return player.isAlive()&&!player.isRemoved()&&player.level()==ownerLevel
                    &&level.getServer().getPlayerList().getPlayer(player.getUUID())==player;
            if(owner.isRemoved()||owner.getLevel()!=ownerLevel||!owner.getBlockPos().equals(origin))return false;
            var chunk=ownerLevel.getChunkSource().getChunkNow(origin.getX()>>4,origin.getZ()>>4);
            return chunk!=null&&chunk.getBlockEntities().get(origin)==owner;
        }
        @Override public boolean advance(int coldBudget){
            if(!level.getServer().isSameThread())throw new IllegalStateException("Server thread required");
            if(closed)return false;
            if(!present()){close();return false;}
            if(!preparation.advanceCold(coldBudget))return false;
            for(var pos:positions)if(level.getChunkSource().getChunkNow(pos.x,pos.z)==null)return false;
            return true;
        }
        @Override public boolean closed(){return closed;}
        @Override public int submitted(){return preparation.submitted();}
        @Override public int total(){return positions.size();}
        @Override public void close(){if(closed)return;if(!level.getServer().isSameThread())throw new IllegalStateException("Server thread required");closed=true;preparation.close();ACTIVE.remove(this);}
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){
        if(event.getLevel() instanceof ServerLevel level)for(var job:List.copyOf(ACTIVE))
            if((job.level==level||job.ownerLevel==level)&&(!job.present()||(job.owner!=null&&!job.ownerLevel.shouldTickBlocksAt(ChunkPos.asLong(job.origin)))))job.close();
    }
    @SubscribeEvent public static void unloaded(LevelEvent.Unload event){for(var job:List.copyOf(ACTIVE))if(job.level==event.getLevel()||job.ownerLevel==event.getLevel())job.close();}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){for(var job:List.copyOf(ACTIVE))if(job.level.getServer()==event.getServer())job.close();}
}
