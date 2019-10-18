import beef.GetBeef;

public class MatchDifferentTypes {
    public static void main(String[] args) {
        if(GetBeef.getCharA() == 'A') {
            System.out.println("OK");
        } else {
            System.out.println("NOT_OK");
        }

        if(GetBeef.getBeef() == 0xbeef) {
            System.out.println("OK");
        } else {
            System.out.println("NOT_OK");
        }

        if(GetBeef.getBeefByte() == (byte)0xbe) {
            System.out.println("OK");
        } else {
            System.out.println("NOT_OK");
        }

        if(GetBeef.getBeefShort() == 69) {
            System.out.println("OK");
        } else {
            System.out.println("NOT_OK");
        }

        if(GetBeef.getBeefLong() == 69) {
            System.out.println("OK");
        } else {
            System.out.println("NOT_OK");
        }

        if(GetBeef.getBeefFloat() == 69) {
            System.out.println("OK");
        } else {
            System.out.println("NOT_OK");
        }

        if(GetBeef.getBeefDouble() == 69) {
            System.out.println("OK");
        } else {
            System.out.println("NOT_OK");
        }
    }
}
