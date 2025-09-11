package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.launcher.LaunchPrepareLaunchableFile;
import com.github.winplay02.gitcraft.launcher.LaunchStepHardlinkAssets;
import com.github.winplay02.gitcraft.launcher.LaunchStepLaunch;
import com.github.winplay02.gitcraft.pipeline.workers.ArtifactsFetcher;
import com.github.winplay02.gitcraft.pipeline.workers.ArtifactsUnpacker;
import com.github.winplay02.gitcraft.pipeline.workers.AssetsFetcher;
import com.github.winplay02.gitcraft.pipeline.workers.Committer;
import com.github.winplay02.gitcraft.pipeline.workers.DataGenerator;
import com.github.winplay02.gitcraft.pipeline.workers.Decompiler;
import com.github.winplay02.gitcraft.pipeline.workers.ExceptionsProvider;
import com.github.winplay02.gitcraft.pipeline.workers.JarsExceptor;
import com.github.winplay02.gitcraft.pipeline.workers.JarsMerger;
import com.github.winplay02.gitcraft.pipeline.workers.JarsNester;
import com.github.winplay02.gitcraft.pipeline.workers.JarsSignatureChanger;
import com.github.winplay02.gitcraft.pipeline.workers.LibrariesFetcher;
import com.github.winplay02.gitcraft.pipeline.workers.LvtPatcher;
import com.github.winplay02.gitcraft.pipeline.workers.MappingsProvider;
import com.github.winplay02.gitcraft.pipeline.workers.NestsProvider;
import com.github.winplay02.gitcraft.pipeline.workers.Preener;
import com.github.winplay02.gitcraft.pipeline.workers.Remapper;
import com.github.winplay02.gitcraft.pipeline.workers.RepoGarbageCollector;
import com.github.winplay02.gitcraft.pipeline.workers.Resetter;
import com.github.winplay02.gitcraft.pipeline.workers.SignaturesProvider;
import com.github.winplay02.gitcraft.pipeline.workers.UnpickProvider;
import com.github.winplay02.gitcraft.pipeline.workers.Unpicker;

import java.util.function.Function;

public enum Step {

	RESET("Reset", ExecutionScope.GRAPH, Resetter::new),
	FETCH_ARTIFACTS("Fetch Artifacts", ArtifactsFetcher::new),
	FETCH_LIBRARIES("Fetch Libraries", LibrariesFetcher::new),
	FETCH_ASSETS("Fetch Assets", AssetsFetcher::new),
	UNPACK_ARTIFACTS("Unpack Artifacts", ArtifactsUnpacker::new),
	MERGE_OBFUSCATED_JARS("Merge Obfuscated Jars", cfg -> new JarsMerger(true, cfg)),
	DATAGEN("Datagen", DataGenerator::new),
	PATCH_LOCAL_VARIABLE_TABLES("Patch Local Variable Tables", LvtPatcher::new),
	PROVIDE_EXCEPTIONS("Provide Exceptions", ExceptionsProvider::new),
	APPLY_EXCEPTIONS("Apply Exceptions", JarsExceptor::new),
	PROVIDE_SIGNATURES("Provide Signatures", SignaturesProvider::new),
	APPLY_SIGNATURES("Apply Signatures", JarsSignatureChanger::new),
	PROVIDE_MAPPINGS("Provide Mappings", MappingsProvider::new),
	PROVIDE_UNPICK("Provide Unpick Information", UnpickProvider::new),
	REMAP_JARS("Remap Jars", Remapper::new),
	MERGE_REMAPPED_JARS("Merge Remapped Jars", cfg -> new JarsMerger(false, cfg)),
	UNPICK_JARS("Unpick Jars", Unpicker::new),
	PROVIDE_NESTS("Provide Nests", NestsProvider::new),
	APPLY_NESTS("Apply Nests", JarsNester::new),
	PREEN_JARS("Preen Jars", Preener::new),
	DECOMPILE_JARS("Decompile Jars", ExecutionScope.GRAPH, Decompiler::new),
	COMMIT("Commit to repository", ExecutionScope.GRAPH, Committer::new),
	REPO_GARBAGE_COLLECTOR("GC repository", ExecutionScope.GRAPH, RepoGarbageCollector::new),
	LAUNCH_PREPARE_HARDLINK_ASSETS("Hardlink Assets to Launch Environment", LaunchStepHardlinkAssets::new),
	LAUNCH_PREPARE_CONSTRUCT_LAUNCHABLE_FILE("Construct a launchable file", LaunchPrepareLaunchableFile::new),
	LAUNCH_CLIENT("Launch Client", LaunchStepLaunch::new);

	private final String name;
	private final ExecutionScope scope;
	private final Function<StepWorker.Config, StepWorker<?, ?>> workerFactory;

	Step(String name, Function<StepWorker.Config, StepWorker<?, ?>> workerFactory) {
		this(name, ExecutionScope.VERSION, workerFactory);
	}

	Step(String name, ExecutionScope scope, Function<StepWorker.Config, StepWorker<?, ?>> workerFactory) {
		this.name = name;
		this.scope = scope;
		this.workerFactory = workerFactory;
	}

	public String getName() {
		return name;
	}

	public ExecutionScope getScope() {
		return scope;
	}

	public StepWorker<?, ?> createWorker(StepWorker.Config config) {
		return workerFactory.apply(config);
	}
}
