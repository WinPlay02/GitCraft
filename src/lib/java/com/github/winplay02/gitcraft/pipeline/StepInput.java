package com.github.winplay02.gitcraft.pipeline;

public interface StepInput {
	record Empty() implements StepInput {
	}

	StepInput EMPTY = new Empty();
}
