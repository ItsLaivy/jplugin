package codes.laivy.plugin.info;

import codes.laivy.plugin.annotation.Category;
import codes.laivy.plugin.annotation.Initializer;
import codes.laivy.plugin.category.PluginHandler;
import codes.laivy.plugin.exception.PluginInitializeException;
import codes.laivy.plugin.exception.PluginInterruptException;
import codes.laivy.plugin.factory.handlers.Handlers;
import codes.laivy.plugin.initializer.ConstructorPluginInitializer;
import codes.laivy.plugin.initializer.PluginInitializer;
import codes.laivy.plugin.main.Plugins;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

public abstract class PluginInfo {

    // Object

    private final @Nullable String name;
    private final @Nullable String description;

    private final @NotNull Class<?> reference;

    private final @NotNull PluginInfo @NotNull [] dependencies;
    public final @NotNull Set<@NotNull PluginInfo> dependants = new LinkedHashSet<>();

    private final @NotNull Set<String> categories;
    private final @NotNull Class<? extends PluginInitializer> initializer;

    private volatile @NotNull State state = State.IDLE;
    protected @Nullable Object instance;

    private final @NotNull Handlers handlers = Handlers.create();

    public PluginInfo(@NotNull Class<?> reference, @Nullable String name, @Nullable String description, @NotNull PluginInfo @NotNull [] dependencies, @NotNull String @NotNull [] categories, @NotNull Class<? extends PluginInitializer> initializer) {
        this.name = name;
        this.description = description;

        this.reference = reference;
        this.dependencies = dependencies;

        this.categories = new HashSet<>(Arrays.asList(categories));
        this.initializer = initializer;
    }

    // Getters

    public @Nullable String getName() {
        return name;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public final @NotNull State getState() {
        return state;
    }
    protected void setState(@NotNull State state) {
        @NotNull State previous = this.state;
        this.state = state;

        handle("change state", (handler) -> handler.state(this, previous));

        if (state == State.RUNNING) {
            handle("mark as running", (handler) -> handler.run(this));
        }
    }

    public final @NotNull Class<?> getReference() {
        return reference;
    }

    @Unmodifiable
    public @NotNull Collection<@NotNull PluginInfo> getDependencies() {
        return Arrays.asList(dependencies);
    }
    public final @NotNull Collection<@NotNull PluginInfo> getDependants() {
        return dependants;
    }

    public @NotNull Collection<String> getCategories() {
        return categories;
    }
    public @NotNull Class<? extends PluginInitializer> getInitializer() {
        return initializer;
    }

    public @NotNull Handlers getHandlers() {
        return handlers;
    }

    /**
     * Represents the instance of this plugin, the instance of the reference. It could be
     * null if the {@link Initializer} uses a PluginInitializer that generates the instance like {@link ConstructorPluginInitializer}
     *
     * @return
     */
    public final @Nullable Object getInstance() {
        return instance;
    }

    // Modules

    public void start() throws PluginInitializeException {
        setState(State.STARTING);
        handle("start", (handler) -> handler.start(this));
    }
    public void close() throws PluginInterruptException {
        if (getState().isRunning()) {
            return;
        }

        // Dependencies
        @NotNull PluginInfo[] dependants = getDependants().stream().filter(dependency -> dependency.getState() != State.IDLE && dependency.getState() != State.FAILED).toArray(PluginInfo[]::new);

        if (dependants.length > 0) {
            @NotNull String list = Arrays.toString(dependants);
            list = list.substring(1, list.length() - 1);

            throw new IllegalStateException("cannot interrupt plugin '" + this + "' because there's active dependants: " + list);
        }

        // Mark as stopping
        setState(State.STOPPING);
        handle("close", (handler) -> handler.close(this));

        // The implementation should do the rest
    }

    // Implementations

    @Override
    public final boolean equals(@Nullable Object object) {
        if (this == object) return true;
        if (!(object instanceof PluginInfo)) return false;
        @NotNull PluginInfo that = (PluginInfo) object;
        return Objects.equals(getReference(), that.getReference());
    }
    @Override
    public final int hashCode() {
        return Objects.hashCode(getReference());
    }

    @Override
    public final @NotNull String toString() {
        return name != null ? name : getReference().getName();
    }

    // Classes

    public enum State {

        /**
         * In case the plugin is idle. A plugin can be idle when it's been created or when it fully stops running
         */
        IDLE,

        /**
         * The failed state of a plugin can be caused if the plugin failed to start. This state is considered an idle state
         * If the plugin fail to stop, it will not receive this state, it will receive the idle state normally.
         *
         */
        FAILED,

        STARTING,

        /**
         * When the plugin is running
         */
        RUNNING,
        STOPPING,
        ;

        public boolean isRunning() {
            return this == RUNNING;
        }
        public boolean isIdle() {
            return this == IDLE || this == FAILED;
        }

    }

    // Utilities

    private void handle(@NotNull String action, @NotNull ThrowingConsumer<PluginHandler> consumer) {
        // Call handlers
        {
            // Plugin handlers
            for (@NotNull PluginHandler handler : getHandlers()) {
                try {
                    consumer.accept(handler);
                } catch (@NotNull Throwable throwable) {
                    throw new RuntimeException("cannot invoke plugin's handler to " + action + " '" + this + "': " + handler);
                }
            }

            // Category handlers
            for (@NotNull Category category : getReference().getAnnotationsByType(Category.class)) {
                @NotNull String name = category.name();

                for (@NotNull PluginHandler handler : Plugins.getFactory().getHandlers(name)) {
                    try {
                        consumer.accept(handler);
                    } catch (@NotNull Throwable throwable) {
                        throw new RuntimeException("cannot invoke category's handler to " + action + " '" + this + "': " + handler);
                    }
                }
            }

            // Global handlers
            for (@NotNull PluginHandler handler : Plugins.getFactory().getHandlers()) {
                try {
                    consumer.accept(handler);
                } catch (@NotNull Throwable throwable) {
                    throw new RuntimeException("cannot invoke global handler to " + action + " '" + this + "': " + handler);
                }
            }
        }
    }
    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T t) throws Throwable;
    }

}
