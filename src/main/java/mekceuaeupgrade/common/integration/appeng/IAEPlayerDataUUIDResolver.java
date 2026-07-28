package mekceuaeupgrade.common.integration.appeng;

import javax.annotation.Nullable;
import java.util.UUID;

/** Added to AE2's internal player data by Mixin. */
public interface IAEPlayerDataUUIDResolver {

    @Nullable
    UUID mekceuaeupgrade$getPlayerUUID(int playerID);
}
