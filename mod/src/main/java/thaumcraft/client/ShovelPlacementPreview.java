package thaumcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class ShovelPlacementPreview {
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||!mc.player.isShiftKeyDown()||!mc.player.getMainHandItem().is(thaumcraft.registry.R3Equipment.TOOLS.get("elemental_shovel").get())||!(mc.hitResult instanceof BlockHitResult hit)||hit.getType()!=HitResult.Type.BLOCK||mc.level.getBlockEntity(hit.getBlockPos())!=null)return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        var out=buffers.getBuffer(RenderType.lines());for(var pos:thaumcraft.items.ToolActions.placementPositions(hit.getBlockPos(),hit.getDirection(),mc.player.getDirection(),mc.player.getMainHandItem()))if(mc.level.getBlockState(pos).canBeReplaced())LevelRenderer.renderLineBox(pose,out,new AABB(pos).inflate(.002),.5f,.8f,1,.65f);
        pose.popPose();buffers.endBatch(RenderType.lines());
    }
    private ShovelPlacementPreview(){}
}
