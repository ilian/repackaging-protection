import beef.GetBeef;

/* IllegalAccessError only seems to be happening on ART */
public class VirtualInvokeSamePackage {
    void accept (VirtualInvokeSamePackage instance) {
        System.out.println("Hello from method with default access modifier!");
    }

    public static void main(String[] args) {
        VirtualInvokeSamePackage instance = new VirtualInvokeSamePackage();
        if(GetBeef.getBeef() == 0xbeef)
            instance.accept(instance);
    }
}
