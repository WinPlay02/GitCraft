package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.pipeline.key.KeyInformation;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public record PipelineFilesystemStorage<T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig>(IPipelineFilesystemRoot rootFilesystem,
																														   Set<StorageKey> resettableKeys,
																														   Map<StorageKey, PathDeriver<T, C, D>> paths) {
	@FunctionalInterface
	public interface PathDeriver<T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig>  {
		Path derive(PipelineFilesystemStorage<T, C, D> storage, C context, D config);
	}

	@SafeVarargs
	public PipelineFilesystemStorage(IPipelineFilesystemRoot rootFilesystem, Set<StorageKey> resettableKeys, Map<StorageKey, PathDeriver<T, C, D>>... paths) {
		this(rootFilesystem, resettableKeys, MiscHelper.mergeMaps(new HashMap<>(), paths));
	}

	public Path getPath(StorageKey key, C context, D config) {
		if (key == null || !this.paths.containsKey(key)) {
			return null;
		}
		return this.paths.get(key).derive(this, context, config);
	}

	public static <T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig> PathDeriver<T, C, D> rootPathConst(Function<IPipelineFilesystemRoot, Path> rootPathConstFunction) {
		return (root, _context, _config) -> rootPathConstFunction.apply(root.rootFilesystem());
	}

	public static <T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig> PathDeriver<T, C, D> rootPathConstSubDir(Function<IPipelineFilesystemRoot, Path> rootPathConstFunction, String subDir1, String... subDirs) {
		return (root, _context, _config) -> rootPathConstFunction.apply(root.rootFilesystem()).resolve(subDir1, subDirs);
	}

	public static <T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig> PathDeriver<T, C, D> rootPathVersioned(Function<IPipelineFilesystemRoot, Path> rootPathConstFunction) {
		return (root, context, _config) -> rootPathConstFunction.apply(root.rootFilesystem()).resolve(context.targetVersion().pathName());
	}

	public Path resolvePath(StorageKey key, C context, D config, String toResolveFirst, String... toResolve) {
		return this.paths.get(key).derive(this, context, config).resolve(toResolveFirst, toResolve);
	}

	public static <T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig> PathDeriver<T, C, D> createFromKey(StorageKey key, String toResolveFirst, String... toResolve) {
		return (root, context, config) -> root.resolvePath(key, context, config, toResolveFirst, toResolve);
	}

	public static <T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig> PathDeriver<T, C, D> createFromKey(StorageKey key, Function<C, String> toResolve) {
		return (root, context, config) -> root.resolvePath(key, context, config, toResolve.apply(context));
	}

	public static <T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig> PathDeriver<T, C, D> createFromKey(StorageKey key, BiFunction<C, D, String> toResolve) {
		return (root, context, config) -> root.resolvePath(key, context, config, toResolve.apply(context, config));
	}

	public static <T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig> PathDeriver<T, C, D> createFromKeyWithConfig(StorageKey key, String pattern, KeyInformation<?> dist, KeyInformation<?>... flavours) {
		return (root, context, config) -> root.resolvePath(key, context, config, String.format(pattern, config.createArtifactComponentString(dist, flavours)));
	}
}
