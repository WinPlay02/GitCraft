package com.github.winplay02.gitcraft.meta;

import java.util.List;

public record VersionDetails(String id, String normalizedVersion, List<String> next, List<String> previous,
							 boolean client, boolean server, boolean sharedMappings) {
}
