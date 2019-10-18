import beef.GetBeef;

public class NestedSDC {
    public static void main(String[] args) {
        int i = 0;
        if(GetBeef.getBeef() == 0xbeef) {
            int j = 1;
            System.out.println("Call from outer block");
            if(GetBeef.getBeef() == 0xbeef) {
                i++;
                System.out.printf("Call from inner block! i: %d, j: %d", i, j);
            }
        }
    }
}
