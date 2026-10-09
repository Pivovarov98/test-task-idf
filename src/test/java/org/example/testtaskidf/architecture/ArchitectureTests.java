package org.example.testtaskidf.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.example.testtaskidf.TestTaskIdfApplication;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "org.example.testtaskidf", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

    @ArchTest
    static final ArchRule LAYERS = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .optionalLayer("Controller").definedBy("..controller..")
            .optionalLayer("Service").definedBy("..service..")
            .optionalLayer("Repository").definedBy("..repository..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service");

    @ArchTest
    static final ArchRule PACKAGES = classes()
            .that().doNotHaveFullyQualifiedName(TestTaskIdfApplication.class.getName())
            .should().resideInAnyPackage(
                    "..controller..", "..service..", "..repository..", "..model..",
                    "..dto..", "..config..", "..exception..", "..util..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule PACKAGE_CYCLES = slices().matching("org.example.testtaskidf.(*)..")
            .should().beFreeOfCycles().allowEmptyShould(true);

    @ArchTest
    static final ArchRule CONTROLLER_LOCATION = classes().that().areMetaAnnotatedWith(Controller.class)
            .should().resideInAPackage("..controller..").allowEmptyShould(true);

    @ArchTest
    static final ArchRule SERVICE_LOCATION = classes().that().areMetaAnnotatedWith(Service.class)
            .should().resideInAPackage("..service..").allowEmptyShould(true);

    @ArchTest
    static final ArchRule REPOSITORY_LOCATION = classes().that().areMetaAnnotatedWith(Repository.class)
            .should().resideInAPackage("..repository..").allowEmptyShould(true);

    @ArchTest
    static final ArchRule CONTROLLER_NAMES = classes().that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage("..controller..").allowEmptyShould(true);

    @ArchTest
    static final ArchRule SERVICE_NAMES = classes().that().haveNameMatching(".*Service(Impl)?")
            .should().resideInAPackage("..service..").allowEmptyShould(true);

    @ArchTest
    static final ArchRule REPOSITORY_NAMES = classes().that().haveNameMatching(".*Repository(Impl)?")
            .should().resideInAPackage("..repository..").allowEmptyShould(true);

    @ArchTest
    static final ArchRule UTILITY_LOCATION = classes().that().haveNameMatching(".*(Util|Utils)")
            .should().resideInAPackage("..util..").allowEmptyShould(true);

    @ArchTest
    static final ArchRule STATIC_HELPERS = methods().that().arePublic().and().areStatic()
            .and(new DescribedPredicate<JavaMethod>("are not compiler-generated enum methods") {
                @Override
                public boolean test(JavaMethod method) {
                    return !method.getOwner().isEnum()
                            || !(method.getName().equals("values") && method.getRawParameterTypes().isEmpty()
                            || method.getName().equals("valueOf")
                            && method.getRawParameterTypes().size() == 1
                            && method.getRawParameterTypes().getFirst().isEquivalentTo(String.class));
                }
            })
            .and().areDeclaredInClassesThat()
            .doNotHaveFullyQualifiedName(TestTaskIdfApplication.class.getName())
            .should().beDeclaredInClassesThat().resideInAPackage("..util..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule UTILITIES = classes().that().resideInAPackage("..util..")
            .should(beUtilityClasses()).allowEmptyShould(true);

    @ArchTest
    static final ArchRule UTILITY_DEPENDENCIES = classes().that().resideInAPackage("..util..")
            .should().onlyDependOnClassesThat().resideOutsideOfPackages(
                    "..controller..", "..service..", "..repository..", "org.springframework..")
            .allowEmptyShould(true);

    private static ArchCondition<JavaClass> beUtilityClasses() {
        return new ArchCondition<>("be final with private constructors, static methods and constants only") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                if (!javaClass.getModifiers().contains(JavaModifier.FINAL)) {
                    events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName() + " must be final"));
                }
                for (JavaConstructor constructor : javaClass.getConstructors()) {
                    if (!constructor.getModifiers().contains(JavaModifier.PRIVATE)) {
                        events.add(SimpleConditionEvent.violated(constructor,
                                constructor.getFullName() + " must be private"));
                    }
                }
                for (JavaMethod method : javaClass.getMethods()) {
                    if (!method.getModifiers().contains(JavaModifier.STATIC)) {
                        events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " must be static"));
                    }
                }
                for (JavaField field : javaClass.getFields()) {
                    if (!field.getModifiers().contains(JavaModifier.STATIC)
                            || !field.getModifiers().contains(JavaModifier.FINAL)) {
                        events.add(SimpleConditionEvent.violated(field,
                                field.getFullName() + " must be static final"));
                    }
                }
            }
        };
    }
}
