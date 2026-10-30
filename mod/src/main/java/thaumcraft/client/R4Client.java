package thaumcraft.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.*;
import thaumcraft.fluids.R4Fluids;

@EventBusSubscriber(modid="thaumcraft_aop",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class R4Client {
    @SubscribeEvent public static void candleBlockColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block e){e.register((state,level,pos,tint)->tint==0?0xff000000|((thaumcraft.blocks.TallowCandleBlock)state.getBlock()).color:-1,thaumcraft.registry.R4Content.CANDLES.stream().map(java.util.function.Supplier::get).toArray(net.minecraft.world.level.block.Block[]::new));}
    @SubscribeEvent public static void candleItemColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item e){e.register((stack,tint)->tint==0?0xff000000|((thaumcraft.blocks.TallowCandleBlock)((net.minecraft.world.item.BlockItem)stack.getItem()).getBlock()).color:-1,thaumcraft.registry.R4Content.CANDLES.stream().map(b->b.get().asItem()).toArray(net.minecraft.world.item.Item[]::new));}
    @SubscribeEvent public static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(thaumcraft.registry.R4Content.BANNER_ENTITY.get(),ThaumicBannerRenderer::new);e.registerBlockEntityRenderer(thaumcraft.registry.R4Content.ADVANCED_FURNACE_ENTITY.get(),AdvancedAlchemyRenderer::new);e.registerBlockEntityRenderer(thaumcraft.registry.R4Content.BLOOM_ENTITY.get(),EtherealBloomRenderer::new);e.registerBlockEntityRenderer(thaumcraft.registry.R4Content.SCRUBBER_ENTITY.get(),FluxScrubberRenderer::new);}
    @SubscribeEvent public static void itemRenderer(RegisterClientExtensionsEvent e){e.registerItem(new ThaumicBannerRenderer.Extension(),thaumcraft.registry.R4Content.BANNER_ITEM.get());e.registerItem(new FluxScrubberRenderer.Extension(),thaumcraft.registry.R4Content.SCRUBBER_ITEM.get());}
    @SubscribeEvent public static void deconstructionRenderer(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(thaumcraft.registry.R4Content.DECONSTRUCTOR_ENTITY.get(),DeconstructionRenderer::new);}
    @SubscribeEvent public static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent e){e.register(thaumcraft.registry.R4Content.DECONSTRUCTOR_MENU.get(),DeconstructionScreen::new);}
    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent e){for(boolean death:new boolean[]{false,true}){var texture=ResourceLocation.fromNamespaceAndPath("thaumcraft","blocks/"+(death?"fluiddeath":"fluidpure"));e.registerFluidType(new IClientFluidTypeExtensions(){@Override public ResourceLocation getStillTexture(){return texture;}@Override public ResourceLocation getFlowingTexture(){return texture;}},death?R4Fluids.DEATH_TYPE.get():R4Fluids.PURE_TYPE.get());}}
    @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->{thaumcraft.production.AdvancedAlchemyBlock.bubbles=AdvancedAlchemyRenderer::bubbles;ItemBlockRenderTypes.setRenderLayer(R4Fluids.PURE.get(),RenderType.translucent());ItemBlockRenderTypes.setRenderLayer(R4Fluids.PURE_FLOWING.get(),RenderType.translucent());ItemBlockRenderTypes.setRenderLayer(R4Fluids.DEATH.get(),RenderType.translucent());});}
    private R4Client(){}
    @SubscribeEvent public static void spaScreen(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent e){e.register(thaumcraft.registry.R4Content.SPA_MENU.get(),ArcaneSpaScreen::new);}
    @SubscribeEvent public static void liftParticles(FMLClientSetupEvent e){e.enqueueWork(()->{
        thaumcraft.blocks.LevitatorBlock.sparkle=LegacyParticles::liftSparkle;
        thaumcraft.blocks.PavingStoneBlock.particleCount=LegacyParticles::particleCount;
        thaumcraft.blocks.PavingStoneBlock.sparkles=(pos,count)->LegacyParticles.blockSparkles(pos,32768,count);
        thaumcraft.blocks.PavingStoneBlock.runes=LegacyParticles::blockRune;
    });}
}
