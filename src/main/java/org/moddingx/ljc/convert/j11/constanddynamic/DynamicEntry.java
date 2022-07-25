package org.moddingx.ljc.convert.j11.constanddynamic;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

public interface DynamicEntry {
    
    void load(MethodVisitor visitor);
    default void generateClassInit(MethodVisitor visitor) {}
    default void generateSynthetic(ClassVisitor visitor) {}
}
