package thaumcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import thaumcraft.network.*;

@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class BiomeColumnClientEvents {
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event) { ClientBiomeColumns.listener(BiomeColumnClientEvents::changed); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ClientBiomeColumns.clear();ClientKnowledge.clear(); }
    private static void changed(BiomeColumnsPayload packet) {
        var minecraft=Minecraft.getInstance();var level=minecraft.level;if(level==null) return;
        if(packet.reset()) { level.clearTintCaches();minecraft.levelRenderer.allChanged();return; }
        if(!level.dimension().location().equals(packet.dimension())) return;
        level.onChunkLoaded(new ChunkPos(packet.x(),packet.z()));
        for(int y=level.getMinSection();y<level.getMaxSection();y++) minecraft.levelRenderer.setSectionDirtyWithNeighbors(packet.x(),y,packet.z());
    }
    private BiomeColumnClientEvents() {}
}
