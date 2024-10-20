package com.github.winplay02.gitcraft.pipeline.key;

import org.apache.groovy.util.Arrays;

public record ArtifactKey(String... keys) implements StorageKey {
	public ArtifactKey(DirectoryKey type, String... keys) {
		this(Arrays.concat(new String[]{type.type()}, keys));
	}
}

/*public record ArtifactKey(String type, MinecraftJar minecraftJar, MinecraftDist minecraftDist) implements StorageKey {
	public ArtifactKey(DirectoryKey type, MinecraftJar minecraftJar, MinecraftDist minecraftDist) {
		this(type.type(), minecraftJar, minecraftDist);
	}
}
*/
