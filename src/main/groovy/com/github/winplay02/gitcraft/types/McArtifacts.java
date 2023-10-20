package com.github.winplay02.gitcraft.types;

/**
 * Artifacts referenced by minecraft meta, excluding libraries
 *
 * @param clientJar      Client JAR
 * @param clientMappings Client Mojmaps
 * @param serverJar      Server JAR
 * @param serverMappings Server Mojmaps
 * @param hasMappings    Mojmaps are not {@code null}
 */
public record McArtifacts(Artifact clientJar, Artifact clientMappings, Artifact serverJar, Artifact serverMappings,
						  boolean hasMappings) {

}
