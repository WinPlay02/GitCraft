package com.github.winplay02.gitcraft.nests;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.util.LazyValue;

import java.util.Locale;

public enum NestsFlavour {
	ORNITHE_NESTS(GitCraft.ORNITHE_NESTS),
	NONE(GitCraft.NONE_NESTS);

	private final LazyValue<? extends Nest> nestsImpl;

	NestsFlavour(LazyValue<? extends Nest> mapping) {
		this.nestsImpl = mapping;
	}

	public Nest getNestsImpl() {
		return this.nestsImpl.get();
	}

	@Override
	public String toString() {
		return super.toString().toLowerCase(Locale.ROOT);
	}
}
