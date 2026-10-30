package thaumcraft;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import thaumcraft.config.ThaumcraftConfig;
import thaumcraft.registry.ThaumcraftContent;

@Mod(Thaumcraft.MOD_ID)
public final class Thaumcraft {
    public static final String MOD_ID = "thaumcraft_aop";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Thaumcraft(IEventBus modBus, ModContainer container) {
        thaumcraft.addon.ApiBootstrap.install();
        ThaumcraftContent.register(modBus);
        thaumcraft.registry.R2Content.register(modBus);
        thaumcraft.crafting.R2Recipes.register(modBus);
        thaumcraft.registry.R3Content.register(modBus);
        thaumcraft.registry.WandComponents.register(modBus);
        thaumcraft.registry.R3Equipment.register(modBus);
        thaumcraft.registry.R4Content.register(modBus);
        thaumcraft.registry.R5Content.register(modBus);
        thaumcraft.registry.SpawnEggs.register(modBus);
        thaumcraft.fluids.R4Fluids.register(modBus);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event)->event.enqueueWork(thaumcraft.items.WandMaterials::initialize));
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event)->event.enqueueWork(thaumcraft.loot.LegacyLoot::initialize));
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event)->event.enqueueWork(thaumcraft.addon.ApiBootstrap::registerAddons));
        thaumcraft.crafting.R3Recipes.register(modBus);
        thaumcraft.entities.R2Entities.register(modBus);
        thaumcraft.entities.R3Entities.register(modBus);
        thaumcraft.entities.R5Entities.register(modBus);
        thaumcraft.entities.ChampionSystem.enabled=()->ThaumcraftConfig.CHAMPION_MOBS.get();
        thaumcraft.entities.ChampionSystem.register(modBus);
        thaumcraft.entities.BiomeSpawns.register(modBus);
        thaumcraft.registry.R2Sounds.register(modBus);
        thaumcraft.effects.TaintEffects.register(modBus);
        thaumcraft.effects.OwnedParticles.register(modBus);
        thaumcraft.effects.WarpEffects.register(modBus);
        thaumcraft.fluids.R2Fluids.register(modBus);
        thaumcraft.world.R2Worldgen.register(modBus);
        thaumcraft.world.R3Worldgen.register(modBus);
        thaumcraft.world.NativeStructures.register(modBus);
        thaumcraft.world.dim.R5Worldgen.register(modBus);
        modBus.addListener(thaumcraft.network.ThaumcraftNetwork::register);
        thaumcraft.knowledge.KnowledgeService.register();
        thaumcraft.knowledge.InventoryScanService.register();
        container.registerConfig(ModConfig.Type.STARTUP, thaumcraft.config.ThaumcraftContentConfig.SPEC, "thaumcraft-startup.toml");
        container.registerConfig(ModConfig.Type.SERVER, ThaumcraftConfig.SPEC, "thaumcraft-server.toml");
        container.registerConfig(ModConfig.Type.CLIENT, thaumcraft.config.ThaumcraftClientConfig.SPEC, "thaumcraft-client.toml");
        aop.ui.api.SettingsRegistry.register("thaumcraft_aop", "thaumcraft", "Thaumcraft", aop.ui.api.UiTheme.THAUMCRAFT);
        thaumcraft.data.ThaumcraftData.register();
        thaumcraft.world.WorldBiomes.register();
        thaumcraft.world.NaturalBiomeIntegration.register(modBus);
        thaumcraft.world.NaturalPopulation.register(modBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent event)->thaumcraft.command.ThaumcraftCommands.register(event.getDispatcher()));
        LOGGER.info("Thaumcraft 4 initialized");
    }
}
