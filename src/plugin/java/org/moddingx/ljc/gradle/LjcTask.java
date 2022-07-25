package org.moddingx.ljc.gradle;

import org.gradle.api.UnknownTaskException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.moddingx.ljc.LanguageLevel;
import org.moddingx.ljc.LegacyConverter;
import org.moddingx.ljc.Log;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

public abstract class LjcTask extends AbstractArchiveTask {

    public LjcTask() {
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
        
        this.getCompiler().convention(compileTask.flatMap(jc -> jc.getJavaCompiler() == null ? this.getProject().provider(() -> null) : jc.getJavaCompiler()));
        this.getClasspath().convention(compileTask.flatMap(jc -> this.getProject().provider(jc::getClasspath)));
        this.getLogFile().convention(() -> this.getProject().file("build").toPath().resolve(this.getName()).resolve("ljc.log").toFile());
        
        this.getDestinationDirectory().set(this.getProject().file("build").toPath().resolve(this.getName()).toFile());
        this.getArchiveBaseName().convention(this.getProject().provider(this.getProject()::getName));
        this.getArchiveVersion().convention(this.getProject().provider(() -> this.getProject().getVersion().toString()));
        this.getArchiveClassifier().convention("legacy");
        this.getArchiveExtension().convention("jar");
        
        this.getOutputs().upToDateWhen(t -> false);
        this.from(this.getInput());
    }

    @InputFile
    public abstract RegularFileProperty getInput();
    
    @Input
    public abstract Property<Integer> getLanguageLevel();
    
    @Nested
    @Optional
    public abstract Property<JavaCompiler> getCompiler();
    
    @InputFiles
    public abstract Property<FileCollection> getClasspath();
    
    @Optional
    @OutputFile
    public abstract RegularFileProperty getLogFile();

    @Nonnull
    @Override
    protected CopyAction createCopyAction() {
        // Just do nothing
        return copy -> () -> true;
    }
    
    @TaskAction
    public void apply() throws IOException {
        LanguageLevel level = LanguageLevel.of(this.getLanguageLevel().get());
        
        Path input = this.getInput().get().getAsFile().toPath().toAbsolutePath().normalize();
        Path output = this.getArchiveFile().get().getAsFile().toPath().toAbsolutePath().normalize();
        
        if (output.getParent() != null && !Files.exists(output.getParent())) {
            Files.createDirectories(output.getParent());
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
        
        FileCollection classpath = this.getClasspath().getOrNull();
        List<Path> cp;
        if (classpath != null) {
            cp = classpath.getFiles().stream().map(File::toPath).map(Path::toAbsolutePath).map(Path::normalize).toList();
        } else {
            cp = List.of();
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
            int exit = LegacyConverter.run(level, input, output, javaDir, cp);
            if (exit != 0) {
                throw new IOException("LegacyJavaConverter failed");
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
