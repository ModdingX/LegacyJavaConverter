# LegacyJavaConverter

LegacyJavaConverter (or LJC for short) converts bytecode compiled for newer Java versions to work with older versions.

### Why 

The Java Programming Language constantly adds new convenient features that make a developers life easier. However, a lot of code still runs on older JVMs, so recent language features can't be used.

There are two different aspects to this:

  * New features to the language itself (eg records). LJC can convert their bytecode to bytecode compatible with older versions, down to java 8.
  * New APIs added to the Java SE API (eg `HttpClient`). Usages of new APIs can't be converted by LJC, however LJC emits a warning, if such new APIs are used. This is possible with the [ct.sym](https://www.morling.dev/blog/the-anatomy-of-ct-sym-how-javac-ensures-backwards-compatibility/) file found in JDKs since java 9.

### How to use

LJC can be used in two ways: As a standalone tool or as a gradle plugin.

To use LJC as a standalone tool, just invoke the jar file with the correct arguments:

  * `--java` The java installation to use
  * `--cp` The classpath used to compile the input. This is required to resolve the type hierarchy when building the new classes.
  * `--target` The target java version to convert to.
  * `--input` The input jar file.
  * `--output` The output jar file.

As a gradle plugin, you need to add the following to your `build.gradle`:

```groovy
buildscript {
    repositories {
        maven { url = 'https://maven.moddingx.org' }
        mavenCentral()
    }
    dependencies {
        classpath 'org.moddingx:LegacyJavaConverter:<version>'
    }
}

apply plugin: 'org.moddingx.lcj'
```

The gradle plugin provides two new task types. Both are located in the `org.moddingx.ljc.gradle` package:

`LjcTask` produces an artifact, that can be published. It extends [AbstractArchiveTask](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.AbstractArchiveTask.html), so it can be published. However, most of the copy logic is not used. Instead, you can set these properties:

  * `input`: The input jar file to convert
  * `languageLevel`: The target language level.
  * `compiler`: A java compiler to use. Defaults to the compiler used in the `compileJava` task and if that is not available, the java installation, gradle is running on.
  * `classpath`: The classpath used to compile the input jar. Defaults to the classpath used in the `compileJava` task.
  * `logFile`: A file to store logs.

`LjcConfigurationTask` converts a whole configuration and produces a new `FileCollection` that can be used as a dependency, like this:

```groovy
configurations {
    legacy
}

dependencies {
    legacy 'group:artifact:version'
}

task convertDependencies(type: org.moddingx.ljc.gradle.LjcConfigurationTask) {
    input = configurations.legacy
    languageLevel = 8
}

dependencies {
    implementation convertDependencies.output()
}
```
