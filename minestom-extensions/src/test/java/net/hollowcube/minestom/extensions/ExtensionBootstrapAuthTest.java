package net.hollowcube.minestom.extensions;

import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Pins that {@link ExtensionBootstrap} hands the caller's {@link Auth} to
 * {@link MinecraftServer#init(Auth)}.
 *
 * <p>This is the whole point of the {@code init(Auth)} overload and it cannot be worked around from
 * the outside: the {@code ServerProcess} keeps the {@link Auth} it was built with, so a bootstrap
 * that silently called {@code MinecraftServer.init()} would leave every server behind a Velocity
 * proxy on offline-mode authentication with no way to correct it afterwards.
 */
class ExtensionBootstrapAuthTest {

    @Test
    @DisplayName("init(Auth) binds the given auth to the server process")
    void initWithAuthBindsGivenAuth() {
        Auth.Velocity velocity = new Auth.Velocity("a-velocity-secret");

        ExtensionBootstrap.init(velocity);

        assertSame(velocity, MinecraftServer.process().auth());
    }

    @Test
    @DisplayName("init() keeps defaulting to offline mode")
    void initWithoutAuthDefaultsToOffline() {
        ExtensionBootstrap.init();

        assertInstanceOf(Auth.Offline.class, MinecraftServer.process().auth());
    }
}
