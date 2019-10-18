package embedded;

import java.util.Arrays;

/**
 *  An instance of this class will be returned by a wrapped if body.
 *  We need to handle 3 different cases:
 *   - Instruction pointer reached end of body. We only need to pass modified primitives/references
 *   - A return statement was reached within the body. We need to pass return value
 *   - A goto statement was reached within the body. We need to pass jump target number and modified primitives/references
 */
public class ResultWrapper {
    public ResultWrapper(Object[] savedPrimitivesAndReferences) {
        this.savedPrimitivesAndReferences = savedPrimitivesAndReferences;
        this.jumpTarget = -2;
    }
    public ResultWrapper(Object returnValue) {
        this.returnValue = returnValue;
        this.jumpTarget = -1;
    }
    public ResultWrapper(int jumpTarget, Object[] savedPrimitivesAndReferences) {
        this.jumpTarget = jumpTarget;
        this.savedPrimitivesAndReferences = savedPrimitivesAndReferences;
        // Note: Calling Arrays.toString(savedPrimitivesAndReferences) can cause crashes since toString() is invoked
        // Example: Firefox throws an exception: https://hg.mozilla.org/mozilla-central/file/3401d063be3f/mobile/android/geckoview/src/main/java/org/mozilla/geckoview/GeckoEditable.java#l1704
    }

    public Object[] savedPrimitivesAndReferences;
    public Object returnValue = null;
    public int jumpTarget; // -1 means return value present, -2 means no return nor jump
}
