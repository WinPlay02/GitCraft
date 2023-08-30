package dex.mcgitmaker.loom;

import com.github.winplay02.MappingHelper;
import dex.mcgitmaker.GitCraft;
import dex.mcgitmaker.data.McVersion;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.util.RecordComponentFixVisitor;
import net.fabricmc.loom.util.TinyRemapperHelper;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class Remapper {
	// From Fabric-loom
	private static final Pattern MC_LV_PATTERN = Pattern.compile("\\$\\$\\d+");

	public static Path doRemap(McVersion mcVersion) throws IOException {
		Path output = GitCraft.REMAPPED.resolve(mcVersion.version + "-mojmap.jar"); // TODO other mappings?

		MemoryMappingTree mappingTree = MappingHelper.createMojMapMappingsProvider(mcVersion);
		// Based on what Fabric-loom does
		if (!output.toFile().exists() || output.toFile().length() == 22 /* empty jar */) {
			if (output.toFile().exists()) {
				output.toFile().delete();
			}
			int intermediaryNsId = mappingTree.getNamespaceId(MappingsNamespace.INTERMEDIARY.toString());
			TinyRemapper remapper = TinyRemapper.newRemapper()
					.renameInvalidLocals(true)
					.rebuildSourceFilenames(true)
					.invalidLvNamePattern(MC_LV_PATTERN)
					.inferNameFromSameLvIndex(true)
					.withMappings(TinyRemapperHelper.create(mappingTree, MappingsNamespace.OFFICIAL.toString(), MappingsNamespace.NAMED.toString(), true))
					.fixPackageAccess(true)
					.threads(GitCraft.config.remappingThreads)
					.extraPreApplyVisitor(((cls, next) -> { // See https://github.com/FabricMC/fabric-loom/pull/497
						if (GitCraft.config.loomFixRecords && !cls.isRecord() && "java/lang/Record".equals(cls.getSuperName())) {
							return new RecordComponentFixVisitor(next, mappingTree, intermediaryNsId);
						} else {
							return next;
						}
					})).build();


			remapper.readInputs(mcVersion.merged().toPath());

			try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(output).build()) {
				remapper.apply(consumer, remapper.createInputTag());
			}

			remapper.finish();
		}

		return output;
	}
}
