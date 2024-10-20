package com.github.winplay02.gitcraft.mappings;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.util.LazyValue;

import java.util.Locale;

public enum MappingFlavour {
	MOJMAP(GitCraft.MOJANG_MAPPINGS),
	FABRIC_INTERMEDIARY(GitCraft.FABRIC_INTERMEDIARY_MAPPINGS),
	YARN(GitCraft.YARN_MAPPINGS),
	MOJMAP_PARCHMENT(GitCraft.MOJANG_PARCHMENT_MAPPINGS),
	IDENTITY_UNMAPPED(GitCraft.IDENTITY_UNMAPPED),
	MOJMAP_YARN(GitCraft.MOJANG_YARN_MAPPINGS);

	private final LazyValue<? extends Mapping> mappingImpl;

	MappingFlavour(LazyValue<? extends Mapping> mapping) {
		this.mappingImpl = mapping;
	}

	public Mapping getMappingImpl() {
		return this.mappingImpl.get();
	}

	@Override
	public String toString() {
		return super.toString().toLowerCase(Locale.ROOT);
	}

}
