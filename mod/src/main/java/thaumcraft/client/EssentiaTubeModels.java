package thaumcraft.client;

import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
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

/** Adds tube arms from the connection properties synchronized in the native block state. */
@EventBusSubscriber(modid="thaumcraft_aop",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class EssentiaTubeModels {
    private static final List<String> IDS=List.of("essentia_tube","essentia_valve","filtered_essentia_tube","restricted_essentia_tube","directional_essentia_tube","essentia_buffer");
    private static final ModelProperty<Integer> CONNECTIONS=new ModelProperty<>();
    private static final List<String> VARIABLE_BODIES=List.of("essentia_tube","restricted_essentia_tube","directional_essentia_tube");
    private static ModelResourceLocation body(String id){return ModelResourceLocation.standalone(ResourceLocation.parse("thaumcraft:block/"+id+"_connected_body"));}
    private static ModelResourceLocation arm(String id,Direction direction){return ModelResourceLocation.standalone(ResourceLocation.parse("thaumcraft:block/"+id+"_"+direction.getName()));}
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event){for(var id:IDS)for(var direction:Direction.values())event.register(arm(id,direction));for(var id:VARIABLE_BODIES)event.register(body(id));}
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event){for(var name:IDS){
        var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.parse("thaumcraft:"+name));var arms=new EnumMap<Direction,List<BakedQuad>>(Direction.class);
        var connected=VARIABLE_BODIES.contains(name)?Objects.requireNonNull(event.getModels().get(body(name))):null;
        for(var direction:Direction.values()){var model=Objects.requireNonNull(event.getModels().get(arm(name,direction)));arms.put(direction,List.copyOf(model.getQuads(null,null,RandomSource.create())));}
        for(var state:block.getStateDefinition().getPossibleStates()){var key=BlockModelShaper.stateToModelLocation(state);var base=Objects.requireNonNull(event.getModels().get(key));event.getModels().put(key,new BakedModelWrapper<BakedModel>(base){
            @Override public ModelData getModelData(BlockAndTintGetter world,BlockPos pos,BlockState state,ModelData original){int mask=0;for(var direction:Direction.values()){var property=net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(direction);if(state.hasProperty(property)&&state.getValue(property)){mask|=1<<direction.ordinal();var neighbor=world.getBlockEntity(pos.relative(direction));if(!(neighbor instanceof thaumcraft.production.TransportBlockEntity transport)||!transport.tube())mask|=64;}}return original.derive().with(CONNECTIONS,mask).build();}
            @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType type){Integer mask=data.get(CONNECTIONS);var selected=mask!=null&&(mask&63)!=0&&(mask&64)==0&&connected!=null?connected:base;var quads=selected.getQuads(state,side,random,data,type);if(side!=null||mask==null||mask==0)return quads;var result=new ArrayList<>(quads);for(var direction:Direction.values())if((mask&(1<<direction.ordinal()))!=0)result.addAll(arms.get(direction));return result;}
        });}
    }}
    private EssentiaTubeModels(){}
}
