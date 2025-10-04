package appeng.api.util;

import java.io.Serializable;

import appeng.api.storage.data.IAEStack;

@FunctionalInterface
public interface CraftCompleteListener extends Serializable {

    long serialVersionUID = 734594276097234589L;

    /**
     * Applies this function to the given arguments.
     *
     * @param finalOutput  the output of job
     * @param numsOfOutput the size of output stack
     * @param elapsedTime  the time that job used
     */
    void apply(IAEStack<?> finalOutput, long numsOfOutput, long elapsedTime);
}
