import beef.GetBeef;

public class FieldsTest {
    public static void main(String[] args) {
        pos1();
        pos2();
        pos3();
        Fields f = new Fields(0xabc);
        //f.setPrivateField(1);
        //f.printPrivateField();
        System.out.println(f.getPrivateField());
    }

    private static void pos1() {
        if(GetBeef.getBeef() == 0xbeef) {
            Fields f = new Fields(0xabc);
            System.out.println(f.beef);
            System.out.println(f.constructorValue);
            System.out.println(f.stringConst);
        }
    }

    private static void pos2() {
        Fields f = new Fields(0xabc);
        if(GetBeef.getBeef() == 0xbeef) {
            System.out.println(f.beef);
            System.out.println(f.constructorValue);
            System.out.println(f.stringConst);
        }
    }

    private static void pos3() {
        Fields f = new Fields(0xabc);
        System.out.println(f.beef);
        if(GetBeef.getBeef() == 0xbeef) {
            System.out.println(f.constructorValue);
            System.out.println(f.stringConst);
        }
    }
}
