package com.github.winplay02.gitcraft.pipeline.workers;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import groovy.lang.Tuple2;
import net.fabricmc.loom.util.FileSystemUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record DataGenerator(StepWorker.Config config) implements StepWorker<OrderedVersion, DataGenerator.Inputs> {

	public static final ExternalWorldgenPacks EXTERNAL_WORLDGEN_PACKS = new ExternalWorldgenPacks();
	private static final String DATAGEN_AVAILABLE_START_VERSION = "18w01a";
	private static final String DATAGEN_BUNDLER_START_VERSION = "21w39a";
	private static final String EXT_VANILLA_WORLDGEN_PACK_START = "20w28a";
	private static final String EXT_VANILLA_WORLDGEN_PACK_END = "21w44a";

	@Override
	public boolean shouldExecute(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context) {
		return context.targetVersion().compareTo(GitCraft.getApplicationConfiguration().manifestSource().getMetadataProvider().getVersionByVersionID(DATAGEN_AVAILABLE_START_VERSION)) >= 0;
	}

	@Override
	public StepOutput<OrderedVersion> run(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, DataGenerator.Inputs input, StepResults<OrderedVersion> results) throws Exception {
		if (!GitCraft.getDataConfiguration().loadDatagenRegistry() && (!GitCraft.getDataConfiguration().readableNbt() || !GitCraft.getDataConfiguration().loadIntegratedDatapack())) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		OrderedVersion mcVersion = context.targetVersion();
		if (mcVersion.compareTo(GitCraft.getApplicationConfiguration().manifestSource().getMetadataProvider().getVersionByVersionID(DATAGEN_AVAILABLE_START_VERSION)) < 0) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		if (input.serverJar == null) {
			MiscHelper.panic("Cannot execute datagen, no jar available");
		}

		Path artifactSnbtArchive = null;
		Path artifactReportsArchive = null;

		if (GitCraft.getDataConfiguration().readableNbt()) {
			artifactSnbtArchive = results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.DATAGEN_SNBT_ARCHIVE);
			MiscHelper.deleteJarIfEmpty(artifactSnbtArchive);
		}

		if (GitCraft.getDataConfiguration().loadDatagenRegistry()) {
			artifactReportsArchive = results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.DATAGEN_REPORTS_ARCHIVE);
			MiscHelper.deleteJarIfEmpty(artifactReportsArchive);
		}

		if ((!GitCraft.getDataConfiguration().readableNbt() || Files.exists(artifactSnbtArchive))
			&& (!GitCraft.getDataConfiguration().loadDatagenRegistry() || Files.exists(artifactReportsArchive))) {
			return new StepOutput<>(StepStatus.UP_TO_DATE, results);
		}

		Path executablePath = pipeline.getStoragePath(input.serverJar(), context, this.config);
		Path datagenDirectory = results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.ARTIFACTS_DATAGEN);
		Files.createDirectories(datagenDirectory);

		if (GitCraft.getDataConfiguration().readableNbt()) {
			// Structures (& more)
			{
				Path dataJarPath = pipeline.getStoragePath(input.dataJar(), context, this.config);
				Path nbtSourceDataDirectory = results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.TEMP_DATAGEN_NBT_SOURCE_DATA_DIRECTORY);
				try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(dataJarPath)) {
					MiscHelper.copyLargeDir(fs.get().getPath("data"), nbtSourceDataDirectory);
				}
			}
			Path nbtSourceDirectory = results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.TEMP_DATAGEN_NBT_SOURCE_DIRECTORY);
			// Delete Output files, as some versions do not work, when files already exist
			Path datagenSnbtOutput = results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.TEMP_DATAGEN_SNBT_DESTINATION_DIRECTORY);
			MiscHelper.deleteDirectory(datagenSnbtOutput);
			runDatagen(mcVersion, datagenDirectory, executablePath, "--dev",
				"--input", nbtSourceDirectory.toAbsolutePath().toString(),
				"--output", datagenSnbtOutput.toAbsolutePath().toString()
			);
			// Delete input files, as they are no longer needed
			MiscHelper.deleteDirectory(nbtSourceDirectory);
			if (!Files.exists(datagenSnbtOutput) || !Files.isDirectory(datagenSnbtOutput)) {
				MiscHelper.panic("Datagen step was required, but SNBT files were not generated");
			}
			Path datagenSnbtOutputData = results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.TEMP_DATAGEN_SNBT_DESTINATION_DATA_DIRECTORY);
			// Copy to artifact jar
			try (FileSystemUtil.Delegate snbtArchive = FileSystemUtil.getJarFileSystem(artifactSnbtArchive, true)) {
				MiscHelper.copyLargeDir(datagenSnbtOutputData, snbtArchive.getPath("data"));
			}
			MiscHelper.deleteDirectory(datagenSnbtOutput);
		}
		if (GitCraft.getDataConfiguration().loadDatagenRegistry()) {
			StepStatus status = null;
			Tuple2<OrderedVersion, Artifact> worldgenPack = EXTERNAL_WORLDGEN_PACKS.get(mcVersion);
			if (worldgenPack != null) {
				Path vanillaWorldgenDatapack = results.getPathForDifferentVersionKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.ARTIFACTS_VANILLA_WORLDGEN_DATAPACK_ZIP, worldgenPack.getV1());
				status = worldgenPack.getV2().fetchArtifactToFile(context.executorService(), vanillaWorldgenDatapack, "vanilla worldgen datapack");
			}
			// Datagen
			Path datagenReportsOutput = results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.TEMP_DATAGEN_REPORTS_DIRECTORY);
			MiscHelper.deleteDirectory(datagenReportsOutput);
			runDatagen(mcVersion, datagenDirectory, executablePath, "--reports");
			if (!Files.exists(datagenReportsOutput) || !Files.isDirectory(datagenReportsOutput)) {
				MiscHelper.panic("Datagen step was required, but reports were not generated");
			}
			// Copy to artifact jar
			try (FileSystemUtil.Delegate reportsArchive = FileSystemUtil.getJarFileSystem(artifactReportsArchive, true)) {
				MiscHelper.copyLargeDir(datagenReportsOutput, reportsArchive.getPath("reports"));
			}
			MiscHelper.deleteDirectory(datagenReportsOutput);
			MiscHelper.deleteDirectory(datagenDirectory);
			return new StepOutput<>(status != null ? status : StepStatus.SUCCESS, results);
		}
		MiscHelper.deleteDirectory(datagenDirectory);
		return new StepOutput<>(StepStatus.SUCCESS, results);
	}

	public record Inputs(StorageKey serverJar, StorageKey dataJar) implements StepInput {
	}

	private void runDatagen(OrderedVersion mcVersion, Path cwd, Path executable, String... args) throws IOException, InterruptedException {
		if (mcVersion.compareTo(GitCraft.getApplicationConfiguration().manifestSource().getMetadataProvider().getVersionByVersionID(DATAGEN_BUNDLER_START_VERSION)) >= 0) {
			// >= DATAGEN_BUNDLER: java -DbundlerMainClass=net.minecraft.data.Main -jar minecraft_server.jar
			MiscHelper.createJavaJarSubprocess(executable, cwd, new String[]{"-DbundlerMainClass=net.minecraft.data.Main"}, args);
		} else {
			// < DATAGEN_BUNDLER: java -cp minecraft_server.jar net.minecraft.data.Main
			ArrayList<String> argsList = new ArrayList<>(List.of("net.minecraft.data.Main"));
			argsList.addAll(List.of(args));
			MiscHelper.createJavaCpSubprocess(executable, cwd, new String[0], argsList.toArray(String[]::new));
		}
	}

	public static class ExternalWorldgenPacks {

		private final TreeMap<OrderedVersion, Artifact> artifacts = new TreeMap<>();
		private final OrderedVersion minVersion;
		private final OrderedVersion maxVersion;

		private ExternalWorldgenPacks() {
			this.minVersion = GitCraft.getApplicationConfiguration().manifestSource().getMetadataProvider().getVersionByVersionID(EXT_VANILLA_WORLDGEN_PACK_START);
			this.maxVersion = GitCraft.getApplicationConfiguration().manifestSource().getMetadataProvider().getVersionByVersionID(EXT_VANILLA_WORLDGEN_PACK_END);

			if (this.minVersion != null && this.maxVersion != null) {
				setArtifact("20w28a", "https://raw.githubusercontent.com/slicedlime/examples/d304a1dcf330005e617a78cef4e492ab3e2c09b0/vanilla_worldgen.zip", "863ebaaf64386c6bd30bc88d140a9a3cc2547bcc");
				setArtifact("20w29a", "https://raw.githubusercontent.com/slicedlime/examples/3e513278469122f310567131e91287e4735c6b7c/vanilla_worldgen.zip", "76db0b2ec28ccc3d9045caaa7c3902318b26b3c1");
				setArtifact("20w30a", "https://raw.githubusercontent.com/slicedlime/examples/a6b68600e1ad4f3a489e551cfdfd4e2af49846cf/vanilla_worldgen.zip", "55d2d019a055b20c8396f0bc6c3b028683f85b61");
				setArtifact("1.16.2-pre1", "https://raw.githubusercontent.com/slicedlime/examples/2a97725e52665608abdf763becb597d0f1f73be2/vanilla_worldgen.zip", "8fcccd017a19a43ccc1f30a0def3e5385670ba32");
				setArtifact("1.16.2-pre2", "https://raw.githubusercontent.com/slicedlime/examples/64922d99af4728046568c324c82e7c0d7c3f2789/vanilla_worldgen.zip", "bca5bf8683ae4b35bdf8ffde85ebd88d131f646b");
				setArtifact("1.16.2-pre3", "https://raw.githubusercontent.com/slicedlime/examples/5614172d249fbaf148101ea5ba35d59ced6c1ebc/vanilla_worldgen.zip", "5667338b749f485bb57319296858b0f9416bf9ee");
				setArtifact("1.16.2-rc1", "https://raw.githubusercontent.com/slicedlime/examples/80fb4b8418ff3ff5724f4a0438bb422f58960bd9/vanilla_worldgen.zip", "f7297a68c6aec556699a1a35251f2f7d36299bbe");
				setArtifact("20w45a", "https://raw.githubusercontent.com/slicedlime/examples/baf7c0737151b606709c7574a95c5895a4ae56c1/vanilla_worldgen.zip", "2097c83750f9f52868b72ecb9fff389ec4b6a2f5");
				setArtifact("20w46a", "https://raw.githubusercontent.com/slicedlime/examples/9c5921df08a01f82017c43da8862ccbf29812e0a/vanilla_worldgen.zip", "78b7794d1d7d73404ce0fb932fa4ee87ec7893bf");
				setArtifact("20w49a", "https://raw.githubusercontent.com/slicedlime/examples/60ef645cf91f691dcaf10dbc0a05900aee5308d4/vanilla_worldgen.zip", "9948f0473ca51ba8f8a6da6686fe299b33bed74c");
				setArtifact("21w03a", "https://raw.githubusercontent.com/slicedlime/examples/7a9ba9ee83c8117ed85b45734f555a409f5e0d6d/vanilla_worldgen.zip", "00f00f1cfefffeeb594d13083b628d0759a59b27");
				setArtifact("21w05a", "https://raw.githubusercontent.com/slicedlime/examples/5c078f03d2c8ef3a7e75a2b882a5a9727b0f4252/vanilla_worldgen.zip", "c401851b53f6761f813ca0c63f2ca86efc875168");
				setArtifact("21w06a", "https://raw.githubusercontent.com/slicedlime/examples/f57ab938c661c2292c04ffad0899669470aa32cb/vanilla_worldgen.zip", "b79fe840d4976e8f100eb6e328dd1c9c406f1d75");
				setArtifact("21w07a", "https://raw.githubusercontent.com/slicedlime/examples/905b75fb72a3ed1ff4ceab2a5df333d950f5ffe6/vanilla_worldgen.zip", "e54a32c772c1bd60de0e9363e5315ca529ec0f1e");
				setArtifact("21w08a", "https://raw.githubusercontent.com/slicedlime/examples/9483c04b56ab04cd4f63727c749618f4a9c3610f/vanilla_worldgen.zip", "f7a9ca473469022efbed9575dbdd6c78bf5c49c8");
				setArtifact("21w08b", "https://raw.githubusercontent.com/slicedlime/examples/a6f23274ffef53413225cf1e80f3d3ea610247e2/vanilla_worldgen.zip", "f1805486d201f06032369db5dfb329f0c3cb5ce1");
				setArtifact("21w10a", "https://raw.githubusercontent.com/slicedlime/examples/e4a1442d0c7b78528e43273326fe4c89d58049e4/vanilla_worldgen.zip", "e4efd7dde1fa54136cdb276ceefa8fc24d664c85");
				setArtifact("21w11a", "https://raw.githubusercontent.com/slicedlime/examples/849c00bcd7d54a072d8c9b4d80811f31a8d27c3b/vanilla_worldgen.zip", "005aec17961d49a1c03479c0367091f1435b9a2e");
				setArtifact("21w13a", "https://raw.githubusercontent.com/slicedlime/examples/afcbc4abded8f6dd8d71871e72936073c3d50c17/vanilla_worldgen.zip", "289f114edc5c0efd9323ba8663d5ed7317b20bcf");
				setArtifact("21w14a", "https://raw.githubusercontent.com/slicedlime/examples/40f4a3ba19b97c3903f6e3c2680afbab9e610204/vanilla_worldgen.zip", "3066ed13cfa086396c87b0669bf47247903f3df6");
				setArtifact("21w15a", "https://raw.githubusercontent.com/slicedlime/examples/d4f598a206e88152058184f98cc4d07f2e36d591/vanilla_worldgen.zip", "1d41ce4a78a7dbaef34a4de17c15906b6e458881");
				setArtifact("21w16a", "https://raw.githubusercontent.com/slicedlime/examples/d41ad1838b7ec2dc30a90eda084435243d604d84/vanilla_worldgen.zip", "973f2a71e004542e8c583afb2187f93b2442fea8");
				setArtifact("21w17a", "https://raw.githubusercontent.com/slicedlime/examples/bda832e95ea37539f9010e917c6a133fa848d598/vanilla_worldgen.zip", "d600c1702aedfdb0caf42347c14e56c021973603");
				setArtifact("21w18a", "https://raw.githubusercontent.com/slicedlime/examples/a682c94e4b3bdb6abd65b32285b7f20833988221/vanilla_worldgen.zip", "1629a694c3f63f6e81dc5fd5cf42cb215c780c08");
				setArtifact("21w20a", "https://raw.githubusercontent.com/slicedlime/examples/f48a24cab87e1d1197fa1588955f7d6ce6f74ec5/vanilla_worldgen.zip", "e1cc90cc949b87b62f1c53d3361465ac6eef6b46");
				setArtifact("1.17-pre1", "https://raw.githubusercontent.com/slicedlime/examples/6b9ad5c7a6d8bea6b0221112f7cc996e4d027ff1/vanilla_worldgen.zip", "b2a83f22d2347207b903cf455bb9a9383b2f6893");
				setArtifact("1.17-pre2", "https://raw.githubusercontent.com/slicedlime/examples/23b9ac1ba5eceab976d7bdfef27707c2a44709ea/vanilla_worldgen.zip", "2f99749d27006879ad46660feeac9779de168d26");
				setArtifact("1.17.1-pre1", "https://raw.githubusercontent.com/slicedlime/examples/b9d8261bba055d0c59daa021d81b5e1274067a7b/vanilla_worldgen.zip", "fcb9f6bb959b7010eead441ce326246731ba89f9");
				setArtifact("1.17.1-pre2", "https://raw.githubusercontent.com/slicedlime/examples/7c54f55409f395a0aa517729669b20d570969f30/vanilla_worldgen.zip", "448c3cd0b7c8a427a9823e5d3b689188a756831f");
				setArtifact("21w37a", "https://raw.githubusercontent.com/slicedlime/examples/257a2e9b461f5975e61b3a3bddc65115b6ed0da4/vanilla_worldgen.zip", "8e57f54a319fdcc34522c41152fb6da7a46e51c0");
				setArtifact("21w38a", "https://raw.githubusercontent.com/slicedlime/examples/382cab373db1d9a01698f656a5456237fad5f1e6/vanilla_worldgen.zip", "77572f115135b8ff3ea2abc4c6d9eab8d331caf9");
				setArtifact("21w39a", "https://raw.githubusercontent.com/slicedlime/examples/9651c22030349eb03803ca8a4aa266880aac4592/vanilla_worldgen.zip", "b1608b9834a5da557aa8adaf841276ba7e59fa43");
				setArtifact("21w40a", "https://raw.githubusercontent.com/slicedlime/examples/5705625c80b7f943369b72717c7eb8f4bc5518cd/vanilla_worldgen.zip", "07c4c251099159227ce2735e773b82a08a9e2c27");
				setArtifact("21w41a", "https://raw.githubusercontent.com/slicedlime/examples/0702fe46df61afb564c354137d28e936f08600ba/vanilla_worldgen.zip", "acd112d87b9b6d4456d02d76af634d0ccbe0f7ba");
				setArtifact("21w42a", "https://raw.githubusercontent.com/slicedlime/examples/a25b894f099cd95cb95ef63d3fd1b93e6010491b/vanilla_worldgen.zip", "2e0bf6e130edab90083990f476c2ef9b9e4dedd8");
				setArtifact("21w43a", "https://raw.githubusercontent.com/slicedlime/examples/462d8e7fc256cace54c95a04325c9c8f2ed8af8d/vanilla_worldgen.zip", "841f115ae352181f3ad595ea2bb80252f88b9844");
				setArtifact("21w44a", "https://raw.githubusercontent.com/slicedlime/examples/d26b977501c38fdf4a03e262b859ffa32eb7badf/vanilla_worldgen.zip", "1547489f34a989b20b5a83a022d885020fd76f14");
			}
		}

		private void setArtifact(String version, String url, String sha1) {
			artifacts.put(
				GitCraft.getApplicationConfiguration().manifestSource().getMetadataProvider().getVersionByVersionID(version),
				Artifact.fromURL(url, sha1)
			);
		}

		public Tuple2<OrderedVersion, Artifact> get(OrderedVersion mcVersion) {
			if (minVersion != null && mcVersion.compareTo(minVersion) >= 0
					&& maxVersion != null && mcVersion.compareTo(maxVersion) <= 0) {
				Map.Entry<OrderedVersion, Artifact> entry = artifacts.floorEntry(mcVersion);
				if (entry != null) {
					return Tuple2.tuple(entry.getKey(), entry.getValue());
				}
			}

			return null;
		}
	}
}
