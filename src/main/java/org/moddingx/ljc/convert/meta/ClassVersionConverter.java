package org.moddingx.ljc.convert.meta;

import org.moddingx.ljc.LanguageLevel;
import org.objectweb.asm.ClassVisitor;

public class ClassVersionConverter extends ClassVisitor {

    private final LanguageLevel level;
    
    public ClassVersionConverter(LanguageLevel level, ClassVisitor parent) {
        super(level.asm, parent);
        this.level = level;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        int newVersion = Math.min(version, this.level.jvm);
        super.visit(newVersion, access, name, signature, superName, interfaces);
    }
}
