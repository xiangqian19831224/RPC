package other;

/**
 * Created by lxq on 12/26/16.
 */
public class TestFillInStackTrace {
    public static void main(String args[]) {
//        System.out.println("******************Before f()**********************");
//        try {
//            f();
//            System.out.println("******************After f()**********************");
//        } catch (Throwable throwable) {
//            throwable.printStackTrace();
//        }


        System.out.println("******************Before g()**********************");
        try {
            g();
            System.out.println("******************After g()**********************");
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public static void f() throws Throwable {
        try {
            throw new Throwable();
        } catch (Exception e) {
            System.out.println("catch exception e");
            e.printStackTrace();
        }
    }
    public static void g() throws Throwable {
        try {
            try {
                throw new Exception("exception");
            } catch (Exception e) {
                System.out.println("inner exception handler");
                throw e.fillInStackTrace();
            }
        } catch (Exception e) {
            System.out.println("outer exception handler");
            e.printStackTrace();
        }
    }
}
