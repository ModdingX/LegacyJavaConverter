package org.moddingx.ljc.util;

import jakarta.annotation.Nullable;
import org.moddingx.ljc.LegacyConverter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassPath implements ClassAccessor, Closeable {
    
    private final List<FileSystem> fileSystems;
    private final List<Path> scanPaths;
    private final Map<String, ClassNode> classes;
    
    public ClassPath(FileSystem fs) {
        this.fileSystems = List.of(fs);
        this.scanPaths = List.of(fs.getPath("/"));
        this.classes = new HashMap<>();
    }
    
    public ClassPath(List<Path> paths) throws IOException {
        List<FileSystem> fileSystems = new ArrayList<>();
        List<Path> scanPaths = new ArrayList<>();
        
        for (Path p : paths) {
            Path path = p.toAbsolutePath().normalize();
            if (Files.isDirectory(path)) {
                scanPaths.add(path);
            } else if (Files.isRegularFile(path)) {
                FileSystem fs = LegacyConverter.jarFS(path);
                if (path.getFileName().toString().endsWith(".jmod")) {
                    Path basePath = fs.getPath("/classes");
                    if (Files.isDirectory(basePath)) {
                        fileSystems.add(fs);
                        scanPaths.add(basePath);
                    } else {
                        fs.close();
                    }
                } else {
                    fileSystems.add(fs);
                    scanPaths.add(fs.getPath("/"));
                }
            }
        }
        
        this.fileSystems = List.copyOf(fileSystems);
        this.scanPaths = List.copyOf(scanPaths);
        this.classes = new HashMap<>();
    }

    @Nullable
    public ClassNode get(String cls) throws IOException {
        if (!this.classes.containsKey(cls)) {
            Path found = null;
            for (Path scan : this.scanPaths) {
                Path test = scan.resolve(cls + ".class");
                if (Files.isRegularFile(test)) {
                    found = test;
                    break;
                }
            }
            if (found != null) {
                try (InputStream in = Files.newInputStream(found)) {
                    ClassReader cr = new ClassReader(in);
                    ClassNode node = new ClassNode();
                    cr.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
                    this.classes.put(cls, node);
                }
            } else {
                this.classes.put(cls, null);
            }
        }
        return this.classes.get(cls);
    }
    
    @Override
    public void close() throws IOException {
        IOException ex = new IOException("Failed to close class path.");
        boolean failure = false;
        for (FileSystem fs : this.fileSystems) {
            try {
                fs.close();
            } catch (Exception e) {
                ex.addSuppressed(e);
                failure = true;
            }
        }
        if (failure) {
            throw ex;
        }
    }
}
