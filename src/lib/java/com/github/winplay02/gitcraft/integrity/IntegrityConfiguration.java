package com.github.winplay02.gitcraft.integrity;

/**
 * Configuration for integrity verifying algorithms.
 *
 * @param verifyChecksums Whether checksums should be verified. If this is false, no checksum is actually calculated.
 * @param cacheChecksums  Whether checksums should be cached. If this is false, no checksum is cached and every calculation will start from scratch. When dealing in a malicious environment, this should be disabled as last-modified timestamps can be forged.
 */
public record IntegrityConfiguration(boolean verifyChecksums, boolean cacheChecksums) {
}
