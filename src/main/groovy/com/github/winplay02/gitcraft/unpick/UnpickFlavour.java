package com.github.winplay02.gitcraft.unpick;

import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.LazyValue;

import java.io.IOException;
import java.util.Locale;
import java.util.function.Supplier;

public enum UnpickFlavour {
	NONE(NoneUnpick::new),
	YARN(YarnUnpick::new),
	FEATHER(FeatherUnpick::new);

	private final LazyValue<? extends Unpick> impl;

	UnpickFlavour(Supplier<? extends Unpick> unpick) {
		this.impl = LazyValue.of(unpick);
	}

	@Override
	public String toString() {
		return super.toString().toLowerCase(Locale.ROOT);
	}

	public StepStatus provide(StepWorker.Context<OrderedVersion> versionContext, MinecraftJar minecraftJar) throws IOException {
		return this.impl.get().provideUnpick(versionContext, minecraftJar);
	}

	public Unpick.UnpickContext getContext(OrderedVersion targetVersion, MinecraftJar minecraftJar) throws IOException {
		return this.impl.get().getContext(targetVersion, minecraftJar);
	}

	public boolean exists(OrderedVersion targetVersion) {
		return this.impl.get().doesUnpickInformationExist(targetVersion);
	}

	public MappingFlavour applicableMappingFlavour(UnpickDescriptionFile unpickDescription)  {
		return this.impl.get().applicableMappingFlavour(unpickDescription);
	}

	public boolean supportsRemapping(UnpickDescriptionFile unpickDescription) {
		return this.impl.get().supportsUnpickRemapping(unpickDescription);
	}
}
