package appeng.util;

import java.util.function.Predicate;

public class PriorityPredicate<T> {
    private final int priority;
    private final Predicate<T> predicate;

    public PriorityPredicate(final int priority, final Predicate<T> predicate) {
        this.priority = priority;
        this.predicate = predicate;
    }

    public int getPriority() {
        return priority;
    }

    public Predicate<T> getPredicate() {
        return predicate;
    }
}
