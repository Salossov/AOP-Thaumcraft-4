package thaumcraft.entities;

import java.util.*;
import java.util.function.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.*;
import thaumcraft.api.aspects.*;
import thaumcraft.production.*;

/** TC4 jobs share navigation, but retain distinct input, filter and output rules. */
public final class GolemWorkGoal extends Goal {
    private final ThaumicGolem golem;
    private Vec3 target;
    // Passive diagnostics: populated by the real AI, never a second job/path search.
    private Vec3 workTarget, lastMotion;
    private String debugTask="search", debugReason="search", navigationReason="search";
    private int motionTick,jobRevision;
    private boolean returningHome;
    public record DebugPlan(Vec3 target,String task,String reason,String waitingReason){}
    public DebugPlan debugPlan(){
        String reason=golem.debugInactiveReason();
        if(reason.isEmpty())reason=job!=null&&target!=null?(golem.distanceToSqr(target)<=4?"working":navigationReason):debugReason;
        if(job!=null&&target!=null&&golem.distanceToSqr(target)>4&&golem.tickCount-motionTick>40&&reason.equals("moving"))reason="stuck";
        if(job==null&&returningHome&&golem.debugInactiveReason().isEmpty())reason="moving";
        return new DebugPlan(job==null?(returningHome?golem.home().getCenter():null):(workTarget==null?target:workTarget),job==null&&returningHome?"return_home":debugTask,reason,debugReason);
    }
    private String plannedTask(){return switch(golem.core()){
        case 0->golem.carried().isEmpty()?"take_items":"deposit_items";
        case 1,10->golem.carried().isEmpty()?"take_items":"deliver_items";
        case 2->golem.carried().isEmpty()?"pickup":"deposit_items";
        case 3->"harvest";case 4,9->"attack";
        case 5->golem.fluid.isEmpty()?"take_fluid":"deliver_fluid";
        case 6->golem.essentiaAmount<=0?"take_essentia":"deliver_essentia";
        case 7->"chop";case 8->"use";case 11->"fish";default->"search";};}
    private String idleReason(){return switch(golem.core()){
        case 0->"no_fill_job";case 1,10->"no_transfer_job";case 2->"no_pickup_job";
        case 3->"no_crop";case 4,9->"no_enemy";case 5->"no_fluid_job";
        case 6->"source_empty";case 7->"no_log";case 8->"no_use_job";case 11->"no_water";default->"no_core";};}
    private ItemStack watched=ItemStack.EMPTY;private int watchedRevision;
    private Runnable job;private BooleanSupplier valid;
    private int timeout,delay,searchAfter,miningTime,miningMax;
    private BlockPos mining;
    private ItemEntity pickup;
    private LivingEntity combat;private int dartCooldown,dartTarget=-1;
    private BlockPos fishing;private int fishingTicks;private GolemBobber bobber;private float fishingQuality;
    private BlockPos knownFishingSpot,fishingSearchHome;
    private int fishingSearchRadius,nextWaterSearch;
    private int useMarkerCursor;
    private BlockPos harvestAnchor;
    private final ArrayDeque<BlockPos> cropChecklist=new ArrayDeque<>();
    private BlockPos cropChecklistHome;private int cropChecklistRange;private boolean checklistLogs;
    private void navigate(){
        if(target==null)return;navigationReason="no_path";
        if(combat!=null||pickup!=null){navigationReason=golem.getNavigation().moveTo(target.x,target.y,target.z,1)?"moving":"no_path";return;}
        var path=approachPath(target);
        if(path!=null)navigationReason=golem.getNavigation().moveTo(path,1)?(path.canReach()?"moving":"partial_path"):"no_path";
    }
    private net.minecraft.world.level.pathfinder.Path approachPath(Vec3 destination){
        // Modern navigation cannot finish a path inside the inventory/log/dirt block itself.
        // Choose a standable neighbouring node within TC4's two-block interaction distance.
        var candidates=new HashSet<BlockPos>();var centre=BlockPos.containing(destination);
        for(var pos:BlockPos.betweenClosed(centre.offset(-2,-1,-2),centre.offset(2,1,2))){
            if(!world().hasChunkAt(pos)||new Vec3(pos.getX()+.5,pos.getY(),pos.getZ()+.5).distanceToSqr(destination)>4)continue;
            if(!world().getBlockState(pos).getCollisionShape(world(),pos).isEmpty()||!world().getBlockState(pos.above()).getCollisionShape(world(),pos.above()).isEmpty())continue;
            if(world().getBlockState(pos.below()).getCollisionShape(world(),pos.below()).isEmpty())continue;
            candidates.add(pos.immutable());
        }
        if(candidates.isEmpty()){navigationReason="no_stand";return null;}
        return golem.getNavigation().createPath(candidates,0);
    }
    public GolemWorkGoal(ThaumicGolem golem){this.golem=golem;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    private ServerLevel world(){return (ServerLevel)golem.level();}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public boolean canUse(){
        if(golem.level().isClientSide||golem.inactive()||golem.tickCount<searchAfter)return false;
        searchAfter=golem.tickCount+2;target=null;workTarget=null;job=null;valid=()->true;delay=0;debugTask=plannedTask();debugReason=idleReason();
        return switch(golem.core()){
            case 0->fill();case 1,10->empty();case 2->gather();case 3->harvest(false);case 4,9->guard();case 5->liquid();case 6->alchemy();case 7->harvest(true);case 8->use();case 11->fish();default->false;
        };
    }
    @Override public void start(){jobRevision=golem.workRevision();timeout=200;lastMotion=golem.position();motionTick=golem.tickCount;if(fishing!=null){fishingTicks=300+world().random.nextInt(200);golem.action(10);}navigate();}
    @Override public boolean canContinueToUse(){return !golem.inactive()&&jobRevision==golem.workRevision()&&target!=null&&job!=null&&timeout>0&&valid.getAsBoolean();}
    @Override public void stop(){if(mining!=null)world().destroyBlockProgress(golem.getId(),mining,-1);mining=null;pickup=null;combat=null;dartTarget=-1;dartCooldown=0;if(bobber!=null){bobber.discard();bobber=null;}fishing=null;golem.fishingTarget(null);target=null;job=null;golem.getNavigation().stop();}
    @Override public void tick(){
        // GoalSelector can tick a running goal again before its next continuation check.
        // An action is consumed once; wait for the selector to stop and choose the next job.
        if(job==null||target==null)return;
        if(jobRevision!=golem.workRevision()||!valid.getAsBoolean()){debugReason="invalid_target";job=null;timeout=0;return;}
        if(lastMotion==null||lastMotion.distanceToSqr(golem.position())>.04){lastMotion=golem.position();motionTick=golem.tickCount;}if(--timeout<=0)debugReason="timeout";if(combat!=null)target=combat.position();if(pickup!=null)target=pickup.position();if(target==null)return;
        if(combat!=null)target=combat.position();golem.getLookControl().setLookAt(target.x,target.y,target.z,30,30);
        if(combat!=null&&golem.decoration().contains("R")&&golem.distanceToSqr(combat)>=9){
            golem.getNavigation().stop();var action=job;job=null;action.run();return;
        }
        if(golem.distanceToSqr(target)>(combat!=null&&golem.decoration().contains("R")&&golem.hasLineOfSight(combat)?256:4)){if(golem.tickCount%10==0)navigate();return;}
        golem.getNavigation().stop();if(delay>0){delay--;if(mining!=null&&delay>0&&delay%Math.max(1,miningMax/Math.max(1,Math.round(miningMax/6f)))==0){golem.action(10);world().destroyBlockProgress(golem.getId(),mining,(int)(9*(1-delay/(float)miningMax)));}return;}
        var action=job;job=null;action.run();
    }
    private boolean assign(BlockPos pos,Runnable action,BooleanSupplier valid){
        if(!world().hasChunkAt(pos)||golem.home().distSqr(pos)>golem.range()*golem.range())return false;
        var endpoint=world().getBlockEntity(pos);var home=golem.source();var source=world().hasChunkAt(home)?world().getBlockEntity(home):null;
        target=pos.getCenter();workTarget=target;job=action;
        this.valid=()->world().hasChunkAt(pos)&&world().getBlockEntity(pos)==endpoint&&world().hasChunkAt(home)&&world().getBlockEntity(home)==source&&valid.getAsBoolean();return true;
    }

    private List<GolemMarker> marked(){return golem.markers().stream().filter(m->m.dimension().equals(world().dimension())&&world().hasChunkAt(m.pos())&&golem.home().distSqr(m.pos())<=golem.range()*golem.range()).sorted(Comparator.comparingDouble(m->golem.distanceToSqr(m.pos().getCenter()))).toList();}
    private IItemHandler items(BlockPos pos,Direction face){return world().hasChunkAt(pos)?world().getCapability(Capabilities.ItemHandler.BLOCK,pos,face):null;}
    private IItemHandler homeItems(){return items(golem.source(),golem.homeFace());}
    boolean matches(ItemStack stack,ItemStack filter){
        if(stack.isEmpty()||filter.isEmpty())return false;
        boolean same=ItemStack.isSameItem(stack,filter);
        if(!same&&golem.toggle(5)){var shared=stack.getTags().filter(tag->tag.location().getNamespace().equals("c")).toList();same=filter.getTags().anyMatch(shared::contains);}
        if(!same)return false;
        if(!golem.toggle(6)&&stack.getDamageValue()!=filter.getDamageValue())return false;
        if(golem.toggle(7))return true;
        ItemStack first=stack.copy(),second=filter.copy();if(golem.toggle(6)){first.remove(DataComponents.DAMAGE);second.remove(DataComponents.DAMAGE);}return first.getComponents().equals(second.getComponents());
    }
    private boolean filtered(ItemStack stack){boolean any=false;for(int slot=0;slot<golem.filterSlots();slot++){var filter=golem.filters.getItem(slot);if(filter.isEmpty())continue;any=true;if(matches(stack,filter))return true;}return !any;}
    private boolean markerMatches(GolemMarker marker,ItemStack stack){if(marker.color()<0)return true;for(int slot=0;slot<golem.filterSlots();slot++)if(golem.color(slot)==marker.color()&&matches(stack,golem.filters.getItem(slot)))return true;return false;}
    private static ItemStack insert(IItemHandler handler,ItemStack stack,boolean simulate){return handler==null?stack:ItemHandlerHelper.insertItemStacked(handler,stack,simulate);}
    private boolean sortingAllows(IItemHandler handler,ItemStack stack){if(golem.core()!=10)return true;if(handler==null)return false;for(int slot=0;slot<handler.getSlots();slot++)if(matches(stack,handler.getStackInSlot(slot)))return true;return false;}
    private boolean depositHome(){var carried=golem.carried();if(carried.isEmpty())return false;var handler=homeItems();if(handler==null||insert(handler,carried,true).getCount()==carried.getCount())return false;debugTask="deposit_items";return assign(golem.source(),()->{golem.carried(insert(homeItems(),golem.carried(),false));golem.action(10);},()->!golem.carried().isEmpty()&&homeItems()!=null&&insert(homeItems(),golem.carried(),true).getCount()<golem.carried().getCount());}
    private boolean take(IItemHandler handler,BlockPos pos,Direction face,Predicate<ItemStack> allow){if(handler==null)return false;for(int slot=0;slot<handler.getSlots();slot++){var stack=handler.getStackInSlot(slot);if(!stack.isEmpty()&&allow.test(stack)&&!handler.extractItem(slot,takeCount(stack),true).isEmpty()){int index=slot;watchFillItem(stack);debugTask="take_items";return assign(pos,()->{var actual=items(pos,face);if(actual!=null&&golem.carried().isEmpty()){var preview=actual.extractItem(index,takeCount(actual.getStackInSlot(index)),true);if(allow.test(preview)){golem.carried(actual.extractItem(index,takeCount(preview),false));golem.action(10);}}},()->{var current=items(pos,face);if(current==null||index>=current.getSlots()||!golem.carried().isEmpty())return false;var preview=current.extractItem(index,takeCount(current.getStackInSlot(index)),true);return !preview.isEmpty()&&allow.test(preview);});}}return false;}
    private int takeCount(ItemStack stack){if(golem.core()!=0||golem.toggle(0)||golem.filters.isEmpty())return golem.carryLimit();var home=homeItems();if(home==null)return 0;int missing=0;for(int i=0;i<golem.filterSlots();i++){var filter=golem.filters.getItem(i);if(!matches(stack,filter))continue;int count=0;for(int slot=0;slot<home.getSlots();slot++)if(matches(home.getStackInSlot(slot),filter))count+=home.getStackInSlot(slot).getCount();missing=Math.max(missing,filter.getCount()-count);}return Math.clamp(missing,0,golem.carryLimit());}
    /** The real Fill request selected by native planning, or its last missing filter. */
    List<ItemStack> externalItemDemand(){
        var missing=missingItemRequests();
        if(watchedRevision==golem.workRevision()&&!watched.isEmpty()){
            for(var request:missing)if(ItemStack.isSameItemSameComponents(request,watched))return List.of(request.copy());
        }
        return missing;
    }
    private List<ItemStack> missingItemRequests(){
        if(golem.core()!=0||!golem.carried().isEmpty())return List.of();
        var result=new ArrayList<ItemStack>();var home=homeItems();
        for(int slot=0;slot<golem.filterSlots();slot++){
            var filter=golem.filters.getItem(slot);if(filter.isEmpty())continue;
            if(golem.toggle(0)){result.add(filter.copy());continue;}
            if(home==null)continue;
            long count=0;for(int i=0;i<home.getSlots();i++)if(matches(home.getStackInSlot(i),filter))count+=home.getStackInSlot(i).getCount();
            long required=0;
            for(int i=0;i<golem.filterSlots();i++){
                var other=golem.filters.getItem(i);if(other.isEmpty())continue;
                boolean same=golem.upgrade(5)>0?ItemStack.isSameItem(other,filter)&&other.getDamageValue()==filter.getDamageValue():ItemStack.isSameItemSameComponents(other,filter);
                if(!same&&golem.upgrade(5)>0){var tags=other.getTags().filter(tag->tag.location().getNamespace().equals("c")).toList();same=filter.getTags().anyMatch(tags::contains);}
                if(same)required+=other.getCount();
            }
            if(count>=required)continue;
            int amount=(int)Math.max(0,filter.getCount()-count);
            // Wireless rates, not the material's physical carry limit, bound the network extraction.
            if(amount>0)result.add(filter.copyWithCount(amount));
        }
        return List.copyOf(result);
    }
    private void watchFillItem(ItemStack stack){
        if(golem.core()!=0)return;
        for(var request:missingItemRequests())if(matches(stack,request)){
            watched=request.copy();watchedRevision=golem.workRevision();return;
        }
    }
    /** Modern equivalent of GolemHelper.getMissingLiquids: fillable, filtered fluid types. */
    List<FluidStack> externalFluidDemand(){
        if(golem.core()!=5||!world().hasChunkAt(golem.source()))return List.of();
        var home=world().getCapability(Capabilities.FluidHandler.BLOCK,golem.source(),golem.homeFace());
        if(home==null)return List.of();
        var result=new ArrayList<FluidStack>();var candidates=new ArrayList<FluidStack>();
        boolean hasFilter=false;
        for(int slot=0;slot<golem.filterSlots();slot++){
            var filter=golem.filters.getItem(slot);if(filter.isEmpty())continue;hasFilter=true;
            var tank=filter.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);if(tank==null)continue;
            for(int i=0;i<tank.getTanks();i++){var value=tank.getFluidInTank(i);if(!value.isEmpty())candidates.add(value.copyWithAmount(1000));}
        }
        if(!hasFilter)for(var type:net.minecraft.core.registries.BuiltInRegistries.FLUID){
            if(type.defaultFluidState().isSource())candidates.add(new FluidStack(type,1000));
        }
        for(var candidate:candidates){
            if(!golem.fluid.isEmpty()&&!FluidStack.isSameFluidSameComponents(golem.fluid,candidate))continue;
            if(result.stream().anyMatch(value->FluidStack.isSameFluidSameComponents(value,candidate)))continue;
            if(home.fill(candidate,IFluidHandler.FluidAction.SIMULATE)>0)result.add(candidate.copy());
        }
        return List.copyOf(result);
    }
    private boolean fill(){if(!golem.carried().isEmpty())return depositHome();watched=ItemStack.EMPTY;watchedRevision=golem.workRevision();var home=homeItems();if(home==null)return returnHome();Predicate<ItemStack> needed=stack->{if(!filtered(stack))return false;for(int slot=0;slot<golem.filterSlots();slot++){var filter=golem.filters.getItem(slot);if(!matches(stack,filter))continue;int count=0;for(int i=0;i<home.getSlots();i++)if(matches(home.getStackInSlot(i),filter))count+=home.getStackInSlot(i).getCount();if(count<filter.getCount())return true;}return golem.filters.isEmpty()&&insert(home,stack.copyWithCount(1),true).isEmpty();};for(var marker:marked())if(take(items(marker.pos(),marker.face()),marker.pos(),marker.face(),stack->needed.test(stack)&&markerMatches(marker,stack)))return true;return returnHome();}
    private boolean empty(){
        if(!golem.carried().isEmpty()){for(var marker:marked()){var carried=golem.carried();if(!markerMatches(marker,carried))continue;var handler=items(marker.pos(),marker.face());if(handler!=null&&sortingAllows(handler,carried)&&insert(handler,carried,true).getCount()<carried.getCount())return assign(marker.pos(),()->{golem.carried(insert(items(marker.pos(),marker.face()),golem.carried(),false));golem.action(10);},()->{var actual=items(marker.pos(),marker.face());var carriedNow=golem.carried();return !carriedNow.isEmpty()&&markerMatches(marker,carriedNow)&&sortingAllows(actual,carriedNow)&&insert(actual,carriedNow,true).getCount()<carriedNow.getCount();});if(handler==null&&world().isEmptyBlock(marker.pos().relative(marker.face())))return assign(marker.pos(),()->drop(marker),()->world().isEmptyBlock(marker.pos().relative(marker.face())));}return depositHome();}
        var handler=homeItems();return take(handler,golem.source(),golem.homeFace(),stack->{if(golem.core()!=10&&!filtered(stack))return false;for(var marker:marked()){if(!markerMatches(marker,stack))continue;var destination=items(marker.pos(),marker.face());if(destination!=null&&sortingAllows(destination,stack)&&insert(destination,stack.copyWithCount(1),true).isEmpty())return true;if(golem.core()!=10&&destination==null&&world().isEmptyBlock(marker.pos().relative(marker.face())))return true;}return false;})||returnHome();
    }
    private void drop(GolemMarker marker){var pos=marker.pos().getCenter().add(Vec3.atLowerCornerOf(marker.face().getNormal()).scale(.6));var entity=new ItemEntity(world(),pos.x,pos.y,pos.z,golem.carried().copy());entity.setDefaultPickUpDelay();if(world().addFreshEntity(entity))golem.carried(ItemStack.EMPTY);}
    private boolean gather(){if(!golem.carried().isEmpty())return depositHome();if(homeItems()==null&&!golem.externalItemDestination())return returnHome();
        // Original AIItemPickup: take the item when the golem's filter is empty OR the filter still wants it.
        var entities=world().getEntitiesOfClass(ItemEntity.class,new AABB(golem.home()).inflate(golem.range()),entity->!entity.hasPickUpDelay()&&filtered(entity.getItem()));entities.sort(Comparator.comparingDouble(golem::distanceToSqr));if(!entities.isEmpty()){pickup=entities.getFirst();target=pickup.position();job=()->{if(pickup!=null&&pickup.isAlive()&&!pickup.hasPickUpDelay()&&golem.carried().isEmpty()){var stack=pickup.getItem().copy();int amount=Math.min(golem.carryLimit(),stack.getCount());golem.carried(stack.copyWithCount(amount));stack.shrink(amount);if(stack.isEmpty())pickup.discard();else pickup.setItem(stack);golem.action(10);}};valid=()->pickup!=null&&pickup.isAlive()&&!pickup.hasPickUpDelay()&&filtered(pickup.getItem())&&golem.carried().isEmpty()&&(homeItems()!=null||golem.externalItemDestination());return true;}return returnHome();}
    private boolean acceptsEssentia(GolemMarker marker){
        if(golem.essentia==null||golem.essentiaAmount<=0||!world().hasChunkAt(marker.pos()))return false;
        var transport=world().getCapability(EssentiaCapabilities.TRANSPORT,marker.pos(),marker.face());var container=world().getCapability(EssentiaCapabilities.CONTAINER,marker.pos(),null);
        return transport!=null&&transport.canInputFrom(marker.face())&&transport.getSuctionAmount(marker.face())>0&&(transport.getSuctionType(marker.face())==null||transport.getSuctionType(marker.face())==golem.essentia)&&(container==null||container.doesContainerAccept(golem.essentia));
    }
    private boolean alchemy(){
        if(golem.essentiaAmount>0&&golem.essentia!=null){
            // Original findJarWithRoom: marked transports/reservoirs (non-jar containers) win over jars outright.
            for(var marker:marked()){if(world().getBlockEntity(marker.pos()) instanceof TransportBlockEntity jar&&(jar.kind==ProductionBlock.Kind.JAR||jar.kind==ProductionBlock.Kind.VOID_JAR))continue;var transport=world().getCapability(EssentiaCapabilities.TRANSPORT,marker.pos(),marker.face());if(acceptsEssentia(marker))return assign(marker.pos(),()->{var actual=world().getCapability(EssentiaCapabilities.TRANSPORT,marker.pos(),marker.face());if(actual!=null){golem.essentiaAmount-=actual.addEssentia(golem.essentia,golem.essentiaAmount,marker.face());if(golem.essentiaAmount<=0)golem.essentia=null;golem.action(10);}},()->acceptsEssentia(marker));}
            // TC4 only puts fillable jars in this list. An alembic exposes a container
            // for extraction/phials, but cannot receive a golem delivery.
            var candidates=new ArrayList<GolemMarker>();var seen=new HashSet<BlockPos>();var queue=new ArrayDeque<GolemMarker>(marked());while(!queue.isEmpty()&&seen.size()<4096){var marker=queue.removeFirst();if(!seen.add(marker.pos())||!world().hasChunkAt(marker.pos()))continue;if(!(world().getBlockEntity(marker.pos()) instanceof TransportBlockEntity jar&&(jar.kind==ProductionBlock.Kind.JAR||jar.kind==ProductionBlock.Kind.VOID_JAR)))continue;var container=world().getCapability(EssentiaCapabilities.CONTAINER,marker.pos(),null);if(container!=null&&container.doesContainerAccept(golem.essentia))candidates.add(marker);if(world().getBlockEntity(marker.pos()) instanceof TransportBlockEntity tile&&(tile.kind==ProductionBlock.Kind.JAR||tile.kind==ProductionBlock.Kind.VOID_JAR))for(var face:Direction.values()){var adjacent=marker.pos().relative(face);if(world().hasChunkAt(adjacent)&&golem.home().distSqr(adjacent)<=golem.range()*golem.range()&&world().getBlockEntity(adjacent) instanceof TransportBlockEntity other&&(other.kind==ProductionBlock.Kind.JAR||other.kind==ProductionBlock.Kind.VOID_JAR))queue.add(new GolemMarker(world().dimension(),adjacent,Direction.UP,-1));}}
            candidates.sort(Comparator.<GolemMarker>comparingInt(this::jarPriority).thenComparingDouble(marker->golem.home().distSqr(marker.pos())));
            for(var marker:candidates){var container=world().getCapability(EssentiaCapabilities.CONTAINER,marker.pos(),null);if(container!=null&&container.doesContainerAccept(golem.essentia))return assign(marker.pos(),()->{var target=world().getCapability(EssentiaCapabilities.CONTAINER,marker.pos(),null);if(target!=null){golem.essentiaAmount=target.addToContainer(golem.essentia,golem.essentiaAmount);if(golem.essentiaAmount<=0)golem.essentia=null;golem.action(10);}},()->golem.essentia!=null&&golem.essentiaAmount>0&&world().getCapability(EssentiaCapabilities.CONTAINER,marker.pos(),null)!=null&&world().getCapability(EssentiaCapabilities.CONTAINER,marker.pos(),null).doesContainerAccept(golem.essentia));}
            debugReason=marked().isEmpty()?"no_markers":"no_essentia_destination";return returnHome();
        }
        // Original AIEssentiaGather: pick the source side holding the most essentia, top up to carryLimit.
        boolean sourceFound=false;BlockPos base=golem.source(),source=base;Direction face=golem.homeFace();int most=-1;for(int height=0;height<=5;height++){var pos=base.above(height);if(!world().hasChunkAt(pos))continue;var transport=world().getCapability(EssentiaCapabilities.TRANSPORT,pos,null);if(transport==null)continue;sourceFound=true;Direction side=face;if(world().getBlockEntity(pos) instanceof TransportBlockEntity tile)side=tile.kind==ProductionBlock.Kind.JAR||tile.kind==ProductionBlock.Kind.VOID_JAR||tile.kind==ProductionBlock.Kind.ALEMBIC?Direction.UP:tile.getBlockState().getValue(ProductionBlock.FACING);if(transport.canOutputTo(side)&&transport.getEssentiaAmount(side)>most&&transport.getEssentiaType(side)!=null){most=transport.getEssentiaAmount(side);source=pos;face=side;}}
        if(most<=0){debugReason=sourceFound?"source_empty":"no_source";return returnHome();}var pos=source;var side=face;int have=golem.essentiaAmount;BlockPos approach=golem.source();
        // The selected alembic can sit above the furnace; walk to the reachable base of its column.
        if(world().getBlockEntity(approach) instanceof TransportBlockEntity start&&start.kind==ProductionBlock.Kind.ALEMBIC)for(int depth=1;depth<=5;depth++){var below=approach.below(depth);if(!world().hasChunkAt(below))break;if(world().getBlockEntity(below) instanceof AlchemyFurnaceBlockEntity){approach=below;break;}if(!(world().getBlockEntity(below) instanceof TransportBlockEntity cube&&cube.kind==ProductionBlock.Kind.ALEMBIC))break;}
        boolean assigned=assign(approach,()->{var transport=world().getCapability(EssentiaCapabilities.TRANSPORT,pos,side);if(transport!=null&&transport.canOutputTo(side)){var aspect=transport.getEssentiaType(side);int amount=aspect==null?0:transport.takeEssentia(aspect,Math.min(golem.carryLimit()-have,transport.getEssentiaAmount(side)),side);if(amount>0){golem.essentia=aspect;golem.essentiaAmount=have+amount;golem.action(10);searchAfter=golem.tickCount+2;}}},()->{var current=world().getCapability(EssentiaCapabilities.TRANSPORT,pos,side);return current!=null&&current.canOutputTo(side)&&current.getEssentiaType(side)!=null&&current.getEssentiaAmount(side)>0&&golem.essentiaAmount==have;});if(assigned)workTarget=pos.getCenter();return assigned;
    }
    private int jarPriority(GolemMarker marker){if(!(world().getBlockEntity(marker.pos()) instanceof TransportBlockEntity tile))return 0;boolean labelled=tile.store.filter()!=null,empty=tile.store.amount()==0,voidJar=tile.kind==ProductionBlock.Kind.VOID_JAR;return labelled?(empty?1:voidJar&&tile.store.amount()>=64?2:0):(voidJar?empty?6:5:empty?4:3);}
    private boolean acceptsFluid(FluidStack fluid){if(fluid.isEmpty())return false;boolean filtered=false;for(int i=0;i<golem.filterSlots();i++){var stack=golem.filters.getItem(i);if(stack.isEmpty())continue;var capability=stack.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);if(capability==null)continue;for(int tank=0;tank<capability.getTanks();tank++){var allowed=capability.getFluidInTank(tank);if(allowed.isEmpty())continue;filtered=true;if(FluidStack.isSameFluidSameComponents(fluid,allowed))return true;}}return !filtered;}
    private FluidStack liquidPreview(IFluidHandler handler){if(handler==null)return FluidStack.EMPTY;for(int tank=0;tank<handler.getTanks();tank++){var fluid=handler.getFluidInTank(tank);if(acceptsFluid(fluid)){var drained=handler.drain(fluid.copyWithAmount(golem.fluidLimit()),IFluidHandler.FluidAction.SIMULATE);if(!drained.isEmpty())return drained;}}return FluidStack.EMPTY;}
    private boolean liquid(){
        if(!golem.fluid.isEmpty()){var target=world().getCapability(Capabilities.FluidHandler.BLOCK,golem.source(),golem.homeFace());if(target!=null&&target.fill(golem.fluid,IFluidHandler.FluidAction.SIMULATE)>0)return assign(golem.source(),()->{var actual=world().getCapability(Capabilities.FluidHandler.BLOCK,golem.source(),golem.homeFace());if(actual!=null){golem.fluid.shrink(actual.fill(golem.fluid,IFluidHandler.FluidAction.EXECUTE));golem.action(10);}},()->{var current=world().getCapability(Capabilities.FluidHandler.BLOCK,golem.source(),golem.homeFace());return !golem.fluid.isEmpty()&&current!=null&&current.fill(golem.fluid,IFluidHandler.FluidAction.SIMULATE)>0;});return returnHome();}
        for(var marker:marked()){var handler=world().getCapability(Capabilities.FluidHandler.BLOCK,marker.pos(),marker.face());if(handler!=null&&!liquidPreview(handler).isEmpty())return assign(marker.pos(),()->{var actual=world().getCapability(Capabilities.FluidHandler.BLOCK,marker.pos(),marker.face());if(actual!=null){var requested=liquidPreview(actual);if(!requested.isEmpty())golem.fluid=actual.drain(requested,IFluidHandler.FluidAction.EXECUTE);}},()->!liquidPreview(world().getCapability(Capabilities.FluidHandler.BLOCK,marker.pos(),marker.face())).isEmpty());var state=world().getFluidState(marker.pos());if(!state.isEmpty()&&state.isSource()&&state.getType().getBucket()!=Items.AIR&&state.getAmount()==8&&acceptsFluid(new FluidStack(state.getType(),1000))){return assign(marker.pos(),()->{var block=world().getBlockState(marker.pos()).getBlock();if(block instanceof BucketPickup bucket){var player=fake(ItemStack.EMPTY);if(!permitted(marker.pos(),player)||net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(world(),marker.pos(),world().getBlockState(marker.pos()),player)).isCanceled())return;var stack=bucket.pickupBlock(player,world(),marker.pos(),world().getBlockState(marker.pos()));if(!stack.isEmpty())golem.fluid=new FluidStack(state.getType(),1000);}},()->world().getFluidState(marker.pos()).isSource());}}return returnHome();
    }
    private FakePlayer fake(ItemStack held){var player=FakePlayerFactory.get(world(),new GameProfile(UUID.nameUUIDFromBytes("FakeThaumcraftGolem".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"FakeThaumcraftGolem"));player.getInventory().clearContent();player.moveTo(golem.getX(),golem.getY(),golem.getZ(),golem.getYRot(),golem.getXRot());player.setItemInHand(InteractionHand.MAIN_HAND,held.copy());player.setShiftKeyDown(golem.toggle(2));return player;}
    private boolean permitted(BlockPos pos,Player player){return world().getWorldBorder().isWithinBounds(pos)&&world().mayInteract(player,pos)&&player.mayBuild();}
    private boolean use(){
        if(golem.carried().isEmpty()&&homeItems()!=null)return take(homeItems(),golem.source(),golem.homeFace(),this::filtered)||returnHome();
        var useMarkers=marked();for(int attempt=0;attempt<useMarkers.size();attempt++){int index=Math.floorMod(useMarkerCursor+attempt,useMarkers.size());var marker=useMarkers.get(index);if(!markerMatches(marker,golem.carried())&&!golem.carried().isEmpty())continue;boolean air=world().isEmptyBlock(marker.pos());if(air!=golem.toggle(0)||!world().isEmptyBlock(marker.pos().relative(marker.face())))continue;var selectedState=world().getBlockState(marker.pos());if(!golem.toggle(1)&&golem.carried().getItem() instanceof HoeItem&&!(golem.carried().getItem() instanceof thaumcraft.items.ThaumicTools.Hoe)&&selectedState.is(Blocks.FARMLAND))continue;return assign(marker.pos(),()->{useMarkerCursor=index+1;var pos=world().isEmptyBlock(marker.pos())?marker.pos().relative(marker.face().getOpposite()):marker.pos();var player=fake(golem.carried());if(!permitted(pos,player))return;try{boolean worked;if(golem.toggle(1)){worked=player.gameMode.destroyBlock(pos);}else{var hit=new BlockHitResult(pos.getCenter(),marker.face(),pos,false);worked=player.gameMode.useItemOn(player,world(),player.getMainHandItem(),InteractionHand.MAIN_HAND,hit).consumesAction();}golem.carried(player.getMainHandItem());for(int i=1;i<player.getInventory().getContainerSize();i++){var stack=player.getInventory().getItem(i);if(!stack.isEmpty()){if(golem.carried().isEmpty())golem.carried(stack);else golem.spawnAtLocation(stack);}}if(worked)golem.action(10);}finally{player.getInventory().clearContent();}},()->world().getBlockState(marker.pos()).equals(selectedState));}return returnHome();
    }
    private static boolean mature(BlockState state,ServerLevel level,BlockPos pos){return state.getBlock() instanceof CropBlock crop&&crop.isMaxAge(state)||state.getBlock() instanceof NetherWartBlock&&state.getValue(NetherWartBlock.AGE)==3||state.getBlock() instanceof CocoaBlock&&state.getValue(CocoaBlock.AGE)>=2||state.getBlock() instanceof SugarCaneBlock&&level.getBlockState(pos.below()).is(state.getBlock())||state.getBlock() instanceof CactusBlock&&level.getBlockState(pos.below()).is(state.getBlock());}
    private boolean harvestCandidate(BlockPos pos,boolean logs,double range){
        if(!world().hasChunkAt(pos)||Math.abs(pos.getX()-golem.home().getX())>range||Math.abs(pos.getY()-golem.home().getY())>range||Math.abs(pos.getZ()-golem.home().getZ())>range)return false;
        var state=world().getBlockState(pos);if(!(logs?state.is(BlockTags.LOGS):mature(state,world(),pos)))return false;
        if(logs)while(world().getBlockState(pos.below()).is(BlockTags.LOGS)&&golem.home().distSqr(pos.below())<=range*range)pos=pos.below();
        var selected=pos;var original=world().getBlockState(pos);if(original.getDestroySpeed(world(),pos)<0)return false;
        if(!assign(pos,()->harvestBlock(selected,original,logs),()->world().getBlockState(selected).equals(original)))return false;
        mining=selected;miningMax=Math.max(logs?5:10,(int)((20-golem.strength()*(logs?3:2))*original.getDestroySpeed(world(),pos)));miningTime=miningMax;delay=miningTime;harvestAnchor=selected;return true;
    }
    private boolean harvest(boolean logs){
        int radius=(int)Math.ceil(golem.range()/(logs?3:4));
        if(harvestAnchor!=null){if(harvestCandidate(harvestAnchor,logs,radius))return true;for(int x=logs?-1:-2;x<=(logs?1:2);x++)for(int z=logs?-1:-2;z<=(logs?1:2);z++)for(int y=-1;y<=1;y++)if(harvestCandidate(harvestAnchor.offset(x,y,z),logs,radius))return true;harvestAnchor=null;}
        if(!golem.home().equals(cropChecklistHome)||radius!=cropChecklistRange||logs!=checklistLogs){cropChecklist.clear();cropChecklistHome=golem.home();cropChecklistRange=radius;checklistLogs=logs;}
        if(cropChecklist.isEmpty())for(var column:BlockPos.withinManhattan(golem.home(),radius,0,radius))cropChecklist.add(column.immutable());
        int height=logs?(radius+1)/2:3;
        for(int count=Math.min(16,cropChecklist.size());count>0;count--){var column=cropChecklist.removeFirst();for(int y=-height;y<=height;y++)if(harvestCandidate(column.offset(0,y,0),logs,radius))return true;}
        return false;
    }
    private void harvestBlock(BlockPos pos,BlockState original,boolean logs){var player=fake(new ItemStack(logs?Items.NETHERITE_AXE:Items.NETHERITE_HOE));try{if(!permitted(pos,player))return;if(logs){var targetLog=original.getBlock();var furthest=thaumcraft.items.ToolActions.furthestLog(world(),pos,targetLog);if(player.gameMode.destroyBlock(furthest)){world().updateNeighborsAt(furthest,targetLog);thaumcraft.items.ToolActions.scheduleLeaves(world(),furthest);}}else if(player.gameMode.destroyBlock(pos)&&golem.upgrade(4)>0){var drops=world().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2),item->item.tickCount<3);for(var drop:drops){var stack=drop.getItem();if(stack.getItem() instanceof BlockItem seed){var state=seed.getBlock().defaultBlockState();if(state.canSurvive(world(),pos)&&world().isEmptyBlock(pos)){var snapshot=BlockSnapshot.create(world().dimension(),world(),pos);if(!world().setBlock(pos,state,2))continue;if(net.neoforged.neoforge.event.EventHooks.onBlockPlace(player,snapshot,Direction.UP)){snapshot.restore(3);continue;}world().sendBlockUpdated(pos,original,state,3);stack.shrink(1);if(stack.isEmpty())drop.discard();else drop.setItem(stack);break;}}}}golem.action(10);}finally{player.getInventory().clearContent();}}
    public static boolean validCombatTarget(ThaumicGolem golem,LivingEntity entity){
        if(entity==golem||!entity.isAlive()||entity instanceof ThaumicGolem||entity instanceof net.minecraft.world.entity.animal.IronGolem||entity instanceof net.minecraft.world.entity.animal.SnowGolem||golem.home().distSqr(entity.blockPosition())>=golem.range()*golem.range())return false;
        if(entity instanceof Player player&&(player.isCreative()||player.isSpectator()||player.getUUID().equals(golem.owner())))return false;
        boolean animal=entity instanceof Animal||entity instanceof net.minecraft.world.entity.animal.WaterAnimal||entity instanceof net.minecraft.world.entity.ambient.AmbientCreature;
        boolean tame=entity instanceof net.minecraft.world.entity.TamableAnimal pet&&pet.isTame();
        if(golem.core()==9)return animal&&!(entity instanceof Enemy)&&!tame&&(!(entity instanceof Animal ageable)||!ageable.isBaby());
        if(entity instanceof Creeper)return golem.upgrade(4)>0&&!golem.toggle(4);
        if(entity instanceof Enemy)return !golem.toggle(1);
        if(animal)return golem.upgrade(4)>0&&!golem.toggle(2)&&!tame;
        return entity instanceof Player&&golem.upgrade(4)>0&&!golem.toggle(3);
    }
    private boolean guard(){
        var box=golem.getBoundingBox().inflate(golem.range(),4,golem.range());
        var entities=world().getEntitiesOfClass(LivingEntity.class,box,entity->validCombatTarget(golem,entity));
        if(golem.core()==9)entities.sort(Comparator.comparingInt((LivingEntity entity)->entity.tickCount).reversed());else entities.sort(Comparator.comparingDouble(golem::distanceToSqr));
        for(var entity:entities){
            if(golem.core()==9&&world().getEntitiesOfClass(LivingEntity.class,box,other->entity.getClass().isInstance(other)&&validCombatTarget(golem,other)).size()<=2)continue;
            combat=entity;target=combat.position();valid=()->combat!=null&&validCombatTarget(golem,combat);job=this::guardStep;return true;
        }
        return returnHome();
    }
    private void guardStep(){
        if(combat==null||!combat.isAlive())return;
        if(golem.decoration().contains("R")&&golem.distanceToSqr(combat)>=9){
            int interval=Math.max(1,30-golem.upgrade(0)*8);
            if(dartTarget!=combat.getId()){dartTarget=combat.getId();dartCooldown=interval/2;}
            if(golem.hasLineOfSight(combat)){
                golem.getLookControl().setLookAt(combat,30,30);dartCooldown=Math.max(0,dartCooldown-1);
                double range=golem.range()*0.8;
                if(dartCooldown==0&&golem.distanceToSqr(combat)<=range*range){
                    var dart=new Dart(world(),golem,combat);
                    golem.playSound(thaumcraft.registry.R2Sounds.GOLEM_IRON_SHOOT.get(),.5f,1/(golem.getRandom().nextFloat()*.4f+.6f));
                    world().addFreshEntity(dart);golem.action(10);dartCooldown=interval;
                }
            }
            job=this::guardStep;timeout=2;target=combat.position();return;
        }
        dartTarget=-1;dartCooldown=0;golem.doHurtTarget(combat);golem.action(10);searchAfter=golem.tickCount+(golem.advanced()?18:20);
    }
    private boolean fishingWater(BlockPos pos){return world().hasChunkAt(pos)&&world().getFluidState(pos).is(FluidTags.WATER)&&world().isEmptyBlock(pos.above());}
    private boolean fish(){
        if(bobber!=null){bobber.discard();bobber=null;}
        int radius=(int)Math.ceil(golem.range()/2f);
        if(!golem.home().equals(fishingSearchHome)||radius!=fishingSearchRadius){knownFishingSpot=null;nextWaterSearch=0;fishingSearchHome=golem.home();fishingSearchRadius=radius;}
        if(knownFishingSpot!=null&&!fishingWater(knownFishingSpot)){knownFishingSpot=null;nextWaterSearch=0;}
        if(knownFishingSpot==null){
            if(golem.tickCount<nextWaterSearch)return returnHome();
            // Nearest columns first: water beside the golem is found on the first AI search,
            // instead of waiting for random probes to happen to land in the pond.
            for(var candidate:BlockPos.withinManhattan(golem.home(),radius,(radius+1)/2,radius))if(fishingWater(candidate)){knownFishingSpot=candidate.immutable();break;}
            if(knownFishingSpot==null){nextWaterSearch=golem.tickCount+10;return returnHome();}
        }
        var pos=knownFishingSpot;
        fishing=pos;workTarget=pos.getCenter();fishingQuality=0;
        for(var face:List.of(Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST)){
            var side=pos.relative(face);if(!world().getFluidState(side).is(FluidTags.WATER)||!world().isEmptyBlock(side.above()))continue;
            fishingQuality+=.00003f;if(world().canSeeSky(side.above()))fishingQuality+=.00003f;
            for(int depth=1;depth<=3;depth++)if(world().getFluidState(side.below(depth)).is(FluidTags.WATER))fishingQuality+=.000015f;
        }
        golem.playSound(net.minecraft.sounds.SoundEvents.ARROW_SHOOT,.5f,.4f/(world().random.nextFloat()*.4f+.8f));
        bobber=new GolemBobber(world(),golem,pos);
        if(!world().addFreshEntity(bobber)){bobber=null;fishing=null;return false;}
        golem.fishingTarget(pos);
        target=golem.position();valid=()->fishing!=null&&bobber!=null&&bobber.isAlive()&&fishingWater(fishing);job=this::fishingStep;return true;
    }
    private void fishingStep(){
        if(fishing==null||bobber==null||!bobber.isAlive())return;
        golem.getLookControl().setLookAt(fishing.getX()+.5,fishing.getY()+1,fishing.getZ()+.5,30,30);
        var random=world().random;
        if(random.nextFloat()<fishingQuality+golem.strength()*.00015f){
            golem.action(10);int count=golem.upgrade(0)>0&&random.nextInt(10)<golem.upgrade(0)?2:1;
            for(int i=0;i<count;i++){
                var stack=GolemFishing.loot(golem,world(),fishing);
                var item=new ItemEntity(world(),fishing.getX()+.5,fishing.getY()+1,fishing.getZ()+.5,stack);
                if(golem.upgrade(2)>0)item.igniteForSeconds(2);item.setPickUpDelay(20);
                // Original expressions add +.5/+1 after subtracting the target coordinate.
                double x=golem.getX()+random.nextFloat()-random.nextFloat()-fishing.getX()+.5,
                    y=golem.getY()-fishing.getY()+1,z=golem.getZ()+random.nextFloat()-random.nextFloat()-fishing.getZ()+.5;
                double length=(float)Math.sqrt(x*x+y*y+z*z);
                item.setDeltaMovement(x*.1,y*.1+(double)(float)Math.sqrt(length)*.08,z*.1);world().addFreshEntity(item);
            }
            bobber.playSound(net.minecraft.sounds.SoundEvents.GENERIC_SPLASH,.15f,1+(random.nextFloat()-random.nextFloat())*.4f);
            world().sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,bobber.getX(),bobber.getY()+.5,bobber.getZ(),20+random.nextInt(20),(double).1f,0,(double).1f,0);
            bobber.discard();bobber=null;fishing=null;golem.fishingTarget(null);return;
        }
        if(--fishingTicks>0){job=this::fishingStep;timeout=2;target=golem.position();}
    }
    // Idle movement must never lock the work planner behind a 200-tick action.
    private boolean returnHome(){return false;}
    public Goal returnHomeGoal(){return new Goal(){
        private net.minecraft.world.level.pathfinder.Path path;
        private BlockPos home;private int nextAttempt;
        {setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public boolean canUse(){
            if(golem.level().isClientSide||golem.inactive()||golem.distanceToSqr(golem.home().getCenter())<=4)return false;
            if(!golem.home().equals(home)){home=golem.home();nextAttempt=0;}
            if(golem.tickCount<nextAttempt)return false;nextAttempt=golem.tickCount+20;
            path=approachPath(home.getCenter());return path!=null&&path.canReach()&&!path.isDone();
        }
        @Override public boolean canContinueToUse(){return !golem.inactive()&&home.equals(golem.home())&&golem.distanceToSqr(home.getCenter())>4&&!golem.getNavigation().isDone();}
        @Override public void start(){returningHome=true;golem.getNavigation().moveTo(path,1);}
        @Override public void stop(){returningHome=false;golem.getNavigation().stop();}
        @Override public void tick(){golem.getLookControl().setLookAt(home.getX()+.5,home.getY()+.5,home.getZ()+.5,30,30);}
    };}
}
