/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2017 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package dex.mcgitmaker.loom

import java.nio.charset.StandardCharsets
import java.nio.file.FileSystem
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.function.Supplier

final class FileSystemUtil {
    static class Delegate implements AutoCloseable, Supplier<FileSystem> {
        public FileSystem fs
        public boolean owner

        byte[] readAllBytes(String path) throws IOException {
            Path fsPath = get().getPath(path);
            if (Files.exists(fsPath)) {
                return Files.readAllBytes(fsPath);
            } else {
                throw new NoSuchFileException(fsPath.toString());
            }
        }

        String readString(String path) throws IOException {
            return new String(readAllBytes(path), StandardCharsets.UTF_8);
        }

        @Override
        void close() throws IOException {
            if (owner) {
                fs.close();
            }
        }

        @Override
        FileSystem get() {
            return fs;
        }
    }

    private FileSystemUtil() {
    }

    private static final Map<String, String> jfsArgsCreate = Map.of("create", "true");
    private static final Map<String, String> jfsArgsEmpty = Collections.emptyMap();

    static Delegate getJarFileSystem(File file, boolean create) throws IOException {
        return getJarFileSystem(file.toURI(), create);
    }

    static Delegate getJarFileSystem(Path path, boolean create) throws IOException {
        return getJarFileSystem(path.toUri(), create);
    }

    static Delegate getJarFileSystem(Path path) throws IOException {
        return getJarFileSystem(path, false);
    }

    static Delegate getJarFileSystem(URI uri, boolean create) throws IOException {
        URI jarUri;

        try {
            jarUri = new URI("jar:" + uri.getScheme(), uri.getHost(), uri.getPath(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }

        try {
            return new Delegate(fs: FileSystems.newFileSystem(jarUri, create ? jfsArgsCreate : jfsArgsEmpty), owner: true);
        } catch (FileSystemAlreadyExistsException e) {
            return new Delegate(fs: FileSystems.getFileSystem(jarUri), owner: false);
        }
    }
}
