package com.twitter.util.yaml

import com.twitter.util.TimeConversions._
import java.util.Date
import org.specs.Specification


object RubyUtilSpec extends Specification {
  "RubyUtilSpec" should {
    "evaluateErb" in {
      RubyUtil.evaluateErb("")                              must be_==("")
      RubyUtil.evaluateErb("hello world")                   must be_==("hello world")
      RubyUtil.evaluateErb("<%=")                           must be_==("<%=")
      RubyUtil.evaluateErb("<%=unknown%>")                  must throwA[UnsupportedOperationException]

      RubyUtil.evaluateErb("<%= 0.123456 %>")               must be_==("0.123456")
      RubyUtil.evaluateErb("<%= 1.second %>")               must be_==("1")
      RubyUtil.evaluateErb("<%= 1.seconds %>")              must be_==("1")
      RubyUtil.evaluateErb("<%= (2.second * 0.5).to_i %>")  must be_==("1")
      RubyUtil.evaluateErb("<%= (2.seconds * 0.5).to_i %>") must be_==("1")
      RubyUtil.evaluateErb("<%= 1.minute %>")               must be_==("60")
      RubyUtil.evaluateErb("<%= 1.minutes %>")              must be_==("60")
      RubyUtil.evaluateErb("<%= (2.minute * 0.5).to_i %>")  must be_==("60")
      RubyUtil.evaluateErb("<%= (2.minutes * 0.5).to_i %>") must be_==("60")
      RubyUtil.evaluateErb("<%= 1.hour %>")                 must be_==("3600")
      RubyUtil.evaluateErb("<%= 1.hours %>")                must be_==("3600")
      RubyUtil.evaluateErb("<%= (2.hour * 0.5).to_i %>")    must be_==("3600")
      RubyUtil.evaluateErb("<%= (2.hours * 0.5).to_i %>")   must be_==("3600")
      RubyUtil.evaluateErb("<%= 1.day %>")                  must be_==("86400")
      RubyUtil.evaluateErb("<%= 1.days %>")                 must be_==("86400")
      RubyUtil.evaluateErb("<%= (2.day * 0.5).to_i %>")     must be_==("86400")
      RubyUtil.evaluateErb("<%= (2.days * 0.5).to_i %>")    must be_==("86400")

      RubyUtil.evaluateErb("<%= 1.second %><%= 2.second %>")   must be_==("12")
      RubyUtil.evaluateErb("<%= 1.second %>\n<%= 2.second %>") must be_==("1\n2")
    }

    "evaluateErb alternate format" in {
      RubyUtil.evaluateErb("<%= T_1_SECOND %>")    must be_==("1")
      RubyUtil.evaluateErb("<%= T_100_SECONDS %>") must be_==("100")
      RubyUtil.evaluateErb("<%= T_1_MINUTE %>")    must be_==("60")
      RubyUtil.evaluateErb("<%= T_24_MINUTES %>")  must be_==("1440")
      RubyUtil.evaluateErb("<%= T_1_HOUR %>")      must be_==("3600")
      RubyUtil.evaluateErb("<%= T_1_HOURS %>")     must be_==("3600")
      RubyUtil.evaluateErb("<%= T_1_DAY %>")       must be_==("86400")
      RubyUtil.evaluateErb("<%= T_1_DAYS %>")      must be_==("86400")
    }

    "erbToDuration format" in {
      RubyUtil.erbToDuration("<%= T_1_SECOND %>").inSeconds    must_== 1
      RubyUtil.erbToDuration("<%= T_100_SECONDS %>").inSeconds must_== 100
      RubyUtil.erbToDuration("<%= T_1_MINUTE %>").inSeconds    must_== 60
      RubyUtil.erbToDuration("<%= T_24_MINUTES %>").inSeconds  must_== 1440
      RubyUtil.erbToDuration("<%= T_1_HOUR %>").inSeconds      must_== 3600
      RubyUtil.erbToDuration("<%= T_1_HOURS %>").inSeconds     must_== 3600
      RubyUtil.erbToDuration("<%= T_1_DAY %>").inSeconds       must_== 86400
      RubyUtil.erbToDuration("<%= T_1_DAYS %>").inSeconds      must_== 86400
      RubyUtil.erbToDuration("<%= 1.123456 %>").inMicroseconds must_== 1123456
      RubyUtil.erbToDuration("<% T_1_DAYS %>").inSeconds       must throwA[IllegalArgumentException]
      RubyUtil.erbToDuration("blah").inSeconds                 must throwA[IllegalArgumentException]
    }

    "toInt" in {
      RubyUtil.toInt("0")             must be_==(0)
      RubyUtil.toInt("blarg")         must be_==(0)
      RubyUtil.toInt("1000000000000") must be_==(0)
    }

    "toLong" in {
      RubyUtil.toLong("0")                      must be_==(0L)
      RubyUtil.toLong("blarg")                  must be_==(0L)
      RubyUtil.toLong("1000000000000000000000") must be_==(0L)
    }
  }
}
