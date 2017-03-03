package other;

/**
 * Created by lxq on 12/26/16.
 */
public class TestPrintStackTrace {

    public static void main(String args[]) {
        try {
            a();
        } catch(HighLevelException e) {
            e.printStackTrace();
        }
    }
    static void a() throws HighLevelException {
        try {
            b();
        } catch(MidLevelException e) {
            throw new HighLevelException(e);
        }
    }
    static void b() throws MidLevelException {
        c();
    }
    static void c() throws MidLevelException {
        try {
            d();
        } catch(LowLevelException e) {
            throw new MidLevelException(e);
        }
    }
    static void d() throws LowLevelException {
        e();
    }
    static void e() throws LowLevelException {
        throw new LowLevelException();
    }

    static class HighLevelException extends Exception {
        HighLevelException(Throwable cause) { super(cause); }
    }

    static class MidLevelException extends Exception {
        MidLevelException(Throwable cause)  { super(cause); }
    }

    static class LowLevelException extends Exception {
    }
}
