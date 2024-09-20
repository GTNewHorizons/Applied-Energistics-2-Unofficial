package appeng.api.util;

import java.io.Serializable;
import java.util.function.IntConsumer;

/**
 * consumes an integer, if it's larger than 0, it means the crafting is not stucked.
 */
@FunctionalInterface
public interface CraftUpdateListener extends IntConsumer, Serializable {

    long serialVersionUID = 83482599346L;
}
