package com.twitter.service;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Attila Szegedi
 */
public class TestVirtualMachineErrorTerminator
{
    private static boolean exitInvoked;
    private static final Object lock = new Object();

    static {
        VirtualMachineErrorTerminator.setExitInvoker(new Runnable() {
            public void run() {
                synchronized(lock) {
                    exitInvoked = true;
                    lock.notify();
                }
            }
        });
    }

    @After
    public void tearDown() {
      VirtualMachineErrorTerminator.reset();
    }

    @Test
    public void testNotTerminatingWhenNew() {
        assertNotTerminating();
    }

    private static void assertNotTerminating() {
        try {
            VirtualMachineErrorTerminator.checkTerminating();
        }
        catch(Throwable t) {
            fail();
        }
    }

    @Test
    public void testNotTerminatingOnNull() {
        VirtualMachineErrorTerminator.terminateVMIfMust(null);
        assertNotTerminating();
    }

    @Test
    public void testNotTerminatingOnOtherException() {
        VirtualMachineErrorTerminator.terminateVMIfMust(new Exception());
        assertNotTerminating();
    }

    @Test
    public void testTerminatingOnNestedVirtualMachineError() throws Exception {
        Exception e = new Exception();
        OutOfMemoryError oome = new OutOfMemoryError();
        e.initCause(oome);
        VirtualMachineErrorTerminator.terminateVMIfMust(e);
        assertTerminated();
    }

    @Test
    public void testTerminatingOnVirtualMachineError() throws Exception {
        VirtualMachineErrorTerminator.terminateVMIfMust(new OutOfMemoryError());
        assertTerminated();
    }

    @Test
    public void testTerminateDoesntAcceptNull() throws Exception {
        try {
            VirtualMachineErrorTerminator.terminateVM(null);
            fail();
        }
        catch(IllegalArgumentException e) {
        }
    }

    @Test
    public void testSecondTerminateIneffective() throws Exception {
        OutOfMemoryError oome1 = new OutOfMemoryError();
        OutOfMemoryError oome2 = new OutOfMemoryError();
        VirtualMachineErrorTerminator.terminateVM(oome1);
        VirtualMachineErrorTerminator.terminateVM(oome2);
        try {
            VirtualMachineErrorTerminator.checkTerminating();
            fail();
        }
        catch(OutOfMemoryError oome) {
            assertSame(oome, oome1);
        }
    }

    private static void assertTerminated() throws InterruptedException {
        try {
            VirtualMachineErrorTerminator.checkTerminating();
            fail();
        }
        catch(VirtualMachineError e) {
            synchronized(lock) {
                long timeout = System.currentTimeMillis() + 1000;
                for(;;) {
                    if(exitInvoked) {
                        exitInvoked = false;
                        break;
                    }
                    long now = System.currentTimeMillis();
                    if(now >= timeout) {
                        fail();
                    }
                    lock.wait(timeout - now);
                }
            }
        }
    }
}
