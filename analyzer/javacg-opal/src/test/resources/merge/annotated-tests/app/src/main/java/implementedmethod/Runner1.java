package implementedmethod;

public class Runner1 {
    public static void main(String[] args) {
        //BKlass.<init>()
        IInter i = new BKlass();
        //BKlass.m()
        i.m();
        //CKlass.<init>()
        IInter i1 = new CKlass();
        //CKlass.m()
        i1.m();
    }
}
