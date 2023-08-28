package org.moddingx.ljc.convert.j11.constanddynamic;

import org.moddingx.ljc.util.Bytecode;
import org.objectweb.asm.*;

import java.util.List;
import java.util.function.Function;

public record IndyBouncer(String clsName, String name, Handle oldBootstrap, List<Object> args) {
    
    public static final String BOUNCER_DESC = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType)Ljava/lang/invoke/CallSite;";
    
    public void generateSynthetic(ClassVisitor visitor, Function<ConstantDynamic, DynamicEntry> dynamicFactory) {
        MethodVisitor mv = visitor.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                 this.name(), BOUNCER_DESC, null, null
        );

        int bootstrapArgs = Bytecode.effectiveHandleArgCount(this.oldBootstrap());
        
        Bytecode.callHandleBeforeArgs(mv, this.oldBootstrap());
        
        if (bootstrapArgs >= 1) mv.visitVarInsn(Opcodes.ALOAD, 0);
        if (bootstrapArgs >= 2) mv.visitVarInsn(Opcodes.ALOAD, 1);
        if (bootstrapArgs >= 3) mv.visitVarInsn(Opcodes.ALOAD, 2);

        for (int i = 0; i < this.args().size(); i++) {
            if (bootstrapArgs >= 4 + i) {
                if (this.args().get(i) instanceof ConstantDynamic dyn) {
                    dynamicFactory.apply(dyn).load(mv);
                } else {
                    mv.visitLdcInsn(this.args().get(i));
                }
            }
        }
        
        Bytecode.callHandleAfterArgs(mv, this.oldBootstrap());
        
        mv.visitInsn(Opcodes.ARETURN);
        
        mv.visitEnd();
    }
}
