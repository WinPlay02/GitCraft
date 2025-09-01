package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.pipeline.key.ArtifactKey;
import com.github.winplay02.gitcraft.pipeline.key.DirectoryKey;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.LazyValue;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.pipeline.StepWorker.Config.FlavourMatcher;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public record PipelineFilesystemStorage<T extends AbstractVersion<T>>(PipelineFilesystemRoot rootFilesystem,
																   Set<StorageKey> resettableKeys,
																   Map<StorageKey, PathDeriver<T>> paths) {
	@FunctionalInterface
	public interface PathDeriver<T extends AbstractVersion<T>>  {
		Path derive(PipelineFilesystemStorage<T> storage, StepWorker.Context<T> context, StepWorker.Config config);
	}

	@SafeVarargs
	public PipelineFilesystemStorage(PipelineFilesystemRoot rootFilesystem, Set<StorageKey> resettableKeys, Map<StorageKey, PathDeriver<T>>... paths) {
		this(rootFilesystem, resettableKeys, MiscHelper.mergeMaps(new HashMap<>(), paths));
	}

	public Path getPath(StorageKey key, StepWorker.Context<T> context, StepWorker.Config config) {
		if (key == null || !this.paths.containsKey(key)) {
			return null;
		}
		return this.paths.get(key).derive(this, context, config);
	}

	private static <T extends AbstractVersion<T>> PathDeriver<T> rootPathConst(Function<PipelineFilesystemRoot, Path> rootPathConstFunction) {
		return (root, _context, _config) -> rootPathConstFunction.apply(root.rootFilesystem());
	}

	private static <T extends AbstractVersion<T>> PathDeriver<T> rootPathConstSubDir(Function<PipelineFilesystemRoot, Path> rootPathConstFunction, String subDir1, String... subDirs) {
		return (root, _context, _config) -> rootPathConstFunction.apply(root.rootFilesystem()).resolve(subDir1, subDirs);
	}

	private static <T extends AbstractVersion<T>> PathDeriver<T> rootPathVersioned(Function<PipelineFilesystemRoot, Path> rootPathConstFunction) {
		return (root, context, _config) -> rootPathConstFunction.apply(root.rootFilesystem()).resolve(context.targetVersion().pathName());
	}

	private Path resolvePath(StorageKey key, StepWorker.Context<T> context, StepWorker.Config config, String toResolveFirst, String... toResolve) {
		return this.paths.get(key).derive(this, context, config).resolve(toResolveFirst, toResolve);
	}

	private static <T extends AbstractVersion<T>> PathDeriver<T> createFromKey(StorageKey key, String toResolveFirst, String... toResolve) {
		return (root, context, config) -> root.resolvePath(key, context, config, toResolveFirst, toResolve);
	}

	private static <T extends AbstractVersion<T>> PathDeriver<T> createFromKey(StorageKey key, Function<StepWorker.Context<T>, String> toResolve) {
		return (root, context, config) -> root.resolvePath(key, context, config, toResolve.apply(context));
	}

	private static <T extends AbstractVersion<T>> PathDeriver<T> createFromKey(StorageKey key, BiFunction<StepWorker.Context<T>, StepWorker.Config, String> toResolve) {
		return (root, context, config) -> root.resolvePath(key, context, config, toResolve.apply(context, config));
	}

	private static <T extends AbstractVersion<T>> PathDeriver<T> createFromKeyWithConfig(StorageKey key, String pattern, MinecraftJar dist, FlavourMatcher... flavours) {
		return (root, context, config) -> root.resolvePath(key, context, config, String.format(pattern, config.createArtifactComponentString(dist, flavours)));
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

	// Launching Step
	public static final DirectoryKey LAUNCH_VERSIONS = new DirectoryKey("launch/versions");
	public static final DirectoryKey LAUNCH_GAME = new DirectoryKey("launch/game");
	public static final DirectoryKey LAUNCH_ASSETS = new DirectoryKey("launch/assets");
	public static final DirectoryKey LAUNCH_ASSETS_OBJECTS = new DirectoryKey("launch/assets/objects");
	public static final DirectoryKey LAUNCH_ASSETS_INDEXES = new DirectoryKey("launch/assets/indexes");
	public static final DirectoryKey LAUNCH_ASSETS_VIRTUALFS = new DirectoryKey("launch/assets/virtual");
	public static final DirectoryKey LAUNCH_NATIVES = new DirectoryKey("launch/natives");

	public static final ArtifactKey LAUNCHABLE_CLIENT_JAR = new ArtifactKey(LAUNCH_VERSIONS, SIDE_CLIENT, DIST_JAR);

	public static final LazyValue<PipelineFilesystemStorage<OrderedVersion>> DEFAULT = LazyValue.of(() -> new PipelineFilesystemStorage<OrderedVersion>(
		GitCraftPaths.FILESYSTEM_ROOT,
		Set.of(
			REMAPPED_CLIENT_JAR, REMAPPED_SERVER_JAR, REMAPPED_MERGED_JAR,
			UNPICKED_CLIENT_JAR, UNPICKED_SERVER_JAR, UNPICKED_MERGED_JAR,
			DECOMPILED_CLIENT_JAR, DECOMPILED_SERVER_JAR, DECOMPILED_MERGED_JAR
		),
		Map.of(
			ARTIFACTS, rootPathVersioned(PipelineFilesystemRoot::getMcVersionStore),
			ARTIFACTS_CLIENT_JAR, createFromKeyWithConfig(ARTIFACTS, "client-%s.jar", MinecraftJar.CLIENT),
			ARTIFACTS_SERVER_JAR, createFromKeyWithConfig(ARTIFACTS, "server-%s.jar", MinecraftJar.SERVER),
			ARTIFACTS_SERVER_EXE, createFromKeyWithConfig(ARTIFACTS, "server-%s.exe", MinecraftJar.SERVER),
			ARTIFACTS_SERVER_ZIP, createFromKeyWithConfig(ARTIFACTS, "server-%s.zip", MinecraftJar.SERVER),
			ARTIFACTS_MERGED_JAR, createFromKeyWithConfig(ARTIFACTS, "merged-%s.jar", MinecraftJar.MERGED),
			ARTIFACTS_VANILLA_WORLDGEN_DATAPACK_ZIP, createFromKey(ARTIFACTS, "vanilla_worldgen.zip")
		),
		Map.of(
			LIBRARIES, rootPathConst(PipelineFilesystemRoot::getLibraryStore),
			ASSETS_INDEX, rootPathConst(PipelineFilesystemRoot::getAssetsIndex),
			ASSETS_OBJECTS, rootPathConst(PipelineFilesystemRoot::getAssetsObjects),
			ASSETS_INDEX_JSON, createFromKey(ASSETS_INDEX, (context, config) -> context.targetVersion().assetsIndex().name()),
			UNPACKED_SERVER_JAR, createFromKeyWithConfig(ARTIFACTS, "server-unpacked-%s.jar", MinecraftJar.SERVER),
			UNBUNDLED_SERVER_JAR, createFromKeyWithConfig(ARTIFACTS, "server-unbundled-%s.jar", MinecraftJar.SERVER)
		),
		Map.of(
			ARTIFACTS_DATAGEN, createFromKey(ARTIFACTS, "datagenerator"),
			TEMP_DATAGEN_NBT_SOURCE_DIRECTORY, createFromKey(ARTIFACTS_DATAGEN, "input"),
			TEMP_DATAGEN_NBT_SOURCE_DATA_DIRECTORY, createFromKey(TEMP_DATAGEN_NBT_SOURCE_DIRECTORY, "data"),
			TEMP_DATAGEN_SNBT_DESTINATION_DIRECTORY, createFromKey(ARTIFACTS_DATAGEN, "output"),
			TEMP_DATAGEN_SNBT_DESTINATION_DATA_DIRECTORY, createFromKey(TEMP_DATAGEN_SNBT_DESTINATION_DIRECTORY, "data"),
			TEMP_DATAGEN_REPORTS_DIRECTORY, createFromKey(ARTIFACTS_DATAGEN, "generated", "reports"),
			DATAGEN_SNBT_ARCHIVE, createFromKeyWithConfig(ARTIFACTS, "datagen-snbt-%s.jar", MinecraftJar.SERVER),
			DATAGEN_REPORTS_ARCHIVE, createFromKeyWithConfig(ARTIFACTS, "datagen-reports-%s.jar", MinecraftJar.SERVER)
		),
		Map.of(
			REMAPPED, rootPathVersioned(PipelineFilesystemRoot::getRemapped),
			REMAPPED_CLIENT_JAR, createFromKeyWithConfig(REMAPPED, "client-remapped-%s.jar", MinecraftJar.CLIENT, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS, FlavourMatcher.SIGNATURES, FlavourMatcher.MAPPING),
			REMAPPED_SERVER_JAR, createFromKeyWithConfig(REMAPPED, "server-remapped-%s.jar", MinecraftJar.SERVER, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS, FlavourMatcher.SIGNATURES, FlavourMatcher.MAPPING),
			REMAPPED_MERGED_JAR, createFromKeyWithConfig(REMAPPED, "merged-remapped-%s.jar", MinecraftJar.MERGED, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS, FlavourMatcher.SIGNATURES, FlavourMatcher.MAPPING)
		),
		Map.of(
			UNPICKED_CLIENT_JAR, createFromKeyWithConfig(REMAPPED, "client-unpicked-%s.jar", MinecraftJar.CLIENT, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS, FlavourMatcher.SIGNATURES, FlavourMatcher.MAPPING, FlavourMatcher.UNPICK),
			UNPICKED_SERVER_JAR, createFromKeyWithConfig(REMAPPED, "server-unpicked-%s.jar", MinecraftJar.SERVER, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS, FlavourMatcher.SIGNATURES, FlavourMatcher.MAPPING, FlavourMatcher.UNPICK),
			UNPICKED_MERGED_JAR, createFromKeyWithConfig(REMAPPED, "merged-unpicked-%s.jar", MinecraftJar.MERGED, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS, FlavourMatcher.SIGNATURES, FlavourMatcher.MAPPING, FlavourMatcher.UNPICK),
			DECOMPILED, rootPathVersioned(PipelineFilesystemRoot::getDecompiled),
			DECOMPILED_CLIENT_JAR, createFromKeyWithConfig(DECOMPILED, "client-%s.jar", MinecraftJar.CLIENT, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS, FlavourMatcher.SIGNATURES, FlavourMatcher.MAPPING, FlavourMatcher.UNPICK, FlavourMatcher.NESTS, FlavourMatcher.PREEN),
			DECOMPILED_SERVER_JAR, createFromKeyWithConfig(DECOMPILED, "server-%s.jar", MinecraftJar.SERVER, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS, FlavourMatcher.SIGNATURES, FlavourMatcher.MAPPING, FlavourMatcher.UNPICK, FlavourMatcher.NESTS, FlavourMatcher.PREEN),
			DECOMPILED_MERGED_JAR, createFromKeyWithConfig(DECOMPILED, "merged-%s.jar", MinecraftJar.MERGED, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS, FlavourMatcher.SIGNATURES, FlavourMatcher.MAPPING, FlavourMatcher.UNPICK, FlavourMatcher.NESTS, FlavourMatcher.PREEN)
		),
		Map.of(
			PATCHED, rootPathVersioned(PipelineFilesystemRoot::getPatchedStore),
			LVT_PATCHED_CLIENT_JAR, createFromKeyWithConfig(PATCHED, "client-lvt-%s.jar", MinecraftJar.CLIENT),
			LVT_PATCHED_SERVER_JAR, createFromKeyWithConfig(PATCHED, "server-lvt-%s.jar", MinecraftJar.SERVER),
			LVT_PATCHED_MERGED_JAR, createFromKeyWithConfig(PATCHED, "merged-lvt-%s.jar", MinecraftJar.MERGED),
			EXCEPTIONS_PATCHED_CLIENT_JAR, createFromKeyWithConfig(PATCHED, "client-exc-%s.jar", MinecraftJar.CLIENT, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS),
			EXCEPTIONS_PATCHED_SERVER_JAR, createFromKeyWithConfig(PATCHED, "server-exc-%s.jar", MinecraftJar.SERVER, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS),
			EXCEPTIONS_PATCHED_MERGED_JAR, createFromKeyWithConfig(PATCHED, "merged-exc-%s.jar", MinecraftJar.MERGED, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS),
			SIGNATURES_PATCHED_CLIENT_JAR, createFromKeyWithConfig(PATCHED, "client-sig-%s.jar", MinecraftJar.CLIENT, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS, FlavourMatcher.SIGNATURES),
			SIGNATURES_PATCHED_SERVER_JAR, createFromKeyWithConfig(PATCHED, "server-sig-%s.jar", MinecraftJar.SERVER, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS, FlavourMatcher.SIGNATURES),
			SIGNATURES_PATCHED_MERGED_JAR, createFromKeyWithConfig(PATCHED, "merged-sig-%s.jar", MinecraftJar.MERGED, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS, FlavourMatcher.SIGNATURES)
		),
		Map.of(
			NESTED_CLIENT_JAR, createFromKeyWithConfig(REMAPPED, "client-nested-%s.jar", MinecraftJar.CLIENT, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS, FlavourMatcher.SIGNATURES, FlavourMatcher.MAPPING, FlavourMatcher.UNPICK, FlavourMatcher.NESTS),
			NESTED_SERVER_JAR, createFromKeyWithConfig(REMAPPED, "server-nested-%s.jar", MinecraftJar.SERVER, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS, FlavourMatcher.SIGNATURES, FlavourMatcher.MAPPING, FlavourMatcher.UNPICK, FlavourMatcher.NESTS),
			NESTED_MERGED_JAR, createFromKeyWithConfig(REMAPPED, "merged-nested-%s.jar", MinecraftJar.MERGED, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS, FlavourMatcher.SIGNATURES, FlavourMatcher.MAPPING, FlavourMatcher.UNPICK, FlavourMatcher.NESTS),
			PREENED_CLIENT_JAR, createFromKeyWithConfig(REMAPPED, "client-preened-%s.jar", MinecraftJar.CLIENT, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS, FlavourMatcher.SIGNATURES, FlavourMatcher.MAPPING, FlavourMatcher.UNPICK, FlavourMatcher.NESTS),
			PREENED_SERVER_JAR, createFromKeyWithConfig(REMAPPED, "server-preened-%s.jar", MinecraftJar.SERVER, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS, FlavourMatcher.SIGNATURES, FlavourMatcher.MAPPING, FlavourMatcher.UNPICK, FlavourMatcher.NESTS),
			PREENED_MERGED_JAR, createFromKeyWithConfig(REMAPPED, "merged-preened-%s.jar", MinecraftJar.MERGED, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS, FlavourMatcher.SIGNATURES, FlavourMatcher.MAPPING, FlavourMatcher.UNPICK, FlavourMatcher.NESTS)
		),
		Map.of(
			LAUNCH_VERSIONS, rootPathVersioned(pipelineFsRoot -> pipelineFsRoot.getRuntimeDirectory().resolve("launch_versions")),
			LAUNCH_GAME, rootPathConstSubDir(PipelineFilesystemRoot::getRuntimeDirectory, "game"),
			LAUNCH_ASSETS, rootPathConstSubDir(PipelineFilesystemRoot::getRuntimeDirectory, "assets"),
			LAUNCH_ASSETS_OBJECTS, rootPathConstSubDir(PipelineFilesystemRoot::getRuntimeDirectory, "assets", "objects"),
			LAUNCH_ASSETS_INDEXES, rootPathConstSubDir(PipelineFilesystemRoot::getRuntimeDirectory, "assets", "indexes"),
			LAUNCH_ASSETS_VIRTUALFS, rootPathConstSubDir(PipelineFilesystemRoot::getRuntimeDirectory, "assets", "virtual"),
			LAUNCH_NATIVES, rootPathConstSubDir(PipelineFilesystemRoot::getRuntimeDirectory, "natives")
		),
		Map.of(
			LAUNCHABLE_CLIENT_JAR, createFromKeyWithConfig(LAUNCH_VERSIONS, "client-%s.jar", MinecraftJar.CLIENT, FlavourMatcher.LVT, FlavourMatcher.EXCEPTIONS, FlavourMatcher.SIGNATURES, FlavourMatcher.MAPPING, FlavourMatcher.UNPICK, FlavourMatcher.NESTS, FlavourMatcher.PREEN)
		)
	)
	);
}
