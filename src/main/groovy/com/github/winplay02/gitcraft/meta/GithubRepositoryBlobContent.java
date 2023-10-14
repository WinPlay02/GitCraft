package com.github.winplay02.gitcraft.meta;

public record GithubRepositoryBlobContent(String name, String path, String sha, long size, String url,
										  String download_url, String type) {
}
