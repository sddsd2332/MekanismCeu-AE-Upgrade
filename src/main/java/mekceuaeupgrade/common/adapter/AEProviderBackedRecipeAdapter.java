package mekceuaeupgrade.common.adapter;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.processing.MachinePort;
import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.MachineResourceStack;
import mekanism.api.processing.MachineTransferPlan;
import mekanism.api.processing.ProviderConformanceReport;
import mekanism.api.processing.QIOAutomationMode;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAERecipeMachineHost;
import mekceuaeupgrade.common.host.IAEUpgradeHost;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.route.AERecipeRoute;
import mekceuaeupgrade.common.transfer.AEUpgradeOutputDrainer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Prefers Mekanism's provider contract while keeping the machine-specific adapter as a compatibility fallback.
 */
public final class AEProviderBackedRecipeAdapter {

    private AEProviderBackedRecipeAdapter() {
    }

    public static Object getRecipeSourceKey(IAERecipeMachineHost host,
          Supplier<IAERecipeMachineAdapter> fallback) {
        try {
            ProviderContext context = findContext(host, QIOAutomationMode.SCHEDULED);
            ProviderContextIdentity identity = context == null ? null : context.identity;
            return identity == null ? fallback.get().getRecipeSourceKey(host) :
                  new ProviderRecipeSourceKey(identity.providerId, identity.source, identity.configurationRevision);
        } catch (RuntimeException | LinkageError ignored) {
            return fallback.get().getRecipeSourceKey(host);
        }
    }

    public static List<AEExposedRecipe> getExposedItemRecipes(IAERecipeMachineHost host,
          Supplier<IAERecipeMachineAdapter> fallback) {
        ProviderContext context = findContext(host, QIOAutomationMode.SCHEDULED);
        return context == null ? fallback.get().getExposedItemRecipes(host) : collectExposedRecipes(context);
    }

    public static boolean canAcceptItemInput(IAERecipeMachineHost host, AEExposedRecipe recipe, ItemStack stack,
          Supplier<IAERecipeMachineAdapter> fallback) {
        return canAcceptItemInputs(host, recipe, Collections.singletonList(stack), fallback);
    }

    public static boolean acceptItemInput(IAERecipeMachineHost host, AEExposedRecipe recipe, ItemStack stack,
          Supplier<IAERecipeMachineAdapter> fallback) {
        return acceptItemInputs(host, recipe, Collections.singletonList(stack), fallback);
    }

    public static boolean canAcceptItemInputs(IAERecipeMachineHost host, AEExposedRecipe recipe, List<ItemStack> stacks,
          Supplier<IAERecipeMachineAdapter> fallback) {
        ProviderContext context = findContext(host, QIOAutomationMode.SCHEDULED);
        if (context == null) {
            return fallback.get().canAcceptItemInputs(host, recipe, stacks);
        }
        return createExecutableInputPlan((TileEntity) host, context, recipe, stacks) != null;
    }

    public static boolean acceptItemInputs(IAERecipeMachineHost host, AEExposedRecipe recipe, List<ItemStack> stacks,
          Supplier<IAERecipeMachineAdapter> fallback) {
        ProviderContext context = findContext(host, QIOAutomationMode.SCHEDULED);
        if (context == null) {
            return fallback.get().acceptItemInputs(host, recipe, stacks);
        }
        MachineTransferPlan plan = createExecutableInputPlan((TileEntity) host, context, recipe, stacks);
        return plan != null && plan.execute();
    }

    public static boolean canAcceptAnyItemInput(IAERecipeMachineHost host,
          Supplier<IAERecipeMachineAdapter> fallback) {
        ProviderContext context = findContext(host, QIOAutomationMode.SCHEDULED);
        if (context == null) {
            return fallback.get().canAcceptAnyItemInput(host);
        }
        for (MachineRecipeRoute route : context.routes) {
            if (configurationMatches(route, context.ports) && outputsHaveCapacity(route, context.ports)) {
                MachineTransferPlan plan = inputPlan((TileEntity) host, route, context.ports);
                if (plan != null && plan.canExecute()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void observeInputContainers(IAERecipeMachineHost host, Consumer<Object> observer,
          Supplier<IAERecipeMachineAdapter> fallback) {
        ProviderContext context = findContext(host, QIOAutomationMode.PASSIVE);
        if (context == null) {
            fallback.get().observeInputContainers(host, observer);
            return;
        }
        for (MachinePort port : context.portList) {
            if (!port.isConfiguration() && port.role().acceptsInput()) {
                observeContainerMembers(port.container(), observer);
            }
        }
    }

    private static void observeContainerMembers(Object container, Consumer<Object> observer) {
        if (container instanceof List<?> members) {
            for (Object member : members) {
                observeContainerMembers(member, observer);
            }
        } else {
            observer.accept(container);
        }
    }

    /**
     * Provider output ports are authoritative even when a dedicated output mixin overrides its legacy method.
     */
    public static boolean drainOutputs(IAEUpgradeHost host, AEUpgradeNode node, BooleanSupplier fallback) {
        ProviderContext context = findContext(host, QIOAutomationMode.OUTPUT_ONLY);
        return context == null ? fallback.getAsBoolean() :
              AEUpgradeOutputDrainer.drainProviderPorts(node, (TileEntity) host, context.portList);
    }

    static List<AEExposedRecipe> collectExposedRecipes(List<MachineRecipeRoute> routes, List<MachinePort> ports) {
        Map<String, MachinePort> portsById = indexPorts(ports);
        if (portsById == null) {
            return Collections.emptyList();
        }
        return collectExposedRecipes(new ProviderContext(null, routes, ports, portsById));
    }

    private static List<AEExposedRecipe> collectExposedRecipes(ProviderContext context) {
        Map<LogicalRouteKey, AEExposedRecipe> recipes = new LinkedHashMap<>();
        for (MachineRecipeRoute machineRoute : context.routes) {
            if (machineRoute == null || !configurationMatches(machineRoute, context.ports)) {
                continue;
            }
            LogicalRouteKey logicalKey = LogicalRouteKey.create(machineRoute, context.ports);
            if (logicalKey == null || recipes.containsKey(logicalKey)) {
                continue;
            }
            AERecipeRoute route = AERecipeRoute.fromMachineRecipeRoute(machineRoute);
            AEExposedRecipe recipe = route == null ? null : route.toLegacyRecipe();
            if (recipe != null) {
                recipes.put(logicalKey, recipe);
            }
        }
        return new ArrayList<>(recipes.values());
    }

    @Nullable
    private static MachineTransferPlan createExecutableInputPlan(TileEntity tile, ProviderContext context,
          @Nullable AEExposedRecipe recipe, @Nullable List<ItemStack> stacks) {
        if (recipe == null || stacks == null || !recipe.matchesInputs(stacks)) {
            return null;
        }
        AERecipeRoute exposedRoute = recipe.getRecipeRoute();
        LogicalRouteKey logicalKey = LogicalRouteKey.create(exposedRoute, context.ports);
        if (logicalKey == null) {
            return null;
        }
        List<MachineRecipeRoute> candidates = context.routesByLogicalKey.get(logicalKey);
        if (candidates == null) {
            return null;
        }
        for (MachineRecipeRoute candidate : candidates) {
            if (!configurationMatches(candidate, context.ports)) {
                continue;
            }
            MachineRecipeRoute scaled = scaleRoute(candidate, recipe.getCraftAmount());
            if (scaled == null || !outputsHaveCapacity(scaled, context.ports)) {
                continue;
            }
            MachineTransferPlan plan = inputPlan(tile, scaled, context.ports);
            if (plan != null && plan.canExecute()) {
                return plan;
            }
        }
        return null;
    }

    @Nullable
    private static MachineTransferPlan inputPlan(@Nullable TileEntity tile, MachineRecipeRoute route,
          Map<String, MachinePort> ports) {
        if (tile == null || route.inputs().isEmpty()) {
            return null;
        }
        MachineTransferPlan plan = MachineTransferPlan.create(tile);
        for (MachineResourceStack input : route.inputs()) {
            MachinePort port = ports.get(input.portId());
            if (port == null || port.isConfiguration() || !port.role().acceptsInput() || port.kind() != input.kind()) {
                return null;
            }
            plan.addInsert(port, input);
        }
        return plan;
    }

    private static boolean configurationMatches(MachineRecipeRoute route, Map<String, MachinePort> ports) {
        for (MachineResourceStack configuration : route.configurationInputs()) {
            MachinePort port = ports.get(configuration.portId());
            if (port == null || !port.isConfiguration() || port.kind() != configuration.kind()) {
                return false;
            }
            MachineResourceStack current = port.peek();
            if (current == null || !current.sameResource(configuration)) {
                return false;
            }
        }
        return true;
    }

    private static boolean outputsHaveCapacity(MachineRecipeRoute route, Map<String, MachinePort> ports) {
        Map<String, List<MachineResourceStack>> requiredByPort = new LinkedHashMap<>();
        addOutputs(requiredByPort, route.guaranteedOutputs());
        addOutputs(requiredByPort, route.optionalOutputs());
        for (Map.Entry<String, List<MachineResourceStack>> entry : requiredByPort.entrySet()) {
            MachinePort port = ports.get(entry.getKey());
            if (port == null || port.isConfiguration() || !port.role().allowsOutput() ||
                !portHasOutputCapacity(port, entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static void addOutputs(Map<String, List<MachineResourceStack>> requiredByPort,
          List<MachineResourceStack> outputs) {
        for (MachineResourceStack output : outputs) {
            requiredByPort.computeIfAbsent(output.portId(), ignored -> new ArrayList<>()).add(output);
        }
    }

    private static boolean portHasOutputCapacity(MachinePort port, List<MachineResourceStack> outputs) {
        if (outputs.isEmpty()) {
            return true;
        }
        MachineResourceStack combined = outputs.get(0);
        if (port.kind() != combined.kind()) {
            return false;
        }
        boolean oneResource = true;
        long amount = combined.amount();
        for (int index = 1; index < outputs.size(); index++) {
            MachineResourceStack output = outputs.get(index);
            if (port.kind() != output.kind()) {
                return false;
            }
            if (!combined.sameResource(output)) {
                oneResource = false;
            } else if (Long.MAX_VALUE - amount < output.amount()) {
                return false;
            } else {
                amount += output.amount();
            }
        }
        if (oneResource) {
            MachineResourceStack required = combined.withAmount(amount);
            return port.getAvailableCapacity(required) >= required.amount();
        }
        return port.kind() == mekanism.api.processing.MachineResourceKind.ITEM &&
              groupedItemOutputsHaveCapacity(port, outputs);
    }

    private static boolean groupedItemOutputsHaveCapacity(MachinePort port,
          List<MachineResourceStack> outputs) {
        if (!(port.container() instanceof List<?> containers) || containers.isEmpty()) {
            return false;
        }
        List<VirtualItemSlot> slots = new ArrayList<>(containers.size());
        for (Object container : containers) {
            if (!(container instanceof IInventorySlot slot)) {
                return false;
            }
            slots.add(new VirtualItemSlot(slot));
        }
        for (MachineResourceStack output : outputs) {
            ItemStack stack = output.itemStack();
            if (stack.isEmpty()) {
                return false;
            }
            long remaining = output.amount();
            for (VirtualItemSlot slot : slots) {
                remaining -= slot.reserve(stack, remaining);
                if (remaining == 0) {
                    break;
                }
            }
            if (remaining != 0) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static MachineRecipeRoute scaleRoute(MachineRecipeRoute route, int operations) {
        if (operations <= 0 || operations > route.getMaxOperations()) {
            return null;
        }
        if (operations == 1) {
            return route;
        }
        try {
            MachineRecipeRoute.Builder builder = MachineRecipeRoute.builder(route.routeId())
                  .recipeKey(route.recipeKey()).logicalRecipeKey(route.logicalRecipeKey());
            route.configurationInputs().forEach(builder::configurationInput);
            route.inputs().forEach(stack -> builder.input(stack.scale(operations)));
            route.guaranteedOutputs().forEach(stack -> builder.output(stack.scale(operations)));
            route.optionalOutputs().forEach(stack -> builder.optionalOutput(stack.scale(operations)));
            return builder.build();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static MachineRecipeProviderRegistry.BoundProvider findDeclaredProvider(IAEUpgradeHost host,
          QIOAutomationMode mode) {
        if (!(host instanceof TileEntity tile)) {
            return null;
        }
        try {
            MachineRecipeProviderRegistry.BoundProvider provider = MachineRecipeProviderRegistry.find(tile);
            return provider != null && provider.isAvailable() && provider.getQIOConformance().supports(mode) ? provider : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static ProviderContext findContext(IAEUpgradeHost host, QIOAutomationMode mode) {
        MachineRecipeProviderRegistry.BoundProvider provider = findDeclaredProvider(host, mode);
        if (provider == null) {
            return null;
        }
        try {
            ProviderContextIdentity identity = new ProviderContextIdentity(provider.id(), provider.getRecipeSourceKey(),
                  provider.getConfigurationRevision());
            ContextCache cache = host.getAEUpgradeNode() == null ? null : host.getAEUpgradeNode().getProviderContextCache();
            CachedContext cached = cache == null ? null : cache.contexts.get(mode);
            if (cached != null && cached.identity.equals(identity)) {
                return cached.context;
            }
            ProviderConformanceReport report = provider.validateQIOConformance(mode);
            if (!report.isConformant()) {
                if (cache != null) {
                    cache.contexts.put(mode, new CachedContext(identity, null));
                }
                return null;
            }
            List<MachineRecipeRoute> routes = mode == QIOAutomationMode.SCHEDULED ?
                  provider.getRecipeRoutes() : Collections.emptyList();
            List<MachinePort> ports = provider.getPorts();
            Map<String, MachinePort> portsById = indexPorts(ports);
            if (portsById != null && mode == QIOAutomationMode.SCHEDULED &&
                !capturedRoutesConform(routes, portsById)) {
                portsById = null;
            }
            ProviderContext context = portsById == null ? null : new ProviderContext(identity, routes, ports, portsById);
            if (cache != null) {
                cache.contexts.put(mode, new CachedContext(identity, context));
            }
            return context;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static Map<String, MachinePort> indexPorts(List<MachinePort> ports) {
        if (ports == null || ports.isEmpty()) {
            return null;
        }
        Map<String, MachinePort> portsById = new LinkedHashMap<>();
        for (MachinePort port : ports) {
            if (port == null || portsById.put(port.portId(), port) != null) {
                return null;
            }
        }
        return portsById;
    }

    private static boolean capturedRoutesConform(List<MachineRecipeRoute> routes, Map<String, MachinePort> ports) {
        if (routes == null || routes.isEmpty()) {
            return false;
        }
        Set<String> recipeKeys = new HashSet<>();
        for (MachineRecipeRoute route : routes) {
            if (route == null || !recipeKeys.add(route.recipeKey())) {
                return false;
            }
            Set<String> configurationPorts = new HashSet<>();
            for (MachineResourceStack stack : route.configurationInputs()) {
                MachinePort port = ports.get(stack.portId());
                if (!configurationPorts.add(stack.portId()) || port == null || !port.isConfiguration() ||
                    port.kind() != stack.kind()) {
                    return false;
                }
            }
            if (!routeStacksConform(route.inputs(), ports, true) ||
                !routeStacksConform(route.guaranteedOutputs(), ports, false) ||
                !routeStacksConform(route.optionalOutputs(), ports, false)) {
                return false;
            }
        }
        return true;
    }

    private static boolean routeStacksConform(List<MachineResourceStack> stacks, Map<String, MachinePort> ports,
          boolean input) {
        for (MachineResourceStack stack : stacks) {
            MachinePort port = ports.get(stack.portId());
            if (port == null || port.isConfiguration() || port.kind() != stack.kind() ||
                (input && !port.role().acceptsInput()) || (!input && !port.role().allowsOutput())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Logical routes keep their resource shape while physical factory lanes only change port ids and recipe keys.
     */
    private static final class LogicalRouteKey {

        private final String routeId;
        private final String logicalRecipeKey;
        private final List<MachineResourceStack> configurationInputs;
        private final List<MachineResourceStack> inputs;
        private final List<MachineResourceStack> guaranteedOutputs;
        private final List<MachineResourceStack> optionalOutputs;

        private LogicalRouteKey(MachineRecipeRoute route, List<MachineResourceStack> configurationInputs,
              List<MachineResourceStack> inputs, List<MachineResourceStack> guaranteedOutputs,
              List<MachineResourceStack> optionalOutputs) {
            routeId = route.routeId();
            logicalRecipeKey = route.logicalRecipeKey();
            this.configurationInputs = configurationInputs;
            this.inputs = inputs;
            this.guaranteedOutputs = guaranteedOutputs;
            this.optionalOutputs = optionalOutputs;
        }

        @Nullable
        private static LogicalRouteKey create(@Nullable AERecipeRoute route, Map<String, MachinePort> ports) {
            MachineRecipeRoute converted = route == null ? null : route.toMachineRecipeRoute();
            return converted == null ? null : create(converted, ports);
        }

        @Nullable
        private static LogicalRouteKey create(@Nullable MachineRecipeRoute route, Map<String, MachinePort> ports) {
            if (route == null || ports == null) {
                return null;
            }
            List<MachineResourceStack> configurationInputs = normalize(route.configurationInputs(), ports);
            List<MachineResourceStack> inputs = normalize(route.inputs(), ports);
            List<MachineResourceStack> guaranteedOutputs = normalize(route.guaranteedOutputs(), ports);
            List<MachineResourceStack> optionalOutputs = normalize(route.optionalOutputs(), ports);
            if (configurationInputs == null || inputs == null || guaranteedOutputs == null || optionalOutputs == null) {
                return null;
            }
            return new LogicalRouteKey(route, configurationInputs, inputs, guaranteedOutputs, optionalOutputs);
        }

        @Nullable
        private static List<MachineResourceStack> normalize(List<MachineResourceStack> stacks,
              Map<String, MachinePort> ports) {
            List<MachineResourceStack> normalized = new ArrayList<>(stacks.size());
            for (MachineResourceStack stack : stacks) {
                MachinePort port = ports.get(stack.portId());
                if (port == null || port.kind() != stack.kind()) {
                    return null;
                }
                normalized.add(stack.withPort(port.portGroupId()));
            }
            return Collections.unmodifiableList(normalized);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof LogicalRouteKey other)) {
                return false;
            }
            return routeId.equals(other.routeId) && logicalRecipeKey.equals(other.logicalRecipeKey) &&
                  configurationInputs.equals(other.configurationInputs) && inputs.equals(other.inputs) &&
                  guaranteedOutputs.equals(other.guaranteedOutputs) && optionalOutputs.equals(other.optionalOutputs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(routeId, logicalRecipeKey, configurationInputs, inputs, guaranteedOutputs,
                  optionalOutputs);
        }
    }

    private static final class ProviderContext {

        @Nullable
        private final ProviderContextIdentity identity;
        private final List<MachineRecipeRoute> routes;
        private final Map<LogicalRouteKey, List<MachineRecipeRoute>> routesByLogicalKey;
        private final List<MachinePort> portList;
        private final Map<String, MachinePort> ports;

        private ProviderContext(@Nullable ProviderContextIdentity identity, List<MachineRecipeRoute> routes,
              List<MachinePort> portList,
              Map<String, MachinePort> ports) {
            this.identity = identity;
            this.routes = routes == null ? Collections.emptyList() : routes;
            Map<LogicalRouteKey, List<MachineRecipeRoute>> indexedRoutes = new LinkedHashMap<>();
            for (MachineRecipeRoute route : this.routes) {
                LogicalRouteKey logicalKey = LogicalRouteKey.create(route, ports);
                if (logicalKey != null) {
                    indexedRoutes.computeIfAbsent(logicalKey, ignored -> new ArrayList<>()).add(route);
                }
            }
            Map<LogicalRouteKey, List<MachineRecipeRoute>> immutableRoutes = new LinkedHashMap<>();
            indexedRoutes.forEach((key, value) -> immutableRoutes.put(key,
                  Collections.unmodifiableList(new ArrayList<>(value))));
            routesByLogicalKey = Collections.unmodifiableMap(immutableRoutes);
            this.portList = portList == null ? Collections.emptyList() : portList;
            this.ports = ports;
        }
    }

    /** Per-machine cache of catalogs which already passed strict Provider validation. */
    public static final class ContextCache {

        private final Map<QIOAutomationMode, CachedContext> contexts = new EnumMap<>(QIOAutomationMode.class);

        public void clear() {
            contexts.clear();
        }
    }

    private static final class CachedContext {

        private final ProviderContextIdentity identity;
        @Nullable
        private final ProviderContext context;

        private CachedContext(ProviderContextIdentity identity, @Nullable ProviderContext context) {
            this.identity = identity;
            this.context = context;
        }
    }

    private static final class ProviderContextIdentity {

        private final ResourceLocation providerId;
        @Nullable
        private final Object source;
        private final int configurationRevision;

        private ProviderContextIdentity(ResourceLocation providerId, @Nullable Object source, int configurationRevision) {
            this.providerId = providerId;
            this.source = source;
            this.configurationRevision = configurationRevision;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ProviderContextIdentity other)) {
                return false;
            }
            return configurationRevision == other.configurationRevision && providerId.equals(other.providerId) &&
                  Objects.equals(source, other.source);
        }

        @Override
        public int hashCode() {
            return Objects.hash(providerId, source, configurationRevision);
        }
    }

    private static final class ProviderRecipeSourceKey {

        private final ResourceLocation providerId;
        @Nullable
        private final Object providerSource;
        private final int configurationRevision;

        private ProviderRecipeSourceKey(ResourceLocation providerId, @Nullable Object providerSource,
              int configurationRevision) {
            this.providerId = providerId;
            this.providerSource = providerSource;
            this.configurationRevision = configurationRevision;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ProviderRecipeSourceKey other)) {
                return false;
            }
            return configurationRevision == other.configurationRevision && providerId.equals(other.providerId) &&
                  Objects.equals(providerSource, other.providerSource);
        }

        @Override
        public int hashCode() {
            return Objects.hash(providerId, providerSource, configurationRevision);
        }
    }

    private static final class VirtualItemSlot {

        private final IInventorySlot slot;
        private final ItemStack stored;
        @Nullable
        private ItemStack reservedResource;
        private long reserved;
        private long capacity = -1;

        private VirtualItemSlot(IInventorySlot slot) {
            this.slot = slot;
            ItemStack current = slot.getStack();
            stored = current == null || current.isEmpty() ? ItemStack.EMPTY : current.copy();
        }

        private long reserve(ItemStack resource, long requested) {
            if (requested <= 0 || !stored.isEmpty() && !ItemHandlerHelper.canItemStacksStack(stored, resource) ||
                reservedResource != null && !ItemHandlerHelper.canItemStacksStack(reservedResource, resource)) {
                return 0;
            }
            if (capacity < 0) {
                ItemStack offered = resource.copy();
                int existing = stored.isEmpty() ? 0 : stored.getCount();
                int room = Math.max(0, slot.getLimit(resource) - existing);
                offered.setCount(room);
                ItemStack remainder = room == 0 ? offered :
                      slot.insertItem(offered, Action.SIMULATE, AutomationType.INTERNAL);
                if (remainder == null || !remainder.isEmpty() &&
                    (!ItemHandlerHelper.canItemStacksStack(offered, remainder) || remainder.getCount() > offered.getCount())) {
                    return 0;
                }
                int rejected = remainder.isEmpty() ? 0 : remainder.getCount();
                capacity = Math.max(0, room - rejected);
                if (capacity == 0) {
                    capacity = -1;
                    return 0;
                }
                reservedResource = resource.copy();
                reservedResource.setCount(1);
            }
            long accepted = Math.min(requested, Math.max(0, capacity - reserved));
            reserved += accepted;
            return accepted;
        }
    }
}
