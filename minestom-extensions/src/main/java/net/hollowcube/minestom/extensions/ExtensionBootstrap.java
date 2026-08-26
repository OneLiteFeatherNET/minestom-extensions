package net.hollowcube.minestom.extensions;

import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.extensions.ExtensionManager;
import net.minestom.server.utils.validate.Check;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public final class ExtensionBootstrap {
    private static ExtensionManager extensions = null;

    /**
     * Initialises the server with offline-mode authentication.
     *
     * @return the bootstrap to {@link #start(String, int)} once setup is done
     */
    public static @NotNull ExtensionBootstrap init() {
        return init(new Auth.Offline());
    }

    /**
     * Initialises the server with the given authentication mode.
     *
     * <p>The {@link Auth} is bound to the {@code ServerProcess} by {@link MinecraftServer#init(Auth)}
     * and cannot be swapped afterwards, so a server that sits behind a Velocity proxy has to pass
     * {@link Auth.Velocity} here - there is no way to switch it on later.
     *
     * @param auth how incoming connections are authenticated, e.g. {@link Auth.Velocity} for a
     *             server behind a Velocity proxy
     * @return the bootstrap to {@link #start(String, int)} once setup is done
     */
    public static @NotNull ExtensionBootstrap init(@NotNull Auth auth) {
        return new ExtensionBootstrap(MinecraftServer.init(auth));
    }

    public static @NotNull ExtensionManager getExtensionManager() {
        Check.notNull(extensions, "ExtensionBootstrap has not been initialized yet!");
        return extensions;
    }

    private final MinecraftServer server;

    private ExtensionBootstrap(@NotNull MinecraftServer server) {
        this.server = server;
        extensions = new ExtensionManager(MinecraftServer.process());
        MinecraftServer.getSchedulerManager().buildShutdownTask(extensions::shutdown);

        extensions.start();
        extensions.gotoPreInit();
    }

    public void start(@NotNull String address, int port) {
        start(new InetSocketAddress(address, port));
    }

    public void start(@NotNull SocketAddress address) {
        extensions.gotoInit();
        this.server.start(address);
        extensions.gotoPostInit();
    }

    public void shutdown() {
        extensions.shutdown();
    }

}
