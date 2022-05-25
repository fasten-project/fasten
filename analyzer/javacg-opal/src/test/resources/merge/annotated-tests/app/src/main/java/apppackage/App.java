package apppackage;

import dep1package.Dep1;

public class App {
    public static void main(String[] args) {
        //Dep1.source()
        Dep1.source();
        //App.<init>()
        App app = new App();
        //App.m1()
        app.m1();
        //InterfaceImplementor.<init>()
        AppInterface appInterface = new InterfaceImplementor();
        //InterfaceImplementor.m2()
        appInterface.m2();
    }

    public void m1(){
        System.out.println("I'm in App");
    }

}
