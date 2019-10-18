import beef.GetBeef;

public class RegressionTestInsertBeforeNoRedirect {
    public static void main(String[] args) {
        for(int i = 0; i < 5; i++) {
            if(GetBeef.getBeef() == 0xbeef) {
                if(GetBeef.getBeef() > 2) {
                } else {
                    return;
                }
            }
        }
    }
}
