package dex.mcgitmaker

import org.jetbrains.java.decompiler.main.DecompilerContext
import org.jetbrains.java.decompiler.main.extern.IResultSaver
import org.jetbrains.java.decompiler.util.InterpreterUtil

import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

public class NIODirectoryResultSaver implements IResultSaver {
    private final Path root;

    public NIODirectoryResultSaver(Path root) {
        this.root = root;
    }

    @Override
    public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {
        Path entryPath = this.root.resolve(entryName);

        try (BufferedWriter writer = Files.newBufferedWriter(entryPath)) {
            if (content != null) {
                writer.write(content);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save class", e);
        }
    }

    @Override
    public void saveDirEntry(String path, String archiveName, String entryName) {
        Path entryPath = this.root.resolve(entryName);
        try {
            Files.createDirectories(entryPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save directory", e);
        }
    }

    @Override
    public void createArchive(String path, String archiveName, Manifest manifest) {

    }

    @Override
    public void saveFolder(String path) {
        Path entryPath = this.root.resolve(path);
        try {
            Files.createDirectories(entryPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save directory", e);
        }
    }

    @Override
    public void copyFile(String source, String path, String entryName) {
        try {
            Files.copy(Paths.get(source), this.root.resolve(entryName))
        }
        catch (IOException ex) {
            DecompilerContext.getLogger().writeMessage("Cannot copy " + source + " to " + entryName, ex);
        }
    }

    @Override
    public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
        Path entryPath = this.root.resolve(path).resolve(entryName);
        try (BufferedWriter writer = Files.newBufferedWriter(entryPath)) {
            if (content != null) {
                writer.write(content);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save class", e);
        }
    }

    @Override
    public void copyEntry(String source, String path, String archiveName, String entryName) {
        try (ZipFile srcArchive = new ZipFile(new File(source))) {
            ZipEntry entry = srcArchive.getEntry(entryName);
            if (entry != null) {
                try (InputStream in = srcArchive.getInputStream(entry)) {
                    Files.copy(in, this.root.resolve(entryName))
                }
            }
        }
        catch (IOException ex) {
            String message = "Cannot copy entry " + entryName + " from " + source;
            DecompilerContext.getLogger().writeMessage(message, ex);
        }
    }

    @Override
    public void closeArchive(String path, String archiveName) {

    }
}