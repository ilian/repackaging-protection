package ch.ethz.ivogels.transformers;

import ch.ethz.ivogels.SDC;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.AbstractBinopExpr;
import soot.jimple.internal.JIfStmt;
import soot.jimple.internal.JNeExpr;
import soot.util.Chain;

import java.util.*;
import static ch.ethz.ivogels.SootUtils.*;

public class SDCBodyTransformer extends BodyTransformer {

    private class IfHierarchy {
        private List<IfHierarchyNode> nodes = new ArrayList<>();

        public void add(Chain<Unit> units, IfStmt stmt) {
            assert stmt.getCondition() instanceof JNeExpr;
            IfHierarchyNode parent = null;
            for(IfHierarchyNode node : nodes) {
                if(parent == null) {
                    try {
                        parent = node.getParent(units, stmt);
                    } catch (IllegalArgumentException e) {
                        System.err.println("Will not add stmt to IfHierarchy: " + e.getMessage());
                        return;
                    }
                } else {
                    throw new IllegalStateException("Found two nodes that contain stmt!");
                }
            }

            if(parent != null) {
                parent.addChild(stmt);
            } else {
                nodes.add(new IfHierarchyNode(stmt));
            }
        }

        public List<IfStmt> getBottomUp() {
            List<IfStmt> res = new ArrayList<>();
            for (IfHierarchyNode node : nodes) {
                res.addAll(node.getBottomUp());
            }
            return res;
        }
    }

    private class IfHierarchyNode {
        private IfStmt stmt;
        private List<IfHierarchyNode> children = new ArrayList<>();

        IfHierarchyNode(IfStmt stmt) {
            this.stmt = stmt;
        }

        public void addChild(IfStmt stmt) {
            this.children.add(new IfHierarchyNode(stmt));
        }

        // Get parent hierarchy node. Note that stmt does not need to be added to the data structure.
        public IfHierarchyNode getParent(Chain<Unit> units, IfStmt stmt) {
            if(isBetween(units, stmt, this.stmt, this.stmt.getTarget())) {
                if(!isBetween(units, units.getPredOf(stmt.getTarget()), this.stmt, this.stmt.getTarget())) {
                    throw new IllegalArgumentException("[stmt, stmt.target) is not contained in [this.stmt, this.stmt.target)");
                }
                for(IfHierarchyNode child : this.children) {
                    IfHierarchyNode parent = child.getParent(units, stmt);
                    if(parent != null)
                        return parent;
                }
                return this;
            } else {
                return null;
            }
        }

        public List<IfStmt> getBottomUp() {
            List<IfStmt> res = new ArrayList<>();
            for (IfHierarchyNode child : children) {
                res.addAll(child.getBottomUp());
            }
            res.add(this.stmt);
            return res;
        }
    }

    @Override
    protected void internalTransform(Body body, String s, Map<String, String> map) {
        //TODO: Add class exclusion list in main?
        if(body.getMethod().getDeclaringClass().getPackageName().startsWith("org.spongycastle.crypto.digests")) {
            System.err.println("[SDCBodyTransformer] Ignoring spongycastle crypto digests due to circular dependence when running SHA-1");
            return;
        }
        PatchingChain<Unit> units = body.getUnits();
        Body DEBUG_COPY = (Body) body.clone();
        String className = body.getMethod().getDeclaringClass().toString();
        System.out.printf("Processing %s.%s\n", className, body.getMethod().toString());

        IfHierarchy ifHierarchy = new IfHierarchy();

        for (Iterator it = units.snapshotIterator(); it.hasNext(); ) {
            Unit u = (Unit) it.next();

            u.apply(new AbstractStmtSwitch() {
                // TODO: String equality with .equals()
                @Override
                public void caseIfStmt(IfStmt stmt) {
                    // We are only interested in code blocks inside if statements
                    // with a condition that compares a variable to a constant
                    Value condition = stmt.getCondition();

                    if(!units.contains(stmt)) {
                        System.err.println("Tried to rewrite if statement of already encrypted block. " +
                                "This should not happen, as we encrypt the innermost blocks first.");
                        return;
                    }

                    //TODO: Figure out if JEqExpr can be useful
                    /* TODO: Handle the following
                              $b1 = $l0 cmp 48879L;
                              if $b1 != 0 goto label1;
                     */
                    if (condition instanceof JNeExpr) {
                        JNeExpr expr = (JNeExpr) condition;
                        if (!isCandidate(expr))
                            return;

                        // Ignore if jump target (unit outside if body for JNEExpr) comes after if statement instead of pointing backwards
                        if(!units.follows(stmt.getTarget(), stmt))
                            return;

                        // Optional TODO: Investigate why javac would generate such bytecode. Might be after optimization phase of 3rd party tool?
                        // Example: nl.qmusic.app in function writeSegmentsReplacingExif
                        // https://commons.apache.org/proper/commons-imaging/jacoco/org.apache.commons.imaging.formats.jpeg.exif/ExifRewriter.java.html
                        // Ignore empty if bodies
                        if(stmt.getTarget() == units.getSuccOf(stmt))
                            return;

                        ifHierarchy.add(units, stmt);
                    }
                }

                @Override
                public void caseAssignStmt(AssignStmt stmt) {
                    // We are interested in string equality
                    if (stmt.getLeftOp().getType() == BooleanType.v() && stmt.getRightOp() instanceof InvokeExpr) {

                    }
                }
            });
        }

        // Visit if statements bottom up
        for (IfStmt stmt : ifHierarchy.getBottomUp()) {
            assert body.getUnits().contains(stmt); // Make sure if statement still exists, and has not been extracted
            (new SDC(body, stmt)).transformIfStatement();
        }

        // Assert no jump to self
        for (Unit u : units) {
            if (u instanceof JIfStmt) {
                assert ((JIfStmt) u).getTarget() != u;
            }
        }

        body.validate();
    }

    // Determines whether or not an expression is usable for self-decrypting code
    private boolean isCandidate(AbstractBinopExpr expr) {
        Value op1 = expr.getOp1();
        Value op2 = expr.getOp2();

        return op1 instanceof Local && isCandidateConstant(op2) ||
                op2 instanceof Local && isCandidateConstant(op1);
    }

    // Constants with enough entropy; those are not easily guessable
    private boolean isCandidateConstant(Value c) {
        // TODO: Can ClassConstant be useful for us? Not too easy to guess?
        // TODO: String equality not with .equals()?
        return c instanceof StringConstant || c instanceof ClassConstant ||
                c instanceof NumericConstant && c.toString().length() > 1;
    }

}
