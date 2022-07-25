package org.moddingx.ljc.convert.j11;

import org.moddingx.ljc.convert.j11.constanddynamic.BootstrappedDynamicEntry;
import org.moddingx.ljc.convert.j11.constanddynamic.DynamicEntry;
import org.moddingx.ljc.convert.j11.constanddynamic.IndyBouncer;
import org.moddingx.ljc.convert.j11.constanddynamic.NullDynamicEntry;
import org.moddingx.ljc.util.ChangeAwareClassVisitor;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class ExplicitConstants extends ChangeAwareClassVisitor {

    private String clsName;
    private MethodNode clinit;
    private final Map<ConstantDynamic, DynamicEntry> dynamics;
    private final List<IndyBouncer> indys;
    private final ClassVisitor parent;
    
    public ExplicitConstants(int api, ClassVisitor parent) {
        super(api, parent);
        
        this.clinit = new MethodNode();
        this.clinit.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        this.clinit.name = "<clinit>";
        this.clinit.desc = "()V";
        this.clinit.instructions.add(new InsnNode(Opcodes.RETURN));
        
        this.dynamics = new HashMap<>();
        this.indys = new ArrayList<>();
        this.parent = parent == null ? new ClassVisitor(api) {} : parent;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.clsName = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if ("<clinit>".equals(name) && "()V".equals(descriptor)) {
            this.clinit = new MethodNode();
            this.clinit.access = access;
            this.clinit.name = name;
            this.clinit.desc = descriptor;
            this.clinit.signature = signature;
            this.clinit.exceptions = Arrays.asList(exceptions);
            return new ReplaceDynamic(this.api, this.clinit);
        } else {
            return new ReplaceDynamic(this.api, super.visitMethod(access, name, descriptor, signature, exceptions));
        }
    }

    @Override
    public void visitEnd() {
        for (IndyBouncer bouncer : this.indys) {
            bouncer.generateSynthetic(this.parent, this::addDynamic);
        }
        
        for (DynamicEntry entry : this.dynamics.values()) {
            entry.generateSynthetic(this.parent);
        }
        
        MethodVisitor mv = super.visitMethod(this.clinit.access, this.clinit.name, this.clinit.desc, this.clinit.signature, this.clinit.exceptions == null ? null : this.clinit.exceptions.toArray(String[]::new));
        for (DynamicEntry entry : this.dynamics.values()) {
            entry.generateClassInit(mv);
        }
        this.clinit.accept(mv);
        // visitEnd is called by MethodNode#accept
        
        if (!this.dynamics.isEmpty() || !this.indys.isEmpty()) {
            this.changed();
        }
        
        super.visitEnd();
    }
    
    private DynamicEntry addDynamic(ConstantDynamic dyn) {
        if (!this.dynamics.containsKey(dyn)) {
            String unique = "constant$" + dyn.getName() + "$" + this.dynamics.size();
            if ("java/lang/invoke/ConstantBootstraps".equals(dyn.getBootstrapMethod().getOwner())) {
                if ("nullConstant".equals(dyn.getBootstrapMethod().getName()) && "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;".equals(dyn.getBootstrapMethod().getDesc())) {
                    if (Type.getType(dyn.getDescriptor()).getSort() <= Type.DOUBLE) {
                        throw new IllegalStateException("Null constant on primitive in " + this.clsName + ": " + dyn);
                    } else {
                        this.dynamics.put(dyn, new NullDynamicEntry());
                    }
                } else {
                    this.dynamics.put(dyn, new BootstrappedDynamicEntry(this.clsName, unique, dyn));
                }
            } else {
                this.dynamics.put(dyn, new BootstrappedDynamicEntry(this.clsName, unique, dyn));
            }
        }
        return this.dynamics.get(dyn);
    }
    
    private class ReplaceDynamic extends MethodVisitor {

        protected ReplaceDynamic(int api, MethodVisitor parent) {
            super(api, parent);
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof ConstantDynamic dyn) {
                ExplicitConstants.this.addDynamic(dyn).load(this);
            } else {
                super.visitLdcInsn(value);
            }
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            boolean hasConstantDynamic = false;
            if (bootstrapMethodArguments != null) {
                for (Object arg : bootstrapMethodArguments) {
                    if (arg instanceof ConstantDynamic) {
                        hasConstantDynamic = true;
                        break;
                    }
                }
            }
            if (hasConstantDynamic) {
                String unique = "indyconstant$" + name + "$" + ExplicitConstants.this.indys.size();
                IndyBouncer bouncer = new IndyBouncer(ExplicitConstants.this.clsName, unique, bootstrapMethodHandle, List.of(bootstrapMethodArguments));
                ExplicitConstants.this.indys.add(bouncer);
                super.visitInvokeDynamicInsn(name, descriptor, new Handle(Opcodes.H_INVOKESTATIC, bouncer.clsName(), bouncer.name(), IndyBouncer.BOUNCER_DESC, false));
            } else {
                super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
            }
        }
    }
}
