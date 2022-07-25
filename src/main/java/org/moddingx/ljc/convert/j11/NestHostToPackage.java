package org.moddingx.ljc.convert.j11;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class NestHostToPackage extends ClassVisitor {

    private boolean isNestMember;
    
    public NestHostToPackage(int api, ClassVisitor parent) {
        super(api, parent);
        this.isNestMember = false;
    }

    @Override
    public void visitNestHost(String nestHost) {
        this.isNestMember = true;
    }

    @Override
    public void visitNestMember(String nestMember) {
        this.isNestMember = true;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        if (this.isNestMember && (access & Opcodes.ACC_PRIVATE) != 0) {
            // Make all private members in nest group package private
            return super.visitField(access & ~Opcodes.ACC_PRIVATE, name, descriptor, signature, value);
        } else {
            return super.visitField(access, name, descriptor, signature, value);
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (this.isNestMember && (access & Opcodes.ACC_PRIVATE) != 0) {
            return super.visitMethod(access & ~Opcodes.ACC_PRIVATE, name, descriptor, signature, exceptions);
        } else {
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
    }
}
