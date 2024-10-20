package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record StepOutput(StepStatus status, StepResults results) {
	public static StepOutput ofSingle(StepStatus status, StorageKey key) {
		return new StepOutput(status, new StepResults(Set.of(key)));
	}

	public static StepOutput ofEmptyResultSet(StepStatus status) {
		return new StepOutput(status, new StepResults(Set.of()));
	}

	public static StepOutput merge(StepOutput... outputs) {
		return new StepOutput(
			StepStatus.merge(
				Arrays.stream(outputs).map(StepOutput::status).collect(Collectors.toList())),
			new StepResults(MiscHelper.mergeSets(
				new HashSet<>(), Arrays.stream(outputs).map(output -> output.results().result()).toList()
			))
		);
	}

	public static StepOutput merge(Collection<StepOutput> outputs) {
		return new StepOutput(
			StepStatus.merge(
				outputs.stream().map(StepOutput::status).collect(Collectors.toList())),
			new StepResults(MiscHelper.mergeSets(
				new HashSet<>(), outputs.stream().map(output -> output.results().result()).toList()
			))
		);
	}

	public static StepOutput merge(StepResults otherResults, StepOutput... outputs) {
		return new StepOutput(
			StepStatus.merge(
				Arrays.stream(outputs).map(StepOutput::status).collect(Collectors.toList())),
			new StepResults(MiscHelper.mergeSets(
				new HashSet<>(), Stream.concat(Stream.of(otherResults.result()), Arrays.stream(outputs).map(output -> output.results().result())).toList()
			))
		);
	}
}
