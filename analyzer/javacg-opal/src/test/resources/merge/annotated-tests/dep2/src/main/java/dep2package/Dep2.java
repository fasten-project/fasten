package dep2package;

import apppackage.App;
import apppackage.AppInterface;

public class Dep2 extends App implements AppInterface {
    
    public static void target(){
        Object obj = new Object();
        obj.hashCode();
    }
    
    public void m1(){}

    public void m2() {
        System.out.println();
    }
}
