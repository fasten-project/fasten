package merge.publicObject;

public class Main {
    public static Object v;

    public static void main(String[] args) {
        foo();
        bar();
    }

    public static void foo() {
        Object o = new A();
        v = o;
    }

    public static void bar() {
        v.toString();
    }
}

class A{
    public String toString(){
        return "A";
    }
}