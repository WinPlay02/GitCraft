package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.pipeline.key.KeyInformation;

public interface IStepConfig {

	String createArtifactComponentString(KeyInformation<?> dist, KeyInformation<?>... matchingFlavours);

}
