package com.github.winplay02.gitcraft.mappings;

import com.github.winplay02.gitcraft.GitCraft;

import java.util.Locale;

public enum MappingFlavour {
	MOJMAP(GitCraft.MOJANG_MAPPINGS),
	FABRIC_INTERMEDIARY(GitCraft.FABRIC_INTERMEDIARY_MAPPINGS),
	YARN(GitCraft.YARN_MAPPINGS),
	MOJMAP_PARCHMENT(GitCraft.MOJANG_PARCHMENT_MAPPINGS);

	private final Mapping mappingImpl;

	MappingFlavour(Mapping mapping) {
		this.mappingImpl = mapping;
	}

	public Mapping getMappingImpl() {
		return this.mappingImpl;
	}

	@Override
	public String toString() {
		return super.toString().toLowerCase(Locale.ROOT);
	}

}
