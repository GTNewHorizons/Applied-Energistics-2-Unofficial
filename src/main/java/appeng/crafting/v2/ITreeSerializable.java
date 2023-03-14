package appeng.crafting.v2;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Interface for elements of the crafting tree that can be serialized for network transmission.
 *
 * This forms a semi-sealed class hierarchy, make sure to register all implementers with CraftingTreeSerializer.
 */
public interface ITreeSerializable {

    /**
     * Write the contents of this node to the byte buffer. The type code is pre-serialized into the buffer already.
     *
     * @return The list of child nodes to recursively serialize (done using a custom stack to avoid
     *         StackOverflowExceptions).
     */
    List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException;

    /**
     * Ran after deserializing of the children finishes.
     *
     * @param children The deserialized children.
     */
    void loadChildren(Collection<ITreeSerializable> children) throws IOException;
}
