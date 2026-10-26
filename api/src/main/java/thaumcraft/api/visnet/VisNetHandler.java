package thaumcraft.api.visnet;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.aspects.Aspect;

/** Original source/relay trees, eight-block links and one nearest outlet per source. */
public final class VisNetHandler {
    private static final Map<Level, Network> NETWORKS = new WeakHashMap<>();
    private static final class Network {
        final Map<BlockPos, WeakReference<TileVisNode>> nodes = new HashMap<>();
        boolean dirty = true, rebuilding;
        long refreshed = Long.MIN_VALUE;
    }
    /** Registers a virtual source one block in front of the exact live owner, without placing a block. */
    public static VirtualSourceRegistration registerVirtualSource(BlockEntity owner, Direction face, VirtualVisSource source) {
        return VirtualVisSources.register(owner, face, source);
    }
    static void virtualSourcesChanged(Level level) { NETWORKS.computeIfAbsent(level, ignored -> new Network()).dirty = true; }
    static void forgetLevel(Level level) { NETWORKS.remove(level); }
    public static void addNode(Level level, TileVisNode node) {
        var network = NETWORKS.computeIfAbsent(level, ignored -> new Network());
        network.nodes.put(node.getBlockPos(), new WeakReference<>(node)); network.dirty = true;
    }
    public static void removeNode(Level level, TileVisNode node) {
        var network = NETWORKS.get(level);
        if (network == null) return;
        var ref = network.nodes.get(node.getBlockPos());
        if (ref != null && ref.get() == node) { network.nodes.remove(node.getBlockPos()); network.dirty = true; }
    }
    public static void invalidate(Level level) { var network = NETWORKS.get(level); if (network != null) network.dirty = true; }
    static BlockEntity loadedBlockEntity(Level level, BlockPos pos) {
        var chunk = level instanceof ServerLevel server
                ? server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4)
                : level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
        return chunk instanceof LevelChunk loaded
                ? loaded.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK) : null;
    }
    public static List<TileVisNode> loadedNodes(Level level) {
        var network = NETWORKS.get(level);
        if (network == null) return List.of();
        var result = new ArrayList<TileVisNode>();
        network.nodes.entrySet().removeIf(entry -> {
            var node = entry.getValue().get();
            boolean stale = node == null || node.isRemoved() || loadedBlockEntity(level, entry.getKey()) != node;
            if (!stale) result.add(node);
            return stale;
        });
        result.addAll(VirtualVisSources.loaded(level));
        result.sort(Comparator.comparingLong(node -> node.getBlockPos().asLong()));
        return result;
    }
    public static void refresh(Level level) {
        if (level.isClientSide) return;
        var network = NETWORKS.get(level);
        if (network == null || network.rebuilding || !network.dirty && level.getGameTime() - network.refreshed < 40) return;
        network.rebuilding = true;
        try {
            var nodes = loadedNodes(level);
            var connected = new ArrayList<TileVisNode>();
            var pending = new ArrayList<TileVisNode>();
            for (var node : nodes) {
                if (node.isSource()) {node.setResolvedParent(null);connected.add(node);} else pending.add(node);
            }
            boolean progress;
            do {
                progress = false;
                for (var iterator = pending.iterator(); iterator.hasNext();) {
                    var target = iterator.next(); TileVisNode closest = null; double distance = Double.MAX_VALUE;
                    for (var candidate : connected) {
                        double square = target.getBlockPos().distSqr(candidate.getBlockPos());
                        if (square <= 0 || square > target.getRange() * target.getRange()) continue;
                        if (target.getAttunement() != -1 && candidate.getAttunement() != -1 && target.getAttunement() != candidate.getAttunement()) continue;
                        double score = square - (candidate.isSource() ? target.getRange() * 2 : 0);
                        if (score < distance && canNodeBeSeen(target, candidate)) { closest = candidate; distance = score; }
                    }
                    if (closest != null) { target.setResolvedParent(closest); connected.add(target); iterator.remove(); progress = true; }
                }
            } while (progress && !pending.isEmpty());
            for(var node:pending)node.setResolvedParent(null);
            network.refreshed = level.getGameTime(); network.dirty = false;
        } finally { network.rebuilding = false; }
    }
    public static int drainVis(Level level, BlockPos consumer, Aspect aspect, int amount) {
        if (amount <= 0 || level.isClientSide || !aspect.isPrimal()) return 0;
        refresh(level);
        var nearest = new LinkedHashMap<TileVisNode, TileVisNode>();
        for (var node : loadedNodes(level)) {
            double distance = consumer.distSqr(node.getBlockPos());
            if (distance <= 0 || distance > node.getRange() * node.getRange()) continue;
            var source = node.getRootSource(); if (source == null) continue;
            var previous = nearest.get(source);
            if (previous == null || consumer.distSqr(previous.getBlockPos()) > distance) nearest.put(source, node);
        }
        int result = 0;
        for (var outlet : nearest.values()) { result += outlet.consumeVis(aspect, amount - result); if (result >= amount) break; }
        return result;
    }
    public static int drainVis(Level level, int x, int y, int z, Aspect aspect, int amount) { return drainVis(level, new BlockPos(x,y,z), aspect, amount); }
    public static boolean canNodeBeSeen(TileVisNode source, TileVisNode target) {
        var level = source.getLevel(); if (level == null || level != target.getLevel()) return false;
        Vec3 start = source.getBlockPos().getCenter(), end = target.getBlockPos().getCenter();
        var delta=end.subtract(start);int steps=(int)Math.ceil(delta.length()*2);
        for(int i=1;i<steps;i++)if(!level.hasChunkAt(BlockPos.containing(start.add(delta.scale((double)i/steps)))))return false;
        // Ignore the source's own shape without skipping intervening blocks.
        var hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()) {
            @Override public net.minecraft.world.phys.shapes.VoxelShape getBlockShape(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.BlockGetter getter, BlockPos pos) {
                return pos.equals(source.getBlockPos()) ? net.minecraft.world.phys.shapes.Shapes.empty() : super.getBlockShape(state, getter, pos);
            }
        });
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target.getBlockPos());
    }
    private VisNetHandler() {}
}
