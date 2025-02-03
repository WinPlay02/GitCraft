package com.github.winplay02.gitcraft.pipeline;

import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.github.winplay02.gitcraft.util.GitCraftPaths;

public enum Step {

	RESET("Reset", Resetter::new),
	FETCH_ARTIFACTS("Fetch Artifacts", ArtifactsFetcher::new),
	FETCH_LIBRARIES("Fetch Libraries", LibrariesFetcher::new),
	FETCH_ASSETS("Fetch Assets", AssetsFetcher::new),
	UNPACK_ARTIFACTS("Unpack Artifacts", ArtifactsUnpacker::new),
	MERGE_OBFUSCATED_JARS("Merge Obfuscated Jars", JarsMerger::new),
	DATAGEN("Datagen", DataGenerator::new),
	PROVIDE_EXCEPTIONS("Provide Exceptions", ExceptionsProvider::new),
	APPLY_EXCEPTIONS("Apply Exceptions", JarsExceptor::new),
	PROVIDE_SIGNATURES("Provide Signatures", SignaturesProvider::new),
	APPLY_SIGNATURES("Apply Signatures", JarsSignatureChanger::new),
	PROVIDE_MAPPINGS("Provide Mappings", MappingsProvider::new),
	REMAP_JARS("Remap Jars", Remapper::new),
	MERGE_REMAPPED_JARS("Merge Remapped Jars", JarsMerger::new),
	UNPICK_JARS("Unpick Jars", Unpicker::new),
	PROVIDE_NESTS("Provide Nests", NestsProvider::new),
	APPLY_NESTS("Apply Nests", JarsNester::new),
	DECOMPILE_JARS("Decompile Jars", Decompiler::new),
	COMMIT("Commit to repository", Committer::new);

	private final String name;
	private final BiFunction<Step, StepWorker.Config, StepWorker> workerFactory;

	private final Map<DependencyType, Set<Step>> dependencies;
	private final Map<StepResult, Function<StepWorker.Context, Path>> resultFiles;
	private final Map<MinecraftJar, StepResult> minecraftJars;

	private Step(String name, BiFunction<Step, StepWorker.Config, StepWorker> workerFactory) {
		this.name = name;
		this.workerFactory = workerFactory;

		this.dependencies = new EnumMap<>(DependencyType.class);
		this.resultFiles = new HashMap<>();
		this.minecraftJars = new EnumMap<>(MinecraftJar.class);
	}

	private void setDependency(DependencyType type, Step dependency) {
		dependencies.computeIfAbsent(type, key -> EnumSet.noneOf(Step.class)).add(dependency);
	}

	private void setResultFile(StepResult resultFile, Path path) {
		resultFiles.put(resultFile, context -> path);
	}

	private void setResultFile(StepResult resultFile, Function<StepWorker.Context, Path> path) {
		resultFiles.put(resultFile, path);
	}

	private void setMinecraftJar(MinecraftJar minecraftJar, StepResult resultFile) {
		minecraftJars.put(minecraftJar, resultFile);
	}

	public String getName() {
		return name;
	}

	public Set<Step> getDependencies(DependencyType type) {
		return dependencies.getOrDefault(type, Collections.emptySet());
	}

	public DependencyType getDependencyType(Step other) {
		for (Map.Entry<DependencyType, Set<Step>> entry : dependencies.entrySet()) {
			if (entry.getValue().contains(other)) {
				return entry.getKey();
			}
		}

		return DependencyType.NONE;
	}

	public Path getResultFile(StepResult resultFile, StepWorker.Context context) {
		return resultFiles.get(resultFile).apply(context);
	}

	public StepResult getMinecraftJar(MinecraftJar minecraftJar) {
		return minecraftJars.get(minecraftJar);
	}

	public StepWorker createWorker(StepWorker.Config config) {
		return workerFactory.apply(this, config);
	}

	static {
		{
			FETCH_ARTIFACTS.setResultFile(ArtifactsFetcher.Results.ARTIFACTS_DIRECTORY, context -> GitCraftPaths.MC_VERSION_STORE.resolve(context.minecraftVersion().launcherFriendlyVersionName()));
			FETCH_ARTIFACTS.setResultFile(ArtifactsFetcher.Results.MINECRAFT_CLIENT_JAR, context -> FETCH_ARTIFACTS.getResultFile(ArtifactsFetcher.Results.ARTIFACTS_DIRECTORY, context).resolve("client.jar"));
			FETCH_ARTIFACTS.setResultFile(ArtifactsFetcher.Results.MINECRAFT_SERVER_JAR, context -> FETCH_ARTIFACTS.getResultFile(ArtifactsFetcher.Results.ARTIFACTS_DIRECTORY, context).resolve("server.jar"));
			FETCH_ARTIFACTS.setResultFile(ArtifactsFetcher.Results.MINECRAFT_SERVER_EXE, context -> FETCH_ARTIFACTS.getResultFile(ArtifactsFetcher.Results.ARTIFACTS_DIRECTORY, context).resolve("server.exe"));
			FETCH_ARTIFACTS.setResultFile(ArtifactsFetcher.Results.MINECRAFT_SERVER_ZIP, context -> FETCH_ARTIFACTS.getResultFile(ArtifactsFetcher.Results.ARTIFACTS_DIRECTORY, context).resolve("server.zip"));

			FETCH_ARTIFACTS.setMinecraftJar(MinecraftJar.CLIENT, ArtifactsFetcher.Results.MINECRAFT_CLIENT_JAR);
			FETCH_ARTIFACTS.setMinecraftJar(MinecraftJar.SERVER, ArtifactsFetcher.Results.MINECRAFT_SERVER_JAR);
		}
		{
			FETCH_LIBRARIES.setResultFile(LibrariesFetcher.Results.LIBRARIES_DIRECTORY, GitCraftPaths.LIBRARY_STORE);
		}
		{
			FETCH_ASSETS.setResultFile(AssetsFetcher.ResultFiles.ASSETS_INDEX_DIRECTORY, GitCraftPaths.ASSETS_INDEX);
			FETCH_ASSETS.setResultFile(AssetsFetcher.ResultFiles.ASSETS_OBJECTS_DIRECTORY, GitCraftPaths.ASSETS_OBJECTS);
			FETCH_ASSETS.setResultFile(AssetsFetcher.ResultFiles.ASSETS_INDEX, context -> FETCH_ASSETS.getResultFile(AssetsFetcher.ResultFiles.ASSETS_INDEX_DIRECTORY, context).resolve(context.minecraftVersion().assetsIndex().name()));
		}
		{
			UNPACK_ARTIFACTS.setDependency(DependencyType.REQUIRED, FETCH_ARTIFACTS);

			UNPACK_ARTIFACTS.setResultFile(ArtifactsUnpacker.Results.MINECRAFT_SERVER_JAR, context -> FETCH_ARTIFACTS.getResultFile(ArtifactsFetcher.Results.MINECRAFT_SERVER_JAR, context));

			UNPACK_ARTIFACTS.setMinecraftJar(MinecraftJar.SERVER, ArtifactsUnpacker.Results.MINECRAFT_SERVER_JAR);
		}
		{
			MERGE_OBFUSCATED_JARS.setDependency(DependencyType.REQUIRED, FETCH_ARTIFACTS);
			MERGE_OBFUSCATED_JARS.setDependency(DependencyType.REQUIRED, UNPACK_ARTIFACTS);

			MERGE_OBFUSCATED_JARS.setResultFile(JarsMerger.Results.OBFUSCATED_MINECRAFT_MERGED_JAR, context -> FETCH_ARTIFACTS.getResultFile(ArtifactsFetcher.Results.ARTIFACTS_DIRECTORY, context).resolve("merged.jar"));

			MERGE_OBFUSCATED_JARS.setMinecraftJar(MinecraftJar.MERGED, JarsMerger.Results.OBFUSCATED_MINECRAFT_MERGED_JAR);
		}
		{
			DATAGEN.setDependency(DependencyType.REQUIRED, FETCH_ARTIFACTS);
			DATAGEN.setDependency(DependencyType.REQUIRED, UNPACK_ARTIFACTS);
			DATAGEN.setDependency(DependencyType.REQUIRED, MERGE_OBFUSCATED_JARS);

			DATAGEN.setResultFile(DataGenerator.Results.ARTIFACTS_SNBT_ARCHIVE, context -> FETCH_ARTIFACTS.getResultFile(ArtifactsFetcher.Results.ARTIFACTS_DIRECTORY, context).resolve("datagen-snbt.jar"));
			DATAGEN.setResultFile(DataGenerator.Results.ARTIFACTS_REPORTS_ARCHIVE, context -> FETCH_ARTIFACTS.getResultFile(ArtifactsFetcher.Results.ARTIFACTS_DIRECTORY, context).resolve("datagen-reports.jar"));
			DATAGEN.setResultFile(DataGenerator.Results.DATAGEN_DIRECTORY, context -> FETCH_ARTIFACTS.getResultFile(ArtifactsFetcher.Results.ARTIFACTS_DIRECTORY, context).resolve("datagenerator"));
			DATAGEN.setResultFile(DataGenerator.Results.DATAGEN_NBT_SOURCE_DIRECTORY, context -> DATAGEN.getResultFile(DataGenerator.Results.DATAGEN_DIRECTORY, context).resolve("input"));
			DATAGEN.setResultFile(DataGenerator.Results.DATAGEN_NBT_SOURCE_DATA_DIRECTORY, context -> DATAGEN.getResultFile(DataGenerator.Results.DATAGEN_NBT_SOURCE_DIRECTORY, context).resolve("data"));
			DATAGEN.setResultFile(DataGenerator.Results.DATAGEN_SNBT_DESTINATION_DIRECTORY, context -> DATAGEN.getResultFile(DataGenerator.Results.DATAGEN_DIRECTORY, context).resolve("output"));
			DATAGEN.setResultFile(DataGenerator.Results.DATAGEN_SNBT_DESTINATION_DATA_DIRECTORY, context -> DATAGEN.getResultFile(DataGenerator.Results.DATAGEN_SNBT_DESTINATION_DIRECTORY, context).resolve("data"));
			DATAGEN.setResultFile(DataGenerator.Results.DATAGEN_REPORTS_DIRECTORY, context -> DATAGEN.getResultFile(DataGenerator.Results.DATAGEN_DIRECTORY, context).resolve("generated").resolve("reports"));
		}
		{
			APPLY_EXCEPTIONS.setDependency(DependencyType.REQUIRED, FETCH_ARTIFACTS);
			APPLY_EXCEPTIONS.setDependency(DependencyType.REQUIRED, UNPACK_ARTIFACTS);
			APPLY_EXCEPTIONS.setDependency(DependencyType.REQUIRED, PROVIDE_EXCEPTIONS);
			APPLY_EXCEPTIONS.setDependency(DependencyType.NOT_REQUIRED, MERGE_OBFUSCATED_JARS);

			APPLY_EXCEPTIONS.setResultFile(JarsExceptor.Results.EXCEPTIONS_APPLIED_DIRECTORY, context -> GitCraftPaths.EXCEPTIONS_APPLIED.resolve(context.minecraftVersion().launcherFriendlyVersionName()));
			APPLY_EXCEPTIONS.setResultFile(JarsExceptor.Results.MINECRAFT_CLIENT_JAR, context -> APPLY_EXCEPTIONS.getResultFile(JarsExceptor.Results.EXCEPTIONS_APPLIED_DIRECTORY, context).resolve("client-exceptions-patched.jar"));
			APPLY_EXCEPTIONS.setResultFile(JarsExceptor.Results.MINECRAFT_SERVER_JAR, context -> APPLY_EXCEPTIONS.getResultFile(JarsExceptor.Results.EXCEPTIONS_APPLIED_DIRECTORY, context).resolve("server-exceptions-patched.jar"));
			APPLY_EXCEPTIONS.setResultFile(JarsExceptor.Results.MINECRAFT_MERGED_JAR, context -> APPLY_EXCEPTIONS.getResultFile(JarsExceptor.Results.EXCEPTIONS_APPLIED_DIRECTORY, context).resolve("merged-exceptions-patched.jar"));

			APPLY_EXCEPTIONS.setMinecraftJar(MinecraftJar.CLIENT, JarsExceptor.Results.MINECRAFT_CLIENT_JAR);
			APPLY_EXCEPTIONS.setMinecraftJar(MinecraftJar.SERVER, JarsExceptor.Results.MINECRAFT_SERVER_JAR);
			APPLY_EXCEPTIONS.setMinecraftJar(MinecraftJar.MERGED, JarsExceptor.Results.MINECRAFT_MERGED_JAR);
		}
		{
			APPLY_SIGNATURES.setDependency(DependencyType.REQUIRED, FETCH_ARTIFACTS);
			APPLY_SIGNATURES.setDependency(DependencyType.REQUIRED, UNPACK_ARTIFACTS);
			APPLY_SIGNATURES.setDependency(DependencyType.REQUIRED, PROVIDE_SIGNATURES);
			APPLY_SIGNATURES.setDependency(DependencyType.NOT_REQUIRED, MERGE_OBFUSCATED_JARS);
			APPLY_SIGNATURES.setDependency(DependencyType.NOT_REQUIRED, APPLY_EXCEPTIONS);

			APPLY_SIGNATURES.setResultFile(JarsSignatureChanger.Results.SIGNATURES_APPLIED_DIRECTORY, context -> GitCraftPaths.SIGNATURES_APPLIED.resolve(context.minecraftVersion().launcherFriendlyVersionName()));
			APPLY_SIGNATURES.setResultFile(JarsSignatureChanger.Results.MINECRAFT_CLIENT_JAR, context -> APPLY_SIGNATURES.getResultFile(JarsSignatureChanger.Results.SIGNATURES_APPLIED_DIRECTORY, context).resolve("client-signatures-patched.jar"));
			APPLY_SIGNATURES.setResultFile(JarsSignatureChanger.Results.MINECRAFT_SERVER_JAR, context -> APPLY_SIGNATURES.getResultFile(JarsSignatureChanger.Results.SIGNATURES_APPLIED_DIRECTORY, context).resolve("server-signatures-patched.jar"));
			APPLY_SIGNATURES.setResultFile(JarsSignatureChanger.Results.MINECRAFT_MERGED_JAR, context -> APPLY_SIGNATURES.getResultFile(JarsSignatureChanger.Results.SIGNATURES_APPLIED_DIRECTORY, context).resolve("merged-signatures-patched.jar"));

			APPLY_SIGNATURES.setMinecraftJar(MinecraftJar.CLIENT, JarsSignatureChanger.Results.MINECRAFT_CLIENT_JAR);
			APPLY_SIGNATURES.setMinecraftJar(MinecraftJar.SERVER, JarsSignatureChanger.Results.MINECRAFT_SERVER_JAR);
			APPLY_SIGNATURES.setMinecraftJar(MinecraftJar.MERGED, JarsSignatureChanger.Results.MINECRAFT_MERGED_JAR);
		}
		{
			REMAP_JARS.setDependency(DependencyType.REQUIRED, FETCH_ARTIFACTS);
			REMAP_JARS.setDependency(DependencyType.REQUIRED, UNPACK_ARTIFACTS);
			REMAP_JARS.setDependency(DependencyType.REQUIRED, PROVIDE_MAPPINGS);
			REMAP_JARS.setDependency(DependencyType.NOT_REQUIRED, MERGE_OBFUSCATED_JARS);
			REMAP_JARS.setDependency(DependencyType.NOT_REQUIRED, APPLY_EXCEPTIONS);
			REMAP_JARS.setDependency(DependencyType.NOT_REQUIRED, APPLY_SIGNATURES);

			REMAP_JARS.setResultFile(Remapper.Results.REMAPPED_JARS_DIRECTORY, context -> GitCraftPaths.REMAPPED.resolve(context.minecraftVersion().launcherFriendlyVersionName()));
			REMAP_JARS.setResultFile(Remapper.Results.MINECRAFT_CLIENT_JAR, context -> REMAP_JARS.getResultFile(Remapper.Results.REMAPPED_JARS_DIRECTORY, context).resolve("client-remapped.jar"));
			REMAP_JARS.setResultFile(Remapper.Results.MINECRAFT_SERVER_JAR, context -> REMAP_JARS.getResultFile(Remapper.Results.REMAPPED_JARS_DIRECTORY, context).resolve("server-remapped.jar"));
			REMAP_JARS.setResultFile(Remapper.Results.MINECRAFT_MERGED_JAR, context -> REMAP_JARS.getResultFile(Remapper.Results.REMAPPED_JARS_DIRECTORY, context).resolve("merged-remapped.jar"));

			REMAP_JARS.setMinecraftJar(MinecraftJar.CLIENT, Remapper.Results.MINECRAFT_CLIENT_JAR);
			REMAP_JARS.setMinecraftJar(MinecraftJar.SERVER, Remapper.Results.MINECRAFT_SERVER_JAR);
			REMAP_JARS.setMinecraftJar(MinecraftJar.MERGED, Remapper.Results.MINECRAFT_MERGED_JAR);
		}
		{
			MERGE_REMAPPED_JARS.setDependency(DependencyType.REQUIRED, REMAP_JARS);

			MERGE_REMAPPED_JARS.setResultFile(JarsMerger.Results.REMAPPED_MINECRAFT_MERGED_JAR, context -> REMAP_JARS.getResultFile(Remapper.Results.REMAPPED_JARS_DIRECTORY, context).resolve("merged-remapped.jar"));

			MERGE_REMAPPED_JARS.setMinecraftJar(MinecraftJar.MERGED, JarsMerger.Results.REMAPPED_MINECRAFT_MERGED_JAR);
		}
		{
			UNPICK_JARS.setDependency(DependencyType.REQUIRED, FETCH_LIBRARIES);
			UNPICK_JARS.setDependency(DependencyType.REQUIRED, PROVIDE_MAPPINGS);
			UNPICK_JARS.setDependency(DependencyType.REQUIRED, APPLY_EXCEPTIONS);
			UNPICK_JARS.setDependency(DependencyType.REQUIRED, APPLY_SIGNATURES);
			UNPICK_JARS.setDependency(DependencyType.REQUIRED, REMAP_JARS);

			UNPICK_JARS.setResultFile(Unpicker.Results.UNPICKED_JARS_DIRECTORY, context -> GitCraftPaths.UNPICKED.resolve(context.minecraftVersion().launcherFriendlyVersionName()));
			UNPICK_JARS.setResultFile(Unpicker.Results.MINECRAFT_CLIENT_JAR, context -> UNPICK_JARS.getResultFile(Unpicker.Results.UNPICKED_JARS_DIRECTORY, context).resolve("client-unpicked.jar"));
			UNPICK_JARS.setResultFile(Unpicker.Results.MINECRAFT_SERVER_JAR, context -> UNPICK_JARS.getResultFile(Unpicker.Results.UNPICKED_JARS_DIRECTORY, context).resolve("server-unpicked.jar"));
			UNPICK_JARS.setResultFile(Unpicker.Results.MINECRAFT_MERGED_JAR, context -> UNPICK_JARS.getResultFile(Unpicker.Results.UNPICKED_JARS_DIRECTORY, context).resolve("merged-unpicked.jar"));

			UNPICK_JARS.setMinecraftJar(MinecraftJar.CLIENT, Unpicker.Results.MINECRAFT_CLIENT_JAR);
			UNPICK_JARS.setMinecraftJar(MinecraftJar.SERVER, Unpicker.Results.MINECRAFT_SERVER_JAR);
			UNPICK_JARS.setMinecraftJar(MinecraftJar.MERGED, Unpicker.Results.MINECRAFT_MERGED_JAR);
		}
		{
			PROVIDE_NESTS.setDependency(DependencyType.REQUIRED, PROVIDE_MAPPINGS);
		}
		{
			APPLY_NESTS.setDependency(DependencyType.REQUIRED, REMAP_JARS);
			APPLY_NESTS.setDependency(DependencyType.REQUIRED, PROVIDE_NESTS);
			APPLY_NESTS.setDependency(DependencyType.NOT_REQUIRED, UNPICK_JARS);

			APPLY_NESTS.setResultFile(JarsNester.Results.NESTS_APPLIED_DIRECTORY, context -> GitCraftPaths.NESTS_APPLIED.resolve(context.minecraftVersion().launcherFriendlyVersionName()));
			APPLY_NESTS.setResultFile(JarsNester.Results.MINECRAFT_CLIENT_JAR, context -> APPLY_NESTS.getResultFile(JarsNester.Results.NESTS_APPLIED_DIRECTORY, context).resolve("client-nested.jar"));
			APPLY_NESTS.setResultFile(JarsNester.Results.MINECRAFT_SERVER_JAR, context -> APPLY_NESTS.getResultFile(JarsNester.Results.NESTS_APPLIED_DIRECTORY, context).resolve("server-nested.jar"));
			APPLY_NESTS.setResultFile(JarsNester.Results.MINECRAFT_MERGED_JAR, context -> APPLY_NESTS.getResultFile(JarsNester.Results.NESTS_APPLIED_DIRECTORY, context).resolve("merged-nested.jar"));

			APPLY_NESTS.setMinecraftJar(MinecraftJar.CLIENT, JarsNester.Results.MINECRAFT_CLIENT_JAR);
			APPLY_NESTS.setMinecraftJar(MinecraftJar.SERVER, JarsNester.Results.MINECRAFT_SERVER_JAR);
			APPLY_NESTS.setMinecraftJar(MinecraftJar.MERGED, JarsNester.Results.MINECRAFT_MERGED_JAR);
		}
		{
			DECOMPILE_JARS.setDependency(DependencyType.REQUIRED, FETCH_ARTIFACTS);
			DECOMPILE_JARS.setDependency(DependencyType.REQUIRED, UNPACK_ARTIFACTS);
			DECOMPILE_JARS.setDependency(DependencyType.REQUIRED, FETCH_LIBRARIES);
			DECOMPILE_JARS.setDependency(DependencyType.NOT_REQUIRED, MERGE_OBFUSCATED_JARS);
			DECOMPILE_JARS.setDependency(DependencyType.NOT_REQUIRED, APPLY_EXCEPTIONS);
			DECOMPILE_JARS.setDependency(DependencyType.NOT_REQUIRED, APPLY_SIGNATURES);
			DECOMPILE_JARS.setDependency(DependencyType.NOT_REQUIRED, REMAP_JARS);
			DECOMPILE_JARS.setDependency(DependencyType.NOT_REQUIRED, MERGE_REMAPPED_JARS);
			DECOMPILE_JARS.setDependency(DependencyType.NOT_REQUIRED, UNPICK_JARS);
			DECOMPILE_JARS.setDependency(DependencyType.NOT_REQUIRED, APPLY_NESTS);

			DECOMPILE_JARS.setResultFile(Decompiler.Results.DECOMPILED_JARS_DIRECTORY, context -> GitCraftPaths.DECOMPILED_WORKINGS.resolve(context.minecraftVersion().launcherFriendlyVersionName()));
			DECOMPILE_JARS.setResultFile(Decompiler.Results.MINECRAFT_CLIENT_JAR, context -> DECOMPILE_JARS.getResultFile(Decompiler.Results.DECOMPILED_JARS_DIRECTORY, context).resolve("client.jar"));
			DECOMPILE_JARS.setResultFile(Decompiler.Results.MINECRAFT_SERVER_JAR, context -> DECOMPILE_JARS.getResultFile(Decompiler.Results.DECOMPILED_JARS_DIRECTORY, context).resolve("server.jar"));
			DECOMPILE_JARS.setResultFile(Decompiler.Results.MINECRAFT_MERGED_JAR, context -> DECOMPILE_JARS.getResultFile(Decompiler.Results.DECOMPILED_JARS_DIRECTORY, context).resolve("merged.jar"));

			DECOMPILE_JARS.setMinecraftJar(MinecraftJar.CLIENT, Decompiler.Results.MINECRAFT_CLIENT_JAR);
			DECOMPILE_JARS.setMinecraftJar(MinecraftJar.SERVER, Decompiler.Results.MINECRAFT_SERVER_JAR);
			DECOMPILE_JARS.setMinecraftJar(MinecraftJar.MERGED, Decompiler.Results.MINECRAFT_MERGED_JAR);
		}
		{
			COMMIT.setDependency(DependencyType.REQUIRED, FETCH_ARTIFACTS);
			COMMIT.setDependency(DependencyType.REQUIRED, UNPACK_ARTIFACTS);
			COMMIT.setDependency(DependencyType.REQUIRED, FETCH_ASSETS);
			COMMIT.setDependency(DependencyType.REQUIRED, DECOMPILE_JARS);
		}
	}
}
