package com.github.winplay02.gitcraft.signatures;

import java.io.IOException;
import java.nio.file.Path;

import com.github.winplay02.gitcraft.pipeline.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.types.OrderedVersion;

import io.github.gaming32.signaturechanger.tree.SigsFile;

public class NoneSignatures extends SignaturesPatch {

	@Override
	public String getName() {
		return "None";
	}

	@Override
	public boolean doSignaturesExist(OrderedVersion mcVersion) {
		return true;
	}

	@Override
	public boolean doSignaturesExist(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return true;
	}

	@Override
	public boolean canSignaturesBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return true;
	}

	@Override
	public StepStatus provideSignatures(OrderedVersion mcVersion, MinecraftJar minecraftJar) throws IOException {
		return StepStatus.SUCCESS;
	}

	@Override
	protected Path getSignaturesPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return null;
	}

	@Override
	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, SigsFile visitor) throws IOException {
	}
}
