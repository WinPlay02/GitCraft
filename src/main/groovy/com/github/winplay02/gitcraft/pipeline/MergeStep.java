package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import net.fabricmc.loom.configuration.providers.BundleMetadata;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.stitch.merge.JarMerger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class MergeStep extends Step {

	private final Path rootPath;

	public MergeStep(Path rootPath) {
		this.rootPath = rootPath;
	}

	public MergeStep() {
		this(GitCraft.MC_VERSION_STORE);
	}

	@Override
	public String getName() {
		return STEP_MERGE;
	}

	@Override
	public boolean ignoresMappings() {
		return true;
	}

	@Override
	protected Path getInternalArtifactPath(OrderedVersion mcVersion, MappingFlavour _mappingFlavour) {
		return this.rootPath.resolve(mcVersion.launcherFriendlyVersionName()).resolve("merged-jar.jar");
	}

	@Override
	public StepResult run(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour) throws IOException {
		// TODO do not run for (ancient) versions, where this does not make sense
		Path mergedPath = getInternalArtifactPath(mcVersion, mappingFlavour);
		if (Files.exists(mergedPath)) {
			return StepResult.UP_TO_DATE;
		}

		Path artifactRootPath = pipelineCache.getForKey(Step.STEP_FETCH_ARTIFACTS);
		if (artifactRootPath == null) {
			MiscHelper.panic("Artifacts (client jar, server jar) for version %s do not exist", mcVersion.launcherFriendlyVersionName());
		}

		Path client = mcVersion.clientJar().resolve(artifactRootPath);
		Path server2merge = mcVersion.serverJar().resolve(artifactRootPath);
		BundleMetadata sbm = BundleMetadata.fromJar(server2merge);
		if (sbm != null) {
			Path minecraftExtractedServerJar = GitCraft.MC_VERSION_STORE.resolve(mcVersion.launcherFriendlyVersionName()).resolve("extracted-server.jar");

			if (sbm.versions().size() != 1) {
				throw new UnsupportedOperationException("Expected only 1 version in META-INF/versions.list, but got %d".formatted(sbm.versions().size()));
			}

			unpackJarEntry(sbm.versions().get(0), server2merge, minecraftExtractedServerJar);
			server2merge = minecraftExtractedServerJar;
		}

		try (JarMerger jarMerger = new JarMerger(client.toFile(), server2merge.toFile(), mergedPath.toFile())) {
			jarMerger.enableSyntheticParamsOffset();
			jarMerger.merge();
		}
		return StepResult.SUCCESS;
	}

	private void unpackJarEntry(BundleMetadata.Entry entry, Path jar, Path dest) throws IOException {
		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(jar); InputStream is = Files.newInputStream(fs.get().getPath(entry.path()))) {
			Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
