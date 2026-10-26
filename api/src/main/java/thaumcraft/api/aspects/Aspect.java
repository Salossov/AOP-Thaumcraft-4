package thaumcraft.api.aspects;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Canonical TC4 aspect definitions; tags remain the original Latin identifiers. */
public final class Aspect {
    private static final Map<String, Aspect> REGISTRY = new LinkedHashMap<>();
    private final String tag;
    private final int color;
    private final String chatColor;
    private final int blend;
    private final List<Aspect> components;
    private String icon,translationKey;

    private Aspect(String tag, int color, String chatColor, int blend, List<Aspect> components) {
        this.tag = tag;
        this.color = color;
        this.chatColor = chatColor;
        this.blend = blend;
        this.components = components;
    }

    public static synchronized Aspect register(String tag, int color, String chatColor, int blend, Aspect... components) {
        if (!tag.matches("[a-z0-9_.-]+(?::[a-z0-9_./-]+)?")||tag.length()>128 || (components.length != 0 && components.length != 2)) throw new IllegalArgumentException("Invalid aspect: " + tag);
        if (REGISTRY.containsKey(tag)) throw new IllegalArgumentException("Duplicate aspect: " + tag);
        var aspect = new Aspect(tag, color, chatColor, blend, List.of(components));
        REGISTRY.put(tag, aspect);
        return aspect;
    }

    public static Aspect getAspect(String tag) { Aspects.initialize(); return REGISTRY.get(tag!=null&&tag.startsWith("thaumcraft:")?tag.substring(11):tag); }
    public static Map<String, Aspect> all() { Aspects.initialize(); return Collections.unmodifiableMap(REGISTRY); }
    public String getTag() { return tag; }
    /** Resolves the current language on each call; legacy names remain the fallback. */
    public String getName() {
        var fallback = tag.substring(0,1).toUpperCase(java.util.Locale.ROOT)+tag.substring(1);
        return translationKey == null ? fallback
                : net.minecraft.network.chat.Component.translatableWithFallback(translationKey, fallback).getString();
    }
    public int getColor() { return color; }
    public String getChatColor() { return chatColor; }
    public int getBlend() { return blend; }
    public List<Aspect> getComponents() { return components; }
    public boolean isPrimal() { return components.isEmpty(); }
    public String texture() { return icon==null?"thaumcraft:textures/aspects/" + tag + ".png":icon; }
    public String translationKey(){return translationKey;}
    public static synchronized Aspect registerCompound(net.minecraft.resources.ResourceLocation id,int color,net.minecraft.resources.ResourceLocation icon,String translationKey,Aspect first,Aspect second){
        return registerCompound(id,color,icon,translationKey,1,first,second);
    }
    /** Explicit source blend factor; the original overload retains blend 1. */
    public static synchronized Aspect registerCompound(net.minecraft.resources.ResourceLocation id,int color,net.minecraft.resources.ResourceLocation icon,String translationKey,int blend,Aspect first,Aspect second){
        Aspects.initialize();if(id.getNamespace().equals("thaumcraft"))throw new IllegalArgumentException("Addon aspects need their own namespace");
        var aspect=register(id.toString(),color,"5",blend,Objects.requireNonNull(first),Objects.requireNonNull(second));aspect.icon=Objects.requireNonNull(icon).toString();aspect.translationKey=Objects.requireNonNull(translationKey);return aspect;
    }
    public boolean connects(Aspect other) { return other != null && (components.contains(other) || other.components.contains(this)); }
    public static Aspect combine(Aspect first, Aspect second) {
        Objects.requireNonNull(first); Objects.requireNonNull(second);
        for (var aspect : all().values()) if (!aspect.isPrimal()
                && ((aspect.components.get(0) == first && aspect.components.get(1) == second)
                || (aspect.components.get(0) == second && aspect.components.get(1) == first))) return aspect;
        return null;
    }
    @Override public String toString() { return tag; }
}
