package thaumcraft.data;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.*;

/** TC4 recipe decay/output division; explicit object values take priority over derivation. */
final class RecipeAspectResolver {
    record Rule(String inherit,Map<String,Integer> add,Map<String,Integer> merge,List<String> remove) {}
    private final net.minecraft.core.RegistryAccess registries;
    private final Map<String,Rule> rules;
    private final Map<String,Map<String,Integer>> values;
    private final Map<String,Map<String,Integer>> additions;
    private final Map<String,List<Recipe<?>>> normal=new LinkedHashMap<>(),arcane=new LinkedHashMap<>(),crucible=new LinkedHashMap<>(),infusion=new LinkedHashMap<>();
    private final Set<String> path=new HashSet<>();
    private final Set<String> incomplete=new HashSet<>();
    RecipeAspectResolver(MinecraftServer server,Map<String,Map<String,Integer>> explicit,Map<String,Rule> rules) {
        this(server,explicit,rules,Map.of());
    }
    RecipeAspectResolver(MinecraftServer server,Map<String,Map<String,Integer>> explicit,Map<String,Rule> rules,Map<String,Map<String,Integer>> additions) {
        this(server.registryAccess(),server.getRecipeManager().getRecipes(),explicit,rules,additions);
    }
    RecipeAspectResolver(net.minecraft.core.RegistryAccess registries,Collection<RecipeHolder<?>> recipes,Map<String,Map<String,Integer>> explicit,Map<String,Rule> rules,Map<String,Map<String,Integer>> additions) {
        this.registries=registries;this.rules=new LinkedHashMap<>(rules);this.additions=additions;values=new LinkedHashMap<>(explicit);
        for(var holder:recipes.stream().sorted(Comparator.comparing(h->h.id().toString())).toList()) {
            var recipe=holder.value();boolean magic=recipe instanceof thaumcraft.api.crafting.IArcaneRecipe,alchemy=recipe instanceof thaumcraft.api.crafting.CrucibleRecipe;
            boolean ritual=recipe instanceof thaumcraft.api.crafting.InfusionRecipe;
            if(!magic&&!alchemy&&!ritual&&!(recipe instanceof ShapedRecipe)&&!(recipe instanceof ShapelessRecipe))continue;
            var output=recipe.getResultItem(registries);if(output.isEmpty())continue;
            String id=BuiltInRegistries.ITEM.getKey(output.getItem()).toString();
            if(!explicit.containsKey(id))this.rules.putIfAbsent(id,new Rule("",Map.of(),Map.of(),List.of()));
            if(this.rules.containsKey(id))(alchemy?crucible:magic?arcane:ritual?infusion:normal).computeIfAbsent(id,k->new ArrayList<>()).add(recipe);
        }
    }
    Map<String,Map<String,Integer>> resolve() {for(var id:rules.keySet().stream().sorted().toList())value(id);return Map.copyOf(values);}
    private Map<String,Integer> value(String id) {
        var cached=values.get(id);if(cached!=null)return AspectAdditions.apply(cached,additions.getOrDefault(id,Map.of()));
        var rule=rules.get(id);if(rule==null)return AspectAdditions.apply(Map.of(),additions.getOrDefault(id,Map.of()));
        if(path.size()>=100||!path.add(id)){incomplete.addAll(path);return Map.of();}
        try {
            var result=new LinkedHashMap<String,Integer>();
            if(!rule.inherit.isEmpty())result.putAll(value(rule.inherit));
            else {result.putAll(best(crucible.getOrDefault(id,List.of())));if(result.isEmpty())result.putAll(best(arcane.getOrDefault(id,List.of())));if(result.isEmpty())result.putAll(best(infusion.getOrDefault(id,List.of())));if(result.isEmpty())result.putAll(best(normal.getOrDefault(id,List.of())));}
            rule.add.forEach((tag,amount)->result.merge(tag,amount,Math::addExact));rule.merge.forEach((tag,amount)->result.merge(tag,amount,Math::max));rule.remove.forEach(result::remove);
            result.entrySet().removeIf(e->e.getValue()<=0);result.replaceAll((tag,amount)->Math.min(64,amount));
            // A failed branch through a cycle may acquire a seed from another recipe later.
            // Cache genuine empty results, but do not permanently cache that temporary failure.
            if(!result.isEmpty()||!incomplete.contains(id))values.put(id,Collections.unmodifiableMap(new LinkedHashMap<>(result)));
            return AspectAdditions.apply(result,additions.getOrDefault(id,Map.of()));
        } finally {path.remove(id);}
    }
    private Map<String,Integer> best(List<Recipe<?>> recipes) {
        Map<String,Integer> best=Map.of();long smallest=Long.MAX_VALUE;
        for(var recipe:recipes) {
            var sum=new LinkedHashMap<String,Integer>();
            for(var ingredient:recipe.getIngredients()) {
                if(ingredient.isEmpty())continue;
                for(var option:ingredient.getItems()) {
                    var tags=value(BuiltInRegistries.ITEM.getKey(option.getItem()).toString());
                    if(tags.isEmpty())continue;tags.forEach((tag,amount)->sum.merge(tag,amount,Math::addExact));break;
                }
            }
            int count=recipe.getResultItem(registries).getCount();if(count<=0)continue;
            if(!(recipe instanceof thaumcraft.api.crafting.CrucibleRecipe))sum.replaceAll((tag,amount)->(int)(amount*0.75f/count));
            if(recipe instanceof thaumcraft.api.crafting.CrucibleRecipe alchemy)alchemy.aspects().forEach((tag,amount)->sum.merge(tag,(int)(Math.sqrt(amount)/count),Math::addExact));
            if(recipe instanceof thaumcraft.api.crafting.InfusionRecipe ritual)ritual.aspects().forEach((tag,amount)->sum.merge(tag,(int)(Math.sqrt(amount)/count),Math::addExact));
            if(recipe instanceof thaumcraft.api.crafting.IArcaneRecipe magic)magic.vis().forEach((tag,amount)->sum.merge(tag,(int)(Math.sqrt(amount)/count),Math::addExact));
            sum.entrySet().removeIf(e->e.getValue()<=0);long size=sum.values().stream().mapToLong(Integer::longValue).sum();
            if(size>0&&size<smallest){best=Collections.unmodifiableMap(new LinkedHashMap<>(sum));smallest=size;}
        }
        return best;
    }
}
