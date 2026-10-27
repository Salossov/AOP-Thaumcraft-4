package thaumcraft.crafting;
import java.util.*;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import thaumcraft.api.wands.*;
import thaumcraft.api.aspects.Aspects;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.registry.*;
import thaumcraft.items.WandMaterials;

/** TC4's exact diagonal assembly; authorization is checked by the arcane menu. */
public final class ArcaneWandRecipe implements IArcaneRecipe {
    private final boolean sceptre;
    public ArcaneWandRecipe(boolean sceptre){this.sceptre=sceptre;}
    private record Parts(WandRod rod,WandCap cap){}
    private Parts find(CraftingInput input){
        if(input.width()!=3||input.height()!=3)return null;
        int[] occupied=sceptre?new int[]{1,2,4,5,6}:new int[]{2,4,6};
        for(int i=0;i<9;i++){boolean needed=false;for(int cell:occupied)if(cell==i)needed=true;if(input.getItem(i).isEmpty()==needed)return null;}
        var capStack=input.getItem(sceptre?1:2);if(!samePart(capStack,input.getItem(6)))return null;
        if(sceptre&&(!samePart(capStack,input.getItem(5))||!input.getItem(2).is(R3Equipment.PRIMAL_CHARM.get())))return null;
        WandCap cap=WandCap.caps.values().stream().filter(c->samePart(capStack,c.getItem())).findFirst().orElse(null);
        WandRod rod=WandRod.rods.values().stream().filter(r->samePart(input.getItem(4),r.getItem())).findFirst().orElse(null);
        if(cap==null||rod==null||!sceptre&&cap.getTag().equals("iron")&&rod.getTag().equals("wood"))return null;
        if(cap.getTag().equals("copper")&&!thaumcraft.config.ThaumcraftConfig.ENABLE_COPPER_CONTENT.get())return null;
        return new Parts(rod,cap);
    }
    private static boolean samePart(ItemStack actual,ItemStack expected){
        // Anvil names did not change TC4 part identity; retain semantic components
        // (including addon variants) rather than treating every stack of an item alike.
        var left=actual.copy();var right=expected.copy();
        left.remove(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        right.remove(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        return ItemStack.isSameItemSameComponents(left,right);
    }
    public boolean matches(CraftingInput input,Level level){return find(input)!=null;}
    public boolean mayCraft(net.minecraft.world.entity.player.Player player,CraftingInput input){
        if(!(player instanceof net.minecraft.server.level.ServerPlayer server))return false;var parts=find(input);if(parts==null)return false;
        var known=thaumcraft.knowledge.KnowledgeService.get(server).completedResearch();
        return known.contains(parts.rod.getResearch())&&known.contains(parts.cap.getResearch())&&(!sceptre||known.contains("SCEPTRE"));
    }
    public ItemStack assemble(CraftingInput input,HolderLookup.Provider registries){var parts=find(input);if(parts==null)return ItemStack.EMPTY;
        var result=new ItemStack(sceptre?R3Equipment.SCEPTRE.get():parts.rod instanceof StaffRod?R3Equipment.STAFF.get():R3Equipment.WAND.get());
        result.set(WandComponents.PARTS.get(),new WandParts(parts.rod.getTag(),parts.cap.getTag()));return result;
    }
    public String research(){return "";}
    public Map<String,Integer> vis(){return Map.of();}
    public Map<String,Integer> vis(CraftingInput input){var parts=find(input);if(parts==null)return Map.of();int cost=(int)(parts.rod.getCraftCost()*parts.cap.getCraftCost()*(sceptre?1.5f:1));
        var result=new LinkedHashMap<String,Integer>();for(var aspect:Aspects.PRIMALS)result.put(aspect.getTag(),cost);return result;
    }
    public ItemStack getResultItem(HolderLookup.Provider registries){return ItemStack.EMPTY;}
    public boolean canCraftInDimensions(int w,int h){return w>=3&&h>=3;}
    public boolean isSpecial(){return true;}
    public RecipeType<?> getType(){return R2Recipes.ARCANE_TYPE.get();}
    public RecipeSerializer<?> getSerializer(){return R3Recipes.WAND_ASSEMBLY.get();}
    public static final class Serializer implements RecipeSerializer<ArcaneWandRecipe>{
        private static final MapCodec<ArcaneWandRecipe> CODEC=Codec.BOOL.optionalFieldOf("sceptre",false).xmap(ArcaneWandRecipe::new,r->r.sceptre);
        private static final StreamCodec<RegistryFriendlyByteBuf,ArcaneWandRecipe> STREAM=new StreamCodec<>(){public ArcaneWandRecipe decode(RegistryFriendlyByteBuf b){return new ArcaneWandRecipe(b.readBoolean());}public void encode(RegistryFriendlyByteBuf b,ArcaneWandRecipe r){b.writeBoolean(r.sceptre);}};
        public MapCodec<ArcaneWandRecipe> codec(){return CODEC;}
        public StreamCodec<RegistryFriendlyByteBuf,ArcaneWandRecipe> streamCodec(){return STREAM;}
    }
}
