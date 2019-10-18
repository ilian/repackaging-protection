public class SimpleTest {
    public static void main(String[] args) {
        int i = Integer.parseInt("48879");
        if(i == 0xbeef) {
            System.out.println("If condition evaluated to true");
            i = 0xcafe;
        } else {
            System.out.println("If condition evaluated to false");
        }
        System.out.println(i);
    }
}
