public class SimpleReturn {
    public static void main(String args[]) {

        System.out.println(simpleCalc(0xbeef, true));
        System.out.println(simpleCalc(0xbeef, false));
        System.out.println(simpleCalc(0, true));
        System.out.println(simpleCalc(0, false));
    }

    static boolean simpleCalc(int a, boolean b) {
        if(a==0xbeef) {
            return b;
        } else {
            return !b;
        }
    }
}
