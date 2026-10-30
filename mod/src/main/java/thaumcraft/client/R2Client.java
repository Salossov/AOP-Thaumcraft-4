package thaumcraft.client;

import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.*;
import thaumcraft.fluids.R2Fluids;
import thaumcraft.registry.R2Content;

@EventBusSubscriber(modid="thaumcraft_aop",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class R2Client {
    @SubscribeEvent public static void tooltips(RegisterClientTooltipComponentFactoriesEvent event){event.register(AspectTooltipData.class,AspectTooltipRenderer::new);event.register(thaumcraft.items.WandVisTooltip.class,WandVisTooltipRenderer::new);}
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event){event.register(TableDetailsRenderer.QUILL);}
    @SubscribeEvent public static void entityLayers(EntityRenderersEvent.RegisterLayerDefinitions event){R2EntityRenderers.layers(event);event.registerLayerDefinition(TableDetailsRenderer.LAYER,TableDetailsRenderer::layer);}
    @SubscribeEvent public static void entityRenderers(EntityRenderersEvent.RegisterRenderers event){R2EntityRenderers.register(event);event.registerBlockEntityRenderer(R2Content.NODE_ENTITY.get(),AuraNodeRenderer::new);event.registerBlockEntityRenderer(R2Content.TABLE_ENTITY.get(),TableDetailsRenderer::new);}
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->GuardianFogPostProcessor.reload());}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(R2Content.RESEARCH_MENU.get(),ResearchScreen::new);
        event.register(R2Content.ARCANE_MENU.get(),ArcaneScreen::new);
        event.register(R2Content.BOOK_MENU.get(),ThaumonomiconScreen::new);
    }
    @SubscribeEvent public static void fluidExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new WandClientEffects(),R2Content.WAND.get());
        for(boolean gas:new boolean[]{false,true}) {
            var texture=ResourceLocation.fromNamespaceAndPath("thaumcraft","blocks/"+(gas?"fluxgas":"fluxgoo"));
            event.registerFluidType(new IClientFluidTypeExtensions() {
                @Override public ResourceLocation getStillTexture() {return texture;}
                @Override public ResourceLocation getFlowingTexture() {return texture;}
                @Override public boolean renderFluid(net.minecraft.world.level.material.FluidState fluid,net.minecraft.world.level.BlockAndTintGetter level,net.minecraft.core.BlockPos pos,com.mojang.blaze3d.vertex.VertexConsumer vertices,net.minecraft.world.level.block.state.BlockState block){return gas&&FluxGasGeometry.render(fluid,level,pos,vertices);}
            },gas?R2Fluids.GAS_TYPE.get():R2Fluids.GOO_TYPE.get());
        }
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {event.enqueueWork(()->{
        thaumcraft.api.client.BookNavigation.install(key->net.neoforged.neoforge.network.PacketDistributor.sendToServer(new thaumcraft.network.BookOpenPayload(key)));
        FallingTaintEffects.install();
        FollowingItemEffects.install();
        net.minecraft.client.renderer.item.ItemProperties.register(R2Content.NOTES.get(),ResourceLocation.parse("thaumcraft:complete"),(stack,level,entity,seed)->{var note=thaumcraft.items.ResearchNotes.read(stack);return note!=null&&note.complete?1:0;});
        // Config screen factory is supplied by AOP: UI for all registered providers.
        ItemBlockRenderTypes.setRenderLayer(R2Fluids.GOO.get(),RenderType.translucent());
        ItemBlockRenderTypes.setRenderLayer(R2Fluids.GAS.get(),RenderType.translucent());
        for(var block:java.util.List.of(R2Content.TAINT_GROWTH,R2Content.TAINT_TENDRILS,R2Content.TAINT_SPORE_STALK)) ItemBlockRenderTypes.setRenderLayer(block.get(),RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(R2Content.TAINT_FIBRES.get(),RenderType.translucent());
    });}
    @SubscribeEvent public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack,tint)->{if(tint!=1)return 0xffffffff;var note=thaumcraft.items.ResearchNotes.read(stack);if(note==null)return 0xff999999;return note.grid.values().stream().filter(e->e.type()==1).map(e->thaumcraft.api.aspects.Aspect.getAspect(e.aspect())).filter(java.util.Objects::nonNull).findFirst().map(a->0xff000000|a.getColor()).orElse(0xff999999);},R2Content.NOTES.get());
        // Tint by element NAME, not by index: ELEMENTS order (air,fire,water,earth,order,entropy)
        // differs from Aspects.PRIMALS order (aer,ignis,terra,aqua,ordo,perditio); indexing by
        // position swapped water and earth shards.
        for(int i=0;i<6;i++) {int color=elementColor(R2Content.ELEMENTS[i]);event.register((stack,tint)->tint==0?(0xff000000|color):0xffffffff,R2Content.SHARDS.get(i).get(),R2Content.INFUSED_STONE.get(i).get().asItem());}
        event.register((stack,tint)->{var aspects=thaumcraft.items.WispEssenceItem.aspects(stack).getAspectsSorted();return aspects.length==0?0xffffffff:(0xff000000|aspects[0].getColor());},R2Content.WISP_ESSENCE.get());
    }
    @SubscribeEvent public static void blockColors(RegisterColorHandlersEvent.Block event){for(int i=0;i<6;i++){int color=elementColor(R2Content.ELEMENTS[i]);event.register((state,level,pos,tint)->tint==0?color:0xffffffff,R2Content.INFUSED_STONE.get(i).get());}}
    private static int elementColor(String element){var aspect=switch(element){case "air"->thaumcraft.api.aspects.Aspects.AIR;case "fire"->thaumcraft.api.aspects.Aspects.FIRE;case "water"->thaumcraft.api.aspects.Aspects.WATER;case "earth"->thaumcraft.api.aspects.Aspects.EARTH;case "order"->thaumcraft.api.aspects.Aspects.ORDER;default->thaumcraft.api.aspects.Aspects.ENTROPY;};return aspect.getColor();}
    private R2Client() {}
}
