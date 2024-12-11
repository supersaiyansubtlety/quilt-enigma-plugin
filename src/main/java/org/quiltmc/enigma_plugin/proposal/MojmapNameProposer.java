package org.quiltmc.enigma_plugin.proposal;

import org.quiltmc.enigma.api.Enigma;
import org.quiltmc.enigma.api.analysis.index.jar.JarIndex;
import org.quiltmc.enigma.api.translation.mapping.EntryMapping;
import org.quiltmc.enigma.api.translation.mapping.EntryRemapper;
import org.quiltmc.enigma.api.translation.mapping.tree.EntryTree;
import org.quiltmc.enigma.api.translation.mapping.tree.EntryTreeNode;
import org.quiltmc.enigma.api.translation.representation.entry.ClassEntry;
import org.quiltmc.enigma.api.translation.representation.entry.Entry;
import org.tinylog.Logger;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public class MojmapNameProposer extends NameProposer {
	public static final String ID = "mojmap";

	private final String mojmapPath;
	private EntryTree<EntryMapping> mojmaps;

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	public MojmapNameProposer(Optional<String> path) {
		super(ID);

		if (path.isPresent()) {
			this.mojmapPath = path.get();
		} else {
			Logger.error("no mojmap path provided, disabling " + this.getSourcePluginId());
			this.mojmapPath = null;
		}
	}

	@Override
	public void insertProposedNames(Enigma enigma, JarIndex index, Map<Entry<?>, EntryMapping> mappings) {
		Path path = Path.of(mojmapPath);
		enigma.getReadWriteService(path).ifPresent((service) -> {
			try {
				mojmaps = service.read(path);
			} catch (Exception e) {
				Logger.error(e, "could not read mojmaps!");
			}
		});

		if (mojmaps != null) {
			mojmaps.getRootNodes().forEach((node) -> this.proposeNodeAndChildren(mappings, node));
		}
	}

	private void proposeNodeAndChildren(Map<Entry<?>, EntryMapping> mappings, EntryTreeNode<EntryMapping> node) {
		if (node.getValue() != null && node.getValue().targetName() != null) {
			this.insertProposal(mappings, node.getEntry(), node.getValue().targetName());
		}

		node.getChildNodes().forEach((child) -> proposeNodeAndChildren(mappings, child));
	}

	@Override
	public void proposeDynamicNames(EntryRemapper remapper, Entry<?> obfEntry, EntryMapping oldMapping, EntryMapping newMapping, Map<Entry<?>, EntryMapping> mappings) {
		// todo dynamically fix class names to match their moj package
		// todo definitions file

		if (obfEntry instanceof ClassEntry classEntry && !classEntry.isInnerClass() && mojmaps != null) {

		}
	}
}
