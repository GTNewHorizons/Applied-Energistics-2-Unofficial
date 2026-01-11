package appeng.api.networking.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;

public interface IStorageInterceptor {

    boolean canAccept(IAEStack<?> stack);

    IAEStack<?> injectItems(IAEStack<?> input, final Actionable type, final BaseActionSource src);

    boolean shouldRemoveInterceptor(IAEStack<?> stack);
}
