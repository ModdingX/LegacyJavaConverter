package org.moddingx.ljc;

import org.jetbrains.annotations.Nullable;
import org.moddingx.ljc.convert.ClassConverter;
import org.moddingx.ljc.convert.ClassHierarchy;
import org.moddingx.ljc.symbol.SymbolTable;
import org.moddingx.ljc.util.ClassAccessor;
import org.moddingx.ljc.util.ClassPath;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LegacyConverter {
    
    public static int run(LanguageLevel api, Path inputPath, Path outputPath, Path javaPath, List<Path> classPath) throws IOException {
        Log.info("Building Converter.");
        ClassConverter converter = new ClassConverter(api);

        Log.info("Building Symbol Table.");
        boolean success = true;
        try (
                SymbolTable table = new SymbolTable(api, javaPath);
                ClassPath cp = new ClassPath(classPath)
        ) {
            Log.info("Reading input.");
            Files.deleteIfExists(outputPath);
            try (
                    FileSystem input = FileSystems.newFileSystem(URI.create("jar:" + inputPath.toUri()), Map.of());
                    FileSystem output = FileSystems.newFileSystem(URI.create("jar:" + outputPath.toUri()), Map.of(
                            "create", "true"
                    ));
                    ClassPath mainJar = new ClassPath(input)
            ) {
                ClassAccessor classes = ClassAccessor.of(mainJar, cp, table);
                ClassHierarchy hierarchy = new ClassHierarchy(classes);

                Manifest manifest;
                if (Files.exists(input.getPath("/META-INF/MANIFEST.MF"))) {
                    try (InputStream in = Files.newInputStream(input.getPath("/META-INF/MANIFEST.MF"))) {
                        manifest = new Manifest(in);
                    }
                } else {
                    manifest = new Manifest();
                }

                if (manifest.getMainAttributes().containsKey("Multi-Release") && Boolean.parseBoolean(manifest.getMainAttributes().getValue("Multi-Release"))) {
                    throw new IllegalStateException("Can't process multi-release jars.");
                }
                
                @Nullable
                Map<String, Set<String>> services = api.ordinal() <= LanguageLevel.JAVA_8.ordinal() ? new HashMap<>() : null;

                List<Path> dirs = new ArrayList<>();
                List<Path> paths = new ArrayList<>();
                try (Stream<Path> allPaths = Files.walk(input.getPath("/"))) {
                    Path manifestPath = input.getPath("/META-INF/MANIFEST.MF").toAbsolutePath().normalize();
                    allPaths.map(Path::toAbsolutePath)
                            .map(Path::normalize)
                            .filter(p -> !Objects.equals(p, manifestPath))
                            .forEach(p -> {
                                if (Files.isDirectory(p)) {
                                    dirs.add(p);
                                } else if (Files.isRegularFile(p)) {
                                    paths.add(p);
                                }
                            });
                }

                for (Path dir : dirs) {
                    Files.createDirectories(output.getPath(dir.toString()));
                }

                Path modulePath = input.getPath("/module-info.class").toAbsolutePath().normalize();
                Path servicePath = input.getPath("/META-INF/services").toAbsolutePath().normalize();
                for (Path path : paths) {
                    Path target = output.getPath(path.toString());
                    if (Objects.equals(modulePath, path) && api.ordinal() <= LanguageLevel.JAVA_8.ordinal()) {
                        try (InputStream in = Files.newInputStream(path)) {
                            ModuleDescriptor desc = ModuleDescriptor.read(in);
                            if (desc.name() != null) {
                                manifest.getMainAttributes().putValue("Automatic-Module-Name", desc.name());
                            }
                            if (desc.mainClass().isPresent() && !manifest.getMainAttributes().containsKey("Main-Class")) {
                                manifest.getMainAttributes().putValue("Main-Class", desc.mainClass().get());
                            }
                            //noinspection ConstantValue
                            if (services != null) {
                                for (ModuleDescriptor.Provides provides : desc.provides()) {
                                    services.computeIfAbsent(provides.service(), k -> new HashSet<>()).addAll(provides.providers());
                                }
                            }
                        }
                    } else if (path.startsWith(servicePath) && services != null) {
                        Set<String> implementations = services.computeIfAbsent(path.getFileName().toString(), k -> new HashSet<>());
                        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
                            implementations.addAll(lines.map(String::strip).filter(str -> !str.isEmpty()).collect(Collectors.toSet()));
                        }
                    } else if (!Objects.equals("module-info.class", path.getFileName().toString()) && path.getFileName().toString().endsWith(".class")) {
                        ClassReader cls;
                        try (InputStream in = Files.newInputStream(path)) {
                            cls = new ClassReader(in);
                        }
                        ClassNode node = converter.convert(cls, table);
                        if (node == null) {
                            Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES);
                        } else {
                            if (!table.check(node)) {
                                success = false;
                            }
                            ClassWriter cw = hierarchy.createClassWriter();
                            node.accept(cw);
                            Files.write(target, cw.toByteArray(), StandardOpenOption.CREATE_NEW);
                        }
                    } else {
                        Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                }

                Files.createDirectories(output.getPath("/META-INF"));
                try (OutputStream out = Files.newOutputStream(output.getPath("/META-INF/MANIFEST.MF"), StandardOpenOption.CREATE_NEW)) {
                    manifest.write(out);
                }
                
                if (services != null && !services.isEmpty()) {
                    Files.createDirectories(output.getPath("/META-INF/services"));
                    for (Map.Entry<String, Set<String>> service : services.entrySet()) {
                        Files.writeString(output.getPath("/META-INF/services").resolve(service.getKey()), service.getValue().stream()
                                .sorted().map(provider -> provider + "\n").collect(Collectors.joining()), StandardOpenOption.CREATE_NEW);
                    }
                }
            }
        }
        if (!success) {
            Log.error("Symbol table match failed. The jar file was converted but relies on members not present in the target version.");
            return 1;
        } else {
            Log.info("Done");
            return 0;
        }
    }
    
    public static FileSystem jarFS(Path path) throws IOException {
        try {
            return FileSystems.getFileSystem(URI.create("jar:" + path.toAbsolutePath().normalize().toUri()));
        } catch (FileSystemNotFoundException e) {
            return FileSystems.newFileSystem(URI.create("jar:" + path.toAbsolutePath().normalize().toUri()), Map.of());
        }
    }
}
