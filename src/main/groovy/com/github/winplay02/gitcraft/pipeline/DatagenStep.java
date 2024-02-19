package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.MinecraftVersionGraph;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;
import groovy.lang.Tuple2;
import net.fabricmc.loom.util.FileSystemUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class DatagenStep extends Step {
	private static final String DATAGEN_AVAILABLE_ID = "18w01a";
	private static final String DATAGEN_BUNDLER_ID = "21w39a";
	protected static final String EXT_VANILLA_WORLDGEN_PACK_START_ID = "20w28a";
	protected static final String EXT_VANILLA_WORLDGEN_PACK_END_ID = "21w44a";
	protected static final Map<String, Artifact> EXT_VANILLA_WORLDGEN_PACK;
	private TreeMap<OrderedVersion, Artifact> orderedExtVanillaWorldgen = null;

	static {
		EXT_VANILLA_WORLDGEN_PACK = new TreeMap<>();
		EXT_VANILLA_WORLDGEN_PACK.put("20w28a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/d304a1dcf330005e617a78cef4e492ab3e2c09b0/vanilla_worldgen.zip", "863ebaaf64386c6bd30bc88d140a9a3cc2547bcc"));
		EXT_VANILLA_WORLDGEN_PACK.put("20w29a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/3e513278469122f310567131e91287e4735c6b7c/vanilla_worldgen.zip", "76db0b2ec28ccc3d9045caaa7c3902318b26b3c1"));
		EXT_VANILLA_WORLDGEN_PACK.put("20w30a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/a6b68600e1ad4f3a489e551cfdfd4e2af49846cf/vanilla_worldgen.zip", "55d2d019a055b20c8396f0bc6c3b028683f85b61"));
		EXT_VANILLA_WORLDGEN_PACK.put("1.16.2-pre1", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/2a97725e52665608abdf763becb597d0f1f73be2/vanilla_worldgen.zip", "8fcccd017a19a43ccc1f30a0def3e5385670ba32"));
		EXT_VANILLA_WORLDGEN_PACK.put("1.16.2-pre2", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/64922d99af4728046568c324c82e7c0d7c3f2789/vanilla_worldgen.zip", "bca5bf8683ae4b35bdf8ffde85ebd88d131f646b"));
		EXT_VANILLA_WORLDGEN_PACK.put("1.16.2-pre3", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/5614172d249fbaf148101ea5ba35d59ced6c1ebc/vanilla_worldgen.zip", "5667338b749f485bb57319296858b0f9416bf9ee"));
		EXT_VANILLA_WORLDGEN_PACK.put("1.16.2-rc1", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/80fb4b8418ff3ff5724f4a0438bb422f58960bd9/vanilla_worldgen.zip", "f7297a68c6aec556699a1a35251f2f7d36299bbe"));
		EXT_VANILLA_WORLDGEN_PACK.put("20w45a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/baf7c0737151b606709c7574a95c5895a4ae56c1/vanilla_worldgen.zip", "2097c83750f9f52868b72ecb9fff389ec4b6a2f5"));
		EXT_VANILLA_WORLDGEN_PACK.put("20w46a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/9c5921df08a01f82017c43da8862ccbf29812e0a/vanilla_worldgen.zip", "78b7794d1d7d73404ce0fb932fa4ee87ec7893bf"));
		EXT_VANILLA_WORLDGEN_PACK.put("20w49a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/60ef645cf91f691dcaf10dbc0a05900aee5308d4/vanilla_worldgen.zip", "9948f0473ca51ba8f8a6da6686fe299b33bed74c"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w03a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/7a9ba9ee83c8117ed85b45734f555a409f5e0d6d/vanilla_worldgen.zip", "00f00f1cfefffeeb594d13083b628d0759a59b27"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w05a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/5c078f03d2c8ef3a7e75a2b882a5a9727b0f4252/vanilla_worldgen.zip", "c401851b53f6761f813ca0c63f2ca86efc875168"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w06a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/f57ab938c661c2292c04ffad0899669470aa32cb/vanilla_worldgen.zip", "b79fe840d4976e8f100eb6e328dd1c9c406f1d75"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w07a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/905b75fb72a3ed1ff4ceab2a5df333d950f5ffe6/vanilla_worldgen.zip", "e54a32c772c1bd60de0e9363e5315ca529ec0f1e"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w08a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/9483c04b56ab04cd4f63727c749618f4a9c3610f/vanilla_worldgen.zip", "f7a9ca473469022efbed9575dbdd6c78bf5c49c8"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w08b", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/a6f23274ffef53413225cf1e80f3d3ea610247e2/vanilla_worldgen.zip", "f1805486d201f06032369db5dfb329f0c3cb5ce1"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w10a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/e4a1442d0c7b78528e43273326fe4c89d58049e4/vanilla_worldgen.zip", "e4efd7dde1fa54136cdb276ceefa8fc24d664c85"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w11a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/849c00bcd7d54a072d8c9b4d80811f31a8d27c3b/vanilla_worldgen.zip", "005aec17961d49a1c03479c0367091f1435b9a2e"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w13a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/afcbc4abded8f6dd8d71871e72936073c3d50c17/vanilla_worldgen.zip", "289f114edc5c0efd9323ba8663d5ed7317b20bcf"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w14a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/40f4a3ba19b97c3903f6e3c2680afbab9e610204/vanilla_worldgen.zip", "3066ed13cfa086396c87b0669bf47247903f3df6"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w15a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/d4f598a206e88152058184f98cc4d07f2e36d591/vanilla_worldgen.zip", "1d41ce4a78a7dbaef34a4de17c15906b6e458881"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w16a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/d41ad1838b7ec2dc30a90eda084435243d604d84/vanilla_worldgen.zip", "973f2a71e004542e8c583afb2187f93b2442fea8"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w17a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/bda832e95ea37539f9010e917c6a133fa848d598/vanilla_worldgen.zip", "d600c1702aedfdb0caf42347c14e56c021973603"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w18a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/a682c94e4b3bdb6abd65b32285b7f20833988221/vanilla_worldgen.zip", "1629a694c3f63f6e81dc5fd5cf42cb215c780c08"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w20a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/f48a24cab87e1d1197fa1588955f7d6ce6f74ec5/vanilla_worldgen.zip", "e1cc90cc949b87b62f1c53d3361465ac6eef6b46"));
		EXT_VANILLA_WORLDGEN_PACK.put("1.17-pre1", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/6b9ad5c7a6d8bea6b0221112f7cc996e4d027ff1/vanilla_worldgen.zip", "b2a83f22d2347207b903cf455bb9a9383b2f6893"));
		EXT_VANILLA_WORLDGEN_PACK.put("1.17-pre2", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/23b9ac1ba5eceab976d7bdfef27707c2a44709ea/vanilla_worldgen.zip", "2f99749d27006879ad46660feeac9779de168d26"));
		EXT_VANILLA_WORLDGEN_PACK.put("1.17.1-pre1", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/b9d8261bba055d0c59daa021d81b5e1274067a7b/vanilla_worldgen.zip", "fcb9f6bb959b7010eead441ce326246731ba89f9"));
		EXT_VANILLA_WORLDGEN_PACK.put("1.17.1-pre2", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/7c54f55409f395a0aa517729669b20d570969f30/vanilla_worldgen.zip", "448c3cd0b7c8a427a9823e5d3b689188a756831f"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w37a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/257a2e9b461f5975e61b3a3bddc65115b6ed0da4/vanilla_worldgen.zip", "8e57f54a319fdcc34522c41152fb6da7a46e51c0"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w38a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/382cab373db1d9a01698f656a5456237fad5f1e6/vanilla_worldgen.zip", "77572f115135b8ff3ea2abc4c6d9eab8d331caf9"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w39a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/9651c22030349eb03803ca8a4aa266880aac4592/vanilla_worldgen.zip", "b1608b9834a5da557aa8adaf841276ba7e59fa43"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w40a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/5705625c80b7f943369b72717c7eb8f4bc5518cd/vanilla_worldgen.zip", "07c4c251099159227ce2735e773b82a08a9e2c27"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w41a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/0702fe46df61afb564c354137d28e936f08600ba/vanilla_worldgen.zip", "acd112d87b9b6d4456d02d76af634d0ccbe0f7ba"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w42a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/a25b894f099cd95cb95ef63d3fd1b93e6010491b/vanilla_worldgen.zip", "2e0bf6e130edab90083990f476c2ef9b9e4dedd8"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w43a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/462d8e7fc256cace54c95a04325c9c8f2ed8af8d/vanilla_worldgen.zip", "841f115ae352181f3ad595ea2bb80252f88b9844"));
		EXT_VANILLA_WORLDGEN_PACK.put("21w44a", Artifact.fromURL("https://raw.githubusercontent.com/slicedlime/examples/d26b977501c38fdf4a03e262b859ffa32eb7badf/vanilla_worldgen.zip", "1547489f34a989b20b5a83a022d885020fd76f14"));
	}

	@Override
	public String getName() {
		return STEP_DATAGEN;
	}

	@Override
	public boolean ignoresMappings() {
		return true;
	}

	@Override
	protected Path getInternalArtifactPath(OrderedVersion mcVersion, MappingFlavour mappingFlavour) {
		return GitCraft.STEP_FETCH_ARTIFACTS.getInternalArtifactPath(mcVersion, mappingFlavour).resolve("datagenerator");
	}

	@Override
	public boolean preconditionsShouldRun(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) {
		return mcVersion.compareTo(GitCraft.config.manifestSource.getManifestSourceImpl().getVersionByVersionID(DATAGEN_AVAILABLE_ID)) >= 0 && (GitCraft.config.loadDatagenRegistry || (GitCraft.config.readableNbt && GitCraft.config.loadIntegratedDatapack)) && super.preconditionsShouldRun(pipelineCache, mcVersion, mappingFlavour, versionGraph, repo);
	}

	private TreeMap<OrderedVersion, Artifact> getExtVanillaWorldgenPackOrderedVersions() {
		if (orderedExtVanillaWorldgen == null) {
			try {
				Map<String, OrderedVersion> manifestVersionMetaMap = GitCraft.config.manifestSource.getManifestSourceImpl().getVersionMeta();
				orderedExtVanillaWorldgen = new TreeMap<>(EXT_VANILLA_WORLDGEN_PACK.entrySet().stream().map((entry) -> Tuple2.tuple(manifestVersionMetaMap.get(entry.getKey()), entry.getValue())).collect(Collectors.toMap(Tuple2::getV1, Tuple2::getV2)));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return orderedExtVanillaWorldgen;
	}

	protected Tuple2<OrderedVersion, Artifact> getExtVanillaWorldgenPack(OrderedVersion mcVersion) {
		if (mcVersion.compareTo(GitCraft.config.manifestSource.getManifestSourceImpl().getVersionByVersionID(EXT_VANILLA_WORLDGEN_PACK_START_ID)) >= 0 && mcVersion.compareTo(GitCraft.config.manifestSource.getManifestSourceImpl().getVersionByVersionID(EXT_VANILLA_WORLDGEN_PACK_END_ID)) <= 0) {
			Map.Entry<OrderedVersion, Artifact> entry = getExtVanillaWorldgenPackOrderedVersions().floorEntry(mcVersion);
			if (entry != null) {
				return Tuple2.tuple(entry.getKey(), entry.getValue());
			}
		}
		return null;
	}

	protected Path getDatagenSNBTArchive(Path rootPath) {
		return rootPath.resolve("datagen-snbt.jar");
	}

	protected Path getDatagenReportsArchive(Path rootPath) {
		return rootPath.resolve("datagen-reports.jar");
	}

	@Override
	public StepResult run(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) throws Exception {
		if (!mcVersion.hasServerCode() || !mcVersion.hasServerJar()) {
			MiscHelper.panic("Cannot execute datagen, no jar available");
		}
		Path artifactsRootPath = pipelineCache.getForKey(Step.STEP_FETCH_ARTIFACTS);
		Path artifactSnbtArchive = getDatagenSNBTArchive(artifactsRootPath);
		if (Files.exists(artifactSnbtArchive) && Files.size(artifactSnbtArchive) <= 22 /* empty jar */) {
			Files.delete(artifactSnbtArchive);
		}
		Path artifactReportsArchive = getDatagenReportsArchive(artifactsRootPath);
		if (Files.exists(artifactReportsArchive) && Files.size(artifactReportsArchive) <= 22 /* empty jar */) {
			Files.delete(artifactReportsArchive);
		}
		if ((!GitCraft.config.readableNbt || Files.exists(artifactSnbtArchive)) &&
				(!GitCraft.config.loadDatagenRegistry || Files.exists(artifactReportsArchive))) {
			return StepResult.UP_TO_DATE;
		}
		Path executablePath = mcVersion.serverDist().serverJar().resolve(artifactsRootPath);
		Path datagenDirectory = getInternalArtifactPath(mcVersion, mappingFlavour);
		Files.createDirectories(datagenDirectory);
		if (GitCraft.config.readableNbt) {
			// Structures (& more)
			{
				Path mergedJarPath = pipelineCache.getForKey(Step.STEP_MERGE);
				if (mergedJarPath == null) { // Client JAR could also work, if merge did not happen
					MiscHelper.panic("A merged JAR for version %s does not exist", mcVersion.launcherFriendlyVersionName());
				}
				try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(mergedJarPath)) {
					MiscHelper.copyLargeDir(fs.get().getPath("data"), getDatagenNBTSourcePathData(datagenDirectory));
				}
			}
			// Delete Output files, as some versions do not work, when files already exist
			Path datagenSnbtOutput = getDatagenSNBTDestinationPath(datagenDirectory);
			MiscHelper.deleteDirectory(datagenSnbtOutput);
			executeDatagen(mcVersion, datagenDirectory, executablePath, "--dev",
					"--input", getDatagenNBTSourcePath(datagenDirectory).toAbsolutePath().toString(),
					"--output", datagenSnbtOutput.toAbsolutePath().toString()
			);
			// Delete input files, as they are no longer needed
			MiscHelper.deleteDirectory(getDatagenNBTSourcePath(datagenDirectory));
			if (!Files.exists(datagenSnbtOutput) || !Files.isDirectory(datagenSnbtOutput)) {
				MiscHelper.panic("Datagen step was required, but SNBT files were not generated");
			}
			// Copy to artifact jar
			try (FileSystemUtil.Delegate snbtArchive = FileSystemUtil.getJarFileSystem(artifactSnbtArchive, true)) {
				MiscHelper.copyLargeDir(getDatagenSNBTDestinationPathData(datagenDirectory), snbtArchive.getPath("data"));
			}
			MiscHelper.deleteDirectory(datagenSnbtOutput);
		}
		if (GitCraft.config.loadDatagenRegistry) {
			StepResult result = null;
			Tuple2<OrderedVersion, Artifact> worldgenPack = getExtVanillaWorldgenPack(mcVersion);
			if (worldgenPack != null) {
				result = worldgenPack.getV2().fetchArtifact(GitCraft.STEP_FETCH_ARTIFACTS.getInternalArtifactPath(worldgenPack.getV1(), null), "worldgen datapack");
			}
			// Datagen
			Path datagenReportsOutput = getDatagenReports(datagenDirectory);
			MiscHelper.deleteDirectory(datagenReportsOutput);
			executeDatagen(mcVersion, datagenDirectory, executablePath, "--reports");
			if (!Files.exists(datagenReportsOutput) || !Files.isDirectory(datagenReportsOutput)) {
				MiscHelper.panic("Datagen step was required, but reports were not generated");
			}
			// Copy to artifact jar
			try (FileSystemUtil.Delegate reportsArchive = FileSystemUtil.getJarFileSystem(artifactReportsArchive, true)) {
				MiscHelper.copyLargeDir(datagenReportsOutput, reportsArchive.getPath("reports"));
			}
			MiscHelper.deleteDirectory(datagenReportsOutput);
			MiscHelper.deleteDirectory(datagenDirectory);
			return result != null ? result : StepResult.SUCCESS;
		}
		MiscHelper.deleteDirectory(datagenDirectory);
		return StepResult.SUCCESS;
	}

	private void executeDatagen(OrderedVersion mcVersion, Path cwd, Path executable, String... args) throws IOException, InterruptedException {
		if (mcVersion.compareTo(GitCraft.config.manifestSource.getManifestSourceImpl().getVersionByVersionID(DATAGEN_BUNDLER_ID)) >= 0) {
			// >= DATAGEN_BUNDLER: java -DbundlerMainClass=net.minecraft.data.Main -jar minecraft_server.jar
			MiscHelper.createJavaJarSubprocess(executable, cwd, new String[]{"-DbundlerMainClass=net.minecraft.data.Main"}, args);
		} else {
			// < DATAGEN_BUNDLER: java -cp minecraft_server.jar net.minecraft.data.Main
			ArrayList<String> argsList = new ArrayList<>(List.of("net.minecraft.data.Main"));
			argsList.addAll(List.of(args));
			MiscHelper.createJavaCpSubprocess(executable, cwd, new String[0], argsList.toArray(new String[0]));
		}
	}

	protected Path getDatagenReports(Path rootDatagenPath) {
		return rootDatagenPath.resolve("generated").resolve("reports");
	}

	private Path getDatagenNBTSourcePath(Path rootDatagenPath) {
		return rootDatagenPath.resolve("input");
	}

	private Path getDatagenNBTSourcePathData(Path rootDatagenPath) {
		return getDatagenNBTSourcePath(rootDatagenPath).resolve("data");
	}

	private Path getDatagenSNBTDestinationPath(Path rootDatagenPath) {
		return rootDatagenPath.resolve("output");
	}

	protected Path getDatagenSNBTDestinationPathData(Path rootDatagenPath) {
		return getDatagenSNBTDestinationPath(rootDatagenPath).resolve("data");
	}
}
