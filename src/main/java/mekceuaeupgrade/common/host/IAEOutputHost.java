package mekceuaeupgrade.common.host;

import mekceuaeupgrade.common.transfer.AERecipePort;

import java.util.Collections;
import java.util.List;

public interface IAEOutputHost extends IAEUpgradeHost {

    default List<AERecipePort> getAEOutputPorts() {
        return Collections.emptyList();
    }

    default boolean drainAEOutputs(AEUpgradeNode node) {
        boolean drained = false;
        for (AERecipePort port : getAEOutputPorts()) {
            if (port != null) {
                drained |= port.drain(node);
            }
        }
        return drained;
    }
}
