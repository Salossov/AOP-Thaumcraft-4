package thaumcraft.entities;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.network.GolemDebugPayload;

@EventBusSubscriber(modid="thaumcraft_aop")
public final class GolemDebugService {
    private static final String FLAG="ThaumcraftGolemDebug";
    public static boolean enabled(ServerPlayer player){return player.getPersistentData().getBoolean(FLAG);}
    public static void enabled(ServerPlayer player,boolean value){
        player.getPersistentData().putBoolean(FLAG,value);send(player,value);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){event.getEntity().getPersistentData().remove(FLAG);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){event.getEntity().getPersistentData().remove(FLAG);}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(event.getServer().getTickCount()%5!=0)return;
        for(var player:event.getServer().getPlayerList().getPlayers())if(enabled(player)){
            if(!player.hasPermissions(2))enabled(player,false);else send(player,true);
        }
    }
    private static void send(ServerPlayer player,boolean enabled){
        var entries=new ArrayList<GolemDebugPayload.Entry>();
        if(enabled)player.serverLevel().getEntitiesOfClass(ThaumicGolem.class,player.getBoundingBox().inflate(32),g->g.isAlive()&&player.distanceToSqr(g)<=32*32).stream()
            .sorted(Comparator.comparingDouble(player::distanceToSqr)).limit(24).forEach(g->entries.add(snapshot(g)));
        PacketDistributor.sendToPlayer(player,new GolemDebugPayload(enabled,player.level().dimension().location(),List.copyOf(entries)));
    }
    public static GolemDebugPayload.Entry snapshot(ThaumicGolem golem){
        var plan=golem.debugPlan();var path=golem.getNavigation().getPath();var nodes=new ArrayList<BlockPos>();
        if(path!=null)for(int i=0;i<Math.min(256,path.getNodeCount());i++)nodes.add(path.getNode(i).asBlockPos());
        int next=path==null?0:Math.min(nodes.size(),path.getNextNodeIndex());
        var markers=golem.markers().stream().filter(m->m.dimension().equals(golem.level().dimension())&&golem.level().hasChunkAt(m.pos())&&golem.home().distSqr(m.pos())<=golem.range()*golem.range()).toList();
        var lines=new ArrayList<Component>();
        lines.add(Component.translatable("thaumcraft.golem.debug.title",golem.getId(),golem.core()>=0&&golem.core()<12?thaumcraft.registry.R3Equipment.GOLEM_CORES.get(thaumcraft.items.GolemCoreItem.Kind.values()[golem.core()]).get().getDescription():golem.getName()));
        lines.add(Component.translatable("thaumcraft.golem.debug.plan",tr("task."+plan.task()),plan.target()==null?Component.literal("—"):coordinates(BlockPos.containing(plan.target()))));
        lines.add(Component.translatable("thaumcraft.golem.debug.status",tr("reason."+plan.reason())));
        if(plan.task().equals("return_home"))lines.add(Component.translatable("thaumcraft.golem.debug.waiting",tr("reason."+plan.waitingReason())));
        Component cargo=golem.essentiaAmount>0&&golem.essentia!=null?Component.literal(golem.essentiaAmount+" ").append(Component.translatable("tc.aspect."+golem.essentia.getTag())):
            !golem.fluid.isEmpty()?Component.literal(golem.fluid.getAmount()+" mB").append(" ").append(golem.fluid.getHoverName()):
            !golem.carried().isEmpty()?Component.literal(golem.carried().getCount()+" × ").append(golem.carried().getHoverName()):tr("empty");
        lines.add(Component.translatable("thaumcraft.golem.debug.cargo",cargo,markers.size(),golem.markers().size()));
        lines.add(Component.translatable("thaumcraft.golem.debug.path",next,nodes.size()));
        lines.add(Component.translatable("thaumcraft.golem.debug.source",coordinates(golem.source()),tr("face."+golem.homeFace().getName())));
        return new GolemDebugPayload.Entry(golem.getId(),golem.getUUID(),golem.home(),golem.source(),plan.target(),List.copyOf(nodes),next,markers.stream().limit(32).map(GolemMarker::pos).toList(),plan.reason(),List.copyOf(lines));
    }
    private static Component coordinates(BlockPos pos){return Component.literal(pos.getX()+", "+pos.getY()+", "+pos.getZ());}
    private static Component tr(String key){return Component.translatable("thaumcraft.golem.debug."+key);}
    private GolemDebugService(){}
}
