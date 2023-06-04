package org.moddingx.ljc.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.*;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.moddingx.ljc.LanguageLevel;
import org.moddingx.ljc.LegacyConverter;
import org.moddingx.ljc.Log;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Stream;

public abstract class LjcConfigurationTask extends DefaultTask {

    public LjcConfigurationTask() {
        Provider<JavaCompile> compileTask = this.getProject().provider(() -> {
            try {
                if (this.getProject().getTasks().getByName("compileJava") instanceof JavaCompile jc) {
                    return jc;
                } else {
                    return null;
                }
            } catch (UnknownTaskException e) {
                return null;
            }
        });
        
        this.getOutputDirectory().set(this.getProject().file("build").toPath().resolve(this.getName()).toFile());
        this.getCompiler().convention(compileTask.flatMap(jc -> jc.getJavaCompiler() == null ? this.getProject().provider(() -> null) : jc.getJavaCompiler()));
        this.getLogFile().convention(() -> this.getProject().file("build").toPath().resolve(this.getName()).resolve("ljc.log").toFile());
    }

    public void input(Configuration configuration) {
        this.getInput().set(this.getProject().provider(() -> this.getProject().files(configuration.resolve())));
    }

    @InputFiles
    public abstract Property<FileCollection> getInput();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    public Provider<FileCollection> output() {
        return this.getProject().provider(() -> {
            Directory dir = this.getOutputDirectory().get();
            return this.getProject().fileTree(dir, tree -> tree.include("*.jar")).builtBy(this);
        });
    }
    
    @Input
    public abstract Property<Integer> getLanguageLevel();
    
    @Nested
    @Optional
    public abstract Property<JavaCompiler> getCompiler();
    
    @Optional
    @OutputFile
    public abstract RegularFileProperty getLogFile();
    
    @TaskAction
    public void apply() throws IOException {
        LanguageLevel level = LanguageLevel.of(this.getLanguageLevel().get());
        
        List<Path> inputs = this.getInput().get().getFiles().stream().map(File::toPath).map(Path::toAbsolutePath).map(Path::normalize).toList();
        Path outputDir = this.getOutputDirectory().get().getAsFile().toPath().toAbsolutePath().normalize();
        
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        } else try (Stream<Path> contents = Files.list(outputDir)) {
            for (Path path : contents.toList()) {
                Files.deleteIfExists(path);
            }
        }
        
        JavaCompiler compiler = this.getCompiler().getOrNull();
        Path javaDir;
        // Check that javac exists (we actually have a JDK)
        // The exception proves the rule: This time it's not windows but some linux distros
        // Ubuntu seems to install a JRE into a location that is expected to hold a jdk and even is named 'jdk'
        if (compiler != null && Files.exists(compiler.getExecutablePath().getAsFile().toPath().toAbsolutePath().normalize())) {
            javaDir = compiler.getMetadata().getInstallationPath().getAsFile().toPath().toAbsolutePath().normalize();
        } else {
            javaDir = Paths.get(System.getProperty("java.home")).toAbsolutePath().normalize();
        }

        OutputStream log = null;
        RegularFile logFile = this.getLogFile().getOrNull();
        if (logFile != null) {
            Path path = logFile.getAsFile().toPath().toAbsolutePath().normalize();
            if (path.getParent() != null && !Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            log = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
        
        try {
            Log.configureLogs(null, System.err, log);
            for (Path pathToConvert : inputs) {
                Path dest = outputDir.resolve(pathToConvert.getFileName());
                Log.info("Converting " + pathToConvert + " to " + dest);
                int exit = LegacyConverter.run(level, pathToConvert, dest, javaDir, inputs.stream().filter(p -> p != pathToConvert).toList());
                if (exit != 0) {
                    throw new IOException("LegacyJavaConverter failed on " + pathToConvert);
                }
            }
        } finally {
            try {
                if (log != null) {
                    log.close();
                }
            } catch (Exception e) {
                //
            }
        }
    }
}
