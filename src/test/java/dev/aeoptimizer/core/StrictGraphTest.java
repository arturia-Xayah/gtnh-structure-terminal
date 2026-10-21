package dev.aeoptimizer.core;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class StrictGraphTest {

    private StrictGraph.Recipe<String> recipe(String... inputs) {
        return new StrictGraph.Recipe<>(Arrays.asList(inputs), 1, "recipe");
    }

    @Test
    public void rejectsSelfLoopWithClosedPath() {
        try {
            new StrictGraph<>("A", x -> recipe("A"), x -> x);
            fail();
        } catch (StrictGraph.InvalidPath e) {
            assertTrue(
                e.getMessage()
                    .contains("A --[recipe]--> A"));
        }
    }

    @Test
    public void rejectsProductiveTwoRecipeCycleToo() {
        try {
            new StrictGraph<>(
                "A",
                x -> x.equals("A") ? recipe("C", "D") : x.equals("C") ? recipe("A", "B") : null,
                x -> x);
            fail();
        } catch (StrictGraph.InvalidPath e) {
            assertTrue(
                e.getMessage()
                    .contains("AEO_CYCLE"));
            assertTrue(
                e.getMessage()
                    .contains("C"));
        }
    }

    @Test
    public void rejectsSelectedMultiOutput() {
        try {
            new StrictGraph<>("A", x -> new StrictGraph.Recipe<>(Collections.emptyList(), 2, "bad"), x -> x);
            fail();
        } catch (StrictGraph.InvalidPath e) {
            assertTrue(
                e.getMessage()
                    .contains("AEO_MULTI_OUTPUT"));
        }
    }

    @Test
    public void unrelatedRecipesAreNeverVisited() {
        Set<String> visited = new HashSet<>();
        StrictGraph<String> g = new StrictGraph<>("A", x -> {
            visited.add(x);
            if (x.equals("bad")) throw new AssertionError();
            return x.equals("A") ? recipe("B") : null;
        }, x -> x);
        assertEquals(Arrays.asList("A", "B"), g.order);
        assertFalse(visited.contains("bad"));
    }

    @Test
    public void sharedDependencyComesAfterAllConsumers() {
        StrictGraph<String> g = new StrictGraph<>(
            "root",
            x -> x.equals("root") ? recipe("left", "right")
                : x.equals("left") || x.equals("right") ? recipe("shared") : null,
            x -> x);
        assertEquals(4, g.order.size());
        assertTrue(g.order.indexOf("shared") > g.order.indexOf("left"));
        assertTrue(g.order.indexOf("shared") > g.order.indexOf("right"));
    }

    @Test
    public void deepGraphDoesNotOverflowJavaStack() {
        StrictGraph<Integer> g = new StrictGraph<>(
            0,
            x -> x < 30000 ? new StrictGraph.Recipe<>(Collections.singletonList(x + 1), 1, "deep") : null,
            Object::toString);
        assertEquals(30001, g.order.size());
    }

    @Test
    public void batchesHaveCorrectRemainderAndDoNotLoopPerCraft() {
        long remaining = 10000;
        List<Long> batches = new ArrayList<>();
        while (remaining > 0) {
            long n = BatchMath.limit(remaining, 4096, remaining, 1);
            batches.add(n);
            remaining -= n;
        }
        assertEquals(Arrays.asList(4096L, 4096L, 1808L), batches);
    }

    @Test
    public void exactIntegerCeilingForHugeOrders() {
        assertEquals(3074457345618258603L, BatchMath.ceilDiv(Long.MAX_VALUE, 3));
        assertEquals(0, BatchMath.limit(10, 4096, 1, 2));
        assertEquals(1, BatchMath.limit(10, 4096, 2, 2));
    }
}
