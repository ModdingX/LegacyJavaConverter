package org.moddingx.ljc.convert.j17;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class AlwaysStrictFP extends ClassVisitor {

    public AlwaysStrictFP(int api, ClassVisitor parent) {
        super(api, parent);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return super.visitMethod(access | Opcodes.ACC_STRICT, name, descriptor, signature, exceptions);
    }
}
