package thaumcraft.blocks;

import net.minecraft.core.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import thaumcraft.config.ThaumcraftConfig;
import thaumcraft.registry.R4Content;

@EventBusSubscriber(modid="thaumcraft_aop")
public final class OwnedBlockInteractions {
    public static boolean protectedBlock(Block block){return block instanceof ArcaneDoorBlock||block instanceof WardedGlassBlock||block instanceof ArcanePressurePlateBlock;}
    public static BlockPos lower(net.minecraft.world.level.Level level,BlockPos pos){var state=level.getBlockState(pos);return state.getBlock() instanceof ArcaneDoorBlock&&state.getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER?pos.below():pos;}
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event){if(ThaumcraftConfig.WARDED_STONE.get()&&protectedBlock(event.getState().getBlock()))event.setCanceled(true);}
    /** Original wand removal is owner-only, including for administrators and creative players. */
    public static InteractionResult removeWithWand(UseOnContext context){
        var level=context.getLevel();var pos=context.getClickedPos();var player=context.getPlayer();var state=level.getBlockState(pos);
        if(player==null||!protectedBlock(state.getBlock()))return InteractionResult.PASS;
        // TC4 only removes an unpressed plate through this hook.
        if(state.getBlock() instanceof ArcanePressurePlateBlock&&state.getValue(ArcanePressurePlateBlock.POWERED))return InteractionResult.PASS;
        if(ThaumcraftConfig.WARDED_STONE.get()&&(!(level.getBlockEntity(pos) instanceof OwnedBlockEntity tile)||!tile.ownedBy(player.getUUID())))return InteractionResult.PASS;
        if(!level.isClientSide){var base=lower(level,pos);var item=state.getBlock().asItem();level.levelEvent(2001,pos,Block.getId(state));if(state.getBlock() instanceof ArcaneDoorBlock){level.removeBlock(base.above(),false);level.removeBlock(base,false);}else level.removeBlock(pos,false);Block.popResource(level,pos,new net.minecraft.world.item.ItemStack(item));}else player.swing(context.getHand());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    private OwnedBlockInteractions(){}
}
