package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record StepOutput<T extends AbstractVersion<T>>(StepStatus status, StepResults<T> results) {
	public static <T extends AbstractVersion<T>> StepOutput<T> ofSingle(StepStatus status, StorageKey key) {
		return new StepOutput<>(status, new StepResults<T>(Set.of(key)));
	}

	public static <T extends AbstractVersion<T>> StepOutput<T> ofEmptyResultSet(StepStatus status) {
		return new StepOutput<>(status, new StepResults<T>(Set.of()));
	}

	@SafeVarargs
	public static <T extends AbstractVersion<T>> StepOutput<T> merge(StepOutput<T>... outputs) {
		return new StepOutput<>(
			StepStatus.merge(
				Arrays.stream(outputs).filter(Objects::nonNull).map(StepOutput::status).collect(Collectors.toList())),
			new StepResults<T>(MiscHelper.mergeSetsUnion(
				new HashSet<>(), Arrays.stream(outputs).filter(Objects::nonNull).map(output -> output.results().result()).toList()
			))
		);
	}

	public static <T extends AbstractVersion<T>> StepOutput<T> merge(Collection<StepOutput<T>> outputs) {
		return new StepOutput<>(
			StepStatus.merge(
				outputs.stream().filter(Objects::nonNull).map(StepOutput::status).collect(Collectors.toList())),
			new StepResults<T>(MiscHelper.mergeSetsUnion(
				new HashSet<>(), outputs.stream().filter(Objects::nonNull).map(output -> output.results().result()).toList()
			))
		);
	}

	@SafeVarargs
	public static <T extends AbstractVersion<T>> StepOutput<T> merge(StepResults<T> otherResults, StepOutput<T>... outputs) {
		return new StepOutput<>(
			StepStatus.merge(
				Arrays.stream(outputs).filter(Objects::nonNull).map(StepOutput::status).collect(Collectors.toList())),
			new StepResults<T>(MiscHelper.mergeSetsUnion(
				new HashSet<>(), Stream.concat(Stream.of(otherResults.result()), Arrays.stream(outputs).filter(Objects::nonNull).map(output -> output.results().result())).toList()
			))
		);
	}
}
