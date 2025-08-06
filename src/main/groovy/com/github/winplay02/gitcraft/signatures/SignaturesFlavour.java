package com.github.winplay02.gitcraft.signatures;

import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.signatures.ornithe.SparrowSignatures;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.LazyValue;

import io.github.gaming32.signaturechanger.tree.SigsFile;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

public enum SignaturesFlavour {
	SPARROW(SparrowSignatures::new),
	NONE(NoneSignatures::new);

	private final LazyValue<? extends SignaturesPatch> impl;

	SignaturesFlavour(Supplier<? extends SignaturesPatch> signatures) {
		this.impl = LazyValue.of(signatures);
	}

	@Override
	public String toString() {
		return super.toString().toLowerCase(Locale.ROOT);
	}

	public String getName() {
		return impl.get().getName();
	}

	public boolean exists(OrderedVersion mcVersion) {
		return impl.get().doSignaturesExist(mcVersion);
	}

	public boolean exists(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return impl.get().doSignaturesExist(mcVersion, minecraftJar);
	}

	public boolean canBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return impl.get().canSignaturesBeUsedOn(mcVersion, minecraftJar);
	}

	public StepStatus provide(StepWorker.Context<OrderedVersion> versionContext, MinecraftJar minecraftJar) throws IOException, URISyntaxException, InterruptedException {
		return impl.get().provideSignatures(versionContext, minecraftJar);
	}

	public Optional<Path> getPath(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return impl.get().getSignaturesPath(mcVersion, minecraftJar);
	}

	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, SigsFile visitor) throws IOException {
		impl.get().visit(mcVersion, minecraftJar, visitor);
	}

	public SigsFile getSignatures(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return impl.get().getSignatures(mcVersion, minecraftJar);
	}
}
