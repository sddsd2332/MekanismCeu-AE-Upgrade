package mekceuaeupgrade.common.integration.appeng;

/**
 * Exposes whether a native AE cache still has work which requires its grid tick.
 * Grid retains the crafting cache bridge once at construction. A missing bridge keeps
 * normal dispatch enabled; other cache types are validated separately by Grid.
 */
public interface IAEGridCacheTickGate {

    boolean mekceuaeupgrade$isTickRequired();
}
