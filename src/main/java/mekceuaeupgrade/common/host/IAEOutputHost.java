package mekceuaeupgrade.common.host;

import mekceuaeupgrade.common.transfer.AERecipePort;

import java.util.Collections;
import java.util.List;

public interface IAEOutputHost extends IAEUpgradeHost {

    default List<AERecipePort> getAEOutputPorts() {
        return Collections.emptyList();
    }

    default boolean drainAEOutputs(AEUpgradeNode node) {
        List<AERecipePort> ports = getAEOutputPorts();
        boolean drained = false;
        for (AERecipePort port : ports) {
            if (port != null) {
                drained |= port.drain(node);
            }
        }
        return drained;
    }
}
