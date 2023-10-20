package com.github.winplay02.gitcraft.util;

import com.github.winplay02.gitcraft.GitCraft;
import org.eclipse.jgit.api.Git;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.Objects;

public class RepoWrapper implements Closeable {
	private final Git git;
	private final Path root_path;

	public Git getGit() {
		return this.git;
	}

	public RepoWrapper(Path root_path) throws Exception {
		this.root_path = Objects.requireNonNullElse(root_path, GitCraft.REPO);
		this.git = Git.init().setInitialBranch(GitCraft.config.gitMainlineLinearBranch).setDirectory(this.root_path.toFile()).call();
	}

	@Override
	public void close() {
		git.close();
	}

	public Path getRootPath() {
		return this.root_path;
	}
}
