/*
 * Copyright 2025 QuiltMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.enigma_plugin;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.quiltmc.enigma.api.Enigma;
import org.quiltmc.enigma.api.EnigmaProfile;
import org.quiltmc.enigma.api.EnigmaProject;
import org.quiltmc.enigma.api.ProgressListener;
import org.quiltmc.enigma.api.class_provider.ClasspathClassProvider;
import org.quiltmc.enigma.api.source.TokenType;
import org.quiltmc.enigma.api.translation.mapping.EntryMapping;
import org.quiltmc.enigma.api.translation.mapping.serde.MappingParseException;
import org.quiltmc.enigma.api.translation.representation.MethodDescriptor;
import org.quiltmc.enigma.api.translation.representation.TypeDescriptor;
import org.quiltmc.enigma.api.translation.representation.entry.ClassEntry;
import org.quiltmc.enigma.api.translation.representation.entry.Entry;
import org.quiltmc.enigma.api.translation.representation.entry.FieldEntry;
import org.quiltmc.enigma.api.translation.representation.entry.MethodEntry;
import org.quiltmc.enigma.util.validation.PrintNotifier;
import org.quiltmc.enigma.util.validation.ValidationContext;
import org.quiltmc.enigma_plugin.proposal.MappingMergePackageProposer;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

// todo make sure of priority

public class MojmapTest {
	private static final Path testResources = Path.of("./src/test/resources");
	private static final Path mojmapTest = testResources.resolve("mojmap_test");
	private static final Path exampleJar = mojmapTest.resolve("input.jar");
	private static final Path exampleMappings = mojmapTest.resolve("example_mappings.mapping");
	private static final Path emptyOverrides = mojmapTest.resolve("example_mappings_empty.json");

	private static final Path overrideRenaming = mojmapTest.resolve("override_based_renaming");
	private static final Path overrideRenamingOverrides = overrideRenaming.resolve("input_overrides.json");
	private static final Path overrideRenamingMappings = overrideRenaming.resolve("input.mapping");

	private static final Path invalidOverrides = mojmapTest.resolve("invalid_overrides");
	private static final Path beginWithInt = invalidOverrides.resolve("begin_with_int.json");
	private static final Path hyphens = invalidOverrides.resolve("hyphens.json");
	private static final Path multipleInvalidOverrides = invalidOverrides.resolve("multiple_invalid_overrides.json");
	private static final Path spaces = invalidOverrides.resolve("spaces.json");
	private static final Path slashes = invalidOverrides.resolve("slashes.json");
	private static final Path uppercases = invalidOverrides.resolve("uppercases.json");
	private static final Path validOverrides = invalidOverrides.resolve("valid_overrides.json");

	private static EnigmaProject project;

	static void setupEnigma(Path mojmapPath, Path overridesPath) throws IOException {
		String profileString = """
				{
					"services": {
						"name_proposal": [
							{
								"id": "quiltmc:name_proposal/fallback",
								"args": {
									"merged_mapping_path": "{MOJMAP_PATH}"
								}
							},
							{
								"id": "quiltmc:name_proposal/unchecked",
								"args": {
									"package_name_overrides_path": "{OVERRIDES_PATH}"
								}
							}
						]
					}
				}""";

		profileString = profileString.replace("{MOJMAP_PATH}", mojmapPath.toString());
		profileString = profileString.replace("{OVERRIDES_PATH}", overridesPath.toString());

		var profile = EnigmaProfile.parse(new StringReader(profileString));

		var enigma = Enigma.builder().setProfile(profile).build();
		project = enigma.openJar(MojmapTest.exampleJar, new ClasspathClassProvider(), ProgressListener.createEmpty());
	}

	@Test
	void testStaticProposal() throws IOException {
		setupEnigma(exampleMappings, emptyOverrides);

		// assert that all types propose properly
		// note that mojmaps do not contain params

		ClassEntry a = new ClassEntry("a"); // Axis
		assertMapping(a, "a/A", TokenType.JAR_PROPOSED);

		ClassEntry c = new ClassEntry("c");
		FieldEntry pi = new FieldEntry(c, "a", new TypeDescriptor("Lc$a;"));
		assertMapping(pi, "hatsuneMiku", TokenType.JAR_PROPOSED);

		ClassEntry b = new ClassEntry("b");
		MethodEntry getExplosionResistance = new MethodEntry(b, "a", new MethodDescriptor("(Ld;)V"));
		assertMapping(getExplosionResistance, "meow", TokenType.JAR_PROPOSED);
	}

	@Test
	void testDynamicProposal() throws IOException {
		setupEnigma(exampleMappings, emptyOverrides);

		ClassEntry a = new ClassEntry("a");
		assertMapping(a, "a/A", TokenType.JAR_PROPOSED);

		ValidationContext vc = new ValidationContext(PrintNotifier.INSTANCE);
		project.getRemapper().putMapping(vc, a, new EntryMapping("gaming/Gaming"));

		assertMapping(a, "a/Gaming", TokenType.DEOBFUSCATED);
	}

	@SuppressWarnings("OptionalGetWithoutIsPresent")
	@Test
	void testOverrideGeneration() throws IOException, MappingParseException {
		setupEnigma(exampleMappings, emptyOverrides);

		Path tempFile = Files.createTempFile("temp_package_overrides", "json");
		Path mappingPath = Path.of("./src/test/resources/mojmap_test/example_mappings.mapping");
		var mappings = project.getEnigma().readMappings(mappingPath).get();

		var entries = MappingMergePackageProposer.createPackageJson(mappings);
		MappingMergePackageProposer.writePackageJson(tempFile, entries);

		String expected = Files.readString(Path.of("./src/test/resources/mojmap_test/example_mappings_empty.json"));
		String actual = Files.readString(tempFile);

		Assertions.assertEquals(expected, actual);
	}

	@Test
	void testOverrideUpdating() throws IOException, MappingParseException {
		Path tempFile = Files.createTempFile("temp_package_overrides_update", "json");
		Path mappingPath = Path.of("./src/test/resources/mojmap_test/example_mappings.mapping");

		Path oldOverrides = Path.of("./src/test/resources/mojmap_test/update_test/example_mappings.json");
		Path newOverrides = Path.of("./src/test/resources/mojmap_test/update_test/example_mappings_expected.json");

		var mappings = project.getEnigma().readMappings(mappingPath).get();

		var oldPackageJson = MappingMergePackageProposer.readPackageJson(oldOverrides.toString());
		var updated = MappingMergePackageProposer.updatePackageJson(oldPackageJson, mappings);
		MappingMergePackageProposer.writePackageJson(tempFile, updated);

		String expected = Files.readString(newOverrides);
		String actual = Files.readString(tempFile);

		Assertions.assertEquals(expected, actual);
	}

	@Test
	void testOverrideRenaming() throws IOException, MappingParseException {
		setupEnigma(overrideRenamingMappings, overrideRenamingOverrides);
		project.setMappings(project.getEnigma().readMappings(overrideRenamingMappings).get(), ProgressListener.createEmpty());

		ClassEntry a = new ClassEntry("a");
		ClassEntry b = new ClassEntry("b");
		ClassEntry c = new ClassEntry("c");
		ClassEntry d = new ClassEntry("d");
		ClassEntry e = new ClassEntry("e");
		ClassEntry f = new ClassEntry("f");

		assertMapping(a, "a/Class1PackageA", TokenType.DEOBFUSCATED);
		assertMapping(b, "a/b_/Class2PackageAB", TokenType.DEOBFUSCATED);
		assertMapping(c, "b_/a/Class3PackageBA", TokenType.DEOBFUSCATED);
		assertMapping(d, "c_/a_/Class4PackageCA", TokenType.DEOBFUSCATED);
		assertMapping(e, "b_/b/Class5PackageBB", TokenType.DEOBFUSCATED);
		assertMapping(f, "b_/b/c_/Class6PackageBBC", TokenType.DEOBFUSCATED);
	}

	@Test
	void testDynamicOverrideRenaming() throws IOException, MappingParseException {
		setupEnigma(overrideRenamingMappings, overrideRenamingOverrides);
		project.setMappings(project.getEnigma().readMappings(overrideRenamingMappings).get(), ProgressListener.createEmpty());

		ClassEntry a = new ClassEntry("a");
		ClassEntry b = new ClassEntry("b");
		ClassEntry c = new ClassEntry("c");

		// assert normal proposed mappings
		assertMapping(a, "a/Class1PackageA", TokenType.DEOBFUSCATED);
		assertMapping(b, "a/b_/Class2PackageAB", TokenType.DEOBFUSCATED);
		assertMapping(c, "b_/a/Class3PackageBA", TokenType.DEOBFUSCATED);

		project.getRemapper().putMapping(new ValidationContext(PrintNotifier.INSTANCE), a, new EntryMapping("awesome/slay"));

		assertMapping(a, "a/slay", TokenType.DEOBFUSCATED);

		project.getRemapper().putMapping(new ValidationContext(PrintNotifier.INSTANCE), a, new EntryMapping("awesome/gaming/coquette"));

		assertMapping(a, "a/coquette", TokenType.DEOBFUSCATED);

		project.getRemapper().putMapping(new ValidationContext(PrintNotifier.INSTANCE), b, new EntryMapping("awesome/slay"));

		assertMapping(b, "a/b_/slay", TokenType.DEOBFUSCATED);
	}

	@Test
	void testInvalidOverrides() {
		assertThrows(MappingMergePackageProposer.InvalidOverrideException.class, () -> setupForOverrideValidation(beginWithInt));
		assertThrows(MappingMergePackageProposer.InvalidOverrideException.class, () -> setupForOverrideValidation(hyphens));
		assertThrows(MappingMergePackageProposer.InvalidOverrideException.class, () -> setupForOverrideValidation(multipleInvalidOverrides));
		assertThrows(MappingMergePackageProposer.InvalidOverrideException.class, () -> setupForOverrideValidation(slashes));
		assertThrows(MappingMergePackageProposer.InvalidOverrideException.class, () -> setupForOverrideValidation(spaces));
		assertThrows(MappingMergePackageProposer.InvalidOverrideException.class, () -> setupForOverrideValidation(uppercases));

		assertDoesNotThrow(() -> setupEnigma(overrideRenamingMappings, validOverrides));
	}

	static void setupForOverrideValidation(Path overridesPath) throws MappingParseException, IOException {
		setupEnigma(MojmapTest.overrideRenamingMappings, overridesPath);
		// manually trigger dynamic proposal so validation is run
		project.setMappings(project.getEnigma().readMappings(MojmapTest.overrideRenamingMappings).get(), ProgressListener.createEmpty());
	}

	private static void assertMapping(Entry<?> entry, String name, TokenType type) {
		var mapping = project.getRemapper().getMapping(entry);
		assertEquals(entry, name, mapping.targetName());
		assertEquals(entry, type, mapping.tokenType());
	}

	private static void assertEquals(Entry<?> entry, Object left, Object right) {
		Assertions.assertEquals(left, right, "mismatch for " + entry);
	}
}
