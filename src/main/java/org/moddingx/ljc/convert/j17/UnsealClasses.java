package org.moddingx.ljc.convert.j17;

import org.objectweb.asm.ClassVisitor;

public class UnsealClasses extends ClassVisitor {

    public UnsealClasses(int api, ClassVisitor parent) {
        super(api, parent);
    }
    
    @Override
    public void visitPermittedSubclass(String permittedSubclass) {
        //
    }
}
