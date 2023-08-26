package dex.mcgitmaker.loom;

import dex.mcgitmaker.GitCraft;
import dex.mcgitmaker.data.McVersion;
import net.fabricmc.tinyremapper.IMappingProvider;
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

		IMappingProvider mappingProvider = mcVersion.mappingsProvider();
		// Based on what Fabric-loom does
		if (!output.toFile().exists()) {
			TinyRemapper remapper = TinyRemapper.newRemapper()
					.renameInvalidLocals(true)
					.rebuildSourceFilenames(true)
					.invalidLvNamePattern(MC_LV_PATTERN)
					.inferNameFromSameLvIndex(true)
					.withMappings(mappingProvider)
					.fixPackageAccess(true)
					.threads(GitCraft.config.remappingThreads)
					//  TODO loom RecordComponentFixVisitor
					.build();

			remapper.readInputs(mcVersion.merged().toPath());

			try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(output).build()) {
				remapper.apply(consumer, remapper.createInputTag());
			}

			remapper.finish();
		}

		return output;
	}
}
