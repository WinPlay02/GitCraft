package com.github.winplay02.gitcraft.meta;

import java.io.IOException;
import java.net.URISyntaxException;

public interface VersionMetaSource<M extends VersionMeta<M>> {

	M getLatest(String clas) throws IOException, URISyntaxException, InterruptedException;

}
