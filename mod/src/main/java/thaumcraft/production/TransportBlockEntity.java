package thaumcraft.production;

import java.util.Arrays;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.*;
import thaumcraft.api.visnet.VisNetHandler;
import thaumcraft.registry.R3Content;

/** TC4 local suction network. It never walks or loads a remote chunk. */
public final class TransportBlockEntity extends EssentiaBlockEntity {
    private final boolean[] open = {true, true, true, true, true, true};
    private final byte[] choked = new byte[6];
    private int count;
    private boolean started;
    public boolean allowFlow = true;
    private boolean wasPowered;
    public int venting;
    public int progress;
    public Aspect processing;
    private EssentiaSuction.Demand suction = new EssentiaSuction.Demand(null, 0);

    public TransportBlockEntity(BlockPos pos, BlockState state) {
        super(R3Content.TRANSPORT_ENTITY.get(), pos, state, capacity(((ProductionBlock) state.getBlock()).kind),
                ((ProductionBlock) state.getBlock()).kind == ProductionBlock.Kind.BUFFER || ((ProductionBlock) state.getBlock()).kind == ProductionBlock.Kind.RESERVOIR,
                ((ProductionBlock) state.getBlock()).kind == ProductionBlock.Kind.VOID_JAR);
    }
    private static int capacity(ProductionBlock.Kind k) {
        return switch (k) { case ALEMBIC -> 32; case JAR, VOID_JAR -> 64; case BUFFER -> 8; case RESERVOIR -> 256; default -> 1; };
    }
    public boolean tube() { return switch (kind) { case TUBE, VALVE, FILTER, RESTRICTED, ONEWAY -> true; default -> false; }; }
    @Override public Aspect getAspectFilter() { return kind == ProductionBlock.Kind.JAR || kind == ProductionBlock.Kind.VOID_JAR ? store.filter() : null; }
    @Override public JarType getJarType(){return kind==ProductionBlock.Kind.JAR?JarType.NORMAL:kind==ProductionBlock.Kind.VOID_JAR?JarType.VOID:JarType.NONE;}
    public IEssentiaTransport neighbour(Direction d) {
        if (level == null || !isConnectable(d)) return null;
        BlockPos adjacent = worldPosition.relative(d);
        if (!level.hasChunkAt(adjacent)) return null;
        var other = level.getCapability(EssentiaCapabilities.TRANSPORT, adjacent, d.getOpposite());
        return other != null && other.isConnectable(d.getOpposite()) ? other : null;
    }
    public boolean connected(Direction d) { return neighbour(d) != null; }
    @Override public boolean isConnectable(Direction d) {
        if (d == null) return false;
        return switch (kind) {
            case JAR, VOID_JAR -> d == Direction.UP;
            case ALEMBIC -> d != Direction.DOWN && d != facing();
            case CENTRIFUGE -> d.getAxis() == Direction.Axis.Y;
            case RESERVOIR, CRYSTALLIZER -> d == facing();
            case VALVE -> d != facing() && open[d.ordinal()];
            default -> open[d.ordinal()];
        };
    }
    @Override public boolean canInputFrom(Direction d) {
        return !hasPendingEssentia() && isConnectable(d) && kind != ProductionBlock.Kind.ALEMBIC && (kind != ProductionBlock.Kind.CENTRIFUGE || d == Direction.DOWN);
    }
    @Override public boolean canOutputTo(Direction d) {
        return !hasPendingEssentia() && isConnectable(d) && kind != ProductionBlock.Kind.CRYSTALLIZER && (kind != ProductionBlock.Kind.CENTRIFUGE || d == Direction.UP);
    }
    @Override public boolean renderExtendedTube() { return tube() || kind == ProductionBlock.Kind.BUFFER; }
    @Override public Aspect getSuctionType(Direction d) {
        return tube() ? suction.aspect() : kind == ProductionBlock.Kind.JAR || kind == ProductionBlock.Kind.VOID_JAR ? (store.filter() == null ? store.first() : store.filter()) : null;
    }
    @Override public void setSuction(Aspect a, int n) { if (kind != ProductionBlock.Kind.VALVE || allowFlow) suction = new EssentiaSuction.Demand(a, n); }
    @Override public int getSuctionAmount(Direction d) {
        if (hasPendingEssentia()) return 0;
        if (tube()) return suction.amount();
        return switch (kind) {
            case JAR -> store.amount() < 64 ? (store.filter() == null ? 32 : 64) : 0;
            case VOID_JAR -> store.filter() != null && store.amount() < 64 ? 48 : 32;
            case BUFFER -> d == null ? 0 : choked[d.ordinal()] == 2 ? 0 : choked[d.ordinal()] == 1 ? 1 : Math.max(1, bellows() * 32);
            case RESERVOIR -> store.amount() < 256 ? 24 : 0;
            case CENTRIFUGE -> d == Direction.DOWN && !powered() ? (processing == null ? 128 : 64) : 0;
            case CRYSTALLIZER -> powered() ? 0 : d == facing() && store.amount() == 0 ? 128 : 64;
            default -> 0;
        };
    }
    @Override public int getMinimumSuction() {
        return switch (kind) { case JAR -> store.filter() == null ? 32 : 64; case VOID_JAR -> store.filter() == null ? 32 : 48; case RESERVOIR -> 24; default -> 0; };
    }
    @Override public Aspect getEssentiaType(Direction d) {
        if (kind == ProductionBlock.Kind.RESERVOIR && d != null) return null;
        if (kind == ProductionBlock.Kind.BUFFER && store.amount() > 0 && level != null) {
            var types = store.getAspects().getAspects(); return types[level.random.nextInt(types.length)];
        }
        return store.first();
    }
    @Override public int addToContainer(Aspect a, int n) {
        if (hasPendingEssentia()) return n;
        if (a == null || n <= 0) return n;
        if (kind == ProductionBlock.Kind.CENTRIFUGE) {
            if (a.isPrimal() || processing != null || store.amount() > 0) return n;
            processing = a; progress = 39; sync(); return n - 1;
        }
        if ((kind == ProductionBlock.Kind.BUFFER || tube()) && n != 1) return n;
        return super.addToContainer(a, n);
    }
    @Override public boolean doesContainerAccept(Aspect a) {
        if (hasPendingEssentia()) return false;
        return kind == ProductionBlock.Kind.CENTRIFUGE ? a != null && !a.isPrimal() && processing == null && store.amount() == 0 : store.doesContainerAccept(a);
    }
    @Override public int addEssentia(Aspect a, int n, Direction d) {
        if (!canInputFrom(d) || n <= 0) return 0;
        int offered = tube() || kind == ProductionBlock.Kind.CENTRIFUGE || kind == ProductionBlock.Kind.BUFFER ? 1 : n;
        return offered - addToContainer(a, offered);
    }
    @Override public int takeEssentia(Aspect a, int n, Direction d) {
        if (!canOutputTo(d) || n <= 0) return 0;
        int requested = tube() ? 1 : n; return takeFromContainer(a, requested) ? requested : 0;
    }
    public boolean labelable() { return kind == ProductionBlock.Kind.JAR || kind == ProductionBlock.Kind.VOID_JAR || kind == ProductionBlock.Kind.ALEMBIC || kind == ProductionBlock.Kind.FILTER; }
    public boolean applyLabel(Aspect a) {
        if (hasPendingEssentia()) return false;
        if (!labelable() || a == null || (store.first() != null && store.first() != a)) return false;
        store.filter(a); sync(); return true;
    }
    public void removeLabel() { if (labelable() && store.filter() != null) { store.filter(null); sync(); } }
    public void wandClick(Direction d, boolean sneak) {
        if (hasPendingEssentia()) return;
        if (labelable() && sneak) { store.clear(); sync(); return; }
        if (kind == ProductionBlock.Kind.VALVE) { allowFlow = !allowFlow; sync(); return; }
        if (!tube() && kind != ProductionBlock.Kind.BUFFER) return;
        if (kind == ProductionBlock.Kind.BUFFER && sneak) { choked[d.ordinal()] = (byte) ((choked[d.ordinal()] + 1) % 3); sync(); return; }
        open[d.ordinal()] = !open[d.ordinal()];
        var adjacent = worldPosition.relative(d);
        if (level.hasChunkAt(adjacent) && level.getBlockEntity(adjacent) instanceof TransportBlockEntity other && (other.tube() || other.kind == ProductionBlock.Kind.BUFFER)) {
            other.open[d.getOpposite().ordinal()] = open[d.ordinal()]; other.sync();
        }
        sync();
    }
    public boolean useJar(ItemStack held,Player player,InteractionHand hand){
        if(kind!=ProductionBlock.Kind.ALEMBIC||!held.is(R3Content.WARDED_JAR.get().asItem())&&!held.is(R3Content.VOID_JAR.get().asItem()))return false;
        var aspect=store.first();if(aspect==null||store.amount()<=0)return false;
        var data=held.getOrDefault(DataComponents.BLOCK_ENTITY_DATA,CustomData.EMPTY).copyTag();var contents=data.getCompound("Essentia");int stored=0;
        for(var key:contents.getAllKeys()){int amount=contents.getInt(key);if(amount>0&&!key.equals(aspect.getTag()))return false;stored+=Math.max(0,amount);}
        String filterId=data.getString("AspectFilter");var filter=Aspect.getAspect(filterId);if(!filterId.isEmpty()&&filter!=aspect)return false;
        boolean voidJar=held.is(R3Content.VOID_JAR.get().asItem());int moved=voidJar?store.amount():Math.min(store.amount(),64-stored);if(moved<=0)return false;if(level.isClientSide)return true;
        var filled=held.copyWithCount(1);contents.putInt(aspect.getTag(),Math.min(64,stored+moved));data.put("Essentia",contents);data.putString("id","thaumcraft:essentia_transport");filled.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(data));
        if(!takeFromContainer(aspect,moved))return false;
        if(held.getCount()==1)player.setItemInHand(hand,filled);else{held.shrink(1);if(!player.getInventory().add(filled))player.drop(filled,false);}return true;
    }
    public boolean usePhial(ItemStack stack, Player player, InteractionHand hand) {
        boolean empty = stack.is(R3Content.EMPTY_PHIAL.get()); boolean full = stack.is(R3Content.ESSENTIA_PHIAL.get());
        if ((!empty && !full) || !labelable() || kind == ProductionBlock.Kind.FILTER) return false;
        Aspect a = empty ? store.first() : R3Content.aspect(stack);
        if (a == null || (empty ? store.containerContains(a) < 8 : store.capacity() - store.amount() < 8 || !store.doesContainerAccept(a))) return false;
        if (level.isClientSide) return true;
        ItemStack result = new ItemStack(empty ? R3Content.ESSENTIA_PHIAL.get() : R3Content.EMPTY_PHIAL.get());
        if (empty) { takeFromContainer(a, 8); R3Content.setAspect(result, a); } else addToContainer(a, 8);
        if (!player.isCreative()) stack.shrink(1);
        if (stack.isEmpty()) player.setItemInHand(hand, result); else if (!player.getInventory().add(result)) player.drop(result, false);
        return true;
    }
    private boolean powered() { return level != null && level.hasNeighborSignal(worldPosition); }
    private int bellows() { return R3Content.bellows(level, worldPosition); }
    @Override public void serverTick() {
        if (hasPendingEssentia()) return;
        if (!started) { count = level.random.nextInt(10); started = true; }
        if (count % 5 == 0) TubeBlock.updateConnections(this);
        if (venting > 0) { venting--; setChanged(); }
        if (tube()) {
            if (kind == ProductionBlock.Kind.VALVE && count % 5 == 0) {
                boolean power = powered();
                if (power != wasPowered) { allowFlow = !power; wasPowered = power; sync(); }
            }
            if (venting > 0) return;
            count++;
            if (count % 2 == 0) {
                var previous = suction; suction = new EssentiaSuction.Demand(null, 0);
                if (kind != ProductionBlock.Kind.VALVE || allowFlow) for (Direction d : Direction.values()) {
                    if (kind == ProductionBlock.Kind.ONEWAY && facing() != d.getOpposite()) continue;
                    var other = neighbour(d); if (other == null) continue;
                    suction = EssentiaSuction.consider(suction, new EssentiaSuction.Demand(other.getSuctionType(d.getOpposite()), other.getSuctionAmount(d.getOpposite())), store.filter(), store.first(), kind == ProductionBlock.Kind.RESTRICTED);
                }
                for (Direction d : Direction.values()) { var other = neighbour(d); if (other != null && EssentiaSuction.conflicts(suction, new EssentiaSuction.Demand(other.getSuctionType(d.getOpposite()), other.getSuctionAmount(d.getOpposite())))) { venting = 40;
                    // Original sends block event 1 with the conflicting aspect's colour index.
                    int aspectIndex = -1;if(suction.aspect()!=null){int n=0;for(var a:thaumcraft.api.aspects.Aspect.all().values()){if(!a.isPrimal()){if(a==suction.aspect()){aspectIndex=n;break;}n++;}}}
                    level.blockEvent(worldPosition, getBlockState().getBlock(), 1, aspectIndex);
                } }
                if (!suction.equals(previous) || venting > 0) sync();
            }
            if (count % 5 == 0 && suction.amount() > 0 && store.amount() == 0) for (Direction d : Direction.values()) {
                if (kind == ProductionBlock.Kind.ONEWAY && facing() == d.getOpposite()) continue;
                if (pull(d)) break;
            }
            return;
        }
        count++;
        if (kind == ProductionBlock.Kind.CENTRIFUGE) {
            if (powered()) return;
            if (processing == null && store.amount() == 0 && count % 5 == 0) pull(Direction.DOWN);
            if (progress > 0) { progress--; setChanged(); }
            if (processing != null && progress == 0 && store.amount() == 0) { var parts = processing.getComponents(); store.addToContainer(parts.get(level.random.nextInt(2)), 1); processing = null; sync(); }
        } else if (kind == ProductionBlock.Kind.CRYSTALLIZER) {
            if (count % 5 == 0 && !powered()) {
                if (store.amount() == 0) { pull(facing()); progress = 0; }
                // Original TileEssentiaCrystalizer accelerates crystal growth by draining Earth vis.
                else { progress += 1 + VisNetHandler.drainVis(level, worldPosition, Aspects.EARTH, Math.min(20, Math.max(1, (200 - progress) / 2))) * 2; setChanged(); }
            }
            if (progress >= 200 && store.amount() > 0) {
                var result = new ItemStack(R3Content.ESSENTIA_CRYSTAL.get()); R3Content.setAspect(result, store.first());
                net.minecraft.world.level.block.Block.popResource(level, worldPosition.relative(facing().getOpposite()), result); store.clear(); progress = 0; sync();
            }
        } else if (count % 5 == 0) {
            if (kind == ProductionBlock.Kind.JAR || kind == ProductionBlock.Kind.VOID_JAR) pull(Direction.UP);
            else if (kind == ProductionBlock.Kind.RESERVOIR) pull(facing());
            else if (kind == ProductionBlock.Kind.BUFFER) for (Direction d : Direction.values()) if (pull(d)) break;
        }
    }
    private boolean pull(Direction d) {
        if (!canInputFrom(d)) return false;
        var other = neighbour(d); if (other == null || !other.canOutputTo(d.getOpposite())) return false;
        if (!EssentiaSuction.canPull(getSuctionAmount(d), other.getSuctionAmount(d.getOpposite()), other.getMinimumSuction())) return false;
        Aspect wanted = getSuctionType(d), available = other.getEssentiaType(d.getOpposite());
        if (wanted == null) wanted = available == null ? other.getEssentiaType(null) : available;
        if (wanted == null || (available != null && wanted != available) || !doesContainerAccept(wanted)) return false;
        int taken = other.takeEssentia(wanted, 1, d.getOpposite());
        if (taken == 0) return false;
        int accepted = addEssentia(wanted, taken, d);
        if (accepted != taken) throw new IllegalStateException("Essentia transfer rejected after preflight");
        return true;
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.saveAdditional(tag, r); int bits = 0; for (int i = 0; i < 6; i++) if (open[i]) bits |= 1 << i;
        tag.putInt("OpenSides", bits); tag.putByteArray("ChokedSides", choked); tag.putBoolean("AllowFlow", allowFlow); tag.putBoolean("WasPowered", wasPowered);
        tag.putInt("Venting", venting); tag.putInt("Progress", progress); tag.putInt("Suction", suction.amount());
        if (suction.aspect() != null) tag.putString("SuctionType", suction.aspect().getTag()); if (processing != null) tag.putString("Processing", processing.getTag());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.loadAdditional(tag, r); Arrays.fill(open, true); Arrays.fill(choked, (byte) 0);
        if (tag.contains("OpenSides")) for (int i = 0; i < 6; i++) open[i] = (tag.getInt("OpenSides") & (1 << i)) != 0;
        var saved = tag.getByteArray("ChokedSides"); for (int i = 0; i < Math.min(6, saved.length); i++) choked[i] = (byte) Math.clamp(saved[i], 0, 2);
        allowFlow = !tag.contains("AllowFlow") || tag.getBoolean("AllowFlow"); wasPowered = tag.getBoolean("WasPowered"); venting = Math.clamp(tag.getInt("Venting"), 0, 40);
        progress = Math.clamp(tag.getInt("Progress"), 0, kind == ProductionBlock.Kind.CENTRIFUGE ? 39 : 200); processing = Aspect.getAspect(tag.getString("Processing"));
        if (processing != null && (kind != ProductionBlock.Kind.CENTRIFUGE || processing.isPrimal())) throw new IllegalArgumentException("Invalid centrifuge input");
        suction = new EssentiaSuction.Demand(Aspect.getAspect(tag.getString("SuctionType")), Math.clamp(tag.getInt("Suction"), 0, 256));
    }
}
