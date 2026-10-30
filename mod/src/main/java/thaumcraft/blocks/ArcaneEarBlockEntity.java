package thaumcraft.blocks;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.NoteBlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import thaumcraft.registry.R4Content;

@EventBusSubscriber(modid="thaumcraft_aop")
public final class ArcaneEarBlockEntity extends BlockEntity implements thaumcraft.api.world.IArcaneEar {
    public static final NoteBlockInstrument[] INSTRUMENTS={NoteBlockInstrument.HARP,NoteBlockInstrument.BASEDRUM,NoteBlockInstrument.SNARE,NoteBlockInstrument.HAT,NoteBlockInstrument.BASS};
    private record Played(long sequence,long time,BlockPos position,int tone,int note){}
    private static final Map<ServerLevel,List<Played>> EVENTS=new WeakHashMap<>();
    private static long sequence;
    private long consumedSequence;
    private int note,tone,redstoneTicks;
    public ArcaneEarBlockEntity(BlockPos p,BlockState s){super(R4Content.EAR_ENTITY.get(),p,s);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void played(NoteBlockEvent.Play e){if(e.getLevel() instanceof ServerLevel l){int tone=originalTone(e.getInstrument());if(tone>=0)EVENTS.computeIfAbsent(l,k->new ArrayList<>()).add(new Played(++sequence,l.getGameTime(),e.getPos().immutable(),tone,e.getVanillaNoteId()));}}
    @SubscribeEvent public static void expired(LevelTickEvent.Post e){if(e.getLevel() instanceof ServerLevel l){var events=EVENTS.get(l);if(events!=null){events.removeIf(n->n.time()<l.getGameTime()-1);if(events.isEmpty())EVENTS.remove(l);}}}
    private static int originalTone(NoteBlockInstrument instrument){for(int i=0;i<INSTRUMENTS.length;i++)if(INSTRUMENTS[i]==instrument)return i;return -1;}
    public int note(){return note;}
    @Override public void setNote(byte value){if(!(level instanceof ServerLevel server)||!server.getServer().isSameThread())throw new IllegalStateException("Arcane ear mutation requires its server thread");note=value;changed();}
    public int tone(){return tone;}
    public int redstoneTicks(){return redstoneTicks;}
    public void updateTone(){if(level==null)return;int current=originalTone(level.getBlockState(worldPosition.below()).instrument());current=Math.max(0,current);if(tone!=current){tone=current;changed();}}
    public void changePitch(){note=(note+1)%25;changed();}
    private void changed(){setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    private void output(boolean powered){var s=getBlockState();if(s.getValue(ArcaneEarBlock.POWERED)!=powered)level.setBlock(worldPosition,s.setValue(ArcaneEarBlock.POWERED,powered),3);level.updateNeighborsAt(worldPosition,s.getBlock());level.updateNeighborsAt(worldPosition.below(),s.getBlock());}
    public void triggerNote(boolean sound){if(level!=null&&level.isEmptyBlock(worldPosition.above()))level.blockEvent(worldPosition,getBlockState().getBlock(),sound?tone:-1,note);}
    public void tick(){if(!(level instanceof ServerLevel l))return;if(redstoneTicks>0&&--redstoneTicks==0)output(false);else if(redstoneTicks==0&&getBlockState().getValue(ArcaneEarBlock.POWERED))output(false);for(var n:EVENTS.getOrDefault(l,List.of()))if(n.sequence()>consumedSequence){consumedSequence=n.sequence();if(n.time()>=l.getGameTime()-1&&n.tone()==tone&&n.note()==note&&worldPosition.distSqr(n.position())<=4096){triggerNote(false);redstoneTicks=10;output(true);}}}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);tag.putByte("note",(byte)note);tag.putByte("tone",(byte)tone);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);note=tag.getByte("note");tone=Math.clamp(tag.getByte("tone"),0,4);redstoneTicks=0;consumedSequence=sequence;}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup){return saveWithoutMetadata(lookup);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
