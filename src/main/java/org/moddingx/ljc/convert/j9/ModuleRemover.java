package org.moddingx.ljc.convert.j9;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ModuleVisitor;

public class ModuleRemover extends ClassVisitor {

    public ModuleRemover(int api, ClassVisitor parent) {
        super(api, parent);
    }

    @Override
    public ModuleVisitor visitModule(String name, int access, String version) {
        return null;
    }
}
