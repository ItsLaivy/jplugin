package codes.laivy.plugin.loader;

import codes.laivy.plugin.exception.PluginInitializeException;
import codes.laivy.plugin.exception.PluginInterruptException;
import codes.laivy.plugin.info.PluginInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public final class MethodPluginLoader implements PluginLoader {

    // Object

    private MethodPluginLoader() {
    }

    // Modules

    @Override
    public @NotNull PluginInfo create(@NotNull Class<?> reference, @Nullable String name, @NotNull PluginInfo @NotNull [] dependencies) {
        return new PluginInfoImpl(reference, name, dependencies);
    }

    // Classes

    private static final class PluginInfoImpl implements PluginInfo {

        // Object

        private final @Nullable String name;
        private final @NotNull Class<?> reference;

        private final @NotNull PluginInfo @NotNull [] dependencies;
        public final @NotNull Set<@NotNull PluginInfo> dependants = new LinkedHashSet<>();

        private volatile @NotNull State state = State.IDLE;
        private @Nullable Object instance;

        public PluginInfoImpl(@NotNull Class<?> reference, @Nullable String name, @NotNull PluginInfo @NotNull [] dependencies) {
            this.reference = reference;
            this.name = name;
            this.dependencies = dependencies;
        }

        // Getters

        public @NotNull Class<?> getReference() {
            return reference;
        }

        public @NotNull String getName() {
            return name != null ? name : getReference().getName();
        }

        @Override
        @Unmodifiable
        public @NotNull Collection<@NotNull PluginInfo> getDependencies() {
            return Arrays.asList(dependencies);
        }
        @Override
        public @NotNull Collection<@NotNull PluginInfo> getDependants() {
            return dependants;
        }

        @Override
        public @Nullable Object getInstance() {
            return instance;
        }

        public @NotNull State getState() {
            return state;
        }

        public boolean isRunning() {
            return getState() == State.RUNNING;
        }

        // Implementations

        @Override
        public boolean equals(@Nullable Object object) {
            if (this == object) return true;
            if (!(object instanceof PluginInfoImpl)) return false;
            @NotNull PluginInfoImpl that = (PluginInfoImpl) object;
            return Objects.equals(getReference(), that.getReference());
        }
        @Override
        public int hashCode() {
            return Objects.hashCode(getReference());
        }

        @Override
        public @NotNull String toString() {
            return getName();
        }

        // Modules

        @Override
        public void start() throws PluginInitializeException {
            state = State.STARTING;

            try {
                @NotNull Method method = getReference().getDeclaredMethod("initialize");
                method.setAccessible(true);

                // Invoke method
                this.instance = method.invoke(null);
            } catch (@NotNull Throwable throwable) {
                state = State.FAILED;

                if (throwable instanceof InvocationTargetException) {
                    if (throwable.getCause() instanceof PluginInitializeException) {
                        throw (PluginInitializeException) throwable.getCause();
                    }
                    throw new PluginInitializeException(getReference(), "cannot invoke initialize method", throwable.getCause());
                } else if (throwable instanceof NoSuchMethodException) {
                    throw new PluginInitializeException(getReference(), "cannot find initialize method", throwable);
                } else if (throwable instanceof IllegalAccessException) {
                    throw new PluginInitializeException(getReference(), "cannot access initialize method", throwable);
                } else {
                    throw new RuntimeException("cannot initialize plugin: " + getName(), throwable);
                }
            }

            // Mark as running
            state = State.RUNNING;
        }
        @Override
        public void close() throws PluginInterruptException {
            if (!isRunning()) {
                return;
            }

            // Dependencies
            @NotNull PluginInfo[] dependants = getDependants().stream().filter(dependency -> dependency.getState() != State.IDLE && dependency.getState() != State.FAILED).toArray(PluginInfo[]::new);

            if (dependants.length > 0) {
                @NotNull String list = Arrays.toString(dependants);
                list = list.substring(1, list.length() - 1);

                throw new PluginInterruptException(reference, "cannot interrupt plugin '" + getName() + "' because there's active dependants: " + list);
            }

            // Mark as stopping
            state = State.STOPPING;

            try {
                @NotNull Method method = getReference().getDeclaredMethod("interrupt");
                method.setAccessible(true);

                // Invoke method
                method.invoke(null);

                // Close instance
                if (getInstance() != null) try {
                    if (getInstance() instanceof Closeable) {
                        ((Closeable) getInstance()).close();
                    } else if (getInstance() instanceof Flushable) {
                        ((Flushable) getInstance()).flush();
                    }
                } catch (@NotNull IOException e) {
                    throw new PluginInterruptException(getReference(), "cannot close/flush plugin instance: " + getName());
                }
            } catch (@NotNull InvocationTargetException e) {
                if (e.getCause() instanceof PluginInterruptException) {
                    throw (PluginInterruptException) e.getCause();
                }

                throw new PluginInterruptException(getReference(), "cannot invoke interrupt method", e.getCause());
            } catch (@NotNull NoSuchMethodException e) {
                throw new PluginInterruptException(getReference(), "cannot find interrupt method", e);
            } catch (@NotNull IllegalAccessException e) {
                throw new PluginInterruptException(getReference(), "cannot access interrupt method", e);
            } finally {
                instance = null;
                state = State.IDLE;
            }
        }

    }

}
