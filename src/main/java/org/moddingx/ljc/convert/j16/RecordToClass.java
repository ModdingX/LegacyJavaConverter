package org.moddingx.ljc.convert.j16;

import org.moddingx.ljc.util.Bytecode;
import org.moddingx.ljc.util.MethodNodeVisitor;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;

import java.util.ArrayList;
import java.util.List;

public class RecordToClass extends ClassVisitor {

    private boolean isRecord;
    private String clsName;
    private final List<RecordComponent> components;
    
    public RecordToClass(int api, ClassVisitor parent) {
        super(api, parent);
        this.isRecord = false;
        this.clsName = null;
        this.components = new ArrayList<>();
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.isRecord = (access & Opcodes.ACC_RECORD) != 0;
        this.clsName = name;
        if (this.isRecord) {
            super.visit(version, access & ~Opcodes.ACC_RECORD, name, signature, "java/lang/Object", interfaces);
        } else {
            super.visit(version, access, name, signature, superName, interfaces);
        }
    }

    @Override
    public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
        this.components.add(new RecordComponent(name, descriptor));
        return null;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (this.isRecord) {
            if ("<init>".equals(name)) {
                return new ConstructorFixer(this.api, super.visitMethod(access, name, descriptor, signature, exceptions));
            } else if ("equals".equals(name) && "(Ljava/lang/Object;)Z".equals(descriptor)) {
                return new EqualsFixer(this.api, super.visitMethod((access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC, name, descriptor, signature, exceptions));
            } else if ("hashCode".equals(name) && "()I".equals(descriptor)) {
                return new HashCodeFixer(this.api, super.visitMethod((access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC, name, descriptor, signature, exceptions));
            } else if ("toString".equals(name) && "()Ljava/lang/String;".equals(descriptor)) {
                return new ToStringFixer(this.api, super.visitMethod((access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC, name, descriptor, signature, exceptions));
            } else {
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        } else {
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
    }
    
    private static class ConstructorFixer extends MethodVisitor {

        public ConstructorFixer(int api, MethodVisitor parent) {
            super(api, parent);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESPECIAL && "java/lang/Record".equals(owner) && "<init>".equals(name) && "()V".equals(descriptor)) {
                super.visitMethodInsn(opcode, "java/lang/Object", name, descriptor, isInterface);
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }
    }
    
    private class EqualsFixer extends MethodNodeVisitor {
        
        public EqualsFixer(int api, MethodVisitor parent) {
            super(api, parent);
        }

        @Override
        protected void processMethod() {
            if (Bytecode.find(this.instructions, InvokeDynamicInsnNode.class, insn -> "equals".equals(insn.name) && ("(L" + RecordToClass.this.clsName + ";Ljava/lang/Object;)Z").equals(insn.desc))) {
                Bytecode.clearMethodContent(this);
                Label afterInstanceof = new Label();
                this.visitVarInsn(Opcodes.ALOAD, 1);
                this.visitInsn(Opcodes.DUP);
                this.visitTypeInsn(Opcodes.INSTANCEOF, RecordToClass.this.clsName);
                this.visitJumpInsn(Opcodes.IFNE, afterInstanceof);
                this.visitInsn(Opcodes.ICONST_0);
                this.visitInsn(Opcodes.IRETURN);
                this.visitLabel(afterInstanceof);
                this.visitTypeInsn(Opcodes.CHECKCAST, RecordToClass.this.clsName);
                this.visitVarInsn(Opcodes.ASTORE, 2);
                for (RecordComponent rc : RecordToClass.this.components) {
                    Label afterCheck = new Label();
                    this.visitVarInsn(Opcodes.ALOAD, 0);
                    this.visitFieldInsn(Opcodes.GETFIELD, RecordToClass.this.clsName, rc.name(), rc.desc());
                    Bytecode.wrapToObject(this, rc.desc());
                    this.visitVarInsn(Opcodes.ALOAD, 2);
                    this.visitFieldInsn(Opcodes.GETFIELD, RecordToClass.this.clsName, rc.name(), rc.desc());
                    Bytecode.wrapToObject(this, rc.desc());
                    this.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                    this.visitJumpInsn(Opcodes.IFNE, afterCheck);
                    this.visitInsn(Opcodes.ICONST_0);
                    this.visitInsn(Opcodes.IRETURN);
                    this.visitLabel(afterCheck);
                }
                this.visitInsn(Opcodes.ICONST_1);
                this.visitInsn(Opcodes.IRETURN);
            }
        }
    }
    
    private class HashCodeFixer extends MethodNodeVisitor {

        public HashCodeFixer(int api, MethodVisitor parent) {
            super(api, parent);
        }

        @Override
        protected void processMethod() {
            if (Bytecode.find(this.instructions, InvokeDynamicInsnNode.class, insn -> "hashCode".equals(insn.name) && ("(L" + RecordToClass.this.clsName + ";)I").equals(insn.desc))) {
                Bytecode.clearMethodContent(this);
                this.visitInsn(Opcodes.ICONST_0);
                for (int i = 0; i < RecordToClass.this.components.size(); i++) {
                    if (i != 0) {
                        this.visitLdcInsn(31);
                        this.visitInsn(Opcodes.IMUL);
                    }
                    RecordComponent rc = RecordToClass.this.components.get(i);
                    this.visitVarInsn(Opcodes.ALOAD, 0);
                    this.visitFieldInsn(Opcodes.GETFIELD, RecordToClass.this.clsName, rc.name(), rc.desc());
                    Bytecode.wrapToObject(this, rc.desc());
                    this.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "hashCode", "()I", false);
                    this.visitInsn(Opcodes.IADD);
                }
                this.visitInsn(Opcodes.IRETURN);
            }
        }
    }
    
    private class ToStringFixer extends MethodNodeVisitor {

        public ToStringFixer(int api, MethodVisitor parent) {
            super(api, parent);
        }

        @Override
        protected void processMethod() {
            if (Bytecode.find(this.instructions, InvokeDynamicInsnNode.class, insn -> "toString".equals(insn.name) && ("(L" + RecordToClass.this.clsName + ";)Ljava/lang/String;").equals(insn.desc))) {
                Bytecode.clearMethodContent(this);
                this.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
                this.visitInsn(Opcodes.DUP);
                this.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
                this.visitLdcInsn(Type.getType("L" + RecordToClass.this.clsName + ";"));
                this.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getSimpleName", "()Ljava/lang/String;", false);
                Bytecode.appendToString(this, "Ljava/lang/String;");
                for (int i = 0; i < RecordToClass.this.components.size(); i++) {
                    RecordComponent rc = RecordToClass.this.components.get(i);
                    Bytecode.appendString(this, (i == 0 ? "[" : ", ") + rc.name() + "=");
                    this.visitVarInsn(Opcodes.ALOAD, 0);
                    this.visitFieldInsn(Opcodes.GETFIELD, RecordToClass.this.clsName, rc.name(), rc.desc());
                    Bytecode.appendToString(this, rc.desc());
                }
                Bytecode.appendString(this, "]");
                this.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
                this.visitInsn(Opcodes.ARETURN);
            }
        }
    }
    
    private record RecordComponent(String name, String desc) {}
}
