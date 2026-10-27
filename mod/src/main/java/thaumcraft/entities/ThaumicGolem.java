package thaumcraft.entities;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.items.*;
import thaumcraft.registry.R3Equipment;

public final class ThaumicGolem extends PathfinderMob implements MenuProvider, thaumcraft.api.entities.IControllableGolem {
    @Override protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level){
        return new net.minecraft.world.entity.ai.navigation.GroundPathNavigation(this,level){
            @Override public void tick(){
                if(inactive()||isNoAi()){
                    // stop() alone retains vanilla's delayed recomputation and target.
                    // A later tick must not resurrect a path while the fetter is powered.
                    stop();hasDelayedRecomputation=false;
                    getMoveControl().setWantedPosition(getX(),getY(),getZ(),0);
                    setSpeed(0);
                    return;
                }
                super.tick();
            }
        };
    }
    private static final EntityDataAccessor<Integer> UPGRADE_MASK=SynchedEntityData.defineId(ThaumicGolem.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MATERIAL=SynchedEntityData.defineId(ThaumicGolem.class,EntityDataSerializers.INT),CORE=SynchedEntityData.defineId(ThaumicGolem.class,EntityDataSerializers.INT),ACTION=SynchedEntityData.defineId(ThaumicGolem.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> VISUAL_INACTIVE=SynchedEntityData.defineId(ThaumicGolem.class,EntityDataSerializers.BOOLEAN);
    private int healingVisualUntil;
    private static final EntityDataAccessor<Boolean> ADVANCED=SynchedEntityData.defineId(ThaumicGolem.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DECORATION=SynchedEntityData.defineId(ThaumicGolem.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<ItemStack> CARRIED=SynchedEntityData.defineId(ThaumicGolem.class,EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DISPLAY_CARRIED=SynchedEntityData.defineId(ThaumicGolem.class,EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> BACKPACK_EQUIPMENT=SynchedEntityData.defineId(ThaumicGolem.class,EntityDataSerializers.ITEM_STACK);
    private static final String BACKPACK_EQUIPMENT_KEY="BackpackEquipment";
    private static final net.minecraft.resources.ResourceLocation WATER_SPEED=net.minecraft.resources.ResourceLocation.parse("thaumcraft:golem_water_speed");
    private static final EntityDataAccessor<Optional<BlockPos>> FISHING=SynchedEntityData.defineId(ThaumicGolem.class,EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private UUID owner;private BlockPos home=BlockPos.ZERO;private Direction homeFace=Direction.UP;
    private final List<GolemMarker> markers=new ArrayList<>();
    private byte[] upgrades=new byte[]{-1};
    private int workRevision;
    private int externalCargoRevision;
    private GolemWorkExtensions workExtensions;
    int extensionConfigurationRevision(){return workRevision-externalCargoRevision;}
    void externalCargoChanged(){workRevision++;externalCargoRevision++;updateCarriedDisplay();}
    boolean externalItemDestination(){return workExtensions!=null&&workExtensions.hasItemDestination();}
    GolemWorkGoal nativeWorkGoal(){return workGoal;}
    @Override public void remove(Entity.RemovalReason reason){if(workExtensions!=null)workExtensions.close();super.remove(reason);}
    int workRevision(){return workRevision;}
    public final SimpleContainer filters=new SimpleContainer(18){@Override public void setChanged(){super.setChanged();workRevision++;}};
    private final byte[] colors=new byte[18];
    private int toggles,regen;
    private int viewers;
    private GolemWorkGoal workGoal;
    public GolemWorkGoal.DebugPlan debugPlan(){return workGoal.debugPlan();}
    public String debugInactiveReason(){if(isNoAi())return "no_ai";if(viewers>0)return "menu_open";if(core()<0)return "no_core";var below=level().getBlockState(blockPosition().below());return below.is(thaumcraft.registry.R3Content.GOLEM_FETTER.get())&&below.getValue(thaumcraft.blocks.GolemFetterBlock.POWERED)?"fetter":"";}
    public Aspect essentia;public int essentiaAmount;public FluidStack fluid=FluidStack.EMPTY;
    public ThaumicGolem(EntityType<? extends ThaumicGolem> type,Level level){super(type,level);Arrays.fill(colors,(byte)-1);setPersistenceRequired();}
    private static final EntityDataAccessor<Long> ACTION_STARTED=SynchedEntityData.defineId(ThaumicGolem.class,EntityDataSerializers.LONG);
    public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,10).add(Attributes.MOVEMENT_SPEED,.38).add(Attributes.ARMOR,0).add(Attributes.ATTACK_DAMAGE,2).add(Attributes.FOLLOW_RANGE,32).add(Attributes.STEP_HEIGHT,1);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(UPGRADE_MASK,0);builder.define(MATERIAL,0);builder.define(CORE,-1);builder.define(ACTION,0);builder.define(ACTION_STARTED,0L);builder.define(ADVANCED,false);builder.define(VISUAL_INACTIVE,true);builder.define(DECORATION,"");builder.define(CARRIED,ItemStack.EMPTY);builder.define(DISPLAY_CARRIED,ItemStack.EMPTY);builder.define(BACKPACK_EQUIPMENT,ItemStack.EMPTY);builder.define(FISHING,Optional.empty());}
    @Override protected void registerGoals(){goalSelector.addGoal(1,workGoal=new GolemWorkGoal(this));goalSelector.addGoal(2,workGoal.returnHomeGoal());goalSelector.addGoal(5,new OpenDoorGoal(this,true));goalSelector.addGoal(6,new LookAtPlayerGoal(this,Player.class,6));goalSelector.addGoal(7,new RandomLookAroundGoal(this));}
    public GolemMaterial material(){return GolemMaterial.byId(entityData.get(MATERIAL));}
    public int core(){return entityData.get(CORE);}
    public boolean advanced(){return entityData.get(ADVANCED);}
    public String decoration(){return entityData.get(DECORATION);}
    public UUID owner(){return owner;}
    /** Always a copy, including on tracking clients. Use GolemEquipmentApi from addons. */
    public ItemStack backpackEquipment(){return entityData.get(BACKPACK_EQUIPMENT).copy();}
    public boolean replaceBackpackEquipment(ItemStack expected,ItemStack replacement){
        requireGolemServerThread();Objects.requireNonNull(expected);Objects.requireNonNull(replacement);
        if(!replacement.isEmpty()&&replacement.getCount()!=1)throw new IllegalArgumentException("Backpack equipment must contain one item");
        if(!ItemStack.matches(entityData.get(BACKPACK_EQUIPMENT),expected))return false;
        entityData.set(BACKPACK_EQUIPMENT,replacement.copy());workRevision++;return true;
    }
    private void dropBackpackEquipment(){
        var stack=backpackEquipment();if(stack.isEmpty())return;
        // Clear before spawning: repeated death/dismantling callbacks cannot duplicate it.
        entityData.set(BACKPACK_EQUIPMENT,ItemStack.EMPTY);spawnAtLocation(stack);
    }
    public BlockPos home(){return home;}
    public Direction homeFace(){return homeFace;}
    public BlockPos source(){return home.relative(homeFace.getOpposite());}
    public List<GolemMarker> markers(){return Collections.unmodifiableList(markers);}
    public ItemStack carried(){return entityData.get(CARRIED);}
    public void carried(ItemStack stack){entityData.set(CARRIED,stack.copy());}
    public ItemStack displayCarried(){return entityData.get(DISPLAY_CARRIED);}
    public void updateCarriedDisplay(){
        if(level().isClientSide)return;var display=carried().copy();
        if(display.isEmpty()&&core()==6){display=new ItemStack(thaumcraft.registry.R3Content.WARDED_JAR.get());var data=new CompoundTag();data.putString("id","thaumcraft:essentia_transport");var vis=new CompoundTag();if(essentia!=null&&essentiaAmount>0)vis.putInt(essentia.getTag(),(int)(64f*essentiaAmount/carryLimit()));data.put("Essentia",vis);display.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(data));}
        else if(display.isEmpty()&&core()==5&&!fluid.isEmpty()){display=new ItemStack(fluid.getFluid().getBucket());var tag=new CompoundTag();tag.put("Fluid",fluid.save(registryAccess()));display.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}
        if(!ItemStack.matches(display,displayCarried()))entityData.set(DISPLAY_CARRIED,display);
    }
    public void updateWaterSpeed(){var attribute=getAttribute(Attributes.MOVEMENT_SPEED);boolean heavy=material()==GolemMaterial.STONE||material()==GolemMaterial.IRON||material()==GolemMaterial.THAUMIUM;if(isInWater()&&heavy){if(!attribute.hasModifier(WATER_SPEED))attribute.addTransientModifier(new AttributeModifier(WATER_SPEED,1,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));}else attribute.removeModifier(WATER_SPEED);}
    public int action(){return Math.max(0,entityData.get(ACTION)-(int)Math.max(0,level().getGameTime()-entityData.get(ACTION_STARTED)));}
    public BlockPos fishingTarget(){return entityData.get(FISHING).orElse(null);}
    public void fishingTarget(BlockPos pos){entityData.set(FISHING,Optional.ofNullable(pos));}
    public void open(){viewers++;getNavigation().stop();syncVisualInactive();}
    public void close(){viewers=Math.max(0,viewers-1);syncVisualInactive();}
    private void syncVisualInactive(){
        if(!level().isClientSide&&level().hasChunkAt(blockPosition()))entityData.set(VISUAL_INACTIVE,inactive()||isNoAi());
    }
    public boolean visualInactive(){
        if(level().isClientSide)return entityData.get(VISUAL_INACTIVE);
        return isRemoved()||!level().hasChunkAt(blockPosition())||inactive()||isNoAi();
    }
    public int visualHealingTicks(){return level().isClientSide?Math.clamp(healingVisualUntil-tickCount,0,5):0;}
    @Override public void handleEntityEvent(byte event){
        if(event==5){if(level().isClientSide)healingVisualUntil=tickCount+5;return;}
        super.handleEntityEvent(event);
    }
    public int toggles(){return toggles;}
    public void toggles(int value){workRevision++;toggles=value&255;}
    public int upgradeSlots(){return material().upgrades+(advanced()?1:0);}
    public int installedUpgrades(){int total=0;for(int i=0;i<6;i++)total+=upgrade(i);return total;}
    public int upgradeMask(){int result=0;for(int i=0;i<6;i++)result|=upgrade(i)<<(i*2);return result;}
    public void action(int duration){if(action()>0)return;entityData.set(ACTION_STARTED,level().getGameTime());entityData.set(ACTION,6);}
    public int upgrade(int kind){if(level().isClientSide)return entityData.get(UPGRADE_MASK)>>(kind*2)&3;int count=0;for(byte value:upgrades)if(value==kind)count++;return count;}
    public int strength(){return material().strength+upgrade(1);}
    public int carryLimit(){int base=material().carry;return base+Math.min(16,Math.max(4,base))*upgrade(1);}
    public int fluidLimit(){return (int)Math.sqrt(carryLimit())*1000;}
    public double range(){double range=16+4*upgrade(3);if(decoration().contains("G"))range+=Math.max(range*.1,1);if(advanced())range+=Math.max(range*.2,2);return range;}
    public int filterSlots(){return core()==5?Math.min(18,1+upgrade(2)):Math.min(18,6+6*upgrade(2));}
    public int color(int slot){return slot>=0&&slot<colors.length?colors[slot]:-1;}
    public void color(int slot,int value){workRevision++;if(slot>=0&&slot<colors.length)colors[slot]=(byte)Math.max(-1,Math.min(15,value));}
    public boolean toggle(int index){return (toggles&(1<<index))!=0;}
    public void toggle(int index,boolean value){workRevision++;if(index>=0&&index<8)toggles=value?toggles|1<<index:toggles&~(1<<index);}
    public boolean inactive(){var below=level().getBlockState(blockPosition().below());return viewers>0||core()<0||below.is(thaumcraft.registry.R3Content.GOLEM_FETTER.get())&&below.getValue(thaumcraft.blocks.GolemFetterBlock.POWERED);}
    public void initialize(GolemMaterial material,UUID owner,BlockPos home,Direction face,ItemStack stack){entityData.set(MATERIAL,material.ordinal());this.owner=owner;this.home=home.immutable();homeFace=face;var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();loadConfiguration(data);configure();setHealth(getMaxHealth());if(stack.has(DataComponents.CUSTOM_NAME))setCustomName(stack.getHoverName());}
    private void configure(){workRevision++;entityData.set(UPGRADE_MASK,upgradeMask());var body=material();getAttribute(Attributes.MAX_HEALTH).setBaseValue(body.health+(decoration().contains("H")?5:0));getAttribute(Attributes.ARMOR).setBaseValue(Math.min(20,body.armor+(decoration().contains("P")?4:0)+(decoration().contains("V")?1:0)));getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(body.speed*(decoration().contains("B")?1.1:1)*(decoration().contains("P")?.88:1)*(1+upgrade(0)*.15)*(advanced()?1.1:1));getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2+strength()+upgrade(1)+(decoration().contains("M")?2:0));if(getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation ground){ground.setCanOpenDoors(core()>=0);ground.setCanFloat(body!=GolemMaterial.STONE&&body!=GolemMaterial.IRON&&body!=GolemMaterial.THAUMIUM);}syncVisualInactive();}
    @Override public boolean fireImmune(){return material().fireResist||super.fireImmune();}
    @Override public boolean doHurtTarget(Entity target){
        boolean hit=super.doHurtTarget(target);
        if(hit){if(upgrade(2)>0)target.igniteForSeconds(upgrade(2)*4);if(decoration().contains("V")&&target instanceof LivingEntity living)((thaumcraft.mixin.LivingEntityRecentHitAccessor)living).thaumcraft$setRecentlyHit(100);}
        return hit;
    }
    @Override public void aiStep(){if(!level().isClientSide){syncVisualInactive();if(workExtensions==null)workExtensions=new GolemWorkExtensions(this,goalSelector);workExtensions.refresh();}super.aiStep();if(level().isClientSide)return;updateCarriedDisplay();updateWaterSpeed();if(--regen<=0){regen=(int)(material().regenDelay*(decoration().contains("F")?.66:1));if(getHealth()<getMaxHealth()){heal(1);level().broadcastEntityEvent(this,(byte)5);}}if(distanceToSqr(home.getCenter())>=2304&&level().hasChunkAt(home)){for(var pos:BlockPos.betweenClosed(home.offset(-1,-1,-1),home.offset(1,1,1))){if(level().getBlockState(pos.below()).isFaceSturdy(level(),pos.below(),Direction.UP)&&level().getBlockState(pos).getCollisionShape(level(),pos).isEmpty()&&level().getBlockState(pos.above()).getCollisionShape(level(),pos.above()).isEmpty()){moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,getYRot(),getXRot());getNavigation().stop();break;}}}}
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand){var stack=player.getItemInHand(hand);if(stack.getItem() instanceof GolemBellItem||stack.getItem() instanceof thaumcraft.items.ThaumometerItem)return InteractionResult.PASS;
        if(stack.getItem() instanceof GolemCoreItem item&&core()<0&&item.kind!=GolemCoreItem.Kind.BLANK){if(!level().isClientSide){entityData.set(CORE,item.kind.ordinal());if(!player.getAbilities().instabuild)stack.shrink(1);configure();action(20);}return InteractionResult.sidedSuccess(level().isClientSide);}
        if(stack.getItem() instanceof GolemUpgradeItem item){
            if(upgrade(item.kind)>=2||installedUpgrades()>=upgradeSlots()){
                if(!level().isClientSide)player.displayClientMessage(Component.translatable(upgrade(item.kind)>=2?"thaumcraft.golem.upgrade_duplicate":"thaumcraft.golem.upgrade_full",installedUpgrades(),upgradeSlots()),true);
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            for(int i=0;i<upgrades.length;i++)if(upgrades[i]<0){if(!level().isClientSide){upgrades[i]=(byte)item.kind;if(!player.getAbilities().instabuild)stack.shrink(1);configure();action(10);playSound(thaumcraft.registry.R2Sounds.ZAP.get(),.5f,1);}return InteractionResult.sidedSuccess(level().isClientSide);}
        }
        if(stack.getItem() instanceof GolemDecorationItem item&&!decoration().contains(item.decoration)){if(!level().isClientSide){entityData.set(DECORATION,decoration()+item.decoration);if(!player.getAbilities().instabuild)stack.shrink(1);configure();}return InteractionResult.sidedSuccess(level().isClientSide);}
        if(stack.is(Items.SUGAR)){if(!level().isClientSide){stack.consume(1,player);heal(5);addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,Math.min(2400,600+(hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED)?getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED).getDuration():0)),0));level().broadcastEntityEvent(this,(byte)7);}return InteractionResult.sidedSuccess(level().isClientSide);}
        if(player instanceof ServerPlayer server&&Set.of(0,1,2,4,5,8,10).contains(core()))server.openMenu(this,buffer->buffer.writeVarInt(getId()));return InteractionResult.sidedSuccess(level().isClientSide);
    }
    public void replaceMarkers(java.util.List<GolemMarker> values){var copy=List.copyOf(values);markers.clear();markers.addAll(copy);workRevision++;}
    @Override public String golemMaterial(){return material().name().toLowerCase(Locale.ROOT);}
    @Override public List<thaumcraft.api.entities.IControllableGolem.Marker> golemMarkers(){return markers.stream().map(marker->new thaumcraft.api.entities.IControllableGolem.Marker(marker.dimension(),marker.pos(),marker.face(),marker.color())).toList();}
    private void requireGolemServerThread(){if(level().isClientSide||level().getServer()==null||!level().getServer().isSameThread())throw new IllegalStateException("Golem configuration requires its server thread");}
    @Override public void setGolemHome(BlockPos position,Direction face){requireGolemServerThread();var next=Objects.requireNonNull(position,"position").immutable();Objects.requireNonNull(face,"face");home=next;homeFace=face;restrictTo(next,35);workRevision++;}
    @Override public void replaceGolemMarkers(List<thaumcraft.api.entities.IControllableGolem.Marker> values){requireGolemServerThread();var copy=List.copyOf(values).stream().map(marker->new GolemMarker(marker.dimension(),marker.position(),marker.face(),marker.color())).toList();replaceMarkers(copy);}
    public boolean mark(GolemMarker marker,boolean remove){workRevision++;for(int i=0;i<markers.size();i++){var existing=markers.get(i);if(existing.pos().equals(marker.pos())&&existing.dimension().equals(marker.dimension())&&existing.face()==marker.face()){markers.remove(i);if(!remove&&upgrade(4)>0&&existing.color()<15)markers.add(new GolemMarker(marker.dimension(),marker.pos(),marker.face(),existing.color()+1));return true;}}markers.add(marker);return true;}
    public void dismantle(Player player){if(level().isClientSide)return;var result=new ItemStack(R3Equipment.GOLEM_BODIES.get(material()).get());var config=configuration();config.remove("Toggles");config.remove("Colors");if(player.isShiftKeyDown()){config.remove("core");config.remove("upgrades");config.remove("deco");config.remove("Markers");config.remove("Filters");if(core()>=0)spawnAtLocation(R3Equipment.GOLEM_CORES.get(GolemCoreItem.Kind.values()[core()]).get());for(byte upgrade:upgrades)if(upgrade>=0&&random.nextBoolean())spawnAtLocation(R3Equipment.GOLEM_UPGRADES.get(upgrade).get());}result.set(DataComponents.CUSTOM_DATA,CustomData.of(config));if(hasCustomName()&&!player.isShiftKeyDown())result.set(DataComponents.CUSTOM_NAME,getCustomName());spawnAtLocation(result);dropCarried();dropBackpackEquipment();playSound(thaumcraft.registry.R2Sounds.ZAP.get(),.5f,1);discard();}
    public void dropCarried(){if(!carried().isEmpty()){spawnAtLocation(carried());carried(ItemStack.EMPTY);}}
    @Override protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level,net.minecraft.world.damagesource.DamageSource source,boolean recentlyHit){super.dropCustomDeathLoot(level,source,recentlyHit);dropCarried();dropBackpackEquipment();}
    private CompoundTag configuration(){var tag=new CompoundTag();tag.putBoolean("advanced",advanced());tag.putInt("core",core());tag.putByteArray("upgrades",upgrades);tag.putString("deco",decoration());tag.putInt("Toggles",toggles);tag.putByteArray("Colors",colors);var list=new ListTag();for(var marker:markers)list.add(marker.save());tag.put("Markers",list);var items=new ListTag();for(int i=0;i<18;i++)if(!filters.getItem(i).isEmpty()){var item=new CompoundTag();item.putByte("Slot",(byte)i);item.put("Stack",filters.getItem(i).save(registryAccess()));items.add(item);}tag.put("Filters",items);return tag;}
    private void loadConfiguration(CompoundTag tag){entityData.set(ADVANCED,tag.getBoolean("advanced"));entityData.set(CORE,tag.contains("core")?Math.max(-1,Math.min(11,tag.getInt("core"))):-1);entityData.set(DECORATION,tag.getString("deco"));upgrades=new byte[material().upgrades+(advanced()?1:0)];Arrays.fill(upgrades,(byte)-1);var loaded=tag.getByteArray("upgrades");for(int i=0;i<Math.min(loaded.length,upgrades.length);i++)upgrades[i]=loaded[i]>=0&&loaded[i]<=5?loaded[i]:-1;toggles=tag.contains("Toggles")?tag.getInt("Toggles"):0;Arrays.fill(colors,(byte)-1);var loadedColors=tag.getByteArray("Colors");for(int i=0;i<Math.min(18,loadedColors.length);i++)colors[i]=(byte)Math.max(-1,Math.min(15,loadedColors[i]));markers.clear();for(var entry:tag.getList("Markers",Tag.TAG_COMPOUND)){var marker=GolemMarker.load((CompoundTag)entry);if(marker!=null)markers.add(marker);}filters.clearContent();for(var entry:tag.getList("Filters",Tag.TAG_COMPOUND)){var item=(CompoundTag)entry;int slot=item.getByte("Slot")&255;if(slot<18)filters.setItem(slot,ItemStack.parseOptional(registryAccess(),item.getCompound("Stack")));}}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putInt("Material",material().ordinal());if(owner!=null)tag.putUUID("Owner",owner);tag.putLong("Home",home.asLong());tag.putByte("HomeFace",(byte)homeFace.get3DDataValue());tag.put("Configuration",configuration());if(!carried().isEmpty())tag.put("Carried",carried().save(registryAccess()));if(essentia!=null){tag.putString("Essentia",essentia.getTag());tag.putInt("EssentiaAmount",essentiaAmount);tag.putBoolean("ExtendedWorkCargo",essentiaAmount>carryLimit());}if(!fluid.isEmpty())tag.put("Fluid",fluid.save(registryAccess()));var backpack=backpackEquipment();if(!backpack.isEmpty())tag.put(BACKPACK_EQUIPMENT_KEY,backpack.save(registryAccess()));else tag.remove(BACKPACK_EQUIPMENT_KEY);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);entityData.set(MATERIAL,GolemMaterial.byId(tag.getInt("Material")).ordinal());owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;home=BlockPos.of(tag.getLong("Home"));homeFace=Direction.from3DDataValue(tag.getByte("HomeFace"));loadConfiguration(tag.getCompound("Configuration"));configure();carried(ItemStack.parseOptional(registryAccess(),tag.getCompound("Carried")));essentia=Aspect.getAspect(tag.getString("Essentia"));essentiaAmount=essentia==null?0:Math.max(0,tag.getBoolean("ExtendedWorkCargo")||tag.contains(BACKPACK_EQUIPMENT_KEY,Tag.TAG_COMPOUND)?tag.getInt("EssentiaAmount"):Math.min(carryLimit(),tag.getInt("EssentiaAmount")));fluid=FluidStack.parseOptional(registryAccess(),tag.getCompound("Fluid"));entityData.set(BACKPACK_EQUIPMENT,ItemStack.parseOptional(registryAccess(),tag.getCompound(BACKPACK_EQUIPMENT_KEY)));}
    @Override public Component getDisplayName(){return hasCustomName()?getCustomName():Component.translatable("item.thaumcraft."+material().name().toLowerCase(Locale.ROOT)+"_golem");}
    @Override public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id,net.minecraft.world.entity.player.Inventory inventory,Player player){return new thaumcraft.menu.GolemMenu(id,inventory,this);}
}
