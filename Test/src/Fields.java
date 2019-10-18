import beef.GetBeef;

public class Fields {
    public String stringConst = "Fields.stringConst";
    public int beef = 0xbeef;
    public int constructorValue;
    private int privateField = 0;

    Fields(int constructorValue) {
        this.constructorValue = constructorValue;
    }

    public void setPrivateField(int privateField) {
        if (GetBeef.getBeef() == 0xbeef)
            this.privateField = privateField;
    }

    public int getPrivateField() {
        return privateField;
    }

    public void printPrivateField() {
        System.out.println(this.privateField);
    }
}
