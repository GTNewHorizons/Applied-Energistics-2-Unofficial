package appeng.api.storage.data;

public interface IAEStackType<T extends IAEStack<T>> {

    String getId();

    IItemList<T> createList();
}
