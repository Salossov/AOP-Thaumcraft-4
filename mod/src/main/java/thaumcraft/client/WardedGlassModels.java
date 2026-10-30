package thaumcraft.client;

import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.*;
import thaumcraft.blocks.WardedGlassConnections;

/** Original 47 connected glass tiles, baked into the native chunk renderer. */
@EventBusSubscriber(modid="thaumcraft_aop",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class WardedGlassModels {
    private static final ModelProperty<int[]> TILES=new ModelProperty<>();
    private static ModelResourceLocation tile(int i){return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("thaumcraft","block/warded_glass_"+(i+1)));}
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional e){for(int i=0;i<47;i++)e.register(tile(i));}
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult e){var key=new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath("thaumcraft","warded_glass"),"");var base=e.getModels().get(key);if(base==null)throw new IllegalStateException("Missing warded glass block model");var tiles=new BakedModel[47];for(int i=0;i<47;i++)tiles[i]=Objects.requireNonNull(e.getModels().get(tile(i)),"Missing connected glass tile "+i);e.getModels().put(key,new BakedModelWrapper<BakedModel>(base){
        @Override public ModelData getModelData(BlockAndTintGetter l,BlockPos p,BlockState s,ModelData original){return original.derive().with(TILES,WardedGlassConnections.tiles(l,p)).build();}
        @Override public List<BakedQuad> getQuads(BlockState s,Direction side,RandomSource random,ModelData data,RenderType type){if(side==null)return List.of();var indices=data.get(TILES);int index=indices==null?0:indices[side.get3DDataValue()];return style(tiles[index].getQuads(s,side,random,data,type));}
    });
        var inventoryKey=new ModelResourceLocation(ResourceLocation.parse("thaumcraft:warded_glass"),"inventory");var inventory=e.getModels().get(inventoryKey);
        if(inventory!=null)e.getModels().put(inventoryKey,new BakedModelWrapper<BakedModel>(inventory){
            @Override public List<BakedQuad> getQuads(BlockState s,Direction side,RandomSource random){return style(inventory.getQuads(s,side,random));}
            @Override public List<BakedQuad> getQuads(BlockState s,Direction side,RandomSource random,ModelData data,RenderType type){return style(inventory.getQuads(s,side,random,data,type));}
            @Override public List<BakedModel> getRenderPasses(net.minecraft.world.item.ItemStack stack,boolean fabulous){return List.of(this);}
            @Override public BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){inventory.applyTransform(context,pose,left);return this;}
        });
    }
    private static List<BakedQuad> style(List<BakedQuad> quads){if(!thaumcraft.config.ThaumcraftClientConfig.BRIGHT_WARDED_GLASS.get())return quads;var result=new ArrayList<BakedQuad>();for(var quad:quads){var vertices=quad.getVertices().clone();for(int i=0;i<4;i++)vertices[i*net.neoforged.neoforge.client.model.IQuadTransformer.STRIDE+6]=net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;result.add(new BakedQuad(vertices,quad.getTintIndex(),quad.getDirection(),quad.getSprite(),false,false));}return result;}
    private WardedGlassModels(){}
}
