package appeng.api.parts;

/**
 * Allows for fine-tuning of facade creation for block containers based on meta. Default true but would return false in
 * implementation. false = no facade created.
 */
public interface IFacadeControl {

    default boolean createFacadeForBlock(int meta) {
        return true;
    }

}
