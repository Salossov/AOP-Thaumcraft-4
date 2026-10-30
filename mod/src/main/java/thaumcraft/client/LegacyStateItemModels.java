package thaumcraft.client;

import java.util.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

/** Preserve legacy inventory variants carried by the native block-state component. */
@EventBusSubscriber(modid="thaumcraft_aop",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class LegacyStateItemModels {
    private static final Map<String,String> VARIANTS=Map.of("greatwood_slab","greatwood_planks","silverwood_slab","silverwood_planks","arcane_stone_brick_slab","arcane_stone_bricks","golem_fetter","golem_fetter_active","item_grate","item_grate_closed");
    private static ModelResourceLocation variant(String id){return ModelResourceLocation.standalone(ResourceLocation.parse("thaumcraft:item/state_"+id));}
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional e){for(var id:VARIANTS.keySet())e.register(variant(id));}
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult e){for(var id:VARIANTS.keySet()){
        var key=new ModelResourceLocation(ResourceLocation.parse("thaumcraft:"+id),"inventory");var base=e.getModels().get(key);var alternate=e.getModels().get(variant(id));if(base==null||alternate==null)throw new IllegalStateException("Missing state item model: "+id);
        e.getModels().put(key,new BakedModelWrapper<BakedModel>(base){
            private final ItemOverrides overrides=new ItemOverrides(){@Override public BakedModel resolve(BakedModel model,ItemStack stack,ClientLevel level,LivingEntity entity,int seed){var properties=stack.getOrDefault(DataComponents.BLOCK_STATE,BlockItemStateProperties.EMPTY).properties();return "double".equals(properties.get("type"))||"true".equals(properties.get("powered"))||"false".equals(properties.get("open"))?alternate:base;}};
            @Override public ItemOverrides getOverrides(){return overrides;}
        });
    }}
    private LegacyStateItemModels(){}
}
