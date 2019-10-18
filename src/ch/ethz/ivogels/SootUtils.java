package ch.ethz.ivogels;

import soot.*;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.*;
import soot.jimple.internal.JIdentityStmt;
import soot.util.Chain;

import java.util.*;
import java.util.stream.Stream;

public class SootUtils {

    // Returns true if q in [start,end)
    // TODO: res = false when q is inside try block?
    public static boolean isBetween(Chain<Unit> units, Unit q, Unit start, Unit end) {
        assert units.contains(start) && units.contains(end);
        assert units.follows(end, start);
        boolean res = units.follows(end, q) && units.follows(q, start) || q.equals(start);
        boolean chk = false;
        for (Iterator<Unit> it = units.iterator(start, end); it.hasNext(); ) {
            Unit u = it.next();
            if (u.equals(end))
                break;
            if(u.equals(q)){
                chk = true;
                break;
            }
        }
        if(res != chk) {
            System.err.println("[isBetween] unit.follows() != iterator test. Trusting iterator test...");
        }
        return chk;
    }

    /* Returns the set of locals actively being used by the units inside the body. Useful for removing unused Locals. */
    public static Set<Local> getUsedLocals(Body b) {
        Set<Local> localSet = new HashSet<>();
        for (Unit u : b.getUnits()) {
            for(ValueBox vb : u.getUseAndDefBoxes()) {
                Value value = vb.getValue();
                localSet.addAll(getLocals(value));
            }
        }
        // No pair of locals should exist that have the same name
        assert localSet.stream().noneMatch(l1 -> localSet.stream().anyMatch(l2 -> l1.getName().equals(l2.getName()) && !l1.equals(l2)));
        return localSet;
    }

    private static Set<Local> getLocals(Value v) {
        Set<Local> localSet = new HashSet<>();
        if(v instanceof Local) {
            localSet.add((Local) v);
        } else {
            for(ValueBox innerValueBox : v.getUseBoxes()) {
                localSet.addAll(getLocals(innerValueBox.getValue()));
            }
        }
        return localSet;
    }

    public static List<Local> getParameterLocals(SootMethod m) {
        Local[] parameterLocals = new Local[m.getParameterCount()];
        for(Unit u : m.getActiveBody().getUnits()) {
            if (u instanceof IdentityStmt) {
                int paramNr = ((ParameterRef) ((IdentityStmt) u).getRightOp()).getIndex();
                parameterLocals[paramNr] = (Local)((IdentityStmt) u).getLeftOp();
            }
        }
        return Arrays.asList(parameterLocals);
    }

    public static void initializeLocalToNull(PatchingChain<Unit> units, Local l) {
        initializeLocalsToNull(units, Collections.singleton(l));
    }

    // TODO: Refactor and use method below
    public static void initializeLocalsToNull(PatchingChain<Unit> units, Collection<Local> locals) {
        List<Local> inited = new ArrayList<>();
        Unit unitAfterIdentityStmts = null;
        for (Iterator it = units.snapshotIterator(); it.hasNext(); ) {
            unitAfterIdentityStmts = (Unit) it.next();
            if (unitAfterIdentityStmts instanceof JIdentityStmt) {
                JIdentityStmt s = (JIdentityStmt) unitAfterIdentityStmts;
                inited.add((Local) s.leftBox.getValue());
            } else {
                break;
            }
        }
        // Make sure we don't overwrite previously initialized locals
        assert locals.stream().noneMatch(inited::contains);

        for(Local l : locals)
            units.insertBeforeNoRedirect(Jimple.v().newAssignStmt(l, NullConstant.v()),
                    unitAfterIdentityStmts);
    }

    public enum InitType {
        NoInit,
        AsNull,
        AsNew
    }

    // TODO: types of init: only add to local list, init as null, init as new
    // We need to go through codebase to possibly simplify null declarations if they're not needed
    public static Local useLocal(Body body, String name, Type type, InitType initType, int arraySize) {
        Stream<Local> matchingLocals = body.getLocals().stream().filter(l -> l.getName().equals(name));
        Local local = matchingLocals.findFirst().orElseGet(() -> {
            Local l;
            if (name == null || name.length() == 0) {
                // Add variable to body of type `type`
                l = (new LocalGenerator(body)).generateLocal(type);
            } else {
                l = Jimple.v().newLocal(name, type);
                body.getLocals().add(l);
            }
            return l;
        });

        if(initType == InitType.NoInit)
            return local;

        Unit unitAfterIdentityStmts = null;
        for (Iterator it = body.getUnits().snapshotIterator(); it.hasNext(); ) {
            unitAfterIdentityStmts = (Unit) it.next();
            if (unitAfterIdentityStmts instanceof JIdentityStmt) {
                if(((JIdentityStmt) unitAfterIdentityStmts).getLeftOp() == local)
                    return local; // Don't try to set already initialized locals
                JIdentityStmt s = (JIdentityStmt) unitAfterIdentityStmts;
            } else {
                break;
            }
        }

        Value rValue;
        if(initType == InitType.AsNew) {
            if(!(type instanceof ArrayType || (type instanceof RefType)))
                throw new IllegalArgumentException("Can't instantiate soot.Type that isn't of type ArrayType or RefType");
            if (type instanceof ArrayType) {
                assert arraySize >= 0;
                rValue = Jimple.v().newNewArrayExpr(((ArrayType) type).getArrayElementType(), IntConstant.v(arraySize));
            } else {
                rValue = Jimple.v().newNewExpr((RefType)type);
            }
        } else {
            if(type instanceof PrimType) {
                // TODO: Needed or implicit by JVM/ART?
                if(type instanceof IntType || type instanceof ByteType || type instanceof ShortType
                                           || type instanceof BooleanType || type instanceof CharType) {
                    rValue = IntConstant.v(0);
                } else if (type instanceof LongType) {
                    rValue = LongConstant.v(0);
                } else if (type instanceof FloatType) {
                    rValue = FloatConstant.v(0);
                } else if (type instanceof DoubleType) {
                    rValue = DoubleConstant.v(0);
                } else {
                    throw new IllegalArgumentException("Unknown primitive type: " + type.getClass().getCanonicalName());
                }
            }
            else {
                rValue = NullConstant.v();
            }
        }
        body.getUnits().insertBefore(Jimple.v().newAssignStmt(local, rValue), unitAfterIdentityStmts);
        return local;
    }

    public static Local useLocal(Body body, Type type, InitType initType) {
        return useLocal(body, null, type, initType, -1);
    }

    public static Local useLocal(Body body, String name, Type type, InitType initType) {
        return useLocal(body, name, type, initType, -1);
    }

    // Creates a new Local in b that holds the boxed primitive type.
    // The conversion method 'valueOf' is inserted before the specified unit.
    public static Local boxPrimitive(Body b, Unit insertBefore, Value primitive, Type primitiveType) {
        SootClass wrapperClass = primitiveToBoxedClass(primitiveType);

        // Generate and insert new local in b that holds a reference to the wrapped instance
        Local wrappedLocal = useLocal(b, RefType.v(wrapperClass), InitType.AsNull);

        // TODO: Needed? Add generic method for insertBefore?
        List<Trap> endTraps = new ArrayList<>();
        for(Trap t : b.getTraps()) {
            if(t.getEndUnit() == insertBefore) {
                endTraps.add(t);
            }
        }

        Unit valueOfAssign = Jimple.v().newAssignStmt(wrappedLocal,
                Jimple.v().newStaticInvokeExpr(wrapperClass.getMethod("valueOf",
                        Collections.singletonList(primitiveType)).makeRef(), primitive)
        );

        // $wrappedLocal = <wrapperClass>.valueOf($primitive);
        b.getUnits().insertBefore(valueOfAssign, insertBefore);

        for (Trap t : endTraps) {
            t.setEndUnit(insertBefore);
        }

        return wrappedLocal;
    }
    public static Local boxPrimitive(Body b, Unit insertBefore, Value primitive) {
        return boxPrimitive(b, insertBefore, primitive, primitive.getType());
    }



        // Creates a new Local in b that holds the unboxed primitive type.
    // The conversion method 'boxed.<primitiveType>Value()' is inserted before the specified unit.
    public static Local unboxPrimitive(Body b, Unit insertBefore, Local boxedPrimitive, Type primitiveType) {
        assert primitiveToBoxedClass(primitiveType).getType() == boxedPrimitive.getType();
        SootClass wrapperClass = Scene.v().getSootClass(boxedPrimitive.getType().toString());
        Local primitive = useLocal(b,  primitiveType, InitType.NoInit);

        // $primitive = $wrapperLocalCasted.<unwrapMethod>
        b.getUnits().insertBeforeNoRedirect(Jimple.v().newAssignStmt(primitive, // Note: NoRedir required as else target should not be modified
                Jimple.v().newVirtualInvokeExpr(boxedPrimitive,
                        wrapperClass.getMethod(String.format("%1$s %1$sValue()", primitive.getType().toString())).makeRef())
        ), insertBefore);
        return primitive;
    }

    public static SootClass primitiveToBoxedClass(Type primitiveType) {
        if(primitiveType == IntType.v()) {
            return Scene.v().getSootClass("java.lang.Integer");
        } else if (primitiveType == ByteType.v()) {
            return Scene.v().getSootClass("java.lang.Byte");
        } else if (primitiveType == ShortType.v()) {
            return Scene.v().getSootClass("java.lang.Short");
        } else if (primitiveType == LongType.v()) {
            return Scene.v().getSootClass("java.lang.Long");
        } else if (primitiveType == FloatType.v()) {
            return Scene.v().getSootClass("java.lang.Float");
        } else if (primitiveType == DoubleType.v()) {
            return Scene.v().getSootClass("java.lang.Double");
        } else if (primitiveType == CharType.v()) {
            return Scene.v().getSootClass("java.lang.Character");
        } else if (primitiveType == BooleanType.v()) {
            return Scene.v().getSootClass("java.lang.Boolean");
        } else {
            throw new IllegalArgumentException("Provided type is not a primitive type");
        }
    }

    public static boolean isPrimitive(Type type) {
        return type instanceof PrimType;
        /*
        Collection<Type> primitives = Arrays.asList(new Type[]{
                IntType.v(),
                ByteType.v(),
                ShortType.v(),
                LongType.v(),
                FloatType.v(),
                FloatType.v(),
                DoubleType.v(),
                CharType.v(),
                BooleanType.v()
        });
        return primitives.contains(type); */
    }

    /*
    private List<Local> getUsedLocals(Body b) {
        UnitGraph g = new ExceptionalUnitGraph(b);
        CombinedDUAnalysis combinedDUAnalysis = new CombinedDUAnalysis(g);
        Set<Local> localSet = new HashSet<>();

        for (Unit u : b.getUnits()) {
            List<UnitValueBoxPair> usesOf = combinedDUAnalysis.getUsesOf(u);
            for (UnitValueBoxPair vbp : usesOf) {
                assert vbp.getValueBox().getValue() instanceof Local;
                localSet.add((Local) vbp.getValueBox().getValue());
            }
        }
        return new ArrayList<>(localSet);
    }*/

    // Make a class and its outer classes public
    public static void makePublic(SootClass aClass) {
        if(aClass.hasOuterClass())
            makePublic(aClass.getOuterClass());

        int modifier = aClass.getModifiers();
        if (aClass.isPrivate())
            modifier ^= Modifier.PRIVATE;
        if (aClass.isProtected())
            modifier ^= Modifier.PROTECTED;
        modifier |= Modifier.PUBLIC;
        if (modifier != aClass.getModifiers())
            aClass.setModifiers(modifier);
    }

    // Unset modifiers and make public
    public static void makePublic(ClassMember member) {
        makePublic(member.getDeclaringClass());
        int modifier = member.getModifiers();
        if (member.isPrivate())
            modifier ^= Modifier.PRIVATE;
        if (member.isProtected())
            modifier ^= Modifier.PROTECTED;
        modifier |= Modifier.PUBLIC;
        if (modifier != member.getModifiers())
            member.setModifiers(modifier);
    }
}
