package thaumcraft.addon;

import java.util.OptionalInt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import thaumcraft.api.crafting.InfusionMatrixProbe;
import thaumcraft.production.InfusionMatrixBlockEntity;

final class NativeInfusionMatrixProbe implements InfusionMatrixProbe {
    @Override
    public boolean isMatrix(BlockEntity tile) {
        return tile instanceof InfusionMatrixBlockEntity;
    }

    @Override
    public OptionalInt requestSymmetry(ServerPlayer player, BlockEntity tile) {
        if (player == null || !player.server.isSameThread())
            throw new IllegalStateException("Matrix inspection requires its server game thread");
        if (!(tile instanceof InfusionMatrixBlockEntity matrix)
                || !(tile.getLevel() instanceof ServerLevel level) || level != player.level()
                || player instanceof FakePlayer || !player.isAlive() || player.isRemoved() || player.isSpectator()
                || level.getEntity(player.getUUID()) != player || matrix.isRemoved()
                || !level.hasChunkAt(matrix.getBlockPos()) || level.getBlockEntity(matrix.getBlockPos()) != matrix
                || player.distanceToSqr(Vec3.atCenterOf(matrix.getBlockPos())) >= 64
                || !level.mayInteract(player, matrix.getBlockPos()) || !matrix.active || matrix.crafting)
            return OptionalInt.empty();
        matrix.getSurroundings();
        matrix.setChanged();
        var state = matrix.getBlockState();
        level.sendBlockUpdated(matrix.getBlockPos(), state, state, 2);
        return OptionalInt.of(matrix.symmetry);
    }
}
