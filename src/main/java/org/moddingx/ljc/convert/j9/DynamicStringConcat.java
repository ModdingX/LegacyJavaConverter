package org.moddingx.ljc.convert.j9;

import org.moddingx.ljc.util.Bytecode;
import org.moddingx.ljc.util.ExplicitBootstrapClassVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class DynamicStringConcat extends ExplicitBootstrapClassVisitor {
    
    public DynamicStringConcat(int api, ClassVisitor parent) {
        super(api, parent, "strconcat");
        this.addTarget(
                "java/lang/invoke/StringConcatFactory", "makeConcat",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                (mv, methodName, methodType, invokeDynamicName, bootstrapHandle, bootstrapArgs) -> {
                    generateStringConcat(mv, methodType, "\u0001".repeat(methodType.getArgumentTypes().length), new Object[0]);
                }
        );
        this.addTarget(
                "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                (mv, methodName, methodType, invokeDynamicName, bootstrapHandle, bootstrapArgs) -> {
                    if (bootstrapArgs.length == 0) {
                        mv.visitLdcInsn("");
                        mv.visitInsn(Opcodes.ARETURN);
                    } else if (bootstrapArgs[0] instanceof String template) {
                        Object[] strArgs = new Object[bootstrapArgs.length - 1];
                        System.arraycopy(bootstrapArgs, 1, strArgs, 0, strArgs.length);
                        generateStringConcat(mv, methodType, template, strArgs);
                    } else {
                        throw new IllegalStateException("Invalid template value in string concatenation (has type " + bootstrapArgs[0].getClass().getSimpleName() + "): " + bootstrapArgs[0]);
                    }
                }
        );
    }

    private static void generateStringConcat(MethodVisitor mv, Type type, String template, Object[] args) {
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);

        StringBuilder current = new StringBuilder();
        int argIdx = 0;
        int argBytecodeIdx = 0;
        int objIdx = 0;
        for (char chr : template.toCharArray()) {
            if (chr == '\u0001' || chr == '\u0002') {
                if (!current.isEmpty()) {
                    Bytecode.appendString(mv, current.toString());
                    current = new StringBuilder();
                }
                if (chr == '\u0001') {
                    Type argType = type.getArgumentTypes()[argIdx++];
                    int bytecodeIdx = argBytecodeIdx;
                    argBytecodeIdx += argType.getSize();
                    mv.visitVarInsn(argType.getOpcode(Opcodes.ILOAD), bytecodeIdx);
                    Bytecode.appendToString(mv, argType);
                } else {
                    Object value = args[objIdx++];
                    mv.visitLdcInsn(value);
                    Bytecode.appendToString(mv, Bytecode.typeForConstant(value));
                }
            } else {
                current.append(chr);
            }
        }
        if (!current.isEmpty()) {
            Bytecode.appendString(mv, current.toString());
        }
        
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
        mv.visitInsn(Opcodes.ARETURN);
    }
}
