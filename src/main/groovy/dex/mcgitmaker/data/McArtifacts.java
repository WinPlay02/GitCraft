package dex.mcgitmaker.data;

public record McArtifacts(Artifact clientJar, Artifact clientMappings, Artifact serverJar, Artifact serverMappings,
						  boolean hasMappings) {

}
