package thaumcraft.api.wands;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
public final class WandCap {
    public static final Map<String,WandCap> caps=new LinkedHashMap<>();
    private final String tag;
    private String research;
    private final float modifier,specialModifier;
    private final int craftCost;
    private final Set<Aspect> special;
    private final Supplier<ItemStack> item;
    public WandCap(String tag,float modifier,Set<Aspect> special,float specialModifier,Supplier<ItemStack> item,int cost){
        if(cost<1||modifier<0.0f||caps.containsKey(tag))throw new IllegalArgumentException("Invalid or duplicate cap "+tag);
        this.tag=tag;this.modifier=modifier;this.special=Set.copyOf(special);this.specialModifier=specialModifier;this.item=item;craftCost=cost;caps.put(tag,this);
    }
    public WandCap(net.minecraft.resources.ResourceLocation id,String research,float modifier,Set<Aspect> special,float specialModifier,Supplier<ItemStack> item,int cost){this(id.toString(),modifier,special,specialModifier,item,cost);this.research=java.util.Objects.requireNonNull(research);}
    public String getTag(){return tag;}
    public int getCraftCost(){return craftCost;}
    public ItemStack getItem(){return item.get();}
    public String getResearch(){return research==null?"CAP_"+tag:research;}
    public float getBaseCostModifier(){return modifier;}
    public Set<Aspect> getSpecialCostModifierAspects(){return special;}
    public float getSpecialCostModifier(){return specialModifier;}
    public float modifier(Aspect aspect){return special.contains(aspect)?specialModifier:modifier;}
    public ResourceLocation getTexture(){return tag.contains(":")?ResourceLocation.fromNamespaceAndPath(ResourceLocation.parse(tag).getNamespace(),"textures/models/wand_cap_"+ResourceLocation.parse(tag).getPath().replace("_staff","")+".png"):ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/models/wand_cap_"+tag+".png");}
}
