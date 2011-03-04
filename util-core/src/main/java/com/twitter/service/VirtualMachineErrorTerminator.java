package com.twitter.service;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class to terminate the JVM in case of a virtual machine error.
 * Classes wishing to use should initialize it early using
 * {@link #initialize()}, as it might be unable to initialize properly after
 * the virtual machine error has been thrown. See {@link #checkTerminating()}
 * for the typical usage pattern.
 * @author Attila Szegedi
 */
public class VirtualMachineErrorTerminator
{
    private static final int DEFAULT_VMERROR_EXIT_STATUS = -10;

    private static final Logger logger = Logger.getLogger(
            VirtualMachineErrorTerminator.class.getName());

    private static final Object lock = new Object();
    private static volatile boolean terminating = false;
    private static volatile VirtualMachineError terminationCause;
    // A 1M memory buffer we release immediately before invoking System.exit().
    // This gives a bit of a breathing room for shutdown hooks to execute.
    // While not an absolute guarantee of safety (threads that are not
    // cooperating through {@link #checkTerminated()} might go on allocating
    // memory), together with priority boost of the exit thread it still fairly
    // increases the chances of success. Can be resized by a call to
    // {@link #setSafetyBufferSize(int)}.
    private static volatile byte[] safetyBuffer = new byte[1024*1024];

    // Exit code for System.exit().
    private static volatile int exitCode = DEFAULT_VMERROR_EXIT_STATUS;

    static {
        startExitThread();
    }

    // The runnable that invokes System.exit(). Extracted for testing purposes,
    // as otherwise it'd be impossible to test for whether System.exit() was
    // invoked.
    private static Runnable exitInvoker = new Runnable() {
        public void run() {
          System.exit(exitCode);
        }
    };

    private static void startExitThread() {
        // We're using a separate thread to invoke System.exit() because it
        // blocks while shutdown hooks are running and we don't want to risk a
        // deadlock in the shutdown sequence as the code invoking terminateVM()
        // might be inside a block synchronized on a monitor that is also
        // acquired by the shutdown code.
        Thread t = new Thread(new Runnable() {
            public void run() {
                synchronized(lock) {
                    while(!terminating) {
                        try {
                            lock.wait();
                        }
                        catch(InterruptedException e) {
                            // Intentionally ignored
                        }
                    }
                    safetyBuffer = null;
                    logger.log(Level.SEVERE, "Exiting JVM", terminationCause);
                    exitInvoker.run();
                }
            }
        }, "VirtualMachineErrorTerminator");
        t.setDaemon(true);
        t.setPriority(Thread.MAX_PRIORITY);
        t.start();
    }

    private VirtualMachineErrorTerminator() {
    }

    /**
     * Invoke to initialize the terminator.
     */
    public static void initialize() {
        // Do nothing - invoking it makes sure that the static initializer
        // executes.
    }

    /**
     * Sets the size of the safety buffer to use. Defaults to 1048576
     * (1MiByte). This buffer is released when the VM is being terminated to
     * provide a bit of extra memory for running of the shutdown hooks.
     * @param size the size of the safety buffer, in bytes.
     * @throws IllegalArgumentException if size < 0.
     */
    public static void setSafetyBufferSize(int size) {
        if(size < 0) {
            throw new IllegalArgumentException("size < 0");
        }
        // First release the previous one
        safetyBuffer = null;
        // Only then allocate the new one
        safetyBuffer = new byte[size];
    }

    /**
     * Sets the exit code to use when the VM is shut down using
     * {@link System#exit(int)}. Defaults to -10.
     * @param exitCode the exit code to use.
     */
    public static void setExitCode(int exitCode) {
        VirtualMachineErrorTerminator.exitCode = exitCode;
    }

    /**
     * Sets a new runnable that encapsulates the exit call. Only used for
     * testing.
     * @param exitInvoker the new exit invoker.
     */
    static void setExitInvoker(Runnable exitInvoker) {
        VirtualMachineErrorTerminator.exitInvoker = exitInvoker;
    }

    static void reset() {
        terminating = false;
        terminationCause = null;
        startExitThread();
    }

    /**
     * Checks whether this virtual machine is terminating because of a virtual
     * machine error. If it is not, returns immediately. If it is terminating,
     * it will rethrow the original termination cause.
     * Typically used in per work unit servicing code as
     * <pre>
     * VirtualMachineErrorTerminator.checkTerminating();
     * try
     * {
     *     ... normal work unit servicing code ...
     * }
     * catch(VirtualMachineError e)
     * {
     *     VirtualMachineErrorTerminator.terminateVM(e);
     * }
     * </pre>
     * or
     * <pre>
     * VirtualMachineErrorTerminator.checkTerminating();
     * try
     * {
     *     ... normal work unit servicing code ...
     * }
     * catch(Throwable t)
     * {
     *     VirtualMachineErrorTerminator.terminateVMIfMust(t);
     * }
     * </pre>
     * It should be used this way to prevent parallel processing threads from
     * servicing work units while the terminator is terminating the virtual
     * machine. Instead, these parallel threads will fail fast.
     * @throws VirtualMachineError the original JVM termination cause, if the
     * JVM is being terminated in response to a VirtualMachineError.
     */
    public static void checkTerminating() throws VirtualMachineError {
        if(terminating) {
            throw terminationCause;
        }
    }

    /**
     * Checks if the throwable or any of the causes in its cause chain are a
     * virtual machine error, and if so, terminates the virtual machine. If
     * neither the throwable nor any of its causes are a virtual machine error,
     * then does nothing. Useful in the body of a generic
     * <pre>catch(Throwable t)</pre> block.
     * @param t the topmost throwable. Can be null.
     */
    public static void terminateVMIfMust(Throwable t) {
        while(t != null) {
            if(t instanceof VirtualMachineError) {
                VirtualMachineErrorTerminator.terminateVM(
                        (VirtualMachineError)t);
            }
            t = t.getCause();
        }
    }

    /**
     * Terminates the JVM. It will cause System.exit() with exit code set in
     * {@link #setExitCode(int)} to be invoked on a separate thread.
     * @param e the virtual machine error that is the cause for terminating the
     * JVM
     * @throws IllegalArgumentException if e == null
     */
    public static void terminateVM(VirtualMachineError e) {
        if(e == null) {
            throw new IllegalArgumentException("e == null");
        }
        if(terminating) {
            return;
        }
        // Ordering is important - these fields are volatile; we must first
        // set terminationCause and only after that terminating so that
        // checkTerminating() never observes (terminating==true &&
        // terminationCause == null)
        terminationCause = e;
        synchronized(lock) {
            terminating = true;
            lock.notify();
        }
    }
}