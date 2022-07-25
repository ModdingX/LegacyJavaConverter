package org.moddingx.ljc.convert.j11.constanddynamic;

import org.moddingx.ljc.util.Bytecode;
import org.objectweb.asm.*;

public record BootstrappedDynamicEntry(String clsName, String name, ConstantDynamic dynamic) implements DynamicEntry {
    
    @Override
    public void load(MethodVisitor visitor) {
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, this.clsName(), this.name(), "()" + this.dynamic().getDescriptor(), false);
    }

    @Override
    public void generateClassInit(MethodVisitor visitor) {
        visitor.visitTypeInsn(Opcodes.NEW, "java/lang/Object");
        visitor.visitInsn(Opcodes.DUP);
        visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        visitor.visitFieldInsn(Opcodes.PUTSTATIC, this.clsName(), "lock$" + this.name(), "Ljava/lang/Object;");
    }

    @Override
    public void generateSynthetic(ClassVisitor visitor) {
        FieldVisitor fv1 = visitor.visitField(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE | Opcodes.ACC_SYNTHETIC,
                this.name(), this.dynamic().getDescriptor(), null, null
        );
        fv1.visitEnd();
        
        FieldVisitor fv2 = visitor.visitField(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE | Opcodes.ACC_SYNTHETIC,
                "has$" + this.name(), "Z", null, null
        );
        fv2.visitEnd();

        FieldVisitor fv3 = visitor.visitField(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                "lock$" + this.name(), "Ljava/lang/Object;", null, null
        );
        fv3.visitEnd();
        
        Type elemType = Type.getType(this.dynamic().getDescriptor());
        int bootstrapArgs = Bytecode.effectiveHandleArgCount(this.dynamic().getBootstrapMethod());
        MethodVisitor mv = visitor.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                this.name(), "()" + this.dynamic().getDescriptor(), null, null
        );
        
        Label afterGen = new Label();
        mv.visitFieldInsn(Opcodes.GETSTATIC, this.clsName(), "has$" + this.name(), "Z");
        mv.visitJumpInsn(Opcodes.IFNE, afterGen);
        
        mv.visitFieldInsn(Opcodes.GETSTATIC, this.clsName(), "lock$" + this.name(), "Ljava/lang/Object;");
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ASTORE, 1);
        mv.visitInsn(Opcodes.MONITORENTER);
        
        Label tcStart = new Label();
        Label tcEnd = new Label();
        Label tcHandler = new Label();
        Label tcHandlerEnd = new Label();
        mv.visitTryCatchBlock(tcStart, tcEnd, tcHandler, null);
        mv.visitLabel(tcStart);
        
        Label afterGen2 = new Label();
        
        mv.visitFieldInsn(Opcodes.GETSTATIC, this.clsName(), "has$" + this.name(), "Z");
        mv.visitJumpInsn(Opcodes.IFNE, afterGen2);
        
        Bytecode.loadPreArgHandle(mv, this.dynamic().getBootstrapMethod());
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;", false);
        if (bootstrapArgs >= 2) {
            mv.visitLdcInsn(this.dynamic().getName());
        }
        if (bootstrapArgs >= 3) {
            Bytecode.loadClassRef(mv, elemType);
        }
        for (int i = 0; i < this.dynamic().getBootstrapMethodArgumentCount(); i++) {
            if (bootstrapArgs >= 4 + i) {
                mv.visitLdcInsn(this.dynamic().getBootstrapMethodArgument(i));
            }
        }
        Bytecode.callHandle(mv, this.dynamic().getBootstrapMethod());
        mv.visitFieldInsn(Opcodes.PUTSTATIC, this.clsName(), this.name(), this.dynamic().getDescriptor());

        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, this.clsName(), "has$" + this.name(), "Z");
        
        mv.visitLabel(afterGen2);
        
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitInsn(Opcodes.MONITOREXIT);
        
        mv.visitLabel(tcEnd);
        mv.visitJumpInsn(Opcodes.GOTO, tcHandlerEnd);
        mv.visitLabel(tcHandler);
        
        mv.visitVarInsn(Opcodes.ASTORE, 2);
        
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitInsn(Opcodes.MONITOREXIT);
        
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/BootstrapMethodError");
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/BootstrapMethodError", "<init>", "(Ljava/lang/Throwable;)V", false);
        mv.visitInsn(Opcodes.ATHROW);
        
        mv.visitLabel(tcHandlerEnd);
        
        mv.visitLabel(afterGen);
        mv.visitFieldInsn(Opcodes.GETSTATIC, this.clsName(), this.name(), this.dynamic().getDescriptor());
        
        mv.visitInsn(elemType.getOpcode(Opcodes.IRETURN));
        
        mv.visitEnd();
    }
}
