package appeng.helpers;

import static appeng.helpers.PatternHelper.convertToCondensedAEList;

import appeng.api.storage.data.IAEStack;

final class PatternInputResolver {

    private final IAEStack<?>[] encodedInputs;
    private final ResolvedInputs encodedSnapshot;
    private volatile ResolvedInputs resolvedInputs;

    PatternInputResolver(final IAEStack<?>[] encodedInputs) {
        this(encodedInputs, convertToCondensedAEList(encodedInputs));
    }

    PatternInputResolver(final IAEStack<?>[] encodedInputs, final IAEStack<?>[] condensedInputs) {
        this.encodedInputs = encodedInputs;
        this.encodedSnapshot = new ResolvedInputs(encodedInputs, condensedInputs);
        this.reset();
    }

    IAEStack<?>[] getEncodedInputs() {
        return encodedInputs;
    }

    IAEStack<?>[] getInputs() {
        return resolvedInputs.inputs;
    }

    IAEStack<?>[] getCondensedInputs() {
        return resolvedInputs.condensed;
    }

    void set(final IAEStack<?>[] inputs) {
        resolvedInputs = new ResolvedInputs(inputs, convertToCondensedAEList(inputs));
    }

    void reset() {
        resolvedInputs = encodedSnapshot;
    }

    private static final class ResolvedInputs {

        private final IAEStack<?>[] inputs;
        private final IAEStack<?>[] condensed;

        private ResolvedInputs(final IAEStack<?>[] inputs, final IAEStack<?>[] condensed) {
            this.inputs = inputs;
            this.condensed = condensed;
        }
    }
}
