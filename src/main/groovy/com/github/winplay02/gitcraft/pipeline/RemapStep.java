package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class RemapStep extends Step {

	private final Path rootPath;

	public RemapStep(Path rootPath) {
		this.rootPath = rootPath;
	}

	public RemapStep() {
		this(GitCraft.REMAPPED);
	}

	@Override
	public String getName() {
		return Step.STEP_REMAP;
	}

	@Override
	protected Path getInternalArtifactPath(OrderedVersion mcVersion, MappingFlavour mappingFlavour) {
		return this.rootPath.resolve(String.format("%s-%s.jar", mcVersion.launcherFriendlyVersionName(), mappingFlavour.toString()));
	}

	// From Fabric-loom
	private static final Pattern MC_LV_PATTERN = Pattern.compile("\\$\\$\\d+");

	@Override
	public StepResult run(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour) throws Exception {
		Path remappedPath = getInternalArtifactPath(mcVersion, mappingFlavour);
		if (Files.exists(remappedPath) && Files.size(remappedPath) > 22 /* not empty jar */) {
			return StepResult.UP_TO_DATE;
		}
		if (Files.exists(remappedPath)) {
			Files.delete(remappedPath);
		}

		Path mergedPath = pipelineCache.getForKey(Step.STEP_MERGE);
		// TODO if merging did not happen, do something useful; Maybe there is either only client or only server
		if (mergedPath == null) {
			MiscHelper.panic("A merged JAR for version %s does not exist", mcVersion.launcherFriendlyVersionName());
		}

		TinyRemapper.Builder remapperBuilder = TinyRemapper.newRemapper()
				.renameInvalidLocals(true)
				.rebuildSourceFilenames(true)
				.invalidLvNamePattern(MC_LV_PATTERN)
				.inferNameFromSameLvIndex(true)
				.withMappings(mappingFlavour.getMappingImpl().getMappingsProvider(mcVersion))
				.fixPackageAccess(true)
				.threads(GitCraft.config.remappingThreads);
		TinyRemapper remapper = remapperBuilder.build();
		remapper.readInputs(mergedPath);
		try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(remappedPath).build()) {
			remapper.apply(consumer, remapper.createInputTag());
		}
		remapper.finish();
		return StepResult.SUCCESS;
	}
}
