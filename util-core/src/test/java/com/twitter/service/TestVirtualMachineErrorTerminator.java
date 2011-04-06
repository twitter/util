package com.twitter.service;

import junit.framework.TestCase;

/**
 * @author Attila Szegedi
 */
public class TestVirtualMachineErrorTerminator extends TestCase
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

    public void testNotTerminatingOnNull() {
        VirtualMachineErrorTerminator.terminateVMIfMust(null);
        assertNotTerminating();
    }

    public void testNotTerminatingOnOtherException() {
        VirtualMachineErrorTerminator.terminateVMIfMust(new Exception());
        assertNotTerminating();
    }

    public void testTerminatingOnNestedVirtualMachineError() throws Exception {
        Exception e = new Exception();
        OutOfMemoryError oome = new OutOfMemoryError();
        e.initCause(oome);
        VirtualMachineErrorTerminator.terminateVMIfMust(e);
        assertTerminated();
    }

    public void testTerminatingOnVirtualMachineError() throws Exception {
        VirtualMachineErrorTerminator.terminateVMIfMust(new OutOfMemoryError());
        assertTerminated();
    }

    public void testTerminateDoesntAcceptNull() throws Exception {
        try {
            VirtualMachineErrorTerminator.terminateVM(null);
            fail();
        }
        catch(IllegalArgumentException e) {
        }
    }

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
            try {
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
            finally {
                VirtualMachineErrorTerminator.reset();
            }
        }
    }
}