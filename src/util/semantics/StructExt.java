package util.semantics;

import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.structure.SymbolDataStructure;

public class StructExt extends Struct {
    public StructExt(int kind) {
        super(kind);
    }

    public StructExt(int kind, Struct elemType) {
        super(kind, elemType);
    }

    public StructExt(int kind, SymbolDataStructure members) {
        super(kind, members);
    }

    @Override
    public boolean assignableTo(Struct dest) {
        System.out.println("Hello from new struct");

        return super.assignableTo(dest);
    }

    @Override
    public boolean compatibleWith(Struct other) {
        return this.equals(other) || this == TabExt.nullType && other.isRefType() || other == TabExt.nullType && this.isRefType();
    }
}
