package com.github.winplay02.gitcraft;

import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.graph.AbstractVersionGraph;
import com.github.winplay02.gitcraft.pipeline.IPipeline;
import com.github.winplay02.gitcraft.pipeline.IStep;
import com.github.winplay02.gitcraft.pipeline.IStepConfig;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.IStepWorker;
import com.github.winplay02.gitcraft.pipeline.ParallelismPolicy;
import com.github.winplay02.gitcraft.pipeline.PipelineDescription;
import com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.StepDependencies;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.key.KeyInformation;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.Tuple2;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@ExtendWith({GitCraftTestingFs.class})
@TestMethodOrder(MethodOrderer.MethodName.class)
public class PipelineTest {

	static class Timing {
		private final Map<TestingVersion, Long> timesBegin = new ConcurrentHashMap<>();
		private final Map<TestingVersion, Long> timesEnd = new ConcurrentHashMap<>();

		public Timing() {}

		public void recordBegin(TestingVersion v) {
			if (timesBegin.containsKey(v)) {
				MiscHelper.panic("Timing list not empty");
			}
			timesBegin.put(v, System.nanoTime());
		}

		public void recordEnd(TestingVersion v) {
			if (timesEnd.containsKey(v)) {
				MiscHelper.panic("Timing list not begun");
			}
			timesEnd.put(v, System.nanoTime());
		}

		public boolean isOverlapping(TestingVersion v1, TestingVersion v2) {
			return (timesBegin.get(v1) < timesEnd.get(v2) && timesBegin.get(v1) > timesBegin.get(v2)) || (timesEnd.get(v1) > timesBegin.get(v2) && timesBegin.get(v1) < timesBegin.get(v2));
		}
	}

	record TestingVersion(int num) implements AbstractVersion<TestingVersion> {

		@Override
		public String semanticVersion() {
			return String.valueOf(num);
		}

		@Override
		public String friendlyVersion() {
			return String.valueOf(num);
		}

		@Override
		public String toCommitMessage() {
			return String.valueOf(num);
		}

		@Override
		public int compareTo(TestingVersion o) {
			return Objects.requireNonNull(o).num - this.num;
		}
	}

	static class TestingVersionGraph extends AbstractVersionGraph<TestingVersion> {
		public TestingVersionGraph(List<Tuple2<TestingVersion, TestingVersion>> edgesFw) {
			super();
			this.repoTags = new HashSet<>();
			for (Tuple2<TestingVersion, TestingVersion> edge : edgesFw) {
				this.edgesFw.computeIfAbsent(edge.v1(), $ -> new TreeSet<>()).add(edge.v2());
				this.edgesFw.computeIfAbsent(edge.v2(), $ -> new TreeSet<>());

				this.edgesBack.computeIfAbsent(edge.v2(), $ -> new TreeSet<>()).add(edge.v1());
				this.edgesBack.computeIfAbsent(edge.v1(), $ -> new TreeSet<>());
			}
		}
	}

	record EmptyConfig(Timing timing1, Timing timing2, Timing timing3) implements IStepConfig {
		@Override
		public String createArtifactComponentString(KeyInformation<?> dist, KeyInformation<?>... matchingFlavours) {
			return "";
		}
	}

	record StepWorker(Timing timing, EmptyConfig config, int stepId) implements IStepWorker<TestingVersion, StepInput.Empty, IStepContext.SimpleStepContext<TestingVersion>, EmptyConfig> {
		@Override
		public StepOutput<TestingVersion, IStepContext.SimpleStepContext<TestingVersion>, EmptyConfig> run(IPipeline<TestingVersion, IStepContext.SimpleStepContext<TestingVersion>, EmptyConfig> pipeline, IStepContext.SimpleStepContext<TestingVersion> context, StepInput.Empty input, StepResults<TestingVersion, IStepContext.SimpleStepContext<TestingVersion>, EmptyConfig> results) throws Exception {
			this.timing.recordBegin(context.targetVersion());
			Thread.sleep(500);
			this.timing.recordEnd(context.targetVersion());
			return StepOutput.ofEmptyResultSet(StepStatus.SUCCESS);
		}
	}

	enum TestingStepsParallel implements IStep<TestingVersion, StepInput.Empty, IStepContext.SimpleStepContext<TestingVersion>, EmptyConfig> {
		STEP1("Step1", cfg -> new StepWorker(cfg.timing1(), cfg, 1)),
		STEP2("Step2", cfg -> new StepWorker(cfg.timing2(), cfg, 2)),
		STEP3("Step3", cfg -> new StepWorker(cfg.timing3(), cfg, 3));

		private final String name;
		private final ParallelismPolicy parallelismPolicy;
		private final Function<EmptyConfig, StepWorker> workerFactory;

		TestingStepsParallel(String name, Function<EmptyConfig, StepWorker> workerFactory) {
			this.name = name;
			this.parallelismPolicy = ParallelismPolicy.SAFELY_FULLY_PARALLEL;
			this.workerFactory = workerFactory;
		}

		public String getName() {
			return name;
		}

		public ParallelismPolicy getParallelismPolicy() {
			return this.parallelismPolicy;
		}

		public IStepWorker<TestingVersion, StepInput.Empty, IStepContext.SimpleStepContext<TestingVersion>, EmptyConfig> createWorker(EmptyConfig config) {
			return workerFactory.apply(config);
		}
	}

	enum TestingStepsSequential implements IStep<TestingVersion, StepInput.Empty, IStepContext.SimpleStepContext<TestingVersion>, EmptyConfig> {
		STEP1("Step1", ParallelismPolicy.SAFELY_FULLY_PARALLEL, cfg -> new StepWorker(cfg.timing1(), cfg, 1)),
		STEP2("Step2", ParallelismPolicy.SAFELY_FULLY_PARALLEL, cfg -> new StepWorker(cfg.timing2(), cfg, 2)),
		STEP3("Step3", ParallelismPolicy.UNSAFE_RESTRICTED_TO_SEQUENTIAL, cfg -> new StepWorker(cfg.timing3(), cfg, 3));

		private final String name;
		private final ParallelismPolicy parallelismPolicy;
		private final Function<EmptyConfig, StepWorker> workerFactory;

		TestingStepsSequential(String name, ParallelismPolicy policy, Function<EmptyConfig, StepWorker> workerFactory) {
			this.name = name;
			this.parallelismPolicy = policy;
			this.workerFactory = workerFactory;
		}

		public String getName() {
			return name;
		}

		public ParallelismPolicy getParallelismPolicy() {
			return this.parallelismPolicy;
		}

		public IStepWorker<TestingVersion, StepInput.Empty, IStepContext.SimpleStepContext<TestingVersion>, EmptyConfig> createWorker(EmptyConfig config) {
			return workerFactory.apply(config);
		}
	}

	static Timing[] PARALLEL_TIMING = new Timing[3];
	static {
		PARALLEL_TIMING[0] = new Timing();
		PARALLEL_TIMING[1] = new Timing();
		PARALLEL_TIMING[2] = new Timing();
	}

	static PipelineDescription<TestingVersion, IStepContext.SimpleStepContext<TestingVersion>, EmptyConfig> PARALLEL_DESCRIPTION = new PipelineDescription<TestingVersion, IStepContext.SimpleStepContext<TestingVersion>, EmptyConfig>(
		"parallel-pipeline",
		List.of(TestingStepsParallel.STEP1, TestingStepsParallel.STEP2, TestingStepsParallel.STEP3),
		Map.of(
			TestingStepsParallel.STEP1, ($, $$) -> new StepInput.Empty(),
			TestingStepsParallel.STEP2, ($, $$) -> new StepInput.Empty(),
			TestingStepsParallel.STEP3, ($, $$) -> new StepInput.Empty()
		),
		Map.of(
			TestingStepsParallel.STEP2, StepDependencies.ofHardIntraVersionOnly(TestingStepsParallel.STEP1),
			TestingStepsParallel.STEP3, StepDependencies.merge(
				StepDependencies.ofHardIntraVersionOnly(TestingStepsParallel.STEP1),
				StepDependencies.ofInterVersion(TestingStepsParallel.STEP3)
			)
		),
		(version, repository, versionGraph, executorService) -> new IStepContext.SimpleStepContext<TestingVersion>(repository, versionGraph, version, executorService),
		testingVersion -> new EmptyConfig(PARALLEL_TIMING[0], PARALLEL_TIMING[1], PARALLEL_TIMING[2])
	);

	static Timing[] SEQ_TIMING = new Timing[3];
	static {
		SEQ_TIMING[0] = new Timing();
		SEQ_TIMING[1] = new Timing();
		SEQ_TIMING[2] = new Timing();
	}

	static PipelineDescription<TestingVersion, IStepContext.SimpleStepContext<TestingVersion>, EmptyConfig> SEQ_DESCRIPTION = new PipelineDescription<TestingVersion, IStepContext.SimpleStepContext<TestingVersion>, EmptyConfig>(
		"sequential-pipeline",
		List.of(TestingStepsSequential.STEP1, TestingStepsSequential.STEP2, TestingStepsSequential.STEP3),
		Map.of(
			TestingStepsSequential.STEP1, ($, $$) -> new StepInput.Empty(),
			TestingStepsSequential.STEP2, ($, $$) -> new StepInput.Empty(),
			TestingStepsSequential.STEP3, ($, $$) -> new StepInput.Empty()
		),
		Map.of(
			TestingStepsSequential.STEP2, StepDependencies.ofHardIntraVersionOnly(TestingStepsSequential.STEP1),
			TestingStepsSequential.STEP3, StepDependencies.merge(
				StepDependencies.ofHardIntraVersionOnly(TestingStepsSequential.STEP1),
				StepDependencies.ofInterVersion(TestingStepsSequential.STEP3)
			)
		),
		(version, repository, versionGraph, executorService) -> new IStepContext.SimpleStepContext<TestingVersion>(repository, versionGraph, version, executorService),
		testingVersion -> new EmptyConfig(SEQ_TIMING[0], SEQ_TIMING[1], SEQ_TIMING[2])
	);

	static TestingVersionGraph createVersionGraph() {
		return new TestingVersionGraph(
			List.of(
				Tuple2.tuple(new TestingVersion(1), new TestingVersion(2)),
				Tuple2.tuple(new TestingVersion(2), new TestingVersion(3)),
				Tuple2.tuple(new TestingVersion(2), new TestingVersion(4))
			)
		);
	}

	@Test
	public void pipelineExecutionParallel() throws Exception {
		TestingVersionGraph graph = createVersionGraph();
		IPipeline.run(PARALLEL_DESCRIPTION, new PipelineFilesystemStorage<>(null, null), null, graph);
		// Step 1
		Assertions.assertTrue(PARALLEL_TIMING[0].isOverlapping(new TestingVersion(3), new TestingVersion(4)));
		Assertions.assertTrue(PARALLEL_TIMING[0].isOverlapping(new TestingVersion(2), new TestingVersion(3)));
		Assertions.assertTrue(PARALLEL_TIMING[0].isOverlapping(new TestingVersion(1), new TestingVersion(2)));
		// Step 2
		Assertions.assertTrue(PARALLEL_TIMING[1].isOverlapping(new TestingVersion(3), new TestingVersion(4)));
		Assertions.assertTrue(PARALLEL_TIMING[1].isOverlapping(new TestingVersion(2), new TestingVersion(3)));
		Assertions.assertTrue(PARALLEL_TIMING[1].isOverlapping(new TestingVersion(1), new TestingVersion(2)));
		// Step 3
		Assertions.assertTrue(PARALLEL_TIMING[2].isOverlapping(new TestingVersion(3), new TestingVersion(4)));
		Assertions.assertFalse(PARALLEL_TIMING[2].isOverlapping(new TestingVersion(2), new TestingVersion(3)));
		Assertions.assertFalse(PARALLEL_TIMING[2].isOverlapping(new TestingVersion(1), new TestingVersion(2)));
	}

	@Test
	public void pipelineExecutionSequential() throws Exception {
		TestingVersionGraph graph = createVersionGraph();
		IPipeline.run(SEQ_DESCRIPTION, new PipelineFilesystemStorage<>(null, null), null, graph);
		// Step 1
		Assertions.assertTrue(SEQ_TIMING[0].isOverlapping(new TestingVersion(3), new TestingVersion(4)));
		Assertions.assertTrue(SEQ_TIMING[0].isOverlapping(new TestingVersion(2), new TestingVersion(3)));
		Assertions.assertTrue(SEQ_TIMING[0].isOverlapping(new TestingVersion(1), new TestingVersion(2)));
		// Step 2
		Assertions.assertTrue(SEQ_TIMING[1].isOverlapping(new TestingVersion(3), new TestingVersion(4)));
		Assertions.assertTrue(SEQ_TIMING[1].isOverlapping(new TestingVersion(2), new TestingVersion(3)));
		Assertions.assertTrue(SEQ_TIMING[1].isOverlapping(new TestingVersion(1), new TestingVersion(2)));
		// Step 3
		Assertions.assertFalse(SEQ_TIMING[2].isOverlapping(new TestingVersion(3), new TestingVersion(4)));
		Assertions.assertFalse(SEQ_TIMING[2].isOverlapping(new TestingVersion(2), new TestingVersion(3)));
		Assertions.assertFalse(SEQ_TIMING[2].isOverlapping(new TestingVersion(1), new TestingVersion(2)));
	}
}
