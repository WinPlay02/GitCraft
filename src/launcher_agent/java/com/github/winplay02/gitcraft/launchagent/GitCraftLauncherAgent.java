package com.github.winplay02.gitcraft.launchagent;

import java.lang.instrument.Instrumentation;

public class GitCraftLauncherAgent {
	public static void premain(String agentArgs, Instrumentation inst) {
		inst.addTransformer(new GitCraftLauncherTransformer());
	}
}
