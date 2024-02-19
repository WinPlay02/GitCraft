package com.github.winplay02.gitcraft.manifest;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.util.LazyValue;

import java.util.Locale;

public enum ManifestSource {
	MOJANG_MINECRAFT_LAUNCHER(GitCraft.MANIFEST_SOURCE_MOJANG_MINECRAFT_LAUNCHER),

	SKYRISING(GitCraft.MANIFEST_SKYRISING);

	private final LazyValue<? extends ManifestProvider<?, ?>> manifestSourceImpl;

	ManifestSource(LazyValue<? extends ManifestProvider<?, ?>> manifestSourceImpl) {
		this.manifestSourceImpl = manifestSourceImpl;
	}

	public ManifestProvider<?, ?> getManifestSourceImpl() {
		return this.manifestSourceImpl.get();
	}

	@Override
	public String toString() {
		return super.toString().toLowerCase(Locale.ROOT);
	}
}
