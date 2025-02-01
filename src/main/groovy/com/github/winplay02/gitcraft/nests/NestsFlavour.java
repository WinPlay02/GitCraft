package com.github.winplay02.gitcraft.nests;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.pipeline.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.LazyValue;

import net.ornithemc.nester.nest.Nests;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public enum NestsFlavour {
	ORNITHE_NESTS(GitCraft.ORNITHE_NESTS),
	NONE(GitCraft.NONE_NESTS);

	private final LazyValue<? extends Nest> impl;

	NestsFlavour(LazyValue<? extends Nest> nests) {
		this.impl = nests;
	}

	@Override
	public String toString() {
		return super.toString().toLowerCase(Locale.ROOT);
	}

	public String getName() {
		return impl.get().getName();
	}

	public boolean exists(OrderedVersion mcVersion) {
		return impl.get().doNestsExist(mcVersion);
	}

	public boolean exists(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return impl.get().doNestsExist(mcVersion, minecraftJar);
	}

	public boolean canBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour) {
		return impl.get().canNestsBeUsedOn(mcVersion, minecraftJar, mappingFlavour);
	}

	public StepStatus provide(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour) throws IOException {
		return impl.get().provideNests(mcVersion, minecraftJar, mappingFlavour);
	}

	public Optional<Path> getPath(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour) {
		return impl.get().getNestsPath(mcVersion, minecraftJar, mappingFlavour);
	}

	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour, Nests visitor) throws IOException {
		impl.get().visit(mcVersion, minecraftJar, mappingFlavour, visitor);
	}

	public Nests getNests(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour) {
		return impl.get().getNests(mcVersion, minecraftJar, mappingFlavour);
	}
}
