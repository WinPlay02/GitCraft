package com.github.winplay02.gitcraft.mappings;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.util.LazyValue;

import java.util.Locale;

public enum MappingFlavour {
	MOJMAP(GitCraft.MOJANG_MAPPINGS),
	FABRIC_INTERMEDIARY(GitCraft.FABRIC_INTERMEDIARY_MAPPINGS),
	YARN(GitCraft.YARN_MAPPINGS),
	MOJMAP_PARCHMENT(GitCraft.MOJANG_PARCHMENT_MAPPINGS),
	CALAMUS_INTERMEDIARY(GitCraft.CALAMUS_INTERMEDIARY_MAPPINGS),
	FEATHER(GitCraft.FEATHER_MAPPINGS),
	IDENTITY_UNMAPPED(GitCraft.IDENTITY_UNMAPPED);

	private final LazyValue<? extends Mapping> mappingImpl;

	MappingFlavour(LazyValue<? extends Mapping> mapping) {
		this.mappingImpl = mapping;
	}

	public Mapping getMappingImpl() {
		return this.mappingImpl.get();
	}

	@Override
	public String toString() {
		String s = super.toString().toLowerCase(Locale.ROOT);
		if (this == CALAMUS_INTERMEDIARY || this == FEATHER) {
			s += "_gen" + GitCraft.config.ornitheIntermediaryGeneration;
		}
		return s;
	}
}
