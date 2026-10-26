package thaumcraft.api.wands;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;

/** Add-ons register rods during common setup, after item registration. */
public class WandRod {
    public static final Map<String, WandRod> rods = new LinkedHashMap<>();
    private final String tag;
    private String research;
    private final int capacity, craftCost;
    private final Supplier<ItemStack> item;
    private final java.util.function.BiConsumer<ItemStack, Player> onUpdate;
    private boolean glowing;
    public WandRod(String tag, int capacity, Supplier<ItemStack> item, int craftCost,
                   java.util.function.BiConsumer<ItemStack, Player> onUpdate) {
        if (capacity < 1 || craftCost < 1 || rods.containsKey(tag)) throw new IllegalArgumentException("Invalid or duplicate rod " + tag);
        this.tag=tag; this.capacity=capacity; this.item=item; this.craftCost=craftCost; this.onUpdate=onUpdate; rods.put(tag,this);
    }
    public WandRod(net.minecraft.resources.ResourceLocation id,String research,int capacity,Supplier<ItemStack> item,int craftCost,java.util.function.BiConsumer<ItemStack,Player> update){this(id.toString(),capacity,item,craftCost,update);this.research=java.util.Objects.requireNonNull(research);}
    public String getTag(){return tag;}
    public int getCapacity(){return capacity;}
    public int getCraftCost(){return craftCost;}
    public ItemStack getItem(){return item.get();}
    public String getResearch(){return research==null?"ROD_"+tag:research;}
    public boolean isGlowing(){return glowing;}
    public void setGlowing(boolean value){glowing=value;}
    public ResourceLocation getTexture(){return tag.contains(":")?ResourceLocation.fromNamespaceAndPath(ResourceLocation.parse(tag).getNamespace(),"textures/models/wand_rod_"+ResourceLocation.parse(tag).getPath().replace("_staff","")+".png"):ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/models/wand_rod_"+tag.replace("_staff", "")+".png");}
    public void onUpdate(ItemStack stack,Player player){if(onUpdate!=null)onUpdate.accept(stack,player);}
}
