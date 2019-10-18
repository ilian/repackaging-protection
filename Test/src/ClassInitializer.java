import beef.GetBeef;

public class ClassInitializer {

    static {
        if(GetBeef.getBeef() == 0xbeef)
            a();
    }

    {
        if(GetBeef.getBeef() == 0xbeef)
            b();
    }

    public static void main(String[] args) {
        ClassInitializer instance = null;
        if(GetBeef.getBeef() == 0xbeef)
            instance = new ClassInitializer();
        instance.b();
    }

    public static void a() {
        if(GetBeef.getBeef() == 0xbeef)
            System.out.println("Hello from a");
    }

    public void b() {
        if(GetBeef.getBeef() == 0xbeef)
            System.out.println("Hello from b");
    }
}
