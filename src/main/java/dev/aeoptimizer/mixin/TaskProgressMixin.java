package dev.aeoptimizer.mixin;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import appeng.me.cluster.implementations.CraftingCPUCluster.TaskProgress;
import appeng.me.cluster.implementations.CraftingCPUCluster.TaskProgress.SessionCraftCount;
import appeng.me.diagnostics.CraftingDiagnosticSessionId;
import dev.aeoptimizer.api.ProgressAccess;

@Mixin(value = TaskProgress.class, remap = false)
public abstract class TaskProgressMixin implements ProgressAccess {

    @Shadow
    protected long value;
    @Shadow
    @Final
    protected LinkedList<SessionCraftCount> diagnosticSessionCrafts;

    public long aeo$get() {
        return value;
    }

    public void aeo$set(long count) {
        value = count;
    }

    public Map<CraftingDiagnosticSessionId, Long> aeo$consume(long count) {
        Map<CraftingDiagnosticSessionId, Long> out = new LinkedHashMap<>();
        while (count > 0 && !diagnosticSessionCrafts.isEmpty()) {
            SessionCraftCount first = diagnosticSessionCrafts.getFirst();
            long take = Math.min(count, first.getRemaining());
            out.merge(first.getSessionId(), take, Long::sum);
            count -= take;
            long left = first.getRemaining() - take;
            if (left == 0) diagnosticSessionCrafts.removeFirst();
            else((SessionCountMixin) (Object) first).aeo$remaining(left);
        }
        return out;
    }
}
