package thaumcraft.api.crafting;

import java.util.OptionalInt;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Neutral matrix inspection; never advances or starts an infusion ritual. */
public interface InfusionMatrixProbe {
    /** Pure type check, readable on either logical side. */
    boolean isMatrix(BlockEntity tile);

    /**
     * Server game thread only. Recomputes surroundings and returns current symmetry
     * only for an accessible, loaded, active matrix outside crafting. Invalid players,
     * stale tiles, other levels, denied access and distance >=8 blocks return empty.
     * Does not consume items, essentia or Vis and does not run a ritual tick.
     */
    OptionalInt requestSymmetry(ServerPlayer player, BlockEntity tile);
}
