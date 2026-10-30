package thaumcraft.items;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.network.OreScanPayload;

/** Uses the normal server harvesting path, including protection and loot hooks. */
@net.neoforged.fml.common.EventBusSubscriber(modid="thaumcraft_aop")
public final class ToolActions {
    private static final Set<UUID> BREAKING = new HashSet<>(), SWEEPING = new HashSet<>();

    /** Runs after protection handlers; nested harvesting retains normal loot and durability. */
    @net.neoforged.bus.api.SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)
    public static void breakBlock(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if(event.isCanceled()||!(event.getPlayer() instanceof ServerPlayer player))return;
        var stack=player.getMainHandItem();var item=stack.getItem();var pos=event.getPos();
        boolean handled=item instanceof ThaumicTools.Axe axe?axe.onBlockStartBreak(stack,pos,player)
            :item instanceof ThaumicTools.Shovel shovel?shovel.onBlockStartBreak(stack,pos,player)
            :item instanceof ThaumicTools.Crusher crusher&&crusher.onBlockStartBreak(stack,pos,player);
        if(handled)event.setCanceled(true);
    }

    public static boolean fellFurthestLog(ItemStack stack, BlockPos origin, Player player) {
        if (!(player instanceof ServerPlayer server) || player.isShiftKeyDown() || BREAKING.contains(player.getUUID())) return false;
        var level=server.serverLevel();var block=level.getBlockState(origin).getBlock();
        if(!level.getBlockState(origin).is(BlockTags.LOGS))return false;
        BlockPos furthest=furthestLog(level,origin,block);
        BREAKING.add(player.getUUID());
        try{
            boolean harvested=server.gameMode.destroyBlock(furthest);
            if(harvested){
                thaumcraft.entities.FollowingItem.followFreshDrops(level,furthest,player,10);
                level.playSound(null,origin,thaumcraft.registry.R2Sounds.BUBBLE.get(),net.minecraft.sounds.SoundSource.BLOCKS,.15f,1);
                scheduleLeaves(level,furthest);
            }
            return harvested;
        }finally{BREAKING.remove(player.getUUID());}
    }

    private record LeafDecay(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,BlockPos pos,long due){}
    public static void scheduleLeaves(net.minecraft.server.level.ServerLevel level,BlockPos felled){for(var pos:BlockPos.betweenClosed(felled.offset(-3,-3,-3),felled.offset(3,3,3)))if(level.hasChunkAt(pos)&&level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.LeavesBlock)leafDecay.add(new LeafDecay(level.dimension(),pos.immutable(),level.getGameTime()+150+level.random.nextInt(150)));}
    private static final List<LeafDecay> leafDecay=new ArrayList<>();
    @net.neoforged.bus.api.SubscribeEvent public static void decayLeaves(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
        var iterator=leafDecay.iterator();while(iterator.hasNext()){var task=iterator.next();var level=event.getServer().getLevel(task.dimension());if(level==null){iterator.remove();continue;}if(level.getGameTime()<task.due())continue;iterator.remove();if(level.hasChunkAt(task.pos())){var state=level.getBlockState(task.pos());if(state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock)state.randomTick(level,task.pos(),level.random);}}
    }
    @net.neoforged.bus.api.SubscribeEvent public static void clearTasks(net.neoforged.neoforge.event.server.ServerStoppedEvent event){leafDecay.clear();BREAKING.clear();SWEEPING.clear();}

    /** Iterative equivalent of TC4 BlockUtils.findBlocks, including its one-block gap. */
    public static BlockPos furthestLog(net.minecraft.server.level.ServerLevel level,BlockPos origin,net.minecraft.world.level.block.Block block){
        BlockPos current=origin.immutable();double distance=0;
        search:while(true){
            for(int x=-2;x<=2;x++)for(int y=2;y>=-2;y--)for(int z=-2;z<=2;z++){
                var pos=current.offset(x,y,z);if(Math.abs(pos.getX()-origin.getX())>24||Math.abs(pos.getY()-origin.getY())>48||Math.abs(pos.getZ()-origin.getZ())>24)return current;
                if(!level.hasChunkAt(pos))continue;var state=level.getBlockState(pos);
                boolean same=state.is(block)||isSilverwood(block)&&isSilverwood(state.getBlock());
                if(!same||!state.is(BlockTags.LOGS)||state.getDestroySpeed(level,pos)<0)continue;
                double next=pos.distSqr(origin);if(next>distance){current=pos;distance=next;continue search;}
            }
            return current;
        }
    }
    private static boolean isSilverwood(net.minecraft.world.level.block.Block block){return block==thaumcraft.registry.R3Content.SILVERWOOD_LOG.get()||block==thaumcraft.registry.R3Content.SILVERWOOD_NODE_LOG.get();}

    public static boolean minePlane(ItemStack stack, BlockPos origin, Player player, boolean crusher) {
        if(!(player instanceof ServerPlayer server)||player.isShiftKeyDown()||BREAKING.contains(player.getUUID()))return false;
        var hit=player.pick(8,0,false);Direction face=hit instanceof BlockHitResult blockHit?blockHit.getDirection():Direction.UP;
        var tag=crusher?BlockTags.MINEABLE_WITH_PICKAXE:BlockTags.MINEABLE_WITH_SHOVEL;
        if(!server.level().getBlockState(origin).is(tag)&&!(crusher&&server.level().getBlockState(origin).is(BlockTags.MINEABLE_WITH_SHOVEL)))return false;
        BREAKING.add(player.getUUID());boolean mined=false;
        try{
            for(int a=-1;a<=1;a++)for(int b=-1;b<=1;b++){
                var pos=plane(origin,face,a,b);if(!server.level().hasChunkAt(pos)||!server.level().mayInteract(player,pos))continue;
                var state=server.level().getBlockState(pos);if(state.getDestroySpeed(server.level(),pos)<0||!state.is(tag)&&!(crusher&&state.is(BlockTags.MINEABLE_WITH_SHOVEL)))continue;
                if(stack.isEmpty())return mined;boolean harvested=server.gameMode.destroyBlock(pos);mined|=harvested;
                if(harvested)thaumcraft.entities.FollowingItem.followFreshDrops(server.serverLevel(),pos,player,3);
            }
            return mined;
        }finally{BREAKING.remove(player.getUUID());}
    }

    private static BlockPos plane(BlockPos origin,Direction face,int a,int b){return switch(face.getAxis()){
        case Y->origin.offset(a,0,b);case Z->origin.offset(a,b,0);case X->origin.offset(0,b,a);
    };}

    public static InteractionResult placePlane(UseOnContext context){
        var player=context.getPlayer();var level=context.getLevel();var source=context.getClickedPos();var face=context.getClickedFace();
        if(player==null||level.getBlockEntity(source)!=null)return InteractionResult.PASS;
        var state=level.getBlockState(source);var block=state.getBlock();var stack=context.getItemInHand();
        // Original ItemElementalShovel places the plane while sneaking too; only mining is suppressed.
        if(level.isClientSide)return InteractionResult.SUCCESS;
        boolean placed=false;
        for(var pos:placementPositions(source,face,player.getDirection(),stack)){
            if(!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos)||!level.mayInteract(player,pos)||!level.getBlockState(pos).canBeReplaced())continue;
            int inventorySlot=-1;var placedState=state;
            if(!player.isCreative()){
                for(int i=0;i<player.getInventory().items.size();i++)if(player.getInventory().getItem(i).is(block.asItem())){inventorySlot=i;break;}
                if(inventorySlot<0&&state.is(Blocks.GRASS_BLOCK))for(int i=0;i<player.getInventory().items.size();i++)if(player.getInventory().getItem(i).is(Items.DIRT)){inventorySlot=i;placedState=Blocks.DIRT.defaultBlockState();break;}
                if(inventorySlot<0)continue;
            }
            var snapshot=net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(),level,pos);
            if(level.setBlockAndUpdate(pos,placedState)){
                if(net.neoforged.neoforge.event.EventHooks.onBlockPlace(player,snapshot,face)){snapshot.restore(3);continue;}
                if(!player.isCreative())player.getInventory().getItem(inventorySlot).shrink(1);
                level.playSound(null,pos,placedState.getSoundType().getPlaceSound(),net.minecraft.sounds.SoundSource.BLOCKS,.25f,.8f);
                stack.hurtAndBreak(1,player,LivingEntity.getSlotForHand(context.getHand()));placed=true;if(stack.isEmpty())break;
            }
        }
        return placed?InteractionResult.CONSUME:InteractionResult.PASS;
    }
    public static List<BlockPos> placementPositions(BlockPos source,Direction face,Direction facing,ItemStack shovel){
        int orientation=shovel.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getByte("or");var result=new ArrayList<BlockPos>();
        Direction normal=orientation==1?(face.getAxis()==Direction.Axis.Y?facing:face.getClockWise()):orientation==2?(face.getAxis()==Direction.Axis.Y?facing:Direction.UP):face;
        for(int a=-1;a<=1;a++)for(int b=-1;b<=1;b++)result.add(plane(source,normal,a,b).relative(face));return List.copyOf(result);
    }

    public static void sweep(Player player,Entity original){
        if(player.level().isClientSide||SWEEPING.contains(player.getUUID())||player.getAttackStrengthScale(0)<.9)return;
        SWEEPING.add(player.getUUID());
        var ticker=(thaumcraft.mixin.LivingAttackTickerAccessor)player;int strength=ticker.thaumcraft$getAttackTicker();
        try{
            for(var target:player.level().getEntitiesOfClass(Mob.class,original.getBoundingBox().inflate(1.2,1.1,1.2))){
                if(target==original||!target.isAlive()||target instanceof thaumcraft.entities.ThaumicGolem golem&&player.getUUID().equals(golem.owner())||target instanceof TamableAnimal tame&&tame.isOwnedBy(player))continue;
                ticker.thaumcraft$setAttackTicker(strength);player.attack(target);
            }
        }finally{ticker.thaumcraft$setAttackTicker(strength);SWEEPING.remove(player.getUUID());}
    }

    public static void scanOres(ServerPlayer player,BlockPos origin){
        PacketDistributor.sendToPlayer(player,oreScan(player.serverLevel(),origin));
    }

    public static OreScanPayload oreScan(net.minecraft.server.level.ServerLevel level,BlockPos origin){
        var markers=new ArrayList<OreScanPayload.Marker>();
        for(var pos:BlockPos.betweenClosed(origin.offset(-8,-8,-8),origin.offset(8,8,8))){
            if(!level.hasChunkAt(pos))continue;var state=level.getBlockState(pos);var fluid=state.getFluidState();
            if(fluid.is(net.minecraft.tags.FluidTags.WATER))markers.add(new OreScanPayload.Marker(pos,-5));
            else if(fluid.is(net.minecraft.tags.FluidTags.LAVA))markers.add(new OreScanPayload.Marker(pos,-10));
            else if(state.is(Tags.Blocks.ORES)){
                int value=thaumcraft.data.ThaumcraftData.aspects(level.getServer(),new ItemStack(state.getBlock().asItem())).values().stream().mapToInt(Integer::intValue).sum();
                markers.add(new OreScanPayload.Marker(pos,value));
            }
        }
        return new OreScanPayload(origin,markers);
    }

    private ToolActions(){}
}
