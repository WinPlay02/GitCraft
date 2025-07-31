package com.github.winplay02.gitcraft.util;

import com.github.winplay02.gitcraft.GitCraft;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;

public class RepoWrapper implements Closeable {
	private final Git git;
	private final Path root_path;

	public Git getGit() {
		return this.git;
	}

	public RepoWrapper(Path root_path) throws Exception {
		this.root_path = Objects.requireNonNullElse(root_path, GitCraftPaths.FILESYSTEM_ROOT.getDefaultRepository());
		this.git = Git.init().setInitialBranch(GitCraft.getRepositoryConfiguration().gitMainlineLinearBranch()).setDirectory(this.root_path.toFile()).call();
	}

	@Override
	public void close() {
		this.git.close();
	}

	public Path getRootPath() {
		return this.root_path;
	}

	public void clearWorkingTree() throws IOException {
		for (Path innerPath : MiscHelper.listDirectly(this.getRootPath())) {
			if (!innerPath.toString().endsWith(".git")) {
				if (Files.isDirectory(innerPath)) {
					MiscHelper.deleteDirectory(innerPath);
				} else {
					Files.deleteIfExists(innerPath);
				}
			}
		}
	}

	public boolean existsRevWithCommitMessage(String commitMessage) throws GitAPIException, IOException {
		if (this.git.getRepository().resolve(Constants.HEAD) == null) {
			return false;
		}
		return this.git.log().all().setRevFilter(new CommitMsgFilter(commitMessage)).call().iterator().hasNext();
	}

	public RevCommit findRevByCommitMessage(String commitMessage) throws GitAPIException, IOException {
		Iterator<RevCommit> iterator = this.git.log().all().setRevFilter(new CommitMsgFilter(commitMessage)).call().iterator();
		if (iterator.hasNext()) {
			return iterator.next();
		}
		return null;
	}

	public boolean doesBranchExist(String target_branch) throws IOException {
		Ref target_ref = this.git.getRepository().getRefDatabase().findRef(target_branch);
		return target_ref != null;
	}

	public void checkoutNewOrphanBranch(String target_branch) throws GitAPIException {
		this.git.checkout().setOrphan(true).setName(target_branch).call();
	}

	public void switchHEAD(Ref ref) throws IOException {
		RefUpdate refUpdate = this.git.getRepository().getRefDatabase().newUpdate(Constants.HEAD, false);
		RefUpdate.Result result = refUpdate.link(ref.getName());
		if (result != RefUpdate.Result.FORCED) {
			MiscHelper.panic("Unsuccessfully changed HEAD to %s, result was: %s", ref, result);
		}
	}

	public void writeMERGE_HEAD(HashSet<RevCommit> commits) throws IOException {
		if (commits.isEmpty()) {
			this.git.getRepository().writeMergeHeads(null);
		} else {
			this.git.getRepository().writeMergeHeads(commits.stream().toList());
		}
	}

	public void createCommitUsingAllChanges(String authorName, String authorMail, Date authoredDateTime, TimeZone authoredTimeZone, String message) throws GitAPIException {
		// Remove removed files from index
		this.git.add().addFilepattern(".").setRenormalize(false).setUpdate(true).call();
		// Stage new files
		this.git.add().addFilepattern(".").setRenormalize(false).call();
		PersonIdent author = new PersonIdent(authorName, authorMail, authoredDateTime.toInstant(), authoredTimeZone.toZoneId());
		this.git.commit().setMessage(message).setAuthor(author).setCommitter(author).setSign(false).call();
	}

	public void createBranchFromCurrentCommit(String branchName) throws GitAPIException, IOException {
		try (RevWalk walk = new RevWalk(this.git.getRepository())) {
			ObjectId commitId = this.git.getRepository().resolve(Constants.HEAD);
			RevCommit commit = walk.parseCommit(commitId);
			this.git.branchCreate().setName(branchName).setStartPoint(commit).call();
		}
	}

	public void resetRef(String targetRefName, RevCommit targetCommit) throws IOException {
		Ref targetRef = this.git.getRepository().getRefDatabase().findRef(targetRefName);
		RefUpdate refUpdate = this.git.getRepository().getRefDatabase().newUpdate(targetRef.getName(), true);
		refUpdate.setNewObjectId(targetCommit);
		refUpdate.setForceUpdate(true);
		RefUpdate.Result result = refUpdate.update();
		if (result != RefUpdate.Result.FORCED && result != RefUpdate.Result.NO_CHANGE) {
			MiscHelper.panic("Unsuccessfully changed ref %s to %s, result was: %s", targetRefName, targetCommit.getId(), result);
		}
	}

	public void deleteRef(String targetRefName) throws IOException {
		Ref targetRef = this.git.getRepository().getRefDatabase().findRef(targetRefName);
		RefUpdate refUpdate = this.git.getRepository().getRefDatabase().newUpdate(targetRef.getName(), true);
		refUpdate.setForceUpdate(true);
		RefUpdate.Result result = refUpdate.delete();
		if (result != RefUpdate.Result.FORCED) {
			MiscHelper.panic("Unsuccessfully deleted ref %s, result was: %s", targetRefName, result);
		}
	}

	public void checkoutBranch(String targetBranch) throws IOException {
		if (!Objects.equals(this.git.getRepository().getBranch(), targetBranch)) {
			Ref target_ref = this.git.getRepository().getRefDatabase().findRef(targetBranch);
			if (target_ref == null) {
				MiscHelper.panic("Could not find target branch '%s'", targetBranch);
			}
			this.switchHEAD(target_ref);
		}
	}

	public boolean isHeadless() throws IOException {
		return this.git.getRepository().resolve(Constants.HEAD) == null;
	}

	public List<Ref> getRefsContainingCommit(RevCommit targetCommit) throws IOException {
		List<Ref> refs = this.git.getRepository().getRefDatabase().getRefs();
		List<Ref> refsContaining = new ArrayList<>();
		RevWalk walk = new RevWalk(this.git.getRepository());
		for (Ref ref : refs) {
			if (!ref.isPeeled()) {
				ref = this.git.getRepository().getRefDatabase().peel(ref);
			}

			ObjectId objectId = ref.getPeeledObjectId();
			if (objectId == null)
				objectId = ref.getObjectId();
			RevCommit commit = null;
			try {
				commit = walk.parseCommit(objectId);
			} catch (MissingObjectException | IncorrectObjectTypeException ignored) {
			}
			if (commit != null) {
				walk.markStart(commit);
				walk.setRevFilter(new CommitRevFilter(targetCommit.getId()));
				if (walk.iterator().hasNext()) {
					refsContaining.add(ref);
				}
			}
			walk.reset();
		}
		return refsContaining;
	}

	public void createSymbolicHEAD(String refTarget) throws IOException {
		RefUpdate refUpdate = this.git.getRepository().getRefDatabase().newUpdate(Constants.HEAD, false);
		RefUpdate.Result result = refUpdate.link(refTarget);
		if (result != RefUpdate.Result.FORCED && result != RefUpdate.Result.NEW) {
			MiscHelper.panic("Unsuccessfully created symbolic HEAD to %s, result was: %s", refTarget, result);
		}
	}

	public void gc() throws GitAPIException {
		this.git.gc().call();
	}

	public static final class CommitMsgFilter extends RevFilter {
		String msg;

		public CommitMsgFilter(String msg) {
			this.msg = msg;
		}

		@Override
		public boolean include(RevWalk walker, RevCommit c) {
			return Objects.equals(c.getFullMessage(), this.msg);
		}

		@Override
		public RevFilter clone() {
			return new CommitMsgFilter(this.msg);
		}

		@Override
		public String toString() {
			return "Msg Filter: " + this.msg;
		}
	}

	public static final class CommitRevFilter extends RevFilter {
		ObjectId revId;

		public CommitRevFilter(ObjectId revId) {
			this.revId = revId;
		}

		@Override
		public boolean include(RevWalk walker, RevCommit c) {
			return Objects.equals(c.getId(), this.revId);
		}

		@Override
		public RevFilter clone() {
			return new CommitRevFilter(this.revId);
		}

		@Override
		public String toString() {
			return "Rev Filter: " + this.revId;
		}
	}
}
