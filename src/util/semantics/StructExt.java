package util.semantics;

import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.structure.SymbolDataStructure;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class StructExt extends Struct {
    private Set<Obj> inheritedMethods = new HashSet<>();
    private String className;
    private int tvfp;

    public int getTvfp() {
        return tvfp;
    }

    public void setTvfp(int tvfp) {
        this.tvfp = tvfp;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public Set<Obj> getInheritedMethods() {
        return inheritedMethods;
    }

    public void setInheritedMethods(Set<Obj> inheritedMethods) {
        this.inheritedMethods = inheritedMethods;
    }

    public StructExt(int kind) {
        super(kind);
    }

    public StructExt(int kind, Struct elemType) {
        super(kind, elemType);
    }

    public StructExt(int kind, SymbolDataStructure members) {
        super(kind, members);
    }

    public boolean isSuperClass(Struct p) {
        /* probably smth like this */
        for(Struct t = this; t != null; t = t.getElemType()) {
            if(t == p) return true;
        }

        return  false;
    }

    /* override entirely, super assignableTo uses Tab.noType etc. */
    @Override
    public boolean assignableTo(Struct dest) {
        if(getKind() == Struct.Class && dest.getKind() == Struct.Class) {
            return this == TabExt.nullType || isSuperClass(dest);
        }

        /* equals checks basic array case */
        return 	this.equals(dest)
                ||
                (this == TabExt.nullType && dest.getKind() == Array)
                ||
                (getKind() == Array && dest.getKind() == Array && dest.getElemType() == TabExt.noType);
    }

    @Override
    public boolean compatibleWith(Struct other) {
        return this.equals(other) || this == TabExt.nullType && other.isRefType() || other == TabExt.nullType && this.isRefType();
    }

    public void copyAddresses() {
        for(Obj inheritedMethod : inheritedMethods) {
            for(Obj parentMethod : getElemType().getMembers()) {
                if(inheritedMethod.getName().equals(parentMethod.getName())) {
                    inheritedMethod.setAdr(parentMethod.getAdr());
                    System.out.println("found a method to copy adr " + inheritedMethod.getName() + " " + inheritedMethod.getAdr());
                    break;
                }
            }
        }
    }
}
