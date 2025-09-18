package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.types.OrderedVersion;

import java.util.Optional;

public interface GitCraftStepWorker<S extends StepInput> extends IStepWorker<OrderedVersion, S, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> {

	record JarTupleInput(Optional<StorageKey> mergedJar, Optional<StorageKey> clientJar, Optional<StorageKey> serverJar) implements StepInput {
	}

}
