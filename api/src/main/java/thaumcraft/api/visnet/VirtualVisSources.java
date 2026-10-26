package thaumcraft.api.visnet;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import thaumcraft.api.aspects.Aspect;

/** Lifecycle adapter only; normal VisNet topology and consumption remain in VisNetHandler. */
final class VirtualVisSources {
    private record Key(BlockPos ownerPosition, Direction face) {}
    private static final Map<Level, Map<Key, Registration>> SOURCES = new WeakHashMap<>();
    static {
        NeoForge.EVENT_BUS.addListener(VirtualVisSources::chunkUnloaded);
        NeoForge.EVENT_BUS.addListener(VirtualVisSources::levelUnloaded);
        NeoForge.EVENT_BUS.addListener(VirtualVisSources::serverStopped);
        NeoForge.EVENT_BUS.addListener(VirtualVisSources::levelTick);
    }
    static VirtualSourceRegistration register(BlockEntity owner, Direction face, VirtualVisSource source) {
        Objects.requireNonNull(owner); Objects.requireNonNull(face); Objects.requireNonNull(source);
        if (!(owner.getLevel() instanceof ServerLevel level)) throw new IllegalStateException("A server-level owner is required");
        requireThread(level);
        checkRange(source.range());
        var key = new Key(owner.getBlockPos().immutable(), face);
        var registration = new Registration(level, owner, key, source);
        if (!registration.valid()) throw new IllegalArgumentException("Virtual Vis source is not attached to a live, loaded owner");
        var entries = SOURCES.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        var previous = entries.get(key);
        if (previous != null) {
            if (previous.valid()) throw new IllegalStateException("Virtual Vis source already registered on " + key);
            previous.closeInternal(false);
        }
        registration.node = new VirtualNode(registration, owner);
        entries.put(key, registration);
        VisNetHandler.virtualSourcesChanged(level);
        return registration;
    }
    static List<TileVisNode> loaded(Level level) {
        var entries = SOURCES.get(level);
        if (entries == null) return List.of();
        var result = new ArrayList<TileVisNode>();
        var registrations = new ArrayList<>(entries.values());
        registrations.sort(Comparator.comparingLong((Registration value) -> value.key.ownerPosition.asLong())
                .thenComparingInt(value -> value.key.face.ordinal()));
        for (var registration : registrations) {
            if (registration.valid()) result.add(registration.node);
            else registration.closeInternal(true);
        }
        return result;
    }
    private static void requireThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Virtual Vis source requires the server thread");
    }
    private static void checkRange(int range) {
        if (range < 0 || range > 32767) throw new IllegalArgumentException("Virtual Vis range outside 0..32767");
    }
    private static void chunkUnloaded(ChunkEvent.Unload event) {
        var entries = SOURCES.get(event.getLevel());
        if (entries == null) return;
        var chunk = event.getChunk().getPos();
        for (var registration : new ArrayList<>(entries.values())) {
            if (new net.minecraft.world.level.ChunkPos(registration.key.ownerPosition).equals(chunk)
                    || new net.minecraft.world.level.ChunkPos(registration.position).equals(chunk)) registration.closeInternal(true);
        }
    }
    private static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) forget(level);
    }
    private static void levelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide) return;
        var entries = SOURCES.get(event.getLevel());
        if (entries != null) for (var registration : new ArrayList<>(entries.values())) {
            if (!registration.valid()) registration.closeInternal(true);
        }
    }
    private static void serverStopped(ServerStoppedEvent event) {
        for (var level : new ArrayList<>(SOURCES.keySet())) if (level.getServer() == event.getServer()) forget(level);
    }
    private static void forget(Level level) {
        var entries = SOURCES.remove(level);
        if (entries != null) for (var registration : new ArrayList<>(entries.values())) registration.closeInternal(false);
        VisNetHandler.forgetLevel(level);
    }
    private static final class Registration implements VirtualSourceRegistration {
        private final WeakReference<ServerLevel> level;
        private final WeakReference<BlockEntity> owner;
        private final Key key;
        private final BlockPos position;
        private VirtualVisSource source;
        private VirtualNode node;
        private boolean closed, draining;
        Registration(ServerLevel level, BlockEntity owner, Key key, VirtualVisSource source) {
            this.level = new WeakReference<>(level); this.owner = new WeakReference<>(owner);
            this.key = key; this.position = key.ownerPosition.relative(key.face); this.source = source;
        }
        boolean valid() {
            var world = level.get(); var block = owner.get();
            return !closed && world != null && block != null && !block.isRemoved() && block.getLevel() == world
                    && block.getBlockPos().equals(key.ownerPosition) && world.hasChunkAt(key.ownerPosition)
                    && world.hasChunkAt(position) && VisNetHandler.loadedBlockEntity(world, key.ownerPosition) == block && source.valid();
        }
        @Override public void refresh() {
            var world = level.get();
            if (closed || world == null) throw new IllegalStateException("Virtual Vis registration is closed");
            requireThread(world);
            if (!valid()) { closeInternal(true); return; }
            checkRange(source.range());
            VisNetHandler.virtualSourcesChanged(world);
        }
        @Override public void close() {
            if (closed) return;
            var world = level.get();
            if (world != null) requireThread(world);
            closeInternal(true);
        }
        void closeInternal(boolean invalidate) {
            if (closed) return;
            closed = true;
            var world = level.get();
            var entries = world == null ? null : SOURCES.get(world);
            if (entries != null) entries.remove(key, this);
            source = null; node = null;
            if (invalidate && world != null) VisNetHandler.virtualSourcesChanged(world);
        }
        int drain(Aspect aspect, int amount) {
            var world = level.get();
            if (world == null || amount <= 0 || aspect == null || !aspect.isPrimal() || !valid() || draining) return 0;
            requireThread(world);
            draining = true;
            try {
                int actual = source.drain(aspect, amount);
                if (actual < 0 || actual > amount) throw new IllegalStateException("Virtual Vis source returned an invalid amount");
                return actual;
            } finally { draining = false; }
        }
    }
    private static final class VirtualNode extends TileVisNode {
        private final Registration registration;
        VirtualNode(Registration registration, BlockEntity owner) {
            super(owner.getType(), registration.position, owner.getBlockState());
            this.registration = registration;
            setLevel(owner.getLevel());
        }
        @Override public int getRange() { return registration.closed ? 0 : registration.source.range(); }
        @Override public byte getAttunement() { return registration.closed ? -1 : registration.source.attunement(); }
        @Override public boolean isSource() { return true; }
        @Override public boolean isRemoved() { return !registration.valid(); }
        @Override public int consumeVis(Aspect aspect, int amount) { return registration.drain(aspect, amount); }
    }
    private VirtualVisSources() {}
}
