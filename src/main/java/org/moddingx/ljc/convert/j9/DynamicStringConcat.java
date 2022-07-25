package org.moddingx.ljc.convert.j9;

import org.moddingx.ljc.util.Bytecode;
import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.List;

public class DynamicStringConcat extends ClassVisitor {

    private String clsName;
    private final List<TemplatedStringFactory> factories;
    
    public DynamicStringConcat(int api, ClassVisitor parent) {
        super(api, parent);
        this.factories = new ArrayList<>();
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.clsName = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return new FixDynamicStrings(this.api, super.visitMethod(access, name, descriptor, signature, exceptions));
    }

    @Override
    public void visitEnd() {
        for (TemplatedStringFactory factory : this.factories) {
            MethodVisitor mv = super.visitMethod(
                    Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                    factory.name(), factory.type().getDescriptor(), null, null
            );

            mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
            mv.visitInsn(Opcodes.DUP);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);

            StringBuilder current = new StringBuilder();
            int argIdx = 0;
            int argBytecodeIdx = 0;
            int objIdx = 0;
            for (char chr : factory.template.toCharArray()) {
                if (chr == '\u0001' || chr == '\u0002') {
                    if (!current.isEmpty()) {
                        Bytecode.appendString(mv, current.toString());
                        current = new StringBuilder();
                    }
                    if (chr == '\u0001') {
                        Type argType = factory.type.getArgumentTypes()[argIdx++];
                        int bytecodeIdx = argBytecodeIdx;
                        argBytecodeIdx += argType.getSize();
                        mv.visitVarInsn(argType.getOpcode(Opcodes.ILOAD), bytecodeIdx);
                        Bytecode.appendToString(mv, argType);
                    } else {
                        Object value = factory.args[objIdx++];
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

            mv.visitEnd();
        }
        super.visitEnd();
    }

    private class FixDynamicStrings extends MethodVisitor {

        public FixDynamicStrings(int api, MethodVisitor parent) {
            super(api, parent);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            if ("java/lang/invoke/StringConcatFactory".equals(bootstrapMethodHandle.getOwner())) {
                Type type = Type.getMethodType(descriptor);
                if ("makeConcat".equals(bootstrapMethodHandle.getName()) && "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;".equals(bootstrapMethodHandle.getDesc())) {
                    this.generateWithTemplate(bootstrapMethodHandle.getName(), type, "\u0001".repeat(type.getArgumentTypes().length), new Object[0]);
                } else if ("makeConcatWithConstants".equals(bootstrapMethodHandle.getName()) && "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;".equals(bootstrapMethodHandle.getDesc())) {
                    if (bootstrapMethodArguments.length == 0) {
                        this.visitLdcInsn("");
                    } else if (bootstrapMethodArguments[0] instanceof String template) {
                        Object[] strArgs = new Object[bootstrapMethodArguments.length - 1];
                        System.arraycopy(bootstrapMethodArguments, 1, strArgs, 0, strArgs.length);
                        this.generateWithTemplate(bootstrapMethodHandle.getName(), type, template, strArgs);
                    } else {
                        throw new IllegalStateException("Invalid template value in string concatenation (has type " + bootstrapMethodArguments[0].getClass().getSimpleName() + "): " + bootstrapMethodArguments[0]);
                    }
                } else {
                    throw new IllegalStateException("Unknown string concatenation: " + bootstrapMethodHandle);
                }
            } else {
                super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
            }
        }
        
        private void generateWithTemplate(String name, Type type, String template, Object[] args) {
            String unique = "strconcat$" + name + "$" + DynamicStringConcat.this.factories.size();
            DynamicStringConcat.this.factories.add(new TemplatedStringFactory(unique, type, template, args));
            super.visitMethodInsn(Opcodes.INVOKESTATIC, DynamicStringConcat.this.clsName, unique, type.getDescriptor(), false);
        }
    }
    
    private record TemplatedStringFactory(String name, Type type, String template, Object[] args) {}
}
