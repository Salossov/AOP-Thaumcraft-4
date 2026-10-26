package thaumcraft.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.research.ResearchDefinition;

/** Separate snapshots per reload's recipe manager; invalid reloads never install half a catalogue. */
public final class ThaumcraftData extends SimpleJsonResourceReloadListener {
    public record EntityVariant(String field,String value,Map<String,Integer> aspects) {public EntityVariant{aspects=Map.copyOf(aspects);}}
    public record ItemTagRule(Map<String,Integer> aspects,List<String> requiredTags){public ItemTagRule{aspects=Map.copyOf(aspects);requiredTags=List.copyOf(requiredTags);}}
    public record Snapshot(Map<String,Map<String,Integer>> objects,Map<String,Map<String,Integer>> entities,Map<String,ResearchDefinition> research,Map<String,List<EntityVariant>> entityVariants,Map<String,RecipeAspectResolver.Rule> derived,Map<String,String> scanGroups,StackAspectRules.Settings modifiers,boolean resolved,Map<String,List<String>> researchItemTags,Map<String,ItemTagRule> itemTagAspects,List<AspectAdditions.Rule> itemAdditionRules,List<AspectAdditions.Rule> entityAdditionRules,Map<String,Map<String,Integer>> entityAdditions,Map<String,List<EntityVariant>> entityVariantAdditions) {
        public Snapshot { objects=Map.copyOf(objects);entities=Map.copyOf(entities);research=Map.copyOf(research);entityVariants=Map.copyOf(entityVariants);derived=Map.copyOf(derived);scanGroups=Map.copyOf(scanGroups);researchItemTags=Map.copyOf(researchItemTags);itemTagAspects=Map.copyOf(itemTagAspects);itemAdditionRules=List.copyOf(itemAdditionRules);entityAdditionRules=List.copyOf(entityAdditionRules);entityAdditions=Map.copyOf(entityAdditions);var copied=new LinkedHashMap<String,List<EntityVariant>>();entityVariantAdditions.forEach((id,rules)->copied.put(id,List.copyOf(rules)));entityVariantAdditions=Map.copyOf(copied); }
    }
    private static final Map<net.minecraft.world.item.crafting.RecipeManager,Snapshot> SERVERS=java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<net.minecraft.world.item.crafting.RecipeManager,thaumcraft.api.book.BookCatalog> BOOKS=java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private record ResolvedSnapshot(Snapshot data,thaumcraft.api.book.BookCatalog books) {}
    private static final Map<MinecraftServer,ResolvedSnapshot> LAST_GOOD=java.util.Collections.synchronizedMap(new WeakHashMap<>());
    public static thaumcraft.api.book.BookCatalog books(MinecraftServer server){get(server);return BOOKS.getOrDefault(server.getRecipeManager(),thaumcraft.api.book.BookCatalog.EMPTY);}
    private final net.minecraft.world.item.crafting.RecipeManager recipes;
    private ThaumcraftData(net.minecraft.world.item.crafting.RecipeManager recipes) { super(new Gson(),"thaumcraft");this.recipes=recipes; }
    public static void register() { NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event)->event.addListener(new ThaumcraftData(event.getServerResources().getRecipeManager()))); }
    public static Snapshot get(MinecraftServer server) {
        // Creating a new singleplayer world replaces the resource manager while
        // retaining the already loaded ReloadableServerResources and recipes.
        var manager=server.getRecipeManager();var data=SERVERS.get(manager);if(data==null) throw new IllegalStateException("Thaumcraft data not loaded");
        if(!data.resolved) {
            if(!server.isSameThread())throw new IllegalStateException("Aspect derivation requires server thread after tag binding");
            try {
            var explicit=new LinkedHashMap<>(data.objects);var items=server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ITEM);
            for(var entry:data.itemTagAspects.entrySet())if(entry.getValue().requiredTags.stream().allMatch(tag->thaumcraft.crafting.TagOutput.present(server.registryAccess(),tag)))items.get(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,ResourceLocation.parse(entry.getKey()))).ifPresent(values->values.forEach(holder->explicit.putIfAbsent(BuiltInRegistries.ITEM.getKey(holder.value()).toString(),entry.getValue().aspects)));
            var active=new LinkedHashMap<>(data.research);active.keySet().removeAll(thaumcraft.api.addon.AddonRegistry.disabledResearch());if(!thaumcraft.config.ThaumcraftConfig.ALLOW_MIRRORS.get())for(var key:java.util.List.of("MIRROR","MIRRORHAND","MIRRORESSENTIA"))active.remove(key);var requirements=data.researchItemTags;active.keySet().removeIf(key->dataResearchTagsMissing(server,requirements.getOrDefault(key,List.of())));
            boolean removed;do{removed=active.values().removeIf(d->java.util.stream.Stream.concat(d.parents().stream(),d.hiddenParents().stream()).anyMatch(parent->!active.containsKey(parent)));}while(removed);
            Map<String,ResearchDefinition> dynamic;
            try {dynamic=thaumcraft.api.research.ResearchCatalogRules.resolve(active);}
            catch(IllegalArgumentException error) {
                var previous=LAST_GOOD.get(server);if(previous==null)throw error;
                // Tag/config filtering happens only after reload's resource/tag binding.
                // Keep the entire prior snapshot and its matching book catalog atomically.
                SERVERS.put(manager,previous.data);BOOKS.put(manager,previous.books);
                Thaumcraft.LOGGER.error("Rejected active research catalog; retaining previous snapshot",error);return previous.data;
            }
            active.clear();active.putAll(dynamic);
            var itemChanges=resolveAdditions(server.registryAccess(),net.minecraft.core.registries.Registries.ITEM,data.itemAdditionRules);
            var entityChanges=resolveAdditions(server.registryAccess(),net.minecraft.core.registries.Registries.ENTITY_TYPE,data.entityAdditionRules);
            var modifiers=new StackAspectRules.Settings(data.modifiers.items(),data.modifiers.potionBases(),data.modifiers.effects(),data.modifiers.enchantments(),itemChanges);
            var objects=new RecipeAspectResolver(server,explicit,data.derived,itemChanges).resolve();data=new Snapshot(objects,data.entities,active,data.entityVariants,data.derived,data.scanGroups,modifiers,true,data.researchItemTags,data.itemTagAspects,data.itemAdditionRules,data.entityAdditionRules,entityChanges,data.entityVariantAdditions);SERVERS.put(manager,data);
            Thaumcraft.LOGGER.info("TC4 recipe aspects resolved: {} object values",objects.size());
            } catch(IllegalArgumentException|ArithmeticException error) {
                var previous=LAST_GOOD.get(server);if(previous==null)throw error;
                SERVERS.put(manager,previous.data);BOOKS.put(manager,previous.books);
                Thaumcraft.LOGGER.error("Rejected aspect/catalog resolution; retaining previous snapshot",error);return previous.data;
            }
        }LAST_GOOD.put(server,new ResolvedSnapshot(data,BOOKS.getOrDefault(manager,thaumcraft.api.book.BookCatalog.EMPTY)));return data;
    }
    public static Map<String,Integer> aspects(MinecraftServer server,ItemStack stack) {
        var data=get(server);return StackAspectRules.aspects(stack,data.objects.getOrDefault(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),Map.of()),data.modifiers);
    }
    public static Map<String,Integer> aspects(MinecraftServer server,BlockState state) {
        String key=objectKey(state);
        var data=get(server);return StackAspectRules.blockAspects(state,data.objects.getOrDefault(key,Map.of()),data.modifiers);
    }
    private static String objectKey(BlockState state) {String key=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();return key.equals("minecraft:redstone_ore")&&state.getValue(net.minecraft.world.level.block.RedStoneOreBlock.LIT)?"minecraft:lit_redstone_ore":key;}
    public static String objectScanKey(MinecraftServer server,ItemStack stack) {return StackAspectRules.scanKey(stack,get(server).scanGroups);}
    public static String objectScanKey(MinecraftServer server,BlockState state) {String key=objectKey(state);return get(server).scanGroups.getOrDefault(key,key);}
    public static Map<String,Integer> aspects(MinecraftServer server,Entity entity) {
        var data=get(server);return EntityAspectRules.aspects(entity,data.entities,data.entityVariants,data.entityAdditions,data.entityVariantAdditions);
    }
    public static String entityScanKey(MinecraftServer server,Entity entity) {
        return EntityAspectRules.scanKey(entity,get(server).entityVariants);
    }
    /** Unlike vanilla's permissive scanner, a malformed file aborts the entire transaction. */
    @Override protected Map<ResourceLocation,JsonElement> prepare(ResourceManager manager,ProfilerFiller profiler){
        var converter=net.minecraft.resources.FileToIdConverter.json("thaumcraft");var files=new LinkedHashMap<ResourceLocation,JsonElement>();
        for(var entry:converter.listMatchingResources(manager).entrySet())try(var reader=entry.getValue().openAsReader()){
            var json=com.google.gson.JsonParser.parseReader(reader);if(json==null||!json.isJsonObject()||!json.getAsJsonObject().has("kind"))throw new IllegalArgumentException("Missing data kind in "+entry.getKey());
            files.put(converter.fileToId(entry.getKey()),json);
        }catch(java.io.IOException|com.google.gson.JsonParseException error){throw new IllegalArgumentException("Invalid Thaumcraft data: "+entry.getKey(),error);}
        return files;
    }
    @Override protected void apply(Map<ResourceLocation,JsonElement> files,ResourceManager manager,ProfilerFiller profiler) {
        var categories=new LinkedHashMap<>(thaumcraft.api.book.BookCategory.originals());categories.putAll(thaumcraft.api.addon.AddonRegistry.categories());var pages=new LinkedHashMap<>(thaumcraft.api.addon.AddonRegistry.pages());
        var objects=new LinkedHashMap<String,Map<String,Integer>>();var entities=new LinkedHashMap<String,Map<String,Integer>>();var research=new LinkedHashMap<String,ResearchDefinition>();var variants=new LinkedHashMap<String,List<EntityVariant>>();var derived=new LinkedHashMap<String,RecipeAspectResolver.Rule>();var groups=new LinkedHashMap<String,String>();
        var researchItemTags=new LinkedHashMap<String,List<String>>();var itemTagAspects=new LinkedHashMap<String,ItemTagRule>();
        var variantAdditions=new LinkedHashMap<String,List<EntityVariant>>();
        var itemAdditions=new ArrayList<AspectAdditions.Rule>();var entityAdditions=new ArrayList<AspectAdditions.Rule>();
        var itemMinimums=new LinkedHashMap<String,Map<String,Integer>>();var potionBases=new LinkedHashMap<String,Map<String,Integer>>();var potionEffects=new LinkedHashMap<String,Map<String,Integer>>();var enchantments=new LinkedHashMap<String,Map<String,Integer>>();
        research.putAll(thaumcraft.api.addon.AddonRegistry.research());
        for(var id:files.keySet().stream().sorted().toList()) {
            var json=files.get(id).getAsJsonObject();String kind=json.get("kind").getAsString();
            if(kind.equals("book_category")||kind.equals("book_page")){String key=json.has("id")?json.get("id").getAsString():id.toString();thaumcraft.api.addon.AddonRegistry.id(key);if(kind.equals("book_category")){if(categories.putIfAbsent(key,thaumcraft.addon.BookData.category(key,json))!=null)throw new IllegalArgumentException("Duplicate book category "+key);}else {var page=thaumcraft.api.book.BookPage.read(key,json);thaumcraft.api.book.PageTypes.validate(page);if(pages.putIfAbsent(key,page)!=null)throw new IllegalArgumentException("Duplicate book page "+key);}
            } else if(kind.equals("object_aspects")||kind.equals("entity_aspects")) {
                var target=kind.equals("object_aspects")?objects:entities;
                for(var entry:json.getAsJsonObject("entries").entrySet()) { ResourceLocation.parse(entry.getKey());target.put(entry.getKey(),quantities(entry.getValue().getAsJsonObject())); }
            } else if(kind.equals("item_aspect_additions")||kind.equals("entity_aspect_additions")) {
                if(json.has("required_mods")&&!strings(json,"required_mods").stream().allMatch(mod->net.neoforged.fml.ModList.get()!=null&&net.neoforged.fml.ModList.get().isLoaded(mod)))continue;
                boolean item=kind.equals("item_aspect_additions");String requiredKey=item?"required_item_tags":"required_entity_tags";
                var required=json.has(requiredKey)?strings(json,requiredKey):List.<String>of();
                var target=item?itemAdditions:entityAdditions;
                for(var entry:json.getAsJsonObject("entries").entrySet())target.add(new AspectAdditions.Rule(entry.getKey(),changes(entry.getValue().getAsJsonObject()),required));
            } else if(kind.equals("item_tag_aspects")) {
                for(var entry:json.getAsJsonObject("entries").entrySet()){ResourceLocation.parse(entry.getKey());var rule=entry.getValue().getAsJsonObject();var required=rule.has("required_item_tags")?strings(rule,"required_item_tags"):List.<String>of();required.forEach(ResourceLocation::parse);itemTagAspects.put(entry.getKey(),new ItemTagRule(quantities(rule.getAsJsonObject("aspects")),required));}
            } else if(java.util.Set.of("stack_aspect_minimums","potion_aspect_bases","potion_aspect_effects","enchantment_aspect_minimums").contains(kind)) {
                var target=switch(kind){case "stack_aspect_minimums"->itemMinimums;case "potion_aspect_bases"->potionBases;case "potion_aspect_effects"->potionEffects;default->enchantments;};for(var entry:json.getAsJsonObject("entries").entrySet()){ResourceLocation.parse(entry.getKey());target.put(entry.getKey(),quantities(entry.getValue().getAsJsonObject()));}
            } else if(kind.equals("derived_object_aspects")) {
                for(var entry:json.getAsJsonObject("entries").entrySet()) {var rule=entry.getValue().getAsJsonObject();ResourceLocation.parse(entry.getKey());String inherit=rule.has("inherit")?rule.get("inherit").getAsString():"";if(!inherit.isEmpty())ResourceLocation.parse(inherit);
                    derived.put(entry.getKey(),new RecipeAspectResolver.Rule(inherit,rule.has("add")?quantities(rule.getAsJsonObject("add")):Map.of(),rule.has("merge")?quantities(rule.getAsJsonObject("merge")):Map.of(),rule.has("remove")?strings(rule,"remove"):List.of()));}
            } else if(kind.equals("scan_groups")) {
                for(var entry:json.getAsJsonObject("entries").entrySet()) {ResourceLocation.parse(entry.getKey());ResourceLocation.parse(entry.getValue().getAsString());groups.put(entry.getKey(),entry.getValue().getAsString());}
            } else if(kind.equals("entity_variant_aspect_additions")) {
                if(json.has("required_mods")&&!strings(json,"required_mods").stream().allMatch(mod->net.neoforged.fml.ModList.get()!=null&&net.neoforged.fml.ModList.get().isLoaded(mod)))continue;
                for(var entry:json.getAsJsonObject("entries").entrySet()) {
                    ResourceLocation.parse(entry.getKey());var values=variantAdditions.computeIfAbsent(entry.getKey(),key->new ArrayList<>());
                    for(var value:entry.getValue().getAsJsonArray()){var rule=value.getAsJsonObject();String field=rule.get("field").getAsString();if(field.isBlank())throw new IllegalArgumentException("Empty entity variant field");values.add(new EntityVariant(field,rule.get("value").getAsString(),changes(rule.getAsJsonObject("aspects"))));}
                }
            } else if(kind.equals("entity_variant_aspects")) {
                for(var entry:json.getAsJsonObject("entries").entrySet()) {
                    ResourceLocation.parse(entry.getKey());var values=new ArrayList<EntityVariant>();
                    for(var value:entry.getValue().getAsJsonArray()) {var variant=value.getAsJsonObject();String field=variant.get("field").getAsString();if(field.isBlank())throw new IllegalArgumentException("Empty entity variant field");values.add(new EntityVariant(field,variant.get("value").getAsString(),quantities(variant.getAsJsonObject("aspects"))));}
                    variants.put(entry.getKey(),List.copyOf(values));
                }
            } else if(kind.equals("research")) {
                thaumcraft.addon.BookData.researchDefaults(json);
                String key=json.get("key").getAsString();if(!id.getNamespace().equals("thaumcraft"))thaumcraft.api.addon.AddonRegistry.id(key);if(research.containsKey(key)) throw new IllegalArgumentException("Duplicate research "+key);
                if(json.has("required_item_tags")){var required=strings(json,"required_item_tags");required.forEach(ResourceLocation::parse);researchItemTags.put(key,required);}
                var definition=new ResearchDefinition(key,json.get("category").getAsString(),strings(json,"parents"),quantities(json.getAsJsonObject("aspects")),
                    json.get("complexity").getAsInt(),json.get("auto_unlock").getAsBoolean(),json.get("concealed").getAsBoolean(),json.get("column").getAsInt(),json.get("row").getAsInt(),
                    strings(json,"text_pages"),strings(json,"recipe_pages"),json.get("icon").getAsString(),
                    json.has("virtual")&&json.get("virtual").getAsBoolean(),json.has("round")&&json.get("round").getAsBoolean(),json.has("stub")&&json.get("stub").getAsBoolean(),json.has("secondary")&&json.get("secondary").getAsBoolean(),json.has("siblings")?strings(json,"siblings"):List.of(),json.has("page_order")?strings(json,"page_order"):List.of(),json.has("hidden")&&json.get("hidden").getAsBoolean(),json.has("warp")?json.get("warp").getAsInt():0,
                    json.has("item_triggers")?strings(json,"item_triggers"):List.of(),json.has("entity_triggers")?strings(json,"entity_triggers"):List.of(),json.has("aspect_triggers")?strings(json,"aspect_triggers"):List.of(),json.has("hidden_parents")?strings(json,"hidden_parents"):List.of(),json.has("page_requirements")?stringMap(json.getAsJsonObject("page_requirements")):Map.of(),json.has("special")&&json.get("special").getAsBoolean(),recipeGroups(json),json.has("linked_research")?json.get("linked_research").getAsString():"");
                if(json.has("lost"))definition=definition.withLost(json.get("lost").getAsBoolean());
                if(definition.complexity()<0||definition.complexity()>3) throw new IllegalArgumentException("Invalid complexity "+key);research.put(key,definition);
            } else throw new IllegalArgumentException("Unknown Thaumcraft data kind "+kind+" in "+id);
        }
        for(var definition:research.values()) for(var parent:java.util.stream.Stream.concat(definition.parents().stream(),definition.hiddenParents().stream()).toList()) if(!research.containsKey(parent)) throw new IllegalArgumentException("Unknown parent "+parent+" for "+definition.key());
        for(var definition:research.values())for(var sibling:definition.siblings())if(!research.containsKey(sibling))throw new IllegalArgumentException("Unknown sibling "+sibling+" for "+definition.key());
        var visiting=new java.util.HashSet<String>();var done=new java.util.HashSet<String>();for(var key:research.keySet()) visit(key,research,visiting,done);
        if(!research.containsKey("GOGGLES")) throw new IllegalArgumentException("Missing GOGGLES research");
        for(var key:objects.keySet()) {var id=ResourceLocation.parse(key);if(!BuiltInRegistries.ITEM.containsKey(id)&&!BuiltInRegistries.BLOCK.containsKey(id)&&!key.equals("minecraft:lit_redstone_ore"))throw new IllegalArgumentException("Unknown object ID "+key);}
        for(var key:entities.keySet())if(!BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(key)))throw new IllegalArgumentException("Unknown entity ID "+key);
        for(var key:variants.keySet())if(!BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(key)))throw new IllegalArgumentException("Unknown entity variant ID "+key);
        var unknownDerived=derived.keySet().stream().filter(key->!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(key))).sorted().toList();if(!unknownDerived.isEmpty())throw new IllegalArgumentException("Unknown derived item IDs "+unknownDerived);
        for(var key:groups.keySet())if(!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(key))&&!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(key)))throw new IllegalArgumentException("Unknown scan-group object ID "+key);
        for(var key:itemMinimums.keySet())if(!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(key)))throw new IllegalArgumentException("Unknown stack modifier item ID "+key);
        for(var key:potionBases.keySet())if(!BuiltInRegistries.POTION.containsKey(ResourceLocation.parse(key)))throw new IllegalArgumentException("Unknown potion modifier ID "+key);
        for(var key:potionEffects.keySet())if(!BuiltInRegistries.MOB_EFFECT.containsKey(ResourceLocation.parse(key)))throw new IllegalArgumentException("Unknown effect modifier ID "+key);
        var bookCatalog=new thaumcraft.api.book.BookCatalog(categories,pages);thaumcraft.api.book.CatalogValidator.validate(research,bookCatalog);
        SERVERS.put(recipes,new Snapshot(objects,entities,research,variants,derived,groups,new StackAspectRules.Settings(itemMinimums,potionBases,potionEffects,enchantments),false,researchItemTags,itemTagAspects,itemAdditions,entityAdditions,Map.of(),variantAdditions));
        BOOKS.put(recipes,bookCatalog);
        thaumcraft.api.crafting.RecipeDisplays.invalidate();
        Thaumcraft.LOGGER.info("TC4 R2 data loaded: {} object tags, {} entity tags, {} research definitions",objects.size(),entities.size(),research.size());
    }
    static <T> Map<String,Map<String,Integer>> resolveAdditions(net.minecraft.core.RegistryAccess access,net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>> key,List<AspectAdditions.Rule> rules){
        var registry=access.lookupOrThrow(key);
        return AspectAdditions.resolve(rules,selector->{
            if(selector.startsWith("#"))return registry.get(net.minecraft.tags.TagKey.create(key,ResourceLocation.parse(selector.substring(1))))
                .map(values->values.stream().map(holder->holder.unwrapKey().orElseThrow().location().toString()).toList()).orElse(List.of());
            return registry.get(net.minecraft.resources.ResourceKey.create(key,ResourceLocation.parse(selector))).isPresent()?List.of(selector):List.of();
        },tag->registry.get(net.minecraft.tags.TagKey.create(key,ResourceLocation.parse(tag))).map(values->values.size()>0).orElse(false));
    }
    private static Map<String,Integer> changes(JsonObject json){
        var result=new LinkedHashMap<String,Integer>();
        for(var entry:json.entrySet()){
            if(Aspect.getAspect(entry.getKey())==null)throw new IllegalArgumentException("Unknown aspect "+entry.getKey());
            int amount;try{amount=entry.getValue().getAsBigDecimal().intValueExact();}catch(ArithmeticException|NumberFormatException error){throw new IllegalArgumentException("Invalid aspect change "+entry,error);}
            if(amount < -32767||amount > 32767)throw new IllegalArgumentException("Invalid aspect change "+entry);
            result.put(entry.getKey(),amount);
        }
        return Map.copyOf(result);
    }
    private static boolean dataResearchTagsMissing(MinecraftServer server,List<String> tags){return tags.stream().anyMatch(tag->!thaumcraft.crafting.TagOutput.present(server.registryAccess(),tag));}
    private static void visit(String key,Map<String,ResearchDefinition> definitions,java.util.Set<String> visiting,java.util.Set<String> done) {
        if(done.contains(key)) return;if(!visiting.add(key)) throw new IllegalArgumentException("Research parent cycle at "+key);
        for(var parent:java.util.stream.Stream.concat(definitions.get(key).parents().stream(),definitions.get(key).hiddenParents().stream()).toList()) visit(parent,definitions,visiting,done);visiting.remove(key);done.add(key);
    }
    private static Map<String,String> stringMap(JsonObject json){var result=new LinkedHashMap<String,String>();json.entrySet().forEach(entry->result.put(entry.getKey(),entry.getValue().getAsString()));return result;}
    private static Map<String,List<String>> recipeGroups(JsonObject json){var result=new LinkedHashMap<String,List<String>>();if(json.has("recipe_groups"))json.getAsJsonObject("recipe_groups").entrySet().forEach(entry->{var values=new ArrayList<String>();entry.getValue().getAsJsonArray().forEach(v->values.add(v.getAsString()));result.put(entry.getKey(),values);});return result;}
    private static List<String> strings(JsonObject json,String key) { var result=new ArrayList<String>();json.getAsJsonArray(key).forEach(x->result.add(x.getAsString()));return result; }
    private static Map<String,Integer> quantities(JsonObject json) {
        var result=new LinkedHashMap<String,Integer>();for(var entry:json.entrySet()) {
            if(Aspect.getAspect(entry.getKey())==null) throw new IllegalArgumentException("Unknown aspect "+entry.getKey());int amount=entry.getValue().getAsInt();
            if(amount<=0||amount>32767) throw new IllegalArgumentException("Invalid aspect amount "+entry);result.put(entry.getKey(),amount);
        }return java.util.Collections.unmodifiableMap(result);
    }
}
