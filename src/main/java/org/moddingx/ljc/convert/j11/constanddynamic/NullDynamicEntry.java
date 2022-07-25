package org.moddingx.ljc.convert.j11.constanddynamic;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public record NullDynamicEntry() implements DynamicEntry {

    @Override
    public void load(MethodVisitor visitor) {
        visitor.visitInsn(Opcodes.ACONST_NULL);
    }
}
