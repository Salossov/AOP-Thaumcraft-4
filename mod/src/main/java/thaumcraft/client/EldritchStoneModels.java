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
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.*;
import thaumcraft.blocks.EldritchStoneTextures;

@EventBusSubscriber(modid="thaumcraft_aop",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class EldritchStoneModels {
    private static final ModelProperty<int[]> TILES=new ModelProperty<>();
    private static ModelResourceLocation tile(String kind,int index){return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("thaumcraft","block/eldritch_"+kind+"_"+(index+1)));}
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event){for(String kind:List.of("stone","rock","trap"))for(int i=0;i<4;i++)event.register(tile(kind,i));}
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event){
        for(String name:List.of("eldritch_stone","eldritch_stone_nospawn","eldritch_rock","eldritch_trap")){
            String kind=name.equals("eldritch_rock")?"rock":name.equals("eldritch_trap")?"trap":"stone";
            var key=new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath("thaumcraft",name),"");
            var base=Objects.requireNonNull(event.getModels().get(key),"Missing "+name+" block model");
            var tiles=new BakedModel[4];for(int i=0;i<4;i++)tiles[i]=Objects.requireNonNull(event.getModels().get(tile(kind,i)),"Missing eldritch texture "+i);
            event.getModels().put(key,new BakedModelWrapper<BakedModel>(base){
                @Override public ModelData getModelData(BlockAndTintGetter level,BlockPos pos,BlockState state,ModelData original){var indices=new int[6];for(var face:Direction.values())indices[face.get3DDataValue()]=kind.equals("rock")?EldritchStoneTextures.rock(pos,face):EldritchStoneTextures.stone(pos,face);return original.derive().with(TILES,indices).build();}
                @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType type){if(side==null)return List.of();var indices=data.get(TILES);return tiles[indices==null?0:indices[side.get3DDataValue()]].getQuads(state,side,random,data,type);}
            });
        }
        var sprites=new net.minecraft.client.renderer.texture.TextureAtlasSprite[4];for(int i=0;i<4;i++)sprites[i]=Objects.requireNonNull(event.getModels().get(tile("stone",i))).getParticleIcon();
        for(var entry:event.getModels().entrySet()){
            var key=entry.getKey();if(!key.id().equals(ResourceLocation.fromNamespaceAndPath("thaumcraft","eldritch_stone_stairs"))||key.getVariant().equals("inventory"))continue;
            var base=entry.getValue();entry.setValue(new BakedModelWrapper<BakedModel>(base){
                private final List<IdentityHashMap<BakedQuad,BakedQuad>> cache=List.of(new IdentityHashMap<>(),new IdentityHashMap<>(),new IdentityHashMap<>(),new IdentityHashMap<>());
                @Override public ModelData getModelData(BlockAndTintGetter level,BlockPos pos,BlockState state,ModelData original){var indices=new int[6];for(var face:Direction.values())indices[face.get3DDataValue()]=EldritchStoneTextures.stairs(pos,face);return original.derive().with(TILES,indices).build();}
                @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType type){var quads=base.getQuads(state,side,random,data,type);var indices=data.get(TILES);if(indices==null)return quads;var result=new ArrayList<BakedQuad>(quads.size());synchronized(cache){for(var quad:quads){int index=indices[quad.getDirection().get3DDataValue()];result.add(cache.get(index).computeIfAbsent(quad,q->remap(q,sprites[index])));}}return result;}
            });
        }
    }
    private static BakedQuad remap(BakedQuad quad,net.minecraft.client.renderer.texture.TextureAtlasSprite sprite){var vertices=quad.getVertices().clone();var old=quad.getSprite();for(int i=0;i<4;i++){int offset=i*IQuadTransformer.STRIDE+IQuadTransformer.UV0;vertices[offset]=Float.floatToRawIntBits(sprite.getU(old.getUOffset(Float.intBitsToFloat(vertices[offset]))));vertices[offset+1]=Float.floatToRawIntBits(sprite.getV(old.getVOffset(Float.intBitsToFloat(vertices[offset+1]))));}return new BakedQuad(vertices,quad.getTintIndex(),quad.getDirection(),sprite,quad.isShade(),quad.hasAmbientOcclusion());}
    private EldritchStoneModels(){}
}
