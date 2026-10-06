package org.example.testtaskidf.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.example.testtaskidf.controller.DirectRepositoryController;
import org.example.testtaskidf.repository.ArchitectureFixtureRepository;
import org.example.testtaskidf.util.UtilityFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureRulesTests {

    @Test
    void rejectsControllerAccessingRepositoryDirectly() {
        assertViolation(ArchitectureTests.LAYERS,
                DirectRepositoryController.class, ArchitectureFixtureRepository.class);
    }

    @Test
    void recognizesRestControllerMetaAnnotation() {
        assertViolation(ArchitectureTests.CONTROLLER_LOCATION, MisplacedWebEndpoint.class);
    }

    @Test
    void recognizesServiceAnnotation() {
        assertViolation(ArchitectureTests.SERVICE_LOCATION, MisplacedBusinessComponent.class);
    }

    @Test
    void rejectsInlineStaticHelpers() {
        assertViolation(ArchitectureTests.STATIC_HELPERS, InlineHelper.class);
    }

    @Test
    void rejectsUtilityClassOutsideUtilityPackage() {
        assertViolation(ArchitectureTests.UTILITY_LOCATION, MisplacedUtils.class);
    }

    @Test
    void rejectsStatefulUtilityClass() {
        assertViolation(ArchitectureTests.UTILITIES, UtilityFixtures.StatefulUtils.class);
    }

    @Test
    void acceptsStatelessUtilityClass() {
        var classes = new ClassFileImporter().importClasses(UtilityFixtures.ValidUtils.class);
        assertFalse(ArchitectureTests.UTILITIES.evaluate(classes).hasViolation());
        assertFalse(ArchitectureTests.STATIC_HELPERS.evaluate(classes).hasViolation());
    }

    private void assertViolation(ArchRule rule, Class<?>... types) {
        var classes = new ClassFileImporter().importClasses(types);
        assertTrue(rule.evaluate(classes).hasViolation(), rule.getDescription());
    }

    @RestController
    @Profile("architecture-fixtures")
    static class MisplacedWebEndpoint {
    }

    @Service
    @Profile("architecture-fixtures")
    static class MisplacedBusinessComponent {
    }

    static class InlineHelper {
        public static String normalize(String value) {
            return value.trim();
        }
    }

    static final class MisplacedUtils {
        private MisplacedUtils() {
        }
    }
}
