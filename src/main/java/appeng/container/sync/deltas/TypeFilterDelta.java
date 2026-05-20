package appeng.container.sync.deltas;

import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;

public interface TypeFilterDelta {

    static TypeFilterDelta toggle(final IAEStackType<?> type) {
        return new Toggle(AEStackTypeRegistry.getNetworkId(type));
    }

    final class Toggle implements TypeFilterDelta {

        private final byte networkId;

        public Toggle(final byte networkId) {
            this.networkId = networkId;
        }

        public byte getNetworkId() {
            return this.networkId;
        }
    }
}
