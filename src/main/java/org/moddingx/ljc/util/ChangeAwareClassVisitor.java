package org.moddingx.ljc.util;

import org.objectweb.asm.ClassVisitor;

public class ChangeAwareClassVisitor extends ClassVisitor {

    private boolean changed;
    
    protected ChangeAwareClassVisitor(int api, ClassVisitor parent) {
        super(api, parent);
        this.changed = false;
    }
    
    protected void changed() {
        this.changed = true;
    }
    
    public boolean hasChanged() {
        return this.changed;
    }
}
