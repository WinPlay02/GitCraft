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
	PROVIDE_MAPPINGS("Provide Mappings", MappingsProvider::new),
	REMAP_JARS("Remap Jars", Remapper::new),
	MERGE_REMAPPED_JARS("Merge Remapped Jars", JarsMerger::new),
	UNPICK_JARS("Unpick Jars", Unpicker::new),
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

		for (DependencyType type : DependencyType.values()) {
			if (type != DependencyType.NONE) {
				this.dependencies.put(type, EnumSet.noneOf(Step.class));
			}
		}
	}

	private void setDependency(DependencyType type, Step dependency) {
		dependencies.get(type).add(dependency);
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
			REMAP_JARS.setDependency(DependencyType.REQUIRED, FETCH_ARTIFACTS);
			REMAP_JARS.setDependency(DependencyType.REQUIRED, UNPACK_ARTIFACTS);
			REMAP_JARS.setDependency(DependencyType.REQUIRED, PROVIDE_MAPPINGS);
			REMAP_JARS.setDependency(DependencyType.NOT_REQUIRED, MERGE_OBFUSCATED_JARS);

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
			UNPICK_JARS.setDependency(DependencyType.REQUIRED, REMAP_JARS);

			UNPICK_JARS.setResultFile(Unpicker.Results.MINECRAFT_CLIENT_JAR, context -> REMAP_JARS.getResultFile(Remapper.Results.REMAPPED_JARS_DIRECTORY, context).resolve("client-unpicked.jar"));
			UNPICK_JARS.setResultFile(Unpicker.Results.MINECRAFT_SERVER_JAR, context -> REMAP_JARS.getResultFile(Remapper.Results.REMAPPED_JARS_DIRECTORY, context).resolve("server-unpicked.jar"));
			UNPICK_JARS.setResultFile(Unpicker.Results.MINECRAFT_MERGED_JAR, context -> REMAP_JARS.getResultFile(Remapper.Results.REMAPPED_JARS_DIRECTORY, context).resolve("merged-unpicked.jar"));

			UNPICK_JARS.setMinecraftJar(MinecraftJar.CLIENT, Unpicker.Results.MINECRAFT_CLIENT_JAR);
			UNPICK_JARS.setMinecraftJar(MinecraftJar.SERVER, Unpicker.Results.MINECRAFT_SERVER_JAR);
			UNPICK_JARS.setMinecraftJar(MinecraftJar.MERGED, Unpicker.Results.MINECRAFT_MERGED_JAR);
		}
		{
			DECOMPILE_JARS.setDependency(DependencyType.REQUIRED, FETCH_ARTIFACTS);
			DECOMPILE_JARS.setDependency(DependencyType.REQUIRED, UNPACK_ARTIFACTS);
			DECOMPILE_JARS.setDependency(DependencyType.REQUIRED, FETCH_LIBRARIES);
			DECOMPILE_JARS.setDependency(DependencyType.NOT_REQUIRED, MERGE_OBFUSCATED_JARS);
			DECOMPILE_JARS.setDependency(DependencyType.NOT_REQUIRED, REMAP_JARS);
			DECOMPILE_JARS.setDependency(DependencyType.NOT_REQUIRED, MERGE_REMAPPED_JARS);
			DECOMPILE_JARS.setDependency(DependencyType.NOT_REQUIRED, UNPICK_JARS);

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
