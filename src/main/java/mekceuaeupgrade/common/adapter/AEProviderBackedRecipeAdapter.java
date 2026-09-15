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
import mekanism.common.recipe.RecipeHandler;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAERecipeMachineHost;
import mekceuaeupgrade.common.host.IAEUpgradeHost;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.route.AERecipeRoute;
import mekceuaeupgrade.common.transfer.AEUpgradeOutputDrainer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
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
    private static final boolean BOUNDED_CAPACITY = hasBoundedCapacity();
    private static final boolean ADMISSION_HINT = hasAdmissionHint();
    private static final ClassValue<Boolean> DEFAULT_BUSY_METHOD = new ClassValue<Boolean>() {
        @Override protected Boolean computeValue(Class<?> type) {
            return declaredByProviderHost(type, "canAcceptAnyAEItemInput");
        }
    };
    private static final ClassValue<Boolean> DEFAULT_INPUT_METHODS = new ClassValue<Boolean>() {
        @Override protected Boolean computeValue(Class<?> type) {
            return declaredByProviderHost(type, "canAcceptAEItemInputs", AEExposedRecipe.class, List.class) &&
                  declaredByProviderHost(type, "acceptAEItemInputs", AEExposedRecipe.class, List.class) &&
                  declaredByProviderHost(type, "canAcceptAEItemInput", AEExposedRecipe.class, ItemStack.class) &&
                  declaredByProviderHost(type, "acceptAEItemInput", AEExposedRecipe.class, ItemStack.class);
        }
    };

    private static boolean declaredByProviderHost(Class<?> type, String method, Class<?>... arguments) {
        try {
            return type.getMethod(method, arguments).getDeclaringClass() == IAERecipeMachineHost.class;
        } catch (ReflectiveOperationException | SecurityException unavailable) {
            return false;
        }
    }

    private static boolean hasAdmissionHint() {
        try {
            MachinePort.class.getMethod("mayHaveInputSpace");
            return true;
        } catch (ReflectiveOperationException | SecurityException unavailable) {
            return false;
        }
    }

    private static boolean hasBoundedCapacity() {
        try {
            MachinePort.class.getMethod("getAvailableCapacity", MachineResourceStack.class, long.class);
            return true;
        } catch (ReflectiveOperationException | SecurityException unavailable) { return false; }
    }

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
        for (BusyRoute route : context.busyRoutes) {
            if (configurationMatches(route.configurations, context.ports) && outputsHaveCapacity(route.outputs, context.ports)) {
                if (canExecuteInput((TileEntity) host, route.route, context.ports)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** No recipe iteration or resource materialization. False is only returned for definite admission failures. */
    public static boolean canAttemptItemInput(IAERecipeMachineHost host,
          Supplier<IAERecipeMachineAdapter> fallback) {
        if (!DEFAULT_BUSY_METHOD.get(host.getClass())) return host.canAcceptAnyAEItemInput();
        ProviderContext context = findContext(host, QIOAutomationMode.SCHEDULED);
        if (context == null) return fallback.get().canAcceptAnyItemInput(host);
        if (context.routes.isEmpty()) return false;
        if (!ADMISSION_HINT) return true;
        for (MachinePort input : context.admissionInputs) {
            if (input.mayHaveInputSpace()) return true;
        }
        return false;
    }

    /** Prepare once for this call and execute inside the caller's machine transaction; never cache a mutable plan. */
    public static boolean tryAcceptItemInputs(IAERecipeMachineHost host, AEExposedRecipe recipe,
          List<ItemStack> stacks, Supplier<IAERecipeMachineAdapter> fallback) {
        // Do not bypass extension hosts which override the established check/execute callbacks.
        if (!DEFAULT_INPUT_METHODS.get(host.getClass())) {
            return host.canAcceptAEItemInputs(recipe, stacks) && host.acceptAEItemInputs(recipe, stacks);
        }
        ProviderContext context = findContext(host, QIOAutomationMode.SCHEDULED);
        if (context == null) {
            IAERecipeMachineAdapter adapter = fallback.get();
            return adapter.canAcceptItemInputs(host, recipe, stacks) && adapter.acceptItemInputs(host, recipe, stacks);
        }
        MachineTransferPlan plan = createExecutableInputPlan((TileEntity) host, context, recipe, stacks);
        return plan != null && plan.execute();
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

    public static void observeOutputContainers(IAERecipeMachineHost host, Consumer<Object> observer,
          Supplier<IAERecipeMachineAdapter> fallback) {
        ProviderContext context = findContext(host, QIOAutomationMode.OUTPUT_ONLY);
        if (context == null) {
            fallback.get().observeOutputContainers(host, observer);
            return;
        }
        for (MachinePort port : context.portList) {
            if (!port.isConfiguration() && port.role().allowsOutput()) {
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
        for (int i = 0, size = candidates.size(); i < size; i++) {
            MachineRecipeRoute candidate = candidates.get(i);
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
        List<MachineResourceStack> inputs = route.inputs();
        for (int i = 0, size = inputs.size(); i < size; i++) {
            MachineResourceStack input = inputs.get(i);
            MachinePort port = ports.get(input.portId());
            if (port == null || port.isConfiguration() || !port.role().acceptsInput() || port.kind() != input.kind()) {
                return null;
            }
            plan.addInsert(port, input);
        }
        return plan;
    }

    /**
     * The busy query only needs to answer whether insertion is possible. A one-input route cannot have duplicate
     * containers, so constructing a transfer plan and entering its transaction wrapper adds no safety. Multi-input
     * routes retain the full plan validation because they need the duplicate-container and atomicity checks.
     */
    private static boolean canExecuteInput(@Nullable TileEntity tile, MachineRecipeRoute route,
          Map<String, MachinePort> ports) {
        if (tile == null || route.inputs().isEmpty()) {
            return false;
        }
        if (route.inputs().size() != 1) {
            MachineTransferPlan plan = inputPlan(tile, route, ports);
            return plan != null && plan.canExecute();
        }
        MachineResourceStack input = route.inputs().get(0);
        MachinePort port = ports.get(input.portId());
        return port != null && !port.isConfiguration() && port.role().acceptsInput() && port.kind() == input.kind() &&
              port.canInsert(input);
    }

    private static boolean configurationMatches(MachineRecipeRoute route, Map<String, MachinePort> ports) {
        List<MachineResourceStack> configurations = route.configurationInputs();
        for (int i = 0, size = configurations.size(); i < size; i++) {
            MachineResourceStack configuration = configurations.get(i);
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

    private static boolean configurationMatches(MachineResourceStack[] configurations, Map<String, MachinePort> ports) {
        for (MachineResourceStack configuration : configurations) {
            MachinePort port = ports.get(configuration.portId());
            if (port == null || !port.isConfiguration() || port.kind() != configuration.kind()) return false;
            MachineResourceStack current = port.peek();
            if (current == null || !current.sameResource(configuration)) return false;
        }
        return true;
    }

    private static boolean outputsHaveCapacity(MachineRecipeRoute route, Map<String, MachinePort> ports) {
        return outputsHaveCapacity(compileOutputRequirements(route), ports);
    }

    private static OutputRequirement[] compileOutputRequirements(MachineRecipeRoute route) {
        Map<String, List<MachineResourceStack>> requiredByPort = new LinkedHashMap<>();
        addOutputs(requiredByPort, route.guaranteedOutputs());
        addOutputs(requiredByPort, route.optionalOutputs());
        List<OutputRequirement> requirements = new ArrayList<>(requiredByPort.size());
        for (Map.Entry<String, List<MachineResourceStack>> entry : requiredByPort.entrySet()) {
            requirements.add(new OutputRequirement(entry.getKey(), entry.getValue()));
        }
        return requirements.toArray(new OutputRequirement[0]);
    }

    private static boolean outputsHaveCapacity(OutputRequirement[] requirements, Map<String, MachinePort> ports) {
        for (OutputRequirement requirement : requirements) {
            MachinePort port = ports.get(requirement.portId);
            if (port == null || port.isConfiguration() || !port.role().allowsOutput() ||
                !portHasOutputCapacity(port, requirement)) {
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

    private static boolean portHasOutputCapacity(MachinePort port, OutputRequirement requirement) {
        if (!requirement.valid) return false;
        MachineResourceStack required = requirement.combined;
        if (required != null) {
            if (port.kind() != required.kind()) return false;
            long capacity = BOUNDED_CAPACITY ? port.getAvailableCapacity(required, required.amount()) : port.getAvailableCapacity(required);
            return capacity >= required.amount();
        }
        return port.kind() == mekanism.api.processing.MachineResourceKind.ITEM &&
              groupedItemOutputsHaveCapacity(port, requirement.outputs);
    }

    private static final class OutputRequirement {
        private final String portId;
        private final List<MachineResourceStack> outputs;
        @Nullable
        private final MachineResourceStack combined;
        private final boolean valid;

        private OutputRequirement(String portId, List<MachineResourceStack> outputs) {
            this.portId = portId;
            this.outputs = Collections.unmodifiableList(new ArrayList<>(outputs));
            MachineResourceStack first = outputs.get(0);
            long amount = first.amount();
            boolean sameResource = true;
            boolean valid = true;
            for (int i = 1; i < outputs.size(); i++) {
                MachineResourceStack output = outputs.get(i);
                if (first.kind() != output.kind()) { valid = false; break; }
                if (!first.sameResource(output)) sameResource = false;
                else if (Long.MAX_VALUE - amount < output.amount()) { valid = false; break; }
                else amount += output.amount();
            }
            this.valid = valid;
            combined = valid && sameResource ? first.withAmount(amount) : null;
        }
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
            ProviderConformanceReport report = provider.validateQIOEndpointConformance(mode);
            if (!report.isConformant()) {
                if (cache != null) {
                    cache.contexts.put(mode, new CachedContext(identity, null));
                }
                return null;
            }
            List<MachineRecipeRoute> routes = mode == QIOAutomationMode.SCHEDULED ?
                  normalizeRoutes(provider.getRecipeRoutes()) : Collections.emptyList();
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
    private static List<MachineRecipeRoute> normalizeRoutes(List<MachineRecipeRoute> routes) {
        Map<String, MachineRecipeRoute> unique = new LinkedHashMap<>();
        for (MachineRecipeRoute route : routes) {
            if (route == null) return null;
            MachineRecipeRoute previous = unique.putIfAbsent(route.recipeKey(), route);
            // Ore dictionary expansion can describe the same route more than once.
            if (previous != null && (!previous.routeId().equals(route.routeId()) ||
                  !previous.logicalRecipeKey().equals(route.logicalRecipeKey()) ||
                  !sameResources(previous.configurationInputs(), route.configurationInputs()) ||
                  !sameResources(previous.inputs(), route.inputs()) ||
                  !sameResources(previous.guaranteedOutputs(), route.guaranteedOutputs()) ||
                  !sameResources(previous.optionalOutputs(), route.optionalOutputs()))) {
                return null;
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static boolean sameResources(List<MachineResourceStack> first, List<MachineResourceStack> second) {
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            if (!first.get(i).write(new NBTTagCompound()).equals(second.get(i).write(new NBTTagCompound()))) return false;
        }
        return true;
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
        if (routes == null) {
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
                    !stack.isResolved() || !port.acceptsResource(stack)) {
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
            if (port == null || port.isConfiguration() || !stack.isResolved() || !port.acceptsResource(stack) ||
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
        private final BusyRoute[] busyRoutes;
        private final MachinePort[] admissionInputs;

        private ProviderContext(@Nullable ProviderContextIdentity identity, List<MachineRecipeRoute> routes,
              List<MachinePort> portList,
              Map<String, MachinePort> ports) {
            this.identity = identity;
            this.routes = routes == null ? Collections.emptyList() : routes;
            busyRoutes = new BusyRoute[this.routes.size()];
            for (int i = 0; i < busyRoutes.length; i++) busyRoutes[i] = new BusyRoute(this.routes.get(i));
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
            List<MachinePort> inputs = new ArrayList<>();
            for (MachinePort port : this.portList) {
                if (!port.isConfiguration() && port.role().acceptsInput()) inputs.add(port);
            }
            admissionInputs = inputs.toArray(new MachinePort[0]);
        }

    }

    /** Private arrays contain only immutable recipe metadata and expire with the provider context. */
    private static final class BusyRoute {
        private final MachineRecipeRoute route;
        private final MachineResourceStack[] configurations;
        private final OutputRequirement[] outputs;

        private BusyRoute(MachineRecipeRoute route) {
            this.route = route;
            configurations = route.configurationInputs().toArray(new MachineResourceStack[0]);
            outputs = compileOutputRequirements(route);
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
        private final int recipeVersion = RecipeHandler.getGlobalRecipeVersion();

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
            return recipeVersion == other.recipeVersion && configurationRevision == other.configurationRevision && providerId.equals(other.providerId) &&
                  Objects.equals(source, other.source);
        }

        @Override
        public int hashCode() {
            return Objects.hash(providerId, source, configurationRevision, recipeVersion);
        }
    }

    private static final class ProviderRecipeSourceKey {

        private final ResourceLocation providerId;
        @Nullable
        private final Object providerSource;
        private final int configurationRevision;
        private final int recipeVersion = RecipeHandler.getGlobalRecipeVersion();

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
            return recipeVersion == other.recipeVersion && configurationRevision == other.configurationRevision && providerId.equals(other.providerId) &&
                  Objects.equals(providerSource, other.providerSource);
        }

        @Override
        public int hashCode() {
            return Objects.hash(providerId, providerSource, configurationRevision, recipeVersion);
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
                int accepted = room == 0 ? 0 : slot.insertItemCount(offered, Action.SIMULATE, AutomationType.INTERNAL);
                capacity = Math.max(0, accepted);
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
