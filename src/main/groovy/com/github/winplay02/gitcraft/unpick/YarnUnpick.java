package com.github.winplay02.gitcraft.unpick;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.GitCraftQuirks;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.mappings.yarn.YarnMappings;
import com.github.winplay02.gitcraft.meta.GameVersionBuildMeta;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemRoot;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.FileSystemNetworkManager;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class YarnUnpick implements Unpick {
	@Override
	public StepStatus provideUnpick(IStepContext<?, OrderedVersion> versionContext, MinecraftJar minecraftJar) throws IOException {
		if (!doesUnpickInformationExist(versionContext.targetVersion())) {
			return StepStatus.NOT_RUN;
		}
		// fabric yarn is provided for the merged jar
		if (minecraftJar != MinecraftJar.MERGED) {
			return StepStatus.NOT_RUN;
		}
		Path unpickDefinitionsFile = getUnpickDefinitionsPath(versionContext.targetVersion());
		Path unpickDescriptionPath = getUnpickDescriptionPath(versionContext.targetVersion());
		// Try existing
		if (Files.exists(unpickDefinitionsFile) && Files.exists(unpickDescriptionPath)) {
			return StepStatus.UP_TO_DATE;
		}
		Files.deleteIfExists(unpickDefinitionsFile);
		Files.deleteIfExists(unpickDescriptionPath);
		// Get latest build info
		GameVersionBuildMeta yarnVersion = YarnMappings.getTargetYarnBuild(versionContext.targetVersion());
		if (yarnVersion == null) {
			return StepStatus.FAILED;
		}
		// Only try latest yarn merged v2 JAR build
		{
			Path mappingsFileJar = YarnMappings.getYarnMergedV2JarPath(versionContext.targetVersion(), yarnVersion);
			StepStatus unpickConstantsArtifact = StepStatus.NOT_RUN;
			try {
				StepStatus result = YarnMappings.fetchYarnMergedV2Jar(versionContext, yarnVersion);
				try (FileSystem fs = FileSystems.newFileSystem(mappingsFileJar)) {
					{
						Path unpickDefinitions = fs.getPath("extras", "definitions.unpick");
						if (Files.exists(unpickDefinitions) && unpickDefinitionsFile != null) {
							Files.copy(unpickDefinitions, unpickDefinitionsFile, StandardCopyOption.REPLACE_EXISTING);
						}
					}
					{
						Path unpickDescription = fs.getPath("extras", "unpick.json");
						if (Files.exists(unpickDescription) && unpickDescriptionPath != null) {
							Files.copy(unpickDescription, unpickDescriptionPath, StandardCopyOption.REPLACE_EXISTING);
						}
					}
					{
						if (isUnpickConstantsJarUsed(versionContext.targetVersion())) {
							unpickConstantsArtifact = fetchUnpickArtifacts(versionContext);
						}
					}
				}
				return StepStatus.merge(result, unpickConstantsArtifact, StepStatus.SUCCESS);
			} catch (IOException | RuntimeException ignored) {
				Files.deleteIfExists(mappingsFileJar);
			}
			MiscHelper.println("Yarn unpick information does not exist for %s.", versionContext.targetVersion().launcherFriendlyVersionName());
		}
		return null;
	}

	@Override
	public UnpickContext getContext(OrderedVersion targetVersion, MinecraftJar minecraftJar) throws IOException {
		// if (minecraftJar == MinecraftJar.MERGED) { // ignore jar configuration, just always use merged
		Path unpickConstants = isUnpickConstantsJarUsed(targetVersion) ? getUnpickConstantsPath(targetVersion) : null;
		Path unpickDefinitions = getUnpickDefinitionsPath(targetVersion);
		Path unpickDescription = getUnpickDescriptionPath(targetVersion);
		if ((!isUnpickConstantsJarUsed(targetVersion) || Files.exists(unpickConstants)) && Files.exists(unpickDefinitions) && Files.exists(unpickDescription)) {
			return new UnpickContext(unpickConstants, unpickDefinitions, unpickDescription);
		}
		// }
		return null;
	}

	@Override
	public boolean doesUnpickInformationExist(OrderedVersion mcVersion)  {
		if (YarnMappings.isYarnBrokenVersion(mcVersion)) { // exclude broken versions
			return false;
		}
		return mcVersion.compareTo(GitCraft.getApplicationConfiguration().manifestSource().getMetadataProvider().getVersionByVersionID(GitCraftQuirks.YARN_UNPICK_START_VERSION_ID)) >= 0;
	}

	@Override
	public MappingFlavour applicableMappingFlavour(UnpickDescriptionFile unpickDescription)  {
		if (unpickDescription == null || unpickDescription.namespace() == null || MappingsNamespace.of(unpickDescription.namespace()) == null) {
			throw new IllegalStateException("Unexpected value: null");
		}
		return switch (MappingsNamespace.of(unpickDescription.namespace())) {
			case MappingsNamespace.NAMED -> MappingFlavour.YARN;
			case MappingsNamespace.INTERMEDIARY -> MappingFlavour.FABRIC_INTERMEDIARY;
			default ->
				throw new IllegalStateException("Unexpected value: " + MappingsNamespace.of(unpickDescription.namespace()));
		};
	}

	@Override
	public boolean supportsUnpickRemapping(UnpickDescriptionFile unpickDescription) {
		return unpickDescription.version() >= 2;
	}

	private StepStatus fetchUnpickArtifacts(IStepContext<?, OrderedVersion> versionContext) throws IOException {
		// Try constants JAR for unpicking
		Path unpickingConstantsJar = getUnpickConstantsPath(versionContext.targetVersion());
		GameVersionBuildMeta yarnVersion = YarnMappings.getTargetYarnBuild(versionContext.targetVersion());
		if (yarnVersion == null) {
			return StepStatus.FAILED;
		}
		try {
			return RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(versionContext.executorService(), yarnVersion.makeConstantsJarMavenUrl(GitCraft.FABRIC_MAVEN), new FileSystemNetworkManager.LocalFileInfo(unpickingConstantsJar, null, null, "yarn unpicking constants", versionContext.targetVersion().launcherFriendlyVersionName()));
		} catch (RuntimeException ignored) {
			Files.deleteIfExists(unpickingConstantsJar);
		}
		MiscHelper.println("Yarn unpicking constants do not exist for %s, skipping download...", versionContext.targetVersion().launcherFriendlyVersionName());
		return StepStatus.FAILED;
	}

	protected Path getUnpickDefinitionsPath(OrderedVersion mcVersion) {
		GameVersionBuildMeta yarnVersion = YarnMappings.getTargetYarnBuild(mcVersion);
		if (yarnVersion == null) {
			return null;
		}
		return GitCraftPipelineFilesystemRoot.getMappings().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(String.format("%s-yarn-build.%s-unpick-definitions.unpick", mcVersion.launcherFriendlyVersionName(), yarnVersion.build()));
	}

	protected Path getUnpickDescriptionPath(OrderedVersion mcVersion) {
		GameVersionBuildMeta yarnVersion = YarnMappings.getTargetYarnBuild(mcVersion);
		if (yarnVersion == null) {
			return null;
		}
		return GitCraftPipelineFilesystemRoot.getMappings().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(String.format("%s-yarn-build.%s-unpick-description.json", mcVersion.launcherFriendlyVersionName(), yarnVersion.build()));
	}

	protected Path getUnpickConstantsPath(OrderedVersion mcVersion) {
		GameVersionBuildMeta yarnVersion = YarnMappings.getTargetYarnBuild(mcVersion);
		if (yarnVersion == null) {
			return null;
		}
		return GitCraftPipelineFilesystemRoot.getMappings().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(String.format("%s-yarn-build.%s-constants.jar", mcVersion.launcherFriendlyVersionName(), yarnVersion.build()));
	}

	private boolean isUnpickConstantsJarUsed(OrderedVersion targetVersion) {
		return targetVersion.compareTo(GitCraft.getApplicationConfiguration().manifestSource().getMetadataProvider().getVersionByVersionID(GitCraftQuirks.YARN_UNPICK_NO_CONSTANTS_JAR_VERSION_ID)) < 0;
	}
}
