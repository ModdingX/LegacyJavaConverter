package org.moddingx.ljc.util;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.MethodNode;

public abstract class MethodNodeVisitor extends MethodNode {

    private final MethodVisitor parent;
    
    public MethodNodeVisitor(int api, MethodVisitor parent) {
        super(api);
        this.parent = parent;
    }
    
    protected abstract void processMethod();
    
    @Override
    public void visitEnd() {
        super.visitEnd();
        this.processMethod();
        this.accept(this.parent);
    }
}
