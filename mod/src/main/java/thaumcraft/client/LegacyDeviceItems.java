package thaumcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.*;
import thaumcraft.blocks.*;
import thaumcraft.production.ProductionBlock;
import thaumcraft.registry.*;

/** Inventory forms include the same visible details as their original TC4 renderers. */
@EventBusSubscriber(modid="thaumcraft_aop",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class LegacyDeviceItems {
    private static final class Renderer extends BlockEntityWithoutLevelRenderer {
        private TableDetailsRenderer details;
        Renderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
        @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
            var mc=Minecraft.getInstance();var block=((BlockItem)stack.getItem()).getBlock();
            if(block==R4Content.ADVANCED_FURNACE.get()){
                pose.pushPose();pose.translate(.5,1.0/6,.5);pose.scale(1f/3,1f/3,1f/3);pose.translate(-.5,0,-.5);
                new AdvancedAlchemyRenderer(null).render(new thaumcraft.production.AdvancedAlchemyBlockEntity(BlockPos.ZERO,block.defaultBlockState()),0,pose,buffers,light,overlay);pose.popPose();return;
            }
            if(block==R3Content.INFERNAL_FURNACE.get()){
                pose.pushPose();pose.translate(.5,.5,.5);pose.scale(1f/3,1f/3,1f/3);pose.translate(-.5,-.5,-.5);
                for(int part=0;part<27;part++){var offset=thaumcraft.production.InfernalFurnaceBlock.offset(part);pose.pushPose();pose.translate(offset.getX(),offset.getY(),offset.getZ());mc.getBlockRenderer().renderSingleBlock(block.defaultBlockState().setValue(thaumcraft.production.InfernalFurnaceBlock.PART,part),pose,buffers,light,overlay);pose.popPose();}
                pose.popPose();return;
            }
            if(block instanceof ProductionBlock production){TransportDeviceGeometry.draw(production.kind,Direction.DOWN,pose,buffers,light,overlay,0,0,true);return;}
            var state=block.defaultBlockState();
            if(block==R2Content.RESEARCH_TABLE.get()){state=state.setValue(TableBlock.FACING,Direction.EAST);pose.translate(-.5,0,0);}
            mc.getBlockRenderer().renderSingleBlock(state,pose,buffers,light,overlay);
            if(block==R2Content.RESEARCH_TABLE.get()){
                if(details==null)details=new TableDetailsRenderer(mc.getEntityModels().bakeLayer(TableDetailsRenderer.LAYER));
                var table=new TableBlockEntity(BlockPos.ZERO,state);table.setItem(0,new ItemStack(R2Content.SCRIBING_TOOLS.get()));var note=new ItemStack(R2Content.NOTES.get());thaumcraft.items.ResearchNotes.write(note,new thaumcraft.api.research.ResearchNote("NITOR",new java.util.LinkedHashMap<>(),false,0));table.setItem(1,note);details.render(table,0,pose,buffers,light,overlay);
            }else{
                TableScannerGeometry.draw(pose,buffers,light,overlay);
            }
        }
    }
    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event){event.registerItem(new IClientItemExtensions(){private Renderer renderer;@Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new Renderer();return renderer;}},R3Content.ALEMBIC.get().asItem(),R3Content.CRYSTALLIZER.get().asItem(),R3Content.RESERVOIR.get().asItem(),R2Content.RESEARCH_TABLE_ITEM.get(),R4Content.DECONSTRUCTOR.get().asItem(),R4Content.ADVANCED_FURNACE_ITEM.get(),R3Content.INFERNAL_FURNACE_ITEM.get());}
}
