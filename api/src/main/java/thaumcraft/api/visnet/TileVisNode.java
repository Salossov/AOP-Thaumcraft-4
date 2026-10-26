package thaumcraft.api.visnet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;

/** A loaded member of a TC4 centivis network. Networks never load chunks. */
public abstract class TileVisNode extends BlockEntity {
    private BlockPos parent;
    private java.lang.ref.WeakReference<TileVisNode> resolvedParent;
    private boolean parentResolved;
    private int pulseColor = 0xffffff;
    private long pulseUntil;

    protected TileVisNode(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    public abstract int getRange();
    public abstract boolean isSource();
    public byte getAttunement() { return -1; }
    public BlockPos getParentPosition() { return parent; }
    public int pulseColor() { return pulseColor; }
    public boolean isPulsing() { return level != null && level.getGameTime() <= pulseUntil; }

    public int consumeVis(Aspect aspect, int amount) {
        if (amount <= 0 || level == null || level.isClientSide) return 0;
        VisNetHandler.refresh(level);
        var upstream = getParent();
        int taken = upstream == null ? 0 : upstream.consumeVis(aspect, amount);
        if (taken > 0) triggerConsumeEffect(aspect);
        return taken;
    }
    public TileVisNode getParent() {
        if (parentResolved) {
            var node = resolvedParent == null ? null : resolvedParent.get();
            return node != null && !node.isRemoved() && node.getLevel() == level && level != null
                    && level.hasChunkAt(node.getBlockPos()) ? node : null;
        }
        return parent != null && level != null && level.hasChunkAt(parent)
                && VisNetHandler.loadedBlockEntity(level, parent) instanceof TileVisNode node && !node.isRemoved() ? node : null;
    }
    public TileVisNode getRootSource() {
        var node = this;
        for (int i = 0; i < 512; i++) {
            if (node.isSource()) return node;
            node = node.getParent();
            if (node == null) return null;
        }
        return null;
    }
    void setNetworkParent(BlockPos value) {
        if (!java.util.Objects.equals(parent, value)) { parent = value; changedAndSync(); }
    }
    void setResolvedParent(TileVisNode value) {
        parentResolved = true;
        resolvedParent = value == null ? null : new java.lang.ref.WeakReference<>(value);
        setNetworkParent(value == null ? null : value.getBlockPos());
    }
    public void triggerConsumeEffect(Aspect aspect) {
        if (level == null || level.isClientSide) return;
        pulseColor = aspect.getColor(); pulseUntil = level.getGameTime() + 5;
        changedAndSync();
    }
    public void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override public void onLoad() { super.onLoad(); if (level != null && !level.isClientSide) VisNetHandler.addNode(level, this); }
    @Override public void setRemoved() { if (level != null && !level.isClientSide) VisNetHandler.removeNode(level, this); super.setRemoved(); }
    @Override public void onChunkUnloaded() { if (level != null && !level.isClientSide) VisNetHandler.removeNode(level, this); super.onChunkUnloaded(); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) { super.saveAdditional(tag, lookup); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        parent = tag.contains("VisParent") ? BlockPos.of(tag.getLong("VisParent")) : null;
        parentResolved = false; resolvedParent = null;
        pulseColor = tag.contains("VisPulseColor") ? tag.getInt("VisPulseColor") : 0xffffff;
        pulseUntil = tag.getLong("VisPulseUntil");
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        var tag = saveWithoutMetadata(lookup);
        if (parent != null) tag.putLong("VisParent", parent.asLong());
        tag.putInt("VisPulseColor", pulseColor); tag.putLong("VisPulseUntil", pulseUntil);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
