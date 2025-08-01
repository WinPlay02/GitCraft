package com.github.winplay02.gitcraft.manifest.metadata;

public record GithubRepositoryBlobContent(String name, String path, String sha, long size, String url,
										  String download_url, String type) {
}
