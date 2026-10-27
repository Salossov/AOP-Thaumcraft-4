package thaumcraft.data;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/** Signed patches are accumulated before application, so decreases do not depend on file order. */
public final class AspectAdditions {
    public record Rule(String selector, Map<String,Integer> amounts, List<String> requiredTags) {
        public Rule {
            ResourceLocation.parse(selector.startsWith("#") ? selector.substring(1) : selector);
            amounts = Collections.unmodifiableMap(new LinkedHashMap<>(amounts)); requiredTags = List.copyOf(requiredTags);
            requiredTags.forEach(ResourceLocation::parse);
        }
    }

    static Map<String,Map<String,Integer>> resolve(List<Rule> rules,
            Function<String,List<String>> targets, Predicate<String> tagPresent) {
        var result = new LinkedHashMap<String,Map<String,Integer>>();
        for (var rule : rules) {
            if (!rule.requiredTags.stream().allMatch(tagPresent)) continue;
            for (var id : targets.apply(rule.selector).stream().distinct().sorted().toList()) {
                var sums = result.computeIfAbsent(id, ignored -> new LinkedHashMap<>());
                rule.amounts.forEach((aspect, amount) -> sums.merge(aspect, amount, Math::addExact));
            }
        }
        var frozen = new LinkedHashMap<String,Map<String,Integer>>();
        result.forEach((id, amounts) -> frozen.put(id, Collections.unmodifiableMap(new LinkedHashMap<>(amounts))));
        return Map.copyOf(frozen);
    }

    public static Map<String,Integer> apply(Map<String,Integer> base, Map<String,Integer> changes) {
        if (changes.isEmpty()) return Collections.unmodifiableMap(new LinkedHashMap<>(base));
        var result = new LinkedHashMap<>(base);
        changes.forEach((aspect, amount) -> result.merge(aspect, amount, Math::addExact));
        result.entrySet().removeIf(entry -> entry.getValue() <= 0);
        return Collections.unmodifiableMap(result);
    }

    private AspectAdditions() { }
}
