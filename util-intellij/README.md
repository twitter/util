# util-intellij

`util-intellij` is a project containing IntelliJ plugins and other utilities.

## Contents

### Twitter Futures Capture Points

Stack traces produced from asynchronous execution have undesirable properties,
e.g., they are disorganized and often contain frames from internal libraries
that programmers need not know about. This result is inevitable since not only
do different parts of the same program get executed on different threads, and
possibly different cores, but execution also jumps between different frames.
This means that each thread used to execute a program may produce a unique stack
trace. Thus, an Asynchronous Stack Trace (AST) gives a fragmented window view
into the execution of a program and thus makes it difficult to understand the
flow of code because the causality between frames is lost.

In response to this phenomenon, IntelliJ 2017.1 introduced a feature called
Capture Points which is built on top of the IntelliJ Debugger. A capture point
is a place in a computer program where the debugger collects and saves stack
frames to be used later when we reach a specific point in the code and we want
to see how we got there. IntelliJ IDEA does this by substituting part of the
call stack with the captured frame.

We have written capture points for Twitter Futures in an XML file. Users can
debug their asynchronous code more easily with these capture points. Note that
as of October 2017, the capture points work with only Scala 2.11.11.

#### Setup

The capture points are defined in
`util/util-intellij/src/main/resources/TwitterFuturesCapturePoints.xml`.
To import them into IntelliJ,

1. Open IntelliJ. In the menu bar, click IntelliJ IDEA > Preferences.
2. Navigate to Build, Execution, Deployment > Debugger > Async Stacktraces.
3. Click the Import icon on the bottom bar. Find the XML file and click OK.

#### Use

TL;DR: set a breakpoint where you wish to see the stack trace, debug your code,
and look at the "Frames" tab in the Debugger. Any asynchronous calls in the 
stack trace will appear in logical order. If you wish to clean up the stack
trace, click the funnel icon in the top right, "Hide Frames from Libraries".

We will illustrate how to use the capture points to assist with debugging with a
small example. The example is located in
`util/util-intellij/src/test/scala/com/twitter/util/capturepoints/Demo.scala`.

A brief explanation of this test: the test passes a `Promise[Int]` through three
methods and then sets the `Promise`’s value in a `futurePool`. The calls are
asynchronous, but the logical flow of the test is as follows:

1. `test` block calls `someBusinessLogic`
2. `someBusinessLogic` calls `moreBusinessLogic`
3. `moreBusinessLogic` calls `lordBusinessLogic`
4. `lordBusinessLogic` waits for the `Promise`’s value to be set
5. The `Promise`’s value is set in the test block (this could happen at any
   time; it is not necessarily step number 5)
6. `lordBusinessLogic` returns
7. `test`’s `result` variable is `4`, and the test passes

Suppose we wish to inspect the stack trace from inside `lordBusinessLogic`. If 
we set a breakpoint at line 47 and then debug the test with pants in IntelliJ 
with the capture points disabled, we see the following stack trace:

```
apply$mcVI$sp:47, Demo$$anonfun$lordBusinessLogic$1
apply:1820, Future$$anonfun$onSuccess$1
apply:205, Promise$Monitored
run:532, Promise$$anon$7
run:198, LocalScheduler$Activation
submit:157, LocalScheduler$Activation
submit:274, LocalScheduler
submit:109, Scheduler$
runq:522, Promise
updatelfEmpty:880, Promise
update:859, Promise
setValue:835, Promise
apply$mcV$sp:20, Demo$$anonfun$3$$anonfun$apply$1
apply:15, Try$
run:140, ExecutorServiceFuturePool$$anon$4
```

The library calls in the stack trace do not help us understand our code. If we
enable the capture points and again debug the test, we see the following stack
trace:

```
apply$mcVI$sp:47, Demo$$anonfun$lordBusinessLogic$1
apply:1820, Future$$anonfun$onSuccess$1
onSuccess:1819, Future
lordBusinessLogic:42, Demo
moreBusinessLogic:38, Demo
someBusinessLogic:31, Demo
apply:17, Demo$$anonfun$3
```

This is much cleaner and it includes only calls to functions we wrote. It lets
us clearly see that the test block called `someBusinessLogic`, that
`someBusinessLogic` called `moreBusinessLogic`, and that `moreBusinessLogic`
called `lordBusinessLogic`.
