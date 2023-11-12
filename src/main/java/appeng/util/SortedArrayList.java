package appeng.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

public class SortedArrayList<E> extends ArrayList<E> {

    private final Comparator<? super E> comparator;

    public SortedArrayList(Comparator<? super E> comparator) {
        this.comparator = comparator;
    }

    @Override
    public boolean add(E e) {
        int index = Collections.binarySearch(this, e, comparator);
        if (index < 0) {
            index = -(index + 1);
        }

        super.add(index, e);
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean result = super.addAll(c);
        sort(comparator);
        return result;
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        boolean result = super.addAll(index, c);
        sort(comparator);
        return result;
    }

    @Override
    public E set(int index, E element) {
        E result = super.set(index, element);
        sort(comparator);
        return result;
    }
}
