package com.github.winplay02.gitcraft.nests;

import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.nests.ornithe.OrnitheNests;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.LazyValue;

import net.ornithemc.nester.nest.Nests;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

public enum NestsFlavour {
	ORNITHE_NESTS(OrnitheNests::new),
	NONE(NoneNests::new);

	private final LazyValue<? extends Nest> impl;

	NestsFlavour(Supplier<? extends Nest> nests) {
		this.impl = LazyValue.of(nests);
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

	public StepStatus provide(IStepContext<?, OrderedVersion> versionContext, MinecraftJar minecraftJar, MappingFlavour mappingFlavour) throws IOException, URISyntaxException, InterruptedException {
		return impl.get().provideNests(versionContext, minecraftJar, mappingFlavour);
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
