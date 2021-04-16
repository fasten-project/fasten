package inheritedandsubtyped;

public class Runner {
    public static void main(String[] args) {
        //BClass.<init>()
        IInterface iInterface = new BClass();
        //AClass.m()
        iInterface.m();
        //CClass.<init>()
        IInterface iInterface1 = new CClass();
        //CClass.m()
        iInterface1.m();
    }
}
