package mekceuaeupgrade.common.host;

public interface IAEUpgradeHostBridge extends IAEUpgradeHost {

    AEUpgradeHostDelegate mekceuaeupgrade$getAEUpgradeDelegate();

    @Override
    default AEUpgradeNode getAEUpgradeNode() {
        return mekceuaeupgrade$getAEUpgradeDelegate().getNode();
    }

    default void mekceuaeupgrade$invalidateAERecipeCache() {
        mekceuaeupgrade$getAEUpgradeDelegate().invalidateRecipeCache();
    }

    default void mekceuaeupgrade$onAERecipePortsChanged() {
        mekceuaeupgrade$getAEUpgradeDelegate().onRecipePortsChanged();
    }

    default void mekceuaeupgrade$invalidateAEBusyCache() {
        mekceuaeupgrade$getAEUpgradeDelegate().invalidateBusyCache("container_contents");
    }
}
