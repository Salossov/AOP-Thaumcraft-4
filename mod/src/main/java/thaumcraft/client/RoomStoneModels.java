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
import net.neoforged.neoforge.client.model.*;
import net.neoforged.neoforge.client.model.data.*;
import thaumcraft.blocks.RoomStoneBounds;

@EventBusSubscriber(modid="thaumcraft_aop",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class RoomStoneModels {
    private static final ModelProperty<Integer> NEIGHBORS=new ModelProperty<>();
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event){for(String id:List.of("carved_eldritch_stone","ancient_runic_stone","decorated_eldritch_stone")){var key=new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath("thaumcraft",id),"");var base=Objects.requireNonNull(event.getModels().get(key));event.getModels().put(key,new BakedModelWrapper<BakedModel>(base){
        // These blocks have full-cube selection/collision. Render the full cube
        // as well: neighbor-dependent insets left invisible strips with a hitbox.
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType type){return base.getQuads(state,side,random,data,type);}
    });}}
    /** Crop UVs to the inset bounds, as RenderBlocks did, rather than stretching the texture. */
    static BakedQuad inset(BakedQuad quad,int mask){var original=quad.getVertices();var vertices=original.clone();int stride=IQuadTransformer.STRIDE,position=IQuadTransformer.POSITION,uv=IQuadTransformer.UV0;float[][] gradient=new float[2][3];for(int edge:new int[]{1,3})for(int axis=0;axis<3;axis++){float delta=value(original,edge*stride+position+axis)-value(original,position+axis);if(delta!=0)for(int channel=0;channel<2;channel++)gradient[channel][axis]=(value(original,edge*stride+uv+channel)-value(original,uv+channel))/delta;}for(int vertex=0;vertex<4;vertex++){int offset=vertex*stride;float[] moved=new float[3];for(int axis=0;axis<3;axis++){float old=value(original,offset+position+axis),low=RoomStoneBounds.min(mask,axis),high=RoomStoneBounds.max(mask,axis);moved[axis]=low+(high-low)*old;vertices[offset+position+axis]=Float.floatToRawIntBits(moved[axis]);}for(int channel=0;channel<2;channel++){float mapped=value(original,uv+channel);for(int axis=0;axis<3;axis++)mapped+=gradient[channel][axis]*(moved[axis]-value(original,position+axis));vertices[offset+uv+channel]=Float.floatToRawIntBits(mapped);}}return new BakedQuad(vertices,quad.getTintIndex(),quad.getDirection(),quad.getSprite(),false,false);}
    private static float value(int[] vertices,int index){return Float.intBitsToFloat(vertices[index]);}
    private RoomStoneModels(){}
}
