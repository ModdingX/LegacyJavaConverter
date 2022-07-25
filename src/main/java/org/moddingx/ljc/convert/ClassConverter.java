package org.moddingx.ljc.convert;

import org.moddingx.ljc.LanguageLevel;
import org.moddingx.ljc.Log;
import org.moddingx.ljc.convert.meta.AttributeRemover;
import org.moddingx.ljc.convert.meta.ClassVersionConverter;
import org.moddingx.ljc.convert.meta.MethodToOverriddenConverter;
import org.moddingx.ljc.symbol.SymbolTable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.tree.ClassNode;

import javax.annotation.Nullable;
import java.util.*;

public class ClassConverter {
    
    private final LanguageLevel api;
    private final Map<Integer, LanguageLevel[]> conversions;
    
    public ClassConverter(LanguageLevel api) {
        this.api = api;
        
        LanguageLevel[] levels = LanguageLevel.values();
        Map<Integer, LanguageLevel[]> map = new HashMap<>();
        for (int i = api.ordinal() + 1; i < levels.length; i++) {
            List<LanguageLevel> list = new ArrayList<>();
            for (int j = i; j > api.ordinal(); j--) {
                list.add(levels[j]);
            }
            map.put(levels[i].jvm, list.toArray(LanguageLevel[]::new));
        }
        this.conversions = Map.copyOf(map);
    }
    
    @Nullable
    public ClassNode convert(ClassReader cls, @Nullable SymbolTable table) {
        int classVer = cls.readInt(cls.getItem(1) - 7);
        if (classVer > this.api.jvm) {
            Log.info("Converting " + cls.getClassName());
            if (!this.conversions.containsKey(classVer)) {
                throw new IllegalStateException("Don't know how to downgrade a class of version 0x" + String.format("%08X", classVer));
            } else {
                ClassNode node = new ClassNode(this.api.asm);
                ClassVisitor visitor = new ClassVersionConverter(this.api, node);
                visitor = new AttributeRemover(this.api.asm, visitor);
                if (table != null) {
                    visitor = new MethodToOverriddenConverter(this.api.asm, table, visitor);
                }
                for (LanguageLevel level : this.conversions.get(classVer)) {
                    visitor = level.create(visitor);
                }
                cls.accept(visitor, 0);
                return node;
            }
        } else {
            return null;
        }
    }
}
