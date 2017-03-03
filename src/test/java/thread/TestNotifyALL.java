package thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by lxq on 2016/12/27.
 */
public class TestNotifyALL {
    public class NotifyChild extends Thread {
        private int num;
        private Object lock;

        public NotifyChild(int num, Object lock) {
            super();
            this.num = num;
            this.lock = lock;
        }

        public synchronized void testWait() {
            try {
                System.out.println("Before wait");
                wait();
                System.out.println("After wait");
                System.out.println("notifyAll is ok");
                System.out.println("\n\n");
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public synchronized void testNotifyAll() {
            notifyAll();
        }

        @Override
        public void run() {
            try {
//                while (true)
                {
                    System.out.println("Thread is running ......");
                    Thread.sleep(3000);
//                    System.out.println("Before notifyAll");
                    testNotifyAll();
//                    System.out.println("After notifyAll");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public class WaitChild extends Thread {
        private NotifyChild child;
        private int num;

        public WaitChild(NotifyChild child, int num) {
            super();
            this.child = child;
            this.num = num;
        }

        @Override
        public void run() {
            try {
                System.out.println("Before receive notify.This thread is WaitChild-" + num);
                synchronized (child) {
                    child.wait();
                }
                System.out.println("After receive notify. This thread is WaitChild-" + num);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    synchronized void notifyALLTest() {
        Object lock = new Object();
        NotifyChild thread1 = new NotifyChild(1, lock);
        WaitChild waitChild1= new WaitChild(thread1,1);
        WaitChild waitChild2= new WaitChild(thread1,2);
        WaitChild waitChild3= new WaitChild(thread1,3);
        WaitChild waitChild4= new WaitChild(thread1,4);

        waitChild1.start();
        waitChild2.start();
        waitChild3.start();
        waitChild4.start();

        thread1.start();




    }

    public static void main(String[] args) {
        TestNotifyALL testNotifyALL = new TestNotifyALL();

        testNotifyALL.notifyALLTest();
    }
}
