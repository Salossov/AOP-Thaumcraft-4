package thaumcraft.api.wands;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
public final class StaffRod extends WandRod {
    private boolean runes;
    public StaffRod(String tag,int capacity,Supplier<ItemStack> item,int craftCost,java.util.function.BiConsumer<ItemStack,Player> update){
        super(tag+"_staff",capacity,item,craftCost,update);
    }
    public StaffRod(ResourceLocation id, String research, int capacity, Supplier<ItemStack> item, int craftCost, java.util.function.BiConsumer<ItemStack, Player> update) {
        super(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_staff"), research, capacity, item, craftCost, update);
    }
    public boolean hasRunes(){return runes;}
    public void setRunes(boolean value){runes=value;}
}
