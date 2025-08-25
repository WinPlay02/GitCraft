package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.pipeline.key.ArtifactKey;
import com.github.winplay02.gitcraft.pipeline.key.DirectoryKey;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.LazyValue;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public record PipelineFilesystemStorage<T extends AbstractVersion<T>>(PipelineFilesystemRoot rootFilesystem,
																   Set<StorageKey> resettableKeys,
																   Map<StorageKey, BiFunction<PipelineFilesystemStorage<T>, StepWorker.Context<T>, Path>> paths) {
	@SafeVarargs
	public PipelineFilesystemStorage(PipelineFilesystemRoot rootFilesystem, Set<StorageKey> resettableKeys, Map<StorageKey, BiFunction<PipelineFilesystemStorage<T>, StepWorker.Context<T>, Path>>... paths) {
		this(rootFilesystem, resettableKeys, MiscHelper.mergeMaps(new HashMap<>(), paths));
	}

	public Path getPath(StorageKey key, StepWorker.Context<T> context) {
		if (key == null || !this.paths.containsKey(key)) {
			return null;
		}
		return this.paths.get(key).apply(this, context);
	}

	private static <T extends AbstractVersion<T>> BiFunction<PipelineFilesystemStorage<T>, StepWorker.Context<T>, Path> rootPathConst(Function<PipelineFilesystemRoot, Path> rootPathConstFunction) {
		return (root, _context) -> rootPathConstFunction.apply(root.rootFilesystem());
	}

	private static <T extends AbstractVersion<T>> BiFunction<PipelineFilesystemStorage<T>, StepWorker.Context<T>, Path> rootPathVersioned(Function<PipelineFilesystemRoot, Path> rootPathConstFunction) {
		return (root, context) -> rootPathConstFunction.apply(root.rootFilesystem()).resolve(context.targetVersion().pathName());
	}

	private Path resolvePath(StorageKey key, StepWorker.Context<T> context, String toResolveFirst, String... toResolve) {
		return this.paths.get(key).apply(this, context).resolve(toResolveFirst, toResolve);
	}

	private static <T extends AbstractVersion<T>> BiFunction<PipelineFilesystemStorage<T>, StepWorker.Context<T>, Path> createFromKey(StorageKey key, String toResolveFirst, String... toResolve) {
		return (root, context) -> root.resolvePath(key, context, toResolveFirst, toResolve);
	}

	private static <T extends AbstractVersion<T>> BiFunction<PipelineFilesystemStorage<T>, StepWorker.Context<T>, Path> createFromKey(StorageKey key, Function<StepWorker.Context<T>, String> toResolve) {
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
	private static final String HINT_UNBUNDLED = "unbundled";
	private static final String HINT_OBFUSCATED = "obfuscated";
	private static final String HINT_REMAPPED = "remapped";
	private static final String HINT_UNPICKED = "unpicked";
	private static final String HINT_DECOMPILED = "decompiled";
	private static final String HINT_LVT = "lvt";
	private static final String HINT_EXCEPTIONS = "exceptions";
	private static final String HINT_SIGNATURES = "signatures";
	private static final String HINT_NESTED = "nested";
	private static final String HINT_PREENED = "preened";

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
	public static final DirectoryKey PATCHES = new DirectoryKey("patches");
	public static final DirectoryKey PATCHED = new DirectoryKey("patched");

	public static final ArtifactKey ARTIFACTS_CLIENT_JAR = new ArtifactKey(ARTIFACTS, SIDE_CLIENT, DIST_JAR);
	public static final ArtifactKey ARTIFACTS_SERVER_JAR = new ArtifactKey(ARTIFACTS, SIDE_SERVER, DIST_JAR);
	public static final ArtifactKey ARTIFACTS_SERVER_EXE = new ArtifactKey(ARTIFACTS, SIDE_SERVER, DIST_EXE);
	public static final ArtifactKey ARTIFACTS_SERVER_ZIP = new ArtifactKey(ARTIFACTS, SIDE_SERVER, DIST_ZIP);
	public static final ArtifactKey ARTIFACTS_MERGED_JAR = new ArtifactKey(ARTIFACTS, SIDE_MERGED, DIST_JAR);
	public static final ArtifactKey ARTIFACTS_VANILLA_WORLDGEN_DATAPACK_ZIP = new ArtifactKey(ARTIFACTS, "vanilla-datapack", DIST_ZIP);
	public static final ArtifactKey ASSETS_INDEX_JSON = new ArtifactKey(ASSETS_INDEX, DIST_JSON);
	public static final ArtifactKey UNPACKED_SERVER_JAR = new ArtifactKey(ARTIFACTS, SIDE_SERVER, DIST_JAR, HINT_UNPACKED);
	public static final ArtifactKey UNBUNDLED_SERVER_JAR = new ArtifactKey(ARTIFACTS, SIDE_SERVER, DIST_JAR, HINT_UNBUNDLED);
	public static final ArtifactKey DATAGEN_SNBT_ARCHIVE = new ArtifactKey(ARTIFACTS, "datagen", "snbt", HINT_UNPACKED);
	public static final ArtifactKey DATAGEN_REPORTS_ARCHIVE = new ArtifactKey(ARTIFACTS, "datagen", "reports", HINT_OBFUSCATED);

	// TODO Move remapped (and unpicked) artifacts to version directory?
	// TODO Do not distinguish between both variants as there should be no execution plan able to reach both variants
	// For Remap Step (after merging)
	public static final ArtifactKey REMAPPED_CLIENT_JAR = new ArtifactKey(REMAPPED, SIDE_CLIENT, DIST_JAR);
	public static final ArtifactKey REMAPPED_SERVER_JAR = new ArtifactKey(REMAPPED, SIDE_SERVER, DIST_JAR);
	public static final ArtifactKey REMAPPED_MERGED_JAR = new ArtifactKey(REMAPPED, SIDE_MERGED, DIST_JAR);

	// For Unpick Step
	public static final ArtifactKey UNPICKED_CLIENT_JAR = new ArtifactKey(REMAPPED, SIDE_CLIENT, DIST_JAR, HINT_UNPICKED);
	public static final ArtifactKey UNPICKED_SERVER_JAR = new ArtifactKey(REMAPPED, SIDE_SERVER, DIST_JAR, HINT_UNPICKED);
	public static final ArtifactKey UNPICKED_MERGED_JAR = new ArtifactKey(REMAPPED, SIDE_MERGED, DIST_JAR, HINT_UNPICKED);

	// TODO Move decompiled artifacts to version directory?
	public static final ArtifactKey DECOMPILED_CLIENT_JAR = new ArtifactKey(DECOMPILED, SIDE_CLIENT, DIST_JAR);
	public static final ArtifactKey DECOMPILED_SERVER_JAR = new ArtifactKey(DECOMPILED, SIDE_SERVER, DIST_JAR);
	public static final ArtifactKey DECOMPILED_MERGED_JAR = new ArtifactKey(DECOMPILED, SIDE_MERGED, DIST_JAR);

	// For Local Variable Table Patching Step
	public static final ArtifactKey LVT_PATCHED_CLIENT_JAR = new ArtifactKey(PATCHED, SIDE_CLIENT, DIST_JAR, HINT_LVT);
	public static final ArtifactKey LVT_PATCHED_SERVER_JAR = new ArtifactKey(PATCHED, SIDE_SERVER, DIST_JAR, HINT_LVT);
	public static final ArtifactKey LVT_PATCHED_MERGED_JAR = new ArtifactKey(PATCHED, SIDE_MERGED, DIST_JAR, HINT_LVT);

	// For Exception Patching Step
	public static final ArtifactKey EXCEPTIONS_PATCHED_CLIENT_JAR = new ArtifactKey(PATCHED, SIDE_CLIENT, DIST_JAR, HINT_EXCEPTIONS);
	public static final ArtifactKey EXCEPTIONS_PATCHED_SERVER_JAR = new ArtifactKey(PATCHED, SIDE_SERVER, DIST_JAR, HINT_EXCEPTIONS);
	public static final ArtifactKey EXCEPTIONS_PATCHED_MERGED_JAR = new ArtifactKey(PATCHED, SIDE_MERGED, DIST_JAR, HINT_EXCEPTIONS);

	// For Signature Patching Step
	public static final ArtifactKey SIGNATURES_PATCHED_CLIENT_JAR = new ArtifactKey(PATCHED, SIDE_CLIENT, DIST_JAR, HINT_SIGNATURES);
	public static final ArtifactKey SIGNATURES_PATCHED_SERVER_JAR = new ArtifactKey(PATCHED, SIDE_SERVER, DIST_JAR, HINT_SIGNATURES);
	public static final ArtifactKey SIGNATURES_PATCHED_MERGED_JAR = new ArtifactKey(PATCHED, SIDE_MERGED, DIST_JAR, HINT_SIGNATURES);

	// For Nesting Step
	public static final ArtifactKey NESTED_CLIENT_JAR = new ArtifactKey(REMAPPED, SIDE_CLIENT, DIST_JAR, HINT_NESTED);
	public static final ArtifactKey NESTED_SERVER_JAR = new ArtifactKey(REMAPPED, SIDE_SERVER, DIST_JAR, HINT_NESTED);
	public static final ArtifactKey NESTED_MERGED_JAR = new ArtifactKey(REMAPPED, SIDE_MERGED, DIST_JAR, HINT_NESTED);

	// For Preening Step
	public static final ArtifactKey PREENED_CLIENT_JAR = new ArtifactKey(REMAPPED, SIDE_CLIENT, DIST_JAR, HINT_PREENED);
	public static final ArtifactKey PREENED_SERVER_JAR = new ArtifactKey(REMAPPED, SIDE_SERVER, DIST_JAR, HINT_PREENED);
	public static final ArtifactKey PREENED_MERGED_JAR = new ArtifactKey(REMAPPED, SIDE_MERGED, DIST_JAR, HINT_PREENED);


	public static final LazyValue<PipelineFilesystemStorage<OrderedVersion>> DEFAULT = LazyValue.of(() -> new PipelineFilesystemStorage<OrderedVersion>(
		GitCraftPaths.FILESYSTEM_ROOT,
		Set.of(
			REMAPPED_CLIENT_JAR, REMAPPED_SERVER_JAR, REMAPPED_MERGED_JAR,
			UNPICKED_CLIENT_JAR, UNPICKED_SERVER_JAR, UNPICKED_MERGED_JAR,
			DECOMPILED_CLIENT_JAR, DECOMPILED_SERVER_JAR, DECOMPILED_MERGED_JAR
		),
		Map.of(
			ARTIFACTS, rootPathVersioned(PipelineFilesystemRoot::getMcVersionStore),
			ARTIFACTS_CLIENT_JAR, createFromKey(ARTIFACTS, "client.jar"),
			ARTIFACTS_SERVER_JAR, createFromKey(ARTIFACTS, "server.jar"),
			ARTIFACTS_SERVER_EXE, createFromKey(ARTIFACTS, "server.exe"),
			ARTIFACTS_SERVER_ZIP, createFromKey(ARTIFACTS, "server.zip"),
			ARTIFACTS_MERGED_JAR, createFromKey(ARTIFACTS, "merged.jar"),
			ARTIFACTS_VANILLA_WORLDGEN_DATAPACK_ZIP, createFromKey(ARTIFACTS, "vanilla_worldgen.zip")
		),
		Map.of(
			LIBRARIES, rootPathConst(PipelineFilesystemRoot::getLibraryStore),
			ASSETS_INDEX, rootPathConst(PipelineFilesystemRoot::getAssetsIndex),
			ASSETS_OBJECTS, rootPathConst(PipelineFilesystemRoot::getAssetsObjects),
			ASSETS_INDEX_JSON, createFromKey(ASSETS_INDEX, context -> context.targetVersion().assetsIndex().name()),
			UNPACKED_SERVER_JAR, createFromKey(ARTIFACTS, "server-unpacked.jar"),
			UNBUNDLED_SERVER_JAR, createFromKey(ARTIFACTS, "server-unbundled.jar")
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
			REMAPPED_MERGED_JAR, createFromKey(REMAPPED, "merged-remapped.jar")
		),
		Map.of(
			UNPICKED_CLIENT_JAR, createFromKey(REMAPPED, "client-unpicked.jar"),
			UNPICKED_SERVER_JAR, createFromKey(REMAPPED, "server-unpicked.jar"),
			UNPICKED_MERGED_JAR, createFromKey(REMAPPED, "merged-unpicked.jar"),
			DECOMPILED, rootPathVersioned(PipelineFilesystemRoot::getDecompiled),
			DECOMPILED_CLIENT_JAR, createFromKey(DECOMPILED, "client.jar"),
			DECOMPILED_SERVER_JAR, createFromKey(DECOMPILED, "server.jar"),
			DECOMPILED_MERGED_JAR, createFromKey(DECOMPILED, "merged.jar")
		),
		Map.of(
			PATCHED, rootPathVersioned(PipelineFilesystemRoot::getPatchedStore),
			LVT_PATCHED_CLIENT_JAR, createFromKey(PATCHED, "client-lvt.jar"),
			LVT_PATCHED_SERVER_JAR, createFromKey(PATCHED, "server-lvt.jar"),
			LVT_PATCHED_MERGED_JAR, createFromKey(PATCHED, "merged-lvt.jar"),
			EXCEPTIONS_PATCHED_CLIENT_JAR, createFromKey(PATCHED, "client-exc.jar"),
			EXCEPTIONS_PATCHED_SERVER_JAR, createFromKey(PATCHED, "server-exc.jar"),
			EXCEPTIONS_PATCHED_MERGED_JAR, createFromKey(PATCHED, "merged-exc.jar"),
			SIGNATURES_PATCHED_CLIENT_JAR, createFromKey(PATCHED, "client-sig.jar"),
			SIGNATURES_PATCHED_SERVER_JAR, createFromKey(PATCHED, "server-sig.jar"),
			SIGNATURES_PATCHED_MERGED_JAR, createFromKey(PATCHED, "merged-sig.jar")
		),
		Map.of(
			NESTED_CLIENT_JAR, createFromKey(REMAPPED, "client-nested.jar"),
			NESTED_SERVER_JAR, createFromKey(REMAPPED, "server-nested.jar"),
			NESTED_MERGED_JAR, createFromKey(REMAPPED, "merged-nested.jar"),
			PREENED_CLIENT_JAR, createFromKey(REMAPPED, "client-preened.jar"),
			PREENED_SERVER_JAR, createFromKey(REMAPPED, "server-preened.jar"),
			PREENED_MERGED_JAR, createFromKey(REMAPPED, "merged-preened.jar")
		)));
}
