package apppackage;

public class SuperAndThis {
    public static void main(String[] args) {
        //A2.<init>(IntegerType)
        A a = new A2(1);

        //A2.m()
        a.m();

        //A2.n()
        a.n();
    }

// ########################################

     static abstract class A {

        public A(int i) {
        }

        public void m() {
            //A2.n()
            n();
        }

        public abstract void n();

        public void o() {}
    }

    static class A2 extends A {

        public A2(int i) {
            //A.<init>(IntegerType)
            super(i);
            //A.m()
            super.m();
        }

        public void m() {

            //A.m()
            super.m();

            //A.o()
            o();
        }

        public void n() {}
    }
}
