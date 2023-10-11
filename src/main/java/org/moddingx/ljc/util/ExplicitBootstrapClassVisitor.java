package org.moddingx.ljc.util;

import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExplicitBootstrapClassVisitor extends ClassVisitor {
    
    private String clsName;
    private boolean clsIsInterface;
    private final String basename;
    private final Map<Target, DynamicFactory> targets;
    private final List<DynamicMethod> methods;

    public ExplicitBootstrapClassVisitor(int api, ClassVisitor parent, String basename) {
        super(api, parent);
        this.basename = basename;
        this.targets = new HashMap<>();
        this.methods = new ArrayList<>();
    }
    
    protected final void addTarget(String owner, String name, String desc, DynamicFactory factory) {
        this.targets.put(new Target(owner, name, desc), factory);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.clsName = name;
        this.clsIsInterface = (access & Opcodes.ACC_INTERFACE) != 0;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return new ReplaceDynamics(this.api, super.visitMethod(access, name, descriptor, signature, exceptions));
    }

    @Override
    public void visitEnd() {
        for (DynamicMethod method : this.methods) {
            MethodVisitor mv = super.visitMethod(
                    Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                    method.methodName(), method.methodType().getDescriptor(), null, null
            );
            method.factory().generateMethod(mv, method.methodName(), method.methodType(), method.invokeDynamicName(), method.bootstrapHandle(), method.bootstrapArgs());
            mv.visitEnd();
        }
        super.visitEnd();
    }
    
    private class ReplaceDynamics extends MethodVisitor {

        public ReplaceDynamics(int api, MethodVisitor methodVisitor) {
            super(api, methodVisitor);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            DynamicFactory factory = ExplicitBootstrapClassVisitor.this.targets.get(new Target(bootstrapMethodHandle));
            if (factory != null) {
                String methodName = ExplicitBootstrapClassVisitor.this.basename + "$" + bootstrapMethodHandle.getName() + "$" + name + "$" + ExplicitBootstrapClassVisitor.this.methods.size();
                Type methodType = Type.getMethodType(descriptor);
                ExplicitBootstrapClassVisitor.this.methods.add(new DynamicMethod(methodName, methodType, name, bootstrapMethodHandle, bootstrapMethodArguments, factory));
                super.visitMethodInsn(Opcodes.INVOKESTATIC, ExplicitBootstrapClassVisitor.this.clsName, methodName, methodType.getDescriptor(), ExplicitBootstrapClassVisitor.this.clsIsInterface);
            } else {
                super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
            }
        }
    }

    private record Target(String owner, String name, String desc) {
        public Target(Handle handle) {
            this(handle.getOwner(), handle.getName(), handle.getDesc());
        }
    }
    
    private record DynamicMethod(String methodName, Type methodType, String invokeDynamicName, Handle bootstrapHandle, Object[] bootstrapArgs, DynamicFactory factory) { }
    
    @FunctionalInterface
    public interface DynamicFactory {
        void generateMethod(MethodVisitor mv, String methodName, Type methodType, String invokeDynamicName, Handle bootstrapHandle, Object[] bootstrapArgs);
    }
}
