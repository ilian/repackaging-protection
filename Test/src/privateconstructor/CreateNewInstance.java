package privateconstructor;

import beef.GetBeef;

public class CreateNewInstance {
    public final class SampleClass {
        SampleClass() {
            System.out.println("default init");
        }
    }
    public static void main(String[] args){
        CreateNewInstance instance = new CreateNewInstance();
        instance.test();
    }

    private void test() {
        if(GetBeef.getBeef() == 0xbeef) {
            SampleClass s = new SampleClass();
        }
    }
}
