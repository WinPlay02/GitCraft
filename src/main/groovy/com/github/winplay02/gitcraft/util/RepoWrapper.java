package com.github.winplay02.gitcraft.util;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.pipeline.Committer.CommitMsgFilter;
import com.github.winplay02.gitcraft.types.OrderedVersion;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public class RepoWrapper implements Closeable {
	private final Git git;
	private final Path root_path;

	public Git getGit() {
		return this.git;
	}

	public RepoWrapper(Path root_path) throws Exception {
		this.root_path = Objects.requireNonNullElse(root_path, GitCraftPaths.REPO);
		this.git = Git.init().setInitialBranch(GitCraft.config.gitMainlineLinearBranch).setDirectory(this.root_path.toFile()).call();
	}

	@Override
	public void close() {
		git.close();
	}

	public Path getRootPath() {
		return this.root_path;
	}

	public boolean findVersionRev(OrderedVersion mcVersion) throws GitAPIException, IOException {
		if (git.getRepository().resolve(Constants.HEAD) == null) {
			return false;
		}
		return git.log().all().setRevFilter(new CommitMsgFilter(mcVersion.toCommitMessage())).call().iterator().hasNext();
	}
}
