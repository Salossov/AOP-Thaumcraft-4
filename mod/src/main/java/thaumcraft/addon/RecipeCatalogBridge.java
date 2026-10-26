package thaumcraft.addon;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.*;
import thaumcraft.api.crafting.*;
import thaumcraft.api.wands.*;
import thaumcraft.crafting.*;
import thaumcraft.registry.*;

/** One effective catalog shared by viewers and the authoritative transfer handler. */
public final class RecipeCatalogBridge {
    public static void install(){RecipeDisplays.register(ResourceLocation.parse("thaumcraft:standard"),RecipeCatalogBridge::collect);}
    private static ResourceLocation child(ResourceLocation id,String suffix){return id.withPath(id.getPath()+"/"+suffix.replace(':','/'));}
    private static ResourceLocation synthetic(String kind,ResourceLocation item){return ResourceLocation.fromNamespaceAndPath("thaumcraft","viewer/"+kind+"/"+item.getNamespace()+"/"+item.getPath());}
    private static List<String> research(String key){return key.isEmpty()?List.of():List.of(key);}
    private static Map<String,Integer> centivis(Map<String,Integer> cost){var result=new LinkedHashMap<String,Integer>();cost.forEach((a,n)->result.put(a,Math.multiplyExact(n,100)));return result;}
    private static RecipeDisplay display(ResourceLocation id,ResourceLocation recipe,RecipeDisplay.Kind kind,List<String> research,List<Ingredient> inputs,List<ItemStack> outputs,Map<String,Integer> in,Map<String,Integer> out,Map<String,Integer> vis,int instability,int xp,String device,List<Component> notes){
        return new RecipeDisplay(id,recipe,kind,research,inputs,outputs,in,out,vis,instability,xp,List.of(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("thaumcraft:"+device)))),notes);
    }
    private static Collection<RecipeDisplay> collect(Level level){
        var result=new ArrayList<RecipeDisplay>();var manager=level.getRecipeManager();
        // Legacy maps also contain aliases for addon parts. Only canonical entries
        // create display variants; aliases must not duplicate the same recipe ID.
        var rods=WandRod.rods.entrySet().stream().filter(e->e.getKey().equals(e.getValue().getTag())).map(Map.Entry::getValue).toList();
        var caps=WandCap.caps.entrySet().stream().filter(e->e.getKey().equals(e.getValue().getTag())).map(Map.Entry::getValue).toList();
        for(var holder:manager.getAllRecipesFor(R2Recipes.ARCANE_TYPE.get())){
            var r=holder.value();
            if(r instanceof ArcaneWandRecipe){
                for(var rod:rods)for(var cap:caps)for(boolean sceptre:new boolean[]{false,true}){
                    var items=new ArrayList<ItemStack>(Collections.nCopies(9,ItemStack.EMPTY));items.set(4,rod.getItem());items.set(6,cap.getItem());
                    if(sceptre){items.set(1,cap.getItem());items.set(2,new ItemStack(R3Equipment.PRIMAL_CHARM.get()));items.set(7,cap.getItem());}else items.set(2,cap.getItem());
                    var input=CraftingInput.of(3,3,items);if(!r.matches(input,level))continue;
                    var keys=new ArrayList<>(List.of(rod.getResearch(),cap.getResearch()));if(sceptre)keys.add("SCEPTRE");
                    result.add(display(child(holder.id(),rod.getTag()+"/"+cap.getTag()+"/"+sceptre),holder.id(),RecipeDisplay.Kind.ARCANE,keys,items.stream().map(s->s.isEmpty()?Ingredient.EMPTY:Ingredient.of(s)).toList(),List.of(r.assemble(input,level.registryAccess())),Map.of(),Map.of(),centivis(r.vis(input)),0,0,"arcane_workbench",List.of()));
                }continue;
            }
            var inputs=new ArrayList<Ingredient>(Collections.nCopies(9,Ingredient.EMPTY));
            if(r instanceof ArcaneRecipe shaped){for(int y=0;y<shaped.pattern().height();y++)for(int x=0;x<shaped.pattern().width();x++)inputs.set(y*3+x,shaped.getIngredients().get(y*shaped.pattern().width()+x));}
            else for(int i=0;i<Math.min(9,r.getIngredients().size());i++)inputs.set(i,r.getIngredients().get(i));
            var output=r.getResultItem(level.registryAccess());if(!output.isEmpty())result.add(display(holder.id(),holder.id(),RecipeDisplay.Kind.ARCANE,research(r.research()),inputs,List.of(output),Map.of(),Map.of(),centivis(r.vis()),0,0,"arcane_workbench",List.of()));
        }
        for(var h:manager.getAllRecipesFor(R3Recipes.CRUCIBLE_TYPE.get())){var r=h.value();result.add(display(h.id(),h.id(),RecipeDisplay.Kind.CRUCIBLE,research(r.research()),List.of(r.catalyst()),List.of(r.getRecipeOutput()),r.aspects(),Map.of(),Map.of(),0,0,"crucible",List.of()));}
        for(var h:manager.getAllRecipesFor(R3Recipes.INFUSION_TYPE.get())){
            var r=h.value();var samples=new ArrayList<ItemStack>();
            if(r instanceof InfusionRunicRecipe){for(var item:BuiltInRegistries.ITEM)if(item instanceof thaumcraft.api.IRunicArmor){var stack=new ItemStack(item);for(int n=0;n<5;n++){samples.add(stack.copy());thaumcraft.equipment.RunicShield.harden(stack);}}}
            else if(r instanceof InfusionEnchantmentRecipeData enchantment){for(var item:BuiltInRegistries.ITEM){var stack=new ItemStack(item);if(stack.isDamageableItem()&&enchantment.enchantment().value().canEnchant(stack)){for(int n=0;n<enchantment.enchantment().value().getMaxLevel();n++){var sample=stack.copy();final int rank=n;net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(sample,m->m.set(enchantment.enchantment(),rank));samples.add(sample);}}}}
            else samples.addAll(Arrays.asList(r.input().getItems()));
            int variant=0;for(var central:samples){if(central.isEmpty())continue;var inputs=new ArrayList<Ingredient>();inputs.add(Ingredient.of(central));inputs.addAll(r.components(central));var output=r.getRecipeOutput(central);if(output.isEmpty())continue;
                List<Component> notes=(r instanceof InfusionRunicRecipe||r instanceof InfusionEnchantmentRecipeData)?List.of(Component.translatable("thaumcraft.api.dynamic_infusion")):List.<Component>of();
                result.add(display(child(h.id(),"variant_"+variant++),h.id(),RecipeDisplay.Kind.INFUSION,research(r.research()),inputs,List.of(output),r.aspects(central),Map.of(),Map.of(),r.instability(central),r.experienceCost(central),"infusion_matrix",notes));}
        }
        for(var h:manager.getAllRecipesFor(R3Recipes.INFERNAL_BONUS_TYPE.get())){var r=h.value();result.add(display(h.id(),h.id(),RecipeDisplay.Kind.INFERNAL,List.of("INFERNALFURNACE"),List.of(r.ingredient()),List.of(r.result().copyWithCount(Math.max(1,r.baseCount()))),Map.of(),Map.of(),Map.of(),0,0,"infernal_furnace",List.of(Component.translatable("thaumcraft.api.infernal_bonus",r.baseCount()))));}
        for(var item:BuiltInRegistries.ITEM){var stack=new ItemStack(item);if(stack.isEmpty())continue;var aspects=AspectApi.aspects(level,stack);if(!aspects.isEmpty())result.add(display(synthetic("distillation",BuiltInRegistries.ITEM.getKey(item)),null,RecipeDisplay.Kind.DISTILLATION,List.of("COREALCHEMY"),List.of(Ingredient.of(stack)),List.of(),Map.of(),aspects,Map.of(),0,0,"alchemy_furnace",List.of()));}
        for(var aspect:Aspect.all().values()){
            var components=aspect.getComponents();if(components!=null&&components.size()==2){var out=new LinkedHashMap<String,Integer>();for(var a:components)out.merge(a.getTag(),1,Integer::sum);result.add(display(synthetic("centrifuge",ResourceLocation.parse(aspect.getTag().contains(":")?aspect.getTag():"thaumcraft:"+aspect.getTag())),null,RecipeDisplay.Kind.CENTRIFUGE,List.of("CENTRIFUGE"),List.of(),List.of(),Map.of(aspect.getTag(),1),out,Map.of(),0,0,"alchemical_centrifuge",List.of()));}
            var crystal=new ItemStack(R3Content.ESSENTIA_CRYSTAL.get());R3Content.setAspect(crystal,aspect);
            result.add(display(synthetic("crystallization",ResourceLocation.parse(aspect.getTag().contains(":")?aspect.getTag():"thaumcraft:"+aspect.getTag())),null,RecipeDisplay.Kind.CRYSTALLIZATION,List.of("ESSENTIACRYSTAL"),List.of(),List.of(crystal),Map.of(aspect.getTag(),1),Map.of(),Map.of(),0,0,"essentia_crystallizer",List.of()));
        }
        return result;
    }
    private RecipeCatalogBridge(){}
}
