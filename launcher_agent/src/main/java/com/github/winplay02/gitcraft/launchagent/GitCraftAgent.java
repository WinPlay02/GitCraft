package com.github.winplay02.gitcraft.launchagent;

import java.lang.instrument.Instrumentation;

public class GitCraftAgent {
	public static void premain(String agentArgs, Instrumentation inst) {
		System.out.println("[GitCraft Agent]: Initializing GitCraft Agent");
		inst.addTransformer(new GitCraftLauncherTransformer());
		inst.addTransformer(new GitCraftResourceDownloaderTransformer());
	}
}
