package dev.aeoptimizer.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Iterative DFS of the chosen route only. O(V+E), including explicit self-edge rejection. */
public final class StrictGraph<T> {

    public static final class Recipe<T> {

        public final List<T> inputs;
        public final int outputKinds;
        public final java.util.function.Supplier<String> description;

        public Recipe(List<T> inputs, int outputKinds, String description) {
            this(inputs, outputKinds, () -> description);
        }

        public Recipe(List<T> inputs, int outputKinds, java.util.function.Supplier<String> description) {
            this.inputs = inputs;
            this.outputKinds = outputKinds;
            this.description = description;
        }
    }

    public static final class InvalidPath extends RuntimeException {

        public InvalidPath(String text) {
            super(text);
        }
    }

    private static final class Frame<T> {

        final T key;
        final Iterator<T> children;

        Frame(T key, List<T> children) {
            this.key = key;
            this.children = children.iterator();
        }
    }

    public final Map<T, Recipe<T>> recipes = new LinkedHashMap<>();
    public final List<T> order = new ArrayList<>();

    public StrictGraph(T target, Function<T, Recipe<T>> resolver, Function<T, String> label) {
        Map<T, Integer> colors = new HashMap<>();
        Deque<Frame<T>> stack = new ArrayDeque<>();
        enter(target, resolver, label, colors, stack);
        while (!stack.isEmpty()) {
            Frame<T> frame = stack.peekLast();
            if (!frame.children.hasNext()) {
                colors.put(frame.key, 2);
                order.add(frame.key);
                stack.removeLast();
                continue;
            }
            T child = frame.children.next();
            int state = colors.getOrDefault(child, 0);
            if (state == 1) {
                StringBuilder path = new StringBuilder("AEO_CYCLE: ");
                boolean include = false;
                for (Frame<T> entry : stack) {
                    if (entry.key.equals(child)) include = true;
                    if (include) {
                        path.append(label.apply(entry.key))
                            .append(" --[");
                        Recipe<T> recipe = recipes.get(entry.key);
                        path.append(recipe == null ? "?" : recipe.description.get())
                            .append("]--> ");
                    }
                }
                throw new InvalidPath(
                    path.append(label.apply(child))
                        .toString());
            }
            if (state == 0) enter(child, resolver, label, colors, stack);
        }
        Collections.reverse(order); // All consumers before a shared dependency; demand is summed first.
    }

    private void enter(T key, Function<T, Recipe<T>> resolver, Function<T, String> label, Map<T, Integer> colors,
        Deque<Frame<T>> stack) {
        Recipe<T> recipe = resolver.apply(key);
        if (recipe != null && recipe.outputKinds != 1) throw new InvalidPath(
            "AEO_MULTI_OUTPUT: " + label
                .apply(key) + " [" + recipe.description.get() + "] outputs=" + recipe.outputKinds);
        colors.put(key, 1);
        recipes.put(key, recipe);
        stack.addLast(new Frame<>(key, recipe == null ? Collections.emptyList() : recipe.inputs));
    }
}
