package org.moddingx.ljc;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.util.PathConverter;
import joptsimple.util.PathProperties;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    
    public static void main(String[] args) throws IOException {
        OptionParser options = new OptionParser(false);
        OptionSpec<Path> specJava = options.acceptsAll(List.of("java"), "The Java installation to use").withRequiredArg().withValuesConvertedBy(new PathConverter(PathProperties.DIRECTORY_EXISTING)).defaultsTo(Paths.get(System.getProperty("java.home")).toAbsolutePath().normalize());
        OptionSpec<Path> specClassPath = options.acceptsAll(List.of("cp"), "The runtime class path").withRequiredArg().withValuesConvertedBy(new PathConverter(PathProperties.READABLE)).withValuesSeparatedBy(File.pathSeparator);
        OptionSpec<Integer> specTarget = options.acceptsAll(List.of("target"), "The target language level").withRequiredArg().ofType(Integer.class);
        OptionSpec<Path> specInput = options.acceptsAll(List.of("input"), "The input jar file").withRequiredArg().withValuesConvertedBy(new PathConverter(PathProperties.FILE_EXISTING));
        OptionSpec<Path> specOutput = options.acceptsAll(List.of("output"), "The output jar file").withRequiredArg().withValuesConvertedBy(new PathConverter());
        OptionSet set;
        try {
            set = options.parse(args);
        } catch (OptionException e) {
            options.printHelpOn(System.err);
            return;
        }
        if (!set.has(specTarget) || !set.has(specInput) || !set.has(specOutput)) {
            if (!set.has(specTarget)) System.err.println("Missing required option: " + specTarget);
            if (!set.has(specInput)) System.err.println("Missing required option: " + specInput);
            if (!set.has(specOutput)) System.err.println("Missing required option: " + specOutput);
            options.printHelpOn(System.err);
            return;
        }
        
        Path javaPath = set.valueOf(specJava).toAbsolutePath().normalize();
        List<Path> classPath = set.valuesOf(specClassPath).stream().map(Path::toAbsolutePath).map(Path::normalize).toList();
        
        LanguageLevel api = LanguageLevel.of(set.valueOf(specTarget));
        
        Path inputPath = set.valueOf(specInput).toAbsolutePath().normalize();
        Path outputPath = set.valueOf(specOutput).toAbsolutePath().normalize();

        Log.configureLogs(System.out, System.err, null);
        System.exit(LegacyConverter.run(api, inputPath, outputPath, javaPath, classPath));
    }
}
