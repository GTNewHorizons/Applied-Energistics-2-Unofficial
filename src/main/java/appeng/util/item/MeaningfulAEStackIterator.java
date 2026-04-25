package appeng.util.item;

import java.util.Iterator;
import java.util.NoSuchElementException;

import appeng.api.storage.data.IAEStack;

public class MeaningfulAEStackIterator<T extends IAEStack<?>> implements Iterator<T> {

    private final Iterator<T> parent;
    private T next;

    public MeaningfulAEStackIterator(final Iterator<T> iterator) {
        parent = iterator;
    }

    @Override
    public boolean hasNext() {
        while (parent.hasNext()) {
            next = parent.next();

            if (next.isMeaningful()) return true;
            else parent.remove(); // self cleaning :3
        }

        next = null;
        return false;
    }

    @Override
    public T next() {
        if (next != null) return next;
        throw new NoSuchElementException();
    }

    @Override
    public void remove() {
        parent.remove();
    }
}
