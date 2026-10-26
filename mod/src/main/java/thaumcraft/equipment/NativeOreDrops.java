package thaumcraft.equipment;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import thaumcraft.api.wands.FocusUpgradeType;
import thaumcraft.config.ThaumcraftConfig;
import thaumcraft.items.*;
import thaumcraft.registry.R3Equipment;

/** Transforms native loot after protection and normal loot calculation. */
@EventBusSubscriber(modid="thaumcraft")
public final class NativeOreDrops {
    @SubscribeEvent public static void drops(BlockDropsEvent event){
        var tool=event.getTool();var registry=event.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        // TC4 EventHandlerWorld transforms ore drops even when Silk Touch supplied them.
        var focus=WandFocus.get(tool);
        boolean dowsing=focus.getItem() instanceof thaumcraft.api.wands.ItemFocusBasic basic&&basic.isUpgradedWith(focus,FocusUpgradeType.dowsing);
        dowsing|=tool.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getBoolean("ThaumcraftDowsing");
        if(!tool.is(R3Equipment.TOOLS.get("elemental_pickaxe").get())&&!tool.is(R3Equipment.TOOLS.get("primal_crusher").get())&&!dowsing)return;
        float chance=.2f+tool.getEnchantmentLevel(registry.getOrThrow(Enchantments.FORTUNE))*.075f;
        if(event.getLevel().random.nextFloat()>=chance)return;
        for(var drop:event.getDrops()){
            var result=specialMiningResult(event.getLevel(),event.getState(),drop.getItem());
            if(!result.isEmpty())drop.setItem(result.copyWithCount(drop.getItem().getCount()));
        }
    }
    /** Shared native policy; no random roll, loot mutation or count multiplication. */
    public static ItemStack specialMiningResult(net.minecraft.world.level.Level level,net.minecraft.world.level.block.state.BlockState state,ItemStack originalDrop){
        if(originalDrop.isEmpty())return ItemStack.EMPTY;
        for(var entry:R3Equipment.NATIVE_CLUSTERS.entrySet()){
            var metal=entry.getKey();if(metal.equals("copper")&&!ThaumcraftConfig.ENABLE_COPPER_CONTENT.get())continue;
            var ore=TagKey.create(Registries.BLOCK,ResourceLocation.parse("c:ores/"+metal));
            if(!state.is(ore)&&!(metal.equals("cinnabar")&&state.is(thaumcraft.registry.R3Content.CINNABAR_ORE.get())))continue;
            var raw=TagKey.create(Registries.ITEM,ResourceLocation.parse("c:raw_materials/"+metal));
            boolean isOre=originalDrop.is(TagKey.create(Registries.ITEM,ResourceLocation.parse("c:ores/"+metal)));
            boolean isRaw=originalDrop.is(raw);
            if(isOre||isRaw&&ThaumcraftConfig.RAW_ORE_PURIFICATION.get()||metal.equals("cinnabar")&&originalDrop.is(thaumcraft.registry.R3Content.QUICKSILVER.get()))return new ItemStack(entry.getValue().get());
            break;
        }
        return ItemStack.EMPTY;
    }
    private NativeOreDrops(){}
}
