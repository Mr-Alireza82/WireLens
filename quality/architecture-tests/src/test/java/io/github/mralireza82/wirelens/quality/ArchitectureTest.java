package io.github.mralireza82.wirelens.quality;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The architecture, as rules the build enforces. Dependencies point inwards: apps and di know adapters; adapters
 * know presentation and contexts; presentation knows contexts; contexts know only the shared kernel. Nothing in the
 * core knows a framework (JavaFX, Dagger, SQL, sockets).
 */
class ArchitectureTest {
    private static final String ROOT = "io.github.mralireza82.wirelens";
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
        assertThat(classes).as("the production classes are on the test classpath").hasSizeGreaterThan(100);
    }

    private static void check(ArchRule rule) {
        rule.check(classes);
    }

    @Test
    void sharedModulesDependOnNothingButTheJdk() {
        check(classes().that().resideInAnyPackage(ROOT + ".kernel..", ROOT + ".tokens..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", ROOT + ".kernel..", ROOT + ".tokens.."));
    }

    @Test
    void relayKnowsOnlyTheKernel() {
        check(classes().that().resideInAPackage(ROOT + ".relay..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", ROOT + ".kernel..", ROOT + ".relay.."));
    }

    @Test
    void domainDoesNotDependOnApplication() {
        check(noClasses().that().resideInAPackage(ROOT + ".relay.domain..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".relay.application.."));
    }

    @Test
    void inspectionIsDownstreamOfRelayOnly() {
        check(noClasses().that().resideInAPackage(ROOT + ".inspection..")
                .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".presentation..", ROOT + ".adapter..",
                        ROOT + ".di..", ROOT + ".app.."));
    }

    @Test
    void presentationIsPlainJava() {
        check(noClasses().that().resideInAPackage(ROOT + ".presentation..")
                .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".adapter..", ROOT + ".di..", ROOT + ".app..",
                        "javafx..", "android..", "androidx..", "java.sql..", "java.net.."));
        check(classes().that().resideInAPackage(ROOT + ".presentation.layout..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", ROOT + ".presentation.layout.."));
    }

    @Test
    void coreIsFreeOfFrameworks() {
        check(noClasses().that().resideInAnyPackage(ROOT + ".kernel..", ROOT + ".relay..", ROOT + ".inspection..",
                        ROOT + ".presentation..", ROOT + ".tokens..")
                .should().dependOnClassesThat().resideInAnyPackage("javafx..", "dagger..", "javax.inject..",
                        "jakarta.inject..", "java.sql..", "atlantafx..", "org.sqlite.."));
    }

    @Test
    void adaptersDoNotKnowEachOther() {
        check(slices().matching(ROOT + ".adapter.(*).(*)..").should().notDependOnEachOther());
    }

    @Test
    void onlyDiAndAppsCreateTheGraph() {
        check(noClasses().that().resideOutsideOfPackages(ROOT + ".di..", ROOT + ".app..")
                .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".di..", ROOT + ".app.."));
        check(noClasses().that().resideInAnyPackage(ROOT + ".kernel..", ROOT + ".relay..", ROOT + ".inspection..",
                        ROOT + ".presentation..", ROOT + ".tokens..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".adapter.."));
    }

    @Test
    void appsUseDependencyInjectionInsteadOfBuildingAdapters() {
        check(noClasses().that().resideInAPackage(ROOT + ".app..")
                .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".adapter.connection..",
                        ROOT + ".adapter.storage.."));
    }
}
