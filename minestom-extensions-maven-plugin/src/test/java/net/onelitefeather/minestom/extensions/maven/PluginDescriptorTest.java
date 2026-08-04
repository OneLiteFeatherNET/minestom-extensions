package net.onelitefeather.minestom.extensions.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the hand-written {@code META-INF/maven/plugin.xml} in sync with {@link
 * ExtensionDescriptorMojo}.
 *
 * <p>A Maven build would generate that file from the annotations. This module is built with Gradle,
 * where the equivalent plugin calls an API Gradle 9 removed, so the descriptor is maintained by
 * hand — and a hand-written descriptor drifts. The failure mode is unpleasant: Maven reports a
 * parameter as unknown, or silently never injects it, and the goal misbehaves in the user's build
 * rather than in ours. These assertions move that failure here.
 *
 * <p>The mojo's own annotations cannot be used for the comparison: Maven's {@code @Mojo} and
 * {@code @Parameter} are {@code RetentionPolicy.CLASS}, so they do not exist at runtime. The check
 * therefore runs against the declared fields, which is what Maven ultimately injects into anyway.
 */
class PluginDescriptorTest {

    private static final String DESCRIPTOR = "/META-INF/maven/plugin.xml";

    @Test
    @DisplayName("every injectable field of the mojo is declared in plugin.xml, and vice versa")
    void parametersMatchTheMojoFields() throws Exception {
        final Set<String> declared = declaredParameters();

        final Set<String> fields = new TreeSet<>();
        for (Field field : ExtensionDescriptorMojo.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                fields.add(field.getName());
            }
        }

        assertAll(
                () -> assertEquals(fields, declared,
                        "plugin.xml and the mojo's fields disagree — add, rename or remove the "
                                + "matching <parameter> and <configuration> entry"),
                () -> assertFalse(fields.isEmpty(), "the mojo declares no fields at all"));
    }

    @Test
    @DisplayName("the declared parameter types match the field types")
    void parameterTypesMatchTheMojo() throws Exception {
        final NodeList parameters = descriptor().getElementsByTagName("parameter");
        assertTrue(parameters.getLength() > 0, "no parameters declared");

        for (int i = 0; i < parameters.getLength(); i++) {
            final Element parameter = (Element) parameters.item(i);
            final String name = text(parameter, "name");
            final String type = text(parameter, "type");

            final Field field = ExtensionDescriptorMojo.class.getDeclaredField(name);
            assertEquals(field.getType().getName(), type,
                    () -> "declared type of '" + name + "' does not match the field");
        }
    }

    @Test
    @DisplayName("every parameter also has a configuration entry, which is what Maven injects from")
    void everyParameterHasAConfigurationEntry() throws Exception {
        final Element configuration =
                (Element) descriptor().getElementsByTagName("configuration").item(0);
        assertNotNull(configuration, "plugin.xml has no <configuration> block");

        final Set<String> configured = new TreeSet<>();
        final NodeList children = configuration.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element element) {
                configured.add(element.getTagName());
            }
        }

        assertEquals(declaredParameters(), configured,
                "<parameters> and <configuration> disagree — Maven would not inject the difference");
    }

    @Test
    @DisplayName("the implementation class exists and is a mojo")
    void implementationClassIsUsable() throws Exception {
        final Element mojo = (Element) descriptor().getElementsByTagName("mojo").item(0);
        final String implementation = text(mojo, "implementation");

        final Class<?> type = Class.forName(implementation);
        assertAll(
                () -> assertTrue(AbstractMojo.class.isAssignableFrom(type),
                        implementation + " does not extend AbstractMojo"),
                () -> assertFalse(Modifier.isAbstract(type.getModifiers()),
                        implementation + " is abstract and could not be instantiated by Maven"),
                () -> assertNotNull(type.getConstructor(),
                        implementation + " has no public no-arg constructor"));
    }

    @Test
    @DisplayName("goal and phase are set to the documented values")
    void goalAndPhaseAreDeclared() throws Exception {
        final Element mojo = (Element) descriptor().getElementsByTagName("mojo").item(0);

        assertAll(
                () -> assertEquals("describe", text(mojo, "goal")),
                // process-classes runs after compile, so the processor has written the descriptor.
                () -> assertEquals("process-classes", text(mojo, "phase")),
                // provided-scope artifacts are only populated once dependencies are resolved.
                () -> assertEquals("compile", text(mojo, "requiresDependencyResolution")),
                () -> assertEquals("true", text(mojo, "threadSafe")));
    }

    @Test
    @DisplayName("the version placeholder is filtered in by the build")
    void versionIsFiltered() throws Exception {
        final String version = text(descriptor().getDocumentElement(), "version");
        assertAll(
                () -> assertNotNull(version),
                () -> assertFalse(version.contains("@"),
                        () -> "the version token was not replaced: " + version),
                () -> assertFalse(version.contains("${"),
                        () -> "the version still holds a placeholder: " + version));
    }

    private static Set<String> declaredParameters() throws Exception {
        final NodeList nodes = descriptor().getElementsByTagName("parameter");
        final Set<String> names = new LinkedHashSet<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            names.add(text((Element) nodes.item(i), "name"));
        }
        return new TreeSet<>(names);
    }

    private static Document descriptor() throws Exception {
        try (InputStream in = PluginDescriptorTest.class.getResourceAsStream(DESCRIPTOR)) {
            assertNotNull(in, "missing " + DESCRIPTOR + " on the test classpath");
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        }
    }

    /** First matching descendant, which is unambiguous for the elements looked up here. */
    private static String text(Element parent, String tag) {
        final NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }
}
