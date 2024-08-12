package com.github.winplay02.gitcraft.signatures;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.util.LazyValue;

import java.util.Locale;

public enum SignaturesFlavour {
	SPARROW(GitCraft.SPARROW_SIGNATURES),
	NONE(GitCraft.NONE_SIGNATURES);

	private final LazyValue<? extends SignaturesPatch> signaturesImpl;

	SignaturesFlavour(LazyValue<? extends SignaturesPatch> signatures) {
		this.signaturesImpl = signatures;
	}

	public SignaturesPatch getSignaturesImpl() {
		return this.signaturesImpl.get();
	}

	@Override
	public String toString() {
		return super.toString().toLowerCase(Locale.ROOT);
	}
}
