package org.moddingx.ljc.convert.meta;

import org.moddingx.ljc.symbol.SymbolTable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class MethodToOverriddenConverter extends ClassVisitor {

    private final int api;
    private final SymbolTable table;
    
    public MethodToOverriddenConverter(int api, SymbolTable table, ClassVisitor visitor) {
        super(api, visitor);
        this.api = api;
        this.table = table;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return new MethodVisitor(this.api, super.visitMethod(access, name, descriptor, signature, exceptions)) {

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE) {
                    SymbolTable.OwnerReplace replace = MethodToOverriddenConverter.this.table.replaceWithOverriddenMethod(opcode, owner, name, descriptor, isInterface);
                    super.visitMethodInsn(replace.opcode(), replace.owner(), name, descriptor, replace.isInterface());
                } else {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                if (bootstrapMethodHandle.getTag() == Opcodes.H_INVOKEVIRTUAL || bootstrapMethodHandle.getTag() == Opcodes.H_INVOKEINTERFACE) {
                    SymbolTable.OwnerReplace replace = MethodToOverriddenConverter.this.table.replaceWithOverriddenMethod(
                            bootstrapMethodHandle.getTag() == Opcodes.H_INVOKEINTERFACE ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL,
                            bootstrapMethodHandle.getOwner(), bootstrapMethodHandle.getName(),
                            bootstrapMethodHandle.getDesc(), bootstrapMethodHandle.isInterface()
                    );
                    int tag = replace.opcode() == Opcodes.INVOKEINTERFACE ? Opcodes.H_INVOKEINTERFACE : Opcodes.H_INVOKEVIRTUAL;
                    Handle newHandle = new Handle(tag, replace.owner(), bootstrapMethodHandle.getName(), bootstrapMethodHandle.getDesc(), replace.isInterface());
                    super.visitInvokeDynamicInsn(name, descriptor, newHandle, bootstrapMethodArguments);
                } else {
                    super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
                }
            }
        };
    }
}
