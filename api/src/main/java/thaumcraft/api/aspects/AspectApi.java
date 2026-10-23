package thaumcraft.api.aspects;

import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

public final class AspectApi {
    public interface Access {Map<String,Integer> aspects(Level level,ItemStack stack);boolean scanned(ItemStack stack);
        default boolean scanned(ServerPlayer player,ItemStack stack){return false;}
        default List<ItemStack> variants(){return List.of();}
        default ItemStack wispEssence(Aspect aspect){return ItemStack.EMPTY;}
        default Map<String,Integer> aspects(Level level,net.minecraft.world.level.block.state.BlockState state){return Map.of();}
        default Map<String,Integer> aspects(net.minecraft.world.entity.Entity entity){return Map.of();}}
    private static Access access;
    public static void install(Access value){if(access!=null)throw new IllegalStateException("Aspect API already installed");access=Objects.requireNonNull(value);}
    public static Map<String,Integer> aspects(Level level,ItemStack stack){return Map.copyOf(access.aspects(level,stack.copy()));}
    public static Map<String,Integer> aspects(Level level,net.minecraft.world.level.block.state.BlockState state){return Map.copyOf(access.aspects(level,state));}
    public static Map<String,Integer> aspects(net.minecraft.world.entity.Entity entity){return Map.copyOf(access.aspects(entity));}
    /** Filled phials, jars, crystals and essence pieces; copies safe for viewer indexing. */
    public static List<ItemStack> variants(){return access==null?List.of():access.variants().stream().map(ItemStack::copy).toList();}
    /** Fresh native two-unit wisp essence, using the core's real stack components. */
    public static ItemStack wispEssence(Aspect aspect){return access==null?ItemStack.EMPTY:access.wispEssence(Objects.requireNonNull(aspect)).copy();}
    public static boolean scanned(ItemStack stack){return access!=null&&access.scanned(stack.copy());}
    /** Read-only native object-scan query for this player, including variants and scan groups.
     * Must run on the player's server thread. The adapter receives an independent stack copy.
     */
    public static boolean scanned(ServerPlayer player,ItemStack stack){
        Objects.requireNonNull(player);Objects.requireNonNull(stack);
        if(!player.server.isSameThread())throw new IllegalStateException("Server thread required");
        if(access==null)throw new IllegalStateException("Aspect API not installed");
        return !stack.isEmpty()&&access.scanned(player,stack.copy());
    }
    private AspectApi(){}
}
