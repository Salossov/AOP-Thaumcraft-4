package thaumcraft.production;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.*;

public abstract class EssentiaBlockEntity extends BlockEntity implements IAspectContainer, IEssentiaTransport {
    public final ProductionBlock.Kind kind;
    public final EssentiaStore store;
    // Unknown addon aspects remain serialized, never exposed as usable essentia.
    private CompoundTag pendingEssentia;
    private String pendingFilter;
    private final boolean mixed;
    protected EssentiaBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int capacity, boolean mixed, boolean voidOverflow) {
        super(type, pos, state); kind = ((ProductionBlock) state.getBlock()).kind;
        store = new EssentiaStore(capacity, mixed, voidOverflow);
        this.mixed = mixed;
    }
    public abstract void serverTick();
    protected Direction facing() { return getBlockState().getValue(ProductionBlock.FACING); }
    public void sync() { super.setChanged(); if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
    final boolean hasPendingEssentia() { return pendingEssentia != null; }
    @Override public AspectList getAspects() { return hasPendingEssentia() ? new AspectList() : store.getAspects(); }
    @Override public void setAspects(AspectList aspects) { if (!hasPendingEssentia()) { store.setAspects(aspects); sync(); } }
    @Override public boolean doesContainerAccept(Aspect a) { return !hasPendingEssentia() && store.doesContainerAccept(a); }
    @Override public int addToContainer(Aspect a, int n) { if (hasPendingEssentia()) return n; int remaining = store.addToContainer(a, n); if (remaining != n) sync(); return remaining; }
    @Override public boolean takeFromContainer(Aspect a, int n) { if (hasPendingEssentia()) return false; boolean taken = store.takeFromContainer(a, n); if (taken && n > 0) sync(); return taken; }
    @Override public boolean doesContainerContainAmount(Aspect a, int n) { return store.doesContainerContainAmount(a, n); }
    @Override public int containerContains(Aspect a) { return store.containerContains(a); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); var aspects = new CompoundTag(); store.getAspects().asMap().forEach((a, n) -> aspects.putInt(a.getTag(), n));
        tag.put("Essentia", hasPendingEssentia() ? pendingEssentia.copy() : aspects);
        tag.remove("AspectFilter");
        if (hasPendingEssentia() && pendingFilter != null) tag.putString("AspectFilter", pendingFilter);
        else if (store.filter() != null) tag.putString("AspectFilter", store.filter().getTag());
        tag.putInt("TC4ProductionVersion", 1);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); store.clear(); store.filter(null);
        pendingEssentia = null; pendingFilter = null;
        var saved = new AspectList(); var values = tag.getCompound("Essentia");
        var sanitized = new CompoundTag(); int remaining = store.capacity(); String first = null;
        boolean unknown = false;
        for (var key : values.getAllKeys()) {
            if (!validAspectId(key) || !values.contains(key, net.minecraft.nbt.Tag.TAG_INT)) continue;
            var aspect = Aspect.getAspect(key); String canonical = aspect == null ? key : aspect.getTag();
            int amount = Math.min(remaining, Math.max(0, values.getInt(key)));
            if (amount == 0 || (!mixed && first != null && !first.equals(canonical))) continue;
            first = canonical; remaining -= amount;
            sanitized.putInt(canonical, sanitized.getInt(canonical) + amount);
            if (aspect == null) unknown = true; else saved.add(aspect, amount);
        }
        String filterId = tag.getString("AspectFilter");
        if (!validAspectId(filterId)) filterId = null;
        var filter = filterId == null ? null : Aspect.getAspect(filterId);
        if (!mixed && filter != null && first != null && !first.equals(filter.getTag())) { filter = null; filterId = null; }
        unknown |= filterId != null && filter == null;
        if (unknown) { pendingEssentia = sanitized; pendingFilter = filterId; }
        else { store.setAspects(saved); store.filter(filter); }
    }
    private static boolean validAspectId(String id) {
        return id.length() <= 128 && id.matches("[a-z0-9_.-]+(?::[a-z0-9_./-]+)?");
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider r) { return saveWithoutMetadata(r); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public boolean isConnectable(Direction face) { return false; }
    @Override public boolean canInputFrom(Direction face) { return false; }
    @Override public boolean canOutputTo(Direction face) { return false; }
    @Override public void setSuction(Aspect a, int n) {}
    @Override public Aspect getSuctionType(Direction face) { return null; }
    @Override public int getSuctionAmount(Direction face) { return 0; }
    @Override public int getMinimumSuction() { return 0; }
    @Override public boolean renderExtendedTube() { return false; }
    @Override public Aspect getEssentiaType(Direction face) { return store.first(); }
    @Override public int getEssentiaAmount(Direction face) { return store.amount(); }
    @Override public int addEssentia(Aspect a, int n, Direction face) { return canInputFrom(face) ? n - addToContainer(a, n) : 0; }
    @Override public int takeEssentia(Aspect a, int n, Direction face) { return canOutputTo(face) && takeFromContainer(a, n) ? n : 0; }
}
