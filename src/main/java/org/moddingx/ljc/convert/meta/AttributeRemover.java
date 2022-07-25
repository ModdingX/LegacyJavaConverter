package org.moddingx.ljc.convert.meta;

import org.objectweb.asm.*;

// Remove non-standard attributes
public class AttributeRemover extends ClassVisitor {
    
    public AttributeRemover(int api, ClassVisitor parent) {
        super(api, parent);
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        //
    }

    @Override
    public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
        return new RecordComponentVisitor(this.api, super.visitRecordComponent(name, descriptor, signature)) {

            @Override
            public void visitAttribute(Attribute attribute) {
                //
            }
        };
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        return new FieldVisitor(this.api, super.visitField(access, name, descriptor, signature, value)) {
            
            @Override
            public void visitAttribute(Attribute attribute) {
                //
            }
        };
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return new MethodVisitor(this.api, super.visitMethod(access, name, descriptor, signature, exceptions)) {
            
            @Override
            public void visitAttribute(Attribute attribute) {
                //
            }
        };
    }
}
