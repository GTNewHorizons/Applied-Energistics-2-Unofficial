package appeng.api.config;

import java.util.Comparator;

import appeng.util.AlphanumComparator;

public enum StringOrder {

    NATURAL(Comparator.naturalOrder()),

    ALPHANUM(new AlphanumComparator());

    public final Comparator<String> comparator;

    StringOrder(Comparator<String> comparator) {
        this.comparator = comparator;
    }
}
