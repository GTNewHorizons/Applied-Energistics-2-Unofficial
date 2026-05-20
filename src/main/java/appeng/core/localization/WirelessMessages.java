package appeng.core.localization;

public enum WirelessMessages implements Localization {

    Security,
    DimensionMismatch,
    BoundAdvancedFilled,
    Connected,
    Disconnected,
    InvalidTarget,
    Failed,
    Cleared,
    Mode,
    ModeToggle,
    SetMode,

    rowBindSuccess,
    rowBindInvalidSource,
    rowBindInvalidTarget,
    rowBindFailed,

    SourceHubFull,
    TargetHubFull,
    HubToHub,

    Simple,
    SimpleEmpty,
    SimpleBound,
    SimpleBounded,

    Advanced,
    AdvancedNext,
    AdvancedHowToggle,
    AdvancedActivated,

    AdvancedQueueing,
    AdvancedQueued,
    AdvancedNoConnectors,
    AdvancedQueueEmpty,
    AdvancedQueueNotEmpty,
    AdvancedQueueingHub,
    AdvancedQueueingHubQol,
    AdvancedQueueingTargetHubFull,

    AdvancedBinding,
    AdvancedBindingHub,
    AdvancedBindingHubQol,
    AdvancedBindingEmpty,
    AdvancedBindingNotEmpty,

    AdvancedQueueingLine,
    AdvancedBindingLine,
    AdvancedQueueingLineNotEmpty,

    AdvancedLine1st,
    AdvancedLine2nd,
    AdvancedLineEmpty1st,
    AdvancedLineEmpty2nd,

    AdvancedLine1stAdded,
    AdvancedLine2ndAdded,

    AdvancedLineNotLine,
    AdvancedLineReset,

    Super,
    SuperBound,
    SuperBoundFailed,
    SuperClear,
    SuperNetworkList,
    SuperNetwork,
    SuperNetworkListEmpty;

    public String getUnlocalized() {
        return "item.appliedenergistics2.WirelessMessages." + this.name();
    }
}
