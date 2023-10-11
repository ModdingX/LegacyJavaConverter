package org.moddingx.ljc.convert.j21;

import org.moddingx.ljc.util.Bytecode;
import org.moddingx.ljc.util.ExplicitBootstrapClassVisitor;
import org.objectweb.asm.*;

public class DynamicSwitchPatterns extends ExplicitBootstrapClassVisitor {
    
    public DynamicSwitchPatterns(int api, ClassVisitor classVisitor) {
        super(api, classVisitor, "switch");
        this.addTarget(
                "java/lang/runtime/SwitchBootstraps", "enumSwitch",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                (mv, methodName, methodType, invokeDynamicName, bootstrapHandle, bootstrapArgs) -> {
                    String enumOwner = methodType.getArgumentTypes()[0].getInternalName();
                    generateSwitch(mv, resolveEnumSwitch(enumOwner, bootstrapArgs));
                }
        );
        this.addTarget(
                "java/lang/runtime/SwitchBootstraps", "typeSwitch",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                (mv, methodName, methodType, invokeDynamicName, bootstrapHandle, bootstrapArgs) -> {
                    generateSwitch(mv, resolveTypeSwitch(bootstrapArgs));
                }
        );
    }
    
    private static void generateSwitch(MethodVisitor mv, Object[] constants) {
        Label nonNull = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitJumpInsn(Opcodes.IFNONNULL, nonNull);
        mv.visitLdcInsn(-1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitLabel(nonNull);
        
        for (int i = 0; i < constants.length; i++) {
            generateCheck(mv, i, constants[i]);
        }
        
        mv.visitLdcInsn(constants.length);
        mv.visitInsn(Opcodes.IRETURN);
    }
    
    private static void generateCheck(MethodVisitor mv, int atIdx, Object constant) {
        Label checkFail = new Label();
        
        // First check that our index is greater or equal to restart
        mv.visitLdcInsn(atIdx);
        mv.visitVarInsn(Opcodes.ILOAD, 1);
        mv.visitJumpInsn(Opcodes.IF_ICMPLT, checkFail);
        
        // Perform the correct check depending on the type
        switch (constant) {
            case Integer value -> {
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitTypeInsn(Opcodes.INSTANCEOF, "java/lang/Integer");
                mv.visitJumpInsn(Opcodes.IFEQ, checkFail);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                mv.visitLdcInsn(value);
                mv.visitJumpInsn(Opcodes.IF_ICMPNE, checkFail);
            }
            case String value -> {
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitTypeInsn(Opcodes.INSTANCEOF, "java/lang/String");
                mv.visitJumpInsn(Opcodes.IFEQ, checkFail);
                mv.visitLdcInsn(value);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
                mv.visitJumpInsn(Opcodes.IFEQ, checkFail);
            }
            case Type type when type.getSort() != Type.METHOD -> {
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitTypeInsn(Opcodes.INSTANCEOF, Bytecode.wrapped(type).getInternalName());
                mv.visitJumpInsn(Opcodes.IFEQ, checkFail);
            }
            case EnumConstant(String owner, String name) -> {
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitTypeInsn(Opcodes.INSTANCEOF, owner);
                mv.visitJumpInsn(Opcodes.IFEQ, checkFail);
                mv.visitLdcInsn(name);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitTypeInsn(Opcodes.CHECKCAST, owner);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "name", "()Ljava/lang/String;", false);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
                mv.visitJumpInsn(Opcodes.IFEQ, checkFail);
            }
            case null, default -> throw new IllegalStateException("Invalid switch branch: " + constant);
        }
        
        // Check succeeded, return index
        mv.visitLdcInsn(atIdx);
        mv.visitInsn(Opcodes.IRETURN);
        
        // Jump target for failed check
        mv.visitLabel(checkFail);
    }

    // In enumSwitch, enum constants are represented as plain strings and the enum type is inferred from the method type.
    // This method processes the bootstrap arguments and replaces strings with matching EnumConstant instances.
    private static Object[] resolveEnumSwitch(String enumOwner, Object[] bootstrapArgs) {
        Object[] args = new Object[bootstrapArgs.length];
        for (int i = 0; i < bootstrapArgs.length; i++) {
            if (bootstrapArgs[i] instanceof String enumConstantName) {
                args[i] = new EnumConstant(enumOwner, enumConstantName);
            } else {
                args[i] = bootstrapArgs[i];
            }
        }
        return args;
    }
    
    // In typeSwitch, enum constants are represented as EnumDesc and loaded via two nested constant bootstraps (for ClassDesc and EnumDesc)
    // This method processes the bootstrap arguments, extracts the data from the ConstantDynamic nodes and places them into EnumConstant instances.
    private static Object[] resolveTypeSwitch(Object[] bootstrapArgs) {
        Object[] args = new Object[bootstrapArgs.length];
        for (int i = 0; i < bootstrapArgs.length; i++) {
            if (bootstrapArgs[i] instanceof ConstantDynamic dyn) {
                checkConstantInvoke("typeSwitch", dyn, "java/lang/Enum$EnumDesc", "of", "(Ljava/lang/constant/ClassDesc;Ljava/lang/String;)Ljava/lang/Enum$EnumDesc;");
                if (dyn.getBootstrapMethodArgument(1) instanceof ConstantDynamic clsRefDyn && dyn.getBootstrapMethodArgument(2) instanceof String enumConstantName) {
                    checkConstantInvoke("typeSwitch.classRef", clsRefDyn, "java/lang/constant/ClassDesc", "of", "(Ljava/lang/String;)Ljava/lang/constant/ClassDesc;");
                    if (clsRefDyn.getBootstrapMethodArgument(1) instanceof String enumOwner) {
                        args[i] = new EnumConstant(enumOwner.replace('.', '/'), enumConstantName);
                    } else {
                        throw new IllegalStateException("Invalid constant bootstrap in typeSwitch.classRef: Expected (String), got: (" + dyn.getBootstrapMethodArgument(1) + ")");
                    }
                } else {
                    throw new IllegalStateException("Invalid constant bootstrap in typeSwitch: Expected (ClassDesc, String), got: (" + dyn.getBootstrapMethodArgument(1) + ", " + dyn.getBootstrapMethodArgument(2) + ")");
                }
            } else {
                args[i] = bootstrapArgs[i];
            }
        }
        return args;
    }
    
    private static void checkConstantInvoke(String path, ConstantDynamic dyn, String targetOwner, String targetName, String targetDesc) {
        if (!"java/lang/invoke/ConstantBootstraps".equals(dyn.getBootstrapMethod().getOwner()) || !"invoke".equals(dyn.getBootstrapMethod().getName()) || !"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;[Ljava/lang/Object;)Ljava/lang/Object;".equals(dyn.getBootstrapMethod().getDesc())) {
            throw new IllegalStateException("Invalid constant bootstrap in " + path + ": Wrong bootstrap method: " + dyn.getBootstrapMethod());
        } else if (!(dyn.getBootstrapMethodArgument(0) instanceof Handle handle)) {
            throw new IllegalStateException("Invalid constant bootstrap in " + path + ": Invalid argument, expected handle (at position 0): " + dyn.getBootstrapMethodArgument(0));
        } else if (!targetOwner.equals(handle.getOwner()) || !targetName.equals(handle.getName()) || !targetDesc.equals(handle.getDesc())) {
            throw new IllegalStateException("Invalid constant bootstrap in " + path + ": Invalid invoke target, expected " + targetOwner + " " + targetName + targetDesc + ", got: " + handle);
        }
    }
    
    private record EnumConstant(String owner, String name) {}
}
