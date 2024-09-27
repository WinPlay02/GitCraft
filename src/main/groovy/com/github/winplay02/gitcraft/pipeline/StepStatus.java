package com.github.winplay02.gitcraft.pipeline;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;

public enum StepStatus implements Comparator<StepStatus> {

	NOT_RUN,
	UP_TO_DATE,
	SUCCESS,
	FAILED;

	public static StepStatus merge(StepStatus... statuses) {
		return Arrays.stream(statuses).filter(Objects::nonNull).max(StepStatus::compareTo).orElse(NOT_RUN);
	}

	public static StepStatus merge(Collection<StepStatus> statuses) {
		return statuses.stream().filter(Objects::nonNull).max(StepStatus::compareTo).orElse(NOT_RUN);
	}

	@Override
	public int compare(StepStatus o1, StepStatus o2) {
		if (o1 == null) {
			o1 = NOT_RUN;
		}
		if (o2 == null) {
			o2 = NOT_RUN;
		}
		return o2.ordinal() - o1.ordinal();
	}

	public boolean hasRun() {
		return this != NOT_RUN;
	}
}
