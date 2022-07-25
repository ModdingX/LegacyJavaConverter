package org.moddingx.ljc.convert;

import org.moddingx.ljc.Log;
import org.moddingx.ljc.util.ClassAccessor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

public class ClassHierarchy {
    
    private final ClassAccessor accessor;
    private final Set<String> interfaces;
    private final Map<String, List<String>> hierarchies;

    public ClassHierarchy(ClassAccessor accessor) {
        this.accessor = accessor;
        this.interfaces = new HashSet<>();
        this.hierarchies = new HashMap<>();
        this.hierarchies.put("java/lang/Object", List.of());
    }
    
    public ClassWriter createClassWriter() {
        return new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {

            @Override
            protected ClassLoader getClassLoader() {
                throw new NoSuchElementException();
            }

            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                try {
                    if ("java/lang/Object".equals(type1) || "java/lang/Object".equals(type2)) {
                        return "java/lang/Object";
                    }
                    ClassHierarchy.this.loadHierarchy(type1);
                    ClassHierarchy.this.loadHierarchy(type2);
                    if (ClassHierarchy.this.interfaces.contains(type1) || ClassHierarchy.this.interfaces.contains(type2)) {
                        return "java/lang/Object";
                    }
                    List<String> typesToCheck = ClassHierarchy.this.hierarchies.get(type2);
                    for (String type : ClassHierarchy.this.hierarchies.get(type1)) {
                        if (typesToCheck.contains(type)) {
                            return type;
                        }
                    }
                    return "java/lang/Object";
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
    
    private void loadHierarchy(String cls) throws IOException {
        this.loadHierarchy(cls, cls);
    }
    
    private void loadHierarchy(String cls, String requestedCls) throws IOException {
        if (!this.hierarchies.containsKey(cls)) {
            ClassNode node = this.accessor.get(cls);
            if (node == null) {
                Log.error("Failed to compute class hierarchy for " + requestedCls + ": Class not found: " + cls + " (assuming java/lang/Object)");
                this.hierarchies.put(cls, List.of("java/lang/Object"));
            } else {
                if ((node.access & Opcodes.ACC_INTERFACE) != 0) {
                    this.interfaces.add(cls);
                }
                if (node.superName == null) {
                    this.hierarchies.put(cls, List.of("java/lang/Object"));
                } else {
                    this.loadHierarchy(node.superName, requestedCls);
                    this.hierarchies.put(cls, Stream.concat(Stream.of(node.superName), this.hierarchies.get(node.superName).stream()).toList());
                }
            }
        }
    }
}
