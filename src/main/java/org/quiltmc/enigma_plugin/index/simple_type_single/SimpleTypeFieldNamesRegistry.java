/*
 * Copyright 2022 QuiltMC
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

package org.quiltmc.enigma_plugin.index.simple_type_single;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.enigma.api.analysis.index.jar.EntryIndex;
import org.quiltmc.enigma.api.analysis.index.jar.JarIndex;
import org.quiltmc.enigma.api.translation.representation.entry.ClassEntry;
import org.quiltmc.enigma_plugin.util.CasingUtil;
import org.quiltmc.parsers.json.JsonReader;
import org.quiltmc.parsers.json.JsonToken;
import org.tinylog.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.quiltmc.enigma_plugin.index.simple_type_single.SimpleTypeSingleIndex.*;

public class SimpleTypeFieldNamesRegistry {
	private final Path path;
	private final EntryIndex index;
	private final TypeVerification typeVerification;

	/**
	 * Using a {@link LinkedHashMap} to ensure we keep the read order.
	 */
	private final Map<String, Entry> entries = new LinkedHashMap<>();

	public SimpleTypeFieldNamesRegistry(Path path, JarIndex jarIndex, TypeVerification typeVerification) {
		this.path = path;
		this.index = jarIndex.getIndex(EntryIndex.class);
		this.typeVerification = typeVerification;
	}

	public @Nullable Entry getEntry(String type) {
		return this.entries.get(type);
	}

	public Stream<String> streamTypes() {
		return this.entries.keySet().stream();
	}

	public void read() {
		try (var reader = JsonReader.json5(this.path)) {
			if (reader.peek() != JsonToken.BEGIN_OBJECT) {
				return;
			}

			reader.beginObject();

			Set<String> missingTypes = new HashSet<>();
			boolean verify = this.typeVerification != TypeVerification.NONE;
			while (reader.hasNext()) {
				String type = reader.nextName();

				if (type.equals("$schema")) {
					reader.skipValue();
					continue;
				}

				if (this.entries.containsKey(type)) {
					throw new IllegalArgumentException("Duplicate type " + type);
				}

				if (verify && !this.index.hasClass(new ClassEntry(type))) {
					missingTypes.add(type);
				}

				switch (reader.peek()) {
					case STRING -> {
						String localName = reader.nextString();
						this.entries.put(type, new Entry(type, localName, CasingUtil.toScreamingSnakeCase(localName)));
					}
					case BEGIN_OBJECT -> {
						String localName = null;
						String staticName = null;
						boolean exclusive = false;
						boolean inherit = false;
						List<Name> fallback = Collections.emptyList();

						reader.beginObject();

						while (reader.hasNext()) {
							String key = reader.nextName();

							switch (key) {
								case "local_name" -> localName = reader.nextString();
								case "static_name" -> staticName = reader.nextString();
								case "exclusive" -> exclusive = reader.nextBoolean();
								case "inherit" -> inherit = reader.nextBoolean();
								case "fallback" -> {
									reader.beginArray();

									fallback = this.collectFallbacks(reader, type);

									reader.endArray();
								}
								default -> reader.skipValue();
							}
						}

						reader.endObject();

						if (localName == null) {
							Logger.error("Failed parsing local name for type " + type);
							break;
						}

						if (staticName == null) staticName = CasingUtil.toScreamingSnakeCase(localName);

						this.entries.put(type, new Entry(type, new Name(localName, staticName), exclusive, inherit, fallback));
					}
					default -> reader.skipValue();
				}
			}

			reader.endObject();

			if (verify) {
				if (!missingTypes.isEmpty()) {
					boolean single = missingTypes.size() == 1;
					StringBuilder message = new StringBuilder("The following simple type field name type");
					message.append(single ? " is" : "s are");
					message.append(" missing:");

					if (single) {
						message.append(' ').append(missingTypes.iterator().next());
					} else {
						missingTypes.forEach(type -> message.append("\n\t").append(type));
					}

					if (this.typeVerification == TypeVerification.WARN) {
						Logger.warn(message);
					} else {
						throw new IllegalStateException(message.toString());
					}
				}
			}
		} catch (IOException e) {
			Logger.error(e, "Failed to read simple type field names registry.");
		}
	}

	private List<Name> collectFallbacks(JsonReader reader, String type) throws IOException {
		var list = new ArrayList<Name>();

		while (reader.hasNext()) {
			switch (reader.peek()) {
				case STRING -> {
					String name = reader.nextString();
					list.add(new Name(name, CasingUtil.toScreamingSnakeCase(name)));
				}
				case BEGIN_OBJECT -> {
					String localName = null;
					String staticName = null;
					reader.beginObject();

					while (reader.hasNext()) {
						String key = reader.nextName();

						switch (key) {
							case "local_name" -> localName = reader.nextString();
							case "static_name" -> staticName = reader.nextString();
							default -> reader.skipValue();
						}
					}

					reader.endObject();

					if (localName == null) {
						Logger.error("Failed parsing fallback local name for type " + type);
						break;
					}

					if (staticName == null) staticName = CasingUtil.toScreamingSnakeCase(localName);

					list.add(new Name(localName, staticName));
				}
			}
		}

		return list;
	}

	public record Entry(String type, Name name, boolean exclusive, boolean inherit, List<Name> fallback) {
		public Entry(String type, String localName, String staticName) {
			this(type, new Name(localName, staticName), false, false, Collections.emptyList());
		}

		public @Nullable Name findFallback(Predicate<Name> predicate) {
			for (var fallback : this.fallback) {
				if (predicate.test(fallback)) {
					return fallback;
				}
			}

			return null;
		}
	}

	public record Name(String local, String staticName) {
	}
}
