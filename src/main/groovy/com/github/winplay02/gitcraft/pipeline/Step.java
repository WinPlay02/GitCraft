package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.pipeline.workers.ArtifactsFetcher;
import com.github.winplay02.gitcraft.pipeline.workers.ArtifactsUnpacker;
import com.github.winplay02.gitcraft.pipeline.workers.AssetsFetcher;
import com.github.winplay02.gitcraft.pipeline.workers.Committer;
import com.github.winplay02.gitcraft.pipeline.workers.DataGenerator;
import com.github.winplay02.gitcraft.pipeline.workers.Decompiler;
import com.github.winplay02.gitcraft.pipeline.workers.JarsMerger;
import com.github.winplay02.gitcraft.pipeline.workers.LibrariesFetcher;
import com.github.winplay02.gitcraft.pipeline.workers.MappingsProvider;
import com.github.winplay02.gitcraft.pipeline.workers.Remapper;
import com.github.winplay02.gitcraft.pipeline.workers.RepoGarbageCollector;
import com.github.winplay02.gitcraft.pipeline.workers.Resetter;
import com.github.winplay02.gitcraft.pipeline.workers.Unpicker;

import java.util.function.Function;

public enum Step {

	RESET("Reset", Resetter::new),
	FETCH_ARTIFACTS("Fetch Artifacts", ArtifactsFetcher::new),
	FETCH_LIBRARIES("Fetch Libraries", LibrariesFetcher::new),
	FETCH_ASSETS("Fetch Assets", AssetsFetcher::new),
	UNPACK_ARTIFACTS("Unpack Artifacts", ArtifactsUnpacker::new),
	MERGE_OBFUSCATED_JARS("Merge Obfuscated Jars", cfg -> new JarsMerger(true, cfg)),
	DATAGEN("Datagen", DataGenerator::new),
	PROVIDE_MAPPINGS("Provide Mappings", MappingsProvider::new),
	REMAP_JARS("Remap Jars", Remapper::new),
	MERGE_REMAPPED_JARS("Merge Remapped Jars", cfg -> new JarsMerger(false, cfg)),
	UNPICK_JARS("Unpick Jars", Unpicker::new),
	DECOMPILE_JARS("Decompile Jars", Decompiler::new),
	COMMIT("Commit to repository", Committer::new),
	REPO_GARBAGE_COLLECTOR("GC repository", RepoGarbageCollector::new);

	private final String name;
	private final Function<StepWorker.Config, StepWorker<?>> workerFactory;

	Step(String name, Function<StepWorker.Config, StepWorker<?>> workerFactory) {
		this.name = name;
		this.workerFactory = workerFactory;
	}

	public String getName() {
		return name;
	}

	public StepWorker<?> createWorker(StepWorker.Config config) {
		return workerFactory.apply(config);
	}
}
