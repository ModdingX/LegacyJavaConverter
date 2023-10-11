package org.moddingx.ljc.util;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import java.util.function.Predicate;

public class Bytecode {
    
    public static <T extends AbstractInsnNode> boolean find(InsnList list, Class<T> cls, Predicate<T> test) {
        for (AbstractInsnNode node : list) {
            //noinspection unchecked
            if (cls.isAssignableFrom(node.getClass()) && test.test((T) node)) {
                return true;
            }
        }
        return false;
    }

    public static Type wrapped(Type type) {
        return switch (type.getSort()) {
            case Type.BOOLEAN -> Type.getObjectType("java/lang/Boolean");
            case Type.BYTE -> Type.getObjectType("java/lang/Byte");
            case Type.CHAR -> Type.getObjectType("java/lang/Character");
            case Type.SHORT -> Type.getObjectType("java/lang/Short");
            case Type.INT -> Type.getObjectType("java/lang/Integer");
            case Type.LONG -> Type.getObjectType("java/lang/Long");
            case Type.FLOAT -> Type.getObjectType("java/lang/Float");
            case Type.DOUBLE -> Type.getObjectType("java/lang/Double");
            case Type.VOID -> Type.getObjectType("java/lang/Void");
            case Type.ARRAY, Type.OBJECT -> type;
            case Type.METHOD -> throw new IllegalArgumentException("Can't wrap method type.");
            default -> throw new IllegalArgumentException("Invalid type");
        };
    }
    
    public static void loadClassRef(MethodVisitor visitor, String type) {
        loadClassRef(visitor, Type.getType(type));
    }
    
    public static void loadClassRef(MethodVisitor visitor, Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN -> visitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Boolean", "TYPE", "Ljava/lang/Class;");
            case Type.BYTE -> visitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Byte", "TYPE", "Ljava/lang/Class;");
            case Type.CHAR -> visitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Character", "TYPE", "Ljava/lang/Class;");
            case Type.SHORT -> visitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Short", "TYPE", "Ljava/lang/Class;");
            case Type.INT -> visitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Integer", "TYPE", "Ljava/lang/Class;");
            case Type.LONG -> visitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Long", "TYPE", "Ljava/lang/Class;");
            case Type.FLOAT -> visitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Float", "TYPE", "Ljava/lang/Class;");
            case Type.DOUBLE -> visitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Double", "TYPE", "Ljava/lang/Class;");
            case Type.VOID -> visitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Void", "TYPE", "Ljava/lang/Class;");
            case Type.ARRAY, Type.OBJECT -> visitor.visitLdcInsn(type);
            case Type.METHOD -> throw new IllegalArgumentException("Can't load classRef for method type.");
        }
    }
    
    public static void wrapToObject(MethodVisitor visitor, String type) {
        wrapToObject(visitor, Type.getType(type));
    }
    
    public static void wrapToObject(MethodVisitor visitor, Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN -> visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
            case Type.BYTE -> visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
            case Type.CHAR -> visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
            case Type.SHORT -> visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
            case Type.INT -> visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            case Type.LONG -> visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
            case Type.FLOAT -> visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
            case Type.DOUBLE -> visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
            case Type.VOID, Type.ARRAY, Type.OBJECT -> {}
            case Type.METHOD -> throw new IllegalArgumentException("Can't wrap instance to object for method type.");
        }
    }
    
    public static void appendString(MethodVisitor visitor, String value) {
        visitor.visitLdcInsn(value);
        appendToString(visitor, "Ljava/lang/String;");
    }
    
    public static void appendToString(MethodVisitor visitor, String type) {
        appendToString(visitor, Type.getType(type));
    }
    
    public static void appendToString(MethodVisitor visitor, Type type) {
        switch (type.getSort()) {
            case Type.VOID -> {}
            case Type.BOOLEAN -> visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Z)Ljava/lang/StringBuilder;", false);
            case Type.BYTE, Type.SHORT, Type.INT -> visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
            case Type.CHAR -> visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
            case Type.LONG -> visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(J)Ljava/lang/StringBuilder;", false);
            case Type.FLOAT -> visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(F)Ljava/lang/StringBuilder;", false);
            case Type.DOUBLE -> visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(D)Ljava/lang/StringBuilder;", false);
            case Type.ARRAY, Type.OBJECT -> visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);
            case Type.METHOD -> throw new IllegalArgumentException("Can't append method instance to string.");
        }
    }
    
    public static Type typeForConstant(Object constant) {
        if (constant instanceof Boolean) {
            return Type.BOOLEAN_TYPE;
        } else if (constant instanceof Byte) {
            return Type.BYTE_TYPE;
        } else if (constant instanceof Character) {
            return Type.CHAR_TYPE;
        } else if (constant instanceof Short) {
            return Type.SHORT_TYPE;
        } else if (constant instanceof Integer) {
            return Type.INT_TYPE;
        } else if (constant instanceof Long) {
            return Type.LONG_TYPE;
        } else if (constant instanceof Float) {
            return Type.FLOAT_TYPE;
        } else if (constant instanceof Double) {
            return Type.DOUBLE_TYPE;
        } else if (constant instanceof Type type) {
            if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
                return Type.getObjectType("java/lang/Class");
            } else if (type.getSort() == Type.METHOD) {
                return Type.getObjectType("java/lang/invoke/MethodType");
            } else {
                throw new IllegalArgumentException("Invalid constant type: " + type);
            }
        } else if (constant instanceof Handle) {
            return Type.getObjectType("java/lang/invoke/MethodHandle");
        } else if (constant instanceof ConstantDynamic dyn) {
            return Type.getType(dyn.getDescriptor());
        } else {
            throw new IllegalArgumentException("Invalid constant (has type " + constant.getClass() + ": " + constant);
        }
    }
    
    public static void callHandle(MethodVisitor visitor, Handle handle) {
        if (handle.getTag() == Opcodes.H_NEWINVOKESPECIAL) {
            throw new IllegalArgumentException("Can't use callHandle with newInvokeSpecial");
        } else {
            callHandleAfterArgs(visitor, handle);
        }
    }
    
    public static void callHandleBeforeArgs(MethodVisitor visitor, Handle handle) {
        if (handle.getTag() == Opcodes.H_NEWINVOKESPECIAL) {
            visitor.visitTypeInsn(Opcodes.NEW, handle.getOwner());
            visitor.visitInsn(Opcodes.DUP);
        }
    }
    
    public static void callHandleAfterArgs(MethodVisitor visitor, Handle handle) {
        switch (handle.getTag()) {
            case Opcodes.H_GETFIELD -> visitor.visitFieldInsn(Opcodes.GETFIELD, handle.getOwner(), handle.getName(), handle.getDesc());
            case Opcodes.H_GETSTATIC -> visitor.visitFieldInsn(Opcodes.GETSTATIC, handle.getOwner(), handle.getName(), handle.getDesc());
            case Opcodes.H_PUTFIELD -> visitor.visitFieldInsn(Opcodes.PUTFIELD, handle.getOwner(), handle.getName(), handle.getDesc());
            case Opcodes.H_PUTSTATIC -> visitor.visitFieldInsn(Opcodes.PUTSTATIC, handle.getOwner(), handle.getName(), handle.getDesc());
            case Opcodes.H_INVOKEVIRTUAL -> visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, handle.getOwner(), handle.getName(), handle.getDesc(), handle.isInterface());
            case Opcodes.H_INVOKESTATIC -> visitor.visitMethodInsn(Opcodes.INVOKESTATIC, handle.getOwner(), handle.getName(), handle.getDesc(), handle.isInterface());
            case Opcodes.H_INVOKESPECIAL, Opcodes.H_NEWINVOKESPECIAL -> visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, handle.getOwner(), handle.getName(), handle.getDesc(), handle.isInterface());
            case Opcodes.H_INVOKEINTERFACE -> visitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, handle.getOwner(), handle.getName(), handle.getDesc(), handle.isInterface());
            default -> throw new IllegalArgumentException("Invalid handle tag: " + handle.getTag());
        }
    }
    
    public static int effectiveHandleArgCount(Handle handle) {
        return switch (handle.getTag()) {
            case Opcodes.H_GETFIELD, Opcodes.H_PUTSTATIC -> 1;
            case Opcodes.H_GETSTATIC -> 0;
            case Opcodes.H_PUTFIELD -> 2;
            case Opcodes.H_INVOKEVIRTUAL, Opcodes.H_INVOKEINTERFACE, Opcodes.H_INVOKESPECIAL -> Type.getMethodType(handle.getDesc()).getArgumentTypes().length + 1;
            case Opcodes.H_INVOKESTATIC, Opcodes.H_NEWINVOKESPECIAL -> Type.getMethodType(handle.getDesc()).getArgumentTypes().length;
            default -> throw new IllegalArgumentException("Invalid handle tag: " + handle.getTag());
        };
    }
    
    public static void clearMethodContent(MethodNode node) {
        if (node.instructions != null) {
            node.instructions.clear();
        } else if ((node.access & Opcodes.ACC_ABSTRACT) != 0 && (node.access & Opcodes.ACC_NATIVE) != 0) {
            node.instructions = new InsnList();
        }
        node.tryCatchBlocks = null;
        node.localVariables = null;
        node.maxLocals = 0;
        node.maxStack = 0;
        node.invisibleLocalVariableAnnotations = null;
        node.visibleLocalVariableAnnotations = null;
    }
}
