package thaumcraft.addon;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.EssentiaItemApi;
import thaumcraft.items.EssentiaJarItem;
import thaumcraft.registry.R3Content;

/** Native data schemas stay in the core; addons receive independent conversion results. */
final class NativeEssentiaItems implements EssentiaItemApi.Access {
    @Override public Optional<EssentiaItemApi.Contents> inspect(ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        if (stack.is(R3Content.EMPTY_PHIAL.get())) return contents(8, false, null, 0, null);
        if (stack.is(R3Content.ESSENTIA_PHIAL.get())) {
            var aspect = R3Content.aspect(stack);
            return aspect == null ? Optional.empty() : contents(8, false, aspect, 8, null);
        }
        if (!isJar(stack)) return Optional.empty();
        var data = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        String filterTag = data.getString("AspectFilter");
        var filter = filterTag.isEmpty() ? null : Aspect.getAspect(filterTag);
        if (!filterTag.isEmpty() && filter == null) return Optional.empty();
        if (data.contains("Essentia") && !data.contains("Essentia", Tag.TAG_COMPOUND)) return Optional.empty();
        var stored = data.getCompound("Essentia");
        Aspect aspect = null;
        int amount = 0;
        for (var tag : stored.getAllKeys()) {
            var candidate = Aspect.getAspect(tag);
            int units = stored.getInt(tag);
            if (candidate == null || !stored.contains(tag, Tag.TAG_INT) || units < 0 || units > 64) return Optional.empty();
            if (units == 0) continue;
            if (aspect != null || filter != null && candidate != filter) return Optional.empty();
            aspect = candidate;
            amount = units;
        }
        return contents(64, true, aspect, amount, filter);
    }

    @Override public Optional<EssentiaItemApi.TransferResult> fill(ItemStack stack, Aspect aspect, int offered) {
        var found = inspect(stack);
        if (found.isEmpty() || offered <= 0) return Optional.empty();
        var current = found.get();
        if (current.aspect() != null && current.aspect() != aspect || current.filter() != null && current.filter() != aspect) return Optional.empty();
        int units = Math.min(offered, current.capacity() - current.amount());
        if (units <= 0 || !current.partial() && units != 8) return Optional.empty();
        var result = stack.copyWithCount(1);
        if (current.partial()) {
            ((EssentiaJarItem) result.getItem()).setAspects(result, new AspectList().add(aspect, current.amount() + units));
        } else {
            result = stack.transmuteCopy(R3Content.ESSENTIA_PHIAL.get(), 1);
            R3Content.setAspect(result, aspect);
        }
        return Optional.of(new EssentiaItemApi.TransferResult(units, result));
    }

    @Override public Optional<EssentiaItemApi.TransferResult> drain(ItemStack stack, Aspect aspect, int requested) {
        var found = inspect(stack);
        if (found.isEmpty() || requested <= 0) return Optional.empty();
        var current = found.get();
        if (current.aspect() != aspect) return Optional.empty();
        int units = Math.min(requested, current.amount());
        if (units <= 0 || !current.partial() && units != 8) return Optional.empty();
        var result = stack.copyWithCount(1);
        if (current.partial()) {
            ((EssentiaJarItem) result.getItem()).setAspects(result, new AspectList().add(aspect, current.amount() - units));
        } else {
            result = stack.transmuteCopy(R3Content.EMPTY_PHIAL.get(), 1);
            CustomData.update(DataComponents.CUSTOM_DATA, result, tag -> tag.remove("Aspect"));
        }
        return Optional.of(new EssentiaItemApi.TransferResult(units, result));
    }

    @Override public Optional<ItemStack> configureLabel(ItemStack stack, Aspect aspect) {
        if (!stack.is(R3Content.JAR_LABEL.get())) return Optional.empty();
        var result = stack.copy();
        R3Content.setAspect(result, aspect);
        return Optional.of(result);
    }

    private static boolean isJar(ItemStack stack) {
        return stack.is(R3Content.WARDED_JAR.get().asItem()) || stack.is(R3Content.VOID_JAR.get().asItem());
    }
    private static Optional<EssentiaItemApi.Contents> contents(int capacity, boolean partial, Aspect aspect, int amount, Aspect filter) {
        return Optional.of(new EssentiaItemApi.Contents(capacity, partial, aspect, amount, filter));
    }
}
