package codes.laivy.plugin.main;

import codes.laivy.plugin.PluginInfo;
import codes.laivy.plugin.PluginInfo.Builder;
import codes.laivy.plugin.annotation.Priority;
import codes.laivy.plugin.category.AbstractPluginCategory;
import codes.laivy.plugin.category.PluginCategory;
import codes.laivy.plugin.exception.PluginInitializeException;
import codes.laivy.plugin.exception.PluginInterruptException;
import codes.laivy.plugin.factory.PluginFactory;
import codes.laivy.plugin.factory.PluginFinder;
import codes.laivy.plugin.factory.handlers.Handlers;
import codes.laivy.plugin.factory.handlers.PluginHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

final class PluginFactoryImpl implements PluginFactory {

    // Object

    private final @NotNull Map<String, PluginCategory> categories = new HashMap<>();
    private final @NotNull Handlers handlers = Handlers.create();

    final @NotNull Map<Class<?>, PluginInfo> plugins = new LinkedHashMap<>();

    public PluginFactoryImpl() {
        // Just to initialize Plugins class (shutdown hook)
        Class<Plugins> reference = Plugins.class;

        // Default categories
        setCategory(new AutoRegisterPluginCategory());
    }

    // Getters

    @Override
    public @NotNull PluginInfo retrieve(@NotNull Class<?> reference) {
        return plugins.values().stream().filter(plugin -> plugin.getReference().equals(reference)).findFirst().orElseThrow(() -> new IllegalArgumentException("the class '" + reference.getName() + "' isn't a plugin"));
    }
    @Override
    public @NotNull PluginInfo retrieve(@NotNull String name) {
        return plugins.values().stream().filter(plugin -> Objects.equals(plugin.getName(), name)).findFirst().orElseThrow(() -> new IllegalArgumentException("there's no plugin with name '" + name + "'"));
    }

    @Override
    public @NotNull Handlers getGlobalHandlers() {
        return handlers;
    }

    // Handlers

    @Override
    public @NotNull PluginCategory getCategory(@NotNull String name) {
        return categories.computeIfAbsent(name.toLowerCase(), k -> new AbstractPluginCategory(name) {});
    }
    @Override
    public @NotNull Optional<PluginCategory> getCategory(@NotNull String name, boolean create) {
        if (create) {
            return Optional.of(categories.computeIfAbsent(name.toLowerCase(), k -> new AbstractPluginCategory(name) {}));
        } else {
            return Optional.ofNullable(categories.getOrDefault(name.toLowerCase(), null));
        }
    }
    @Override
    public boolean hasCategory(@NotNull String name) {
        return categories.containsKey(name.toLowerCase());
    }

    @Override
    public void setCategory(@NotNull PluginCategory category) {
        categories.put(category.getName().toLowerCase(), category);
    }

    // Instances

    @Override
    public @NotNull <T> Optional<T> getInstance(@NotNull Class<?> reference) {
        @NotNull PluginInfo plugin = retrieve(reference);

        //noinspection unchecked
        return Optional.ofNullable((T) plugin.getInstance());
    }

    // Initialization and interruption

    @Override
    public void interrupt(@NotNull ClassLoader loader, @NotNull String packge, boolean recursive) throws PluginInterruptException {
        @NotNull List<PluginInfo> plugins = new LinkedList<>(PluginFactoryImpl.this.plugins.values());
        Collections.reverse(plugins);

        for (@NotNull PluginInfo info : plugins) {
            @NotNull String two = info.getReference().getPackage().getName();
            boolean isWithin = recursive ? two.startsWith(packge) : two.equals(packge);

            if (info.getReference().getClassLoader().equals(loader) && !isWithin) {
                continue;
            }

            info.close();
        }
    }
    @Override
    public @NotNull PluginInfo @NotNull [] initialize(@NotNull ClassLoader loader, @NotNull String packge, boolean recursive) throws PluginInitializeException, IOException {
        @NotNull PluginFinder finder = find();
        finder.addClassLoader(loader);
        finder.addPackage(packge, recursive);

        return finder.load();
    }

    @Override
    public void interrupt(@NotNull String packge, boolean recursive) throws PluginInterruptException {
        interrupt(Thread.currentThread().getContextClassLoader(), packge, recursive);
    }
    @Override
    public @NotNull PluginInfo @NotNull [] initialize(@NotNull String packge, boolean recursive) throws PluginInitializeException, IOException {
        @NotNull PluginFinder finder = find();
        finder.addPackage(packge, recursive);

        return finder.load();
    }

    @Override
    public void interrupt(@NotNull ClassLoader loader, @NotNull Package packge, boolean recursive) throws PluginInterruptException {
        interrupt(loader, packge.getName(), recursive);
    }
    @Override
    public @NotNull PluginInfo @NotNull [] initialize(@NotNull ClassLoader loader, @NotNull Package packge, boolean recursive) throws PluginInitializeException, IOException {
        @NotNull PluginFinder finder = find();
        finder.addClassLoader(loader);
        finder.addPackage(packge, recursive);

        return finder.load();
    }

    @Override
    public void interrupt(@NotNull Package packge, boolean recursive) throws PluginInterruptException {
        interrupt(Thread.currentThread().getContextClassLoader(), packge.getName(), recursive);
    }
    @Override
    public @NotNull PluginInfo @NotNull [] initialize(@NotNull Package packge, boolean recursive) throws PluginInitializeException, IOException {
        @NotNull PluginFinder finder = find();
        finder.addPackage(packge, recursive);

        return finder.load();
    }

    @Override
    public void interrupt(@NotNull ClassLoader loader) throws PluginInterruptException {
        @NotNull List<PluginInfo> plugins = new LinkedList<>(PluginFactoryImpl.this.plugins.values());
        Collections.reverse(plugins);

        for (@NotNull PluginInfo info : plugins) {
            @NotNull Class<?> reference = info.getReference();

            if (reference.getClassLoader().equals(loader)) {
                info.close();
            }
        }
    }
    @Override
    @ApiStatus.Experimental
    public @NotNull PluginInfo @NotNull [] initialize(@NotNull ClassLoader loader) throws PluginInitializeException, IOException {
        @NotNull PluginFinder finder = find();
        finder.addClassLoader(loader);

        return finder.load();
    }

    @Override
    @ApiStatus.Experimental
    public @NotNull PluginInfo @NotNull [] initializeAll() throws PluginInitializeException, IOException {
        @NotNull PluginFinder finder = find();
        return finder.load();
    }
    @Override
    public void interruptAll() throws PluginInterruptException {
        @NotNull List<PluginInfo> plugins = new LinkedList<>(PluginFactoryImpl.this.plugins.values());
        Collections.reverse(plugins);

        for (@NotNull PluginInfo info : plugins) {
            info.close();
        }
    }

    // Finders

    @Override
    public @NotNull PluginFinder find() {
        return new PluginFinderImpl(this);
    }

    // Iterator and stream

    @Override
    public @NotNull Iterator<PluginInfo> iterator() {
        return plugins.values().iterator();
    }
    @Override
    public @NotNull Stream<PluginInfo> stream() {
        return plugins.values().stream();
    }

    // Classes

    private final class AutoRegisterPluginCategory extends AbstractPluginCategory {

        // Object

        private AutoRegisterPluginCategory() {
            super("Category Reference");
        }

        // Modules

        @Override
        public boolean accept(@NotNull Builder builder) {
            if (PluginCategory.class.isAssignableFrom(builder.getReference())) {
                builder.comparable(o -> -1);
                return true;
            } else {
                return false;
            }
        }
        @Override
        public boolean accept(@NotNull PluginInfo info) {
            if (PluginCategory.class.isAssignableFrom(info.getReference())) {
                info.getHandlers().add(new HandlerImpl(info.getReference()));

                return true;
            } else {
                return false;
            }
        }

        @Override
        public void start(@NotNull PluginInfo info) throws PluginInitializeException {
            if (!(info.getInstance() instanceof PluginCategory)) {
                throw new PluginInitializeException(info.getReference(), "the 'Category Reference' plugin doesn't have a plugin category instance: " + info.getInstance());
            }

            @NotNull PluginCategory category = (PluginCategory) info.getInstance();
            categories.put(category.getName().toLowerCase(), category);
        }
        @Override
        public void close(@NotNull PluginInfo info) throws PluginInterruptException {
            if (!(info.getInstance() instanceof PluginCategory)) {
                throw new PluginInterruptException(info.getReference(), "the 'Category Reference' plugin doesn't have the plugin category instance: " + info.getInstance());
            }

            @NotNull PluginCategory category = (PluginCategory) info.getInstance();

            category.getPlugins().clear();
            categories.remove(category.getName().toLowerCase());
        }

        // Classes

        private final class HandlerImpl implements PluginHandler {

            // Object

            private final @NotNull Class<?> category;

            private HandlerImpl(@NotNull Class<?> category) {
                this.category = category;
            }

            // Modules

            @Override
            public boolean accept(@NotNull Builder builder) {
                builder.dependency(category);
                return PluginHandler.super.accept(builder);
            }
            @Override
            public boolean accept(@NotNull PluginInfo info) {
                info.getDependencies().remove(Plugins.retrieve(category));
                return PluginHandler.super.accept(info);
            }

        }

    }

}
