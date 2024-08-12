package com.github.winplay02.gitcraft.exceptions;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.util.LazyValue;

import java.util.Locale;

public enum ExceptionsFlavour {
	RAVEN(GitCraft.RAVEN_EXCEPTIONS),
	NONE(GitCraft.NONE_EXCEPTIONS);

	private final LazyValue<? extends ExceptionsPatch> exceptionsImpl;

	ExceptionsFlavour(LazyValue<? extends ExceptionsPatch> exceptions) {
		this.exceptionsImpl = exceptions;
	}

	public ExceptionsPatch getExceptionsImpl() {
		return this.exceptionsImpl.get();
	}

	@Override
	public String toString() {
		return super.toString().toLowerCase(Locale.ROOT);
	}
}
