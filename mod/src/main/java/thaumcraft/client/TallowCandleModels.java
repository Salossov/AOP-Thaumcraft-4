package thaumcraft.client;

import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
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
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.*;
import org.joml.Vector3f;
import thaumcraft.blocks.TallowCandleGeometry;

/** Native baked geometry/model data; no tile or render-time world RNG is needed. */
@EventBusSubscriber(modid="thaumcraft_aop",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class TallowCandleModels {
    private static final ModelProperty<List<BakedQuad>> DRIPS=new ModelProperty<>();
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event){
        for(var candle:thaumcraft.registry.R4Content.CANDLES){
            var id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(candle.get());
            for(String variant:List.of("","inventory")){var key=new ModelResourceLocation(id,variant);
            var base=Objects.requireNonNull(event.getModels().get(key),"Missing candle block model "+id);
            var wax=base.getParticleIcon();var bakery=new FaceBakery();var cache=new HashMap<TallowCandleGeometry.Box,List<BakedQuad>>();
            // There are exactly24 possible drip cuboids. Bake each once at resource reload.
            for(boolean alongX:new boolean[]{true,false})for(boolean side:new boolean[]{true,false})for(int loc=2;loc<=3;loc++)for(int h=1;h<=3;h++){
                float height=h*.0625f;var box=alongX?new TallowCandleGeometry.Box(.3125f+.0625f*loc,0,side?.3125f:.625f,.375f+.0625f*loc,height,side?.375f:.6875f,false):new TallowCandleGeometry.Box(side?.3125f:.625f,0,.3125f+.0625f*loc,side?.375f:.6875f,height,.375f+.0625f*loc,false);
                var from=new Vector3f(box.x0()*16,box.y0()*16,box.z0()*16);var to=new Vector3f(box.x1()*16,box.y1()*16,box.z1()*16);var quads=new ArrayList<BakedQuad>();
                for(var face:Direction.values()){
                    var descriptor=new BlockElementFace(null,0,"#wax",new BlockFaceUV(new float[]{0,0,16,16},0));
                    quads.add(originalUvs(bakery.bakeQuad(from,to,descriptor,wax,face,BlockModelRotation.X0_Y0,null,true)));
                }
                cache.put(box,List.copyOf(quads));
            }
            event.getModels().put(key,new BakedModelWrapper<BakedModel>(base){
                private final Map<BakedQuad,BakedQuad> remapped=new IdentityHashMap<>();
                private List<BakedQuad> original(List<BakedQuad> quads){synchronized(remapped){return quads.stream().map(q->remapped.computeIfAbsent(q,TallowCandleModels::originalUvs)).toList();}}
                @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return original(base.getQuads(state,side,random));}
                @Override public List<BakedModel> getRenderPasses(net.minecraft.world.item.ItemStack stack,boolean fabulous){return List.of(this);}
                @Override public BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){base.applyTransform(context,pose,left);return this;}
                @Override public ModelData getModelData(BlockAndTintGetter level,BlockPos pos,BlockState state,ModelData original){
                    var quads=new ArrayList<BakedQuad>();var boxes=TallowCandleGeometry.world(pos);
                    for(int i=1;i<boxes.size()-1;i++)quads.addAll(Objects.requireNonNull(cache.get(boxes.get(i)),"Unknown original candle drip"));
                    return original.derive().with(DRIPS,List.copyOf(quads)).build();
                }
                @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType type){
                    var original=original(base.getQuads(state,side,random,data,type));var drips=data.get(DRIPS);
                    if(side!=null||drips==null)return original;
                    var result=new ArrayList<BakedQuad>(original.size()+drips.size());result.addAll(original);result.addAll(drips);return result;
                }
            });}
        }
    }
    /** Original vanilla RenderBlocks face coordinates, proven by its retained six face methods. */
    private static BakedQuad originalUvs(BakedQuad quad){
        var vertices=quad.getVertices().clone();var sprite=quad.getSprite();
        float minX=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,minZ=Float.POSITIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
        for(int vertex=0;vertex<4;vertex++){int offset=vertex*IQuadTransformer.STRIDE;float x=Float.intBitsToFloat(vertices[offset]),z=Float.intBitsToFloat(vertices[offset+2]);minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);}
        for(int vertex=0;vertex<4;vertex++){
            int offset=vertex*IQuadTransformer.STRIDE;float x=Float.intBitsToFloat(vertices[offset]),y=Float.intBitsToFloat(vertices[offset+1]),z=Float.intBitsToFloat(vertices[offset+2]);
            float u=switch(quad.getDirection()){case DOWN,UP,SOUTH -> x;case NORTH -> minX+maxX-x;case WEST -> z;case EAST -> minZ+maxZ-z;};
            float v=quad.getDirection().getAxis()==Direction.Axis.Y?z:1-y;
            vertices[offset+IQuadTransformer.UV0]=Float.floatToRawIntBits(sprite.getU(u));vertices[offset+IQuadTransformer.UV0+1]=Float.floatToRawIntBits(sprite.getV(v));
        }
        return new BakedQuad(vertices,quad.getTintIndex(),quad.getDirection(),sprite,quad.isShade(),quad.hasAmbientOcclusion());
    }
    private TallowCandleModels(){}
}
