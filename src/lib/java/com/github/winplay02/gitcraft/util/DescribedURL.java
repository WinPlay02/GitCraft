package com.github.winplay02.gitcraft.util;

/**
 * URL with description.
 *
 * @param url URL
 * @param description Description (human-readable) of the URL
 */
public record DescribedURL(String url, String description) {
}
