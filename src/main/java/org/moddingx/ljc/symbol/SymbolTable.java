package org.moddingx.ljc.symbol;

import org.moddingx.ljc.LanguageLevel;
import org.moddingx.ljc.LegacyConverter;
import org.moddingx.ljc.Log;
import org.moddingx.ljc.util.ClassAccessor;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

// SymbolTable only checks for existence, not accessibility.
public class SymbolTable implements ClassAccessor, Closeable {
    
    private static final Pattern OBJECT_PATTERN = Pattern.compile("L([^;]+);");
    
    private final LanguageLevel api;
    private final FileSystem fileSystem;
    private final List<Path> scanPaths;
    private final Set<String> allCurrentClasses;
    private final Map<String, ClassNode> classes;
    
    public SymbolTable(LanguageLevel api, Path path) throws IOException {
        this.api = api;
        Path symTable = path.resolve("lib").resolve("ct.sym").toAbsolutePath().normalize();
        if (!Files.isRegularFile(symTable)) {
            throw new IOException("ct.sym not found: " + symTable);
        }
        this.fileSystem = LegacyConverter.jarFS(symTable);
        try (Stream<Path> paths = Files.list(this.fileSystem.getPath("/"))) {
            List<Path> scanDirs = paths.filter(Files::isDirectory)
                    .map(Path::toAbsolutePath)
                    .filter(p -> p.getFileName().toString().indexOf(api.symbol) >= 0)
                    .toList();
            List<Path> scanPaths = new ArrayList<>();
            for (Path scanDir : scanDirs) {
                try (Stream<Path> modules = Files.list(scanDir)) {
                    modules.map(Path::toAbsolutePath)
                            .map(Path::normalize)
                            .forEach(scanPaths::add);
                }
            }
            this.scanPaths = List.copyOf(scanPaths);
        }
        this.allCurrentClasses = loadAllClasses(path);
        this.classes = new HashMap<>();
    }
    
    public boolean check(ClassNode cls) {
        AtomicBoolean failed = new AtomicBoolean(false);
        ClassVisitor visitor = new Visitor(cls.name, failed);
        cls.accept(visitor);
        return !failed.get();
    }

    @Override
    public void close() throws IOException {
        this.fileSystem.close();
    }

    @Nullable
    public ClassNode get(String cls) throws IOException {
        if (!this.classes.containsKey(cls)) {
            Path found = null;
            for (Path scan : this.scanPaths) {
                Path test = scan.resolve(cls + ".sig");
                if (Files.isRegularFile(test)) {
                    found = test;
                    break;
                }
            }
            if (found != null) {
                try (InputStream in = Files.newInputStream(found)) {
                    ClassReader cr = new ClassReader(in);
                    ClassNode node = new ClassNode();
                    cr.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
                    this.classes.put(cls, node);
                }
            } else {
                this.classes.put(cls, null);
            }
        }
        return this.classes.get(cls);
    }
    
    // If a method is added in a later release, that overrides a method that exists in the older release,
    // we need to change it to the more broader method that exists in the older release.
    public OwnerReplace replaceWithOverriddenMethod(int opcode, String owner, String name, String desc, boolean isInterface) {
        if (this.allCurrentClasses.contains(owner) && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)) {
            try {
                OwnerReplace match = this.findMatchingMethod(owner, name, desc);
                return match == null ? new OwnerReplace(opcode, owner, isInterface) : match;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            return new OwnerReplace(opcode, owner, isInterface);
        }
    }
    
    @Nullable
    private OwnerReplace findMatchingMethod(String owner, String name, String desc) throws IOException {
        ClassNode cls = this.get(owner);
        if (cls == null) return null;
        for (MethodNode method : cls.methods) {
            if ((method.access & Opcodes.ACC_PRIVATE) == 0 && name.equals(method.name) && desc.equals(method.desc)) {
                boolean isInterface = (cls.access & Opcodes.ACC_INTERFACE) != 0;
                return new OwnerReplace(isInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL, owner, isInterface);
            }
        }
        Set<OwnerReplace> allOwners = new HashSet<>();
        if (cls.superName != null) {
            allOwners.add(this.findMatchingMethod(cls.superName, name, desc));
        }
        if (cls.interfaces != null) {
            for (String itf : cls.interfaces) {
                allOwners.add(this.findMatchingMethod(itf, name, desc));
            }
        }
        allOwners.remove(null);
        if (allOwners.isEmpty()) {
            return null;
        } else if (allOwners.size() == 1) {
            return allOwners.iterator().next();
        } else {
            Log.error("Ambiguous method call: Could not track overridden owner for " + owner + " " + name + desc + ", multiple matching: " + allOwners.stream().map(OwnerReplace::owner).sorted().collect(Collectors.joining(", ")));
            return null;
        }
    }

    private boolean missingClass(String cls) throws IOException {
        return this.allCurrentClasses.contains(cls) && this.get(cls) == null;
    }
    
    private boolean missingDesc(String desc) throws IOException {
        if (desc.startsWith("L") && desc.endsWith(";")) {
            return this.missingClass(desc.substring(1, desc.length() - 1));
        } else {
            return false;
        }
    }
    
    private boolean missingDescAny(String desc) throws IOException {
        try {
            return OBJECT_PATTERN.matcher(desc).results().anyMatch(r -> {
                try {
                    return this.missingClass(r.group(1));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException x) {
                throw x;
            } else {
                throw e;
            }
        }
    }
    
    private boolean missingField(String owner, String name, String desc) throws IOException {
        if (this.allCurrentClasses.contains(owner)) {
            ClassNode cls = this.get(owner);
            if (cls == null) return true;
            for (FieldNode field : cls.fields) {
                if (name.equals(field.name) && desc.equals(field.desc)) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }
    
    private boolean missingMethod(String owner, String name, String desc) throws IOException {
        if (this.allCurrentClasses.contains(owner)) {
            ClassNode cls = this.get(owner);
            if (cls == null) return true;
            for (MethodNode method : cls.methods) {
                if (name.equals(method.name) && desc.equals(method.desc)) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }
    
    private static Set<String> loadAllClasses(Path jdkPath) throws IOException {
        Path jmodDir = jdkPath.resolve("jmods").toAbsolutePath().normalize();
        if (!Files.isDirectory(jmodDir)) {
            throw new IOException("JDK modules not found: " + jmodDir);
        }
        List<Path> jmods;
        try (Stream<Path> paths = Files.list(jmodDir)) {
             jmods = paths.filter(Files::isRegularFile)
                    .map(Path::toAbsolutePath)
                    .filter(p -> p.getFileName().toString().endsWith(".jmod"))
                    .toList();
        }
        Set<String> classes = new HashSet<>();
        for (Path jmod : jmods) {
            try (
                    FileSystem fs = LegacyConverter.jarFS(jmod);
                    Stream<Path> paths = Files.walk(fs.getPath("/classes"))
            ) {
                Path classesBase = fs.getPath("/classes");
                paths.map(Path::toAbsolutePath)
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".class"))
                        .filter(p -> !"module-info.class".equals(p.getFileName().toString()))
                        .filter(p -> !"package-info.class".equals(p.getFileName().toString()))
                        .map(classesBase::relativize)
                        .map(p -> IntStream.range(0, p.getNameCount())
                                .mapToObj(p::getName)
                                .map(Path::toString)
                                .collect(Collectors.joining("/"))
                        )
                        .map(str -> str.substring(0, str.length() - 6))
                        .forEach(classes::add);
            }
        }
        return Set.copyOf(classes);
    }
    
    @SuppressWarnings("UnusedReturnValue")
    private class Visitor extends ClassVisitor {
        
        private final String clsName;
        private final AtomicBoolean failed;

        private Visitor(String clsName, AtomicBoolean failed) {
            super(SymbolTable.this.api.asm);
            this.clsName = clsName;
            this.failed = failed;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            if (superName != null) {
                this.reportClass(superName, "#extends");
            }
            if (interfaces != null) {
                for (String iface : interfaces) {
                    this.reportClass(iface, "#implements");
                }
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (this.reportDesc(descriptor, "#annotation")) return null;
            return new SymbolAnnotationVisitor(this.api, "#annotation");
        }

        @Override
        public void visitPermittedSubclass(String permittedSubclass) {
            this.reportClass(permittedSubclass, "#permits");
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (this.reportDesc(descriptor, name)) return null;
            return new SymbolFieldVisitor(this.api, name);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (this.reportDescAny(descriptor, name + descriptor)) return null;
            if (exceptions != null) {
                for (String ex : exceptions) {
                    this.reportClass(ex, name + descriptor + "#exc");
                }
            }
            return new SymbolMethodVisitor(this.api, name + descriptor);
        }
        
        private boolean reportClass(String cls, String from) {
            try {
                if (SymbolTable.this.missingClass(cls)) {
                    Log.error("In " + this.clsName + " (" + from + "): Class not found in java " + SymbolTable.this.api.version + ": " + cls);
                    this.failed.set(true);
                    return true;
                } else {
                    return false;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        
        private boolean reportDesc(String desc, String from) {
            try {
                if (SymbolTable.this.missingDesc(desc)) {
                    Log.error("In " + this.clsName + " (" + from + "): Type not found in java " + SymbolTable.this.api.version + ": " + desc);
                    this.failed.set(true);
                    return true;
                } else {
                    return false;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        
        private boolean reportDescAny(String desc, String from) {
            try {
                if (SymbolTable.this.missingDescAny(desc)) {
                    Log.error("In " + this.clsName + " (" + from + "): Descriptor not found in java " + SymbolTable.this.api.version + ": " + desc);
                    this.failed.set(true);
                    return true;
                } else {
                    return false;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private boolean reportField(String owner, String name, String desc, String from) {
            try {
                if (SymbolTable.this.missingField(owner, name, desc)) {
                    Log.error("In " + this.clsName + " (" + from + "): Field not found in java " + SymbolTable.this.api.version + ": " + owner + " " + name + " " + desc);
                    this.failed.set(true);
                    return true;
                } else {
                    return false;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private boolean reportMethod(String owner, String name, String desc, String from) {
            try {
                if (SymbolTable.this.missingMethod(owner, name, desc)) {
                    Log.error("In " + this.clsName + " (" + from + "): Method not found in java " + SymbolTable.this.api.version + ": " + owner + " " + name + " " + desc);
                    this.failed.set(true);
                    return true;
                } else {
                    return false;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private boolean reportHandle(Handle handle, String from) {
            if (handle.getTag() <= Opcodes.H_PUTSTATIC) {
                return this.reportField(handle.getOwner(), handle.getName(), handle.getDesc(), from + "#handle");
            } else {
                return this.reportMethod(handle.getOwner(), handle.getName(), handle.getDesc(), from + "#handle");
            }
        }

        private boolean reportArg(Object arg, String from) {
            if (arg instanceof Handle handle) {
                return this.reportHandle(handle, from);
            } else if (arg instanceof ConstantDynamic dyn) {
                if (this.reportDescAny(dyn.getDescriptor(), from + "#constant")) return true;
                if (this.reportHandle(dyn.getBootstrapMethod(), from + "#constantbootstrap")) return true;
                for (int i = 0; i < dyn.getBootstrapMethodArgumentCount(); i++) {
                    if (this.reportArg(dyn.getBootstrapMethodArgument(i), from + "#bootstraparg" + i)) return true;
                }
                return false;
            } else {
                return false;
            }
        }
        
        private class SymbolAnnotationVisitor extends AnnotationVisitor {

            private final String from;
            
            public SymbolAnnotationVisitor(int api, String from) {
                super(api);
                this.from = from;
            }

            @Override
            public void visitEnum(String name, String descriptor, String value) {
                Visitor.this.reportDesc(descriptor, this.from);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                if (Visitor.this.reportDesc(descriptor, this.from)) return null;
                return this;
            }

            @Override
            public AnnotationVisitor visitArray(String name) {
                return this;
            }
        }
            
        private class SymbolFieldVisitor extends FieldVisitor {

            private final String from;
            
            public SymbolFieldVisitor(int api, String from) {
                super(api);
                this.from = from;
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (Visitor.this.reportDesc(descriptor, this.from + "#annotation")) return null;
                return new SymbolAnnotationVisitor(this.api, this.from + "annotation");
            }
        }
            
        private class SymbolMethodVisitor extends MethodVisitor {

            private final String from;

            protected SymbolMethodVisitor(int api, String from) {
                super(api);
                this.from = from;
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (Visitor.this.reportDesc(descriptor, this.from + "#annotation")) return null;
                return new SymbolAnnotationVisitor(this.api, this.from + "#annotation");
            }

            @Override
            public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                if (Visitor.this.reportDesc(descriptor, this.from + "#param_annotation")) return null;
                return new SymbolAnnotationVisitor(this.api, this.from + "#param_annotation");
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                Visitor.this.reportClass(type, this.from);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                Visitor.this.reportField(owner, name, descriptor, this.from);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                Visitor.this.reportMethod(owner, name, descriptor, this.from);
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                if (Visitor.this.reportHandle(bootstrapMethodHandle, this.from)) return;
                for (Object arg : bootstrapMethodArguments) {
                    Visitor.this.reportArg(arg, this.from);
                }
            }

            @Override
            public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                Visitor.this.reportDesc(descriptor, this.from);
            }

            @Override
            public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                if (Visitor.this.reportDesc(descriptor, this.from + "#localannotation")) return null;
                return new SymbolAnnotationVisitor(this.api, this.from + "#localannotation");
            }

            @Override
            public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                if (Visitor.this.reportDesc(descriptor, this.from + "#localannotation")) return null;
                return new SymbolAnnotationVisitor(this.api, this.from + "#localannotation");
            }

            @Override
            public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
                Visitor.this.reportDesc(descriptor, this.from);
            }

            @Override
            public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String descriptor, boolean visible) {
                if (Visitor.this.reportDesc(descriptor, this.from + "#localannotation")) return null;
                return new SymbolAnnotationVisitor(this.api, this.from + "#localannotation");
            }
        }
    }
    
    public record OwnerReplace(int opcode, String owner, boolean isInterface) {
        
    }
}
