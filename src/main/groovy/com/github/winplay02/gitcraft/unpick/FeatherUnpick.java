package com.github.winplay02.gitcraft.unpick;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.mappings.ornithe.FeatherMappings;
import com.github.winplay02.gitcraft.meta.GameVersionBuildMeta;
import com.github.winplay02.gitcraft.meta.VersionMetaSource;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemRoot;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.FileSystemNetworkManager;
import com.github.winplay02.gitcraft.util.RemoteHelper;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FeatherUnpick implements Unpick {
	private final int generation;

	private VersionMetaSource<GameVersionBuildMeta> featherVersions() {
		return FeatherMappings.featherVersions(this.generation);
	}

	public FeatherUnpick() {
		if (GitCraft.getApplicationConfiguration().ornitheIntermediaryGeneration() < 1) {
			throw new IllegalArgumentException("ornithe intermediary generation cannot be less than 1");
		}

		this.generation = GitCraft.getApplicationConfiguration().ornitheIntermediaryGeneration();
		this.featherVersions();
	}

	@Override
	public StepStatus provideUnpick(IStepContext<?, OrderedVersion> versionContext, MinecraftJar minecraftJar) throws IOException {
		GameVersionBuildMeta featherVersion = FeatherMappings.getLatestFeatherVersion(this.generation, versionContext.targetVersion(), minecraftJar);
		if (featherVersion == null) {
			return StepStatus.NOT_RUN;
		}
		if (!doesUnpickInformationExist(versionContext.targetVersion())) {
			return StepStatus.NOT_RUN;
		}

		Path unpickDefinitionsFile = getUnpickDefinitionsPath(versionContext.targetVersion(), minecraftJar);
		Path unpickConstantsJarFile = getUnpickConstantsJarPath(versionContext.targetVersion(), minecraftJar);
		if (!doesUnpickInformationExist(versionContext.targetVersion()) || (Files.exists(unpickDefinitionsFile) && Unpick.validateUnpickDefinitionsV2(unpickDefinitionsFile))) {
			if (!doesUnpickInformationExist(versionContext.targetVersion()) || (Files.exists(unpickConstantsJarFile) && Files.size(unpickConstantsJarFile) > 22 /* not empty jar */)) {
				return StepStatus.UP_TO_DATE;
			}
		}
		Files.deleteIfExists(unpickDefinitionsFile);
		Files.deleteIfExists(unpickConstantsJarFile);

		Path mappingsJarFile = FeatherMappings.getMappingsJarPath(this.generation, versionContext.targetVersion(), minecraftJar);
		StepStatus downloadStatus = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(versionContext.executorService(), featherVersion.makeMergedV2JarMavenUrl(GitCraft.ORNITHE_MAVEN), new FileSystemNetworkManager.LocalFileInfo(mappingsJarFile, null, null, "feather gen " + generation + " mapping", versionContext.targetVersion().launcherFriendlyVersionName()));
		try (FileSystem fs = FileSystems.newFileSystem(mappingsJarFile)) {
			Path unpickDefinitionsPathInJar = fs.getPath("extras", "definitions.unpick");
			Files.copy(unpickDefinitionsPathInJar, unpickDefinitionsFile, StandardCopyOption.REPLACE_EXISTING);
		}
		downloadStatus = StepStatus.merge(downloadStatus, RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(versionContext.executorService(), featherVersion.makeConstantsJarMavenUrl(GitCraft.ORNITHE_MAVEN), new FileSystemNetworkManager.LocalFileInfo(unpickConstantsJarFile, null, null, "feather gen " + generation + " unpicking constants", versionContext.targetVersion().launcherFriendlyVersionName())));
		return StepStatus.merge(downloadStatus, StepStatus.SUCCESS);
	}

	private Path getUnpickDefinitionsPath(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		try {
			GameVersionBuildMeta featherVersion = FeatherMappings.getLatestFeatherVersion(this.generation, mcVersion, minecraftJar);
			if (featherVersion == null) {
				return null;
			}
			return GitCraftPipelineFilesystemRoot.getMappings().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(FeatherMappings.versionKey(this.generation, mcVersion, minecraftJar) + "-feather-gen" + generation + "-build." + featherVersion.build() + "-unpick-definitions.unpick");
		} catch (IOException e) {
			return null;
		}
	}

	private Path getUnpickConstantsJarPath(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		try {
			GameVersionBuildMeta featherVersion = FeatherMappings.getLatestFeatherVersion(this.generation, mcVersion, minecraftJar);
			if (featherVersion == null) {
				return null;
			}
			return GitCraftPipelineFilesystemRoot.getMappings().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(FeatherMappings.versionKey(this.generation, mcVersion, minecraftJar) + "-feather-gen" + generation + "-build." + featherVersion.build() + "-unpick-constants.jar");
		} catch (IOException e) {
			return null;
		}
	}

	@Override
	public UnpickContext getContext(OrderedVersion targetVersion, MinecraftJar minecraftJar) throws IOException {
		Path unpickDefinitions = getUnpickDefinitionsPath(targetVersion, minecraftJar);
		Path unpickConstants = getUnpickConstantsJarPath(targetVersion, minecraftJar);
		if (unpickConstants != null && Files.exists(unpickConstants) && unpickDefinitions != null && Files.exists(unpickDefinitions)) {
			return new UnpickContext(unpickConstants, unpickDefinitions, null);
		}
		return null;
	}

	private boolean doMappingsExist(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		try {
			return FeatherMappings.getLatestFeatherVersion(this.generation, mcVersion, minecraftJar) != null;
		} catch (IOException e) {
			return false;
		}
	}

	@Override
	public boolean doesUnpickInformationExist(OrderedVersion mcVersion) {
		return generation > 1 && doMappingsExist(mcVersion, MinecraftJar.MERGED);
	}

	@Override
	public MappingFlavour applicableMappingFlavour(UnpickDescriptionFile unpickDescription) {
		return MappingFlavour.FEATHER;
	}

	@Override
	public boolean supportsUnpickRemapping(UnpickDescriptionFile unpickDescription) {
		return false;
	}
}
