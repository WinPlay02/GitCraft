package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.GitCraftConfig;
import com.github.winplay02.gitcraft.MinecraftVersionGraph;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class RemapStep extends Step {

	protected final Path rootPath;
	private static RemapStepClientOnly remapStepClientOnly;
	private static RemapStepServerOnly remapStepServerOnly;
	public static final String SERVER_ZIP_JAR_NAME = "minecraft-server.jar";

	public static void init(Path rootPath) {
		remapStepClientOnly = new RemapStepClientOnly(rootPath);
		remapStepServerOnly = new RemapStepServerOnly(rootPath);
	}

	public static void init() {
		init(GitCraftPaths.REMAPPED);
	}

	public RemapStep(Path rootPath) {
		this.rootPath = rootPath;
	}

	public RemapStep() {
		this(GitCraftPaths.REMAPPED);
	}

	@Override
	public String getName() {
		return Step.STEP_REMAP;
	}

	@Override
	protected Path getInternalArtifactPath(OrderedVersion mcVersion, MappingFlavour mappingFlavour) {
		return this.rootPath.resolve(String.format("%s-%s.jar", mcVersion.launcherFriendlyVersionName(), mappingFlavour));
	}

	// From Fabric-loom
	private static final Pattern MC_LV_PATTERN = Pattern.compile("\\$\\$\\d+");

	@Override
	public StepResult run(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) throws Exception {
		if (mcVersion.timestamp().isBefore(GitCraftConfig.FIRST_MERGEABLE_VERSION_RELEASE_TIME)) {
			List<Step> separateRemapStep = new ArrayList<>(2);
			if (mcVersion.hasClientCode()) {
				separateRemapStep.add(remapStepClientOnly);
			}
			if (mcVersion.hasServerCode()) {
				separateRemapStep.add(remapStepServerOnly);
			}
			if (!separateRemapStep.isEmpty()) {
				injectStepsAndReplace(separateRemapStep);
			}
			return StepResult.NOT_RUN;
		}
		Path remappedPath = getInternalArtifactPath(mcVersion, mappingFlavour);
		if (Files.exists(remappedPath) && Files.size(remappedPath) > 22 /* not empty jar */) {
			return StepResult.UP_TO_DATE;
		}
		if (Files.exists(remappedPath)) {
			Files.delete(remappedPath);
		}
		Path mergedPath = pipelineCache.getForKey(Step.STEP_MERGE);
		if (mergedPath == null) { // Should only happen before 1.3, which is caught above
			MiscHelper.panic("A merged JAR for version %s does not exist", mcVersion.launcherFriendlyVersionName());
		}
		return remap(mergedPath, mcVersion, mappingFlavour);
	}

	protected StepResult remap(Path input, OrderedVersion mcVersion, MappingFlavour mappingFlavour) throws IOException {
		Path remappedPath = getInternalArtifactPath(mcVersion, mappingFlavour);
		final IMappingProvider mappingProvider = mappingFlavour.getMappingImpl().getMappingsProvider(mcVersion);

		TinyRemapper.Builder remapperBuilder = TinyRemapper.newRemapper()
			.renameInvalidLocals(true)
			.rebuildSourceFilenames(true)
			.invalidLvNamePattern(MC_LV_PATTERN)
			.inferNameFromSameLvIndex(true)
			.withMappings(mappingProvider)
			.fixPackageAccess(true)
			.threads(GitCraft.config.remappingThreads);
		TinyRemapper remapper = remapperBuilder.build();
		remapper.readInputs(input);
		try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(remappedPath).build()) {
			remapper.apply(consumer, remapper.createInputTag());
		}
		remapper.finish();
		return StepResult.SUCCESS;
	}

	private static class RemapStepClientOnly extends RemapStep {
		private RemapStepClientOnly(Path rootPath) {
			super(rootPath);
		}

		@Override
		public String getName() {
			return Step.STEP_REMAP_CLIENT_ONLY;
		}

		@Override
		protected Path getInternalArtifactPath(OrderedVersion mcVersion, MappingFlavour mappingFlavour) {
			return this.rootPath.resolve(String.format("%s-%s-client.jar", mcVersion.launcherFriendlyVersionName(), mappingFlavour));
		}

		@Override
		public StepResult run(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) throws Exception {
			if (!mcVersion.timestamp().isBefore(GitCraftConfig.FIRST_MERGEABLE_VERSION_RELEASE_TIME)) {
				MiscHelper.panic("Calling separate remapping functionality is not supported for mergable version %s", mcVersion);
			}
			Path remappedPath = getInternalArtifactPath(mcVersion, mappingFlavour);
			if (Files.exists(remappedPath) && Files.size(remappedPath) > 22 /* not empty jar */) {
				return StepResult.UP_TO_DATE;
			}
			if (Files.exists(remappedPath)) {
				Files.delete(remappedPath);
			}
			Path artifactPath = pipelineCache.getForKey(Step.STEP_FETCH_ARTIFACTS);
			Path clientJar = mcVersion.clientJar().resolve(artifactPath);
			return remap(clientJar, mcVersion, mappingFlavour);
		}
	}

	private static class RemapStepServerOnly extends RemapStep {
		private RemapStepServerOnly(Path rootPath) {
			super(rootPath);
		}

		@Override
		public String getName() {
			return Step.STEP_REMAP_SERVER_ONLY;
		}

		@Override
		protected Path getInternalArtifactPath(OrderedVersion mcVersion, MappingFlavour mappingFlavour) {
			return this.rootPath.resolve(String.format("%s-%s-server.jar", mcVersion.launcherFriendlyVersionName(), mappingFlavour));
		}

		@Override
		public StepResult run(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) throws Exception {
			if (!mcVersion.timestamp().isBefore(GitCraftConfig.FIRST_MERGEABLE_VERSION_RELEASE_TIME)) {
				MiscHelper.panic("Calling separate remapping functionality is not supported for mergable version %s", mcVersion);
			}
			Path remappedPath = getInternalArtifactPath(mcVersion, mappingFlavour);
			if (Files.exists(remappedPath) && Files.size(remappedPath) > 22 /* not empty jar */) {
				return StepResult.UP_TO_DATE;
			}
			if (Files.exists(remappedPath)) {
				Files.delete(remappedPath);
			}
			Path artifactPath = pipelineCache.getForKey(Step.STEP_FETCH_ARTIFACTS);
			if (mcVersion.serverDist().serverJar() != null) {
				Path serverJar = mcVersion.serverDist().serverJar().resolve(artifactPath);
				return remap(serverJar, mcVersion, mappingFlavour);
			} else if (mcVersion.serverDist().serverZip() != null) {
				Path serverZip = mcVersion.serverDist().serverZip().resolve(artifactPath);
				try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(serverZip)) {
					// to make tine remapper happy
					Path temporaryExtractedFile = serverZip.getParent().resolve(String.format("%s-%s-%s.jar", serverZip.getFileName().toString(), "temp", System.nanoTime()));
					Files.copy(Files.newInputStream(fs.get().getPath(SERVER_ZIP_JAR_NAME)), temporaryExtractedFile);
					StepResult result = remap(temporaryExtractedFile, mcVersion, mappingFlavour);
					MiscHelper.deleteFile(temporaryExtractedFile);
					return result;
				}
			}
			MiscHelper.panic("Server code is available, but neither a zip nor a jar is containing the server code.");
			return StepResult.FAILED;
		}
	}
}
