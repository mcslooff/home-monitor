package nl.tufa.poc.bme680.identity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

class IdentityArchitectureTest {
	@Test
	void domainModelsAndRepositoryPortsDependOnlyOnTheDomainAndJava() {
		var domain = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("nl.tufa.poc.bme680.identity.domain");
		classes().should().onlyDependOnClassesThat()
				.resideInAnyPackage("java..", "nl.tufa.poc.bme680.identity.domain..")
				.check(domain);
	}

	@Test
	void applicationUseCasesDoNotDependOnSpringOrPersistenceOrWeb() {
		var application = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("nl.tufa.poc.bme680.identity.application");
		classes().should().onlyDependOnClassesThat()
				.resideInAnyPackage("java..", "nl.tufa.poc.bme680.identity.domain..",
						"nl.tufa.poc.bme680.identity.application..")
				.check(application);
	}
}
