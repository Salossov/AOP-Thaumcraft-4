package thaumcraft.entities;

import java.util.*;
import java.util.function.UnaryOperator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import thaumcraft.api.entities.GolemWorkApi;
import thaumcraft.api.entities.GolemWorkApi.*;

/** Entity-owned runtime adapters. Equipment/cargo persistence remains on ThaumicGolem. */
final class GolemWorkExtensions {
    private final ThaumicGolem golem;
    private final GoalSelector selector;
    private final List<Goal> goals = new ArrayList<>();
    private final List<Extension> extensions = new ArrayList<>();
    private int configuration = Integer.MIN_VALUE, generation;
    private boolean transferring;

    GolemWorkExtensions(ThaumicGolem golem, GoalSelector selector) { this.golem = golem; this.selector = selector; }
    private boolean live() {
        return golem.level() instanceof ServerLevel level && level.getServer().isSameThread()
                && golem.isAlive() && !golem.isRemoved() && level.hasChunkAt(golem.blockPosition())
                && level.getEntity(golem.getUUID()) == golem;
    }
    void refresh() {
        if (!live()) { close(); return; }
        if (configuration == golem.extensionConfigurationRevision()) return;
        close(); configuration = golem.extensionConfigurationRevision();
        for (var factory : GolemWorkApi.factories()) {
            var context = new NativeContext(generation);
            var extension = factory.create(context);
            if (extension == null) continue;
            extensions.add(extension);
            for (var entry : List.copyOf(extension.goals())) {
                var guarded = new GuardedGoal(context, entry.goal());
                goals.add(guarded); selector.addGoal(entry.priority(), guarded);
            }
        }
    }
    void close() {
        generation++;
        for (var goal : goals) selector.removeGoal(goal);
        goals.clear();
        for (var extension : extensions) extension.close();
        extensions.clear(); configuration = Integer.MIN_VALUE;
    }
    boolean hasItemDestination() {
        if (!live() || golem.inactive() || golem.isNoAi() || configuration != golem.extensionConfigurationRevision()) return false;
        for (var extension : extensions) if (extension.hasItemDestination()) return true;
        return false;
    }
    private static boolean same(Cargo a, Cargo b) {
        var af = a.fluid(); var bf = b.fluid();
        return ItemStack.matches(a.item(), b.item()) && af.getAmount() == bf.getAmount()
                && (af.isEmpty() && bf.isEmpty() || FluidStack.isSameFluidSameComponents(af, bf))
                && a.aspect() == b.aspect() && a.essentia() == b.essentia();
    }
    private Cargo cargo() { return new Cargo(golem.carried(), golem.fluid, golem.essentia, golem.essentiaAmount); }

    private final class NativeContext implements Context {
        private final int epoch;
        private Snapshot last;
        NativeContext(int epoch) { this.epoch = epoch; }
        private boolean valid() {
            return epoch == generation && live() && configuration == golem.extensionConfigurationRevision();
        }
        private void requireValid() { if (!valid()) throw new IllegalStateException("Expired golem work context"); }
        @Override public PathfinderMob entity() { return golem; }
        @Override public Snapshot snapshot() {
            requireValid();
            var upgrades = new ArrayList<Integer>(); for (int kind = 0; kind < 6; kind++) upgrades.add(golem.upgrade(kind));
            var filters = new ArrayList<ItemStack>(); for (int slot = 0; slot < golem.filterSlots(); slot++) filters.add(golem.filters.getItem(slot).copy());
            last = new Snapshot(golem.workRevision(), golem.core(), golem.advanced(), upgrades,
                    golem.inactive() || golem.isNoAi(), golem.action(), golem.home(), golem.homeFace(),
                    golem.golemMarkers(), filters, golem.carryLimit(), golem.fluidLimit(), golem.range(), cargo());
            return last;
        }
        @Override public boolean current(Snapshot expected) {
            return expected != null && expected == last && valid() && !transferring && !golem.inactive() && !golem.isNoAi()
                    && expected.revision() == golem.workRevision() && same(expected.cargo(), cargo());
        }
        @Override public boolean matches(ItemStack stack, ItemStack filter) {
            requireValid(); return golem.nativeWorkGoal().matches(stack.copy(), filter.copy());
        }
        @Override public List<ItemStack> itemDemand() { requireValid(); return golem.nativeWorkGoal().externalItemDemand(); }
        @Override public List<FluidStack> fluidDemand() { requireValid(); return golem.nativeWorkGoal().externalFluidDemand(); }
        @Override public boolean beginAction(Snapshot expected) {
            if (!current(expected)) return false;
            golem.action(10); return true;
        }
        @Override public boolean exchange(Snapshot expected, UnaryOperator<Cargo> transfer, boolean animate) {
            Objects.requireNonNull(transfer);
            if (!current(expected)) return false;
            transferring = true;
            try {
                var before = cargo();
                var after = Objects.requireNonNull(transfer.apply(before), "Transfer cargo");
                if (same(before, after)) return false;
                golem.carried(after.item()); golem.fluid = after.fluid();
                golem.essentia = after.aspect(); golem.essentiaAmount = after.essentia();
                golem.externalCargoChanged();
                if (animate) golem.action(10);
                last = null;
                return true;
            } finally { transferring = false; }
        }
    }
    private static final class GuardedGoal extends Goal {
        private final NativeContext context;
        private final Goal delegate;
        GuardedGoal(NativeContext context, Goal delegate) {
            this.context = context; this.delegate = delegate; setFlags(delegate.getFlags());
        }
        @Override public boolean canUse() { return context.valid() && delegate.canUse(); }
        @Override public boolean canContinueToUse() { return context.valid() && delegate.canContinueToUse(); }
        @Override public boolean isInterruptable() { return delegate.isInterruptable(); }
        @Override public boolean requiresUpdateEveryTick() { return delegate.requiresUpdateEveryTick(); }
        @Override public void start() { if (context.valid()) delegate.start(); }
        @Override public void tick() { if (context.valid()) delegate.tick(); }
        @Override public void stop() { delegate.stop(); }
    }
}
