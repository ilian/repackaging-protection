package ch.ethz.ivogels;

import embedded.SDCLoader;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JIdentityStmt;
import soot.jimple.internal.StmtBox;
import soot.options.Options;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.util.Chain;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static ch.ethz.ivogels.Main.dexEncryptionEnabled;
import static ch.ethz.ivogels.Main.nativeEnabled;
import static ch.ethz.ivogels.SootUtils.*;
import static ch.ethz.ivogels.SootUtils.primitiveToBoxedClass;
import static ch.ethz.ivogels.SootUtils.unboxPrimitive;

public class SDC {
    private Body body;
    private IfStmt u;
    private ConditionExpr conditionExpr;
    private PatchingChain<Unit> units;
    private Unit start; // First unit inside if block
    private Unit end; // Last unit inside if block // MAJOR TODO: Not last unit in if block when expression is last unit

    private Object constant; // Non-hashed constant
    private Local compareLocal; // Non-hashed local variable
    private Local wrappedCompareLocal; // Non-hashed non-primitive local variable

    // After extraction
    private SootClass extractedClass;
    private List<Unit> jumpTargets;
    /**
     *  List of locals used inside the SDC block. Locals are passed as arguments in the same order to main()
     */

    private List<Local> usedLocals;

    /**
     * Non-hashed local used inside the SDC block. Used as a key to decrypt native code.
     */
    //private Local compareLocalSDC;

    public SDC(Body body, IfStmt ifStmt) {
        this.body = body;
        this.u = ifStmt;
        this.units = body.getUnits();
        this.start = units.getSuccOf(ifStmt);
        this.end = ifStmt.getTarget();

        assert ifStmt.getCondition() instanceof ConditionExpr;
        conditionExpr = (ConditionExpr) ifStmt.getCondition();
        Value op1 = conditionExpr.getOp1();
        Value op2 = conditionExpr.getOp2();
        System.out.println("Candidate found! " + op1 + " " + op2);
        Value constantValue; // Non-hashed constant
        if (op1 instanceof Local) {
            compareLocal = (Local) op1;
            constantValue = op2;
        } else {
            compareLocal = (Local) op2;
            constantValue = op1;
        }
        constant = getConstantValue(constantValue);
        Stats.logConst(constant);
    }

    private static Object getConstantValue(Value value) {
        Object constantValue = null;
        if (value instanceof StringConstant) {
            constantValue = ((StringConstant) value).value;
        } else if (value instanceof ClassConstant) {
            // TODO: More robust?
            // Ignore first character L and last ; https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.2
            String classFieldType = ((ClassConstant) value).value;
            constantValue = classFieldType.substring(1, classFieldType.length() - 1).replace('/', '.');
        } else if (value instanceof IntConstant) {
            constantValue = ((IntConstant) value).value;
        }
        assert constantValue != null;
        return constantValue;
    }

    public void transformIfStatement() {
        Unit startElseForAssert = u.getTarget();

        for (Iterator<Trap> it1 = body.getTraps().snapshotIterator(); it1.hasNext(); ) {
            Trap t = it1.next();
            if (isBetween(units, t.getBeginUnit(), start, end) ||
                    isBetween(units, t.getEndUnit(), start, end) ||
                    isBetween(units, t.getHandlerUnit(), start, end)) {
                System.err.println("Trap unit or handler is inside if body. Skipping!");
                return;
            }
        }

        if(Main.transformOnlyIfStatement()) {
            try {
                replaceWithHashedComparison();
                return;
            } catch (IllegalStateException e) {
                System.err.println("Skipping failed condition substitution for method " + body.getMethod().getName() + "\n" +
                        "Reason: " + e.getMessage());
                e.printStackTrace();
                return;
            }
        }

        try {
            extractIfBody();
        } catch (Exception e) {
            System.err.println("Skipping failed body extraction in method " + body.getMethod().getName() + "\n" +
                    "Reason: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        try {
            replaceWithHashedComparison(); // TODO: Firefox stackoverflow when replacing hashed comparison before block extraction
        } catch (IllegalStateException e) {
            System.err.println("Skipping failed condition substitution for method " + body.getMethod().getName() + "\n" +
                    "Reason: " + e.getMessage());
            e.printStackTrace();
            return;
        }



        SootMethod ifBody = extractedClass.getMethods().stream().filter(m -> m.getName().equals("main"))
                .findFirst().orElseThrow(() ->
                        new IllegalStateException("ifBody does not contain the newly created method!"));

        if(Main.nativeEnabled()) {
            addNativeCheck(ifBody);
        }

        // Remove traps
        // TODO: Needs testing
        for (Iterator<Trap> it1 = body.getTraps().snapshotIterator(); it1.hasNext(); ) {
            Trap t = it1.next();
            if (isBetween(units, t.getBeginUnit(), start, end) &&
                    isBetween(units, t.getEndUnit(), start, end) &&
                    isBetween(units, t.getHandlerUnit(), start, end)) {
                body.getTraps().remove(t);
            }
        }

        // Remove original, unencrypted statements
        removeStatements(units, start, end);

        // Pass locals as arguments to newly created static function
        List<Local> args = new ArrayList<>();

        // No InMemoryDexClassLoader testing yet
        if(!dexEncryptionEnabled()) {
            // synchronized block prevents java.lang.NullPointerException
            //     at soot.util.HashChain.addLast(HashChain.java:472)
            // Probably some internal race condition in Soot
            synchronized (Options.v()) {
                // Add class to transformed output
                Scene.v().addClass(extractedClass);
                extractedClass.setApplicationClass();
            }
        }

        for (Local paramLocal : getParameterLocals(ifBody)) {
            Local arg = body.getLocals().stream()
                    .filter(l -> l.getName().equals(paramLocal.getName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Failed to find local that matches argument of extracted method"));
            // Make sure the types of the matched variables with the same name match
            assert paramLocal.getType() == arg.getType();
            if (Main.dexEncryptionEnabled()) {
                // TODO: currently setting primitives to 0. We did not have to anything w/o dynamic loading
                // Wrap primitives, as we put them in an Object[]
                args.add(isPrimitive(arg.getType()) ? SootUtils.boxPrimitive(body, u, useLocal(body, arg.getName(), arg.getType(), InitType.AsNull)) : arg);
            } else {
                args.add(arg);
            }
        }

        /* Prevent "Accessing value from uninitialized register" errors */

        // We initialize all uninitialized locals to null after IdentityStmts
        for (Local l : args) {
            useLocal(body, l.getName(), l.getType(), InitType.AsNull, -1);
        }

        // Sanity check after if body substitution
        body.validate();

        /* Post-encrypted block logic */

        SootClass resultWrapperClass = Scene.v().getSootClass("embedded.ResultWrapper");

        // Invoke our newly created static method with all the required arguments
        Local resultWrapperLocal = useLocal(body, resultWrapperClass.getType(), InitType.AsNull);
        // Variables that hold the return value received from the result wrapper
        Value assignResultExpr;
        if (Main.dexEncryptionEnabled()) {
            SootMethod decryptAndLoadClassMethod = Scene.v().getSootClass("embedded.SDCLoader")
                    .getMethod("embedded.ResultWrapper decryptAndInvokeMain(java.lang.String,byte[],java.lang.Object,java.lang.Object[])");

            DexBytesPrinter dp = new DexBytesPrinter();
            dp.add(extractedClass);
            byte[] classPayload = dp.printAsByteArray();

            try {
                classPayload = SDCLoader.encrypt(SDCLoader.genKey(this.extractedClass.getName(), constant), classPayload);
            } catch (Exception e) {
                System.err.println("Error in encrypting block!");
                e.printStackTrace();
                System.exit(1);
            }

            // TODO: Extract as method: createStaticArray
            // byte[] classPayloadLocal = new byte[] { 0x64 0x65 0x78 0x0a 0x30 0x33 0x38 0x00 ... }
            Local classPayloadLocal = useLocal(body, ArrayType.v(ByteType.v(), 1), InitType.NoInit);
            body.getUnits().insertBefore(Jimple.v().newAssignStmt(classPayloadLocal,
                    Jimple.v().newNewArrayExpr(ByteType.v(), IntConstant.v(classPayload.length))), u);

            for (int i = 0; i < classPayload.length; i++) {
                body.getUnits().insertBefore(Jimple.v().newAssignStmt(Jimple.v().newArrayRef(classPayloadLocal, IntConstant.v(i)),
                        IntConstant.v(classPayload[i])), u);
            }

            Local classNameLocal = useLocal(body, RefType.v("java.lang.String"), InitType.NoInit);
            body.getUnits().insertBefore(Jimple.v().newAssignStmt(classNameLocal, StringConstant.v(extractedClass.getName())), u);

            // TODO: Extract declare jimple array
            Local objectArgArrayLocal = useLocal(body, ArrayType.v(Scene.v().getObjectType(), 1), InitType.NoInit);
            body.getUnits().insertBefore(Jimple.v().newAssignStmt(objectArgArrayLocal,
                    Jimple.v().newNewArrayExpr(Scene.v().getObjectType(), IntConstant.v(args.size()))), u);
            for (int i = 0; i < args.size(); i++) {
                body.getUnits().insertBefore(Jimple.v().newAssignStmt(Jimple.v().newArrayRef(objectArgArrayLocal, IntConstant.v(i)),
                        args.get(i)), u);
            }

            assignResultExpr = Jimple.v().newStaticInvokeExpr(decryptAndLoadClassMethod.makeRef(), classNameLocal,
                    classPayloadLocal, wrappedCompareLocal, objectArgArrayLocal);
        } else {
            assignResultExpr = Jimple.v().newStaticInvokeExpr(ifBody.makeRef(), args);
        }

        // $resultWrapperLocal = extractedBody(args...);
        units.insertBeforeNoRedirect(Jimple.v().newAssignStmt(resultWrapperLocal, assignResultExpr), end);

        // Sanity check after invoking extracted method with arguments
        body.validate();


        /*
         * We insert the following code to handle the result of the invoked code block
         *
         *     $jumpTarget = $r1.<embedded.ResultWrapper: int jumpTarget>;
         *     if $jumpTarget != -1 goto restorePrimitives;
         *     <handle return statements>
         * restorePrimitives:
         *     <restore code if wrapped primitives is not empty>
         *     if $jumpTarget == -2 goto end;
         *     <tableSwitch logic>
         *
         */

        // Receive jump target
        Local jumpTargetLocal = useLocal(body, null, IntType.v(), InitType.NoInit); // TODO: Reuse jump target local if already present
        // $jumpTarget = $resultWrapperLocal.jumpTarget
        units.insertBeforeNoRedirect(Jimple.v().newAssignStmt(jumpTargetLocal,
                Jimple.v().newInstanceFieldRef(resultWrapperLocal, resultWrapperClass.getFieldByName("jumpTarget").makeRef())),
                end);

        // Declare jump boxes (end already present)
        StmtBox returnHandlerBox = new StmtBox(Jimple.v().newNopStmt());
        StmtBox afterReturnHandlerBox = new StmtBox(Jimple.v().newNopStmt()); // Either start of retorePrimitives or if $jumpTarget == -2 goto end

        // if $jumpTarget != -1 goto restorePrimitives;
        units.insertBeforeNoRedirect(Jimple.v().newIfStmt(Jimple.v().newNeExpr(jumpTargetLocal, IntConstant.v(-1)), afterReturnHandlerBox), end);


        if (!body.getMethod().getReturnType().equals(VoidType.v())) {
            Local returnLocal = useLocal(body, Scene.v().getObjectType(), InitType.AsNull);

            Unit receiveResultStmt = Jimple.v().newAssignStmt(returnLocal,
                    Jimple.v().newInstanceFieldRef(resultWrapperLocal, resultWrapperClass.getFieldByName("returnValue").makeRef()));
            returnHandlerBox.setUnit(receiveResultStmt); // Set unit as return handler
            units.insertBeforeNoRedirect(receiveResultStmt, end);

            if (isPrimitive(body.getMethod().getReturnType())) {
                Local returnLocalBoxedCasted = useLocal(body, primitiveToBoxedClass(body.getMethod().getReturnType()).getType(), InitType.AsNull);
                Unit castResultStmt = Jimple.v().newAssignStmt(returnLocalBoxedCasted,
                        Jimple.v().newCastExpr(returnLocal, returnLocalBoxedCasted.getType()));
                units.insertBeforeNoRedirect(castResultStmt, end);
                // We need to unbox from the object to a primitive
                Unit returnWrappedResultStmt = Jimple.v().newReturnStmt(
                        unboxPrimitive(body, end, returnLocalBoxedCasted, body.getMethod().getReturnType())
                );
                units.insertBeforeNoRedirect(returnWrappedResultStmt, end);
            } else {
                Local returnLocalCasted = useLocal(body, body.getMethod().getReturnType(), InitType.AsNull);
                Unit castResultStmt = Jimple.v().newAssignStmt(returnLocalCasted,
                        Jimple.v().newCastExpr(returnLocal, returnLocalCasted.getType()));
                units.insertBeforeNoRedirect(castResultStmt, end);
                Unit returnWrappedResultStmt = Jimple.v().newReturnStmt(returnLocalCasted);
                units.insertBeforeNoRedirect(returnWrappedResultStmt, end);
            }
        } else {
            Unit returnVoidStmt = Jimple.v().newReturnVoidStmt();
            returnHandlerBox.setUnit(returnVoidStmt); // Set unit as return handler
            units.insertBeforeNoRedirect(returnVoidStmt, end);
        }

        // Sanity check after handling return value
        //body.validate();

        // List of primitives (of extracted body, not this body) that are wrapped
        if (!usedLocals.isEmpty()) {
            Local savedObjsArrayLocal =
                    useLocal(body, ArrayType.v(Scene.v().getObjectType(), 1), InitType.AsNull);
            // $savedObjsArrayLocal = $resultWrapperLocal.savedPrimitivesAndReferences;
            Unit receiveResultStmt = Jimple.v().newAssignStmt(savedObjsArrayLocal,
                    Jimple.v().newInstanceFieldRef(resultWrapperLocal, resultWrapperClass.getFieldByName("savedPrimitivesAndReferences").makeRef()));
            afterReturnHandlerBox.setUnit(receiveResultStmt);
            units.insertBeforeNoRedirect(receiveResultStmt, end);

            int i = 0;
            for (Local otherBodySavedObj : usedLocals) {
                Local localMatchingSavedObj = body.getLocals().stream().filter(l -> l.getName().equals(otherBodySavedObj.getName()))
                        .findFirst().orElseThrow(() ->
                                new IllegalStateException("No local found that matches the saved primitive or reference"));
                assert localMatchingSavedObj.getType() == otherBodySavedObj.getType();

                // TODO: Declare temp per possible type instead of per local as an optimization

                Type targetType = localMatchingSavedObj.getType();

                Local savedObj = useLocal(body, Scene.v().getObjectType(), InitType.AsNull);
                // savedObj = $savedObjsArrayLocal[i];
                units.insertBeforeNoRedirect(Jimple.v().newAssignStmt(
                        savedObj, Jimple.v().newArrayRef(savedObjsArrayLocal, IntConstant.v(i++))
                ), end);

                if (isPrimitive(targetType)) {
                    // We need to unbox and restore a primitive

                    SootClass wrapperClass = primitiveToBoxedClass(targetType);
                    Local wrapperLocalCasted = useLocal(body, wrapperClass.getType(), InitType.AsNull);
                    // $wrapperLocalCasted = (WrapperType) savedObj
                    units.insertBeforeNoRedirect(Jimple.v().newAssignStmt(
                            wrapperLocalCasted,
                            Jimple.v().newCastExpr(savedObj, wrapperClass.getType())
                    ), end);

                    // localMatchingSavedObj = $wrapperLocalCasted.<unwrapMethod>
                    units.insertBeforeNoRedirect(Jimple.v().newAssignStmt(localMatchingSavedObj,
                            Jimple.v().newVirtualInvokeExpr(wrapperLocalCasted,
                                    wrapperClass.getMethod(String.format("%1$s %1$sValue()", targetType.toString())).makeRef())
                    ), end);
                } else {
                    // We need to restore an object reference

                    // $localMatchingSavedObj = (TargetType) savedObj
                    units.insertBeforeNoRedirect(Jimple.v().newAssignStmt(
                            localMatchingSavedObj,
                            Jimple.v().newCastExpr(savedObj, targetType)
                    ), end);
                }

            }
        }

        Stmt jumpToEndIfNotJumping = Jimple.v().newIfStmt(Jimple.v().newEqExpr(jumpTargetLocal, IntConstant.v(-2)), end);
        if (usedLocals.isEmpty()) {
            afterReturnHandlerBox.setUnit(jumpToEndIfNotJumping);
        }
        units.insertBeforeNoRedirect(jumpToEndIfNotJumping, end);

        if (!jumpTargets.isEmpty()) {
            // Default: Throw IllegalStateException
            // Create exception local if not already present in body
            SootClass gotoExceptionClass = Scene.v().getSootClass("java.lang.IllegalStateException");
            Optional<Local> gotoExceptionOpt = body.getLocals().stream().filter(l -> l.getName().equals("gotoException")).findFirst();

            // We only init during runtime if needed
            Local gotoException = useLocal(body, "gotoException", gotoExceptionClass.getType(), InitType.AsNull);

            List<Stmt> exceptionStmts = Arrays.asList(
                    // $gotoException = new java.lang.IllegalStateException;
                    Jimple.v().newAssignStmt(gotoException, Jimple.v().newNewExpr(gotoExceptionClass.getType())),

                    // specialinvoke $gotoException.<java.lang.IllegalStateException: void <init>(java.lang.String)>(...);
                    Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr(gotoException,
                            gotoExceptionClass.getMethod("void <init>(java.lang.String)").makeRef(),
                            StringConstant.v("[ResultWrapper] Tried to jump to an unknown taget"))),

                    // throw $gotoException
                    Jimple.v().newThrowStmt(gotoException)
            );

            Stmt tableSwitchStmt = Jimple.v().newTableSwitchStmt(jumpTargetLocal, 0, jumpTargets.size() - 1, jumpTargets, exceptionStmts.get(0));

            /*
             * Alternative: Put exceptionStmts after tableswitch without a goto in front
             * The implementation currently inserts the following statements (probably the safest way)
             *
             *     goto switch
             *     throw new IllegalStateException(...);
             * switch:
             *     tableswitch($jumpTarget)
             *     ...
             */
            units.insertBeforeNoRedirect(Jimple.v().newGotoStmt(tableSwitchStmt), end);
            exceptionStmts.forEach(s -> units.insertBeforeNoRedirect(s, end));
            units.insertBeforeNoRedirect(tableSwitchStmt, end);

        }

        // Sanity check after handling jump target of ResultWrapper
        body.validate();

        assert startElseForAssert == u.getTarget();
    }

    private void addNativeCheck(SootMethod SDCMethod) {

        assert extractedClass.getMethods().contains(SDCMethod);

        Body SDCBody = SDCMethod.getActiveBody();

        Unit unitAfterIdentityStmts = null;
        for (Iterator it = SDCBody.getUnits().snapshotIterator(); it.hasNext(); ) {
            unitAfterIdentityStmts = (Unit) it.next();
            if (!(unitAfterIdentityStmts instanceof JIdentityStmt)) {
                break;
            }
        }

        assert unitAfterIdentityStmts != null;

        if(nativeEnabled()) {
            // We can generate a random encryption key and pass it directly as an argument to the native function, as the
            // encryption key is encrypted inside the SDC block
            // We are using AES128, so we'll need a 16 byte key
            final byte[] key = new byte[16];
            ThreadLocalRandom.current().nextBytes(key);

            Local keyLocal = useLocal(SDCBody, ArrayType.v(ByteType.v(), 1), InitType.AsNull);
            SDCBody.getUnits().insertBefore(Jimple.v().newAssignStmt(keyLocal,
                    Jimple.v().newNewArrayExpr(ByteType.v(), IntConstant.v(key.length))), unitAfterIdentityStmts);

            for (int i = 0; i < key.length; i++) {
                SDCBody.getUnits().insertBefore(Jimple.v().newAssignStmt(Jimple.v().newArrayRef(keyLocal, IntConstant.v(i)),
                        IntConstant.v(key[i])), unitAfterIdentityStmts);
            }

            NativeUtils.addCheckRoutine(extractedClass, key, keyLocal);
        }
    }

    /* We make a new method body which only contains the Locals required to complete the execution of the if block */
    private void extractIfBody() {
        assert extractedClass == null && jumpTargets == null && usedLocals == null;

        // Check if jump target (unit outside if body for JNEExpr) comes after if statement instead of pointing backwards
        if(!units.follows(end, u)) {
            throw new IllegalArgumentException("If condition jumps backwards. Can't handle this yet!");
        }

        UnitGraph ug = new ExceptionalUnitGraph(body);
        for (Iterator<Unit> it = units.iterator(start, end); it.hasNext(); ) {
            Unit u = it.next();

            // Iterator includes end, but we want to iterate until (not including) end
            if(u.equals(end))
                break;

            // Check if boxes pointing to u are not from outside the if block
            List<Unit> unitsPointingToU = ug.getPredsOf(u);
            unitsPointingToU.remove(units.getPredOf(u)); // We are not interested in predecessors in program text
            for (Unit jumpSource: unitsPointingToU) {
                if(!isBetween(units, jumpSource, start, end)) {
                    throw new IllegalArgumentException("Can't handle pointing from outside if body yet!");
                }
            }

            if(!body.getTraps().isEmpty())
                throw new IllegalArgumentException("Can't handle traps yet!");
        }

        // We need to allocate a separate class so we can load it during runtime
        // Illegal characters: !<>
        // Dots signify other package name. Package name needs to be the same to avoid IllegalAccessErrors when accessing
        // methods with default access modifiers
        String classname = body.getMethod().getDeclaringClass() + "_" +
                body.getMethod().getName().replace("<", "").replace(">","") + "_" +
                UUID.randomUUID().toString();
        this.extractedClass = new SootClass(classname, Modifier.PUBLIC);
        assert body.getMethod().getDeclaringClass().getPackageName().equals(extractedClass.getPackageName());

        Body extractedBody = (Body) body.clone(); // Deep copy body (also creates new local instances of the same type)
        PatchingChain<Unit> extractedBodyUnits = extractedBody.getUnits();
        List<Object> origBodyUnitsList = Arrays.asList(units.toArray());
        int startIdx = origBodyUnitsList.indexOf(start);
        int endIdx = origBodyUnitsList.indexOf(end);

        /* Remove statements outside if block */
        for(int i = 0; i < startIdx; i++)
            extractedBodyUnits.removeFirst();
        for(int i = units.size() - 1; i >= endIdx; i--)
            extractedBodyUnits.removeLast();


        /* Copy locals to extracted if body */
        Chain<Local> localChain = extractedBody.getLocals();
        Set<Local> usedLocalsSet = SootUtils.getUsedLocals(extractedBody);
        /*
        // We need the unhashed local to be into the scope inside the SDC block
        // Actually no... Let's just pass a random encryption key as an argument, since we're already inside an encrypted block :)
        if(usedLocalsSet.stream().noneMatch(l -> l.getName().equals(compareLocal.getName()))) {
            usedLocalsSet.add(useLocal(extractedBody, compareLocal.getName(),compareLocal.getType(), InitType.NoInit));
        } */
        this.usedLocals = new ArrayList<>(usedLocalsSet);
        localChain.clear();
        localChain.addAll(usedLocals);
        int i = 0;
        List<Type> parameterTypes = new ArrayList<>();
        for(Local l : usedLocals) {
            extractedBodyUnits.addFirst(Jimple.v().newIdentityStmt(l, Jimple.v().newParameterRef(l.getType(), i++)));
            parameterTypes.add(l.getType());
        }

        /* Wrap goto and return statements */
        jumpTargets = new ArrayList<>();
        Iterator<Unit> extractedIter = extractedBodyUnits.snapshotIterator();
        boolean movedForward = true;

        SootClass resultWrapperClass = Scene.v().getSootClass("embedded.ResultWrapper");
        RefType resultWrapperType = resultWrapperClass.getType();
        Local wrappedResultLocal = useLocal(extractedBody, "wrappedResult", resultWrapperType, InitType.AsNew);
        SootMethodRef jumpCtorRef = resultWrapperClass.getMethod("void <init>(int,java.lang.Object[])").makeRef();
        SootMethodRef objCtorRef = resultWrapperClass.getMethod("void <init>(java.lang.Object)").makeRef();
        SootMethodRef nopCtorRef = resultWrapperClass.getMethod("void <init>(java.lang.Object[])").makeRef();

        SootMethod restorePrimitiveMethod;
        Local wrappedPrimitivesArrayLocal = null;
        Stmt assignWrappedPrimitivesArray = null;
        // TODO: Restoring primitives on return statement not needed
        if(!usedLocals.isEmpty()) {
            restorePrimitiveMethod = generateRestoreMethod(usedLocals);
            extractedClass.addMethod(restorePrimitiveMethod);
            // TODO: reuse using constant name --> java.lang.RuntimeException: Chain already contains object: wrappedPrimitivesArray = ...
            wrappedPrimitivesArrayLocal = useLocal(extractedBody, "wrappedPrimitivesArray",
                    ArrayType.v(Scene.v().getSootClass("java.lang.Object").getType(), 1), InitType.AsNew, usedLocals.size());
            assignWrappedPrimitivesArray = Jimple.v().newAssignStmt(wrappedPrimitivesArrayLocal,
                    Jimple.v().newStaticInvokeExpr(restorePrimitiveMethod.makeRef(), usedLocals));
        }


        for(Iterator<Unit> origIter = body.getUnits().iterator(start, end); extractedIter.hasNext(); ) {
            Stmt extracted = (Stmt)extractedIter.next();
            Stmt orig = (Stmt)origIter.next();

            // Iterator includes end, but we want to iterate until (not including) end
            if(orig.equals(end))
                break;

            // Move pointer forward s.t. orig and extracted point to the same instruction
            while (movedForward && extracted instanceof IdentityStmt)
                extracted = (Stmt) extractedIter.next();
            movedForward = false;

            // Can't compare strings, since Goto of extracted contains jump boxes to itself instead (since target can
            // outside if block).
            assert extracted.getClass().equals(orig.getClass());

            // Make inaccessible fields from extracted class public. Needed for e.g. getting this$0 for nested classes
            if(orig.containsFieldRef()) {
                FieldRef fieldRef = orig.getFieldRef();
                makePublic(fieldRef.getField());
            }

            // Wrap gotos as a ResultWrapper if they point outside of the encrypted if block
            if (orig instanceof GotoStmt && !isBetween(units, ((GotoStmt) orig).getTarget(), start, end)) {
                /*
                 * Replace goto statement with
                 * return new WrappedResult(n, a); // with n jump target number, a wrapped primitives array
                 */
                GotoStmt s = (GotoStmt) orig;
                jumpTargets.add(s.getTarget()); // Add target in original body to the list of jump targets

                List<Value> args = new ArrayList<>(Collections.singletonList(IntConstant.v(jumpTargets.size() - 1)));
                if(wrappedPrimitivesArrayLocal != null) {
                    args.add(wrappedPrimitivesArrayLocal);
                    // We use the same assignWrappedPrimitivesArray unit elsewhere, so we insert a copy here with clone()
                    extractedBodyUnits.insertBefore((Unit) assignWrappedPrimitivesArray.clone(), extracted);
                } else {
                    args.add(NullConstant.v());
                }
                extractedBodyUnits.insertBefore(
                        Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr(wrappedResultLocal, jumpCtorRef, args))
                        , extracted);
                extractedBodyUnits.insertBeforeNoRedirect(Jimple.v().newReturnStmt(wrappedResultLocal), extracted);
                extractedBodyUnits.remove(extracted);
            } else if (orig instanceof IfStmt && !isBetween(units, ((IfStmt) orig).getTarget(), start, end)) {
                /*
                 * Replace if statement with
                 *     goto label2;
                 * label1:
                 *     return new WrappedResult(n, a); // with n jump target number, a wrapped primitives array
                 * label2:
                 *     if <condition> goto label1;
                 */
                IfStmt s = (IfStmt) orig;
                jumpTargets.add(s.getTarget());

                // goto label2;
                extractedBodyUnits.insertBeforeNoRedirect(Jimple.v().newGotoStmt(extracted), extracted);

                // label1: ...
                Unit startLabel1 = null;
                List<Value> args = new ArrayList<>(Collections.singletonList(IntConstant.v(jumpTargets.size() - 1)));
                if(wrappedPrimitivesArrayLocal != null) {
                    args.add(wrappedPrimitivesArrayLocal);
                    // We use the same assignWrappedPrimitivesArray unit elsewhere, so we insert a copy here with clone()
                    Unit assignArray = (Unit) assignWrappedPrimitivesArray.clone();
                    startLabel1 = assignArray;
                    extractedBodyUnits.insertBeforeNoRedirect(assignArray, extracted);
                } else {
                    args.add(NullConstant.v());
                }
                Unit returnStmt = Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr(wrappedResultLocal, jumpCtorRef, args));
                if(startLabel1 == null)
                    startLabel1 = returnStmt;
                extractedBodyUnits.insertBeforeNoRedirect(returnStmt, extracted);
                extractedBodyUnits.insertBeforeNoRedirect(Jimple.v().newReturnStmt(wrappedResultLocal), extracted);

                ((IfStmt)extracted).setTarget(startLabel1);

            } else if (orig instanceof ReturnStmt || orig instanceof ReturnVoidStmt) {
                /*
                 * Replace return statement with
                 * return new WrappedResult(returnObject);
                 * If orig is of type ReturnVoidStmt, returnObject is set to null.
                 * The caller will notice that the return type of the caller function is void, and will ignore returnObject.
                 */

                Value wrappedValue = NullConstant.v();
                if(orig instanceof ReturnStmt) {
                    Value returnValue = ((ReturnStmt) extracted).getOp();
                    if(isPrimitive(returnValue.getType())) {
                        wrappedValue = boxPrimitive(extractedBody, extracted, returnValue, body.getMethod().getReturnType());
                    } else {
                        wrappedValue = returnValue;
                    }
                }
                List<Value> args = Collections.singletonList(wrappedValue);
                extractedBodyUnits.insertBefore(
                        Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr(wrappedResultLocal, objCtorRef, args))
                        , extracted);

                extractedBodyUnits.insertBeforeNoRedirect(Jimple.v().newReturnStmt(wrappedResultLocal), extracted);
                extractedBodyUnits.remove(extracted);
            } else if(orig instanceof SwitchStmt) {
                throw new IllegalArgumentException("Can't handle switch statements yet!");
            } else if(orig.containsInvokeExpr() && (orig.getInvokeExpr() instanceof VirtualInvokeExpr || orig.getInvokeExpr() instanceof InterfaceInvokeExpr)) {
                // private methods are always called with invokespecial bytecode instructions
                assert !orig.getInvokeExpr().getMethod().isPrivate();

                // Set method and its class & outer classes public
                makePublic(orig.getInvokeExpr().getMethod());

            } else if (orig.containsInvokeExpr() && orig.getInvokeExpr() instanceof StaticInvokeExpr) {
                // Make classes related to method invocation public.
                // This is also needed for JNI's env->FindClass (which is called only for static methods)
                makePublic(orig.getInvokeExpr().getMethod());
            } else if(orig.containsInvokeExpr() && orig.getInvokeExpr() instanceof SpecialInvokeExpr) {
                // Set required modifiers in order to call e.g. private functions from the extracted class
                // SpecialInvokeExpr: Invoke instance method; special handling for superclass, private, and instance initialization method invocations
                ValueBox invokeExprBox = extracted.getInvokeExprBox();
                SpecialInvokeExpr specialInvokeExpr = (SpecialInvokeExpr)invokeExprBox.getValue();
                SootMethod method = specialInvokeExpr.getMethod();
                Local instance = (Local) specialInvokeExpr.getBase();

                // if super.<method> has been called
                if(body.getMethod().getDeclaringClass().getSuperclass() == method.getDeclaringClass()) {
                    throw new IllegalArgumentException("Can't handle super method call. Potential solution: MethodHandle");
                }
                if(!method.isConstructor())
                    throw new IllegalArgumentException("TODO: invoke-direct converted to invoke-virtual in dex files...");

                makePublic(method);

                /*
                // Keep SpecialInvoke for constructors
                if(!method.isConstructor())
                    invokeExprBox.setValue(Jimple.v().newVirtualInvokeExpr(instance, method.makeRef(), specialInvokeExpr.getArgs())); */
            } else if (orig.branches() && !(orig instanceof GotoStmt || orig instanceof IfStmt)) {
                // We need to handle this type of unit as it may jump outside if block
                assert false;
            }

            // Jump table targets should point to units in original method
            assert jumpTargets.stream().allMatch(units::contains);
        }

        Value arg;
        if(wrappedPrimitivesArrayLocal != null) {
            arg = wrappedPrimitivesArrayLocal;
            extractedBodyUnits.addLast(assignWrappedPrimitivesArray);
        } else {
            arg = NullConstant.v();
        }

        extractedBodyUnits.addLast(
                Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr(wrappedResultLocal, nopCtorRef, arg)));
        extractedBodyUnits.addLast(Jimple.v().newReturnStmt(wrappedResultLocal));

        // Create a new method that receives all locals as method parameters
        SootMethod method = new SootMethod("main", parameterTypes, resultWrapperType, Modifier.PUBLIC | Modifier.STATIC);
        method.setActiveBody(extractedBody);

        extractedClass.addMethod(method);
        // We would set the same superclass for the extracted method so we could access protected methods and fields
        // BUT: Loading a class from a different class loader does not allow access to protected fields.
        // We need to make protected fields public!
        extractedClass.setSuperclass(Scene.v().getObjectType().getSootClass());
        method.setDeclaringClass(extractedClass);
        method.setDeclared(true);

        // Locals in extracted body should be a copy
        Set<Local> localIntersection = getUsedLocals(body);
        localIntersection.retainAll(getUsedLocals(extractedBody));
        assert localIntersection.isEmpty();

        try {
            extractedBody.validate(); // Verify that we didn't screw up
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to validate extracted if body: " + e.getMessage());
        }
    }


    private void replaceWithHashedComparison() {
        if(!(u.getCondition() instanceof ConditionExpr))
            throw new IllegalArgumentException("No condition expression supplied");

        // TODO: What about already wrapped locals with .equals() comparison?
        if(!isPrimitive(compareLocal.getType())) {
            // TODO: Ignoring all reference type equality with ==
            if(1==1) {
                throw new IllegalStateException("Ignoring reference equality");
            }
            if (compareLocal.getType() == RefType.v("java.lang.Class") || compareLocal.getType() == RefType.v("java.lang.String") ||
                    compareLocal.getType() == RefType.v("java.lang.reflect.Type")) {

                // TODO: $r1 == "id" ?!
                System.out.println("Allowing non-primitive type comparison of type " + compareLocal.getType());
            } else {
                System.out.println("SDC Ignoring constant of type " + compareLocal.getType());
                assert false;
                return;
            }
        }


        // Convert primitives to wrapped locals
        this.wrappedCompareLocal = isPrimitive(compareLocal.getType()) ? boxPrimitive(body, u, compareLocal) : compareLocal;

        /* Compare hash(originalLocal) to hash(constant) */

        // hashedLocal = hash(originalLocal)
        byte[] correctHash = SDCLoader.getHash(constant);
        // TODO: Before refactoring: just declared it using a localgenerator without init
        Local hashedConstant = useLocal(body, ArrayType.v(ByteType.v(), 1), SootUtils.InitType.NoInit);
        body.getUnits().insertBefore(Jimple.v().newAssignStmt(hashedConstant,
                Jimple.v().newNewArrayExpr(ByteType.v(), IntConstant.v(correctHash.length))), u);

        for (int i = 0; i < correctHash.length; i++) {
            body.getUnits().insertBefore(Jimple.v().newAssignStmt(Jimple.v().newArrayRef(hashedConstant, IntConstant.v(i)),
                    IntConstant.v(correctHash[i])), u);
        }

        // TODO: Before refactoring: just declared it using a localgenerator without init
        Local hashMatches = useLocal(body, BooleanType.v(), SootUtils.InitType.NoInit);
        SootMethod getHash = Scene.v().getSootClass("embedded.SDCLoader")
                .getMethod("boolean match(java.lang.Object,byte[])");
        body.getUnits().insertBefore(Jimple.v().newAssignStmt(
                hashMatches, Jimple.v().newStaticInvokeExpr(getHash.makeRef(), wrappedCompareLocal, hashedConstant)
        ), u);


        conditionExpr.setOp1(hashMatches);
        conditionExpr.setOp2(IntConstant.v(1));

        System.out.println("Hash comparison replacement success!");
        // Sanity check after if condition substitution
        body.validate();
    }

    // Generate a SootMethod that returns an array of wrapped primitive objects and modified object references
    private static SootMethod generateRestoreMethod(List<Local> locals) {
        if(locals.isEmpty())
            throw new IllegalArgumentException("Local list is empty. There is no point in restoring an empty set of locals");

        List<Type> parameterTypes = locals.stream().map(Local::getType).collect(Collectors.toList());
        SootClass objectClass = Scene.v().getSootClass("java.lang.Object");
        Local wrappedArray = Jimple.v().newLocal("wrappedArray", ArrayType.v(objectClass.getType(), 1));

        // Declare method: public static Object[] restoreLocals(char p1, int p2, bool p3, double p4, ... /* list of primitives to wrap */);
        SootMethod method = new SootMethod("restoreLocals", parameterTypes, wrappedArray.getType(), Modifier.PUBLIC | Modifier.STATIC);
        Body body = Jimple.v().newBody(method);
        method.setActiveBody(body); // TODO: Required?
        PatchingChain<Unit> units = body.getUnits();

        // Declare locals
        List<Local> methodLocals = locals.stream().map(l -> (Local) l.clone()).collect(Collectors.toList());
        body.getLocals().addAll(methodLocals);
        body.getLocals().add(wrappedArray);

        // Assign args to locals
        int i = 0;
        for(Local l : methodLocals)
            units.addLast(Jimple.v().newIdentityStmt(l, Jimple.v().newParameterRef(l.getType(), i++)));

        // Initialize wrappedArray
        units.addLast(Jimple.v().newAssignStmt(wrappedArray, Jimple.v().newNewArrayExpr(objectClass.getType(), IntConstant.v(locals.size()))));

        // Return array at end of method
        Unit returnStmt = Jimple.v().newReturnStmt(wrappedArray);
        units.addLast(returnStmt);

        // Wrap every primitive type to Object and set as element to array
        i = 0;
        for(Local l : methodLocals){
            Local toAdd;
            if(isPrimitive(l.getType())) {
                toAdd = boxPrimitive(body, returnStmt, l);
            } else {
                toAdd = l;
            }
            units.insertBeforeNoRedirect(Jimple.v().newAssignStmt(
                    Jimple.v().newArrayRef(wrappedArray, IntConstant.v(i++)),
                    toAdd
            ), returnStmt);
        }

        body.validate();

        return method;
    }

    /* Removes all units between start and end (exclusive) from units*/
    private static void removeStatements(PatchingChain<Unit> units, Unit start, Unit end) {
        List<Object> origBodyUnitsList = Arrays.asList(units.toArray());
        int startIdx = origBodyUnitsList.indexOf(start);
        int endIdx = origBodyUnitsList.indexOf(end);
        if(startIdx > endIdx || startIdx == -1 || endIdx == -1)
            throw new IllegalArgumentException();
        Unit curr = start;
        Unit succ = units.getSuccOf(start);
        for(int i = startIdx; i < endIdx; i++) {
            units.remove(curr);
            curr = succ;
            succ = units.getSuccOf(curr); // TODO: Check if succ of last item throws exception
        }
    }

}
