package appendix;

public class Example {

    private static void printMe(int i) {
        System.out.println("i = " + i);
    }

    public static void main(String[] args) {
        int[] a = new int[20];
        for(int i = 0; i < a.length; i++) {
            a[i] = i*i;
            if(a[i] == 0x169) {
                printMe(i);
            }
        }
    }
}
