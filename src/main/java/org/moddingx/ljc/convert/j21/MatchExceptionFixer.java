package org.moddingx.ljc.convert.j21;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

// Convert match exception constructors auto-generated by the compiler to runtime exceptions.
// Also put a marker in the exception message to show that this should have been a MatchException.
public class MatchExceptionFixer extends ClassVisitor {
    
    public MatchExceptionFixer(int api, ClassVisitor classVisitor) {
        super(api, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return new ConvertMatchException(this.api, super.visitMethod(access, name, descriptor, signature, exceptions));
    }
    
    private static class ConvertMatchException extends MethodVisitor {

        public ConvertMatchException(int api, MethodVisitor methodVisitor) {
            super(api, methodVisitor);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == Opcodes.NEW && "java/lang/MatchException".equals(type)) {
                super.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException");
            } else {
                super.visitTypeInsn(opcode, type);
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESPECIAL && "java/lang/MatchException".equals(owner) && "<init>".equals(name)) {
                if ("(Ljava/lang/String)V".equals(descriptor)) {
                    super.visitLdcInsn("(java/lang/MatchException) ");
                    super.visitInsn(Opcodes.SWAP);
                    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false);
                    super.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;Ljava/lang/Throwable;)V", isInterface);
                } else if ("(Ljava/lang/String;Ljava/lang/Throwable;)V".equals(descriptor)) {
                    super.visitInsn(Opcodes.SWAP);
                    super.visitLdcInsn("(java/lang/MatchException) ");
                    super.visitInsn(Opcodes.SWAP);
                    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false);
                    super.visitInsn(Opcodes.SWAP);
                    super.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;Ljava/lang/Throwable;)V", isInterface);
                } else {
                    super.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/RuntimeException", "<init>", descriptor, isInterface);
                }
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }
    }
}
