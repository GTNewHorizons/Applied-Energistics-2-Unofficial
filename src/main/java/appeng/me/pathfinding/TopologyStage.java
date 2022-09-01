package appeng.me.pathfinding;

public enum TopologyStage {
    CONTROLLER_TO_BACKBONE, // at this stage we set paths from controller to backbone and set the channels along these
    // paths if necessary
    BACKBONE, // at this stage we establish backbone cables relationships (paths to controller connections), no channels
    // increment here
    PERIPHERALS // here we set up channels of all the nodes requiring channels, removing filled controller connections
    // as necessary
}
