package com.github.winplay02.gitcraft.types;

public record ServerDistribution(Artifact serverJar, Artifact windowsServer, Artifact serverZip) {
	public boolean hasServerCode() {
		return this.serverJar() != null || this.windowsServer() != null || this.serverZip() != null;
	}
}
