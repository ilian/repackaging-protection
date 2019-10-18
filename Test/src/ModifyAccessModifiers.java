import beef.GetBeef;

public class ModifyAccessModifiers {
    public static void main(String[] args) {
        ModifyAccessModifiers instance = new ModifyAccessModifiers();
        instance.test();
    }

    public void test() {
        B b = new B();
        b.test();
    }

    public class A {
        private void a() {
            System.out.println("Hello from A::a");
        }

        public void test() {
            if(GetBeef.getBeef() == 0xbeef) {
                a();
            }
        }
    }

    public class B extends A {
        public void a() {
            System.out.println("Hello from B::a");
        }
    }

}

