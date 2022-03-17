package org.testing.osgi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.junit5.service.ServiceExtension;
import org.testing.osgi.exception.second.SecondException;
import org.testing.osgi.security.SecurityConfig;
import org.testing.osgi.unprivileged.Unprivileged;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.testing.osgi.security.SecurityConfig.ALL_PERMISSIONS;
import static org.testing.osgi.unprivileged.Unprivileged.doUnprivileged;

@ExtendWith(ServiceExtension.class)
@TestInstance(PER_CLASS)
class QuasarInstrumentationTest {
    private static final String MESSAGE = "BOOM!";

    @BeforeAll
    void setSecurityPolicy(
        @InjectService(timeout = 1000)
        SecurityConfig securityConfig
    ) {
        securityConfig.setSecurityPolicy(
            // Any call-stack containing Unprivileged has no permissions.
            securityConfig.denyAllFor(Unprivileged.class),

            // Everyone else has all permissions.
            securityConfig.allow("*", ALL_PERMISSIONS)
        );
    }

    @Test
    void testSuperClasses() throws Exception {
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        PrintStream oldStderr = System.err;
        try (PrintStream stderr = new PrintStream(errors, true, UTF_8)) {
            System.setErr(stderr);
            assertThat(doUnprivileged(() -> ExceptionSuperClasses.throwException(MESSAGE)))
                .isInstanceOf(SecondException.class)
                .hasMessage(MESSAGE);
        } finally {
            System.setErr(oldStderr);

            // Copy the output back to stderr for people to see.
            System.err.write(errors.toByteArray());
        }

        String[] lines = errors.toString(UTF_8).split(System.lineSeparator());
        assertThat(lines)
            .contains("Caught: " + MESSAGE)
            .noneMatch(line -> line.startsWith("[quasar]") && line.contains("Can't determine super class of "));
    }
}
