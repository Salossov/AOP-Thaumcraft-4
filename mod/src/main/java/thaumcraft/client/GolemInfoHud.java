package thaumcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import thaumcraft.entities.ThaumicGolem;
import thaumcraft.items.GolemBellItem;
import thaumcraft.items.GolemCoreItem;
import thaumcraft.registry.R3Equipment;

/** Inspection shares the goggles/thaumometer reveal rule, with the bell as a tool for configuring golems. */
@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class GolemInfoHud {
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.options.hideGui||mc.screen!=null||!(mc.hitResult instanceof EntityHitResult hit)||!(hit.getEntity() instanceof ThaumicGolem golem)||!AuraNodeRenderer.revealed()&&!(mc.player.getMainHandItem().getItem() instanceof GolemBellItem))return;
        var items=new java.util.ArrayList<ItemStack>();if(golem.core()>=0&&golem.core()<GolemCoreItem.Kind.values().length)items.add(new ItemStack(R3Equipment.GOLEM_CORES.get(GolemCoreItem.Kind.values()[golem.core()]).get()));
        for(int kind=0;kind<6;kind++)for(int i=0;i<golem.upgrade(kind);i++)items.add(new ItemStack(R3Equipment.GOLEM_UPGRADES.get(kind).get()));
        for(var entry:R3Equipment.GOLEM_DECORATIONS)if(golem.decoration().contains(entry.get().decoration))items.add(new ItemStack(entry.get()));
        if(items.isEmpty())return;var pose=WorldOverlayPose.create(event);var camera=event.getCamera().getPosition();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);var position=golem.getPosition(partial);
        // Vanilla places custom names at almost the same height as the inspection icons.
        pose.pushPose();pose.translate(position.x-camera.x,position.y+golem.getBbHeight()+.4+(golem.hasCustomName()?.35:0)-camera.y,position.z-camera.z);pose.mulPose(event.getCamera().rotation());
        var buffers=mc.renderBuffers().bufferSource();for(int i=0;i<items.size();i++){int row=i/5,count=Math.min(5,items.size()-row*5);pose.pushPose();pose.translate((i%5-(count-1)/2f)*.32,row*.34,0);pose.scale(.28f,.28f,.28f);mc.getItemRenderer().renderStatic(items.get(i),net.minecraft.world.item.ItemDisplayContext.GUI,net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,pose,buffers,mc.level,golem.getId());pose.popPose();}buffers.endBatch();pose.popPose();
    }
    private GolemInfoHud(){}
}
