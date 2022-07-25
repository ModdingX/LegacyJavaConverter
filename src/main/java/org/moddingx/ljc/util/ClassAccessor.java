package org.moddingx.ljc.util;

import org.objectweb.asm.tree.ClassNode;

import javax.annotation.Nullable;
import java.io.IOException;

@FunctionalInterface
public interface ClassAccessor {
    
    @Nullable
    ClassNode get(String cls) throws IOException;
    
    static ClassAccessor of(ClassAccessor... accessors) {
        return cls -> {
            for (ClassAccessor accessor : accessors) {
                ClassNode node = accessor.get(cls);
                if (node != null) return node;
            }
            return null;
        };
    }
}
