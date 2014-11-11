package com.twitter.util;

import org.junit.Assert;
import org.junit.Test;
import scala.Tuple2;

import java.util.ArrayList;
import java.util.Collection;

public class ActivityCompilationTest {

  private static final Activity.State<String> ok = new Activity.Ok<String>("ok");

  @Test
  public void testActivityCreation() {
    Activity<?> a = Activities.newPendingActivity();
    Activity<?> b = Activities.newFailedActivity(new Exception());
    Activity<Object> c = Activities.newFutureActivity(Future.value(null));
    Activity<Object> d = Activities.newValueActivity(new Object());
    Tuple2<Activity<Object>, Witness<Try<Object>>> e = Activities.newActivity();
    Activity<String> f = Activities.newActivity(Vars.newConstVar(ok));

    Activity<?> all = a.join(b).join(c).join(d).join(e._1()).join(f);
    Assert.assertTrue(all != null);
  }

  @Test
  public void testSample() {
    Activity<String> a = Activities.newValueActivity("42");
    Assert.assertEquals("42", Activities.sample(a));
  }

  @Test
  public void testS() {
    ArrayList<Activity<String>> list = new ArrayList<Activity<String>>();
    list.add(Activities.newValueActivity("42"));
    list.add(Activities.newValueActivity("24"));

    Activity<Collection<String>> activities = Activities.collect(list);
    Assert.assertArrayEquals(new String[]{"42", "24"}, Activities.sample(activities).toArray());
  }
}
