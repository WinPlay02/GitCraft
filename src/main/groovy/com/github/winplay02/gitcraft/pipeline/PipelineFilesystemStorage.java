package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.pipeline.key.ArtifactKey;
import com.github.winplay02.gitcraft.pipeline.key.DirectoryKey;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.LazyValue;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public record PipelineFilesystemStorage(PipelineFilesystemRoot rootFilesystem,
										Set<StorageKey> resettableKeys,
										Map<StorageKey, BiFunction<PipelineFilesystemStorage, StepWorker.Context, Path>> paths) {
	@SafeVarargs
	public PipelineFilesystemStorage(PipelineFilesystemRoot rootFilesystem, Set<StorageKey> resettableKeys, Map<StorageKey, BiFunction<PipelineFilesystemStorage, StepWorker.Context, Path>>... paths) {
		this(rootFilesystem, resettableKeys, MiscHelper.mergeMaps(new HashMap<>(), paths));
	}

	public Path getPath(StorageKey key, StepWorker.Context context) {
		return this.paths.get(key).apply(this, context);
	}

	private static BiFunction<PipelineFilesystemStorage, StepWorker.Context, Path> rootPathConst(Function<PipelineFilesystemRoot, Path> rootPathConstFunction) {
		return (root, _context) -> rootPathConstFunction.apply(root.rootFilesystem());
	}

	private static BiFunction<PipelineFilesystemStorage, StepWorker.Context, Path> rootPathVersioned(Function<PipelineFilesystemRoot, Path> rootPathConstFunction) {
		return (root, context) -> rootPathConstFunction.apply(root.rootFilesystem()).resolve(context.minecraftVersion().launcherFriendlyVersionName());
	}

	private Path resolvePath(StorageKey key, StepWorker.Context context, String toResolveFirst, String... toResolve) {
		return this.paths.get(key).apply(this, context).resolve(toResolveFirst, toResolve);
	}

	private static BiFunction<PipelineFilesystemStorage, StepWorker.Context, Path> createFromKey(StorageKey key, String toResolveFirst, String... toResolve) {
		return (root, context) -> root.resolvePath(key, context, toResolveFirst, toResolve);
	}

	private static BiFunction<PipelineFilesystemStorage, StepWorker.Context, Path> createFromKey(StorageKey key, Function<StepWorker.Context, String> toResolve) {
		return (root, context) -> root.resolvePath(key, context, toResolve.apply(context));
	}

	private static final String SIDE_CLIENT = "client";
	private static final String SIDE_SERVER = "server";
	private static final String SIDE_MERGED = "merged";

	private static final String DIST_JAR = "jar";
	private static final String DIST_EXE = "exe";
	private static final String DIST_ZIP = "zip";
	private static final String DIST_JSON = "json";

	private static final String HINT_UNPACKED = "unpacked";
	private static final String HINT_OBFUSCATED = "obfuscated";
	private static final String HINT_REMAPPED = "remapped";
	private static final String HINT_UNPICKED = "unpicked";
	private static final String HINT_DECOMPILED = "decompiled";

	public static final DirectoryKey ARTIFACTS = new DirectoryKey("artifacts");
	public static final DirectoryKey LIBRARIES = new DirectoryKey("libraries");
	public static final DirectoryKey ASSETS_INDEX = new DirectoryKey("assets-index");
	public static final DirectoryKey ASSETS_OBJECTS = new DirectoryKey("assets-objects");
	public static final DirectoryKey ARTIFACTS_DATAGEN = new DirectoryKey("artifacts-datagen");
	public static final DirectoryKey TEMP_DATAGEN_NBT_SOURCE_DIRECTORY = new DirectoryKey("artifacts-datagen-nbt-src");
	public static final DirectoryKey TEMP_DATAGEN_NBT_SOURCE_DATA_DIRECTORY = new DirectoryKey("artifacts-datagen-nbt-data-src");
	public static final DirectoryKey TEMP_DATAGEN_SNBT_DESTINATION_DIRECTORY = new DirectoryKey("artifacts-datagen-snbt-dst");
	public static final DirectoryKey TEMP_DATAGEN_SNBT_DESTINATION_DATA_DIRECTORY = new DirectoryKey("artifacts-datagen-snbt-data-dst");
	public static final DirectoryKey TEMP_DATAGEN_REPORTS_DIRECTORY = new DirectoryKey("artifacts-datagen-reports");
	public static final DirectoryKey REMAPPED = new DirectoryKey("remapped");
	public static final DirectoryKey DECOMPILED = new DirectoryKey("decompiled");

	public static final ArtifactKey ARTIFACTS_CLIENT_JAR = new ArtifactKey(ARTIFACTS, SIDE_CLIENT, DIST_JAR);
	public static final ArtifactKey ARTIFACTS_SERVER_JAR = new ArtifactKey(ARTIFACTS, SIDE_SERVER, DIST_JAR);
	public static final ArtifactKey ARTIFACTS_SERVER_EXE = new ArtifactKey(ARTIFACTS, SIDE_SERVER, DIST_EXE);
	public static final ArtifactKey ARTIFACTS_SERVER_ZIP = new ArtifactKey(ARTIFACTS, SIDE_SERVER, DIST_ZIP);
	public static final ArtifactKey ARTIFACTS_VANILLA_WORLDGEN_DATAPACK_ZIP = new ArtifactKey(ARTIFACTS, "vanilla-datapack", DIST_ZIP);
	public static final ArtifactKey ASSETS_INDEX_JSON = new ArtifactKey(ASSETS_INDEX, DIST_JSON);
	public static final ArtifactKey UNPACKED_SERVER_JAR = new ArtifactKey(ARTIFACTS, SIDE_SERVER, DIST_JAR, HINT_UNPACKED);
	public static final ArtifactKey MERGED_JAR_OBFUSCATED = new ArtifactKey(ARTIFACTS, SIDE_MERGED, DIST_JAR, HINT_OBFUSCATED);
	public static final ArtifactKey DATAGEN_SNBT_ARCHIVE = new ArtifactKey(ARTIFACTS, "datagen", "snbt", HINT_UNPACKED);
	public static final ArtifactKey DATAGEN_REPORTS_ARCHIVE = new ArtifactKey(ARTIFACTS, "datagen", "reports", HINT_OBFUSCATED);

	// TODO Move remapped (and unpicked) artifacts to version directory?
	// TODO Do not distinguish between both variants as there should be no execution plan able to reach both variants
	// For Remap Step (after merging)
	public static final ArtifactKey REMAPPED_CLIENT_JAR = new ArtifactKey(REMAPPED, SIDE_CLIENT, DIST_JAR);
	public static final ArtifactKey REMAPPED_SERVER_JAR = new ArtifactKey(REMAPPED, SIDE_SERVER, DIST_JAR);
	public static final ArtifactKey REMAPPED_MERGED_JAR = new ArtifactKey(REMAPPED, SIDE_MERGED, DIST_JAR);

	// For Merge Step (after remapping)
	public static final ArtifactKey MERGED_JAR_REMAPPED = new ArtifactKey(ARTIFACTS, SIDE_MERGED, DIST_JAR, HINT_REMAPPED);

	public static final ArtifactKey UNPICKED_CLIENT_JAR = new ArtifactKey(REMAPPED, SIDE_CLIENT, DIST_JAR, HINT_UNPICKED);
	public static final ArtifactKey UNPICKED_SERVER_JAR = new ArtifactKey(REMAPPED, SIDE_SERVER, DIST_JAR, HINT_UNPICKED);
	public static final ArtifactKey UNPICKED_MERGED_JAR = new ArtifactKey(REMAPPED, SIDE_MERGED, DIST_JAR, HINT_UNPICKED);

	// TODO Move decompiled artifacts to version directory?
	public static final ArtifactKey DECOMPILED_CLIENT_JAR = new ArtifactKey(DECOMPILED, SIDE_CLIENT, DIST_JAR);
	public static final ArtifactKey DECOMPILED_SERVER_JAR = new ArtifactKey(DECOMPILED, SIDE_SERVER, DIST_JAR);
	public static final ArtifactKey DECOMPILED_MERGED_JAR = new ArtifactKey(DECOMPILED, SIDE_MERGED, DIST_JAR);

	public static final LazyValue<PipelineFilesystemStorage> DEFAULT = LazyValue.of(() -> new PipelineFilesystemStorage(
		GitCraftPaths.FILESYSTEM_ROOT,
		Set.of(
			REMAPPED_CLIENT_JAR, REMAPPED_SERVER_JAR, REMAPPED_MERGED_JAR,
			MERGED_JAR_REMAPPED,
			UNPICKED_CLIENT_JAR, UNPICKED_SERVER_JAR, UNPICKED_MERGED_JAR,
			DECOMPILED_CLIENT_JAR, DECOMPILED_SERVER_JAR, DECOMPILED_MERGED_JAR
		),
		Map.of(
			ARTIFACTS, rootPathVersioned(PipelineFilesystemRoot::getMcVersionStore),
			ARTIFACTS_CLIENT_JAR, createFromKey(ARTIFACTS, "client.jar"),
			ARTIFACTS_SERVER_JAR, createFromKey(ARTIFACTS, "server.jar"),
			ARTIFACTS_SERVER_EXE, createFromKey(ARTIFACTS, "server.exe"),
			ARTIFACTS_SERVER_ZIP, createFromKey(ARTIFACTS, "server.zip"),
			ARTIFACTS_VANILLA_WORLDGEN_DATAPACK_ZIP, createFromKey(ARTIFACTS, "vanilla_worldgen.zip")
		),
		Map.of(
			LIBRARIES, rootPathConst(PipelineFilesystemRoot::getLibraryStore),
			ASSETS_INDEX, rootPathConst(PipelineFilesystemRoot::getAssetsIndex),
			ASSETS_OBJECTS, rootPathConst(PipelineFilesystemRoot::getAssetsObjects),
			ASSETS_INDEX_JSON, createFromKey(ASSETS_INDEX, context -> context.minecraftVersion().assetsIndex().name()),
			UNPACKED_SERVER_JAR, createFromKey(ARTIFACTS, "server-unpacked.jar"),
			MERGED_JAR_OBFUSCATED, createFromKey(ARTIFACTS, "merged-obfuscated.zip")
		),
		Map.of(
			ARTIFACTS_DATAGEN, createFromKey(ARTIFACTS, "datagenerator"),
			TEMP_DATAGEN_NBT_SOURCE_DIRECTORY, createFromKey(ARTIFACTS_DATAGEN, "input"),
			TEMP_DATAGEN_NBT_SOURCE_DATA_DIRECTORY, createFromKey(TEMP_DATAGEN_NBT_SOURCE_DIRECTORY, "data"),
			TEMP_DATAGEN_SNBT_DESTINATION_DIRECTORY, createFromKey(ARTIFACTS_DATAGEN, "output"),
			TEMP_DATAGEN_SNBT_DESTINATION_DATA_DIRECTORY, createFromKey(TEMP_DATAGEN_SNBT_DESTINATION_DIRECTORY, "data"),
			TEMP_DATAGEN_REPORTS_DIRECTORY, createFromKey(ARTIFACTS_DATAGEN, "generated", "reports"),
			DATAGEN_SNBT_ARCHIVE, createFromKey(ARTIFACTS, "datagen-snbt.jar"),
			DATAGEN_REPORTS_ARCHIVE, createFromKey(ARTIFACTS, "datagen-reports.jar")
		),
		Map.of(
			REMAPPED, rootPathVersioned(PipelineFilesystemRoot::getRemapped),
			REMAPPED_CLIENT_JAR, createFromKey(REMAPPED, "client-remapped.jar"),
			REMAPPED_SERVER_JAR, createFromKey(REMAPPED, "server-remapped.jar"),
			REMAPPED_MERGED_JAR, createFromKey(REMAPPED, "merged-remapped-1.jar"),
			MERGED_JAR_REMAPPED, createFromKey(ARTIFACTS, "merged-remapped-2.jar")
		),
		Map.of(
			UNPICKED_CLIENT_JAR, createFromKey(REMAPPED, "client-unpicked.jar"),
			UNPICKED_SERVER_JAR, createFromKey(REMAPPED, "server-unpicked.jar"),
			UNPICKED_MERGED_JAR, createFromKey(REMAPPED, "merged-unpicked.jar"),
			DECOMPILED, rootPathVersioned(PipelineFilesystemRoot::getDecompiled),
			DECOMPILED_CLIENT_JAR, createFromKey(DECOMPILED, "client.jar"),
			DECOMPILED_SERVER_JAR, createFromKey(DECOMPILED, "server.jar"),
			DECOMPILED_MERGED_JAR, createFromKey(DECOMPILED, "merged.jar")
		)));
}
