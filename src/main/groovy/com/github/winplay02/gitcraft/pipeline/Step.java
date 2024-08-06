package com.github.winplay02.gitcraft.pipeline;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.github.winplay02.gitcraft.util.GitCraftPaths;

public enum Step {

	RESET("Reset", true, Resetter::new),
	FETCH_ARTIFACTS("Fetch Artifacts", true, ArtifactsFetcher::new) {

		{
			Path root = GitCraftPaths.MC_VERSION_STORE;

			setResultFile(ArtifactsFetcher.Results.ARTIFACTS_DIRECTORY, context -> root.resolve(context.minecraftVersion().launcherFriendlyVersionName()));
			setResultFile(ArtifactsFetcher.Results.MINECRAFT_CLIENT_JAR, context -> getResultFile(ArtifactsFetcher.Results.ARTIFACTS_DIRECTORY, context).resolve("client.jar"));
			setResultFile(ArtifactsFetcher.Results.MINECRAFT_SERVER_JAR, context -> getResultFile(ArtifactsFetcher.Results.ARTIFACTS_DIRECTORY, context).resolve("server.jar"));
			setResultFile(ArtifactsFetcher.Results.MINECRAFT_SERVER_EXE, context -> getResultFile(ArtifactsFetcher.Results.ARTIFACTS_DIRECTORY, context).resolve("server.exe"));
			setResultFile(ArtifactsFetcher.Results.MINECRAFT_SERVER_ZIP, context -> getResultFile(ArtifactsFetcher.Results.ARTIFACTS_DIRECTORY, context).resolve("server.zip"));

			setMinecraftJar(MinecraftJar.CLIENT, ArtifactsFetcher.Results.MINECRAFT_CLIENT_JAR);
			setMinecraftJar(MinecraftJar.SERVER, ArtifactsFetcher.Results.MINECRAFT_SERVER_JAR);
		}
	},
	FETCH_LIBRARIES("Fetch Libraries", true, LibrariesFetcher::new) {

		{
			setResultFile(LibrariesFetcher.Results.LIBRARIES_DIRECTORY, GitCraftPaths.LIBRARY_STORE);
		}
	},
	FETCH_ASSETS("Fetch Assets", true, AssetsFetcher::new) {

		{
			setResultFile(AssetsFetcher.ResultFiles.ASSETS_INDEX_DIRECTORY, GitCraftPaths.ASSETS_INDEX);
			setResultFile(AssetsFetcher.ResultFiles.ASSETS_OBJECTS_DIRECTORY, GitCraftPaths.ASSETS_OBJECTS);
			setResultFile(AssetsFetcher.ResultFiles.ASSETS_INDEX, context -> getResultFile(AssetsFetcher.ResultFiles.ASSETS_INDEX_DIRECTORY, context).resolve(context.minecraftVersion().assetsIndexId()));
		}
	},
	UNPACK_ARTIFACTS("Unpack Artifacts", true, ArtifactsUnpacker::new) {

		{
			setResultFile(ArtifactsUnpacker.Results.MINECRAFT_SERVER_JAR, context -> FETCH_ARTIFACTS.getResultFile(ArtifactsFetcher.Results.MINECRAFT_SERVER_JAR, context));

			setMinecraftJar(MinecraftJar.SERVER, ArtifactsUnpacker.Results.MINECRAFT_SERVER_JAR);
		}
	},
	MERGE_OBFUSCATED_JARS("Merge Obfuscated Jars", true, JarsMerger::new) {

		{
			setResultFile(JarsMerger.Results.OBFUSCATED_MINECRAFT_MERGED_JAR, context -> FETCH_ARTIFACTS.getResultFile(ArtifactsFetcher.Results.ARTIFACTS_DIRECTORY, context).resolve("merged.jar"));

			setMinecraftJar(MinecraftJar.MERGED, JarsMerger.Results.OBFUSCATED_MINECRAFT_MERGED_JAR);
		}
	},
	DATAGEN("Datagen", true, DataGenerator::new) {

		{
			setResultFile(DataGenerator.Results.ARTIFACTS_SNBT_ARCHIVE, context -> FETCH_ARTIFACTS.getResultFile(ArtifactsFetcher.Results.ARTIFACTS_DIRECTORY, context).resolve("datagen-snbt.jar"));
			setResultFile(DataGenerator.Results.ARTIFACTS_REPORTS_ARCHIVE, context -> FETCH_ARTIFACTS.getResultFile(ArtifactsFetcher.Results.ARTIFACTS_DIRECTORY, context).resolve("datagen-reports.jar"));
			setResultFile(DataGenerator.Results.DATAGEN_DIRECTORY, context -> FETCH_ARTIFACTS.getResultFile(ArtifactsFetcher.Results.ARTIFACTS_DIRECTORY, context).resolve("datagenerator"));
			setResultFile(DataGenerator.Results.DATAGEN_NBT_SOURCE_DIRECTORY, context -> getResultFile(DataGenerator.Results.DATAGEN_DIRECTORY, context).resolve("input"));
			setResultFile(DataGenerator.Results.DATAGEN_NBT_SOURCE_DATA_DIRECTORY, context -> getResultFile(DataGenerator.Results.DATAGEN_NBT_SOURCE_DIRECTORY, context).resolve("data"));
			setResultFile(DataGenerator.Results.DATAGEN_SNBT_DESTINATION_DIRECTORY, context -> getResultFile(DataGenerator.Results.DATAGEN_DIRECTORY, context).resolve("output"));
			setResultFile(DataGenerator.Results.DATAGEN_SNBT_DESTINATION_DATA_DIRECTORY, context -> getResultFile(DataGenerator.Results.DATAGEN_SNBT_DESTINATION_DIRECTORY, context).resolve("data"));
			setResultFile(DataGenerator.Results.DATAGEN_REPORTS_DIRECTORY, context -> getResultFile(DataGenerator.Results.DATAGEN_DIRECTORY, context).resolve("generated").resolve("reports"));
		}
	},
	PROVIDE_MAPPINGS("Provide Mappings", false, MappingsProvider::new),
	REMAP_JARS("Remap Jars", false, Remapper::new) {

		{
			Path root = GitCraftPaths.REMAPPED;

			setResultFile(Remapper.Results.REMAPPED_JARS_DIRECTORY, context -> root.resolve(context.minecraftVersion().launcherFriendlyVersionName()));
			setResultFile(Remapper.Results.MINECRAFT_CLIENT_JAR, context -> getResultFile(Remapper.Results.REMAPPED_JARS_DIRECTORY, context).resolve("client-remapped.jar"));
			setResultFile(Remapper.Results.MINECRAFT_SERVER_JAR, context -> getResultFile(Remapper.Results.REMAPPED_JARS_DIRECTORY, context).resolve("server-remapped.jar"));
			setResultFile(Remapper.Results.MINECRAFT_MERGED_JAR, context -> getResultFile(Remapper.Results.REMAPPED_JARS_DIRECTORY, context).resolve("merged-remapped.jar"));

			setMinecraftJar(MinecraftJar.CLIENT, Remapper.Results.MINECRAFT_CLIENT_JAR);
			setMinecraftJar(MinecraftJar.SERVER, Remapper.Results.MINECRAFT_SERVER_JAR);
			setMinecraftJar(MinecraftJar.MERGED, Remapper.Results.MINECRAFT_MERGED_JAR);
		}
	},
	MERGE_REMAPPED_JARS("Merge Remapped Jars", false, JarsMerger::new) {

		{
			setResultFile(JarsMerger.Results.REMAPPED_MINECRAFT_MERGED_JAR, context -> REMAP_JARS.getResultFile(Remapper.Results.REMAPPED_JARS_DIRECTORY, context).resolve("merged-remapped.jar"));

			setMinecraftJar(MinecraftJar.MERGED, JarsMerger.Results.REMAPPED_MINECRAFT_MERGED_JAR);
		}
	},
	UNPICK_JARS("Unpick Jars", true, Unpicker::new) {

		{
			setResultFile(Unpicker.Results.MINECRAFT_CLIENT_JAR, context -> REMAP_JARS.getResultFile(Remapper.Results.REMAPPED_JARS_DIRECTORY, context).resolve("client-unpicked.jar"));
			setResultFile(Unpicker.Results.MINECRAFT_SERVER_JAR, context -> REMAP_JARS.getResultFile(Remapper.Results.REMAPPED_JARS_DIRECTORY, context).resolve("server-unpicked.jar"));
			setResultFile(Unpicker.Results.MINECRAFT_MERGED_JAR, context -> REMAP_JARS.getResultFile(Remapper.Results.REMAPPED_JARS_DIRECTORY, context).resolve("merged-unpicked.jar"));

			setMinecraftJar(MinecraftJar.CLIENT, Unpicker.Results.MINECRAFT_CLIENT_JAR);
			setMinecraftJar(MinecraftJar.SERVER, Unpicker.Results.MINECRAFT_SERVER_JAR);
			setMinecraftJar(MinecraftJar.MERGED, Unpicker.Results.MINECRAFT_MERGED_JAR);
		}
	},
	DECOMPILE_JARS("Decompile Jars", true, Decompiler::new) {

		{
			Path root = GitCraftPaths.DECOMPILED_WORKINGS;

			setResultFile(Decompiler.Results.DECOMPILED_JARS_DIRECTORY, context -> root.resolve(context.minecraftVersion().launcherFriendlyVersionName()));
			setResultFile(Decompiler.Results.MINECRAFT_CLIENT_JAR, context -> getResultFile(Decompiler.Results.DECOMPILED_JARS_DIRECTORY, context).resolve("client.jar"));
			setResultFile(Decompiler.Results.MINECRAFT_SERVER_JAR, context -> getResultFile(Decompiler.Results.DECOMPILED_JARS_DIRECTORY, context).resolve("server.jar"));
			setResultFile(Decompiler.Results.MINECRAFT_MERGED_JAR, context -> getResultFile(Decompiler.Results.DECOMPILED_JARS_DIRECTORY, context).resolve("merged.jar"));

			setMinecraftJar(MinecraftJar.CLIENT, Decompiler.Results.MINECRAFT_CLIENT_JAR);
			setMinecraftJar(MinecraftJar.SERVER, Decompiler.Results.MINECRAFT_SERVER_JAR);
			setMinecraftJar(MinecraftJar.MERGED, Decompiler.Results.MINECRAFT_MERGED_JAR);
		}
	};

	private final String name;
	private final boolean ignoresMappings;
	private final Map<StepResult, Function<StepWorker.Context, Path>> resultFiles;
	private final Map<MinecraftJar, StepResult> minecraftJars;
	private final BiFunction<Step, StepWorker.Config, StepWorker> workerFactory;

	private Step(String name, boolean ignoresMappings, BiFunction<Step, StepWorker.Config, StepWorker> workerFactory) {
		this.name = name;
		this.ignoresMappings = ignoresMappings;
		this.resultFiles = new HashMap<>();
		this.minecraftJars = new EnumMap<>(MinecraftJar.class);
		this.workerFactory = workerFactory;
	}

	protected void setResultFile(StepResult resultFile, Path path) {
		resultFiles.put(resultFile, context -> path);
	}

	protected void setResultFile(StepResult resultFile, Function<StepWorker.Context, Path> path) {
		resultFiles.put(resultFile, path);
	}

	protected void setMinecraftJar(MinecraftJar minecraftJar, StepResult resultFile) {
		minecraftJars.put(minecraftJar, resultFile);
	}

	public String getName() {
		return name;
	}

	public boolean ignoresMappings() {
		return ignoresMappings;
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
}
