import beef.GetBeef;

public class SpecialInvokeTest {
    public static void main (String[] args) {
        // Calling private static methods is apparently not a problem during runtime. With instances, however,
        // a VerifyError occurs: Illegal use of nonvirtual function call
        SpecialInvokeTest instance = new SpecialInvokeTest();
        int i = 0;
        if(GetBeef.getBeef() == 0xbeef)
            i = instance.privateMethod() + 1;
        System.out.println(i);
    }

    private int privateMethod() {
        BaseClass instance = new SubClass();
        instance.test();
        instance.callPrivateConstructor();
        return 1;
    }

    public class BaseClass {
        public BaseClass() {}
        private BaseClass(int a) {
            System.out.println("Hello from private constructor in BaseClass");
        }
        public void test() {
            System.out.println("Hello from base class");
        }

        public void callPrivateConstructor() {
            if(GetBeef.getBeef() == 0xbeef) {
                // Should cause java.lang.IllegalAccessError if constructor is private and not handled correctly
                BaseClass instance = new BaseClass(1);
            }
        }
    }
    public class SubClass extends BaseClass {
        @Override
        public void test() {
            System.out.println("Hello from SubClass");
            if(GetBeef.getBeef() == 0xbeef)
                super.test();
        }
    }
}
